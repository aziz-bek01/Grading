package uz.hrlab.grading.integration.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

/**
 * Apache POI streaming writer (SXSSF) for XLSX exports. All user-originated
 * cells are routed through {@link SafeCellWriter} so formula-injection
 * payloads are neutralised at the write boundary (integration-blueprint
 * §13.1).
 */
@Component
public class ExcelWriter {

    /**
     * Write a single-sheet workbook from headers + rows.
     *
     * @param sheetName  POI sheet name (≤ 31 chars)
     * @param headers    column titles, in order — written as-is, no
     *                   sanitization (platform-authored).
     * @param rows       data rows, each a Map keyed by header; values are
     *                   sanitized via {@link SafeCellWriter}.
     */
    public byte[] write(String sheetName, List<String> headers, List<Map<String, String>> rows) {
        return write(sheetName, headers, rows, null);
    }

    /**
     * Write a single-sheet workbook where the displayed HEADER text differs from
     * the data-row lookup KEY. Reports need this for localized human column
     * labels whose underlying value lookup must stay keyed by a stable machine
     * key (e.g. a factor code): {@code displayHeaders} renders in row 0, but each
     * data cell is read with the matching {@code dataKeys} entry. Both lists must
     * be the same length and column-aligned.
     *
     * @param sheetName      POI sheet name (≤ 31 chars)
     * @param displayHeaders human column titles (platform/locale-authored, safe)
     * @param dataKeys       the map keys used to look up each column's value
     * @param rows           data rows keyed by {@code dataKeys}; values sanitized
     */
    public byte[] write(String sheetName, List<String> displayHeaders, List<String> dataKeys,
                        List<Map<String, String>> rows) {
        if (displayHeaders.size() != dataKeys.size()) {
            throw new IllegalArgumentException(
                    "displayHeaders and dataKeys must be the same length");
        }
        try (Workbook wb = new SXSSFWorkbook(100);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(sheetName == null ? "Sheet1" : sheetName);

            Row header = sheet.createRow(0);
            for (int c = 0; c < displayHeaders.size(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(displayHeaders.get(c)); // platform/locale-authored, safe
            }

            int rIdx = 1;
            for (Map<String, String> data : rows) {
                Row row = sheet.createRow(rIdx++);
                for (int c = 0; c < dataKeys.size(); c++) {
                    String value = data.get(dataKeys.get(c));
                    Cell cell = row.createCell(c);
                    SafeCellWriter.writeString(cell, value == null ? "" : value);
                }
            }

            wb.write(out);
            if (wb instanceof SXSSFWorkbook s) {
                s.dispose();
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Per-row visual styling applied by {@link #writeWithRowStyles}. {@code
     * NORMAL} renders with no fill; {@code AVERAGE_HIGHLIGHT} paints the whole
     * row with a solid light-green background (the panel AVERAGE row in the
     * evaluation grading XLSX). Kept tiny and format-agnostic so callers describe
     * intent, not POI styling details.
     */
    public enum RowStyle { NORMAL, AVERAGE_HIGHLIGHT }

    /**
     * Same display-header / data-key contract as {@link #write(String, List,
     * List, List)} (sic — the {@code displayHeaders}+{@code dataKeys} overload),
     * with one extra parallel list of {@link RowStyle} flags — one per data row —
     * that drives a green background fill on {@code AVERAGE_HIGHLIGHT} rows. The
     * header row and {@code NORMAL} rows are written exactly as the unstyled
     * overload would, so non-highlighted output is byte-compatible in layout.
     * All data cells still route through {@link SafeCellWriter}.
     *
     * @param sheetName      POI sheet name (≤ 31 chars)
     * @param displayHeaders human column titles (platform/locale-authored, safe)
     * @param dataKeys       the map keys used to look up each column's value
     * @param rows           data rows keyed by {@code dataKeys}; values sanitized
     * @param rowStyles      one style per row (same size + order as {@code rows})
     */
    public byte[] writeWithRowStyles(String sheetName, List<String> displayHeaders,
                                     List<String> dataKeys, List<Map<String, String>> rows,
                                     List<RowStyle> rowStyles) {
        if (displayHeaders.size() != dataKeys.size()) {
            throw new IllegalArgumentException(
                    "displayHeaders and dataKeys must be the same length");
        }
        if (rowStyles.size() != rows.size()) {
            throw new IllegalArgumentException(
                    "rowStyles and rows must be the same length");
        }
        try (Workbook wb = new SXSSFWorkbook(100);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(sheetName == null ? "Sheet1" : sheetName);

            // One shared green style for every highlighted row (POI caps styles).
            CellStyle highlight = wb.createCellStyle();
            highlight.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            highlight.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row header = sheet.createRow(0);
            for (int c = 0; c < displayHeaders.size(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(displayHeaders.get(c)); // platform/locale-authored, safe
            }

            int rIdx = 1;
            for (int i = 0; i < rows.size(); i++) {
                Map<String, String> data = rows.get(i);
                boolean green = rowStyles.get(i) == RowStyle.AVERAGE_HIGHLIGHT;
                Row row = sheet.createRow(rIdx++);
                for (int c = 0; c < dataKeys.size(); c++) {
                    String value = data.get(dataKeys.get(c));
                    Cell cell = row.createCell(c);
                    SafeCellWriter.writeString(cell, value == null ? "" : value);
                    if (green) {
                        cell.setCellStyle(highlight);
                    }
                }
            }

            wb.write(out);
            if (wb instanceof SXSSFWorkbook s) {
                s.dispose();
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Write a workbook whose FIRST (index-0) sheet is the canonical data sheet
     * and whose SECOND sheet is an optional human-readable guide. The data
     * sheet layout is byte-for-byte identical to {@link #write(String, List,
     * List)} — the guide is appended afterwards, so the parser (which reads
     * sheet index 0 by name) is unaffected.
     *
     * <p>Guide cells are still routed through {@link SafeCellWriter}; bold +
     * wrapped header styling is applied to the marked header rows and column
     * widths come from {@link GuideSheet}. The guide is never parsed on
     * upload (the importer targets the data sheet explicitly).
     *
     * @param sheetName  data-sheet POI name (≤ 31 chars) — sheet index 0
     * @param headers    column titles for the data sheet
     * @param rows       data rows for the data sheet
     * @param guide      optional second guide sheet; {@code null} = no guide
     */
    public byte[] write(String sheetName, List<String> headers,
                        List<Map<String, String>> rows, GuideSheet guide) {
        try (Workbook wb = new SXSSFWorkbook(100);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(sheetName == null ? "Sheet1" : sheetName);

            Row header = sheet.createRow(0);
            for (int c = 0; c < headers.size(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(headers.get(c)); // platform-authored, safe
            }

            int rIdx = 1;
            for (Map<String, String> data : rows) {
                Row row = sheet.createRow(rIdx++);
                for (int c = 0; c < headers.size(); c++) {
                    String value = data.get(headers.get(c));
                    Cell cell = row.createCell(c);
                    SafeCellWriter.writeString(cell, value == null ? "" : value);
                }
            }

            if (guide != null) {
                writeGuideSheet(wb, guide);
            }

            wb.write(out);
            if (wb instanceof SXSSFWorkbook s) {
                s.dispose();
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeGuideSheet(Workbook wb, GuideSheet guide) {
        Sheet sheet = wb.createSheet(guide.sheetName());

        CellStyle headerStyle = wb.createCellStyle();
        Font bold = wb.createFont();
        bold.setBold(true);
        headerStyle.setFont(bold);
        headerStyle.setWrapText(true);
        headerStyle.setVerticalAlignment(VerticalAlignment.TOP);

        CellStyle bodyStyle = wb.createCellStyle();
        bodyStyle.setWrapText(true);
        bodyStyle.setVerticalAlignment(VerticalAlignment.TOP);

        List<GuideSheet.GuideRow> guideRows = guide.rows();
        for (int r = 0; r < guideRows.size(); r++) {
            GuideSheet.GuideRow gr = guideRows.get(r);
            Row row = sheet.createRow(r);
            List<String> cells = gr.cells();
            for (int c = 0; c < cells.size(); c++) {
                Cell cell = row.createCell(c);
                SafeCellWriter.writeString(cell, cells.get(c) == null ? "" : cells.get(c));
                cell.setCellStyle(gr.header() ? headerStyle : bodyStyle);
            }
        }

        int[] widths = guide.columnWidthsChars();
        for (int c = 0; c < widths.length; c++) {
            // POI width unit = 1/256th of a character.
            sheet.setColumnWidth(c, Math.min(widths[c], 255) * 256);
        }
    }
}
