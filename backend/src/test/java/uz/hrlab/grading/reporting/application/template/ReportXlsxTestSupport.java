package uz.hrlab.grading.reporting.application.template;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Test-only helpers that render a report template to XLSX and parse the bytes
 * back, so content assertions can verify the actual VISIBLE text (localized
 * headers, resolved names) — not just that valid OOXML bytes were produced.
 * XLSX is the one format all six reports emit and that POI can read row/cell.
 */
final class ReportXlsxTestSupport {

    private ReportXlsxTestSupport() { }

    /** Render the template to bytes for the given context's format. */
    static byte[] render(ReportTemplate template, ReportGenerationContext ctx) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        template.render(ctx, out);
        return out.toByteArray();
    }

    /** Render the template to XLSX bytes for the given context. */
    static byte[] renderXlsx(ReportTemplate template, ReportGenerationContext ctx) {
        return render(template, ctx);
    }

    /**
     * Extract the concatenated visible text of a DOCX ({@code word/document.xml}).
     * Lets a test assert that meta lines / table cells contain a resolved NAME —
     * DOCX text is uncompressed XML once the zip entry is inflated (unlike a
     * compressed PDF content stream, which is not reliably greppable).
     */
    static String docxText(byte[] docx) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(e.getName())) {
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    byte[] chunk = new byte[8192];
                    int n;
                    while ((n = zip.read(chunk)) > 0) {
                        buf.write(chunk, 0, n);
                    }
                    return buf.toString(StandardCharsets.UTF_8);
                }
            }
            return "";
        } catch (Exception ex) {
            throw new UncheckedIOException("failed to read DOCX", asIo(ex));
        }
    }

    /** Header row (sheet 0, row 0) as visible strings. */
    static List<String> headerRow(byte[] xlsx) {
        return readRow(xlsx, 0);
    }

    /** All data rows (sheet 0, rows 1..n) as visible strings. */
    static List<List<String>> cells(byte[] xlsx) {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = wb.getSheetAt(0);
            List<List<String>> rows = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                rows.add(rowToStrings(row));
            }
            return rows;
        } catch (Exception e) {
            throw new UncheckedIOException("failed to read XLSX", asIo(e));
        }
    }

    private static List<String> readRow(byte[] xlsx, int index) {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Row row = wb.getSheetAt(0).getRow(index);
            return row == null ? List.of() : rowToStrings(row);
        } catch (Exception e) {
            throw new UncheckedIOException("failed to read XLSX", asIo(e));
        }
    }

    private static List<String> rowToStrings(Row row) {
        List<String> values = new ArrayList<>();
        for (int c = 0; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            values.add(cell == null ? "" : cell.toString());
        }
        return values;
    }

    private static java.io.IOException asIo(Exception e) {
        return e instanceof java.io.IOException io ? io : new java.io.IOException(e);
    }
}
