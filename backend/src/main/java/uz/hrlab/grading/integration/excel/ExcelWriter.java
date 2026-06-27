package uz.hrlab.grading.integration.excel;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
     * Fully-styled grading sheet — the visual EVALUATION_SUMMARY export that
     * matches the brand target design (green header, grid borders, fixed widths,
     * one-decimal numeric score columns, a highlighted+merged panel AVERAGE row,
     * and a frozen header). This is a STYLING superset of
     * {@link #writeWithRowStyles}; the data sourcing / column contract is
     * identical (display headers vs stable data keys, one {@link RowStyle} per
     * row). The report layer owns the layout and tells this writer, explicitly,
     * which columns hold numbers and how wide each column should be — the writer
     * never guesses a column's type from its values.
     *
     * <p>Styling rules:
     * <ul>
     *   <li>Header (row 0): solid brand dark-green fill, white bold centered
     *       wrapped text, taller row, thin borders, frozen.</li>
     *   <li>Every cell (header/data/average) gets thin borders → grid look.</li>
     *   <li>{@code numericColumns} cells are written NUMERIC with format "0.0"
     *       and right-aligned; a blank/empty/unparseable value stays an empty
     *       cell. All other columns stay sanitized STRINGS via
     *       {@link SafeCellWriter} (formula-injection safe).</li>
     *   <li>{@code AVERAGE_HIGHLIGHT} rows keep the light-green fill, add bold,
     *       and merge columns {@code 0..averageMergeEndCol} into one label cell.
     *       Numeric columns on the average row still render with the "0.0"
     *       numeric format.</li>
     * </ul>
     *
     * <p>All POI {@link CellStyle}s are created ONCE and reused (POI caps at
     * ~64k styles); custom {@link XSSFColor} fills work under streaming SXSSF.
     *
     * @param sheetName          POI sheet name (≤ 31 chars)
     * @param displayHeaders     human column titles (platform/locale-authored)
     * @param dataKeys           map keys used to look up each column's value
     * @param rows               data rows keyed by {@code dataKeys}
     * @param rowStyles          one style per row (same size + order as rows)
     * @param numericColumns     0-based column indices written as NUMERIC "0.0"
     * @param columnWidthsChars  fixed per-column width in characters (capped 255);
     *                           may be shorter than the column count — missing
     *                           trailing columns keep POI's default width
     * @param averageMergeEndCol last column index merged into the average-row
     *                           label (label spans {@code 0..averageMergeEndCol})
     */
    public byte[] writeGradingSheet(String sheetName, List<String> displayHeaders,
                                    List<String> dataKeys, List<Map<String, String>> rows,
                                    List<RowStyle> rowStyles, Set<Integer> numericColumns,
                                    int[] columnWidthsChars, int averageMergeEndCol) {
        if (displayHeaders.size() != dataKeys.size()) {
            throw new IllegalArgumentException(
                    "displayHeaders and dataKeys must be the same length");
        }
        if (rowStyles.size() != rows.size()) {
            throw new IllegalArgumentException(
                    "rowStyles and rows must be the same length");
        }
        int columnCount = displayHeaders.size();
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(sheetName == null ? "Sheet1" : sheetName);

            GradingStyles styles = new GradingStyles(wb);

            // Fixed widths — never autoSize under SXSSF (forces full in-memory
            // tracking). POI width unit = 1/256th of a character; cap at 255.
            for (int c = 0; c < columnWidthsChars.length && c < columnCount; c++) {
                sheet.setColumnWidth(c, Math.min(columnWidthsChars[c], 255) * 256);
            }

            // Header row: green + white bold + wrap, ~3 wrapped lines tall.
            Row header = sheet.createRow(0);
            header.setHeightInPoints(sheet.getDefaultRowHeightInPoints() * 3);
            for (int c = 0; c < columnCount; c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(displayHeaders.get(c)); // platform/locale-authored, safe
                cell.setCellStyle(styles.header);
            }

            int rIdx = 1;
            for (int i = 0; i < rows.size(); i++) {
                Map<String, String> data = rows.get(i);
                boolean avg = rowStyles.get(i) == RowStyle.AVERAGE_HIGHLIGHT;
                Row row = sheet.createRow(rIdx);

                if (avg && averageMergeEndCol >= 1) {
                    sheet.addMergedRegion(
                            new CellRangeAddress(rIdx, rIdx, 0, averageMergeEndCol));
                }

                for (int c = 0; c < columnCount; c++) {
                    Cell cell = row.createCell(c);
                    String value = data.get(dataKeys.get(c));
                    String safe = value == null ? "" : value;
                    boolean numeric = numericColumns.contains(c);
                    boolean merged = avg && c <= averageMergeEndCol;

                    if (numeric && !merged) {
                        Double n = parseNumeric(safe);
                        if (n != null) {
                            cell.setCellValue(n);
                        } else {
                            cell.setBlank();
                        }
                        cell.setCellStyle(avg ? styles.avgNumber : styles.number);
                    } else {
                        // Merged average cells: only the top-left cell carries the
                        // label; the rest are styled-but-blank (POI requires each
                        // merged cell to be styled to keep the border grid intact).
                        if (merged && c != 0) {
                            cell.setBlank();
                        } else {
                            SafeCellWriter.writeString(cell, safe);
                        }
                        cell.setCellStyle(avg ? styles.avgText : styles.text);
                    }
                }
                rIdx++;
            }

            sheet.createFreezePane(0, 1);

            wb.write(out);
            wb.dispose();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Parse a score/total string to a double; null/blank/unparseable → null. */
    private static Double parseNumeric(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        if (t.isEmpty()) return null;
        try {
            return Double.valueOf(t);
        } catch (NumberFormatException e) {
            return null; // keep the cell empty rather than corrupt the grid
        }
    }

    /**
     * The reusable cell styles for the grading sheet, created ONCE per workbook
     * (POI ~64k style cap). Borders make the sheet read as a grid; numeric styles
     * carry the "0.0" one-decimal format + right alignment; the average styles add
     * the light-green fill + bold. The viewer's locale renders the decimal
     * separator ("10.0" vs "10,0"). The average LABEL cell and other average TEXT
     * cells share one style ({@code avgText}) — identical green+bold+border — so
     * the spec's avgLabel/avgText collapse into a single reused style.
     */
    private static final class GradingStyles {
        private static final String ONE_DECIMAL = "0.0";

        final CellStyle header;
        final CellStyle text;
        final CellStyle number;
        final CellStyle avgText;
        final CellStyle avgNumber;

        GradingStyles(Workbook wb) {
            short fmt = wb.createDataFormat().getFormat(ONE_DECIMAL);

            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            Font bodyBold = wb.createFont();
            bodyBold.setBold(true);

            // Brand primary dark forest green for the header fill (no brand token
            // exists for an XLSX header; see report doc) — #1F4E2C.
            XSSFColor darkGreen = new XSSFColor(new byte[] {0x1F, 0x4E, 0x2C}, null);

            // SXSSFWorkbook.createCellStyle() returns an XSSFCellStyle, so the
            // custom XSSFColor fill applies directly under streaming (the
            // XSSFColor overload lives on XSSFCellStyle, not the CellStyle iface).
            XSSFCellStyle headerStyle = (XSSFCellStyle) wb.createCellStyle();
            headerStyle.setFillForegroundColor(darkGreen);
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setWrapText(true);
            thinBorders(headerStyle);
            this.header = headerStyle;

            this.text = wb.createCellStyle();
            text.setVerticalAlignment(VerticalAlignment.CENTER);
            thinBorders(text);

            this.number = wb.createCellStyle();
            number.setVerticalAlignment(VerticalAlignment.CENTER);
            number.setAlignment(HorizontalAlignment.RIGHT);
            number.setDataFormat(fmt);
            thinBorders(number);

            this.avgText = wb.createCellStyle();
            avgText.setVerticalAlignment(VerticalAlignment.CENTER);
            avgText.setFont(bodyBold);
            avgText.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            avgText.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            thinBorders(avgText);

            this.avgNumber = wb.createCellStyle();
            avgNumber.setVerticalAlignment(VerticalAlignment.CENTER);
            avgNumber.setAlignment(HorizontalAlignment.RIGHT);
            avgNumber.setDataFormat(fmt);
            avgNumber.setFont(bodyBold);
            avgNumber.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            avgNumber.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            thinBorders(avgNumber);
        }

        private static void thinBorders(CellStyle style) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
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
