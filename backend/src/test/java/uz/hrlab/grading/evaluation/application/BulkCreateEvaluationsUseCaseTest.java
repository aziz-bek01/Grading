package uz.hrlab.grading.evaluation.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.evaluation.api.BulkCreateEvaluationsResponse;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BE-1 / BE-4(a) — {@link BulkCreateEvaluationsUseCase} per-row failure
 * collector. Mocks the inner {@link CreateEvaluationUseCase} so the focus is the
 * collector contract: valid rows count toward {@code created}; ABAC / cross-
 * tenant denial → {@code ACCESS_DENIED}; duplicate → {@code ALREADY_EXISTS};
 * failures key on {@code position_id} (not an evaluation id). The per-row
 * security checks themselves are owned (and tested) by CreateEvaluationUseCase.
 */
@ExtendWith(MockitoExtension.class)
class BulkCreateEvaluationsUseCaseTest {

    @Mock CreateEvaluationUseCase createUseCase;

    BulkCreateEvaluationsUseCase useCase;

    UUID tenantId;
    UUID userId;
    UUID projectId;
    UUID versionId;

    @BeforeEach
    void setUp() {
        useCase = new BulkCreateEvaluationsUseCase(createUseCase);
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        setEditorContext();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void allValidRowsCreated() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        when(createUseCase.create(any())).thenReturn(null);

        BulkCreateEvaluationsResponse res = useCase.execute(List.of(
                new CreateEvaluationCommand(p1, versionId, null),
                new CreateEvaluationCommand(p2, versionId, null)));

        assertThat(res.created()).isEqualTo(2);
        assertThat(res.failed()).isEmpty();
        // Each row routed through the single-create use case — no shortcut path.
        verify(createUseCase, times(2)).create(any());
    }

    @Test
    void outOfDepartmentRowSkippedWithAccessDenied() {
        UUID ok = UUID.randomUUID();
        UUID denied = UUID.randomUUID();
        when(createUseCase.create(any())).thenAnswer(inv -> {
            CreateEvaluationCommand cmd = inv.getArgument(0);
            if (cmd.positionId().equals(denied)) {
                // CreateEvaluationUseCase throws this when the ABAC department
                // write-gate denies (out-of-subtree) OR on a cross-tenant id.
                throw new TenantAccessDeniedException();
            }
            return null;
        });

        BulkCreateEvaluationsResponse res = useCase.execute(List.of(
                new CreateEvaluationCommand(ok, versionId, null),
                new CreateEvaluationCommand(denied, versionId, null)));

        assertThat(res.created()).isEqualTo(1);
        assertThat(res.failed()).hasSize(1);
        BulkCreateEvaluationsResponse.Failure f = res.failed().get(0);
        assertThat(f.positionId()).isEqualTo(denied);
        assertThat(f.errorCode()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void duplicateRowSkippedWithAlreadyExists() {
        UUID dup = UUID.randomUUID();
        when(createUseCase.create(any())).thenThrow(new ValidationException(
                "An active evaluation already exists for this position and methodology version"));

        BulkCreateEvaluationsResponse res = useCase.execute(List.of(
                new CreateEvaluationCommand(dup, versionId, null)));

        assertThat(res.created()).isZero();
        assertThat(res.failed()).hasSize(1);
        assertThat(res.failed().get(0).positionId()).isEqualTo(dup);
        assertThat(res.failed().get(0).errorCode()).isEqualTo("ALREADY_EXISTS");
    }

    @Test
    void missingEditPermissionThrows403() {
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(projectId),
                Set.of("CLIENT_HR_DIRECTOR"),
                Set.of("EVALUATION_READ"), Set.of(), false, "ru-RU"));

        assertThatThrownBy(() -> useCase.execute(List.of(
                new CreateEvaluationCommand(UUID.randomUUID(), versionId, null))))
                .isInstanceOf(PermissionDeniedException.class);
        verify(createUseCase, times(0)).create(any());
    }

    @Test
    void emptyRowsRejected() {
        assertThatThrownBy(() -> useCase.execute(List.of()))
                .isInstanceOf(ValidationException.class);
    }

    private void setEditorContext() {
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(projectId),
                Set.of("CLIENT_HR_DIRECTOR"),
                Set.of("EVALUATION_EDIT"), Set.of(), false, "ru-RU"));
    }
}
