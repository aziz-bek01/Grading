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
import uz.hrlab.grading.reporting.domain.ReportType;

import java.io.OutputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executive summary — single-page KPI snapshot for sponsors. PDF is the
 * primary deliverable (printable, branded). DOCX/XLSX exist so the request
 * lifecycle stays format-agnostic and downstream consumers (e.g. an HR analyst
 * dropping numbers into a spreadsheet) keep their workflow.
 *
 * <p>Numbers come from {@link ReportDataPort#loadExecutiveKpi(java.util.UUID, java.util.UUID)};
 * the template does no aggregation itself.
 *
 * <p>Salary KPIs are intentionally absent — this report is sponsor-facing and
 * exposed without {@code SALARY_VIEW}. A future EXECUTIVE_COMPENSATION report
 * will live behind a separate permission.
 */
@Component
public class ExecutiveSummaryReport
        extends AbstractReportTemplate<ReportDataPort.ExecutiveKpi> {

    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final ReportDataPort data;
    private final ExcelWriter excel;

    public ExecutiveSummaryReport(ReportDataPort data, ExcelWriter excel) {
        this.data = data;
        this.excel = excel;
    }

    @Override public ReportType reportType() { return ReportType.EXECUTIVE_SUMMARY; }

    @Override
    protected ReportDataPort.ExecutiveKpi loadData(ReportGenerationContext ctx) {
        return data.loadExecutiveKpi(ctx.tenantId(), ctx.projectId());
    }

    private static final List<String> RECENT_HEADERS =
            List.of("Timestamp", "Action", "Entity type", "Entity id", "Actor");

    @Override
    protected void renderPdf(ReportGenerationContext ctx, OutputStream out,
                             ReportDataPort.ExecutiveKpi kpi) {
        Document doc = PdfBuilder.open(out);
        try {
            // Hero
            PdfBuilder.heading(doc, ctx.title());
            PdfBuilder.metaLine(doc, "Project", nz(kpi.projectName()));
            PdfBuilder.metaLine(doc, "Tenant", ctx.tenantId() == null
                    ? "" : ctx.tenantId().toString());
            PdfBuilder.metaLine(doc, "Status", nz(kpi.projectStatus()));
            PdfBuilder.metaLine(doc, "Period",
                    nz(kpi.periodFrom()) + " → " + nz(kpi.periodTo()));

            // KPI cards (rendered as a 2-column key/value table for printability)
            int approvedPct = approvalPercent(kpi);
            PdfBuilder.subheading(doc, "Key indicators");
            List<List<String>> cards = List.of(
                    List.of("Positions", String.valueOf(kpi.positionCount())),
                    List.of("Evaluations",
                            kpi.evaluatedCount() + " (" + approvedPct + "% approved)"),
                    List.of("Approved grades", String.valueOf(kpi.gradeCount())),
                    List.of("Audit events", String.valueOf(kpi.auditEventCount())));
            PdfBuilder.table(doc, List.of("Indicator", "Value"), cards);

            // Recent approvals (top 5)
            PdfBuilder.subheading(doc, "Recent approvals");
            List<List<String>> recent = new ArrayList<>();
            for (ReportDataPort.RecentApprovalRow r : kpi.recentApprovals()) {
                recent.add(List.of(
                        fmt(r.timestamp()),
                        nz(r.action()),
                        nz(r.entityType()),
                        nz(r.entityId()),
                        nz(r.actorDisplay())));
            }
            if (recent.isEmpty()) {
                PdfBuilder.paragraph(doc, "No approvals recorded for this project yet.");
            } else {
                PdfBuilder.table(doc, RECENT_HEADERS, recent);
            }

            PdfBuilder.footer(doc, OffsetDateTime.now(), "HRLab grading platform");
        } finally {
            PdfBuilder.close(doc);
        }
    }

    @Override
    protected void renderDocx(ReportGenerationContext ctx, OutputStream out,
                              ReportDataPort.ExecutiveKpi kpi) {
        WordprocessingMLPackage pkg = DocxBuilder.create();
        DocxBuilder.heading(pkg.getMainDocumentPart(), ctx.title());
        DocxBuilder.metaLine(pkg.getMainDocumentPart(), "Project", nz(kpi.projectName()));
        DocxBuilder.metaLine(pkg.getMainDocumentPart(), "Tenant",
                ctx.tenantId() == null ? "" : ctx.tenantId().toString());
        DocxBuilder.metaLine(pkg.getMainDocumentPart(), "Status", nz(kpi.projectStatus()));
        DocxBuilder.metaLine(pkg.getMainDocumentPart(), "Period",
                nz(kpi.periodFrom()) + " → " + nz(kpi.periodTo()));

        DocxBuilder.subheading(pkg.getMainDocumentPart(), "Key indicators");
        int approvedPct = approvalPercent(kpi);
        DocxBuilder.metaLine(pkg.getMainDocumentPart(), "Positions",
                String.valueOf(kpi.positionCount()));
        DocxBuilder.metaLine(pkg.getMainDocumentPart(), "Evaluations",
                kpi.evaluatedCount() + " (" + approvedPct + "% approved)");
        DocxBuilder.metaLine(pkg.getMainDocumentPart(), "Approved grades",
                String.valueOf(kpi.gradeCount()));
        DocxBuilder.metaLine(pkg.getMainDocumentPart(), "Audit events",
                String.valueOf(kpi.auditEventCount()));

        DocxBuilder.subheading(pkg.getMainDocumentPart(), "Recent approvals");
        if (kpi.recentApprovals() == null || kpi.recentApprovals().isEmpty()) {
            DocxBuilder.paragraph(pkg.getMainDocumentPart(),
                    "No approvals recorded for this project yet.");
        } else {
            List<List<String>> recent = new ArrayList<>();
            for (ReportDataPort.RecentApprovalRow r : kpi.recentApprovals()) {
                recent.add(List.of(
                        fmt(r.timestamp()),
                        nz(r.action()),
                        nz(r.entityType()),
                        nz(r.entityId()),
                        nz(r.actorDisplay())));
            }
            DocxBuilder.table(pkg.getMainDocumentPart(), RECENT_HEADERS, recent);
        }

        DocxBuilder.write(pkg, out);
    }

    @Override
    protected void renderXlsx(ReportGenerationContext ctx, OutputStream out,
                              ReportDataPort.ExecutiveKpi kpi) {
        // Flat KPI table — sponsors can paste numbers straight into dashboards.
        List<String> cols = List.of("indicator", "value");
        int approvedPct = approvalPercent(kpi);
        List<Map<String, String>> rows = new ArrayList<>();
        rows.add(row("project_name", nz(kpi.projectName())));
        rows.add(row("project_status", nz(kpi.projectStatus())));
        rows.add(row("period_from", nz(kpi.periodFrom())));
        rows.add(row("period_to", nz(kpi.periodTo())));
        rows.add(row("positions", String.valueOf(kpi.positionCount())));
        rows.add(row("evaluations", String.valueOf(kpi.evaluatedCount())));
        rows.add(row("evaluations_approved", String.valueOf(kpi.approvedEvaluationCount())));
        rows.add(row("evaluations_approved_pct", approvedPct + "%"));
        rows.add(row("grades", String.valueOf(kpi.gradeCount())));
        rows.add(row("audit_events", String.valueOf(kpi.auditEventCount())));
        writeXlsx(out, excel.write("ExecutiveSummary", cols, rows));
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

    private static String fmt(OffsetDateTime ts) {
        return ts == null ? "" : TS.format(ts);
    }
}
