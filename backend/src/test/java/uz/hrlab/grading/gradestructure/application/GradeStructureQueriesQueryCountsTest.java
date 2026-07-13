package uz.hrlab.grading.gradestructure.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.gradestructure.domain.GradeBandGapPolicy;
import uz.hrlab.grading.gradestructure.domain.GradeStructureStatus;
import uz.hrlab.grading.gradestructure.domain.GradeStructureType;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * N+1 regression lock for {@link GradeStructureQueries#findDetail}. Proves the band
 * load is O(1) — ONE structure-scoped batch query
 * ({@code findAllByTenantIdAndGradeStructureIdOrderByMinScoreAsc}) regardless of
 * grade count — and NEVER falls back to the per-grade {@code findByTenantIdAndGradeId}
 * (which was the N+1). Seeds 8 grades and asserts the band query count stays 1, so
 * it does not scale with the number of grades. Pure Mockito (no Docker) — mirrors
 * {@code FindDepartmentQueryCountsTest}.
 */
@ExtendWith(MockitoExtension.class)
class GradeStructureQueriesQueryCountsTest {

    @Mock GradeStructureRepository structures;
    @Mock GradeRepository grades;
    @Mock GradeBandRepository bands;

    GradeStructureQueries queries;

    UUID tenantId;
    UUID structureId;

    @BeforeEach
    void setUp() {
        queries = new GradeStructureQueries(structures, grades, bands);
        tenantId = UUID.randomUUID();
        structureId = UUID.randomUUID();
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenantId, Set.of(), Set.of(),
                Set.of(PermissionCodes.GRADE_READ), Set.of(), false, "ru-RU"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void bandLoadIsOneBatchQueryRegardlessOfGradeCount() {
        int gradeCount = 8;
        GradeStructureJpaEntity s = new GradeStructureJpaEntity(structureId, tenantId, null, "GS",
                GradeStructureType.CUSTOM, GradeStructureStatus.APPROVED, 1, null,
                GradeBandGapPolicy.STRICT_NO_GAPS);
        List<GradeJpaEntity> gradeRows = new ArrayList<>();
        List<GradeBandJpaEntity> bandRows = new ArrayList<>();
        for (int i = 0; i < gradeCount; i++) {
            GradeJpaEntity g = new GradeJpaEntity(UUID.randomUUID(), tenantId, structureId, i + 1, i + 1);
            gradeRows.add(g);
            bandRows.add(new GradeBandJpaEntity(UUID.randomUUID(), tenantId, g.getId(), structureId,
                    new BigDecimal(i * 100), new BigDecimal((i + 1) * 100)));
        }
        given(structures.findByIdAndTenantId(structureId, tenantId)).willReturn(Optional.of(s));
        given(grades.findAllByTenantIdAndGradeStructureIdOrderBySortOrderAsc(tenantId, structureId))
                .willReturn(gradeRows);
        given(bands.findAllByTenantIdAndGradeStructureIdOrderByMinScoreAsc(tenantId, structureId))
                .willReturn(bandRows);

        GradeStructureAggregate aggregate = queries.findDetail(structureId);

        // Behavior preserved: every grade's band is present in the aggregate.
        assertThat(aggregate.grades()).hasSize(gradeCount);
        assertThat(aggregate.bands()).hasSize(gradeCount);

        // Bounded: exactly ONE batch band query, ZERO per-grade band queries —
        // the count does NOT scale with the 8 grades.
        verify(bands, times(1)).findAllByTenantIdAndGradeStructureIdOrderByMinScoreAsc(tenantId, structureId);
        verify(bands, never()).findByTenantIdAndGradeId(any(), any());
    }
}
