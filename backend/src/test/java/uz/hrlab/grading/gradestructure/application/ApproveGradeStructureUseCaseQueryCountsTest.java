package uz.hrlab.grading.gradestructure.application;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.approval.application.CreateApprovalRequestUseCase;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.gradestructure.domain.GradeBandGapDetector;
import uz.hrlab.grading.gradestructure.domain.GradeBandGapPolicy;
import uz.hrlab.grading.gradestructure.domain.GradeBandOverlapValidator;
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
 * N+1 regression lock for {@link ApproveGradeStructureUseCase#approve} (approval hot
 * path). Proves the "every grade has a band" check loads bands with ONE
 * structure-scoped batch query
 * ({@code findAllByTenantIdAndGradeStructureIdOrderByMinScoreAsc}) and NEVER falls
 * back to the per-grade {@code findByTenantIdAndGradeId} (the former N+1). Seeds 8
 * grades and asserts the band query count stays 1, i.e. it does not scale with the
 * grade count. Overlap/gap validation is mocked away to keep the test focused on the
 * query count. Pure Mockito (no Docker) — mirrors {@code FindDepartmentQueryCountsTest}.
 */
@ExtendWith(MockitoExtension.class)
class ApproveGradeStructureUseCaseQueryCountsTest {

    @Mock GradeStructureRepository structures;
    @Mock GradeRepository grades;
    @Mock GradeBandRepository bands;
    @Mock GradeStructureStatusTransitionPolicy transitionPolicy;
    @Mock GradeBandOverlapValidator overlapValidator;
    @Mock GradeBandGapDetector gapDetector;
    @Mock AbacGate abacGate;
    @Mock AuditService audit;
    @Mock GradeStructureAuditSnapshot snapshot;
    @Mock CreateApprovalRequestUseCase createApprovalRequest;

    ApproveGradeStructureUseCase useCase;

    UUID tenantId;
    UUID structureId;

    @BeforeEach
    void setUp() {
        useCase = new ApproveGradeStructureUseCase(structures, grades, bands, transitionPolicy,
                overlapValidator, gapDetector, abacGate, audit, snapshot, createApprovalRequest);
        tenantId = UUID.randomUUID();
        structureId = UUID.randomUUID();
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenantId, Set.of(), Set.of(),
                Set.of(PermissionCodes.GRADE_STRUCTURE_APPROVE), Set.of(), false, "ru-RU"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void bandLoadIsOneBatchQueryRegardlessOfGradeCount() {
        int gradeCount = 8;
        // projectId == null → skips ABAC + system approval-request recording.
        GradeStructureJpaEntity s = new GradeStructureJpaEntity(structureId, tenantId, null, "GS",
                GradeStructureType.CUSTOM, GradeStructureStatus.DRAFT, 1, null,
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
        given(snapshot.of(any(GradeStructureJpaEntity.class))).willReturn(mock(JsonNode.class));
        // overlapValidator.findOverlaps / gapDetector.findGaps default to empty lists.

        useCase.approve(structureId);

        // Bounded: exactly ONE batch band query, ZERO per-grade band queries —
        // the count does NOT scale with the 8 grades.
        verify(bands, times(1)).findAllByTenantIdAndGradeStructureIdOrderByMinScoreAsc(tenantId, structureId);
        verify(bands, never()).findByTenantIdAndGradeId(any(), any());
    }
}
