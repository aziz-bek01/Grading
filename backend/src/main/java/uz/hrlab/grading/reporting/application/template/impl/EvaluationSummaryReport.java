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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Evaluation summary — one row per position with factor scores, total, and the
 * assigned grade. PDF and DOCX render a condensed canonical table
 * (Position | Department | Status | Total | Grade); XLSX expands to every factor
 * column for downstream reconciliation. Column order matches {@code
 * EvaluationMatrix#factors} so cross-format diffs stay stable.
 *
 * <p>WIRE NAMING: the XLSX factor-column DATA-ROW lookup key stays the factor
 * code; only the human HEADER label is localized ({@code name + " (" + code +
 * ")"}). Status, grade, methodology, and department are localized in the port.
 *
 * <p>Salary fields are not part of EVALUATION_SUMMARY by design — this report
 * is exposed to evaluators (no salary permission). When MVP 3 introduces an
 * EVALUATION_COMPENSATION report, salary masking will live there and honor
 * {@code SALARY_VIEW}.
 */
@Component
public class EvaluationSummaryReport
        extends AbstractReportTemplate<ReportDataPort.EvaluationMatrix> {

    private final ReportDataPort data;
    private final ExcelWriter excel;
    private final ObjectMapper objectMapper;

    public EvaluationSummaryReport(ReportDataPort data, ExcelWriter excel, ObjectMapper objectMapper) {
        this.data = data;
        this.excel = excel;
        this.objectMapper = objectMapper;
    }

    @Override public ReportType reportType() { return ReportType.EVALUATION_SUMMARY; }

    @Override
    protected ReportDataPort.EvaluationMatrix loadData(ReportGenerationContext ctx) {
        EvaluationReportFilter filter =
                EvaluationReportFilter.parse(ctx.filterParams(), objectMapper);
        return data.loadEvaluations(ctx.tenantId(), ctx.projectId(), ctx.locale(), filter);
    }

    private static List<String> condensedHeaders(String locale) {
        return List.of(
                ReportLabels.label("col.positionCode", locale),
                ReportLabels.label("col.position", locale),
                ReportLabels.label("col.department", locale),
                ReportLabels.label("col.status", locale),
                ReportLabels.label("col.total", locale),
                ReportLabels.label("col.grade", locale));
    }

    @Override
    protected void renderPdf(ReportGenerationContext ctx, OutputStream out,
                             ReportDataPort.EvaluationMatrix matrix) {
        String locale = ctx.locale();
        Document doc = PdfBuilder.open(out);
        try {
            PdfBuilder.heading(doc, ctx.title());
            PdfBuilder.metaLine(doc, ReportLabels.label("meta.project", locale), nz(matrix.projectName()));
            PdfBuilder.metaLine(doc, ReportLabels.label("meta.methodology", locale),
                    nz(matrix.methodologyName()));
            PdfBuilder.metaLine(doc, ReportLabels.label("meta.totalPositions", locale),
                    String.valueOf(matrix.totalPositions()));
            PdfBuilder.metaLine(doc, ReportLabels.label("meta.approved", locale),
                    String.valueOf(matrix.approvedCount()));
            ReportDataPort.FilterEcho filters = matrix.activeFilters();
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
            if (matrix.rows().isEmpty()) {
                PdfBuilder.paragraph(doc, ReportLabels.label("empty.evaluations", locale));
            } else {
                List<List<String>> tbl = new ArrayList<>();
                for (ReportDataPort.EvaluationRow r : matrix.rows()) {
                    tbl.add(List.of(
                            nz(r.positionCode()),
                            nz(r.positionTitle()),
                            nz(r.departmentName()),
                            nz(r.status()),
                            nz(r.totalScore()),
                            nz(r.gradeName())));
                }
                PdfBuilder.table(doc, condensedHeaders(locale), tbl);
            }
            PdfBuilder.footer(doc, OffsetDateTime.now(), null);
        } finally {
            PdfBuilder.close(doc);
        }
    }

    @Override
    protected void renderDocx(ReportGenerationContext ctx, OutputStream out,
                              ReportDataPort.EvaluationMatrix matrix) {
        String locale = ctx.locale();
        WordprocessingMLPackage pkg = DocxBuilder.create();
        DocxBuilder.heading(pkg.getMainDocumentPart(), ctx.title());
        DocxBuilder.metaLine(pkg.getMainDocumentPart(),
                ReportLabels.label("meta.project", locale), nz(matrix.projectName()));
        DocxBuilder.metaLine(pkg.getMainDocumentPart(),
                ReportLabels.label("meta.methodology", locale), nz(matrix.methodologyName()));
        DocxBuilder.metaLine(pkg.getMainDocumentPart(),
                ReportLabels.label("meta.totalPositions", locale),
                String.valueOf(matrix.totalPositions()));
        DocxBuilder.metaLine(pkg.getMainDocumentPart(),
                ReportLabels.label("meta.approved", locale),
                String.valueOf(matrix.approvedCount()));
        ReportDataPort.FilterEcho filters = matrix.activeFilters();
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
        if (matrix.rows().isEmpty()) {
            DocxBuilder.paragraph(pkg.getMainDocumentPart(),
                    ReportLabels.label("empty.evaluations", locale));
        } else {
            List<List<String>> body = new ArrayList<>();
            for (ReportDataPort.EvaluationRow r : matrix.rows()) {
                body.add(List.of(
                        nz(r.positionCode()),
                        nz(r.positionTitle()),
                        nz(r.departmentName()),
                        nz(r.status()),
                        nz(r.totalScore()),
                        nz(r.gradeName())));
            }
            DocxBuilder.table(pkg.getMainDocumentPart(), condensedHeaders(locale), body);
        }
        DocxBuilder.write(pkg, out);
    }

    /**
     * Grading XLSX — one row per evaluator, then a highlighted AVERAGE row per
     * panel. Column order (left→right): Position code · Departament (top-level
     * ancestor) · Bo'limi (own leaf dept when nested, else blank) · Position
     * title · Evaluator FIO · Evaluator role · Methodology · Status · Evaluation
     * date · factor columns (union of methodology factors) · Total · Grade code ·
     * Grade name. This is a RENDER-SIDE transform over the already-filtered row
     * set ({@link ReportDataPort.EvaluationMatrix#rows()}); the port carries full
     * data and the matrix supplies the green average rows
     * ({@link ReportDataPort.EvaluationMatrix#panelAverages()}). The condensed
     * PDF/DOCX path above is UNCHANGED.
     */
    @Override
    protected void renderXlsx(ReportGenerationContext ctx, OutputStream out,
                              ReportDataPort.EvaluationMatrix matrix) {
        String locale = ctx.locale();
        // dataKeys stay machine-stable (factor code keyed); displayHeaders are the
        // localized human labels ("name (CODE)" for factors).
        List<String> displayHeaders = new ArrayList<>();
        List<String> dataKeys = new ArrayList<>();
        addColumn(displayHeaders, dataKeys, ReportLabels.label("col.positionCode", locale), "position_code");
        addColumn(displayHeaders, dataKeys, ReportLabels.label("col.department", locale), "department");
        addColumn(displayHeaders, dataKeys, ReportLabels.label("col.division", locale), "division");
        addColumn(displayHeaders, dataKeys, ReportLabels.label("col.position", locale), "position_title");
        addColumn(displayHeaders, dataKeys, ReportLabels.label("col.evaluator", locale), "evaluator");
        addColumn(displayHeaders, dataKeys, ReportLabels.label("col.evaluatorRole", locale), "evaluator_role");
        addColumn(displayHeaders, dataKeys, ReportLabels.label("col.methodology", locale), "methodology");
        addColumn(displayHeaders, dataKeys, ReportLabels.label("col.status", locale), "status");
        addColumn(displayHeaders, dataKeys, ReportLabels.label("col.evaluationDate", locale), "evaluation_date");
        for (ReportDataPort.FactorRef f : matrix.factors()) {
            addColumn(displayHeaders, dataKeys, nz(f.name()) + " (" + f.code() + ")", "factor_" + f.code());
        }
        addColumn(displayHeaders, dataKeys, ReportLabels.label("col.total", locale), "total_score");
        addColumn(displayHeaders, dataKeys, ReportLabels.label("col.grade", locale), "grade_code");
        addColumn(displayHeaders, dataKeys, ReportLabels.label("col.gradeName", locale), "grade_name");

        // Group evaluator rows by panelId; null-panel rows are standalone (no
        // average). Sort group order by position code ASC; within a panel, by the
        // backing-port order (already EvaluatorRole ordinal then evaluator name
        // ASC from DefaultReportDataPort). Standalone rows interleave by position
        // code so the whole sheet reads position-code ASC.
        Map<UUID, ReportDataPort.PanelAverageRow> avgByPanel = new LinkedHashMap<>();
        for (ReportDataPort.PanelAverageRow a : matrix.panelAverages()) {
            avgByPanel.put(a.panelId(), a);
        }

        List<RenderRow> ordered = orderRows(matrix.rows());

        List<Map<String, String>> dataRows = new ArrayList<>();
        List<ExcelWriter.RowStyle> rowStyles = new ArrayList<>();
        UUID renderedAvgFor = null;
        for (int i = 0; i < ordered.size(); i++) {
            RenderRow rr = ordered.get(i);
            ReportDataPort.EvaluationRow r = rr.row();
            dataRows.add(evaluatorRow(matrix, r));
            rowStyles.add(ExcelWriter.RowStyle.NORMAL);

            // Emit the panel AVERAGE row immediately AFTER the last evaluator row of
            // its group (group boundary = next row has a different panel id, or end).
            UUID panelId = r.panelId();
            boolean lastOfGroup = panelId != null
                    && (i + 1 == ordered.size() || !panelId.equals(ordered.get(i + 1).row().panelId()));
            if (panelId != null && lastOfGroup && !panelId.equals(renderedAvgFor)) {
                ReportDataPort.PanelAverageRow avg = avgByPanel.get(panelId);
                if (avg != null) {
                    dataRows.add(averageRow(matrix, avg, locale));
                    rowStyles.add(ExcelWriter.RowStyle.AVERAGE_HIGHLIGHT);
                    renderedAvgFor = panelId;
                }
            }
        }

        writeXlsx(out, excel.writeWithRowStyles(
                "EvaluationSummary", displayHeaders, dataKeys, dataRows, rowStyles));
    }

    private static void addColumn(List<String> headers, List<String> keys, String header, String key) {
        headers.add(header);
        keys.add(key);
    }

    /** One evaluator data row keyed by the stable XLSX column keys. */
    private static Map<String, String> evaluatorRow(ReportDataPort.EvaluationMatrix matrix,
                                                    ReportDataPort.EvaluationRow r) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("position_code", nz(r.positionCode()));
        m.put("department", nz(r.departmentName()));
        m.put("division", nz(r.divisionName()));
        m.put("position_title", nz(r.positionTitle()));
        m.put("evaluator", nz(r.evaluatorName()));
        m.put("evaluator_role", nz(r.evaluatorRole()));
        m.put("methodology", nz(r.methodologyVersionLabel()));
        m.put("status", nz(r.status()));
        m.put("evaluation_date", nz(r.evaluationDate()));
        Map<String, String> scoresByCode = r.scoresByFactorCode();
        for (ReportDataPort.FactorRef f : matrix.factors()) {
            String v = scoresByCode == null ? null : scoresByCode.get(f.code());
            m.put("factor_" + f.code(), v == null ? "" : v);
        }
        m.put("total_score", nz(r.totalScore()));
        m.put("grade_code", nz(r.gradeCode()));
        m.put("grade_name", nz(r.gradeName()));
        return m;
    }

    /**
     * The green AVERAGE row: cols 1-4 blank; FIO column carries the localized
     * "average" label; role blank; methodology + status + date + factor averages
     * + total from the panel; grade only when the panel is APPROVED/LOCKED.
     */
    private static Map<String, String> averageRow(ReportDataPort.EvaluationMatrix matrix,
                                                  ReportDataPort.PanelAverageRow avg, String locale) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("position_code", "");
        m.put("department", "");
        m.put("division", "");
        m.put("position_title", "");
        m.put("evaluator", ReportLabels.label("row.panelAverage", locale));
        m.put("evaluator_role", "");
        m.put("methodology", nz(avg.methodologyVersionLabel()));
        m.put("status", nz(avg.status()));
        m.put("evaluation_date", nz(avg.evaluationDate()));
        Map<String, String> scoresByCode = avg.avgScoresByFactorCode();
        for (ReportDataPort.FactorRef f : matrix.factors()) {
            String v = scoresByCode == null ? null : scoresByCode.get(f.code());
            m.put("factor_" + f.code(), v == null ? "" : v);
        }
        m.put("total_score", nz(avg.totalScore()));
        m.put("grade_code", nz(avg.gradeCode()));
        m.put("grade_name", nz(avg.gradeName()));
        return m;
    }

    /**
     * Order the filtered rows so the sheet reads position-code ASC, with each
     * panel's evaluator rows kept CONTIGUOUS (so the average row lands directly
     * after them). A group's sort key is the MIN position code among its members
     * (rows of one panel share a position, so this is stable). Within a group the
     * port's order is preserved (EvaluatorRole ordinal then evaluator name ASC).
     * Standalone (null-panel) rows are their own singleton groups, interleaved by
     * the same position-code key.
     */
    private static List<RenderRow> orderRows(List<ReportDataPort.EvaluationRow> rows) {
        List<List<ReportDataPort.EvaluationRow>> groups = new ArrayList<>();
        Map<UUID, List<ReportDataPort.EvaluationRow>> byPanel = new LinkedHashMap<>();
        for (ReportDataPort.EvaluationRow r : rows) {
            if (r.panelId() == null) {
                groups.add(List.of(r));
            } else {
                byPanel.computeIfAbsent(r.panelId(), k -> new ArrayList<>()).add(r);
            }
        }
        groups.addAll(byPanel.values());
        // Stable sort by the group's representative position code (MIN), keeping
        // insertion order for equal keys (Collections.sort is stable).
        groups.sort(Comparator.comparing(EvaluationSummaryReport::groupPositionCode,
                Comparator.nullsLast(Comparator.naturalOrder())));

        List<RenderRow> out = new ArrayList<>();
        for (List<ReportDataPort.EvaluationRow> g : groups) {
            for (ReportDataPort.EvaluationRow r : g) {
                out.add(new RenderRow(r));
            }
        }
        return out;
    }

    private static String groupPositionCode(List<ReportDataPort.EvaluationRow> group) {
        String min = null;
        for (ReportDataPort.EvaluationRow r : group) {
            String c = nz(r.positionCode());
            if (min == null || c.compareTo(min) < 0) min = c;
        }
        return min;
    }

    /** Tiny holder so the render loop can look ahead at panel-id group boundaries. */
    private record RenderRow(ReportDataPort.EvaluationRow row) { }
}
