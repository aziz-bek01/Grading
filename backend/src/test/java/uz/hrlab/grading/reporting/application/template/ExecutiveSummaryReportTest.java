package uz.hrlab.grading.reporting.application.template;

import org.junit.jupiter.api.Test;
import uz.hrlab.grading.integration.excel.ExcelWriter;
import uz.hrlab.grading.reporting.application.template.impl.ExecutiveSummaryReport;
import uz.hrlab.grading.reporting.domain.ReportFormat;
import uz.hrlab.grading.reporting.domain.ReportType;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.hrlab.grading.reporting.application.template.ReportXlsxTestSupport.docxText;
import static uz.hrlab.grading.reporting.application.template.ReportXlsxTestSupport.render;

/**
 * EXECUTIVE_SUMMARY now consumes the SAME structured evaluation filter as
 * EVALUATION_SUMMARY. These tests prove: (a) with no filter the rendered meta
 * block is unchanged (no Period/Evaluators/Methodologies lines — regression),
 * and (b) with a non-empty filter the localized filter meta lines render,
 * mirroring EvaluationSummaryReport.
 */
class ExecutiveSummaryReportTest {

    private final ExecutiveSummaryReport template =
            new ExecutiveSummaryReport(new FakeReportDataPort(), new ExcelWriter(),
                    new com.fasterxml.jackson.databind.ObjectMapper());

    @Test
    void typeAndFormats() {
        assertThat(template.reportType()).isEqualTo(ReportType.EXECUTIVE_SUMMARY);
        assertThat(template.supports(ReportFormat.PDF)).isTrue();
        assertThat(template.supports(ReportFormat.DOCX)).isTrue();
        assertThat(template.supports(ReportFormat.XLSX)).isTrue();
    }

    @Test
    void docxShowsBaseMetaAndOmitsFilterLinesWhenNoFilter() {
        String text = docxText(render(template, ctx(ReportFormat.DOCX, "en-US", null)));
        // Base meta still renders (regression — unchanged behaviour).
        assertThat(text).contains("Fixture project");
        assertThat(text).contains("Fixture company-client");
        // No applied filter ⇒ no filter meta lines.
        assertThat(text).doesNotContain("Evaluators", "Methodologies");
    }

    @Test
    void docxRendersLocalizedFilterMetaLinesWhenFilterApplied() {
        ReportGenerationContext ctx = ctx(ReportFormat.DOCX, "en-US",
                "{\"date_from\":\"2026-04-01\",\"date_to\":\"2026-06-30\","
                        + "\"evaluator_user_ids\":[\"" + UUID.randomUUID() + "\"]}");

        String text = docxText(render(template, ctx));
        assertThat(text).contains("Period", "2026-04-01 – 2026-06-30");
        assertThat(text).contains("Evaluators", "Aliyev A.");
        assertThat(text).contains("Methodologies");
    }

    @Test
    void pdfRendersWithFilterWithoutError() {
        // PDF is the primary executive deliverable — render with a filter must not
        // throw (the meta-line branch executes for both formats).
        byte[] pdf = render(template, ctx(ReportFormat.PDF, "ru-RU",
                "{\"methodology_version_ids\":[\"" + UUID.randomUUID() + "\"]}"));
        assertThat(pdf).isNotEmpty();
    }

    private ReportGenerationContext ctx(ReportFormat format, String locale, String filterParams) {
        return ReportGenerationContext.builder()
                .reportId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .projectId(UUID.randomUUID())
                .reportType(ReportType.EXECUTIVE_SUMMARY)
                .format(format)
                .locale(locale)
                .filterParams(filterParams)
                .requestedBy(UUID.randomUUID())
                .requestedAt(OffsetDateTime.now())
                .title("Executive summary")
                .build();
    }
}
