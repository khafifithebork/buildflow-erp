package com.buildflow.erp.domain.bpu.service;

import com.buildflow.erp.common.exception.BusinessRuleException;
import com.buildflow.erp.domain.bpu.dto.request.CreateBpuLigneRequest;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a BPU (Bordereau des Prix Unitaires) market schedule uploaded as
 * .xlsx/.xls.
 * <p>
 * Dynamically detects the header row by scanning for known column keywords
 * (e.g. "désignation"), then maps columns by name rather than fixed position.
 * Section/lot header rows (those with a reference but no unit, quantity, or
 * price) are automatically skipped.
 * <p>
 * Expected columns (order is auto-detected):
 * N° | Désignation | U | Qté | P.U. HT | [Mont. HT]
 * <p>
 * The optional Mont. HT column is ignored since {@code budgetPrevuHt} is
 * computed server-side as {@code qtePrevue × puHt}.
 */
@Component
public class BpuExcelParser {

    /** Maximum number of rows to scan when looking for the header. */
    private static final int MAX_HEADER_SCAN = 20;

    // Fallback column indices (original fixed layout)
    private static final int DEFAULT_COL_REF = 0;
    private static final int DEFAULT_COL_DESIGNATION = 1;
    private static final int DEFAULT_COL_UNITE = 2;
    private static final int DEFAULT_COL_QTE = 3;
    private static final int DEFAULT_COL_PU = 4;

    public List<CreateBpuLigneRequest> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("The uploaded BPU Excel file is empty");
        }

        List<CreateBpuLigneRequest> lignes = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            int headerRowIndex = detectHeaderRow(sheet);
            int[] cols = detectColumns(sheet.getRow(headerRowIndex));

            for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }

                String ref = readString(row, cols[0]);
                if (ref == null || ref.isBlank()) {
                    continue; // skip blank / trailing rows
                }

                String designation = readString(row, cols[1]);
                String unite = readString(row, cols[2]);
                BigDecimal qtePrevue = readNumericOrNull(row, cols[3]);
                BigDecimal puHt = readNumericOrNull(row, cols[4]);

                // ── Section / lot header rows: have ref+designation but
                //    no unit, quantity, or price → skip silently
                if ((unite == null || unite.isBlank())
                        && qtePrevue == null
                        && puHt == null) {
                    continue;
                }

                // ── Actual data rows: validate all required fields ──
                if (designation == null || designation.isBlank()) {
                    throw new BusinessRuleException(
                            "Unparseable BPU row for ref '" + ref + "': designation is required");
                }
                if (unite == null || unite.isBlank()) {
                    throw new BusinessRuleException(
                            "Unparseable BPU row for ref '" + ref + "': unite is required");
                }
                if (qtePrevue == null) {
                    throw new BusinessRuleException(
                            "Unparseable BPU row for ref '" + ref + "': missing quantity value");
                }
                if (puHt == null) {
                    throw new BusinessRuleException(
                            "Unparseable BPU row for ref '" + ref + "': missing unit price value");
                }

                lignes.add(new CreateBpuLigneRequest(ref, designation, unite, qtePrevue, puHt));
            }
        } catch (IOException e) {
            throw new BusinessRuleException(
                    "Could not read the uploaded BPU Excel file: " + e.getMessage());
        }

        if (lignes.isEmpty()) {
            throw new BusinessRuleException(
                    "No BPU lines could be parsed from the uploaded file");
        }

        return lignes;
    }

    // ── Header & column detection ──────────────────────────────────

    /**
     * Scans the first rows of the sheet looking for one that contains a
     * recognisable header keyword (e.g. "désignation").  Falls back to row 0
     * if nothing is found.
     */
    private int detectHeaderRow(Sheet sheet) {
        int limit = Math.min(sheet.getLastRowNum(), MAX_HEADER_SCAN);
        for (int i = 0; i <= limit; i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            for (Cell cell : row) {
                if (cell.getCellType() == CellType.STRING) {
                    String val = cell.getStringCellValue().trim().toLowerCase();
                    if (val.contains("désignation") || val.contains("designation")) {
                        return i;
                    }
                }
            }
        }
        return 0; // fallback
    }

    /**
     * Detects column positions from the header row by matching known keywords.
     *
     * @return {@code int[5]} = { refCol, designationCol, uniteCol, qteCol, puHtCol }
     */
    private int[] detectColumns(Row headerRow) {
        int refCol = -1;
        int desCol = -1;
        int uniteCol = -1;
        int qteCol = -1;
        int puCol = -1;

        if (headerRow != null) {
            for (Cell cell : headerRow) {
                if (cell.getCellType() != CellType.STRING) {
                    continue;
                }
                String val = cell.getStringCellValue().trim().toLowerCase();
                int col = cell.getColumnIndex();

                if (refCol == -1 && isRefHeader(val)) {
                    refCol = col;
                } else if (desCol == -1 && isDesignationHeader(val)) {
                    desCol = col;
                } else if (uniteCol == -1 && isUniteHeader(val)) {
                    uniteCol = col;
                } else if (qteCol == -1 && isQuantiteHeader(val)) {
                    qteCol = col;
                } else if (puCol == -1 && isPuHeader(val)) {
                    puCol = col;
                }
                // Mont. HT column is intentionally not captured
            }
        }

        return new int[]{
                refCol >= 0 ? refCol : DEFAULT_COL_REF,
                desCol >= 0 ? desCol : DEFAULT_COL_DESIGNATION,
                uniteCol >= 0 ? uniteCol : DEFAULT_COL_UNITE,
                qteCol >= 0 ? qteCol : DEFAULT_COL_QTE,
                puCol >= 0 ? puCol : DEFAULT_COL_PU
        };
    }

    private boolean isRefHeader(String val) {
        return val.equals("n°") || val.equals("n °") || val.equals("ref")
                || val.equals("réf") || val.equals("réf.") || val.equals("n");
    }

    private boolean isDesignationHeader(String val) {
        return val.contains("désignation") || val.contains("designation");
    }

    private boolean isUniteHeader(String val) {
        return val.equals("u") || val.equals("u.") || val.equals("unité")
                || val.equals("unite");
    }

    private boolean isQuantiteHeader(String val) {
        return val.contains("qté") || val.contains("qte") || val.contains("quantit");
    }

    private boolean isPuHeader(String val) {
        // Match "P.U.", "P.U. HT", "PU HT", "Prix Unitaire", etc.
        // but NOT "Mont. HT" or "Montant"
        if (val.contains("mont")) {
            return false;
        }
        return val.contains("p.u") || val.contains("p u")
                || val.equals("pu ht") || val.contains("prix unit");
    }

    // ── Cell readers ───────────────────────────────────────────────

    private String readString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue())
                    .stripTrailingZeros().toPlainString();
            case FORMULA -> readFormulaAsString(cell);
            case BLANK -> null;
            default -> cell.getStringCellValue().trim();
        };
    }

    /**
     * Reads a numeric value from the cell, returning {@code null} when the cell
     * is missing or blank instead of throwing.  This allows the caller to
     * distinguish between "section header" rows (all nulls) and genuinely
     * invalid data.
     */
    private BigDecimal readNumericOrNull(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            return null;
        }
        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
                case FORMULA -> BigDecimal.valueOf(cell.getNumericCellValue());
                case STRING -> {
                    String val = cell.getStringCellValue().trim();
                    yield val.isEmpty() ? null : new BigDecimal(val);
                }
                case BLANK -> null;
                default -> null;
            };
        } catch (NumberFormatException | IllegalStateException e) {
            return null;
        }
    }

    private String readFormulaAsString(Cell cell) {
        try {
            return cell.getStringCellValue().trim();
        } catch (IllegalStateException e) {
            // Formula evaluates to a number
            return BigDecimal.valueOf(cell.getNumericCellValue())
                    .stripTrailingZeros().toPlainString();
        }
    }
}
