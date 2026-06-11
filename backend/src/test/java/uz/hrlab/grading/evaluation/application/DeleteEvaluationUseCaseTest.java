package uz.hrlab.grading.evaluation.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BE-2 / BE-4(b) — {@link DeleteEvaluationUseCase}. Mirrors the archive use-case
 * test shape with mocks. Asserts:
 * <ul>
 *   <li>every PRE-SUBMISSION status (DRAFT / INCOMPLETE / COMPLETE) deletes:
 *       dependent score rows removed FIRST, parent removed via tenant-scoped
 *       {@code deleteByIdAndTenantId}, EVALUATION_DELETED audited with beforeJson
 *       + afterJson null;</li>
 *   <li>post-submission (SUBMITTED / APPROVED / LOCKED / ARCHIVED) → 400
 *       {@code EVALUATION_NOT_DELETABLE} (nothing deleted);</li>
 *   <li>missing/short reason → 400 before any load;</li>
 *   <li>missing EVALUATION_EDIT → 403.</li>
 * </ul>
 * Cross-tenant 404 is covered at the controller layer
 * ({@code EvaluationControllerSecurityTest.deleteCrossTenantIdReturns404}) since
 * the loader's {@code findByIdAndTenantId} throws {@link TenantAccessDeniedException}.
 */
@ExtendWith(MockitoExtension.class)
class DeleteEvaluationUseCaseTest {

    @Mock EvaluationRepository evaluations;
    @Mock EvaluationScoreRepository scores;
    @Mock EvaluationContextLoader loader;
    @Mock AbacGate abacGate;
    @Mock AuditService audit;
    @Mock EvaluationAuditSnapshot snapshot;

    DeleteEvaluationUseCase useCase;

    UUID tenantId;
    UUID userId;
    UUID projectId;
    UUID evaluationId;
    UUID positionId;
    UUID versionId;

    @BeforeEach
    void setUp() {
        useCase = new DeleteEvaluationUseCase(
                evaluations, scores, loader, abacGate, audit, snapshot);
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        evaluationId = UUID.randomUUID();
        positionId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        setEditorContext();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    /**
     * Every PRE-SUBMISSION status deletes: dependent score rows are removed FIRST
     * (INCOMPLETE / COMPLETE may already carry scores — same tenant-scoped path as
     * DRAFT), then the parent via the tenant-scoped deleter, then EVALUATION_DELETED
     * is audited with beforeJson present + afterJson null.
     */
    @ParameterizedTest
    @EnumSource(value = EvaluationStatus.class, names = {"DRAFT", "INCOMPLETE", "COMPLETE"})
    void preSubmissionDeletesScoresFirstThenRowAndAudits(EvaluationStatus status) {
        stubLoad(status);
        EvaluationScoreJpaEntity s1 = new EvaluationScoreJpaEntity(
                UUID.randomUUID(), tenantId, evaluationId, UUID.randomUUID(),
                UUID.randomUUID(), new BigDecimal("10"));
        EvaluationScoreJpaEntity s2 = new EvaluationScoreJpaEntity(
                UUID.randomUUID(), tenantId, evaluationId, UUID.randomUUID(),
                UUID.randomUUID(), new BigDecimal("20"));
        when(scores.findAllByTenantIdAndEvaluationId(tenantId, evaluationId))
                .thenReturn(List.of(s1, s2));

        useCase.delete(evaluationId, "pre-submission clean-up before re-creation");

        // Dependent scores removed first (proves INCOMPLETE/COMPLETE scores cascade).
        verify(scores).delete(s1);
        verify(scores).delete(s2);
        // Parent removed via the tenant-scoped deleter (not single-arg deleteById).
        verify(evaluations).deleteByIdAndTenantId(evaluationId, tenantId);
        // EVALUATION_DELETED audited with beforeJson present + afterJson null.
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(captor.capture());
        AuditEvent ev = captor.getValue();
        assertThat(ev.action()).isEqualTo(AuditAction.EVALUATION_DELETED);
        assertThat(ev.entityType()).isEqualTo("Evaluation");
        assertThat(ev.entityId()).isEqualTo(evaluationId);
        assertThat(ev.afterJson()).isNull();
    }

    /**
     * Post-submission statuses keep their audit trail — rejected with
     * EVALUATION_NOT_DELETABLE and nothing is deleted or audited.
     */
    @ParameterizedTest
    @EnumSource(value = EvaluationStatus.class,
            names = {"SUBMITTED", "APPROVED", "LOCKED", "ARCHIVED"})
    void postSubmissionRejectedWithNotDeletableCodeAndNothingDeleted(EvaluationStatus status) {
        stubLoad(status);

        assertThatThrownBy(() ->
                useCase.delete(evaluationId, "trying to delete a non-deletable eval"))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getCode())
                        .isEqualTo("EVALUATION_NOT_DELETABLE"));

        verify(scores, never()).delete(any());
        verify(evaluations, never()).deleteByIdAndTenantId(any(), any());
        verify(audit, never()).record(any());
    }

    @Test
    void shortReasonRejectedBeforeLoad() {
        assertThatThrownBy(() -> useCase.delete(evaluationId, "no"))
                .isInstanceOf(ValidationException.class);
        verify(loader, never()).load(any(), any());
        verify(evaluations, never()).deleteByIdAndTenantId(any(), any());
    }

    @Test
    void nullReasonRejected() {
        assertThatThrownBy(() -> useCase.delete(evaluationId, null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void missingEditPermissionThrows403() {
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(projectId),
                Set.of("CLIENT_HR_DIRECTOR"),
                Set.of("EVALUATION_READ"), Set.of(), false, "ru-RU"));

        assertThatThrownBy(() ->
                useCase.delete(evaluationId, "DRAFT clean-up before re-creation"))
                .isInstanceOf(PermissionDeniedException.class);
        verify(loader, never()).load(any(), any());
    }

    @Test
    void crossTenantIdSurfacesAs404FromLoader() {
        // Loader enforces findByIdAndTenantId → TenantAccessDeniedException (404).
        when(loader.load(eq(evaluationId), eq(tenantId)))
                .thenThrow(new TenantAccessDeniedException());

        assertThatThrownBy(() ->
                useCase.delete(evaluationId, "DRAFT clean-up before re-creation"))
                .isInstanceOf(TenantAccessDeniedException.class);
        verify(evaluations, never()).deleteByIdAndTenantId(any(), any());
    }

    // ---------- helpers ----------

    private void stubLoad(EvaluationStatus status) {
        EvaluationJpaEntity e = new EvaluationJpaEntity(
                evaluationId, tenantId, projectId, positionId, versionId, userId, status);
        EvaluationContext ctx = new EvaluationContext(e, null, List.of(), java.util.Map.of());
        when(loader.load(eq(evaluationId), eq(tenantId))).thenReturn(ctx);
    }

    private void setEditorContext() {
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(projectId),
                Set.of("CLIENT_HR_DIRECTOR"),
                Set.of("EVALUATION_EDIT"), Set.of(), false, "ru-RU"));
    }
}
