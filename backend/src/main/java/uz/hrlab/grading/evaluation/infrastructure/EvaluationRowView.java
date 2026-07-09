package uz.hrlab.grading.evaluation.infrastructure;

import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.domain.EvaluatorRole;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DB-23 — closed read projection for the evaluation LIST/grid read paths.
 *
 * <p>The {@code evaluations} table carries a WIDE {@code methodology_basis_snapshot}
 * JSONB that the list/grid never renders. Selecting the full entity fetched that
 * JSONB on every row. This closed interface projection lists ONLY the scalar
 * columns the list envelope needs, so Spring Data restricts the SELECT to those
 * columns and the JSONB is never read on the list path. The single-evaluation /
 * detail read ({@code findByIdAndTenantId} → entity) keeps the snapshot available.
 *
 * <p>The entity intentionally does NOT implement this interface: Spring Data only
 * applies column-restricting projection when the returned interface is NOT
 * assignable from the domain type — an entity implementing it would silently
 * disable pruning and re-select the JSONB.
 */
public interface EvaluationRowView {
    UUID getId();
    UUID getProjectId();
    UUID getPositionId();
    UUID getMethodologyVersionId();
    UUID getEvaluatorUserId();
    UUID getPanelId();
    EvaluatorRole getEvaluatorRole();
    EvaluationStatus getStatus();
    BigDecimal getRawTotalScore();
    BigDecimal getDisplayedTotalScore();
    UUID getGradeBandId();
    Integer getAssignedGradeNumber();
    OffsetDateTime getSubmittedAt();
    UUID getSubmittedBy();
    OffsetDateTime getApprovedAt();
    UUID getApprovedBy();
    OffsetDateTime getLockedAt();
    UUID getLockedBy();
    OffsetDateTime getArchivedAt();
    UUID getArchivedBy();
}
