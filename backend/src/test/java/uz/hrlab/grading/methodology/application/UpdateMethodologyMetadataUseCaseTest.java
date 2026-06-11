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
import uz.hrlab.grading.methodology.domain.Methodology;
import uz.hrlab.grading.methodology.domain.MethodologyStatus;
import uz.hrlab.grading.methodology.domain.MethodologyType;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatus;
import uz.hrlab.grading.methodology.domain.ScoringMode;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * BE-4 / BE-7 — methodology_type editable in DRAFT (container).
 *
 * <ul>
 *   <li>type edit succeeds when all versions are DRAFT/ARCHIVED + audit row.</li>
 *   <li>type edit blocked (METHODOLOGY_TYPE_LOCKED) when an APPROVED version
 *       exists; LOCKED also blocks.</li>
 *   <li>name/description-only edit still works (no version query needed).</li>
 *   <li>cross-tenant id → 404; missing METHODOLOGY_EDIT → 403.</li>
 * </ul>
 *
 * <p>Pure Mockito unit test — no Spring context, no Testcontainers.
 */
@Tag("workflow")
class UpdateMethodologyMetadataUseCaseTest {

    private MethodologyRepository methodologies;
    private MethodologyVersionRepository versions;
    private AbacGate abacGate;
    private AuditService audit;
    private MethodologyAuditSnapshot snapshot;

    private UpdateMethodologyMetadataUseCase useCase;

    private UUID tenantId;
    private UUID userId;
    private UUID projectId;
    private UUID methodologyId;

    @BeforeEach
    void setUp() {
        methodologies = mock(MethodologyRepository.class);
        versions = mock(MethodologyVersionRepository.class);
        abacGate = mock(AbacGate.class);
        audit = mock(AuditService.class);
        snapshot = mock(MethodologyAuditSnapshot.class);
        given(snapshot.of(any(MethodologyJpaEntity.class)))
                .willReturn(mock(JsonNode.class));
        given(methodologies.save(any(MethodologyJpaEntity.class)))
                .willAnswer(inv -> inv.getArgument(0));

        useCase = new UpdateMethodologyMetadataUseCase(methodologies, versions,
                abacGate, audit, snapshot);

        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();
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

    private MethodologyJpaEntity wireMethodology(MethodologyType type) {
        MethodologyJpaEntity m = new MethodologyJpaEntity(
                methodologyId, tenantId, projectId, "M-1",
                type, MethodologyStatus.ACTIVE);
        m.setNameI18n(Map.of("ru-RU", "Методология"));
        given(methodologies.findByIdAndTenantId(methodologyId, tenantId))
                .willReturn(Optional.of(m));
        return m;
    }

    private MethodologyVersionJpaEntity version(MethodologyVersionStatus status, int number) {
        return new MethodologyVersionJpaEntity(
                UUID.randomUUID(), tenantId, methodologyId, number,
                status, ScoringMode.DIRECT_POINTS, new BigDecimal("1000"), null);
    }

    // --- Tests -----------------------------------------------------------

    @Test
    void typeEditSucceedsWhenAllVersionsDraftAndAudits() {
        MethodologyJpaEntity m = wireMethodology(MethodologyType.CLASSIC_8_FACTOR);
        given(versions.findAllByTenantIdAndMethodologyIdOrderByVersionNumberDesc(
                tenantId, methodologyId))
                .willReturn(List.of(version(MethodologyVersionStatus.DRAFT, 1)));

        Methodology result = useCase.update(methodologyId, null, null,
                MethodologyType.EXTENDED_11_CRITERIA);

        assertThat(result.methodologyType()).isEqualTo(MethodologyType.EXTENDED_11_CRITERIA);
        assertThat(m.getMethodologyType()).isEqualTo(MethodologyType.EXTENDED_11_CRITERIA);

        ArgumentCaptor<AuditEvent> auditCap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, times(1)).record(auditCap.capture());
        assertThat(auditCap.getValue().action()).isEqualTo(AuditAction.METHODOLOGY_UPDATED);
    }

    @Test
    void typeEditBlockedWhenApprovedVersionExists() {
        wireMethodology(MethodologyType.CLASSIC_8_FACTOR);
        given(versions.findAllByTenantIdAndMethodologyIdOrderByVersionNumberDesc(
                tenantId, methodologyId))
                .willReturn(List.of(
                        version(MethodologyVersionStatus.DRAFT, 2),
                        version(MethodologyVersionStatus.APPROVED, 1)));

        assertThatThrownBy(() -> useCase.update(methodologyId, null, null,
                MethodologyType.CUSTOM))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("methodology_type");
        verify(methodologies, never()).save(any());
        verify(audit, never()).record(any());
    }

    @Test
    void typeEditBlockedWhenLockedVersionExists() {
        wireMethodology(MethodologyType.CLASSIC_8_FACTOR);
        given(versions.findAllByTenantIdAndMethodologyIdOrderByVersionNumberDesc(
                tenantId, methodologyId))
                .willReturn(List.of(version(MethodologyVersionStatus.LOCKED, 1)));

        assertThatThrownBy(() -> useCase.update(methodologyId, null, null,
                MethodologyType.CUSTOM))
                .isInstanceOf(ValidationException.class);
        verify(methodologies, never()).save(any());
    }

    @Test
    void sameTypeNoOpDoesNotConsultVersionsAndSucceeds() {
        // Passing the SAME type must not trigger the lock guard (no version query).
        MethodologyJpaEntity m = wireMethodology(MethodologyType.CUSTOM);

        Methodology result = useCase.update(methodologyId,
                Map.of("ru-RU", "Новое имя"), null, MethodologyType.CUSTOM);

        assertThat(result.methodologyType()).isEqualTo(MethodologyType.CUSTOM);
        verify(versions, never())
                .findAllByTenantIdAndMethodologyIdOrderByVersionNumberDesc(any(), any());
        verify(methodologies, times(1)).save(m);
    }

    @Test
    void nameOnlyEditDoesNotConsultVersions() {
        MethodologyJpaEntity m = wireMethodology(MethodologyType.CUSTOM);

        useCase.update(methodologyId, Map.of("en-US", "New name"), null);

        verify(versions, never())
                .findAllByTenantIdAndMethodologyIdOrderByVersionNumberDesc(any(), any());
        verify(methodologies, times(1)).save(m);
    }

    @Test
    void crossTenantMethodologyReturns404() {
        given(methodologies.findByIdAndTenantId(methodologyId, tenantId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.update(methodologyId, null, null,
                MethodologyType.CUSTOM))
                .isInstanceOf(TenantAccessDeniedException.class);
        verify(methodologies, never()).save(any());
    }

    @Test
    void missingEditPermissionRejected() {
        setContext(Set.of(PermissionCodes.METHODOLOGY_READ));

        assertThatThrownBy(() -> useCase.update(methodologyId, null, null,
                MethodologyType.CUSTOM))
                .isInstanceOf(PermissionDeniedException.class);
        verify(methodologies, never()).save(any());
    }
}
