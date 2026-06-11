package uz.hrlab.grading.gradestructure.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.common.exception.ResourceNotFoundException;
import uz.hrlab.grading.gradestructure.domain.GradeStructureType;
import uz.hrlab.grading.gradestructure.domain.GradeTemplateStatus;
import uz.hrlab.grading.gradestructure.infrastructure.CustomGradeTemplateRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeTemplateJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeTemplateSnapshot;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * BE-8 — {@code DefaultGradeTemplateCatalog}: UNION ordering (built-ins first,
 * then ACTIVE custom), source/is_builtin tagging, builtin-wins resolution,
 * cross-tenant / archived → 404, snapshot → instantiation-shape mapping. Pure
 * Mockito (no Docker). Mirrors the methodology DefaultTemplateCatalog behaviour.
 */
@Tag("workflow")
class DefaultGradeTemplateCatalogTest {

    private CustomGradeTemplateRepository customTemplates;
    private DefaultGradeTemplateCatalog catalog;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        customTemplates = mock(CustomGradeTemplateRepository.class);
        catalog = new DefaultGradeTemplateCatalog(new GradeStructureTemplateRegistry(), customTemplates);
        tenantId = UUID.randomUUID();
    }

    private GradeTemplateJpaEntity customRow(String code) {
        GradeTemplateSnapshot snap = new GradeTemplateSnapshot(List.of(
                new GradeTemplateSnapshot.GradeSnapshot(1, 1, Map.of("ru-RU", "Грейд 1"), null,
                        new BigDecimal("0"), new BigDecimal("100"))));
        GradeTemplateJpaEntity e = new GradeTemplateJpaEntity(UUID.randomUUID(), tenantId, code,
                GradeStructureType.CUSTOM, snap, GradeTemplateStatus.ACTIVE);
        e.setNameI18n(Map.of("ru-RU", "Кастом " + code));
        return e;
    }

    @Test
    void listUnionsBuiltinsFirstThenCustomTagged() {
        GradeTemplateJpaEntity custom = customRow("BANK_GRADES");
        given(customTemplates.findByTenantIdAndStatus(tenantId, GradeTemplateStatus.ACTIVE))
                .willReturn(List.of(custom));

        List<GradeTemplateCatalogEntry> list = catalog.list(tenantId);

        // 3 built-ins (GRADE_14, GRADE_16, CUSTOM) + 1 custom.
        assertThat(list).hasSize(4);
        assertThat(list.get(0).code()).isEqualTo("GRADE_14");
        assertThat(list.get(0).builtin()).isTrue();
        assertThat(list.get(0).source()).isEqualTo(GradeTemplateSource.BUILTIN);
        assertThat(list.get(0).id()).isNull();
        assertThat(list.get(0).gradeCount()).isEqualTo(14);
        // last entry is the tenant custom row, tagged + carries id.
        GradeTemplateCatalogEntry last = list.get(3);
        assertThat(last.code()).isEqualTo("BANK_GRADES");
        assertThat(last.builtin()).isFalse();
        assertThat(last.source()).isEqualTo(GradeTemplateSource.CUSTOM);
        assertThat(last.id()).isEqualTo(custom.getId());
        assertThat(last.gradeCount()).isEqualTo(1);
    }

    @Test
    void findInstantiableBuiltinWins() {
        GradeStructureTemplate t = catalog.findInstantiable(tenantId, "GRADE_14");
        assertThat(t.code()).isEqualTo("GRADE_14");
        assertThat(t.grades()).hasSize(14);
    }

    @Test
    void findInstantiableResolvesCustomByCodeFromSnapshot() {
        GradeTemplateJpaEntity custom = customRow("BANK_GRADES");
        given(customTemplates.findByTenantIdAndCode(tenantId, "BANK_GRADES"))
                .willReturn(Optional.of(custom));

        GradeStructureTemplate t = catalog.findInstantiable(tenantId, "BANK_GRADES");
        assertThat(t.code()).isEqualTo("BANK_GRADES");
        assertThat(t.structureType()).isEqualTo(GradeStructureType.CUSTOM);
        assertThat(t.grades()).hasSize(1);
        assertThat(t.grades().get(0).minScore()).isEqualByComparingTo("0");
        assertThat(t.grades().get(0).maxScore()).isEqualByComparingTo("100");
    }

    @Test
    void findInstantiableUnknownOrCrossTenantReturns404() {
        given(customTemplates.findByTenantIdAndCode(any(), any())).willReturn(Optional.empty());
        assertThatThrownBy(() -> catalog.findInstantiable(tenantId, "DOES_NOT_EXIST"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findInstantiableArchivedCustomReturns404() {
        GradeTemplateJpaEntity archived = customRow("OLD");
        archived.setStatus(GradeTemplateStatus.ARCHIVED);
        given(customTemplates.findByTenantIdAndCode(tenantId, "OLD")).willReturn(Optional.of(archived));

        assertThatThrownBy(() -> catalog.findInstantiable(tenantId, "OLD"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void isBuiltinCodeMatchesRegistry() {
        assertThat(catalog.isBuiltinCode("GRADE_14")).isTrue();
        assertThat(catalog.isBuiltinCode("GRADE_16")).isTrue();
        assertThat(catalog.isBuiltinCode("CUSTOM")).isTrue();
        assertThat(catalog.isBuiltinCode("BANK_GRADES")).isFalse();
    }
}
