package uz.hrlab.grading.evaluation.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.methodology.domain.Factor;
import uz.hrlab.grading.methodology.domain.FactorLevel;
import uz.hrlab.grading.methodology.infrastructure.FactorJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * N+1 regression lock for {@link EvaluationContextLoader#loadLevels} /
 * {@link EvaluationContextLoader#loadActiveLevels}. Proves each loads every factor's
 * levels with ONE tenant-scoped batch query
 * ({@code findAllByTenantIdAndFactorIdIn...OrderByLevelOrderAsc}) instead of a
 * per-factor {@code findAllByTenantIdAndFactorIdOrderByLevelOrderAsc} (the former
 * N+1), and that regrouping keeps each factor keyed in list order with its levels in
 * {@code level_order ASC} — a level-less factor keeping an empty list. Seeds 5 factors
 * and asserts the level query count stays 1 (does not scale with the factor count).
 * Pure Mockito (no Docker) — mirrors {@code ApproveMethodologyVersionUseCaseQueryCountsTest}.
 */
@ExtendWith(MockitoExtension.class)
class EvaluationContextLoaderQueryCountsTest {

    @Mock EvaluationRepository evaluations;
    @Mock MethodologyVersionRepository versions;
    @Mock FactorRepository factors;
    @Mock FactorLevelRepository levels;

    private final UUID tenantId = UUID.randomUUID();

    private EvaluationContextLoader loader() {
        return new EvaluationContextLoader(evaluations, versions, factors, levels);
    }

    private FactorJpaEntity factor(UUID versionId, String code, int sortOrder) {
        return new FactorJpaEntity(UUID.randomUUID(), tenantId, versionId, code,
                new BigDecimal("20.0000"), new BigDecimal("100.0000"), sortOrder, true);
    }

    private FactorLevelJpaEntity level(UUID factorId, String code, int order) {
        return new FactorLevelJpaEntity(UUID.randomUUID(), tenantId, factorId, code, order,
                new BigDecimal("10"), null);
    }

    @Test
    void loadLevelsIsOneBatchQueryRegardlessOfFactorCount() {
        UUID versionId = UUID.randomUUID();
        List<Factor> factorList = new ArrayList<>();
        List<FactorLevelJpaEntity> rows = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            FactorJpaEntity f = factor(versionId, "F" + i, i + 1);
            factorList.add(f.toDomain());
            // Global level_order ASC: L2 then L1 across the seed, so grouping must
            // re-sort per factor by encounter order (the DB returns them ASC).
            if (i < 4) { // last factor is level-less → must yield an empty list.
                rows.add(level(f.getId(), "L1", 1));
                rows.add(level(f.getId(), "L2", 2));
            }
        }
        given(levels.findAllByTenantIdAndFactorIdInOrderByLevelOrderAsc(eq(tenantId), any()))
                .willReturn(rows);

        Map<UUID, List<FactorLevel>> out = loader().loadLevels(factorList, tenantId);

        // Exactly ONE batch query, ZERO per-factor queries — bounded by factor count.
        verify(levels, times(1)).findAllByTenantIdAndFactorIdInOrderByLevelOrderAsc(eq(tenantId), any());
        verify(levels, never()).findAllByTenantIdAndFactorIdOrderByLevelOrderAsc(any(), any());

        // Key order == factorList order; per-factor levels in level_order ASC; empty last.
        assertThat(out.keySet()).containsExactlyElementsOf(factorList.stream().map(Factor::id).toList());
        for (int i = 0; i < 4; i++) {
            List<FactorLevel> lv = out.get(factorList.get(i).id());
            assertThat(lv).extracting(FactorLevel::code).containsExactly("L1", "L2");
        }
        assertThat(out.get(factorList.get(4).id())).isEmpty();
    }

    @Test
    void loadActiveLevelsUsesActiveBatchFinderOnce() {
        UUID versionId = UUID.randomUUID();
        List<Factor> factorList = new ArrayList<>();
        List<FactorLevelJpaEntity> rows = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            FactorJpaEntity f = factor(versionId, "F" + i, i + 1);
            factorList.add(f.toDomain());
            rows.add(level(f.getId(), "L1", 1));
        }
        given(levels.findAllByTenantIdAndFactorIdInAndDeprecatedAtIsNullOrderByLevelOrderAsc(
                eq(tenantId), any())).willReturn(rows);

        Map<UUID, List<FactorLevel>> out = loader().loadActiveLevels(factorList, tenantId);

        verify(levels, times(1))
                .findAllByTenantIdAndFactorIdInAndDeprecatedAtIsNullOrderByLevelOrderAsc(
                        eq(tenantId), any());
        verify(levels, never())
                .findAllByTenantIdAndFactorIdAndDeprecatedAtIsNullOrderByLevelOrderAsc(any(), any());
        assertThat(out).hasSize(5);
        assertThat(out.keySet()).containsExactlyElementsOf(factorList.stream().map(Factor::id).toList());
    }

    @Test
    void emptyFactorListSkipsTheQueryEntirely() {
        Map<UUID, List<FactorLevel>> all = loader().loadLevels(List.of(), tenantId);
        Map<UUID, List<FactorLevel>> active = loader().loadActiveLevels(List.of(), tenantId);

        assertThat(all).isEmpty();
        assertThat(active).isEmpty();
        verify(levels, never()).findAllByTenantIdAndFactorIdInOrderByLevelOrderAsc(any(), any());
        verify(levels, never())
                .findAllByTenantIdAndFactorIdInAndDeprecatedAtIsNullOrderByLevelOrderAsc(any(), any());
    }
}
