package uz.hrlab.grading.evaluation.infrastructure;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Dynamic, PostgreSQL-safe filter for the evaluation report finder. Each optional
 * dimension (methodology versions, evaluators, submitted-date range) is added as a
 * predicate ONLY when present, so a nullable value is NEVER bound into a
 * {@code ? IS NULL} / {@code IN (?)} position.
 *
 * <p>The previous static {@code @Query} used the {@code (:param IS NULL OR ...)}
 * idiom; against PostgreSQL an untyped {@code NULL} bind there fails at runtime with
 * <em>"could not determine data type of parameter"</em>, so EVERY report request
 * with an empty/absent dimension errored. Building predicates dynamically avoids the
 * whole class of problem (no null binds at all).
 *
 * <p>Tenant + project scope are always applied (tenant isolation, defense-in-depth
 * on top of RLS). Submitted-date bounds are inclusive; a {@code NULL submittedAt} is
 * naturally excluded when a date bound is active and included when it is not. Single
 * source of truth for the report finder — used by both
 * {@code DefaultReportDataPort.loadEvaluations} and {@code loadExecutiveKpi}.
 */
public final class EvaluationReportSpecifications {

    private EvaluationReportSpecifications() {
    }

    public static Specification<EvaluationJpaEntity> forReport(
            UUID tenantId,
            UUID projectId,
            Collection<UUID> methodologyVersionIds,
            Collection<UUID> evaluatorUserIds,
            OffsetDateTime dateFrom,
            OffsetDateTime dateTo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            predicates.add(cb.equal(root.get("projectId"), projectId));
            if (methodologyVersionIds != null && !methodologyVersionIds.isEmpty()) {
                predicates.add(root.get("methodologyVersionId").in(methodologyVersionIds));
            }
            if (evaluatorUserIds != null && !evaluatorUserIds.isEmpty()) {
                predicates.add(root.get("evaluatorUserId").in(evaluatorUserIds));
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.<OffsetDateTime>get("submittedAt"), dateFrom));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.<OffsetDateTime>get("submittedAt"), dateTo));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
