package uz.hrlab.grading.reporting.application.template;

import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.ObjectFactory;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.docx4j.wml.RPr;
import org.docx4j.wml.Tbl;
import org.docx4j.wml.Tc;
import org.docx4j.wml.Text;
import org.docx4j.wml.Tr;

import java.io.OutputStream;
import java.math.BigInteger;
import java.util.List;

/**
 * Thin docx4j builder used by report templates.
 *
 * <p>docx4j initialises a JAXBContext on first use which is slow — keep the
 * builder static-method-only so the class loads once at worker start.
 */
public final class DocxBuilder {

    private static final ObjectFactory F = new ObjectFactory();

    private DocxBuilder() { }

    public static WordprocessingMLPackage create() {
        try {
            return WordprocessingMLPackage.createPackage();
        } catch (Docx4JException e) {
            throw new ReportTemplateException("DOCX_PACKAGE_INIT_FAILED", e);
        }
    }

    public static void heading(MainDocumentPart body, String text) {
        body.addStyledParagraphOfText("Heading1", text);
    }

    public static void subheading(MainDocumentPart body, String text) {
        body.addStyledParagraphOfText("Heading2", text);
    }

    public static void paragraph(MainDocumentPart body, String text) {
        body.addParagraphOfText(text == null ? "" : text);
    }

    public static void metaLine(MainDocumentPart body, String label, String value) {
        P p = F.createP();
        R r1 = F.createR();
        RPr rPr = F.createRPr();
        rPr.setB(F.createBooleanDefaultTrue());
        r1.setRPr(rPr);
        Text t1 = F.createText();
        t1.setValue(label + ": ");
        r1.getContent().add(t1);

        R r2 = F.createR();
        Text t2 = F.createText();
        t2.setValue(value == null ? "" : value);
        r2.getContent().add(t2);

        p.getContent().add(r1);
        p.getContent().add(r2);
        body.addObject(p);
    }

    public static void table(MainDocumentPart body, List<String> headers, List<List<String>> rows) {
        Tbl tbl = F.createTbl();
        addRow(tbl, headers, true);
        for (List<String> row : rows) {
            addRow(tbl, row, false);
        }
        body.addObject(tbl);
    }

    private static void addRow(Tbl tbl, List<String> cells, boolean bold) {
        Tr tr = F.createTr();
        for (String cell : cells) {
            Tc tc = F.createTc();
            P p = F.createP();
            R r = F.createR();
            if (bold) {
                RPr rPr = F.createRPr();
                rPr.setB(F.createBooleanDefaultTrue());
                r.setRPr(rPr);
            }
            Text t = F.createText();
            t.setSpace("preserve");
            t.setValue(cell == null ? "" : cell);
            r.getContent().add(t);
            p.getContent().add(r);
            tc.getContent().add(p);
            tr.getContent().add(tc);
        }
        tbl.getContent().add(tr);
    }

    public static void write(WordprocessingMLPackage pkg, OutputStream out) {
        try {
            pkg.save(out);
        } catch (Docx4JException e) {
            throw new ReportTemplateException("DOCX_WRITE_FAILED", e);
        }
    }

    @SuppressWarnings("unused")
    private static BigInteger bi(int v) { return BigInteger.valueOf(v); }
}
