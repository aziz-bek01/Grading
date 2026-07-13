package uz.hrlab.grading.gradestructure.application;

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
import uz.hrlab.grading.gradestructure.domain.GradeBandGapPolicy;
import uz.hrlab.grading.gradestructure.domain.GradeStructureStatus;
import uz.hrlab.grading.gradestructure.domain.GradeStructureType;
import uz.hrlab.grading.gradestructure.domain.GradeTemplateStatus;
import uz.hrlab.grading.gradestructure.infrastructure.CustomGradeTemplateRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeTemplateJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeTemplateSnapshot;
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
 * BE-8/9 — {@code SaveAsGradeTemplateUseCase}: snapshot correctness, duplicate-
 * code 409, reserved-vs-builtin code 409, cross-tenant 404, permission gate, and
 * audit. Pure Mockito (no Docker). Mirrors {@code SaveAsTemplateUseCaseTest}.
 */
@Tag("workflow")
class SaveAsGradeTemplateUseCaseTest {

    private GradeTemplateCatalog templateCatalog;
    private CustomGradeTemplateRepository customTemplates;
    private GradeStructureRepository structures;
    private GradeRepository grades;
    private GradeBandRepository bands;
    private AbacGate abacGate;
    private AuditService audit;
    private GradeStructureAuditSnapshot snapshot;

    private SaveAsGradeTemplateUseCase useCase;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        templateCatalog = mock(GradeTemplateCatalog.class);
        customTemplates = mock(CustomGradeTemplateRepository.class);
        structures = mock(GradeStructureRepository.class);
        grades = mock(GradeRepository.class);
        bands = mock(GradeBandRepository.class);
        abacGate = mock(AbacGate.class);
        audit = mock(AuditService.class);
        snapshot = mock(GradeStructureAuditSnapshot.class);
        given(snapshot.of(any(GradeTemplateJpaEntity.class))).willReturn(mock(JsonNode.class));

        useCase = new SaveAsGradeTemplateUseCase(templateCatalog, customTemplates, structures,
                grades, bands, abacGate, audit, snapshot);

        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(), Set.of(),
                Set.of(PermissionCodes.GRADE_EDIT), Set.of(), false, "ru-RU"));
    }

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    private GradeStructureJpaEntity seedStructure(UUID projectId) {
        return new GradeStructureJpaEntity(UUID.randomUUID(), tenantId, projectId, "GS-1",
                GradeStructureType.CUSTOM, GradeStructureStatus.DRAFT, 1, null,
                GradeBandGapPolicy.STRICT_NO_GAPS);
    }

    private GradeJpaEntity seedGrade(UUID structureId, int number, int sort) {
        GradeJpaEntity g = new GradeJpaEntity(UUID.randomUUID(), tenantId, structureId, number, sort);
        g.setNameI18n(Map.of("ru-RU", "Грейд " + number, "en-US", "Grade " + number));
        return g;
    }

    private GradeBandJpaEntity seedBand(UUID gradeId, UUID structureId, String min, String max) {
        return new GradeBandJpaEntity(UUID.randomUUID(), tenantId, gradeId, structureId,
                new BigDecimal(min), new BigDecimal(max));
    }

    @Test
    void freezesGradesSnapshotAndPersistsActiveTemplateWithAudit() {
        UUID projectId = UUID.randomUUID();
        GradeStructureJpaEntity s = seedStructure(projectId);
        GradeJpaEntity g1 = seedGrade(s.getId(), 1, 1);
        GradeJpaEntity g2 = seedGrade(s.getId(), 2, 2);

        given(templateCatalog.isBuiltinCode("BANK_2026")).willReturn(false);
        given(customTemplates.existsByTenantIdAndCode(tenantId, "BANK_2026")).willReturn(false);
        given(structures.findByIdAndTenantId(s.getId(), tenantId)).willReturn(Optional.of(s));
        given(grades.findAllByTenantIdAndGradeStructureIdOrderBySortOrderAsc(tenantId, s.getId()))
                .willReturn(List.of(g1, g2));
        // Batched structure-scoped band load; g2 absent → null scores in its snapshot.
        given(bands.findAllByTenantIdAndGradeStructureIdOrderByMinScoreAsc(tenantId, s.getId()))
                .willReturn(List.of(seedBand(g1.getId(), s.getId(), "0", "99.9999")));
        given(customTemplates.save(any())).willAnswer(inv -> inv.getArgument(0));

        UUID id = useCase.saveAsTemplate(s.getId(), "BANK_2026",
                Map.of("ru-RU", "Грейды банка"), Map.of("ru-RU", "Описание"));

        assertThat(id).isNotNull();
        verify(abacGate).enforceCanListInProject(any(), eq(projectId));

        ArgumentCaptor<GradeTemplateJpaEntity> cap =
                ArgumentCaptor.forClass(GradeTemplateJpaEntity.class);
        verify(customTemplates).save(cap.capture());
        GradeTemplateJpaEntity saved = cap.getValue();

        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getCode()).isEqualTo("BANK_2026");
        assertThat(saved.getStatus()).isEqualTo(GradeTemplateStatus.ACTIVE);
        assertThat(saved.getStructureType()).isEqualTo(GradeStructureType.CUSTOM);

        GradeTemplateSnapshot snap = saved.getGradesSnapshot();
        assertThat(snap.grades()).hasSize(2);
        GradeTemplateSnapshot.GradeSnapshot first = snap.grades().get(0);
        assertThat(first.gradeNumber()).isEqualTo(1);
        assertThat(first.sortOrder()).isEqualTo(1);
        assertThat(first.nameI18n()).containsEntry("ru-RU", "Грейд 1");
        assertThat(first.minScore()).isEqualByComparingTo("0");
        assertThat(first.maxScore()).isEqualByComparingTo("99.9999");
        // grade 2 had no band → null scores frozen.
        assertThat(snap.grades().get(1).minScore()).isNull();
        assertThat(snap.grades().get(1).maxScore()).isNull();

        ArgumentCaptor<AuditEvent> ev = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, times(1)).record(ev.capture());
        assertThat(ev.getValue().action()).isEqualTo(AuditAction.GRADE_TEMPLATE_CREATED);
        assertThat(ev.getValue().tenantId()).isEqualTo(tenantId);
        assertThat(ev.getValue().actorUserId()).isEqualTo(userId);
        assertThat(ev.getValue().projectId()).isEqualTo(projectId);
        assertThat(ev.getValue().afterJson()).isNotNull();
    }

    @Test
    void duplicateTenantCodeReturns409() {
        given(templateCatalog.isBuiltinCode("DUP")).willReturn(false);
        given(customTemplates.existsByTenantIdAndCode(tenantId, "DUP")).willReturn(true);

        assertThatThrownBy(() -> useCase.saveAsTemplate(UUID.randomUUID(), "DUP",
                Map.of("ru-RU", "X"), null))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "GRADE_TEMPLATE_CODE_EXISTS");
        verify(customTemplates, never()).save(any());
    }

    @Test
    void builtinCodeCollisionReturns409() {
        given(templateCatalog.isBuiltinCode("GRADE_14")).willReturn(true);

        assertThatThrownBy(() -> useCase.saveAsTemplate(UUID.randomUUID(), "GRADE_14",
                Map.of("ru-RU", "X"), null))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "GRADE_TEMPLATE_CODE_EXISTS");
        verify(customTemplates, never()).save(any());
    }

    @Test
    void crossTenantSourceStructureReturns404() {
        UUID structureId = UUID.randomUUID();
        given(templateCatalog.isBuiltinCode("T")).willReturn(false);
        given(customTemplates.existsByTenantIdAndCode(tenantId, "T")).willReturn(false);
        given(structures.findByIdAndTenantId(structureId, tenantId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.saveAsTemplate(structureId, "T",
                Map.of("ru-RU", "X"), null))
                .isInstanceOf(TenantAccessDeniedException.class);
        verify(customTemplates, never()).save(any());
    }

    @Test
    void missingEditPermissionRejects() {
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(), Set.of(),
                Set.of(PermissionCodes.GRADE_READ), Set.of(), false, "ru-RU"));

        assertThatThrownBy(() -> useCase.saveAsTemplate(UUID.randomUUID(), "T",
                Map.of("ru-RU", "X"), null))
                .isInstanceOf(PermissionDeniedException.class);
        verify(customTemplates, never()).save(any());
    }
}
