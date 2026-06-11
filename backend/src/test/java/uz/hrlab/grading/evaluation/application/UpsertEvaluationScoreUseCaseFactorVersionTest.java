package uz.hrlab.grading.evaluation.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.domain.DepartmentScopePolicy;
import uz.hrlab.grading.access.domain.ProjectMembershipPolicy;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.evaluation.domain.EvaluationImmutabilityPolicy;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreRepository;
import uz.hrlab.grading.methodology.domain.Factor;
import uz.hrlab.grading.methodology.domain.FactorLevel;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Defense-in-depth regression for the "mixed methodology rows" defect: the
 * upsert-score path MUST reject a factor that does NOT belong to the
 * evaluation's own methodology version, even if a caller manages to point a
 * foreign-version factor at the evaluation (e.g. selecting an 8-factor
 * methodology's factor while a 12-factor HR-Lab evaluation is in the grid).
 *
 * <p>{@link EvaluationContextLoader} loads ONLY the factors of the evaluation's
 * version into {@link EvaluationContext#factors()}; a foreign factor id is
 * therefore absent and {@link UpsertEvaluationScoreUseCase} rejects it with an
 * explicit {@code FACTOR_VERSION_MISMATCH} code BEFORE any score row is written.
 * This complements the read-side K-sheet version scoping in
 * {@link EvaluationQueries#listByFactor}.
 */
@ExtendWith(MockitoExtension.class)
class UpsertEvaluationScoreUseCaseFactorVersionTest {

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
    UUID departmentId;
    UUID ownVersionFactorId;     // a factor that IS in the evaluation's version
    UUID ownVersionLevelId;
    UUID foreignVersionFactorId; // a factor from a DIFFERENT methodology version

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
        departmentId = UUID.randomUUID();
        ownVersionFactorId = UUID.randomUUID();
        ownVersionLevelId = UUID.randomUUID();
        foreignVersionFactorId = UUID.randomUUID();

        // Bypass role so the department write-gate PERMITs and we reach the
        // factor-membership guard (the focus of this test).
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(projectId),
                Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of("EVALUATION_EDIT"), Set.of(), false, "ru-RU"));
    }

    @AfterEach
    void tearDown() { TenantContextHolder.clear(); }

    @Test
    void foreignVersionFactorIsRejectedWithVersionMismatchBeforeAnyWrite() {
        stubLoaderWithOwnVersionFactorOnly();
        stubPosition();

        assertThatThrownBy(() -> useCase.upsert(new UpsertEvaluationScoreCommand(
                evaluationId, foreignVersionFactorId, UUID.randomUUID(), null)))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(
                        ((ValidationException) ex).getCode()).isEqualTo("FACTOR_VERSION_MISMATCH"));

        // No score persisted, no recompute, evaluation not saved, no score audit.
        verify(scores, never()).save(any());
        verify(recompute, never()).recompute(any());
        verify(evaluations, never()).save(any());
        verify(audit, never()).record(any());
    }

    @Test
    void ownVersionFactorIsAccepted() {
        EvaluationContext ctx = stubLoaderWithOwnVersionFactorOnly();
        stubPosition();
        when(scores.findByTenantIdAndEvaluationIdAndFactorId(
                tenantId, evaluationId, ownVersionFactorId)).thenReturn(Optional.empty());
        when(recompute.preview(any(), any()))
                .thenReturn(new uz.hrlab.grading.evaluation.domain.ScoringResult(
                        new BigDecimal("10.0000"),
                        new BigDecimal("10.00"),
                        Map.of(ownVersionFactorId, new BigDecimal("10.0000"))));

        useCase.upsert(new UpsertEvaluationScoreCommand(
                evaluationId, ownVersionFactorId, ownVersionLevelId, null));

        verify(scores).save(any());
        verify(recompute).recompute(ctx);
    }

    // --------------------------------------------------------------- helpers

    private EvaluationContext stubLoaderWithOwnVersionFactorOnly() {
        EvaluationJpaEntity evaluation = new EvaluationJpaEntity(
                evaluationId, tenantId, projectId, positionId, UUID.randomUUID(),
                userId, EvaluationStatus.DRAFT);
        // Context carries ONLY the evaluation's own-version factor + its levels.
        Factor own = new Factor(ownVersionFactorId, tenantId, UUID.randomUUID(), "F1",
                Map.of("ru-RU", "F1"), null,
                new BigDecimal("100"), new BigDecimal("100"), 1, true);
        FactorLevel level = new FactorLevel(ownVersionLevelId, tenantId, ownVersionFactorId,
                "L1", 1, new BigDecimal("10"), new BigDecimal("1"),
                Map.of("ru-RU", "L1"), null);
        EvaluationContext ctx = new EvaluationContext(
                evaluation, null, List.of(own),
                Map.of(ownVersionFactorId, List.of(level)));
        when(loader.load(evaluationId, tenantId)).thenReturn(ctx);
        return ctx;
    }

    private void stubPosition() {
        PositionJpaEntity p = new PositionJpaEntity(
                positionId, tenantId, projectId, departmentId, "P-001",
                Map.of("ru-RU", "Position"), null, null, null, null, PositionStatus.ACTIVE);
        when(positions.findByIdAndTenantId(positionId, tenantId)).thenReturn(Optional.of(p));
    }
}
