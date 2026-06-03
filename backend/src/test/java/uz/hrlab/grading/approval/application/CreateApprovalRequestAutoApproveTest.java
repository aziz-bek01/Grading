package uz.hrlab.grading.approval.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.approval.domain.ApprovalRequest;
import uz.hrlab.grading.approval.domain.ApprovalRequestStatus;
import uz.hrlab.grading.approval.domain.ApprovalStep;
import uz.hrlab.grading.approval.domain.ApprovalStepStatus;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestJpaEntity;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestRepository;
import uz.hrlab.grading.approval.infrastructure.ApprovalStepJpaEntity;
import uz.hrlab.grading.approval.infrastructure.ApprovalStepRepository;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.project.infrastructure.ProjectJpaEntity;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * PO-9 — unit tests for {@link CreateApprovalRequestUseCase#createSystemAndAutoApproveFirstStep}.
 *
 * <p>Asserts:
 * <ol>
 *   <li>request is created with the supplied entityType + entityId,</li>
 *   <li>single step transitions PENDING → APPROVED with decidedBy = caller,</li>
 *   <li>request status flips PENDING → APPROVED,</li>
 *   <li>APPROVAL_REQUEST_CREATED + APPROVAL_STEP_APPROVED audit rows emit,</li>
 *   <li>idempotent — calling twice for the same entity returns null on the
 *       second call (ANOTHER_PENDING_REQUEST_EXISTS swallowed).</li>
 * </ol>
 */
@Tag("workflow")
class CreateApprovalRequestAutoApproveTest {

    private ApprovalRequestRepository requests;
    private ApprovalStepRepository steps;
    private ProjectRepository projects;
    private AbacGate abacGate;
    private AuditService audit;
    private ApprovalQueries queries;
    private CreateApprovalRequestUseCase useCase;

    private UUID tenantId;
    private UUID projectId;
    private UUID userId;

    @BeforeEach
    void setup() {
        requests = mock(ApprovalRequestRepository.class);
        steps = mock(ApprovalStepRepository.class);
        projects = mock(ProjectRepository.class);
        abacGate = mock(AbacGate.class);
        audit = mock(AuditService.class);
        queries = mock(ApprovalQueries.class);
        useCase = new CreateApprovalRequestUseCase(requests, steps, projects, abacGate, audit, queries);

        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        userId = UUID.randomUUID();
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(projectId),
                Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of(PermissionCodes.METHODOLOGY_APPROVE,
                        PermissionCodes.GRADE_STRUCTURE_APPROVE,
                        PermissionCodes.APPROVAL_REQUEST_CREATE),
                Set.of(), false, "ru-RU"));

        given(projects.findByIdAndTenantId(eq(projectId), eq(tenantId)))
                .willReturn(Optional.of(mock(ProjectJpaEntity.class)));
        given(queries.findActivePendingForEntity(eq(tenantId), any(), any()))
                .willReturn(null);
        given(requests.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(steps.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(queries.hydrate(any())).willAnswer(inv -> {
            ApprovalRequestJpaEntity r = inv.getArgument(0);
            return new ApprovalRequest(r.getId(), r.getTenantId(), r.getProjectId(),
                    r.getEntityType(), r.getEntityId(), r.getRequestedBy(),
                    r.getRequestedAt(), r.getCurrentStatus(), r.getNotesI18n(),
                    List.<ApprovalStep>of());
        });
    }

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    @Test
    void methodology_autoApproval_recordsRequest_andMarksFirstStepApproved() {
        UUID methodologyVersionId = UUID.randomUUID();
        CreateApprovalRequestCommand cmd = CreateApprovalRequestCommand.singleStep(
                projectId, ApprovalEntityType.METHODOLOGY_VERSION, methodologyVersionId,
                PermissionCodes.METHODOLOGY_APPROVE);

        // The step repository must echo back the single step we saved so that
        // the use case can mark it APPROVED.
        ArgumentCaptor<ApprovalStepJpaEntity> stepSaved = ArgumentCaptor.forClass(ApprovalStepJpaEntity.class);
        given(steps.findAllByTenantIdAndApprovalRequestIdOrderByStepOrderAsc(
                eq(tenantId), any()))
                .willAnswer(inv -> List.of(stepSaved.getValue()));
        given(requests.findByIdAndTenantId(any(), eq(tenantId)))
                .willAnswer(inv -> {
                    ArgumentCaptor<ApprovalRequestJpaEntity> reqSaved =
                            ArgumentCaptor.forClass(ApprovalRequestJpaEntity.class);
                    verify(requests).save(reqSaved.capture());
                    return Optional.of(reqSaved.getValue());
                });
        // We need to capture the saved step first; do this via doAnswer pattern.
        org.mockito.Mockito.doAnswer(inv -> inv.getArgument(0)).when(steps).save(stepSaved.capture());

        ApprovalRequest result = useCase.createSystemAndAutoApproveFirstStep(cmd, null);
        assertThat(result).isNotNull();
        assertThat(result.currentStatus()).isEqualTo(ApprovalRequestStatus.APPROVED);

        // First step was marked APPROVED with the active user as decider.
        ApprovalStepJpaEntity capturedStep = stepSaved.getValue();
        assertThat(capturedStep.getStatus()).isEqualTo(ApprovalStepStatus.APPROVED);
        assertThat(capturedStep.getDecidedBy()).isEqualTo(userId);
        assertThat(capturedStep.getDecidedAt()).isNotNull();
        assertThat(capturedStep.getRequiredPermission()).isEqualTo(PermissionCodes.METHODOLOGY_APPROVE);

        // Two audit rows: REQUEST_CREATED and STEP_APPROVED.
        ArgumentCaptor<AuditEvent> auditEvents = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, atLeastOnce()).record(auditEvents.capture());
        List<String> actions = auditEvents.getAllValues().stream().map(AuditEvent::action).toList();
        assertThat(actions).contains(AuditAction.APPROVAL_REQUEST_CREATED);
        assertThat(actions).contains(AuditAction.APPROVAL_STEP_APPROVED);
    }

    @Test
    void gradeStructure_autoApproval_usesCorrectEntityType() {
        UUID gradeStructureId = UUID.randomUUID();
        CreateApprovalRequestCommand cmd = CreateApprovalRequestCommand.singleStep(
                projectId, ApprovalEntityType.GRADE_STRUCTURE, gradeStructureId,
                PermissionCodes.GRADE_STRUCTURE_APPROVE);

        ArgumentCaptor<ApprovalRequestJpaEntity> reqCap = ArgumentCaptor.forClass(ApprovalRequestJpaEntity.class);
        ArgumentCaptor<ApprovalStepJpaEntity> stepCap = ArgumentCaptor.forClass(ApprovalStepJpaEntity.class);
        org.mockito.Mockito.doAnswer(inv -> inv.getArgument(0)).when(requests).save(reqCap.capture());
        org.mockito.Mockito.doAnswer(inv -> inv.getArgument(0)).when(steps).save(stepCap.capture());
        given(steps.findAllByTenantIdAndApprovalRequestIdOrderByStepOrderAsc(eq(tenantId), any()))
                .willAnswer(inv -> List.of(stepCap.getValue()));
        given(requests.findByIdAndTenantId(any(), eq(tenantId)))
                .willAnswer(inv -> Optional.of(reqCap.getValue()));

        ApprovalRequest result = useCase.createSystemAndAutoApproveFirstStep(cmd, null);
        assertThat(result).isNotNull();
        assertThat(reqCap.getValue().getEntityType())
                .isEqualTo(ApprovalEntityType.GRADE_STRUCTURE);
        assertThat(reqCap.getValue().getEntityId()).isEqualTo(gradeStructureId);
        assertThat(stepCap.getValue().getRequiredPermission())
                .isEqualTo(PermissionCodes.GRADE_STRUCTURE_APPROVE);
    }

    @Test
    void idempotent_secondCallReturnsNullInsteadOfThrowing() {
        UUID methodologyVersionId = UUID.randomUUID();
        // Simulate a previously-open PENDING request for the same entity.
        ApprovalRequestJpaEntity existingPending = new ApprovalRequestJpaEntity(
                UUID.randomUUID(), tenantId, projectId,
                ApprovalEntityType.METHODOLOGY_VERSION, methodologyVersionId,
                userId, OffsetDateTime.now(), ApprovalRequestStatus.PENDING, null);
        given(queries.findActivePendingForEntity(eq(tenantId),
                eq(ApprovalEntityType.METHODOLOGY_VERSION), eq(methodologyVersionId)))
                .willReturn(existingPending);

        CreateApprovalRequestCommand cmd = CreateApprovalRequestCommand.singleStep(
                projectId, ApprovalEntityType.METHODOLOGY_VERSION, methodologyVersionId,
                PermissionCodes.METHODOLOGY_APPROVE);

        ApprovalRequest result = useCase.createSystemAndAutoApproveFirstStep(cmd, null);
        assertThat(result).isNull();
    }

    @Test
    void systemLevelEntity_noProject_returnsNullSilently() {
        CreateApprovalRequestCommand cmdNoProject = new CreateApprovalRequestCommand(
                null, ApprovalEntityType.METHODOLOGY_VERSION,
                UUID.randomUUID(), null,
                List.of(new CreateApprovalRequestCommand.StepSpec(
                        1, null, PermissionCodes.METHODOLOGY_APPROVE)));
        ApprovalRequest result = useCase.createSystemAndAutoApproveFirstStep(cmdNoProject, null);
        assertThat(result).isNull();
    }
}
