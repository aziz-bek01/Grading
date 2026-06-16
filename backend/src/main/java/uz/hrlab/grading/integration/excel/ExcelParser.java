package uz.hrlab.grading.integration.excel;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException;
import org.apache.poi.poifs.filesystem.NotOLE2FileException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Apache POI XSSF parser that reads the DATA sheet of an .xlsx file and
 * yields one {@code Map<String,String>} per data row, keyed by header.
 *
 * <p><b>Sheet selection.</b> Every downloadable template now ships TWO sheets:
 * the data sheet at index 0 and a human-readable guide sheet (named
 * {@value #GUIDE_SHEET_NAME}) at index 1. Users may fill the data sheet and
 * re-upload the whole workbook including the guide. The parser therefore:
 * <ol>
 *   <li>skips any sheet whose name equals the guide sheet name (case-
 *       insensitive), so a re-ordered workbook still resolves the data sheet;</li>
 *   <li>otherwise takes the FIRST remaining sheet (index 0 in the canonical
 *       layout) — never "the last sheet", never an iterate-all heuristic.</li>
 * </ol>
 *
 * <p>Rejects:
 * <ul>
 *   <li>password-protected workbooks (POI throws {@link InvalidFormatException})</li>
 *   <li>macro-enabled workbooks ({@code .xlsm} — file extension is checked by
 *       the upload layer before bytes reach this class)</li>
 *   <li>non-OOXML payloads ({@link NotOLE2FileException},
 *       {@link NotOfficeXmlFileException})</li>
 * </ul>
 */
@Component
public class ExcelParser {

    private static final DataFormatter FORMATTER = new DataFormatter();

    /**
     * Name of the informational guide sheet shipped as sheet #2 of every
     * template. Kept in lock-step with
     * {@code ImportTemplateGuide.GUIDE_SHEET_NAME}; the parser skips it so the
     * guide is never mistaken for the data sheet on upload.
     */
    static final String GUIDE_SHEET_NAME = "Йўриқнома-Инструкция";

    public ParsedSheet parse(byte[] xlsxBytes) {
        if (xlsxBytes == null || xlsxBytes.length == 0) {
            throw new ExcelParseException("EMPTY_FILE", "Workbook is empty");
        }
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            if (wb.getNumberOfSheets() == 0) {
                throw new ExcelParseException("NO_SHEETS", "Workbook contains no sheets");
            }
            Sheet sheet = selectDataSheet(wb);
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();

            // Headers from row 0
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new ExcelParseException("MISSING_HEADER", "Header row is missing");
            }
            List<String> headers = new ArrayList<>();
            for (int c = headerRow.getFirstCellNum(); c < headerRow.getLastCellNum(); c++) {
                Cell cell = headerRow.getCell(c);
                // Header text is echoed into validation error messages (which can
                // be exported as a CSV error report) — sanitise it on read too so
                // a malicious header like "=cmd" cannot become an executable
                // formula in that downstream export (Batch 5).
                String h = cell == null ? ""
                        : ImportCellSanitizer.sanitizeText(FORMATTER.formatCellValue(cell).trim());
                headers.add(h);
            }
            // duplicate column check
            for (int i = 0; i < headers.size(); i++) {
                String h = headers.get(i);
                if (h != null && !h.isEmpty() && headers.indexOf(h) != i) {
                    throw new ExcelParseException("DUPLICATE_COLUMN",
                            "Duplicate column header: " + h);
                }
            }

            List<Map<String, String>> rows = new ArrayList<>();
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Map<String, String> mapped = new LinkedHashMap<>();
                boolean allBlank = true;
                for (int c = 0; c < headers.size(); c++) {
                    String header = headers.get(c);
                    if (header == null || header.isEmpty()) continue;
                    Cell cell = row.getCell(c);
                    String value = readCell(cell, evaluator);
                    if (value != null && !value.isEmpty()) allBlank = false;
                    mapped.put(header, value);
                }
                if (!allBlank) rows.add(mapped);
            }
            return new ParsedSheet(headers, rows);
        } catch (NotOLE2FileException | NotOfficeXmlFileException e) {
            throw new ExcelParseException("NOT_XLSX",
                    "File is not a valid .xlsx workbook");
        } catch (IOException e) {
            throw new ExcelParseException("IO_ERROR", "Failed to read workbook: " + e.getClass().getSimpleName());
        }
    }

    /**
     * Resolve the DATA sheet: the first sheet that is NOT the guide sheet.
     * Falls back to sheet index 0 if every sheet looks like a guide (defensive
     * — should never happen for a platform-generated template).
     */
    private static Sheet selectDataSheet(Workbook wb) {
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            Sheet s = wb.getSheetAt(i);
            String name = s.getSheetName();
            if (name == null || !name.trim().equalsIgnoreCase(GUIDE_SHEET_NAME)) {
                return s;
            }
        }
        return wb.getSheetAt(0);
    }

    /**
     * Extract a single cell as a String — the SINGLE import-side choke point
     * where a raw POI cell becomes a domain/staging value.
     *
     * <p>Batch 5 (formula-injection hardening, import side): every cell that is
     * read as <b>text</b> is routed through {@link ImportCellSanitizer} so a
     * malicious value beginning with {@code = + - @ \t \r \n} is neutralised on
     * the way in (mirrors {@link SafeCellWriter} on the export side). NUMERIC
     * and date cells are NOT sanitised — a number parsed as a number (e.g. a
     * negative {@code -5}) is not a formula-injection vector, and prefixing it
     * would corrupt legitimate values.
     */
    private static String readCell(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) return "";
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            // Evaluate to a value — we never trust user formulas downstream.
            // A formula whose cached/evaluated result is a STRING is a text
            // cell and MUST be sanitised; a numeric/boolean result is not.
            String value;
            try {
                value = FORMATTER.formatCellValue(cell, evaluator);
            } catch (Exception e) {
                value = FORMATTER.formatCellValue(cell);
            }
            return cell.getCachedFormulaResultType() == CellType.STRING
                    ? ImportCellSanitizer.sanitizeText(value)
                    : value;
        }
        if (type == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toString();
            }
            // Pure number — formatted as a number, never a formula vector.
            return FORMATTER.formatCellValue(cell);
        }
        if (type == CellType.STRING) {
            // The only user free-text branch — sanitise on the way in.
            return ImportCellSanitizer.sanitizeText(FORMATTER.formatCellValue(cell));
        }
        // BOOLEAN / BLANK / ERROR — DataFormatter yields a fixed token
        // ("TRUE"/"FALSE"/""/"#REF!"); none can begin with a dangerous prefix,
        // but route through the sanitiser anyway so the rule has no gap.
        return ImportCellSanitizer.sanitizeText(FORMATTER.formatCellValue(cell));
    }

    public record ParsedSheet(List<String> headers, List<Map<String, String>> rows) { }
}
