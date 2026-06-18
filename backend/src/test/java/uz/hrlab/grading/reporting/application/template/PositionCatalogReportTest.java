package uz.hrlab.grading.reporting.application.template;

import org.junit.jupiter.api.Test;
import uz.hrlab.grading.integration.excel.ExcelWriter;
import uz.hrlab.grading.reporting.application.template.impl.PositionCatalogReport;
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

class PositionCatalogReportTest {

    private final PositionCatalogReport template =
            new PositionCatalogReport(new FakeReportDataPort(), new ExcelWriter());

    @Test
    void multilingualTitlesAcrossFourLocales() {
        for (String locale : List.of("ru-RU", "uz-Cyrl-UZ", "uz-Latn-UZ", "en-US")) {
            String title = ReportTitleResolver.resolve(ReportType.POSITION_CATALOG, locale);
            assertThat(title).isNotBlank();
        }
    }

    @Test
    void rendersPdfWithPositions() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        template.render(ctx(ReportFormat.PDF), out);
        byte[] bytes = out.toByteArray();
        assertThat(new String(bytes, 0, 4)).startsWith("%PDF");
        assertThat(bytes.length).isGreaterThan(100);
    }

    @Test
    void rendersDocxWithPositions() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        template.render(ctx(ReportFormat.DOCX), out);
        byte[] bytes = out.toByteArray();
        assertThat(bytes[0]).isEqualTo((byte) 0x50);
        assertThat(bytes[1]).isEqualTo((byte) 0x4B);
    }

    @Test
    void xlsxHeadersAreLocalizedAndCellsShowResolvedNamesNotCodes() {
        // en-US: localized human headers (not snake_case keys).
        byte[] en = renderXlsx(template, ctx(ReportFormat.XLSX));
        List<String> enHeaders = headerRow(en);
        assertThat(enHeaders).containsExactly(
                "Code", "Title", "Department", "Job family", "Job level", "Status");

        // Department name + status render as resolved human values from the port,
        // never a raw UUID or Java enum constant.
        List<List<String>> rows = cells(en);
        assertThat(rows).isNotEmpty();
        assertThat(rows.get(0)).contains("IT department", "Active");
        assertThat(rows.stream().flatMap(List::stream))
                .noneMatch(v -> v.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-.*")) // no UUID leak
                .doesNotContain("ACTIVE", "DRAFT"); // no raw enum leak
    }

    @Test
    void xlsxHeadersLocalizeToRussian() {
        byte[] ru = renderXlsx(template, ctxLocale("ru-RU"));
        assertThat(headerRow(ru)).containsExactly(
                "Код", "Наименование", "Подразделение",
                "Семейство должностей", "Уровень должности", "Статус");
    }

    @Test
    void xlsxGoesThroughSafeCellWriter() {
        // Smoke: rendering succeeds (SafeCellWriter has its own injection
        // test in ExcelFormulaInjectionTest); here we just confirm the
        // template plumbing through ExcelWriter produces a valid XLSX.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        template.render(ctx(ReportFormat.XLSX), out);
        byte[] bytes = out.toByteArray();
        assertThat(bytes[0]).isEqualTo((byte) 0x50);
        assertThat(bytes[1]).isEqualTo((byte) 0x4B);
        assertThat(bytes.length).isGreaterThan(500);
    }

    private ReportGenerationContext ctx(ReportFormat format) {
        return build(format, "en-US");
    }

    private ReportGenerationContext ctxLocale(String locale) {
        return build(ReportFormat.XLSX, locale);
    }

    private ReportGenerationContext build(ReportFormat format, String locale) {
        return ReportGenerationContext.builder()
                .reportId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .projectId(UUID.randomUUID())
                .reportType(ReportType.POSITION_CATALOG)
                .format(format)
                .locale(locale)
                .requestedBy(UUID.randomUUID())
                .requestedAt(OffsetDateTime.now())
                .title("Position catalog")
                .build();
    }
}
