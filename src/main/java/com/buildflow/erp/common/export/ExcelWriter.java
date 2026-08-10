package com.buildflow.erp.common.export;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

/**
 * Builds .xlsx workbooks for the export endpoints.
 *
 * <p>One workbook can hold several sheets, so "export this section" and "export
 * the whole dashboard" are the same code path with a different number of sheets.
 *
 * <p>Numbers are written as numbers, not strings, so the file opens ready to
 * total and sort. Dates go in as dates for the same reason.
 */
public final class ExcelWriter implements AutoCloseable {

    private final Workbook workbook = new XSSFWorkbook();
    private final CellStyle headerStyle;
    private final CellStyle titleStyle;
    private final CellStyle moneyStyle;
    private final CellStyle dateStyle;

    public ExcelWriter() {
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());

        headerStyle = workbook.createCellStyle();
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.DARK_RED.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderBottom(BorderStyle.THIN);

        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 13);
        titleStyle = workbook.createCellStyle();
        titleStyle.setFont(titleFont);

        DataFormat fmt = workbook.createDataFormat();
        moneyStyle = workbook.createCellStyle();
        moneyStyle.setDataFormat(fmt.getFormat("#,##0.00"));

        dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(fmt.getFormat("yyyy-mm-dd"));
    }

    /** A column: its header and how to pull the value out of a row object. */
    public record Column<T>(String header, Function<T, Object> value) {}

    /**
     * Adds one sheet listing {@code rows}. An empty collection still produces a
     * sheet with its headers, so the workbook shows the section exists and is
     * simply empty rather than omitting it silently.
     */
    public <T> ExcelWriter sheet(String name, String title, List<T> rows, List<Column<T>> columns) {
        Sheet sheet = workbook.createSheet(safeName(name));

        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(title);
        titleCell.setCellStyle(titleStyle);
        if (columns.size() > 1) {
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, columns.size() - 1));
        }

        Row header = sheet.createRow(2);
        for (int c = 0; c < columns.size(); c++) {
            Cell cell = header.createCell(c);
            cell.setCellValue(columns.get(c).header());
            cell.setCellStyle(headerStyle);
        }

        int r = 3;
        for (T row : rows) {
            Row out = sheet.createRow(r++);
            for (int c = 0; c < columns.size(); c++) {
                write(out.createCell(c), columns.get(c).value().apply(row));
            }
        }

        for (int c = 0; c < columns.size(); c++) {
            sheet.autoSizeColumn(c);
            // autoSizeColumn ignores the merged title, and very long text can
            // blow a column out; keep it within something printable.
            int width = Math.min(sheet.getColumnWidth(c) + 512, 12_000);
            sheet.setColumnWidth(c, width);
        }
        sheet.createFreezePane(0, 3);

        return this;
    }

    /** Two-column key/value sheet, for the KPI figures. */
    public ExcelWriter kpiSheet(String name, String title, List<Column<Void>> ignored, List<String[]> pairs) {
        Sheet sheet = workbook.createSheet(safeName(name));

        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(title);
        titleCell.setCellStyle(titleStyle);

        Row header = sheet.createRow(2);
        for (int c = 0; c < 2; c++) {
            Cell cell = header.createCell(c);
            cell.setCellValue(c == 0 ? "Indicateur" : "Valeur");
            cell.setCellStyle(headerStyle);
        }

        int r = 3;
        for (String[] pair : pairs) {
            Row out = sheet.createRow(r++);
            out.createCell(0).setCellValue(pair[0]);
            try {
                Cell v = out.createCell(1);
                v.setCellValue(Double.parseDouble(pair[1]));
                v.setCellStyle(moneyStyle);
            } catch (NumberFormatException e) {
                out.createCell(1).setCellValue(pair[1]);
            }
        }

        sheet.autoSizeColumn(0);
        sheet.setColumnWidth(0, Math.min(sheet.getColumnWidth(0) + 512, 16_000));
        sheet.setColumnWidth(1, 5_000);
        return this;
    }

    private void write(Cell cell, Object value) {
        switch (value) {
            case null -> cell.setBlank();
            case BigDecimal b -> { cell.setCellValue(b.doubleValue()); cell.setCellStyle(moneyStyle); }
            case Double dd -> { cell.setCellValue(dd); cell.setCellStyle(moneyStyle); }
            case Number n -> cell.setCellValue(n.doubleValue());
            case Boolean b -> cell.setCellValue(b ? "Oui" : "Non");
            case LocalDate d -> { cell.setCellValue(d); cell.setCellStyle(dateStyle); }
            case LocalDateTime d -> { cell.setCellValue(d.toLocalDate()); cell.setCellStyle(dateStyle); }
            default -> cell.setCellValue(String.valueOf(value));
        }
    }

    /** Excel rejects some characters in sheet names and caps them at 31 chars. */
    private static String safeName(String name) {
        String cleaned = name.replaceAll("[\\\\/?*\\[\\]:]", "-");
        return cleaned.length() <= 31 ? cleaned : cleaned.substring(0, 31);
    }

    public byte[] toBytes() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the Excel workbook", e);
        }
    }

    @Override
    public void close() {
        try {
            workbook.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
