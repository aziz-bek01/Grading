package uz.hrlab.grading.gradestructure.application;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.gradestructure.domain.GradeBandGapPolicy;
import uz.hrlab.grading.gradestructure.domain.GradeStructureStatus;
import uz.hrlab.grading.gradestructure.domain.GradeStructureStatusTransitionPolicy;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * N+1 regression lock for {@link CreateGradeStructureVersionUseCase#createNewVersion}.
 * Proves the deep-copy loads every source grade's band with ONE structure-scoped batch
 * query ({@code findAllByTenantIdAndGradeStructureIdOrderByMinScoreAsc}) instead of a
 * per-grade {@code findByTenantIdAndGradeId} (the former N+1), while preserving the
 * clone side effects: one grade INSERT per source grade and one band INSERT per source
 * grade that actually has a band. Pure Mockito (no Docker).
 */
@Tag("workflow")
class CreateGradeStructureVersionUseCaseQueryCountsTest {

    private GradeStructureRepository structures;
    private GradeRepository grades;
    private GradeBandRepository bands;
    private GradeStructureStatusTransitionPolicy transitionPolicy;
    private AbacGate abacGate;
    private AuditService audit;
    private GradeStructureAuditSnapshot snapshot;

    private CreateGradeStructureVersionUseCase useCase;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        structures = mock(GradeStructureRepository.class);
        grades = mock(GradeRepository.class);
        bands = mock(GradeBandRepository.class);
        transitionPolicy = mock(GradeStructureStatusTransitionPolicy.class);
        abacGate = mock(AbacGate.class);
        audit = mock(AuditService.class);
        snapshot = mock(GradeStructureAuditSnapshot.class);
        given(snapshot.of(any(GradeStructureJpaEntity.class))).willReturn(mock(JsonNode.class));

        useCase = new CreateGradeStructureVersionUseCase(structures, grades, bands,
                transitionPolicy, abacGate, audit, snapshot);

        tenantId = UUID.randomUUID();
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenantId, Set.of(), Set.of(),
                Set.of(PermissionCodes.GRADE_EDIT), Set.of(), false, "ru-RU"));
    }

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    @Test
    void bandLoadIsOneBatchQueryRegardlessOfGradeCount() {
        // projectId == null → skips ABAC. Source APPROVED (transition policy mocked).
        GradeStructureJpaEntity source = new GradeStructureJpaEntity(UUID.randomUUID(), tenantId,
                null, "GS-1", GradeStructureType.CUSTOM, GradeStructureStatus.APPROVED, 1, null,
                GradeBandGapPolicy.STRICT_NO_GAPS);

        int gradeCount = 6;
        List<GradeJpaEntity> srcGrades = new ArrayList<>();
        List<GradeBandJpaEntity> srcBands = new ArrayList<>();
        for (int i = 0; i < gradeCount; i++) {
            GradeJpaEntity g = new GradeJpaEntity(UUID.randomUUID(), tenantId, source.getId(),
                    i + 1, i + 1);
            srcGrades.add(g);
            // Last grade deliberately has NO band → must NOT emit a band INSERT.
            if (i < gradeCount - 1) {
                srcBands.add(new GradeBandJpaEntity(UUID.randomUUID(), tenantId, g.getId(),
                        source.getId(), new BigDecimal(i * 10), new BigDecimal(i * 10 + 9)));
            }
        }

        given(structures.findByIdAndTenantId(source.getId(), tenantId))
                .willReturn(Optional.of(source));
        given(grades.findAllByTenantIdAndGradeStructureIdOrderBySortOrderAsc(tenantId, source.getId()))
                .willReturn(srcGrades);
        given(bands.findAllByTenantIdAndGradeStructureIdOrderByMinScoreAsc(tenantId, source.getId()))
                .willReturn(srcBands);

        useCase.createNewVersion(source.getId());

        // Exactly ONE batch band query, ZERO per-grade band lookups.
        verify(bands, times(1))
                .findAllByTenantIdAndGradeStructureIdOrderByMinScoreAsc(tenantId, source.getId());
        verify(bands, never()).findByTenantIdAndGradeId(any(), any());

        // Side effects preserved: 6 grade INSERTs, 5 band INSERTs (last grade band-less).
        verify(grades, times(gradeCount)).save(any());
        verify(bands, times(gradeCount - 1)).save(any());
    }
}
