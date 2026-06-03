package uz.hrlab.grading.integration.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
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

            wb.write(out);
            if (wb instanceof SXSSFWorkbook s) {
                s.dispose();
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
