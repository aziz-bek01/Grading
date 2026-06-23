package uz.hrlab.grading.approval.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.approval.domain.ApprovalRequest;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestJpaEntity;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestRepository;
import uz.hrlab.grading.approval.infrastructure.ApprovalStepRepository;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.project.infrastructure.ProjectJpaEntity;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Pins the project-membership ABAC boundary on {@link CreateApprovalRequestUseCase}:
 * the gate is a USER-action check enforced on the public {@code create()} path
 * ONLY. The {@code createSystem} variant — used by the auto-submit flow AND the
 * startup CEO-approval reconciliation, which runs under a permission-less system
 * context (no roles, no projectIds) — must NOT be denied by it, or the
 * reconciliation would silently open zero approvals and fully-evaluated panels
 * would stay invisible to the CEO. Tenant isolation ({@code findByIdAndTenantId})
 * still applies to both paths.
 */
class CreateApprovalRequestAbacTest {

    private ApprovalRequestRepository requests;
    private ApprovalStepRepository steps;
    private ProjectRepository projects;
    private AbacGate abacGate;
    private AuditService audit;
    private ApprovalQueries queries;
    private CreateApprovalRequestUseCase useCase;

    private UUID tenantId;
    private UUID projectId;

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
        given(projects.findByIdAndTenantId(any(), any()))
                .willReturn(Optional.of(mock(ProjectJpaEntity.class)));
    }

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    private CreateApprovalRequestCommand panelCmd() {
        return CreateApprovalRequestCommand.singleStep(
                projectId, ApprovalEntityType.EVALUATION_PANEL, UUID.randomUUID(),
                PermissionCodes.EVALUATION_PANEL_APPROVE);
    }

    /** A permission-less system context — what the reconciliation runner sets. */
    private void setSystemContext() {
        TenantContextHolder.set(new TenantContext(
                null, tenantId, Set.of(), Set.of(), Set.of(), Set.of(), false, null));
    }

    @Test
    void createSystem_doesNotEnforceProjectAbac_underSystemContext() {
        setSystemContext();
        given(queries.findActivePendingForEntity(any(), any(), any())).willReturn(null);
        given(requests.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(steps.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(queries.hydrate(any())).willAnswer(inv -> {
            ApprovalRequestJpaEntity r = inv.getArgument(0);
            return new ApprovalRequest(r.getId(), r.getTenantId(), r.getProjectId(),
                    r.getEntityType(), r.getEntityId(), r.getRequestedBy(),
                    r.getRequestedAt(), r.getCurrentStatus(), r.getNotesI18n(), List.of());
        });

        ApprovalRequest result = useCase.createSystem(panelCmd());

        assertThat(result).isNotNull();
        // The decisive assertion: the system path NEVER hits the project-membership gate.
        verify(abacGate, never()).enforceCanWriteInProject(any(), any());
        verify(requests).save(any());
    }

    @Test
    void create_stillEnforcesProjectAbac() {
        // A caller WITH the create permission but NO project membership / bypass role.
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenantId, Set.of(), Set.of(),
                Set.of(PermissionCodes.APPROVAL_REQUEST_CREATE), Set.of(), false, null));
        doThrow(new TenantAccessDeniedException())
                .when(abacGate).enforceCanWriteInProject(any(), any());

        assertThatThrownBy(() -> useCase.create(panelCmd()))
                .isInstanceOf(TenantAccessDeniedException.class);

        verify(abacGate).enforceCanWriteInProject(any(), any());
        verify(requests, never()).save(any());
    }
}
