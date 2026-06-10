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
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatus;
import uz.hrlab.grading.methodology.domain.ScoringMode;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;
import uz.hrlab.grading.position.domain.PositionStatus;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.math.BigDecimal;
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
 * E4-S3 — department write-gate on {@link CreateEvaluationUseCase}, exercised
 * through a REAL {@link AbacGate} composed of the real
 * {@link DepartmentScopePolicy} + {@link ProjectMembershipPolicy} so the full
 * deny-and-audit chain is asserted.
 *
 * <ul>
 *   <li>department-scoped expert, position IN their subtree → evaluation
 *       created (no denial);</li>
 *   <li>department-scoped expert, position OUTSIDE their subtree →
 *       {@link TenantAccessDeniedException} (404, no existence reveal) AND an
 *       {@code ACCESS_DENIED_BY_ABAC} audit row;</li>
 *   <li>bypass role, position in any department → created (gate permits).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CreateEvaluationUseCaseScopeGateTest {

    @Mock EvaluationRepository evaluations;
    @Mock PositionRepository positions;
    @Mock MethodologyVersionRepository versions;
    @Mock AuditService audit;
    @Mock EvaluationAuditSnapshot snapshot;

    AbacGate abacGate;
    CreateEvaluationUseCase useCase;

    UUID tenantId;
    UUID userId;
    UUID projectId;
    UUID positionId;
    UUID versionId;
    UUID inScopeDept;
    UUID outOfScopeDept;

    @BeforeEach
    void setUp() {
        abacGate = new AbacGate(
                List.of(new ProjectMembershipPolicy(), new DepartmentScopePolicy()), audit);
        useCase = new CreateEvaluationUseCase(
                evaluations, positions, versions, abacGate, audit, snapshot);
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        positionId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        inScopeDept = UUID.randomUUID();
        outOfScopeDept = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() { TenantContextHolder.clear(); }

    @Test
    void scopedExpertCanCreateForPositionInsideSubtree() {
        // Expert assigned to inScopeDept; create evaluation for a position there.
        setExpertContext(Set.of(inScopeDept));
        stubPosition(inScopeDept);
        stubApprovedVersion();
        when(evaluations.existsByTenantIdAndPositionIdAndMethodologyVersionIdAndStatusNot(
                any(), any(), any(), any())).thenReturn(false);

        useCase.create(new CreateEvaluationCommand(positionId, versionId, null));

        verify(evaluations).save(any(EvaluationJpaEntity.class));
        verify(audit, never()).record(argAction(AuditAction.ACCESS_DENIED_BY_ABAC));
    }

    @Test
    void scopedExpertDeniedForPositionOutsideSubtreeWithAudit() {
        // Expert assigned to inScopeDept only; position lives in outOfScopeDept.
        setExpertContext(Set.of(inScopeDept));
        stubPosition(outOfScopeDept);
        stubApprovedVersion();

        assertThatThrownBy(() ->
                useCase.create(new CreateEvaluationCommand(positionId, versionId, null)))
                .isInstanceOf(TenantAccessDeniedException.class);

        // No evaluation persisted.
        verify(evaluations, never()).save(any());
        // ACCESS_DENIED_BY_ABAC audit row written by the gate.
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, atLeastOnce()).record(captor.capture());
        assertThat(captor.getAllValues())
                .anyMatch(e -> AuditAction.ACCESS_DENIED_BY_ABAC.equals(e.action()));
    }

    @Test
    void bypassRoleCanCreateAnywhere() {
        // Bypass role with no department scope still permitted on any department.
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(projectId),
                Set.of("CLIENT_HR_DIRECTOR"),
                Set.of("EVALUATION_EDIT"), Set.of(), false, "ru-RU"));
        stubPosition(outOfScopeDept);
        stubApprovedVersion();
        when(evaluations.existsByTenantIdAndPositionIdAndMethodologyVersionIdAndStatusNot(
                any(), any(), any(), any())).thenReturn(false);

        useCase.create(new CreateEvaluationCommand(positionId, versionId, null));

        verify(evaluations).save(any(EvaluationJpaEntity.class));
        verify(audit, never()).record(argAction(AuditAction.ACCESS_DENIED_BY_ABAC));
    }

    // ---------- helpers ----------

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

    private void stubApprovedVersion() {
        MethodologyVersionJpaEntity v = new MethodologyVersionJpaEntity(
                versionId, tenantId, UUID.randomUUID(), 1,
                MethodologyVersionStatus.APPROVED, ScoringMode.DIRECT_POINTS,
                new BigDecimal("100"), null);
        when(versions.findByIdAndTenantId(versionId, tenantId)).thenReturn(Optional.of(v));
    }

    private static AuditEvent argAction(String action) {
        return org.mockito.ArgumentMatchers.argThat(e ->
                e != null && action.equals(e.action()));
    }
}
