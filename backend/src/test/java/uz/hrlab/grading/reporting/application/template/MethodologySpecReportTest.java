package uz.hrlab.grading.reporting.application.template;

import org.junit.jupiter.api.Test;
import uz.hrlab.grading.integration.excel.ExcelWriter;
import uz.hrlab.grading.reporting.application.template.impl.MethodologySpecReport;
import uz.hrlab.grading.reporting.domain.ReportFormat;
import uz.hrlab.grading.reporting.domain.ReportType;

import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.hrlab.grading.reporting.application.template.ReportXlsxTestSupport.cells;
import static uz.hrlab.grading.reporting.application.template.ReportXlsxTestSupport.renderXlsx;

class MethodologySpecReportTest {

    private final MethodologySpecReport template =
            new MethodologySpecReport(new FakeReportDataPort(), new ExcelWriter());

    @Test
    void rendersFullMethodologyAsPdf() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        template.render(ctx(ReportFormat.PDF), out);
        byte[] bytes = out.toByteArray();
        assertThat(new String(bytes, 0, 4)).startsWith("%PDF");
        assertThat(bytes.length).isGreaterThan(500);
    }

    @Test
    void rendersFullMethodologyAsDocx() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        template.render(ctx(ReportFormat.DOCX), out);
        byte[] bytes = out.toByteArray();
        assertThat(bytes[0]).isEqualTo((byte) 0x50);
        assertThat(bytes[1]).isEqualTo((byte) 0x4B);
    }

    @Test
    void rendersFullMethodologyAsXlsx() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        template.render(ctx(ReportFormat.XLSX), out);
        byte[] bytes = out.toByteArray();
        assertThat(bytes[0]).isEqualTo((byte) 0x50);
        assertThat(bytes[1]).isEqualTo((byte) 0x4B);
        assertThat(bytes.length).isGreaterThan(500);
    }

    @Test
    void xlsxLevelLabelColumnIsPopulatedNotBlank() {
        // Delta 7 regression: the level-label column must read the "label" key the
        // port writes — it was reading "title" and rendering blank.
        byte[] xlsx = renderXlsx(template, ctx(ReportFormat.XLSX));
        List<List<String>> rows = cells(xlsx);
        assertThat(rows).isNotEmpty();
        // Columns: factor_code(0) factor_title(1) weight(2) max_points(3)
        //          scoring_mode(4) level_code(5) level_title/label(6) ...
        assertThat(rows.get(0).get(6)).isEqualTo("Basic");
        assertThat(rows).allSatisfy(r -> assertThat(r.get(6)).isNotBlank());
        // Methodology name is the real localized name, not a UUID/"methodology vN".
        assertThat(rows.get(0).get(1)).isEqualTo("Knowledge");
        assertThat(rows.stream().flatMap(List::stream))
                .noneMatch(v -> v.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-.*"));
    }

    private ReportGenerationContext ctx(ReportFormat format) {
        return ReportGenerationContext.builder()
                .reportId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .projectId(UUID.randomUUID())
                .reportType(ReportType.METHODOLOGY_SPEC)
                .format(format)
                .locale("ru-RU")
                .requestedBy(UUID.randomUUID())
                .requestedAt(OffsetDateTime.now())
                .title("Methodology spec")
                .build();
    }
}
