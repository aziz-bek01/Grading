package uz.hrlab.grading.approval.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.approval.domain.ApprovalRequest;
import uz.hrlab.grading.approval.domain.ApprovalRequestStatus;
import uz.hrlab.grading.approval.domain.ApprovalStepStatus;
import uz.hrlab.grading.approval.domain.ApprovalTransitionRejectedException;
import uz.hrlab.grading.approval.infrastructure.ApprovalDecisionRepository;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestJpaEntity;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestRepository;
import uz.hrlab.grading.approval.infrastructure.ApprovalStepJpaEntity;
import uz.hrlab.grading.approval.infrastructure.ApprovalStepRepository;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * State machine tests for the approval decision core.
 */
@Tag("workflow")
class ApprovalDecisionMakerTest {

    private ApprovalRequestRepository requests;
    private ApprovalStepRepository steps;
    private ApprovalDecisionRepository decisions;
    private ApprovalQueries queries;
    private AbacGate abacGate;
    private AuditService audit;
    private ApprovalDecisionMaker maker;

    private UUID tenantId;
    private UUID projectId;
    private UUID userId;
    private UUID requestId;
    private UUID stepId;
    private ApprovalRequestJpaEntity req;
    private ApprovalStepJpaEntity step;

    @BeforeEach
    void setup() {
        requests = mock(ApprovalRequestRepository.class);
        steps = mock(ApprovalStepRepository.class);
        decisions = mock(ApprovalDecisionRepository.class);
        queries = mock(ApprovalQueries.class);
        abacGate = mock(AbacGate.class);
        audit = mock(AuditService.class);
        maker = new ApprovalDecisionMaker(requests, steps, decisions, queries, abacGate, audit);

        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        userId = UUID.randomUUID();
        requestId = UUID.randomUUID();
        stepId = UUID.randomUUID();

        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(projectId), Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of("APPROVAL_REQUEST_DECIDE", "JOB_PROFILE_APPROVE"),
                Set.of(), false, "ru-RU"));

        req = new ApprovalRequestJpaEntity(requestId, tenantId, projectId,
                ApprovalEntityType.JOB_PROFILE, UUID.randomUUID(), UUID.randomUUID(),
                OffsetDateTime.now(), ApprovalRequestStatus.PENDING, null);
        step = new ApprovalStepJpaEntity(stepId, tenantId, requestId, 1,
                null, "JOB_PROFILE_APPROVE", ApprovalStepStatus.PENDING);

        given(requests.findByIdAndTenantId(eq(requestId), eq(tenantId)))
                .willReturn(Optional.of(req));
        given(steps.findByIdAndTenantId(eq(stepId), eq(tenantId)))
                .willReturn(Optional.of(step));
        given(requests.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(steps.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(queries.hydrate(any())).willAnswer(inv -> {
            ApprovalRequestJpaEntity r = inv.getArgument(0);
            return new ApprovalRequest(r.getId(), r.getTenantId(), r.getProjectId(),
                    r.getEntityType(), r.getEntityId(), r.getRequestedBy(),
                    r.getRequestedAt(), r.getCurrentStatus(), r.getNotesI18n(),
                    java.util.List.of());
        });
        given(queries.findCurrentPendingStep(eq(tenantId), eq(requestId))).willReturn(null);
    }

    @Test
    void approve_lastStep_marksRequestApproved() {
        ApprovalRequest result = maker.approve(requestId, stepId, null);
        assertThat(result.currentStatus()).isEqualTo(ApprovalRequestStatus.APPROVED);
        assertThat(step.getStatus()).isEqualTo(ApprovalStepStatus.APPROVED);
        assertThat(step.getDecidedBy()).isEqualTo(userId);
    }

    @Test
    void approve_withPendingNextStep_keepsRequestPending() {
        ApprovalStepJpaEntity next = new ApprovalStepJpaEntity(UUID.randomUUID(), tenantId,
                requestId, 2, null, "JOB_PROFILE_APPROVE", ApprovalStepStatus.PENDING);
        given(queries.findCurrentPendingStep(eq(tenantId), eq(requestId))).willReturn(next);

        ApprovalRequest result = maker.approve(requestId, stepId, null);
        assertThat(result.currentStatus()).isEqualTo(ApprovalRequestStatus.PENDING);
    }

    @Test
    void reject_marksRequestRejected() {
        ApprovalRequest result = maker.reject(requestId, stepId,
                "Detailed reason that is long enough to pass validation rule.");
        assertThat(result.currentStatus()).isEqualTo(ApprovalRequestStatus.REJECTED);
    }

    @Test
    void reject_shortReason_throws() {
        assertThatThrownBy(() -> maker.reject(requestId, stepId, "short"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void requestChanges_marksChangesRequested() {
        ApprovalRequest result = maker.requestChanges(requestId, stepId,
                "Please add KPI section to the duties area before resubmitting.");
        assertThat(result.currentStatus()).isEqualTo(ApprovalRequestStatus.CHANGES_REQUESTED);
    }

    @Test
    void terminalRequest_cannotBeDecided() {
        req.setCurrentStatus(ApprovalRequestStatus.APPROVED);
        assertThatThrownBy(() -> maker.approve(requestId, stepId, null))
                .isInstanceOf(ApprovalTransitionRejectedException.class);
    }

    @Test
    void wrongApprover_denied() {
        // Step explicitly assigned to a different user.
        step.setApproverUserId(UUID.randomUUID());
        assertThatThrownBy(() -> maker.approve(requestId, stepId, null))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void openStep_callerNeedsRequiredPermission() {
        // approverUserId NULL, caller lacks the required permission
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(projectId), Set.of(),
                Set.of("APPROVAL_REQUEST_DECIDE"), Set.of(), false, "ru-RU"));

        assertThatThrownBy(() -> maker.approve(requestId, stepId, null))
                .isInstanceOf(PermissionDeniedException.class);
    }
}
