package uz.hrlab.grading.reporting.application.template.impl;

import com.lowagie.text.Document;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.springframework.stereotype.Component;
import uz.hrlab.grading.integration.excel.ExcelWriter;
import uz.hrlab.grading.reporting.application.template.AbstractReportTemplate;
import uz.hrlab.grading.reporting.application.template.DocxBuilder;
import uz.hrlab.grading.reporting.application.template.PdfBuilder;
import uz.hrlab.grading.reporting.application.template.ReportDataPort;
import uz.hrlab.grading.reporting.application.template.ReportGenerationContext;
import uz.hrlab.grading.reporting.application.template.ReportLabels;
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
 * 6-column table; XLSX exposes the full forensic columns (with correlation id)
 * so the file can be re-imported into SIEM tooling.
 *
 * <p>The actor column is a resolved display name (or a privacy-safe truncated
 * id) — never a raw UUID. Meta lines show the project / tenant NAME (not the
 * UUID). XLSX timestamps stay ISO-8601 for SIEM ingestion; PDF/DOCX meta lines
 * are localized.
 *
 * <p>Limit: latest {@value #MAX_EVENTS} events newest-first.
 */
@Component
public class AuditSummaryReport
        extends AbstractReportTemplate<AuditSummaryReport.Model> {

    private static final int MAX_EVENTS = 200;
    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final ReportDataPort data;
    private final ExcelWriter excel;

    public AuditSummaryReport(ReportDataPort data, ExcelWriter excel) {
        this.data = data;
        this.excel = excel;
    }

    @Override public ReportType reportType() { return ReportType.AUDIT_SUMMARY; }

    /** Carries the events plus resolved project / tenant names for the meta lines. */
    record Model(String projectName, String tenantName, List<ReportDataPort.AuditEventRow> rows) { }

    @Override
    protected Model loadData(ReportGenerationContext ctx) {
        return new Model(
                data.projectName(ctx.tenantId(), ctx.projectId(), ctx.locale()),
                data.tenantName(ctx.tenantId(), ctx.locale()),
                data.loadAuditEvents(ctx.tenantId(), ctx.projectId(), null, null, MAX_EVENTS));
    }

    private static List<String> headers(String locale) {
        return List.of(
                ReportLabels.label("col.timestamp", locale),
                ReportLabels.label("col.action", locale),
                ReportLabels.label("col.actor", locale),
                ReportLabels.label("col.entityType", locale),
                ReportLabels.label("col.entityId", locale),
                ReportLabels.label("col.reason", locale));
    }

    @Override
    protected void renderPdf(ReportGenerationContext ctx, OutputStream out, Model model) {
        String locale = ctx.locale();
        List<ReportDataPort.AuditEventRow> rows = model.rows();
        Document doc = PdfBuilder.open(out);
        try {
            PdfBuilder.heading(doc, ctx.title());
            PdfBuilder.metaLine(doc, ReportLabels.label("meta.project", locale), nz(model.projectName()));
            PdfBuilder.metaLine(doc, ReportLabels.label("meta.tenant", locale), nz(model.tenantName()));
            PdfBuilder.metaLine(doc, ReportLabels.label("meta.totalEvents", locale),
                    String.valueOf(rows.size()));
            PdfBuilder.metaLine(doc, ReportLabels.label("meta.period", locale),
                    rows.isEmpty() ? "—" :
                            ReportLabels.formatTimestamp(rows.get(rows.size() - 1).timestamp(), locale)
                                    + " → "
                                    + ReportLabels.formatTimestamp(rows.get(0).timestamp(), locale));
            if (rows.isEmpty()) {
                PdfBuilder.paragraph(doc, ReportLabels.label("empty.audit", locale));
            } else {
                List<List<String>> tbl = new ArrayList<>();
                for (ReportDataPort.AuditEventRow r : rows) {
                    tbl.add(humanRow(r, locale));
                }
                PdfBuilder.table(doc, headers(locale), tbl);
            }
            PdfBuilder.footer(doc, OffsetDateTime.now(), null);
        } finally {
            PdfBuilder.close(doc);
        }
    }

    @Override
    protected void renderDocx(ReportGenerationContext ctx, OutputStream out, Model model) {
        String locale = ctx.locale();
        List<ReportDataPort.AuditEventRow> rows = model.rows();
        WordprocessingMLPackage pkg = DocxBuilder.create();
        DocxBuilder.heading(pkg.getMainDocumentPart(), ctx.title());
        DocxBuilder.metaLine(pkg.getMainDocumentPart(),
                ReportLabels.label("meta.project", locale), nz(model.projectName()));
        DocxBuilder.metaLine(pkg.getMainDocumentPart(),
                ReportLabels.label("meta.tenant", locale), nz(model.tenantName()));
        DocxBuilder.metaLine(pkg.getMainDocumentPart(),
                ReportLabels.label("meta.totalEvents", locale), String.valueOf(rows.size()));
        if (rows.isEmpty()) {
            DocxBuilder.paragraph(pkg.getMainDocumentPart(),
                    ReportLabels.label("empty.audit", locale));
        } else {
            List<List<String>> body = new ArrayList<>();
            for (ReportDataPort.AuditEventRow r : rows) {
                body.add(humanRow(r, locale));
            }
            DocxBuilder.table(pkg.getMainDocumentPart(), headers(locale), body);
        }
        DocxBuilder.write(pkg, out);
    }

    @Override
    protected void renderXlsx(ReportGenerationContext ctx, OutputStream out, Model model) {
        String locale = ctx.locale();
        // SIEM-facing sheet: keep ISO timestamps + stable machine column keys;
        // only the displayed header text is localized.
        List<String> displayHeaders = List.of(
                ReportLabels.label("col.timestamp", locale),
                ReportLabels.label("col.action", locale),
                ReportLabels.label("col.actor", locale),
                ReportLabels.label("col.entityType", locale),
                ReportLabels.label("col.entityId", locale),
                ReportLabels.label("col.reason", locale),
                "correlation_id");
        List<String> dataKeys = List.of("timestamp", "action", "actor", "entity_type",
                "entity_id", "reason", "correlation_id");
        List<Map<String, String>> dataRows = new ArrayList<>(model.rows().size());
        for (ReportDataPort.AuditEventRow r : model.rows()) {
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
        writeXlsx(out, excel.write("AuditSummary", displayHeaders, dataKeys, dataRows));
    }

    /**
     * Human PDF/DOCX row — locale-formatted timestamp and localized action
     * label (never raw ISO / forensic code). The actor is already a resolved
     * display name from the port; entityId stays the internal reference.
     */
    private static List<String> humanRow(ReportDataPort.AuditEventRow r, String locale) {
        return List.of(
                ReportLabels.formatTimestamp(r.timestamp(), locale),
                ReportLabels.localizeAction(nz(r.action()), locale),
                nz(r.actorDisplay()),
                nz(r.entityType()),
                nz(r.entityId()),
                nz(r.reason()));
    }

    /** ISO-8601 — XLSX SIEM sheet only (machine-stable for re-ingestion). */
    private static String fmt(OffsetDateTime ts) {
        return ts == null ? "" : TS.format(ts);
    }
}
