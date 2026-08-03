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
 * Parses a BPU market schedule uploaded as .xlsx/.xls, expecting the same column
 * order as the prototype's "Importer Marché Excel" feature:
 * Réf | Désignation | Unité | Qté Prévu | PU HT
 *
 * <p>Real-world BPDE/BPU exports are not a clean table: they carry a title banner,
 * a column-header row, LOT/SECTION/SOUS-LOT title rows, mid-section description-only
 * rows, and a "RECAPITULATION GENERALE" footer (sub-total / total HT / TVA / TTC lines)
 * that reuses the ref column for a text label. None of those are data lines. A real
 * BPU line is the only row type that always carries both a Quantité and a Prix
 * Unitaire as actual numbers, so that pair - not the row position or the ref column -
 * is what identifies a data row.
 *
 * <p>Sub-items are also commonly numbered with a single letter ("a", "b", "c"...)
 * scoped under a preceding group-header row (e.g. "2.1.4.01 Canalisations en PVC..."),
 * and that letter is reused by every group in the file. Since {@code bpu_lignes} has a
 * unique (chantier_id, ref) constraint, those bare letters must be qualified with their
 * group's code (e.g. "2.1.4.01.a") to stay unique across the whole import.
 */
@Component
public class BpuExcelParser {

    private static final int COL_REF = 0;
    private static final int COL_DESIGNATION = 1;
    private static final int COL_UNITE = 2;
    private static final int COL_QTE_PREVUE = 3;
    private static final int COL_PU_HT = 4;

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

            for (Row row : sheet) {
                Cell qteCell = row.getCell(COL_QTE_PREVUE);
                Cell puCell = row.getCell(COL_PU_HT);
                String ref = readText(row.getCell(COL_REF), evaluator, formatter);

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

                String designation = readText(row.getCell(COL_DESIGNATION), evaluator, formatter);
                String unite = readText(row.getCell(COL_UNITE), evaluator, formatter);

                if (designation == null || designation.isBlank() || unite == null || unite.isBlank()) {
                    throw new BusinessRuleException(
                            "Unparseable BPU row for ref '" + ref + "': designation and unite are required");
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
                        numericValue(puCell, evaluator)));
            }
        } catch (IOException e) {
            throw new BusinessRuleException("Could not read the uploaded BPU Excel file: " + e.getMessage());
        }

        if (lignes.isEmpty()) {
            throw new BusinessRuleException("No BPU lines could be parsed from the uploaded file");
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
