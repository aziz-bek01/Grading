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
import uz.hrlab.grading.common.exception.ConflictException;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.methodology.domain.MethodologyStatus;
import uz.hrlab.grading.methodology.domain.MethodologyTemplateStatus;
import uz.hrlab.grading.methodology.domain.MethodologyType;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatus;
import uz.hrlab.grading.methodology.domain.ScoringMode;
import uz.hrlab.grading.methodology.infrastructure.CustomMethodologyTemplateRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyTemplateJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyTemplateSnapshot;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Epic E — {@code SaveAsTemplateUseCase}: snapshot correctness, duplicate-code
 * 409, reserved-vs-builtin code 409, cross-tenant 404, permission gate, and
 * audit. Pure Mockito (no Docker).
 */
@Tag("workflow")
class SaveAsTemplateUseCaseTest {

    private TemplateCatalog templateCatalog;
    private CustomMethodologyTemplateRepository customTemplates;
    private MethodologyRepository methodologies;
    private MethodologyVersionRepository versions;
    private FactorRepository factors;
    private FactorLevelRepository levels;
    private AbacGate abacGate;
    private AuditService audit;
    private MethodologyAuditSnapshot snapshot;

    private SaveAsTemplateUseCase useCase;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        templateCatalog = mock(TemplateCatalog.class);
        customTemplates = mock(CustomMethodologyTemplateRepository.class);
        methodologies = mock(MethodologyRepository.class);
        versions = mock(MethodologyVersionRepository.class);
        factors = mock(FactorRepository.class);
        levels = mock(FactorLevelRepository.class);
        abacGate = mock(AbacGate.class);
        audit = mock(AuditService.class);
        snapshot = mock(MethodologyAuditSnapshot.class);
        given(snapshot.of(any(MethodologyTemplateJpaEntity.class)))
                .willReturn(mock(JsonNode.class));

        useCase = new SaveAsTemplateUseCase(templateCatalog, customTemplates, methodologies,
                versions, factors, levels, abacGate, audit, snapshot);

        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(), Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of(PermissionCodes.METHODOLOGY_CREATE), Set.of(), false, "ru-RU"));
    }

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    private MethodologyJpaEntity seedMethodology(UUID projectId) {
        return new MethodologyJpaEntity(UUID.randomUUID(), tenantId, projectId, "M-1",
                MethodologyType.EXTENDED_11_CRITERIA, MethodologyStatus.ACTIVE);
    }

    private MethodologyVersionJpaEntity seedVersion(UUID methodologyId) {
        return new MethodologyVersionJpaEntity(UUID.randomUUID(), tenantId, methodologyId, 3,
                MethodologyVersionStatus.APPROVED, ScoringMode.WEIGHTED_POINTS,
                new BigDecimal("100.0000"), null);
    }

    private FactorJpaEntity seedFactor(UUID versionId, String code, String weight, int sortOrder) {
        FactorJpaEntity f = new FactorJpaEntity(UUID.randomUUID(), tenantId, versionId, code,
                new BigDecimal(weight), new BigDecimal("100.0000"), sortOrder, true);
        f.setNameI18n(Map.of("ru-RU", "Фактор " + code, "en-US", "Factor " + code));
        f.setDescriptionI18n(Map.of("ru-RU", "Описание " + code));
        return f;
    }

    private FactorLevelJpaEntity seedLevel(UUID factorId, String code, int order, String points,
                                           String scale) {
        FactorLevelJpaEntity l = new FactorLevelJpaEntity(UUID.randomUUID(), tenantId, factorId,
                code, order, new BigDecimal(points), scale == null ? null : new BigDecimal(scale));
        l.setLabelI18n(Map.of("ru-RU", "Уровень " + code));
        return l;
    }

    @Test
    void freezesFactorsSnapshotAndPersistsActiveTemplateWithAudit() {
        UUID projectId = UUID.randomUUID();
        MethodologyJpaEntity m = seedMethodology(projectId);
        MethodologyVersionJpaEntity v = seedVersion(m.getId());
        FactorJpaEntity f1 = seedFactor(v.getId(), "EDU", "60", 1);
        FactorJpaEntity f2 = seedFactor(v.getId(), "EXP", "40", 2);

        given(templateCatalog.isBuiltinCode("BANK_2026")).willReturn(false);
        given(customTemplates.existsByTenantIdAndCode(tenantId, "BANK_2026")).willReturn(false);
        given(versions.findByIdAndTenantId(v.getId(), tenantId)).willReturn(Optional.of(v));
        given(methodologies.findByIdAndTenantId(m.getId(), tenantId)).willReturn(Optional.of(m));
        given(factors.findAllByTenantIdAndMethodologyVersionIdOrderBySortOrderAsc(tenantId, v.getId()))
                .willReturn(List.of(f1, f2));
        given(levels.findAllByTenantIdAndFactorIdOrderByLevelOrderAsc(tenantId, f1.getId()))
                .willReturn(List.of(seedLevel(f1.getId(), "L1", 1, "10", "1"),
                        seedLevel(f1.getId(), "L2", 2, "20", "2")));
        given(levels.findAllByTenantIdAndFactorIdOrderByLevelOrderAsc(tenantId, f2.getId()))
                .willReturn(List.of(seedLevel(f2.getId(), "L1", 1, "5", null)));
        given(customTemplates.save(any())).willAnswer(inv -> inv.getArgument(0));

        UUID id = useCase.saveAsTemplate(new SaveAsTemplateCommand(
                v.getId(), "BANK_2026",
                Map.of("ru-RU", "Грейдинг банка"), Map.of("ru-RU", "Описание")));

        assertThat(id).isNotNull();
        // ABAC list-scope checked for the project-bound methodology.
        verify(abacGate).enforceCanListInProject(any(), eq(projectId));

        ArgumentCaptor<MethodologyTemplateJpaEntity> cap =
                ArgumentCaptor.forClass(MethodologyTemplateJpaEntity.class);
        verify(customTemplates).save(cap.capture());
        MethodologyTemplateJpaEntity saved = cap.getValue();

        // tenant from context, status ACTIVE, code/type/mode/target carried.
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getCode()).isEqualTo("BANK_2026");
        assertThat(saved.getStatus()).isEqualTo(MethodologyTemplateStatus.ACTIVE);
        assertThat(saved.getMethodologyType()).isEqualTo(MethodologyType.EXTENDED_11_CRITERIA);
        assertThat(saved.getScoringMode()).isEqualTo(ScoringMode.WEIGHTED_POINTS);
        assertThat(saved.getTargetTotalPoints()).isEqualByComparingTo(new BigDecimal("100.0000"));

        // snapshot deep copy: 2 factors, with their levels + i18n frozen.
        MethodologyTemplateSnapshot snap = saved.getFactorsSnapshot();
        assertThat(snap.factors()).hasSize(2);
        MethodologyTemplateSnapshot.FactorSnapshot edu = snap.factors().get(0);
        assertThat(edu.code()).isEqualTo("EDU");
        assertThat(edu.sortOrder()).isEqualTo(1);
        assertThat(edu.weight()).isEqualByComparingTo(new BigDecimal("60"));
        assertThat(edu.nameI18n()).containsEntry("ru-RU", "Фактор EDU");
        assertThat(edu.descriptionI18n()).containsEntry("ru-RU", "Описание EDU");
        assertThat(edu.levels()).hasSize(2);
        assertThat(edu.levels().get(0).code()).isEqualTo("L1");
        assertThat(edu.levels().get(0).points()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(edu.levels().get(0).scaleValue()).isEqualByComparingTo(new BigDecimal("1"));
        assertThat(snap.factors().get(1).levels()).hasSize(1);
        assertThat(snap.factors().get(1).levels().get(0).scaleValue()).isNull();

        // audit METHODOLOGY_TEMPLATE_CREATED with after snapshot.
        ArgumentCaptor<AuditEvent> ev = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, times(1)).record(ev.capture());
        assertThat(ev.getValue().action()).isEqualTo(AuditAction.METHODOLOGY_TEMPLATE_CREATED);
        assertThat(ev.getValue().tenantId()).isEqualTo(tenantId);
        assertThat(ev.getValue().actorUserId()).isEqualTo(userId);
        assertThat(ev.getValue().projectId()).isEqualTo(projectId);
        assertThat(ev.getValue().afterJson()).isNotNull();
    }

    @Test
    void duplicateTenantCodeReturns409() {
        given(templateCatalog.isBuiltinCode("DUP")).willReturn(false);
        given(customTemplates.existsByTenantIdAndCode(tenantId, "DUP")).willReturn(true);

        assertThatThrownBy(() -> useCase.saveAsTemplate(new SaveAsTemplateCommand(
                UUID.randomUUID(), "DUP", Map.of("ru-RU", "X"), null)))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "METHODOLOGY_TEMPLATE_CODE_EXISTS");
        verify(customTemplates, never()).save(any());
    }

    @Test
    void builtinCodeCollisionReturns409() {
        given(templateCatalog.isBuiltinCode("CLASSIC_8_FACTOR")).willReturn(true);

        assertThatThrownBy(() -> useCase.saveAsTemplate(new SaveAsTemplateCommand(
                UUID.randomUUID(), "CLASSIC_8_FACTOR", Map.of("ru-RU", "X"), null)))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "METHODOLOGY_TEMPLATE_CODE_EXISTS");
        verify(customTemplates, never()).save(any());
    }

    @Test
    void crossTenantSourceVersionReturns404() {
        UUID versionId = UUID.randomUUID();
        given(templateCatalog.isBuiltinCode("T")).willReturn(false);
        given(customTemplates.existsByTenantIdAndCode(tenantId, "T")).willReturn(false);
        given(versions.findByIdAndTenantId(versionId, tenantId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.saveAsTemplate(new SaveAsTemplateCommand(
                versionId, "T", Map.of("ru-RU", "X"), null)))
                .isInstanceOf(TenantAccessDeniedException.class);
        verify(customTemplates, never()).save(any());
    }

    @Test
    void missingCreatePermissionRejects() {
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(), Set.of(),
                Set.of(PermissionCodes.METHODOLOGY_READ), Set.of(), false, "ru-RU"));

        assertThatThrownBy(() -> useCase.saveAsTemplate(new SaveAsTemplateCommand(
                UUID.randomUUID(), "T", Map.of("ru-RU", "X"), null)))
                .isInstanceOf(PermissionDeniedException.class);
        verify(customTemplates, never()).save(any());
    }

    @Test
    void saveLatestVersionResolvesLatestThenDelegates() {
        MethodologyJpaEntity m = seedMethodology(null);
        MethodologyVersionJpaEntity v = seedVersion(m.getId());
        given(methodologies.findByIdAndTenantId(m.getId(), tenantId)).willReturn(Optional.of(m));
        given(versions.findFirstByTenantIdAndMethodologyIdOrderByVersionNumberDesc(tenantId, m.getId()))
                .willReturn(Optional.of(v));
        given(templateCatalog.isBuiltinCode("LATEST")).willReturn(false);
        given(customTemplates.existsByTenantIdAndCode(tenantId, "LATEST")).willReturn(false);
        given(versions.findByIdAndTenantId(v.getId(), tenantId)).willReturn(Optional.of(v));
        given(factors.findAllByTenantIdAndMethodologyVersionIdOrderBySortOrderAsc(tenantId, v.getId()))
                .willReturn(List.of());
        given(customTemplates.save(any())).willAnswer(inv -> inv.getArgument(0));

        UUID id = useCase.saveLatestVersionAsTemplate(
                m.getId(), "LATEST", Map.of("ru-RU", "X"), null);

        assertThat(id).isNotNull();
        verify(customTemplates).save(any());
    }

    @Test
    void saveLatestVersionWhenNoVersionsReturns404() {
        MethodologyJpaEntity m = seedMethodology(null);
        given(methodologies.findByIdAndTenantId(m.getId(), tenantId)).willReturn(Optional.of(m));
        given(versions.findFirstByTenantIdAndMethodologyIdOrderByVersionNumberDesc(tenantId, m.getId()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.saveLatestVersionAsTemplate(
                m.getId(), "X", Map.of("ru-RU", "X"), null))
                .isInstanceOf(TenantAccessDeniedException.class);
    }
}
