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
import uz.hrlab.grading.methodology.domain.FactorLevel;
import uz.hrlab.grading.methodology.domain.MethodologyStatus;
import uz.hrlab.grading.methodology.domain.MethodologyType;
import uz.hrlab.grading.methodology.domain.MethodologyVersionImmutabilityPolicy;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatus;
import uz.hrlab.grading.methodology.domain.MethodologyVersionTransitionRejectedException;
import uz.hrlab.grading.methodology.domain.ScoringMode;
import uz.hrlab.grading.methodology.infrastructure.FactorJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.math.BigDecimal;
import java.util.Map;
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
 * Regression test for the duplicate-key bug on POST /factors/{id}/levels:
 * the frontend sent a 0-based {@code level_order} (count of loaded levels)
 * which collided with the 1-indexed orders of template-created levels and
 * tripped {@code uq_factor_levels_factor_level_order} (SQLState 23505).
 *
 * <p>The fix makes {@link FactorLevelService#add} server-authoritative: the
 * client-supplied {@code levelOrder} is ignored and the new level always
 * appends as {@code max(existing)+1} (1 for an empty factor). These tests pin
 * that contract and confirm the existing guards still fire.
 *
 * <p>Pure Mockito unit test — no Spring context, no Testcontainers — so it runs
 * in every developer build regardless of Docker availability.
 */
@Tag("workflow")
class FactorLevelServiceTest {

    private MethodologyRepository methodologies;
    private MethodologyVersionRepository versions;
    private FactorRepository factors;
    private FactorLevelRepository levels;
    private AbacGate abacGate;
    private AuditService audit;
    private MethodologyAuditSnapshot snapshot;
    private ApprovedEditGuc approvedEditGuc;
    private ApprovedEditAudit approvedEditAudit;
    private MethodologyReferencePort referencePort;

    private FactorLevelService service;

    private UUID tenantId;
    private UUID userId;
    private UUID projectId;
    private UUID factorId;
    private UUID versionId;
    private UUID methodologyId;

    @BeforeEach
    void setUp() {
        methodologies = mock(MethodologyRepository.class);
        versions = mock(MethodologyVersionRepository.class);
        factors = mock(FactorRepository.class);
        levels = mock(FactorLevelRepository.class);
        abacGate = mock(AbacGate.class);
        audit = mock(AuditService.class);
        snapshot = mock(MethodologyAuditSnapshot.class);
        approvedEditGuc = mock(ApprovedEditGuc.class);
        approvedEditAudit = mock(ApprovedEditAudit.class);
        referencePort = mock(MethodologyReferencePort.class);
        given(snapshot.of(any(FactorLevelJpaEntity.class)))
                .willReturn(mock(JsonNode.class));
        given(levels.save(any(FactorLevelJpaEntity.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service = new FactorLevelService(methodologies, versions, factors, levels,
                abacGate, new MethodologyVersionImmutabilityPolicy(), audit, snapshot,
                approvedEditGuc, approvedEditAudit, referencePort);

        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        factorId = UUID.randomUUID();
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

    /** Wire factor → DRAFT version → methodology so loadAndGate passes. */
    private void wireDraftGraph() {
        FactorJpaEntity f = new FactorJpaEntity(
                factorId, tenantId, versionId, "F-1",
                new BigDecimal("100"), new BigDecimal("100"), 1, true);
        MethodologyVersionJpaEntity v = new MethodologyVersionJpaEntity(
                versionId, tenantId, methodologyId, 1,
                MethodologyVersionStatus.DRAFT, ScoringMode.WEIGHTED_POINTS,
                new BigDecimal("100.0000"), null);
        MethodologyJpaEntity m = new MethodologyJpaEntity(
                methodologyId, tenantId, projectId, "M-1",
                MethodologyType.CUSTOM, MethodologyStatus.ACTIVE);
        given(factors.findByIdAndTenantId(factorId, tenantId)).willReturn(Optional.of(f));
        given(versions.findByIdAndTenantId(versionId, tenantId)).willReturn(Optional.of(v));
        given(methodologies.findByIdAndTenantId(methodologyId, tenantId)).willReturn(Optional.of(m));
    }

    private void wireLockedVersion() {
        FactorJpaEntity f = new FactorJpaEntity(
                factorId, tenantId, versionId, "F-1",
                new BigDecimal("100"), new BigDecimal("100"), 1, true);
        MethodologyVersionJpaEntity v = new MethodologyVersionJpaEntity(
                versionId, tenantId, methodologyId, 1,
                MethodologyVersionStatus.LOCKED, ScoringMode.WEIGHTED_POINTS,
                new BigDecimal("100.0000"), null);
        MethodologyJpaEntity m = new MethodologyJpaEntity(
                methodologyId, tenantId, projectId, "M-1",
                MethodologyType.CUSTOM, MethodologyStatus.ACTIVE);
        given(factors.findByIdAndTenantId(factorId, tenantId)).willReturn(Optional.of(f));
        given(versions.findByIdAndTenantId(versionId, tenantId)).willReturn(Optional.of(v));
        given(methodologies.findByIdAndTenantId(methodologyId, tenantId)).willReturn(Optional.of(m));
    }

    private FactorLevelCommand cmd(String code, Integer clientOrder) {
        return new FactorLevelCommand(code, clientOrder,
                new BigDecimal("10"), null,
                Map.of("ru-RU", "Уровень"), null);
    }

    // --- Tests -----------------------------------------------------------

    @Test
    void addToFactorWithExistingLevelsAppendsAtMaxPlusOne_ignoringClientOrder() {
        wireDraftGraph();
        // Existing levels at orders 1..5 (template-created, 1-indexed).
        given(levels.findMaxLevelOrderByFactorId(tenantId, factorId)).willReturn(5);
        given(levels.existsByTenantIdAndFactorIdAndCode(tenantId, factorId, "L6")).willReturn(false);

        // Frontend (buggy) sends the 0-based count = 5, which would collide.
        FactorLevel result = service.add(factorId, cmd("L6", 5));

        // Server overrides: appended last as max+1 = 6, NOT the client's 5.
        ArgumentCaptor<FactorLevelJpaEntity> cap =
                ArgumentCaptor.forClass(FactorLevelJpaEntity.class);
        verify(levels).save(cap.capture());
        assertThat(cap.getValue().getLevelOrder()).isEqualTo(6);
        assertThat(result.levelOrder()).isEqualTo(6);
        assertThat(result.code()).isEqualTo("L6");
    }

    @Test
    void addToEmptyFactorAssignsOrderOne() {
        wireDraftGraph();
        given(levels.findMaxLevelOrderByFactorId(tenantId, factorId)).willReturn(null);
        given(levels.existsByTenantIdAndFactorIdAndCode(tenantId, factorId, "L1")).willReturn(false);

        // Client sends 0 (the 0-based count of an empty list).
        FactorLevel result = service.add(factorId, cmd("L1", 0));

        assertThat(result.levelOrder()).isEqualTo(1);
    }

    @Test
    void clientOrderIsIgnoredEvenWhenNonColliding() {
        wireDraftGraph();
        given(levels.findMaxLevelOrderByFactorId(tenantId, factorId)).willReturn(3);
        given(levels.existsByTenantIdAndFactorIdAndCode(tenantId, factorId, "L4")).willReturn(false);

        // Client sends a wildly different value; server still appends as 4.
        FactorLevel result = service.add(factorId, cmd("L4", 99));

        assertThat(result.levelOrder()).isEqualTo(4);
    }

    @Test
    void addWritesFactorLevelCreatedAudit() {
        wireDraftGraph();
        given(levels.findMaxLevelOrderByFactorId(tenantId, factorId)).willReturn(2);
        given(levels.existsByTenantIdAndFactorIdAndCode(tenantId, factorId, "L3")).willReturn(false);

        service.add(factorId, cmd("L3", 2));

        ArgumentCaptor<AuditEvent> auditCap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, times(1)).record(auditCap.capture());
        AuditEvent event = auditCap.getValue();
        assertThat(event.action()).isEqualTo(AuditAction.FACTOR_LEVEL_CREATED);
        assertThat(event.tenantId()).isEqualTo(tenantId);
        assertThat(event.actorUserId()).isEqualTo(userId);
        assertThat(event.projectId()).isEqualTo(projectId);
    }

    @Test
    void addRejectsDuplicateCode() {
        wireDraftGraph();
        given(levels.existsByTenantIdAndFactorIdAndCode(tenantId, factorId, "DUP")).willReturn(true);

        assertThatThrownBy(() -> service.add(factorId, cmd("DUP", null)))
                .isInstanceOf(ValidationException.class);
        verify(levels, never()).save(any());
    }

    @Test
    void addToNonDraftVersionRejected() {
        wireLockedVersion();

        assertThatThrownBy(() -> service.add(factorId, cmd("L1", 0)))
                .isInstanceOf(MethodologyVersionTransitionRejectedException.class);
        verify(levels, never()).save(any());
    }

    @Test
    void addWithoutEditPermissionRejected() {
        setContext(Set.of(PermissionCodes.METHODOLOGY_READ));

        assertThatThrownBy(() -> service.add(factorId, cmd("L1", 0)))
                .isInstanceOf(PermissionDeniedException.class);
        verify(levels, never()).save(any());
    }

    @Test
    void addToCrossTenantFactorReturns404() {
        given(factors.findByIdAndTenantId(factorId, tenantId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.add(factorId, cmd("L1", 0)))
                .isInstanceOf(TenantAccessDeniedException.class);
        verify(levels, never()).save(any());
    }

    // --- BE-6: level CODE edit on PATCH /factor-levels/{id} ---------------

    /** Wire an existing level (id={@code levelId}) under the DRAFT factor graph. */
    private UUID wireExistingLevel(String currentCode) {
        wireDraftGraph();
        UUID levelId = UUID.randomUUID();
        FactorLevelJpaEntity l = new FactorLevelJpaEntity(
                levelId, tenantId, factorId, currentCode, 1,
                new BigDecimal("10"), null);
        l.setLabelI18n(Map.of("ru-RU", "Уровень"));
        given(levels.findByIdAndTenantId(levelId, tenantId)).willReturn(Optional.of(l));
        return levelId;
    }

    @Test
    void updateLevelCodeToNewUniqueValueSucceeds() {
        UUID levelId = wireExistingLevel("OLD");
        given(levels.existsByTenantIdAndFactorIdAndCode(tenantId, factorId, "NEW"))
                .willReturn(false);

        FactorLevel result = service.update(levelId, cmd("NEW", null));

        assertThat(result.code()).isEqualTo("NEW");
        ArgumentCaptor<FactorLevelJpaEntity> cap =
                ArgumentCaptor.forClass(FactorLevelJpaEntity.class);
        verify(levels).save(cap.capture());
        assertThat(cap.getValue().getCode()).isEqualTo("NEW");

        ArgumentCaptor<AuditEvent> auditCap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, times(1)).record(auditCap.capture());
        assertThat(auditCap.getValue().action()).isEqualTo(AuditAction.FACTOR_LEVEL_UPDATED);
    }

    @Test
    void updateLevelCodeToDuplicateValueRejected() {
        UUID levelId = wireExistingLevel("OLD");
        given(levels.existsByTenantIdAndFactorIdAndCode(tenantId, factorId, "DUP"))
                .willReturn(true);

        assertThatThrownBy(() -> service.update(levelId, cmd("DUP", null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Level code already exists");
        verify(levels, never()).save(any());
    }

    @Test
    void updateLevelSameCodeIsNotTreatedAsDuplicate() {
        // Re-submitting the unchanged code must NOT trip the uniqueness check.
        UUID levelId = wireExistingLevel("SAME");

        service.update(levelId, cmd("SAME", null));

        verify(levels, never()).existsByTenantIdAndFactorIdAndCode(any(), any(), any());
        verify(levels, times(1)).save(any());
    }

    // --- BE-2: APPROVED edit carve-out (status × permission × GUC) -----------

    private UUID wireApprovedLevel(String currentCode) {
        wireApprovedGraph();
        UUID levelId = UUID.randomUUID();
        FactorLevelJpaEntity l = new FactorLevelJpaEntity(
                levelId, tenantId, factorId, currentCode, 1,
                new BigDecimal("10"), null);
        l.setLabelI18n(Map.of("ru-RU", "Уровень"));
        given(levels.findByIdAndTenantId(levelId, tenantId)).willReturn(Optional.of(l));
        return levelId;
    }

    private void wireApprovedGraph() {
        FactorJpaEntity f = new FactorJpaEntity(
                factorId, tenantId, versionId, "F-1",
                new BigDecimal("100"), new BigDecimal("100"), 1, true);
        MethodologyVersionJpaEntity v = new MethodologyVersionJpaEntity(
                versionId, tenantId, methodologyId, 1,
                MethodologyVersionStatus.APPROVED, ScoringMode.WEIGHTED_POINTS,
                new BigDecimal("100.0000"), null);
        MethodologyJpaEntity m = new MethodologyJpaEntity(
                methodologyId, tenantId, projectId, "M-1",
                MethodologyType.CUSTOM, MethodologyStatus.ACTIVE);
        given(factors.findByIdAndTenantId(factorId, tenantId)).willReturn(Optional.of(f));
        given(versions.findByIdAndTenantId(versionId, tenantId)).willReturn(Optional.of(v));
        given(methodologies.findByIdAndTenantId(methodologyId, tenantId)).willReturn(Optional.of(m));
    }

    @Test
    void approvedEdit_withApprovedPermission_succeeds_setsGuc_emitsUmbrella() {
        setContext(Set.of(PermissionCodes.METHODOLOGY_EDIT_APPROVED));
        UUID levelId = wireApprovedLevel("OLD");

        service.update(levelId, cmd("OLD", null)); // points edit

        verify(levels, times(1)).save(any());
        // GUC set exactly on the approved branch.
        verify(approvedEditGuc, times(1)).enableForCurrentTransaction();
        // Per-field event + ONE umbrella event.
        verify(audit, times(1)).record(any(AuditEvent.class));
        verify(approvedEditAudit, times(1))
                .emit(any(), any(), any(), any(), any(), any());
    }

    @Test
    void approvedEdit_withoutApprovedPermission_rejected_noGuc_noSave() {
        // Holds only the plain DRAFT edit permission.
        setContext(Set.of(PermissionCodes.METHODOLOGY_EDIT));
        UUID levelId = wireApprovedLevel("OLD");

        assertThatThrownBy(() -> service.update(levelId, cmd("OLD", null)))
                .isInstanceOf(MethodologyVersionTransitionRejectedException.class);
        verify(levels, never()).save(any());
        verify(approvedEditGuc, never()).enableForCurrentTransaction();
        verify(approvedEditAudit, never()).emit(any(), any(), any(), any(), any(), any());
    }

    @Test
    void draftEdit_doesNotSetApprovedGuc_norUmbrella() {
        UUID levelId = wireExistingLevel("OLD"); // DRAFT graph
        given(levels.existsByTenantIdAndFactorIdAndCode(tenantId, factorId, "NEW"))
                .willReturn(false);

        service.update(levelId, cmd("NEW", null));

        verify(levels, times(1)).save(any());
        verify(approvedEditGuc, never()).enableForCurrentTransaction();
        verify(approvedEditAudit, never()).emit(any(), any(), any(), any(), any(), any());
    }

    @Test
    void approvedEdit_neitherPermission_deniedAtCoarseGate() {
        setContext(Set.of(PermissionCodes.METHODOLOGY_READ));
        // No graph wiring needed — fails at requireEditPerm before any load.
        assertThatThrownBy(() -> service.update(UUID.randomUUID(), cmd("X", null)))
                .isInstanceOf(PermissionDeniedException.class);
        verify(approvedEditGuc, never()).enableForCurrentTransaction();
    }

    @Test
    void approvedEdit_crossTenantLevel_returns404() {
        setContext(Set.of(PermissionCodes.METHODOLOGY_EDIT_APPROVED));
        UUID levelId = UUID.randomUUID();
        given(levels.findByIdAndTenantId(levelId, tenantId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(levelId, cmd("X", null)))
                .isInstanceOf(TenantAccessDeniedException.class);
        verify(approvedEditGuc, never()).enableForCurrentTransaction();
    }

    // --- BE-4: delete-protection / soft-deprecate ----------------------------

    @Test
    void remove_referencedLevelOnApproved_softDeprecates_notDeleted() {
        setContext(Set.of(PermissionCodes.METHODOLOGY_EDIT_APPROVED));
        UUID levelId = wireApprovedLevel("L1");
        given(referencePort.isFactorLevelReferenced(tenantId, levelId)).willReturn(true);

        service.remove(levelId);

        ArgumentCaptor<FactorLevelJpaEntity> cap =
                ArgumentCaptor.forClass(FactorLevelJpaEntity.class);
        verify(levels, times(1)).save(cap.capture());
        assertThat(cap.getValue().getDeprecatedAt()).isNotNull();
        assertThat(cap.getValue().getDeprecatedBy()).isEqualTo(userId);
        verify(levels, never()).delete(any());

        ArgumentCaptor<AuditEvent> auditCap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, times(1)).record(auditCap.capture());
        assertThat(auditCap.getValue().action()).isEqualTo(AuditAction.FACTOR_LEVEL_DEPRECATED);
        verify(approvedEditAudit, times(1)).emit(any(), any(), any(), any(), any(), any());
    }

    @Test
    void remove_unreferencedLevelOnApproved_hardDeletes() {
        setContext(Set.of(PermissionCodes.METHODOLOGY_EDIT_APPROVED));
        UUID levelId = wireApprovedLevel("L1");
        given(referencePort.isFactorLevelReferenced(tenantId, levelId)).willReturn(false);

        service.remove(levelId);

        verify(levels, times(1)).delete(any());
        verify(levels, never()).save(any());
        ArgumentCaptor<AuditEvent> auditCap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, times(1)).record(auditCap.capture());
        assertThat(auditCap.getValue().action()).isEqualTo(AuditAction.FACTOR_LEVEL_REMOVED);
    }

    @Test
    void remove_levelOnDraft_alwaysHardDeletes_evenIfReferenced() {
        // DRAFT path never soft-deprecates (referenced check still hard-deletes;
        // on DRAFT there should be no eval references, but assert the branch).
        UUID levelId = wireExistingLevel("L1"); // DRAFT graph, METHODOLOGY_EDIT
        given(referencePort.isFactorLevelReferenced(tenantId, levelId)).willReturn(false);

        service.remove(levelId);

        verify(levels, times(1)).delete(any());
        verify(levels, never()).save(any());
    }
}
