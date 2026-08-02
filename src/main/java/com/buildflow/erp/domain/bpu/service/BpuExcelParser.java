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
 * Parses a BPU market schedule uploaded as .xlsx/.xls, expecting the same column
 * order as the prototype's "Importer Marché Excel" feature:
 * Réf | Désignation | Unité | Qté Prévu | PU HT
 */
@Component
public class BpuExcelParser {

    private static final int COL_REF = 0;
    private static final int COL_DESIGNATION = 1;
    private static final int COL_UNITE = 2;
    private static final int COL_QTE_PREVUE = 3;
    private static final int COL_PU_HT = 4;

    public List<CreateBpuLigneRequest> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("The uploaded BPU Excel file is empty");
        }

        List<CreateBpuLigneRequest> lignes = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            for (Row row : sheet) {
                if (row.getRowNum() == 0) {
                    continue; // header row
                }

                String ref = readString(row, COL_REF);
                if (ref == null || ref.isBlank()) {
                    continue; // skip blank trailing rows
                }

                String designation = readString(row, COL_DESIGNATION);
                String unite = readString(row, COL_UNITE);
                BigDecimal qtePrevue = readNumeric(row, COL_QTE_PREVUE, ref);
                BigDecimal puHt = readNumeric(row, COL_PU_HT, ref);

                if (designation == null || designation.isBlank() || unite == null || unite.isBlank()) {
                    throw new BusinessRuleException(
                            "Unparseable BPU row for ref '" + ref + "': designation and unite are required");
                }

                lignes.add(new CreateBpuLigneRequest(ref, designation, unite, qtePrevue, puHt));
            }
        } catch (IOException e) {
            throw new BusinessRuleException("Could not read the uploaded BPU Excel file: " + e.getMessage());
        }

        if (lignes.isEmpty()) {
            throw new BusinessRuleException("No BPU lines could be parsed from the uploaded file");
        }

        return lignes;
    }

    private String readString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
        return cell.getStringCellValue().trim();
    }

    private BigDecimal readNumeric(Row row, int col, String ref) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            throw new BusinessRuleException("Unparseable BPU row for ref '" + ref + "': missing numeric value");
        }
        try {
            if (cell.getCellType() == CellType.STRING) {
                return new BigDecimal(cell.getStringCellValue().trim());
            }
            return BigDecimal.valueOf(cell.getNumericCellValue());
        } catch (NumberFormatException e) {
            throw new BusinessRuleException("Unparseable BPU row for ref '" + ref + "': invalid numeric value");
        }
    }
}
