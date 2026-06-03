package uz.hrlab.grading.reporting.application.template;

import org.junit.jupiter.api.Test;
import uz.hrlab.grading.integration.excel.ExcelWriter;
import uz.hrlab.grading.reporting.application.template.impl.GradeDistributionReport;
import uz.hrlab.grading.reporting.domain.ReportFormat;
import uz.hrlab.grading.reporting.domain.ReportType;

import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
