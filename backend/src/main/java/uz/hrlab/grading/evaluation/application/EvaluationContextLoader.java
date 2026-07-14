package uz.hrlab.grading.evaluation.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.methodology.domain.Factor;
import uz.hrlab.grading.methodology.domain.FactorLevel;
import uz.hrlab.grading.methodology.domain.MethodologyVersion;
import uz.hrlab.grading.methodology.infrastructure.FactorJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Loads the {@link EvaluationContext} bundle in one place — every use case
 * goes through this loader so the tenant-aware {@code findByIdAndTenantId}
 * pattern is enforced uniformly.
 */
@Component
public class EvaluationContextLoader {

    private final EvaluationRepository evaluations;
    private final MethodologyVersionRepository versions;
    private final FactorRepository factors;
    private final FactorLevelRepository levels;

    public EvaluationContextLoader(EvaluationRepository evaluations,
                                   MethodologyVersionRepository versions,
                                   FactorRepository factors,
                                   FactorLevelRepository levels) {
        this.evaluations = evaluations;
        this.versions = versions;
        this.factors = factors;
        this.levels = levels;
    }

    public EvaluationContext load(UUID evaluationId, UUID tenantId) {
        EvaluationJpaEntity e = evaluations.findByIdAndTenantId(evaluationId, tenantId)
                .orElseThrow(TenantAccessDeniedException::new);
        MethodologyVersion version = loadVersion(e.getMethodologyVersionId(), tenantId);
        List<Factor> factorList = loadFactors(version.id(), tenantId);
        Map<UUID, List<FactorLevel>> levelsByFactor = loadLevels(factorList, tenantId);
        return new EvaluationContext(e, version, factorList, levelsByFactor);
    }

    public MethodologyVersion loadVersion(UUID versionId, UUID tenantId) {
        MethodologyVersionJpaEntity v = versions.findByIdAndTenantId(versionId, tenantId)
                .orElseThrow(TenantAccessDeniedException::new);
        return v.toDomain();
    }

    /**
     * Load ALL factors of a version, INCLUDING soft-deprecated ones (BE-4). Used
     * by {@link #load} (recompute of an EXISTING evaluation) so a deprecated
     * factor a past evaluation already scored still resolves — historical
     * preservation. For the "factors a NEW evaluation may score" path use
     * {@link #loadActiveFactors} instead.
     */
    public List<Factor> loadFactors(UUID versionId, UUID tenantId) {
        return toDomain(factors
                .findAllByTenantIdAndMethodologyVersionIdOrderBySortOrderAsc(tenantId, versionId));
    }

    /**
     * Load only ACTIVE (non-deprecated) factors of a version (BE-4) — the set a
     * NEW evaluation may score. Excludes soft-deprecated rows via the partial
     * index {@code idx_factors_active_version}.
     */
    public List<Factor> loadActiveFactors(UUID versionId, UUID tenantId) {
        return toDomain(factors
                .findAllByTenantIdAndMethodologyVersionIdAndDeprecatedAtIsNullOrderBySortOrderAsc(
                        tenantId, versionId));
    }

    private List<Factor> toDomain(List<FactorJpaEntity> rows) {
        List<Factor> out = new ArrayList<>(rows.size());
        for (FactorJpaEntity f : rows) {
            out.add(f.toDomain());
        }
        return out;
    }

    /** Load ALL levels (incl. deprecated) for the given factors — recompute path. */
    public Map<UUID, List<FactorLevel>> loadLevels(List<Factor> factorList, UUID tenantId) {
        List<UUID> factorIds = factorIds(factorList);
        // Batch every factor's levels in ONE tenant-scoped query (was one
        // findAllByTenantIdAndFactorId per factor → N+1). Global level_order ASC +
        // group-by-factorId preserves each factor's per-level order (BE-26 pattern).
        List<FactorLevelJpaEntity> rows = factorIds.isEmpty()
                ? List.of()
                : levels.findAllByTenantIdAndFactorIdInOrderByLevelOrderAsc(tenantId, factorIds);
        return groupByFactor(factorList, rows);
    }

    /** Load only ACTIVE levels for the given factors (BE-4) — new-evaluation path. */
    public Map<UUID, List<FactorLevel>> loadActiveLevels(List<Factor> factorList, UUID tenantId) {
        List<UUID> factorIds = factorIds(factorList);
        List<FactorLevelJpaEntity> rows = factorIds.isEmpty()
                ? List.of()
                : levels.findAllByTenantIdAndFactorIdInAndDeprecatedAtIsNullOrderByLevelOrderAsc(
                        tenantId, factorIds);
        return groupByFactor(factorList, rows);
    }

    private static List<UUID> factorIds(List<Factor> factorList) {
        List<UUID> ids = new ArrayList<>(factorList.size());
        for (Factor f : factorList) {
            ids.add(f.id());
        }
        return ids;
    }

    /**
     * Regroup globally {@code level_order}-ASC rows into a per-factor map keyed in
     * {@code factorList} order — a factor with no levels keeps a (mutable) empty list,
     * exactly as the old per-factor loop produced. Each group's encounter order is the
     * source query's {@code level_order} ASC, so per-factor level order is preserved.
     */
    private static Map<UUID, List<FactorLevel>> groupByFactor(
            List<Factor> factorList, List<FactorLevelJpaEntity> rows) {
        Map<UUID, List<FactorLevel>> byFactor = new HashMap<>();
        for (FactorLevelJpaEntity row : rows) {
            byFactor.computeIfAbsent(row.getFactorId(), k -> new ArrayList<>())
                    .add(row.toDomain());
        }
        Map<UUID, List<FactorLevel>> out = new LinkedHashMap<>();
        for (Factor f : factorList) {
            List<FactorLevel> domain = byFactor.get(f.id());
            out.put(f.id(), domain != null ? domain : new ArrayList<>());
        }
        return out;
    }
}
