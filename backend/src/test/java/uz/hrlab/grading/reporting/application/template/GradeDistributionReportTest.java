package uz.hrlab.grading.reporting.application.template;

import org.junit.jupiter.api.Test;
import uz.hrlab.grading.integration.excel.ExcelWriter;
import uz.hrlab.grading.reporting.application.template.impl.GradeDistributionReport;
import uz.hrlab.grading.reporting.domain.ReportFormat;
import uz.hrlab.grading.reporting.domain.ReportType;

import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.hrlab.grading.reporting.application.template.ReportXlsxTestSupport.cells;
import static uz.hrlab.grading.reporting.application.template.ReportXlsxTestSupport.headerRow;
import static uz.hrlab.grading.reporting.application.template.ReportXlsxTestSupport.renderXlsx;

class GradeDistributionReportTest {

    private final GradeDistributionReport template =
            new GradeDistributionReport(new FakeReportDataPort(), new ExcelWriter());

    @Test
    void typeAndFormats() {
        assertThat(template.reportType()).isEqualTo(ReportType.GRADE_DISTRIBUTION);
        assertThat(template.supports(ReportFormat.PDF)).isTrue();
        assertThat(template.supports(ReportFormat.DOCX)).isTrue();
        assertThat(template.supports(ReportFormat.XLSX)).isTrue();
    }

    @Test
    void rendersValidPdfBytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        template.render(ctx(ReportFormat.PDF), out);
        byte[] bytes = out.toByteArray();
        assertThat(bytes.length).isGreaterThan(0);
        // PDF magic header
        assertThat(new String(bytes, 0, Math.min(4, bytes.length))).startsWith("%PDF");
    }

    @Test
    void rendersValidDocxBytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        template.render(ctx(ReportFormat.DOCX), out);
        byte[] bytes = out.toByteArray();
        assertThat(bytes.length).isGreaterThan(0);
        // DOCX = ZIP — magic "PK"
        assertThat(bytes[0]).isEqualTo((byte) 0x50);
        assertThat(bytes[1]).isEqualTo((byte) 0x4B);
    }

    @Test
    void rendersValidXlsxBytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        template.render(ctx(ReportFormat.XLSX), out);
        byte[] bytes = out.toByteArray();
        assertThat(bytes.length).isGreaterThan(0);
        assertThat(bytes[0]).isEqualTo((byte) 0x50);
        assertThat(bytes[1]).isEqualTo((byte) 0x4B);
    }

    @Test
    void xlsxHasGradeNameColumnPopulatedWithResolvedNames() {
        byte[] xlsx = renderXlsx(template, ctx(ReportFormat.XLSX));
        // Localized headers (en-US) — grade NAME is its own column.
        assertThat(headerRow(xlsx)).containsExactly("Grade", "Grade name", "Positions");
        List<List<String>> rows = cells(xlsx);
        assertThat(rows).isNotEmpty();
        // Column 1 = grade NAME (human), populated — not blank, not the G<n> code.
        assertThat(rows.get(0).get(1)).isEqualTo("Operational");
        assertThat(rows).allSatisfy(r -> assertThat(r.get(1)).isNotBlank());
    }

    private ReportGenerationContext ctx(ReportFormat format) {
        return ReportGenerationContext.builder()
                .reportId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .projectId(UUID.randomUUID())
                .reportType(ReportType.GRADE_DISTRIBUTION)
                .format(format)
                .locale("en-US")
                .requestedBy(UUID.randomUUID())
                .requestedAt(OffsetDateTime.now())
                .title("Grade distribution")
                .build();
    }
}
