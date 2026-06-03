package uz.hrlab.grading.evaluation.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.methodology.domain.Factor;
import uz.hrlab.grading.methodology.domain.FactorLevel;
import uz.hrlab.grading.methodology.domain.MethodologyVersion;
import uz.hrlab.grading.methodology.infrastructure.FactorJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;

import java.util.ArrayList;
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

    public List<Factor> loadFactors(UUID versionId, UUID tenantId) {
        List<FactorJpaEntity> rows = factors
                .findAllByTenantIdAndMethodologyVersionIdOrderBySortOrderAsc(tenantId, versionId);
        List<Factor> out = new ArrayList<>(rows.size());
        for (FactorJpaEntity f : rows) {
            out.add(f.toDomain());
        }
        return out;
    }

    public Map<UUID, List<FactorLevel>> loadLevels(List<Factor> factorList, UUID tenantId) {
        Map<UUID, List<FactorLevel>> out = new LinkedHashMap<>();
        for (Factor f : factorList) {
            var rows = levels.findAllByTenantIdAndFactorIdOrderByLevelOrderAsc(tenantId, f.id());
            List<FactorLevel> domain = new ArrayList<>(rows.size());
            for (var row : rows) {
                domain.add(row.toDomain());
            }
            out.put(f.id(), domain);
        }
        return out;
    }
}
