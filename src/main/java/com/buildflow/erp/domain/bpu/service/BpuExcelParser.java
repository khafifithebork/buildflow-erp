package com.buildflow.erp.domain.bpu.service;

import com.buildflow.erp.common.exception.BusinessRuleException;
import com.buildflow.erp.domain.bpu.dto.request.CreateBpuLigneRequest;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses a BPU (Bordereau des Prix Unitaires) market schedule uploaded as
 * .xlsx/.xls.
 *
 * <p>Dynamically detects the header row and its columns by keyword (e.g.
 * "désignation"), since real-world BPDE/BPU exports don't share a fixed
 * column order across files. Falls back to the prototype's fixed layout
 * (Réf | Désignation | Unité | Qté Prévu | PU HT) when detection fails.
 *
 * <p>Real-world exports are also not a clean table below the header: they
 * carry LOT/SECTION/SOUS-LOT title rows, mid-section description-only rows,
 * and a "RECAPITULATION GENERALE" footer (sub-total / total HT / TVA / TTC
 * lines) that reuses the ref column for a text label. None of those are data
 * lines. A real BPU line is the only row type that always carries both a
 * Quantité and a Prix Unitaire as actual numbers, so that pair - not the row
 * position or the ref column alone - is what identifies a data row.
 *
 * <p>Sub-items are also commonly numbered with a single letter ("a", "b",
 * "c"...) scoped under a preceding group-header row (e.g. "2.1.4.01
 * Canalisations en PVC..."), and that letter is reused by every group in the
 * file. Since {@code bpu_lignes} has a unique (chantier_id, ref) constraint,
 * those bare letters must be qualified with their group's code (e.g.
 * "2.1.4.01.a") to stay unique across the whole import.
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

    private static final Pattern LETTER_SUB_REF = Pattern.compile("(?i)^[a-z]{1,2}$");

    public List<CreateBpuLigneRequest> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("The uploaded BPU Excel file is empty");
        }

        List<CreateBpuLigneRequest> lignes = new ArrayList<>();
        Set<String> usedRefs = new HashSet<>();
        String pendingGroupRef = null;

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            DataFormatter formatter = new DataFormatter(Locale.FRENCH);

            int headerRowIndex = detectHeaderRow(sheet);
            int[] cols = detectColumns(sheet.getRow(headerRowIndex));
            int colRef = cols[0];
            int colDesignation = cols[1];
            int colUnite = cols[2];
            int colQte = cols[3];
            int colPu = cols[4];

            for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }

                Cell qteCell = row.getCell(colQte);
                Cell puCell = row.getCell(colPu);
                String ref = readText(row.getCell(colRef), evaluator, formatter);

                if (!isNumeric(qteCell, evaluator) || !isNumeric(puCell, evaluator)) {
                    // Title banner, column-header row, LOT/SECTION titles, and the
                    // récapitulatif footer all lack a real Quantité + PU HT pair.
                    // A group-header row still carries a real code though - remember it
                    // so a following bare-letter sub-item can be qualified with it.
                    if (ref != null && !ref.isBlank() && !LETTER_SUB_REF.matcher(ref).matches()) {
                        pendingGroupRef = ref;
                    }
                    continue;
                }

                if (ref == null || ref.isBlank()) {
                    continue;
                }

                String designation = readText(row.getCell(colDesignation), evaluator, formatter);
                String unite = readText(row.getCell(colUnite), evaluator, formatter);

                if (designation == null || designation.isBlank()) {
                    throw new BusinessRuleException(
                            "Unparseable BPU row for ref '" + ref + "': designation is required");
                }
                if (unite == null || unite.isBlank()) {
                    throw new BusinessRuleException(
                            "Unparseable BPU row for ref '" + ref + "': unite is required");
                }

                String effectiveRef;
                if (LETTER_SUB_REF.matcher(ref).matches() && pendingGroupRef != null) {
                    effectiveRef = pendingGroupRef + "." + ref;
                } else {
                    effectiveRef = ref;
                    pendingGroupRef = ref;
                }
                effectiveRef = disambiguate(effectiveRef, usedRefs);

                lignes.add(new CreateBpuLigneRequest(
                        effectiveRef,
                        designation,
                        unite,
                        numericValue(qteCell, evaluator),
                        numericValue(puCell, evaluator).doubleValue()));
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

    /**
     * Guarantees uniqueness within the import batch (bpu_lignes has a unique
     * (chantier_id, ref) constraint) in case the group-qualification above still
     * isn't enough for some file's layout, by appending a numeric suffix.
     */
    private String disambiguate(String ref, Set<String> usedRefs) {
        String candidate = ref;
        int suffix = 2;
        while (!usedRefs.add(candidate)) {
            candidate = ref + "-" + suffix++;
        }
        return candidate;
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

    private boolean isNumeric(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return false;
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = evaluator.evaluateFormulaCell(cell);
        }
        if (type == CellType.NUMERIC) {
            // Excel sometimes auto-reinterprets a typed code like "2.2.06" as a date
            // and stores it as a numeric date serial - that's a ref, not a quantity.
            return !DateUtil.isCellDateFormatted(cell);
        }
        if (type == CellType.STRING) {
            return isParseableNumber(cell.getStringCellValue());
        }
        return false;
    }

    private BigDecimal numericValue(Cell cell, FormulaEvaluator evaluator) {
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            return BigDecimal.valueOf(evaluator.evaluate(cell).getNumberValue());
        }
        if (type == CellType.STRING) {
            return new BigDecimal(normalizeDecimal(cell.getStringCellValue()));
        }
        return BigDecimal.valueOf(cell.getNumericCellValue());
    }

    private boolean isParseableNumber(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        try {
            new BigDecimal(normalizeDecimal(text));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String normalizeDecimal(String text) {
        return text.trim().replace(",", ".");
    }

    /**
     * Reads a cell as the text a human would see in Excel, going through the
     * cell's own display formatting rather than its raw stored value. This is
     * what recovers ref codes such as "2.2.06" that Excel silently reinterpreted
     * and stored as a date (format "m.d.yy") instead of as plain text - reading
     * the raw value directly would return an Excel date serial number instead of
     * the code that was actually typed.
     */
    private String readText(Cell cell, FormulaEvaluator evaluator, DataFormatter formatter) {
        if (cell == null) {
            return null;
        }
        String text = formatter.formatCellValue(cell, evaluator);
        return text == null ? null : text.trim();
    }
}
