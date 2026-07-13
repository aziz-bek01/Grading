package uz.hrlab.grading.integration.docs;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.io.OutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Thin OpenPDF builder used by report templates. Centralised so report look &
 * feel stays consistent and we can swap implementations (e.g. for JasperReports
 * .jrxml output) without touching templates.
 */
public final class PdfBuilder {

    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private PdfBuilder() { }

    public static Document open(OutputStream out) {
        Document doc = new Document(PageSize.A4, 36, 36, 48, 36);
        PdfWriter.getInstance(doc, out);
        doc.open();
        return doc;
    }

    public static void heading(Document doc, String text) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        Paragraph p = new Paragraph(text, font);
        p.setSpacingAfter(8f);
        doc.add(p);
    }

    public static void subheading(Document doc, String text) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
        doc.add(new Paragraph(text, font));
    }

    public static void paragraph(Document doc, String text) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA, 10);
        doc.add(new Paragraph(text, font));
    }

    public static void metaLine(Document doc, String label, String value) {
        Font lb = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
        Font vl = FontFactory.getFont(FontFactory.HELVETICA, 9);
        Paragraph p = new Paragraph();
        p.add(new Chunk(label + ": ", lb));
        p.add(new Chunk(value == null ? "" : value, vl));
        doc.add(p);
    }

    public static void table(Document doc, List<String> headers, List<List<String>> rows) {
        PdfPTable table = new PdfPTable(headers.size());
        table.setWidthPercentage(100f);
        table.setSpacingBefore(8f);
        Font th = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
        Font td = FontFactory.getFont(FontFactory.HELVETICA, 9);
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, th));
            c.setBorder(Rectangle.BOTTOM);
            c.setPaddingBottom(4f);
            table.addCell(c);
        }
        for (List<String> row : rows) {
            for (int i = 0; i < headers.size(); i++) {
                String v = i < row.size() ? row.get(i) : "";
                PdfPCell c = new PdfPCell(new Phrase(v == null ? "" : v, td));
                c.setBorder(Rectangle.BOTTOM);
                c.setPaddingBottom(3f);
                table.addCell(c);
            }
        }
        doc.add(table);
    }

    /** Simple horizontal bar chart drawn with table rows — no external chart lib. */
    public static void barChart(Document doc, List<String> labels, List<Integer> values) {
        int max = values.stream().mapToInt(Integer::intValue).max().orElse(1);
        if (max == 0) max = 1;
        PdfPTable table = new PdfPTable(new float[]{2f, 5f, 1f});
        table.setWidthPercentage(100f);
        table.setSpacingBefore(8f);
        Font lb = FontFactory.getFont(FontFactory.HELVETICA, 9);
        for (int i = 0; i < labels.size(); i++) {
            PdfPCell label = new PdfPCell(new Phrase(labels.get(i), lb));
            label.setBorder(Rectangle.NO_BORDER);
            table.addCell(label);

            int width = (int) Math.round(100.0 * values.get(i) / max);
            String bar = "█".repeat(Math.max(1, width / 4));
            PdfPCell barCell = new PdfPCell(new Phrase(bar, lb));
            barCell.setBorder(Rectangle.NO_BORDER);
            table.addCell(barCell);

            PdfPCell val = new PdfPCell(new Phrase(String.valueOf(values.get(i)), lb));
            val.setBorder(Rectangle.NO_BORDER);
            table.addCell(val);
        }
        doc.add(table);
    }

    public static void footer(Document doc, java.time.OffsetDateTime generatedAt, String traceId) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8);
        Paragraph p = new Paragraph();
        p.setSpacingBefore(16f);
        p.add(new Chunk("Generated at " + TS.format(generatedAt)
                + (traceId == null ? "" : "  •  trace=" + traceId), f));
        doc.add(p);
    }

    public static void close(Document doc) {
        if (doc.isOpen()) doc.close();
    }
}
