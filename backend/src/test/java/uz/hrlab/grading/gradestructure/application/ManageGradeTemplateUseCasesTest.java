package uz.hrlab.grading.gradestructure.application;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.gradestructure.domain.GradeStructureType;
import uz.hrlab.grading.gradestructure.domain.GradeTemplateStatus;
import uz.hrlab.grading.gradestructure.infrastructure.CustomGradeTemplateRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeTemplateJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeTemplateSnapshot;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

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
 * BE-8 — manage custom grade templates: rename (UPDATE) + archive. Custom-only
 * (built-in / cross-tenant id → 404 via findByIdAndTenantId), permission-gated by
 * GRADE_EDIT, audited. Pure Mockito (no Docker). Mirrors
 * {@code ManageCustomTemplateUseCasesTest}.
 */
@Tag("workflow")
class ManageGradeTemplateUseCasesTest {

    private CustomGradeTemplateRepository customTemplates;
    private AuditService audit;
    private GradeStructureAuditSnapshot snapshot;

    private UpdateGradeTemplateUseCase update;
    private ArchiveGradeTemplateUseCase archive;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        customTemplates = mock(CustomGradeTemplateRepository.class);
        audit = mock(AuditService.class);
        snapshot = mock(GradeStructureAuditSnapshot.class);
        given(snapshot.of(any(GradeTemplateJpaEntity.class))).willReturn(mock(JsonNode.class));
        given(customTemplates.save(any())).willAnswer(inv -> inv.getArgument(0));

        update = new UpdateGradeTemplateUseCase(customTemplates, audit, snapshot);
        archive = new ArchiveGradeTemplateUseCase(customTemplates, audit, snapshot);

        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        setContext(PermissionCodes.GRADE_EDIT);
    }

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    private void setContext(String... perms) {
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(), Set.of(),
                Set.of(perms), Set.of(), false, "ru-RU"));
    }

    private GradeTemplateJpaEntity seed() {
        GradeTemplateSnapshot snap = new GradeTemplateSnapshot(List.of());
        GradeTemplateJpaEntity e = new GradeTemplateJpaEntity(
                UUID.randomUUID(), tenantId, "BANK_GRADES",
                GradeStructureType.CUSTOM, snap, GradeTemplateStatus.ACTIVE);
        e.setNameI18n(Map.of("ru-RU", "Старое имя"));
        return e;
    }

    @Test
    void renameUpdatesNameAndDescriptionWithAudit() {
        GradeTemplateJpaEntity e = seed();
        given(customTemplates.findByIdAndTenantId(e.getId(), tenantId)).willReturn(Optional.of(e));

        update.rename(e.getId(), Map.of("ru-RU", "Новое имя"), Map.of("ru-RU", "Новое описание"));

        assertThat(e.getNameI18n()).containsEntry("ru-RU", "Новое имя");
        assertThat(e.getDescriptionI18n()).containsEntry("ru-RU", "Новое описание");
        verify(customTemplates).save(e);
        ArgumentCaptor<AuditEvent> ev = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, times(1)).record(ev.capture());
        assertThat(ev.getValue().action()).isEqualTo(AuditAction.GRADE_TEMPLATE_UPDATED);
        assertThat(ev.getValue().tenantId()).isEqualTo(tenantId);
    }

    @Test
    void renameCrossTenantOrBuiltinReturns404() {
        UUID id = UUID.randomUUID();
        given(customTemplates.findByIdAndTenantId(id, tenantId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> update.rename(id, Map.of("ru-RU", "X"), null))
                .isInstanceOf(TenantAccessDeniedException.class);
        verify(customTemplates, never()).save(any());
    }

    @Test
    void renameRequiresGradeEdit() {
        setContext(PermissionCodes.GRADE_READ);
        assertThatThrownBy(() -> update.rename(UUID.randomUUID(), Map.of("ru-RU", "X"), null))
                .isInstanceOf(PermissionDeniedException.class);
        verify(customTemplates, never()).save(any());
    }

    @Test
    void archiveSetsStatusArchivedWithAudit() {
        GradeTemplateJpaEntity e = seed();
        given(customTemplates.findByIdAndTenantId(e.getId(), tenantId)).willReturn(Optional.of(e));

        archive.archive(e.getId());

        assertThat(e.getStatus()).isEqualTo(GradeTemplateStatus.ARCHIVED);
        verify(customTemplates).save(e);
        ArgumentCaptor<AuditEvent> ev = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, times(1)).record(ev.capture());
        assertThat(ev.getValue().action()).isEqualTo(AuditAction.GRADE_TEMPLATE_ARCHIVED);
    }

    @Test
    void archiveCrossTenantOrBuiltinReturns404() {
        UUID id = UUID.randomUUID();
        given(customTemplates.findByIdAndTenantId(id, tenantId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> archive.archive(id))
                .isInstanceOf(TenantAccessDeniedException.class);
        verify(customTemplates, never()).save(any());
    }

    @Test
    void archiveRequiresGradeEdit() {
        setContext(PermissionCodes.GRADE_READ);
        assertThatThrownBy(() -> archive.archive(UUID.randomUUID()))
                .isInstanceOf(PermissionDeniedException.class);
        verify(customTemplates, never()).save(any());
    }
}
