package uz.hrlab.grading.methodology.application;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.ResourceNotFoundException;
import uz.hrlab.grading.methodology.domain.MethodologyType;
import uz.hrlab.grading.methodology.domain.ScoringMode;
import uz.hrlab.grading.methodology.infrastructure.FactorJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Epic E — create-from-template instantiates a CUSTOM template through the same
 * {@code CreateMethodologyFromTemplateUseCase} path as a built-in, by resolving
 * via the {@link TemplateCatalog} port. Verifies the snapshot's factors + levels
 * (incl. description i18n + scaleValue) round-trip into new factor/level rows.
 * Pure Mockito (no Docker).
 */
@Tag("workflow")
class CreateMethodologyFromCustomTemplateUseCaseTest {

    private TemplateCatalog templateCatalog;
    private MethodologyRepository methodologies;
    private MethodologyVersionRepository versions;
    private FactorRepository factors;
    private FactorLevelRepository levels;
    private ProjectRepository projects;
    private AbacGate abacGate;
    private AuditService audit;
    private MethodologyAuditSnapshot snapshot;

    private CreateMethodologyFromTemplateUseCase useCase;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        templateCatalog = mock(TemplateCatalog.class);
        methodologies = mock(MethodologyRepository.class);
        versions = mock(MethodologyVersionRepository.class);
        factors = mock(FactorRepository.class);
        levels = mock(FactorLevelRepository.class);
        projects = mock(ProjectRepository.class);
        abacGate = mock(AbacGate.class);
        audit = mock(AuditService.class);
        snapshot = mock(MethodologyAuditSnapshot.class);
        given(snapshot.of(any(MethodologyJpaEntity.class))).willReturn(mock(JsonNode.class));
        given(snapshot.of(any(MethodologyVersionJpaEntity.class))).willReturn(mock(JsonNode.class));
        given(methodologies.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(versions.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(factors.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(levels.save(any())).willAnswer(inv -> inv.getArgument(0));

        useCase = new CreateMethodologyFromTemplateUseCase(templateCatalog, methodologies, versions,
                factors, levels, projects, abacGate, audit, snapshot);

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

    private MethodologyTemplate customShape() {
        MethodologyTemplate.LevelTemplate l1 = new MethodologyTemplate.LevelTemplate(
                "L1", 1, new BigDecimal("10.0000"), new BigDecimal("1.0000"),
                Map.of("ru-RU", "Уровень 1"), Map.of("ru-RU", "Описание уровня"));
        MethodologyTemplate.LevelTemplate l2 = new MethodologyTemplate.LevelTemplate(
                "L2", 2, new BigDecimal("20.0000"), new BigDecimal("2.0000"),
                Map.of("ru-RU", "Уровень 2"), null);
        MethodologyTemplate.FactorTemplate edu = new MethodologyTemplate.FactorTemplate(
                "EDU", Map.of("ru-RU", "Образование"), Map.of("ru-RU", "Описание EDU"),
                new BigDecimal("60.0000"), new BigDecimal("60.0000"), 1, true, List.of(l1, l2));
        return new MethodologyTemplate("BANK_2026", MethodologyType.CUSTOM,
                ScoringMode.WEIGHTED_SCALE, new BigDecimal("100.0000"),
                Map.of("ru-RU", "Шаблон"), Map.of("ru-RU", "Опис"), List.of(edu));
    }

    @Test
    void instantiatesCustomTemplateCopyingFactorsAndLevelsWithDescriptions() {
        given(templateCatalog.findInstantiable(tenantId, "BANK_2026")).willReturn(customShape());
        given(methodologies.existsByTenantIdAndProjectIdAndCode(tenantId, null, "M-NEW"))
                .willReturn(false);

        MethodologyAggregate agg = useCase.create(new CreateMethodologyFromTemplateCommand(
                "BANK_2026", null, "M-NEW",
                Map.of("ru-RU", "Новая методология"), Map.of()));

        // Methodology type + version scoring mode come from the custom template.
        assertThat(agg.methodology().methodologyType()).isEqualTo(MethodologyType.CUSTOM);
        assertThat(agg.currentVersion().scoringMode()).isEqualTo(ScoringMode.WEIGHTED_SCALE);
        assertThat(agg.currentVersion().targetTotalPoints())
                .isEqualByComparingTo(new BigDecimal("100.0000"));

        // 1 factor copied, with name + description i18n.
        ArgumentCaptor<FactorJpaEntity> fCap = ArgumentCaptor.forClass(FactorJpaEntity.class);
        verify(factors, times(1)).save(fCap.capture());
        FactorJpaEntity f = fCap.getValue();
        assertThat(f.getTenantId()).isEqualTo(tenantId);
        assertThat(f.getCode()).isEqualTo("EDU");
        assertThat(f.getWeight()).isEqualByComparingTo(new BigDecimal("60.0000"));
        assertThat(f.getNameI18n()).containsEntry("ru-RU", "Образование");
        assertThat(f.getDescriptionI18n()).containsEntry("ru-RU", "Описание EDU");

        // 2 levels copied, with scaleValue + label + description i18n.
        ArgumentCaptor<FactorLevelJpaEntity> lCap = ArgumentCaptor.forClass(FactorLevelJpaEntity.class);
        verify(levels, times(2)).save(lCap.capture());
        List<FactorLevelJpaEntity> saved = lCap.getAllValues();
        assertThat(saved).extracting(FactorLevelJpaEntity::getCode)
                .containsExactly("L1", "L2");
        FactorLevelJpaEntity l1 = saved.get(0);
        assertThat(l1.getScaleValue()).isEqualByComparingTo(new BigDecimal("1.0000"));
        assertThat(l1.getLabelI18n()).containsEntry("ru-RU", "Уровень 1");
        assertThat(l1.getDescriptionI18n()).containsEntry("ru-RU", "Описание уровня");
        // L2 had a null description — round-trips as empty map.
        assertThat(saved.get(1).getDescriptionI18n()).isEmpty();
    }

    @Test
    void unknownCustomCodePropagates404() {
        given(templateCatalog.findInstantiable(eq(tenantId), eq("MISSING")))
                .willThrow(new ResourceNotFoundException("Methodology template not found: MISSING"));

        assertThatThrownBy(() -> useCase.create(new CreateMethodologyFromTemplateCommand(
                "MISSING", null, "M-NEW", Map.of("ru-RU", "X"), Map.of())))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
