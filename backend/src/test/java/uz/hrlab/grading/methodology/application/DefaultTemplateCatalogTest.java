package uz.hrlab.grading.methodology.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.common.exception.ResourceNotFoundException;
import uz.hrlab.grading.methodology.domain.MethodologyTemplateStatus;
import uz.hrlab.grading.methodology.domain.MethodologyType;
import uz.hrlab.grading.methodology.domain.ScoringMode;
import uz.hrlab.grading.methodology.infrastructure.CustomMethodologyTemplateRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyTemplateJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyTemplateSnapshot;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Epic E — {@code DefaultTemplateCatalog}: the catalog UNIONs built-ins with the
 * tenant's own ACTIVE custom templates only, resolves a built-in OR a custom
 * code to the common instantiation shape, and round-trips a custom snapshot's
 * factors/levels loss-free. Uses the REAL {@link MethodologyTemplateRegistry}
 * (in-memory) + a mocked custom-template repo (no Docker).
 */
@Tag("workflow")
class DefaultTemplateCatalogTest {

    private CustomMethodologyTemplateRepository customTemplates;
    private DefaultTemplateCatalog catalog;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        customTemplates = mock(CustomMethodologyTemplateRepository.class);
        catalog = new DefaultTemplateCatalog(new MethodologyTemplateRegistry(), customTemplates);
    }

    private MethodologyTemplateJpaEntity customTemplate(String code) {
        MethodologyTemplateSnapshot snap = new MethodologyTemplateSnapshot(List.of(
                new MethodologyTemplateSnapshot.FactorSnapshot(
                        "EDU", 1, new BigDecimal("60.0000"), new BigDecimal("60.0000"), true,
                        Map.of("ru-RU", "Образование"), Map.of("ru-RU", "Описание"),
                        List.of(new MethodologyTemplateSnapshot.LevelSnapshot(
                                "L1", 1, new BigDecimal("10.0000"), new BigDecimal("1.0000"),
                                Map.of("ru-RU", "Уровень 1"), Map.of("ru-RU", "Описание уровня"))))));
        MethodologyTemplateJpaEntity e = new MethodologyTemplateJpaEntity(
                UUID.randomUUID(), tenantId, code,
                MethodologyType.CUSTOM, ScoringMode.WEIGHTED_SCALE,
                new BigDecimal("100.0000"), snap, MethodologyTemplateStatus.ACTIVE);
        e.setNameI18n(Map.of("ru-RU", "Кастомный шаблон"));
        e.setDescriptionI18n(Map.of("ru-RU", "Описание шаблона"));
        return e;
    }

    @Test
    void listUnionsBuiltinsThenOwnTenantActiveCustom() {
        given(customTemplates.findByTenantIdAndStatus(tenantId, MethodologyTemplateStatus.ACTIVE))
                .willReturn(List.of(customTemplate("BANK_2026")));

        List<TemplateCatalogEntry> entries = catalog.list(tenantId);

        // 3 built-ins (read-only, no id) + 1 custom (id present).
        assertThat(entries).hasSize(4);
        assertThat(entries.get(0).code()).isEqualTo("CLASSIC_8_FACTOR");
        assertThat(entries.get(0).builtin()).isTrue();
        assertThat(entries.get(0).source()).isEqualTo(TemplateSource.BUILTIN);
        assertThat(entries.get(0).id()).isNull();
        // Custom appended after built-ins, carrying its row id + CUSTOM source.
        TemplateCatalogEntry custom = entries.get(3);
        assertThat(custom.code()).isEqualTo("BANK_2026");
        assertThat(custom.builtin()).isFalse();
        assertThat(custom.source()).isEqualTo(TemplateSource.CUSTOM);
        assertThat(custom.id()).isNotNull();
        assertThat(custom.factorCount()).isEqualTo(1);
        assertThat(custom.defaultScoringMode()).isEqualTo(ScoringMode.WEIGHTED_SCALE);
    }

    @Test
    void findInstantiableResolvesBuiltinByCode() {
        MethodologyTemplate t = catalog.findInstantiable(tenantId, "CLASSIC_8_FACTOR");
        assertThat(t.methodologyType()).isEqualTo(MethodologyType.CLASSIC_8_FACTOR);
        assertThat(t.factors()).hasSize(8);
    }

    @Test
    void findInstantiableRoundTripsCustomSnapshot() {
        MethodologyTemplateJpaEntity e = customTemplate("BANK_2026");
        given(customTemplates.findByTenantIdAndCode(tenantId, "BANK_2026"))
                .willReturn(Optional.of(e));

        MethodologyTemplate t = catalog.findInstantiable(tenantId, "BANK_2026");

        assertThat(t.code()).isEqualTo("BANK_2026");
        assertThat(t.methodologyType()).isEqualTo(MethodologyType.CUSTOM);
        assertThat(t.scoringMode()).isEqualTo(ScoringMode.WEIGHTED_SCALE);
        assertThat(t.targetTotalPoints()).isEqualByComparingTo(new BigDecimal("100.0000"));
        assertThat(t.nameI18n()).containsEntry("ru-RU", "Кастомный шаблон");
        assertThat(t.factors()).hasSize(1);
        MethodologyTemplate.FactorTemplate f = t.factors().get(0);
        assertThat(f.code()).isEqualTo("EDU");
        assertThat(f.weight()).isEqualByComparingTo(new BigDecimal("60.0000"));
        assertThat(f.required()).isTrue();
        assertThat(f.nameI18n()).containsEntry("ru-RU", "Образование");
        assertThat(f.descriptionI18n()).containsEntry("ru-RU", "Описание");
        assertThat(f.levels()).hasSize(1);
        MethodologyTemplate.LevelTemplate l = f.levels().get(0);
        assertThat(l.code()).isEqualTo("L1");
        assertThat(l.points()).isEqualByComparingTo(new BigDecimal("10.0000"));
        assertThat(l.scaleValue()).isEqualByComparingTo(new BigDecimal("1.0000"));
        assertThat(l.labelI18n()).containsEntry("ru-RU", "Уровень 1");
        assertThat(l.descriptionI18n()).containsEntry("ru-RU", "Описание уровня");
    }

    @Test
    void findInstantiableCrossTenantOrArchivedCustomReturns404() {
        // Repo returns empty for a code not belonging to this tenant.
        given(customTemplates.findByTenantIdAndCode(tenantId, "OTHER"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> catalog.findInstantiable(tenantId, "OTHER"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findInstantiableArchivedCustomReturns404() {
        MethodologyTemplateJpaEntity archived = customTemplate("ARCH");
        archived.setStatus(MethodologyTemplateStatus.ARCHIVED);
        given(customTemplates.findByTenantIdAndCode(tenantId, "ARCH"))
                .willReturn(Optional.of(archived));

        assertThatThrownBy(() -> catalog.findInstantiable(tenantId, "ARCH"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void isBuiltinCodeTrueForRegistryCodes() {
        assertThat(catalog.isBuiltinCode("CLASSIC_8_FACTOR")).isTrue();
        assertThat(catalog.isBuiltinCode("CUSTOM")).isTrue();
        assertThat(catalog.isBuiltinCode("BANK_2026")).isFalse();
    }
}
