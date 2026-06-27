package uz.hrlab.grading.evaluation.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import uz.hrlab.grading.evaluation.domain.EvaluationPanel;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Wire shape for a panel (snake_case via global strategy + NON_NULL). Server-
 * computed fields ({@code raw_total_score} / {@code displayed_total_score}) are
 * null until AVERAGED; grade fields null until APPROVED.
 *
 * <p>{@code department_label_i18n} (Departament) is the position's TOP-LEVEL
 * ancestor department name; {@code division_label_i18n} (Bo'limi) is the
 * position's OWN leaf department name when it is nested under that ancestor, and
 * is {@code null} (omitted by NON_NULL) for a top-level position. Both are
 * localized i18n maps, consistent with {@code position_title_i18n}. The split is
 * computed by {@code PanelQueries.list} reusing the same ancestor-walk the
 * evaluation report uses ({@code DefaultReportDataPort}). They are populated on
 * the LIST surface only.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PanelResponse(
        UUID id,
        UUID projectId,
        UUID positionId,
        Map<String, String> positionTitleI18n,
        Map<String, String> departmentLabelI18n,
        Map<String, String> divisionLabelI18n,
        UUID methodologyVersionId,
        EvaluationPanelStatus status,
        int minEvaluators,
        int evaluatorCount,
        int completedCount,
        BigDecimal rawTotalScore,
        BigDecimal displayedTotalScore,
        Integer assignedGradeNumber,
        UUID gradeBandId,
        OffsetDateTime averagedAt,
        OffsetDateTime submittedAt,
        OffsetDateTime approvedAt,
        OffsetDateTime createdAt
) {
    /**
     * Detail/result surface — no department split (those callers do not page a
     * roster of positions); department + division labels are left null/omitted.
     */
    public static PanelResponse from(EvaluationPanel p, Map<String, String> positionTitleI18n,
                                     int evaluatorCount, int completedCount) {
        return from(p, positionTitleI18n, null, null, evaluatorCount, completedCount);
    }

    /**
     * LIST surface — carries the Departament (top-level ancestor) and Bo'limi
     * (own leaf when nested) localized labels for the CEO table.
     */
    public static PanelResponse from(EvaluationPanel p, Map<String, String> positionTitleI18n,
                                     Map<String, String> departmentLabelI18n,
                                     Map<String, String> divisionLabelI18n,
                                     int evaluatorCount, int completedCount) {
        return new PanelResponse(
                p.id(), p.projectId(), p.positionId(), positionTitleI18n,
                departmentLabelI18n, divisionLabelI18n,
                p.methodologyVersionId(), p.status(), p.minEvaluators(),
                evaluatorCount, completedCount,
                p.rawTotalScore(), p.displayedTotalScore(),
                p.assignedGradeNumber(), p.gradeBandId(),
                p.averagedAt(), p.submittedAt(), p.approvedAt(), p.createdAt());
    }
}
