package uz.hrlab.grading.reporting.application.template.impl;

import com.lowagie.text.Document;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.springframework.stereotype.Component;
import uz.hrlab.grading.integration.excel.ExcelWriter;
import uz.hrlab.grading.reporting.application.template.DocxBuilder;
import uz.hrlab.grading.reporting.application.template.PdfBuilder;
import uz.hrlab.grading.reporting.application.template.ReportDataPort;
import uz.hrlab.grading.reporting.application.template.ReportGenerationContext;
import uz.hrlab.grading.reporting.application.template.ReportTemplate;
import uz.hrlab.grading.reporting.application.template.ReportTemplateException;
import uz.hrlab.grading.reporting.domain.ReportFormat;
import uz.hrlab.grading.reporting.domain.ReportType;

import java.io.OutputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Audit summary — last N tenant audit events. PDF + DOCX show a condensed
 * 6-column table; XLSX exposes the full forensic columns (with correlation id
 * and trace id-like fields available) so the file can be re-imported into
 * SIEM tooling.
 *
 * <p>Limit: latest {@value #MAX_EVENTS} events newest-first.
 */
@Component
public class AuditSummaryReport implements ReportTemplate {

    private static final int MAX_EVENTS = 200;
    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final ReportDataPort data;
    private final ExcelWriter excel;

    public AuditSummaryReport(ReportDataPort data, ExcelWriter excel) {
        this.data = data;
        this.excel = excel;
    }

    @Override public ReportType reportType() { return ReportType.AUDIT_SUMMARY; }

    @Override public boolean supports(ReportFormat format) {
        return format == ReportFormat.PDF
                || format == ReportFormat.DOCX
                || format == ReportFormat.XLSX;
    }

    @Override
    public void render(ReportGenerationContext ctx, OutputStream out) {
        List<ReportDataPort.AuditEventRow> rows =
                data.loadAuditEvents(ctx.tenantId(), ctx.projectId(),
                        null, null, MAX_EVENTS);
        switch (ctx.format()) {
            case PDF -> renderPdf(ctx, out, rows);
            case DOCX -> renderDocx(ctx, out, rows);
            case XLSX -> renderXlsx(out, rows);
            default -> throw new ReportTemplateException("UNSUPPORTED_FORMAT: " + ctx.format());
        }
    }

    private static final List<String> HEADERS =
            List.of("Timestamp", "Action", "Actor", "Entity type", "Entity id", "Reason");

    private void renderPdf(ReportGenerationContext ctx, OutputStream out,
                           List<ReportDataPort.AuditEventRow> rows) {
        Document doc = PdfBuilder.open(out);
        try {
            PdfBuilder.heading(doc, ctx.title());
            PdfBuilder.metaLine(doc, "Project", ctx.projectId() == null
                    ? "" : ctx.projectId().toString());
            PdfBuilder.metaLine(doc, "Tenant", ctx.tenantId() == null
                    ? "" : ctx.tenantId().toString());
            PdfBuilder.metaLine(doc, "Total events", String.valueOf(rows.size()));
            PdfBuilder.metaLine(doc, "Period",
                    rows.isEmpty() ? "—" :
                            fmt(rows.get(rows.size() - 1).timestamp()) + " → "
                                    + fmt(rows.get(0).timestamp()));
            List<List<String>> tbl = new ArrayList<>();
            for (ReportDataPort.AuditEventRow r : rows) {
                tbl.add(List.of(
                        fmt(r.timestamp()),
                        nz(r.action()),
                        nz(r.actorDisplay()),
                        nz(r.entityType()),
                        nz(r.entityId()),
                        nz(r.reason())));
            }
            PdfBuilder.table(doc, HEADERS, tbl);
            PdfBuilder.footer(doc, OffsetDateTime.now(), null);
        } finally {
            PdfBuilder.close(doc);
        }
    }

    private void renderDocx(ReportGenerationContext ctx, OutputStream out,
                            List<ReportDataPort.AuditEventRow> rows) {
        WordprocessingMLPackage pkg = DocxBuilder.create();
        DocxBuilder.heading(pkg.getMainDocumentPart(), ctx.title());
        DocxBuilder.metaLine(pkg.getMainDocumentPart(), "Project",
                ctx.projectId() == null ? "" : ctx.projectId().toString());
        DocxBuilder.metaLine(pkg.getMainDocumentPart(), "Tenant",
                ctx.tenantId() == null ? "" : ctx.tenantId().toString());
        DocxBuilder.metaLine(pkg.getMainDocumentPart(), "Total events",
                String.valueOf(rows.size()));
        List<List<String>> body = new ArrayList<>();
        for (ReportDataPort.AuditEventRow r : rows) {
            body.add(List.of(
                    fmt(r.timestamp()),
                    nz(r.action()),
                    nz(r.actorDisplay()),
                    nz(r.entityType()),
                    nz(r.entityId()),
                    nz(r.reason())));
        }
        DocxBuilder.table(pkg.getMainDocumentPart(), HEADERS, body);
        DocxBuilder.write(pkg, out);
    }

    private void renderXlsx(OutputStream out, List<ReportDataPort.AuditEventRow> rows) {
        List<String> cols = List.of("timestamp", "action", "actor", "entity_type",
                "entity_id", "reason", "correlation_id");
        List<Map<String, String>> dataRows = new ArrayList<>(rows.size());
        for (ReportDataPort.AuditEventRow r : rows) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("timestamp", fmt(r.timestamp()));
            m.put("action", nz(r.action()));
            m.put("actor", nz(r.actorDisplay()));
            m.put("entity_type", nz(r.entityType()));
            m.put("entity_id", nz(r.entityId()));
            m.put("reason", nz(r.reason()));
            m.put("correlation_id", nz(r.correlationId()));
            dataRows.add(m);
        }
        byte[] bytes = excel.write("AuditSummary", cols, dataRows);
        try {
            out.write(bytes);
        } catch (java.io.IOException e) {
            throw new ReportTemplateException("XLSX_WRITE_FAILED", e);
        }
    }

    private static String fmt(OffsetDateTime ts) {
        return ts == null ? "" : TS.format(ts);
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
