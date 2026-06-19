package uz.hrlab.grading.evaluation.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.approval.application.CreateApprovalRequestCommand;
import uz.hrlab.grading.approval.application.CreateApprovalRequestUseCase;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.domain.EvaluationStatusTransitionPolicy;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Consolidated panel flow — a panel-member submit ({@code panelId != null}) must
 * NOT open its own per-EVALUATION approval (the panel carries the single CEO
 * approval), while a NON-panel single-evaluator submit ({@code panelId == null})
 * STILL opens the per-EVALUATION approval requiring EVALUATION_APPROVE — the
 * unchanged legacy path.
 */
@ExtendWith(MockitoExtension.class)
class SubmitEvaluationUseCaseTest {

    @Mock EvaluationRepository evaluations;
    @Mock EvaluationScoreRepository scores;
    @Mock EvaluationContextLoader loader;
    @Mock AbacGate abacGate;
    @Mock AuditService audit;
    @Mock EvaluationAuditSnapshot snapshot;
    @Mock CreateApprovalRequestUseCase createApprovalRequest;
    @Mock PositionRepository positions;

    EvaluationStatusTransitionPolicy transitionPolicy = new EvaluationStatusTransitionPolicy();

    SubmitEvaluationUseCase useCase;

    UUID tenantId;
    UUID projectId;
    UUID positionId;
    UUID departmentId;
    UUID versionId;
    UUID evaluatorId;
    UUID evaluationId;

    @BeforeEach
    void setUp() {
        useCase = new SubmitEvaluationUseCase(
                evaluations, scores, loader, transitionPolicy, abacGate,
                audit, snapshot, createApprovalRequest, positions);
        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        positionId = UUID.randomUUID();
        departmentId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        evaluatorId = UUID.randomUUID();
        evaluationId = UUID.randomUUID();
        setContext(Set.of("EVALUATION_EDIT"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void nonPanelSubmitOpensPerEvaluationApproval() {
        EvaluationJpaEntity eval = stubCompleteEvaluation(null); // panelId == null

        useCase.submit(evaluationId);

        assertThat(eval.getStatus()).isEqualTo(EvaluationStatus.SUBMITTED);
        ArgumentCaptor<CreateApprovalRequestCommand> cmd =
                ArgumentCaptor.forClass(CreateApprovalRequestCommand.class);
        verify(createApprovalRequest).createSystem(cmd.capture());
        assertThat(cmd.getValue().entityType()).isEqualTo(ApprovalEntityType.EVALUATION);
        assertThat(cmd.getValue().entityId()).isEqualTo(evaluationId);
        assertThat(cmd.getValue().steps()).hasSize(1);
        assertThat(cmd.getValue().steps().get(0).requiredPermission())
                .isEqualTo("EVALUATION_APPROVE");
    }

    @Test
    void panelMemberSubmitDoesNotOpenPerEvaluationApprovalButStillSubmitsAndAudits() {
        UUID panelId = UUID.randomUUID();
        EvaluationJpaEntity eval = stubCompleteEvaluation(panelId); // panelId != null

        useCase.submit(evaluationId);

        // Still transitions + persists + writes its EVALUATION_SUBMITTED audit...
        assertThat(eval.getStatus()).isEqualTo(EvaluationStatus.SUBMITTED);
        assertThat(eval.getSubmittedBy()).isNotNull();
        verify(evaluations).save(eval);
        ArgumentCaptor<AuditEvent> audited = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(audited.capture());
        assertThat(audited.getValue().action()).isEqualTo(AuditAction.EVALUATION_SUBMITTED);
        // ...but opens NO per-EVALUATION approval — the panel carries the CEO approval.
        verify(createApprovalRequest, never()).createSystem(any());
    }

    // --------------------------------------------------------------- helpers

    private EvaluationJpaEntity stubCompleteEvaluation(UUID panelId) {
        EvaluationJpaEntity eval = new EvaluationJpaEntity(
                evaluationId, tenantId, projectId, positionId, versionId,
                evaluatorId, EvaluationStatus.COMPLETE);
        eval.setPanelId(panelId);
        EvaluationContext context = new EvaluationContext(eval, null, List.of(), Map.of());
        when(loader.load(evaluationId, tenantId)).thenReturn(context);
        PositionJpaEntity position = new PositionJpaEntity(
                positionId, tenantId, projectId, departmentId, "P-001",
                Map.of("ru-RU", "Кассир"), null, null, null, null, PositionStatus.ACTIVE);
        when(positions.findByIdAndTenantId(positionId, tenantId))
                .thenReturn(Optional.of(position));
        // No required factors -> defensive completeness check passes with no scores.
        when(scores.findAllByTenantIdAndEvaluationId(tenantId, evaluationId))
                .thenReturn(List.of());
        return eval;
    }

    private void setContext(Set<String> permissions) {
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenantId, Set.of(projectId), Set.of(),
                permissions, Set.of(), false, "ru-RU"));
    }
}
