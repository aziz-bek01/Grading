package uz.hrlab.grading.evaluation.infrastructure;

import uz.hrlab.grading.evaluation.domain.EvaluationStatus;

import java.util.UUID;

/**
 * DB-23 — closed read projection for the by-factor K-sheet grid
 * ({@code EvaluationQueries.listByFactor}). The grid row only needs the evaluation
 * id, its position, its methodology version and its status; the position/department
 * metadata and the per-factor score are joined separately. Selecting the full
 * entity dragged the wide {@code methodology_basis_snapshot} JSONB onto every grid
 * row for nothing — this projection restricts the SELECT to the four scalar columns
 * the grid actually uses.
 *
 * <p>As with {@link EvaluationRowView}, the entity intentionally does NOT implement
 * this interface so Spring Data keeps the column-restricting projection.
 */
public interface EvaluationGridView {
    UUID getId();
    UUID getPositionId();
    UUID getMethodologyVersionId();
    EvaluationStatus getStatus();
}
