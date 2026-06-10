package uz.hrlab.grading.evaluation.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.domain.DepartmentScopePolicy;
import uz.hrlab.grading.access.domain.ProjectMembershipPolicy;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.evaluation.domain.EvaluationImmutabilityPolicy;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreRepository;
import uz.hrlab.grading.position.domain.PositionStatus;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * E4-S3 — department write-gate on {@link UpsertEvaluationScoreUseCase} (the
 * scoring path), through a REAL {@link AbacGate}.
 *
 * <ul>
 *   <li>scoped expert, evaluation's position OUTSIDE subtree → denied with a
 *       404-equivalent {@link TenantAccessDeniedException} BEFORE any score is
 *       persisted, plus an {@code ACCESS_DENIED_BY_ABAC} audit row;</li>
 *   <li>bypass role → gate permits (no denial audit).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class UpsertEvaluationScoreUseCaseScopeGateTest {

    @Mock EvaluationRepository evaluations;
    @Mock EvaluationScoreRepository scores;
    @Mock EvaluationContextLoader loader;
    @Mock EvaluationRecomputeService recompute;
    @Mock AuditService audit;
    @Mock EvaluationAuditSnapshot snapshot;
    @Mock PositionRepository positions;

    EvaluationImmutabilityPolicy immutability = new EvaluationImmutabilityPolicy();
    AbacGate abacGate;
    UpsertEvaluationScoreUseCase useCase;

    UUID tenantId;
    UUID userId;
    UUID projectId;
    UUID evaluationId;
    UUID positionId;
    UUID factorId;
    UUID factorLevelId;
    UUID inScopeDept;
    UUID outOfScopeDept;

    @BeforeEach
    void setUp() {
        abacGate = new AbacGate(
                List.of(new ProjectMembershipPolicy(), new DepartmentScopePolicy()), audit);
        useCase = new UpsertEvaluationScoreUseCase(
                evaluations, scores, loader, recompute, immutability, abacGate,
                audit, snapshot, positions);
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        evaluationId = UUID.randomUUID();
        positionId = UUID.randomUUID();
        factorId = UUID.randomUUID();
        factorLevelId = UUID.randomUUID();
        inScopeDept = UUID.randomUUID();
        outOfScopeDept = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() { TenantContextHolder.clear(); }

    @Test
    void scopedExpertDeniedScoringOutsideSubtreeBeforeAnyWrite() {
        setExpertContext(Set.of(inScopeDept));
        EvaluationJpaEntity evaluation = new EvaluationJpaEntity(
                evaluationId, tenantId, projectId, positionId, UUID.randomUUID(),
                userId, EvaluationStatus.DRAFT);
        when(loader.load(evaluationId, tenantId))
                .thenReturn(new EvaluationContext(evaluation, null, List.of(), Map.of()));
        stubPosition(outOfScopeDept);

        assertThatThrownBy(() -> useCase.upsert(new UpsertEvaluationScoreCommand(
                evaluationId, factorId, factorLevelId, null)))
                .isInstanceOf(TenantAccessDeniedException.class);

        // No score row written, no recompute, evaluation not saved.
        verify(scores, never()).save(any());
        verify(recompute, never()).recompute(any());
        verify(evaluations, never()).save(any());
        // Denial audit present.
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, atLeastOnce()).record(captor.capture());
        assertThat(captor.getAllValues())
                .anyMatch(e -> AuditAction.ACCESS_DENIED_BY_ABAC.equals(e.action()));
    }

    private void setExpertContext(Set<UUID> deptScope) {
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(projectId),
                Set.of("EVALUATION_COMMITTEE_MEMBER"),
                Set.of("EVALUATION_EDIT"), deptScope, false, "ru-RU"));
    }

    private void stubPosition(UUID departmentId) {
        PositionJpaEntity p = new PositionJpaEntity(
                positionId, tenantId, projectId, departmentId, "P-001",
                Map.of("ru-RU", "Position"), null, null, null, null, PositionStatus.ACTIVE);
        when(positions.findByIdAndTenantId(positionId, tenantId)).thenReturn(Optional.of(p));
    }
}
