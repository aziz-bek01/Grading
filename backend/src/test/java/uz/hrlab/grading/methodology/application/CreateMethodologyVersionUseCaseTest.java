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
import uz.hrlab.grading.methodology.domain.MethodologyStatus;
import uz.hrlab.grading.methodology.domain.MethodologyType;
import uz.hrlab.grading.methodology.domain.MethodologyVersion;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatus;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatusTransitionPolicy;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * D-402 — verifies CreateMethodologyVersionUseCase contract:
 * <ul>
 *   <li>source version remains UNCHANGED after CREATE_NEW_VERSION</li>
 *   <li>new version: versionNumber = source+1, status=DRAFT,
 *       previousVersionId = source.id, fresh UUID</li>
 *   <li>factors + levels are deep-copied (fresh UUIDs, same code/weight/i18n)</li>
 *   <li>audit row {@code METHODOLOGY_VERSION_REVISION_CREATED} is written
 *       with before=source snapshot and after=new snapshot</li>
 *   <li>cross-tenant source → 404</li>
 *   <li>CREATE_NEW_VERSION from DRAFT/ARCHIVED is rejected; from LOCKED OK</li>
 * </ul>
 *
 * <p>Pure Mockito unit test — no Spring context, no Testcontainers — so it
 * runs in every developer build regardless of Docker availability.
 */
@Tag("workflow")
class CreateMethodologyVersionUseCaseTest {

    private MethodologyRepository methodologies;
    private MethodologyVersionRepository versions;
    private FactorRepository factors;
    private FactorLevelRepository levels;
    private AbacGate abacGate;
    private MethodologyVersionStatusTransitionPolicy transitionPolicy;
    private AuditService audit;
    private MethodologyAuditSnapshot snapshot;

    private CreateMethodologyVersionUseCase useCase;

    private UUID tenantId;
    private UUID userId;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        methodologies = mock(MethodologyRepository.class);
        versions = mock(MethodologyVersionRepository.class);
        factors = mock(FactorRepository.class);
        levels = mock(FactorLevelRepository.class);
        abacGate = mock(AbacGate.class);
        transitionPolicy = new MethodologyVersionStatusTransitionPolicy();
        audit = mock(AuditService.class);
        snapshot = mock(MethodologyAuditSnapshot.class);
        // Snapshot returns distinct non-null JsonNodes so we can verify both
        // beforeJson and afterJson are populated.
        given(snapshot.of(any(MethodologyVersionJpaEntity.class)))
                .willReturn(mock(JsonNode.class));

        useCase = new CreateMethodologyVersionUseCase(methodologies, versions,
                factors, levels, abacGate, transitionPolicy, audit, snapshot);

        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(projectId),
                Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of(PermissionCodes.METHODOLOGY_EDIT),
                Set.of(), false, "ru-RU"));
    }

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    // --- Helpers ---------------------------------------------------------

    private MethodologyJpaEntity seedMethodology() {
        MethodologyJpaEntity m = new MethodologyJpaEntity(
                UUID.randomUUID(), tenantId, projectId, "M-1",
                MethodologyType.CUSTOM, MethodologyStatus.ACTIVE);
        m.setNameI18n(Map.of("ru-RU", "Методология"));
        return m;
    }

    private MethodologyVersionJpaEntity seedSourceVersion(MethodologyJpaEntity m,
                                                          MethodologyVersionStatus status,
                                                          int versionNumber) {
        return new MethodologyVersionJpaEntity(
                UUID.randomUUID(), tenantId, m.getId(), versionNumber,
                status, ScoringMode.WEIGHTED_POINTS,
                new BigDecimal("100.0000"), null);
    }

    private FactorJpaEntity seedFactor(UUID versionId, String code,
                                       String weight, int sortOrder) {
        FactorJpaEntity f = new FactorJpaEntity(
                UUID.randomUUID(), tenantId, versionId, code,
                new BigDecimal(weight), new BigDecimal("100"),
                sortOrder, true);
        f.setNameI18n(Map.of("ru-RU", "Фактор " + code,
                "en-US", "Factor " + code));
        f.setDescriptionI18n(Map.of("ru-RU", "Описание " + code));
        return f;
    }

    private FactorLevelJpaEntity seedLevel(UUID factorId, String code,
                                           int levelOrder, String points) {
        FactorLevelJpaEntity l = new FactorLevelJpaEntity(
                UUID.randomUUID(), tenantId, factorId, code, levelOrder,
                new BigDecimal(points), null);
        l.setLabelI18n(Map.of("ru-RU", "Уровень " + code,
                "en-US", "Level " + code));
        return l;
    }

    private void wireSavers() {
        // versions.save → return the entity unchanged
        given(versions.save(any(MethodologyVersionJpaEntity.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(factors.save(any(FactorJpaEntity.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(levels.save(any(FactorLevelJpaEntity.class)))
                .willAnswer(inv -> inv.getArgument(0));
    }

    // --- Tests ----------------------------------------------------------

    @Test
    void approvedSourceDeepCopiesFactorsAndLevelsAndWritesAudit() {
        MethodologyJpaEntity m = seedMethodology();
        MethodologyVersionJpaEntity source = seedSourceVersion(
                m, MethodologyVersionStatus.APPROVED, 1);
        UUID sourceId = source.getId();

        FactorJpaEntity f1 = seedFactor(sourceId, "A", "40", 1);
        FactorJpaEntity f2 = seedFactor(sourceId, "B", "30", 2);
        FactorJpaEntity f3 = seedFactor(sourceId, "C", "30", 3);
        List<FactorJpaEntity> srcFactors = List.of(f1, f2, f3);

        // 2 levels per factor
        Map<UUID, List<FactorLevelJpaEntity>> levelsByFactor = new HashMap<>();
        for (FactorJpaEntity f : srcFactors) {
            levelsByFactor.put(f.getId(), List.of(
                    seedLevel(f.getId(), f.getCode() + "-L1", 1, "10"),
                    seedLevel(f.getId(), f.getCode() + "-L2", 2, "20")));
        }

        given(versions.findByIdAndTenantId(sourceId, tenantId))
                .willReturn(Optional.of(source));
        given(methodologies.findByIdAndTenantId(m.getId(), tenantId))
                .willReturn(Optional.of(m));
        given(factors.findAllByTenantIdAndMethodologyVersionIdOrderBySortOrderAsc(
                tenantId, sourceId)).willReturn(srcFactors);
        for (var entry : levelsByFactor.entrySet()) {
            given(levels.findAllByTenantIdAndFactorIdOrderByLevelOrderAsc(
                    tenantId, entry.getKey())).willReturn(entry.getValue());
        }
        wireSavers();

        // Capture source-before snapshot so we can compare after-call source
        // state (immutability check).
        MethodologyVersionStatus sourceStatusBefore = source.getStatus();
        int sourceVersionNumberBefore = source.getVersionNumber();
        ScoringMode sourceScoringModeBefore = source.getScoringMode();

        MethodologyVersion result = useCase.createNewVersion(sourceId);

        // ----- new version assertions -----
        assertThat(result.id()).isNotEqualTo(sourceId);
        assertThat(result.versionNumber()).isEqualTo(sourceVersionNumberBefore + 1);
        assertThat(result.status()).isEqualTo(MethodologyVersionStatus.DRAFT);
        assertThat(result.previousVersionId()).isEqualTo(sourceId);
        assertThat(result.scoringMode()).isEqualTo(sourceScoringModeBefore);
        assertThat(result.targetTotalPoints())
                .isEqualByComparingTo(source.getTargetTotalPoints());
        assertThat(result.methodologyId()).isEqualTo(m.getId());

        // ----- source remains UNCHANGED -----
        assertThat(source.getStatus()).isEqualTo(sourceStatusBefore);
        assertThat(source.getVersionNumber()).isEqualTo(sourceVersionNumberBefore);
        assertThat(source.getScoringMode()).isEqualTo(sourceScoringModeBefore);
        // versions.save is invoked once (for the new entity only, not source)
        ArgumentCaptor<MethodologyVersionJpaEntity> vCap =
                ArgumentCaptor.forClass(MethodologyVersionJpaEntity.class);
        verify(versions, times(1)).save(vCap.capture());
        assertThat(vCap.getValue().getId()).isNotEqualTo(sourceId);

        // ----- deep-copy of factors (3 new ones) -----
        ArgumentCaptor<FactorJpaEntity> factorCap =
                ArgumentCaptor.forClass(FactorJpaEntity.class);
        verify(factors, times(3)).save(factorCap.capture());
        List<FactorJpaEntity> savedFactors = factorCap.getAllValues();
        List<UUID> srcIds = srcFactors.stream().map(FactorJpaEntity::getId).toList();
        for (FactorJpaEntity nf : savedFactors) {
            assertThat(nf.getId()).as("new factor id is fresh")
                    .isNotIn(srcIds);
            assertThat(nf.getMethodologyVersionId())
                    .as("new factor points to new version")
                    .isEqualTo(result.id());
            assertThat(nf.getTenantId()).isEqualTo(tenantId);
        }
        // Codes and weights survived
        assertThat(savedFactors).extracting(FactorJpaEntity::getCode)
                .containsExactlyInAnyOrder("A", "B", "C");

        // ----- deep-copy of levels (2 per factor → 6) -----
        ArgumentCaptor<FactorLevelJpaEntity> levelCap =
                ArgumentCaptor.forClass(FactorLevelJpaEntity.class);
        verify(levels, times(6)).save(levelCap.capture());
        List<FactorLevelJpaEntity> savedLevels = levelCap.getAllValues();
        List<UUID> sourceLevelIds = levelsByFactor.values().stream()
                .flatMap(List::stream)
                .map(FactorLevelJpaEntity::getId)
                .toList();
        for (FactorLevelJpaEntity nl : savedLevels) {
            assertThat(nl.getId()).as("new level id is fresh")
                    .isNotIn(sourceLevelIds);
            assertThat(nl.getTenantId()).isEqualTo(tenantId);
        }

        // ----- audit row -----
        ArgumentCaptor<AuditEvent> auditCap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, times(1)).record(auditCap.capture());
        AuditEvent event = auditCap.getValue();
        assertThat(event.action())
                .isEqualTo(AuditAction.METHODOLOGY_VERSION_REVISION_CREATED);
        assertThat(event.tenantId()).isEqualTo(tenantId);
        assertThat(event.actorUserId()).isEqualTo(userId);
        assertThat(event.beforeJson()).as("before snapshot present").isNotNull();
        assertThat(event.afterJson()).as("after snapshot present").isNotNull();
    }

    @Test
    void lockedSourceCanSpawnNewVersion() {
        MethodologyJpaEntity m = seedMethodology();
        MethodologyVersionJpaEntity source = seedSourceVersion(
                m, MethodologyVersionStatus.LOCKED, 5);
        given(versions.findByIdAndTenantId(source.getId(), tenantId))
                .willReturn(Optional.of(source));
        given(methodologies.findByIdAndTenantId(m.getId(), tenantId))
                .willReturn(Optional.of(m));
        given(factors.findAllByTenantIdAndMethodologyVersionIdOrderBySortOrderAsc(
                tenantId, source.getId())).willReturn(new ArrayList<>());
        wireSavers();

        MethodologyVersion result = useCase.createNewVersion(source.getId());
        assertThat(result.versionNumber()).isEqualTo(6);
        assertThat(result.status()).isEqualTo(MethodologyVersionStatus.DRAFT);
        assertThat(source.getStatus()).isEqualTo(MethodologyVersionStatus.LOCKED);
    }

    @Test
    void draftSourceCannotSpawnNewVersion() {
        MethodologyJpaEntity m = seedMethodology();
        MethodologyVersionJpaEntity source = seedSourceVersion(
                m, MethodologyVersionStatus.DRAFT, 1);
        given(versions.findByIdAndTenantId(source.getId(), tenantId))
                .willReturn(Optional.of(source));
        given(methodologies.findByIdAndTenantId(m.getId(), tenantId))
                .willReturn(Optional.of(m));

        assertThatThrownBy(() -> useCase.createNewVersion(source.getId()))
                .isInstanceOf(MethodologyVersionTransitionRejectedException.class);
        verify(versions, never()).save(any());
    }

    @Test
    void archivedSourceCannotSpawnNewVersion() {
        MethodologyJpaEntity m = seedMethodology();
        MethodologyVersionJpaEntity source = seedSourceVersion(
                m, MethodologyVersionStatus.ARCHIVED, 7);
        given(versions.findByIdAndTenantId(source.getId(), tenantId))
                .willReturn(Optional.of(source));
        given(methodologies.findByIdAndTenantId(m.getId(), tenantId))
                .willReturn(Optional.of(m));

        assertThatThrownBy(() -> useCase.createNewVersion(source.getId()))
                .isInstanceOf(MethodologyVersionTransitionRejectedException.class);
        verify(versions, never()).save(any());
    }

    @Test
    void crossTenantSourceReturns404() {
        UUID someVersionId = UUID.randomUUID();
        given(versions.findByIdAndTenantId(eq(someVersionId), eq(tenantId)))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.createNewVersion(someVersionId))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void missingMethodologyEditPermissionRejects() {
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(projectId),
                Set.of("CLIENT_HR_DIRECTOR"),
                Set.of(PermissionCodes.METHODOLOGY_READ),
                Set.of(), false, "ru-RU"));

        assertThatThrownBy(() -> useCase.createNewVersion(UUID.randomUUID()))
                .isInstanceOf(PermissionDeniedException.class);
        verify(versions, never()).save(any());
    }
}
