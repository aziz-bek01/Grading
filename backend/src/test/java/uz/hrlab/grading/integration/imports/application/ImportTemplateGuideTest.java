package uz.hrlab.grading.integration.imports.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uz.hrlab.grading.integration.excel.GuideSheet;
import uz.hrlab.grading.integration.imports.domain.ImportTemplateCode;
import uz.hrlab.grading.organization.domain.DepartmentType;
import uz.hrlab.grading.position.domain.PositionStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the bilingual guide sheet against drift from the REAL importer rules.
 *
 * <p>If a committer changes an enum or a required column, these assertions
 * fail — forcing the guide to be updated in lock-step (the whole point of
 * deriving the guidance from the importer instead of inventing it).
 */
class ImportTemplateGuideTest {

    private final ImportTemplateGuide guide = new ImportTemplateGuide();

    @ParameterizedTest
    @ValueSource(strings = {
            ImportTemplateCode.ORG_STRUCTURE_V1,
            ImportTemplateCode.POSITION_CATALOG_V1,
            ImportTemplateCode.JOB_PROFILE_V1,
            ImportTemplateCode.METHODOLOGY_FACTORS_V1,
            ImportTemplateCode.GRADE_BANDS_V1,
    })
    void every_template_has_a_guide_with_title_general_notes_and_column_table(String code) {
        GuideSheet g = guide.buildFor(code, "DataSheet");
        assertThat(g).as(code).isNotNull();
        assertThat(g.sheetName()).isEqualTo(ImportTemplateGuide.GUIDE_SHEET_NAME);
        assertThat(g.rows()).as(code).isNotEmpty();
        String text = flatten(g);
        // Title.
        assertThat(text).contains("ЙЎРИҚНОМА").contains("ИНСТРУКЦИЯ");
        // General notes mention the data-sheet name and decimal/date rules.
        assertThat(text).contains("DataSheet");
        assertThat(text).contains("YYYY-MM-DD");
        // Bilingual marker — both languages present.
        assertThat(text).contains("Да").contains("Ҳа");
    }

    @Test
    void unknown_template_yields_null_guide() {
        assertThat(guide.buildFor("NOPE_V1", "X")).isNull();
    }

    @Test
    void org_guide_lists_exactly_the_department_type_enum_codes() {
        String text = flatten(guide.buildFor(ImportTemplateCode.ORG_STRUCTURE_V1, "Departments"));
        // The guide must enumerate the ACTUAL DepartmentType values the
        // committer accepts — no more, no less.
        for (DepartmentType t : DepartmentType.values()) {
            assertThat(text).as("DepartmentType." + t).contains(t.name());
        }
    }

    @Test
    void position_guide_lists_exactly_the_position_status_enum_codes() {
        String text = flatten(guide.buildFor(ImportTemplateCode.POSITION_CATALOG_V1, "Positions"));
        for (PositionStatus s : PositionStatus.values()) {
            assertThat(text).as("PositionStatus." + s).contains(s.name());
        }
    }

    @Test
    void grade_guide_warns_grade_code_is_a_number_not_letter_code() {
        String text = flatten(guide.buildFor(ImportTemplateCode.GRADE_BANDS_V1, "Grades"));
        // GradeBandsRowCommitter#parseGradeNumber requires a positive integer.
        assertThat(text).contains("grade_code");
        assertThat(text).contains("INVALID_GRADE_NUMBER");
        assertThat(text).contains("min_score").contains("max_score");
    }

    @Test
    void org_guide_documents_required_and_optional_columns() {
        String text = flatten(guide.buildFor(ImportTemplateCode.ORG_STRUCTURE_V1, "Departments"));
        // Required (committer req()).
        assertThat(text).contains("external_id").contains("name");
        // Optional / cross-reference.
        assertThat(text).contains("parent_external_id").contains("MISSING_PARENT");
    }

    @Test
    void position_guide_documents_department_cross_reference_rule() {
        String text = flatten(guide.buildFor(ImportTemplateCode.POSITION_CATALOG_V1, "Positions"));
        assertThat(text).contains("department_external_id").contains("MISSING_DEPARTMENT");
    }

    private static String flatten(GuideSheet g) {
        StringBuilder sb = new StringBuilder();
        for (GuideSheet.GuideRow row : g.rows()) {
            for (String cell : row.cells()) {
                sb.append(cell).append('\n');
            }
        }
        return sb.toString();
    }
}
