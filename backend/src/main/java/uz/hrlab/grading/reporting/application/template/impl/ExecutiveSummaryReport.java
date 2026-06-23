package uz.hrlab.grading.reporting.application.template.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.springframework.stereotype.Component;
import uz.hrlab.grading.integration.excel.ExcelWriter;
import uz.hrlab.grading.reporting.application.template.AbstractReportTemplate;
import uz.hrlab.grading.reporting.application.template.DocxBuilder;
import uz.hrlab.grading.reporting.application.template.EvaluationReportFilter;
import uz.hrlab.grading.reporting.application.template.PdfBuilder;
import uz.hrlab.grading.reporting.application.template.ReportDataPort;
import uz.hrlab.grading.reporting.application.template.ReportGenerationContext;
import uz.hrlab.grading.reporting.application.template.ReportLabels;
import uz.hrlab.grading.reporting.domain.ReportType;

import java.io.OutputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executive summary — single-page KPI snapshot for sponsors. PDF is the
 * primary deliverable (printable, branded). DOCX/XLSX exist so the request
 * lifecycle stays format-agnostic.
 *
 * <p>All static labels, the project status, the tenant NAME (not UUID), the
 * recent-approval action labels, and the XLSX column-A indicator labels (was
 * the snake_case key) are localized. Numbers come from {@link
 * ReportDataPort#loadExecutiveKpi}; the template does no aggregation itself.
 *
 * <p>Salary KPIs are intentionally absent — this report is sponsor-facing and
 * exposed without {@code SALARY_VIEW}.
 */
@Component
public class ExecutiveSummaryReport
        extends AbstractReportTemplate<ExecutiveSummaryReport.Model> {

    private final ReportDataPort data;
    private final ExcelWriter excel;
    private final ObjectMapper objectMapper;

    public ExecutiveSummaryReport(ReportDataPort data, ExcelWriter excel, ObjectMapper objectMapper) {
        this.data = data;
        this.excel = excel;
        this.objectMapper = objectMapper;
    }

    @Override public ReportType reportType() { return ReportType.EXECUTIVE_SUMMARY; }

    /** Carries the KPI plus the resolved tenant name so the meta line avoids the UUID. */
    record Model(String tenantName, ReportDataPort.ExecutiveKpi kpi) { }

    @Override
    protected Model loadData(ReportGenerationContext ctx) {
        // REUSE the EVALUATION_SUMMARY filter parser — same JSON wire shape; the
        // executive evaluation KPIs are then scoped to the filtered set in the port.
        EvaluationReportFilter filter =
                EvaluationReportFilter.parse(ctx.filterParams(), objectMapper);
        return new Model(
                data.tenantName(ctx.tenantId(), ctx.locale()),
                data.loadExecutiveKpi(ctx.tenantId(), ctx.projectId(), ctx.locale(), filter));
    }

    private static List<String> recentHeaders(String locale) {
        return List.of(
                ReportLabels.label("col.timestamp", locale),
                ReportLabels.label("col.action", locale),
                ReportLabels.label("col.entityType", locale),
                ReportLabels.label("col.entityId", locale),
                ReportLabels.label("col.actor", locale));
    }

    @Override
    protected void renderPdf(ReportGenerationContext ctx, OutputStream out, Model model) {
        String locale = ctx.locale();
        ReportDataPort.ExecutiveKpi kpi = model.kpi();
        Document doc = PdfBuilder.open(out);
        try {
            PdfBuilder.heading(doc, ctx.title());
            PdfBuilder.metaLine(doc, ReportLabels.label("meta.project", locale), nz(kpi.projectName()));
            PdfBuilder.metaLine(doc, ReportLabels.label("meta.tenant", locale), nz(model.tenantName()));
            PdfBuilder.metaLine(doc, ReportLabels.label("meta.status", locale), nz(kpi.projectStatus()));
            PdfBuilder.metaLine(doc, ReportLabels.label("meta.period", locale),
                    nz(kpi.periodFrom()) + " → " + nz(kpi.periodTo()));

            // Applied evaluation-filter echo (REUSE FilterEcho + ReportLabels keys
            // shared with EVALUATION_SUMMARY) — only when the report was filtered.
            ReportDataPort.FilterEcho filters = kpi.activeFilters();
            if (filters.hasMethodologies()) {
                PdfBuilder.metaLine(doc, ReportLabels.label("meta.filterMethodologies", locale),
                        filters.methodologies());
            }
            if (filters.hasPeriod()) {
                PdfBuilder.metaLine(doc, ReportLabels.label("meta.filterPeriod", locale),
                        filters.period());
            }
            if (filters.hasEvaluators()) {
                PdfBuilder.metaLine(doc, ReportLabels.label("meta.filterEvaluators", locale),
                        filters.evaluators());
            }

            int approvedPct = approvalPercent(kpi);
            String approvedSuffix = ReportLabels.label("kpi.approvedSuffix", locale);
            PdfBuilder.subheading(doc, ReportLabels.label("section.keyIndicators", locale));
            // TODO(reports-p2): add weight summaries + chart-percent KPI cards.
            List<List<String>> cards = List.of(
                    List.of(ReportLabels.label("kpi.positions", locale),
                            String.valueOf(kpi.positionCount())),
                    List.of(ReportLabels.label("kpi.evaluations", locale),
                            kpi.evaluatedCount() + " (" + approvedPct + "% " + approvedSuffix + ")"),
                    List.of(ReportLabels.label("kpi.grades", locale),
                            String.valueOf(kpi.gradeCount())),
                    List.of(ReportLabels.label("kpi.auditEvents", locale),
                            String.valueOf(kpi.auditEventCount())));
            PdfBuilder.table(doc, List.of(
                    ReportLabels.label("col.indicator", locale),
                    ReportLabels.label("col.value", locale)), cards);

            PdfBuilder.subheading(doc, ReportLabels.label("section.recentApprovals", locale));
            if (kpi.recentApprovals() == null || kpi.recentApprovals().isEmpty()) {
                PdfBuilder.paragraph(doc, ReportLabels.label("empty.recentApprovals", locale));
            } else {
                List<List<String>> recent = new ArrayList<>();
                for (ReportDataPort.RecentApprovalRow r : kpi.recentApprovals()) {
                    recent.add(recentRow(r, locale));
                }
                PdfBuilder.table(doc, recentHeaders(locale), recent);
            }

            PdfBuilder.footer(doc, OffsetDateTime.now(), "HRLab grading platform");
        } finally {
            PdfBuilder.close(doc);
        }
    }

    @Override
    protected void renderDocx(ReportGenerationContext ctx, OutputStream out, Model model) {
        String locale = ctx.locale();
        ReportDataPort.ExecutiveKpi kpi = model.kpi();
        WordprocessingMLPackage pkg = DocxBuilder.create();
        DocxBuilder.heading(pkg.getMainDocumentPart(), ctx.title());
        DocxBuilder.metaLine(pkg.getMainDocumentPart(),
                ReportLabels.label("meta.project", locale), nz(kpi.projectName()));
        DocxBuilder.metaLine(pkg.getMainDocumentPart(),
                ReportLabels.label("meta.tenant", locale), nz(model.tenantName()));
        DocxBuilder.metaLine(pkg.getMainDocumentPart(),
                ReportLabels.label("meta.status", locale), nz(kpi.projectStatus()));
        DocxBuilder.metaLine(pkg.getMainDocumentPart(),
                ReportLabels.label("meta.period", locale),
                nz(kpi.periodFrom()) + " → " + nz(kpi.periodTo()));

        // Applied evaluation-filter echo (REUSE FilterEcho + ReportLabels keys
        // shared with EVALUATION_SUMMARY) — only when the report was filtered.
        ReportDataPort.FilterEcho filters = kpi.activeFilters();
        if (filters.hasMethodologies()) {
            DocxBuilder.metaLine(pkg.getMainDocumentPart(),
                    ReportLabels.label("meta.filterMethodologies", locale), filters.methodologies());
        }
        if (filters.hasPeriod()) {
            DocxBuilder.metaLine(pkg.getMainDocumentPart(),
                    ReportLabels.label("meta.filterPeriod", locale), filters.period());
        }
        if (filters.hasEvaluators()) {
            DocxBuilder.metaLine(pkg.getMainDocumentPart(),
                    ReportLabels.label("meta.filterEvaluators", locale), filters.evaluators());
        }

        DocxBuilder.subheading(pkg.getMainDocumentPart(),
                ReportLabels.label("section.keyIndicators", locale));
        int approvedPct = approvalPercent(kpi);
        String approvedSuffix = ReportLabels.label("kpi.approvedSuffix", locale);
        DocxBuilder.metaLine(pkg.getMainDocumentPart(),
                ReportLabels.label("kpi.positions", locale), String.valueOf(kpi.positionCount()));
        DocxBuilder.metaLine(pkg.getMainDocumentPart(),
                ReportLabels.label("kpi.evaluations", locale),
                kpi.evaluatedCount() + " (" + approvedPct + "% " + approvedSuffix + ")");
        DocxBuilder.metaLine(pkg.getMainDocumentPart(),
                ReportLabels.label("kpi.grades", locale), String.valueOf(kpi.gradeCount()));
        DocxBuilder.metaLine(pkg.getMainDocumentPart(),
                ReportLabels.label("kpi.auditEvents", locale), String.valueOf(kpi.auditEventCount()));

        DocxBuilder.subheading(pkg.getMainDocumentPart(),
                ReportLabels.label("section.recentApprovals", locale));
        if (kpi.recentApprovals() == null || kpi.recentApprovals().isEmpty()) {
            DocxBuilder.paragraph(pkg.getMainDocumentPart(),
                    ReportLabels.label("empty.recentApprovals", locale));
        } else {
            List<List<String>> recent = new ArrayList<>();
            for (ReportDataPort.RecentApprovalRow r : kpi.recentApprovals()) {
                recent.add(recentRow(r, locale));
            }
            DocxBuilder.table(pkg.getMainDocumentPart(), recentHeaders(locale), recent);
        }

        DocxBuilder.write(pkg, out);
    }

    @Override
    protected void renderXlsx(ReportGenerationContext ctx, OutputStream out, Model model) {
        String locale = ctx.locale();
        ReportDataPort.ExecutiveKpi kpi = model.kpi();
        // Column A is the LOCALIZED indicator label (was the snake_case key);
        // column B the value.
        List<String> displayHeaders = List.of(
                ReportLabels.label("col.indicator", locale),
                ReportLabels.label("col.value", locale));
        List<String> dataKeys = List.of("indicator", "value");
        int approvedPct = approvalPercent(kpi);
        List<Map<String, String>> rows = new ArrayList<>();
        rows.add(row(ReportLabels.label("kpi.projectName", locale), nz(kpi.projectName())));
        rows.add(row(ReportLabels.label("kpi.projectStatus", locale), nz(kpi.projectStatus())));
        rows.add(row(ReportLabels.label("kpi.periodFrom", locale), nz(kpi.periodFrom())));
        rows.add(row(ReportLabels.label("kpi.periodTo", locale), nz(kpi.periodTo())));
        rows.add(row(ReportLabels.label("kpi.positions", locale), String.valueOf(kpi.positionCount())));
        rows.add(row(ReportLabels.label("kpi.evaluations", locale), String.valueOf(kpi.evaluatedCount())));
        rows.add(row(ReportLabels.label("kpi.evaluationsApproved", locale),
                String.valueOf(kpi.approvedEvaluationCount())));
        rows.add(row(ReportLabels.label("kpi.evaluationsApprovedPct", locale), approvedPct + "%"));
        rows.add(row(ReportLabels.label("kpi.grades", locale), String.valueOf(kpi.gradeCount())));
        rows.add(row(ReportLabels.label("kpi.auditEvents", locale), String.valueOf(kpi.auditEventCount())));
        writeXlsx(out, excel.write("ExecutiveSummary", displayHeaders, dataKeys, rows));
    }

    private static List<String> recentRow(ReportDataPort.RecentApprovalRow r, String locale) {
        // Human PDF/DOCX surface: locale-formatted timestamp + localized action.
        // entityId stays the UUID (internal reference) but the column header is
        // labeled as such; action is localized.
        // TODO(reports-p2): resolve entityId UUID → entity code/name when a
        // reverse-lookup port exists; until then it remains the internal id.
        return List.of(
                ReportLabels.formatTimestamp(r.timestamp(), locale),
                ReportLabels.localizeAction(nz(r.action()), locale),
                nz(r.entityType()),
                nz(r.entityId()),
                nz(r.actorDisplay()));
    }

    private static Map<String, String> row(String indicator, String value) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("indicator", indicator);
        m.put("value", value);
        return m;
    }

    private static int approvalPercent(ReportDataPort.ExecutiveKpi kpi) {
        if (kpi.evaluatedCount() <= 0) return 0;
        return (int) Math.round(100.0 * kpi.approvedEvaluationCount() / kpi.evaluatedCount());
    }
}
