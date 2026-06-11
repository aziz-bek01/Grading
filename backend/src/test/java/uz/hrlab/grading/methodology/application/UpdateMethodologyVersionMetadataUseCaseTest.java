package uz.hrlab.grading.methodology.application;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.methodology.domain.MethodologyStatus;
import uz.hrlab.grading.methodology.domain.MethodologyType;
import uz.hrlab.grading.methodology.domain.MethodologyVersion;
import uz.hrlab.grading.methodology.domain.MethodologyVersionImmutabilityPolicy;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatus;
import uz.hrlab.grading.methodology.domain.MethodologyVersionTransitionRejectedException;
import uz.hrlab.grading.methodology.domain.ScoringMode;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * BE-2 / BE-7 — DRAFT-only edit of version metadata (scoring_mode +
 * target_total_points).
 *
 * <ul>
 *   <li>DRAFT version: scoring_mode/target edit succeeds + factors preserved +
 *       METHODOLOGY_VERSION_UPDATED audit row with before/after.</li>
 *   <li>APPROVED/LOCKED/ARCHIVED version: rejected by ensureMutable (no save).</li>
 *   <li>cross-tenant id → TenantAccessDenied (404).</li>
 *   <li>missing METHODOLOGY_EDIT → PermissionDenied (403).</li>
 *   <li>WEIGHTED_SCALE with target &lt;= 0 → SCORING_TARGET_REQUIRED.</li>
 * </ul>
 *
 * <p>Pure Mockito unit test — no Spring context, no Testcontainers.
 */
@Tag("workflow")
class UpdateMethodologyVersionMetadataUseCaseTest {

    private MethodologyRepository methodologies;
    private MethodologyVersionRepository versions;
    private AbacGate abacGate;
    private AuditService audit;
    private MethodologyAuditSnapshot snapshot;

    private UpdateMethodologyVersionMetadataUseCase useCase;

    private UUID tenantId;
    private UUID userId;
    private UUID projectId;
    private UUID versionId;
    private UUID methodologyId;

    @BeforeEach
    void setUp() {
        methodologies = mock(MethodologyRepository.class);
        versions = mock(MethodologyVersionRepository.class);
        abacGate = mock(AbacGate.class);
        audit = mock(AuditService.class);
        snapshot = mock(MethodologyAuditSnapshot.class);
        given(snapshot.of(any(MethodologyVersionJpaEntity.class)))
                .willReturn(mock(JsonNode.class));
        given(versions.save(any(MethodologyVersionJpaEntity.class)))
                .willAnswer(inv -> inv.getArgument(0));

        useCase = new UpdateMethodologyVersionMetadataUseCase(methodologies, versions,
                abacGate, new MethodologyVersionImmutabilityPolicy(), audit, snapshot);

        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        methodologyId = UUID.randomUUID();

        setContext(Set.of(PermissionCodes.METHODOLOGY_EDIT));
    }

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    // --- Helpers ---------------------------------------------------------

    private void setContext(Set<String> permissions) {
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(projectId),
                Set.of("HRLAB_PROJECT_MANAGER"),
                permissions, Set.of(), false, "ru-RU"));
    }

    private MethodologyVersionJpaEntity wireVersion(MethodologyVersionStatus status,
                                                    ScoringMode mode,
                                                    BigDecimal target) {
        MethodologyVersionJpaEntity v = new MethodologyVersionJpaEntity(
                versionId, tenantId, methodologyId, 1, status, mode, target, null);
        MethodologyJpaEntity m = new MethodologyJpaEntity(
                methodologyId, tenantId, projectId, "M-1",
                MethodologyType.CUSTOM, MethodologyStatus.ACTIVE);
        given(versions.findByIdAndTenantId(versionId, tenantId)).willReturn(Optional.of(v));
        given(methodologies.findByIdAndTenantId(methodologyId, tenantId)).willReturn(Optional.of(m));
        return v;
    }

    // --- Tests -----------------------------------------------------------

    @Test
    void draftScoringModeAndTargetEditSucceedsAndAudits() {
        MethodologyVersionJpaEntity v = wireVersion(
                MethodologyVersionStatus.DRAFT, ScoringMode.DIRECT_POINTS, null);

        MethodologyVersion result = useCase.update(versionId,
                ScoringMode.WEIGHTED_SCALE, new BigDecimal("1000"));

        assertThat(result.scoringMode()).isEqualTo(ScoringMode.WEIGHTED_SCALE);
        assertThat(result.targetTotalPoints()).isEqualByComparingTo("1000");
        assertThat(v.getScoringMode()).isEqualTo(ScoringMode.WEIGHTED_SCALE);
        verify(versions, times(1)).save(v);

        ArgumentCaptor<AuditEvent> auditCap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, times(1)).record(auditCap.capture());
        AuditEvent event = auditCap.getValue();
        assertThat(event.action()).isEqualTo(AuditAction.METHODOLOGY_VERSION_UPDATED);
        assertThat(event.tenantId()).isEqualTo(tenantId);
        assertThat(event.actorUserId()).isEqualTo(userId);
        assertThat(event.projectId()).isEqualTo(projectId);
        assertThat(event.beforeJson()).isNotNull();
        assertThat(event.afterJson()).isNotNull();
    }

    @Test
    void partialUpdateLeavesUnspecifiedFieldUnchanged() {
        MethodologyVersionJpaEntity v = wireVersion(
                MethodologyVersionStatus.DRAFT, ScoringMode.DIRECT_POINTS,
                new BigDecimal("500"));

        // Only change the mode to WEIGHTED_POINTS; target stays null-arg → unchanged.
        useCase.update(versionId, ScoringMode.WEIGHTED_POINTS, null);

        assertThat(v.getScoringMode()).isEqualTo(ScoringMode.WEIGHTED_POINTS);
        assertThat(v.getTargetTotalPoints()).isEqualByComparingTo("500");
    }

    @Test
    void approvedVersionEditRejected() {
        wireVersion(MethodologyVersionStatus.APPROVED, ScoringMode.DIRECT_POINTS,
                new BigDecimal("1000"));

        assertThatThrownBy(() -> useCase.update(versionId,
                ScoringMode.WEIGHTED_POINTS, null))
                .isInstanceOf(MethodologyVersionTransitionRejectedException.class);
        verify(versions, never()).save(any());
        verify(audit, never()).record(any());
    }

    @Test
    void lockedVersionEditRejected() {
        wireVersion(MethodologyVersionStatus.LOCKED, ScoringMode.DIRECT_POINTS,
                new BigDecimal("1000"));

        assertThatThrownBy(() -> useCase.update(versionId,
                ScoringMode.WEIGHTED_POINTS, null))
                .isInstanceOf(MethodologyVersionTransitionRejectedException.class);
        verify(versions, never()).save(any());
    }

    @Test
    void archivedVersionEditRejected() {
        wireVersion(MethodologyVersionStatus.ARCHIVED, ScoringMode.DIRECT_POINTS,
                new BigDecimal("1000"));

        assertThatThrownBy(() -> useCase.update(versionId,
                ScoringMode.WEIGHTED_POINTS, null))
                .isInstanceOf(MethodologyVersionTransitionRejectedException.class);
        verify(versions, never()).save(any());
    }

    @Test
    void crossTenantVersionReturns404() {
        given(versions.findByIdAndTenantId(versionId, tenantId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.update(versionId,
                ScoringMode.WEIGHTED_POINTS, null))
                .isInstanceOf(TenantAccessDeniedException.class);
        verify(versions, never()).save(any());
    }

    @Test
    void missingEditPermissionRejected() {
        setContext(Set.of(PermissionCodes.METHODOLOGY_READ));

        assertThatThrownBy(() -> useCase.update(versionId,
                ScoringMode.WEIGHTED_POINTS, null))
                .isInstanceOf(PermissionDeniedException.class);
        verify(versions, never()).save(any());
    }

    @Test
    void weightedScaleWithNonPositiveTargetRejected() {
        wireVersion(MethodologyVersionStatus.DRAFT, ScoringMode.DIRECT_POINTS, null);

        assertThatThrownBy(() -> useCase.update(versionId,
                ScoringMode.WEIGHTED_SCALE, BigDecimal.ZERO))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("target_total_points");
        verify(versions, never()).save(any());
    }

    @Test
    void weightedScaleWithMissingTargetRejected() {
        // version already WEIGHTED_SCALE but with null target; mode unchanged,
        // target null-arg → effective target stays null → still invalid.
        wireVersion(MethodologyVersionStatus.DRAFT, ScoringMode.WEIGHTED_SCALE, null);

        assertThatThrownBy(() -> useCase.update(versionId, null, null))
                .isInstanceOf(ValidationException.class);
        verify(versions, never()).save(any());
    }
}
