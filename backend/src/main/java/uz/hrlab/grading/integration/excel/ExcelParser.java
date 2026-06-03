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
 * Apache POI XSSF parser that reads the first sheet of an .xlsx file and
 * yields one {@code Map<String,String>} per data row, keyed by header.
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

    public ParsedSheet parse(byte[] xlsxBytes) {
        if (xlsxBytes == null || xlsxBytes.length == 0) {
            throw new ExcelParseException("EMPTY_FILE", "Workbook is empty");
        }
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            if (wb.getNumberOfSheets() == 0) {
                throw new ExcelParseException("NO_SHEETS", "Workbook contains no sheets");
            }
            Sheet sheet = wb.getSheetAt(0);
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();

            // Headers from row 0
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new ExcelParseException("MISSING_HEADER", "Header row is missing");
            }
            List<String> headers = new ArrayList<>();
            for (int c = headerRow.getFirstCellNum(); c < headerRow.getLastCellNum(); c++) {
                Cell cell = headerRow.getCell(c);
                String h = cell == null ? "" : FORMATTER.formatCellValue(cell).trim();
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

    private static String readCell(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) return "";
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            // Evaluate to a value — we never trust user formulas downstream.
            try {
                return FORMATTER.formatCellValue(cell, evaluator);
            } catch (Exception e) {
                return FORMATTER.formatCellValue(cell);
            }
        }
        if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toString();
        }
        return FORMATTER.formatCellValue(cell);
    }

    public record ParsedSheet(List<String> headers, List<Map<String, String>> rows) { }
}
