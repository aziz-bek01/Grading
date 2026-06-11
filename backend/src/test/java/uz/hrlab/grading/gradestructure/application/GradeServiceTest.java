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
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.gradestructure.domain.Grade;
import uz.hrlab.grading.gradestructure.domain.GradeBandGapPolicy;
import uz.hrlab.grading.gradestructure.domain.GradeStructureImmutabilityPolicy;
import uz.hrlab.grading.gradestructure.domain.GradeStructureStatus;
import uz.hrlab.grading.gradestructure.domain.GradeStructureTransitionRejectedException;
import uz.hrlab.grading.gradestructure.domain.GradeStructureType;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureRepository;
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
 * BE-5/6 — {@code GradeService.reorderGrades} + {@code removeBand}: DRAFT-only
 * guard, set-mismatch validation, sort-order assignment, idempotent band no-op,
 * tenant isolation, audit. Pure Mockito (no Docker).
 */
@Tag("workflow")
class GradeServiceTest {

    private GradeStructureRepository structures;
    private GradeRepository grades;
    private GradeBandRepository bands;
    private AbacGate abacGate;
    private AuditService audit;
    private GradeStructureAuditSnapshot snapshot;
    private GradeService service;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        structures = mock(GradeStructureRepository.class);
        grades = mock(GradeRepository.class);
        bands = mock(GradeBandRepository.class);
        abacGate = mock(AbacGate.class);
        audit = mock(AuditService.class);
        snapshot = mock(GradeStructureAuditSnapshot.class);
        given(snapshot.of(any(GradeBandJpaEntity.class))).willReturn(mock(JsonNode.class));

        service = new GradeService(structures, grades, bands,
                new GradeStructureImmutabilityPolicy(), abacGate, audit, snapshot);

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

    private GradeStructureJpaEntity structure(GradeStructureStatus status) {
        return new GradeStructureJpaEntity(UUID.randomUUID(), tenantId, null, "GS",
                GradeStructureType.CUSTOM, status, 1, null, GradeBandGapPolicy.STRICT_NO_GAPS);
    }

    private GradeJpaEntity grade(UUID structureId, int number, int sort) {
        GradeJpaEntity g = new GradeJpaEntity(UUID.randomUUID(), tenantId, structureId, number, sort);
        g.setNameI18n(Map.of("ru-RU", "Грейд " + number));
        return g;
    }

    // ---- reorderGrades (BE-5) ----

    @Test
    void reorderAssignsSortOrderByIndexAndAudits() {
        GradeStructureJpaEntity s = structure(GradeStructureStatus.DRAFT);
        GradeJpaEntity g1 = grade(s.getId(), 1, 1);
        GradeJpaEntity g2 = grade(s.getId(), 2, 2);
        GradeJpaEntity g3 = grade(s.getId(), 3, 3);
        given(structures.findByIdAndTenantId(s.getId(), tenantId)).willReturn(Optional.of(s));
        given(grades.findAllByTenantIdAndGradeStructureIdOrderBySortOrderAsc(tenantId, s.getId()))
                .willReturn(List.of(g1, g2, g3));
        given(grades.save(any())).willAnswer(inv -> inv.getArgument(0));

        // New order: g3, g1, g2
        List<Grade> result = service.reorderGrades(s.getId(), List.of(g3.getId(), g1.getId(), g2.getId()));

        assertThat(result).hasSize(3);
        assertThat(result.get(0).id()).isEqualTo(g3.getId());
        assertThat(result.get(0).sortOrder()).isEqualTo(1);
        assertThat(result.get(1).id()).isEqualTo(g1.getId());
        assertThat(result.get(1).sortOrder()).isEqualTo(2);
        assertThat(result.get(2).sortOrder()).isEqualTo(3);

        ArgumentCaptor<AuditEvent> ev = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, times(1)).record(ev.capture());
        assertThat(ev.getValue().action()).isEqualTo(AuditAction.GRADE_REORDERED);
        assertThat(ev.getValue().tenantId()).isEqualTo(tenantId);
        assertThat(ev.getValue().entityId()).isEqualTo(s.getId());
    }

    @Test
    void reorderRejectsSetMismatch() {
        GradeStructureJpaEntity s = structure(GradeStructureStatus.DRAFT);
        GradeJpaEntity g1 = grade(s.getId(), 1, 1);
        GradeJpaEntity g2 = grade(s.getId(), 2, 2);
        given(structures.findByIdAndTenantId(s.getId(), tenantId)).willReturn(Optional.of(s));
        given(grades.findAllByTenantIdAndGradeStructureIdOrderBySortOrderAsc(tenantId, s.getId()))
                .willReturn(List.of(g1, g2));

        // wrong set (only one id)
        assertThatThrownBy(() -> service.reorderGrades(s.getId(), List.of(g1.getId())))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "GRADE_REORDER_SET_MISMATCH");
        // foreign id of same size
        assertThatThrownBy(() -> service.reorderGrades(s.getId(),
                List.of(g1.getId(), UUID.randomUUID())))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "GRADE_REORDER_SET_MISMATCH");
        verify(audit, never()).record(any());
    }

    @Test
    void reorderRejectedWhenStructureNotDraft() {
        GradeStructureJpaEntity s = structure(GradeStructureStatus.APPROVED);
        given(structures.findByIdAndTenantId(s.getId(), tenantId)).willReturn(Optional.of(s));

        assertThatThrownBy(() -> service.reorderGrades(s.getId(), List.of(UUID.randomUUID())))
                .isInstanceOf(GradeStructureTransitionRejectedException.class);
        verify(grades, never()).save(any());
    }

    @Test
    void reorderCrossTenantStructureDenied() {
        UUID id = UUID.randomUUID();
        given(structures.findByIdAndTenantId(id, tenantId)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.reorderGrades(id, List.of(UUID.randomUUID())))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void reorderRequiresGradeEdit() {
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(), Set.of(),
                Set.of(PermissionCodes.GRADE_READ), Set.of(), false, "ru-RU"));
        assertThatThrownBy(() -> service.reorderGrades(UUID.randomUUID(), List.of()))
                .isInstanceOf(PermissionDeniedException.class);
    }

    // ---- removeBand (BE-6) ----

    @Test
    void removeBandDeletesAndAudits() {
        GradeStructureJpaEntity s = structure(GradeStructureStatus.DRAFT);
        GradeJpaEntity g = grade(s.getId(), 1, 1);
        GradeBandJpaEntity band = new GradeBandJpaEntity(UUID.randomUUID(), tenantId, g.getId(),
                s.getId(), new BigDecimal("0"), new BigDecimal("100"));
        given(grades.findByIdAndTenantId(g.getId(), tenantId)).willReturn(Optional.of(g));
        given(structures.findByIdAndTenantId(s.getId(), tenantId)).willReturn(Optional.of(s));
        given(bands.findByTenantIdAndGradeId(tenantId, g.getId())).willReturn(Optional.of(band));

        service.removeBand(g.getId());

        verify(bands).delete(band);
        ArgumentCaptor<AuditEvent> ev = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, times(1)).record(ev.capture());
        assertThat(ev.getValue().action()).isEqualTo(AuditAction.GRADE_BAND_REMOVED);
        assertThat(ev.getValue().entityId()).isEqualTo(band.getId());
    }

    @Test
    void removeBandIsIdempotentNoOpWhenAbsent() {
        GradeStructureJpaEntity s = structure(GradeStructureStatus.DRAFT);
        GradeJpaEntity g = grade(s.getId(), 1, 1);
        given(grades.findByIdAndTenantId(g.getId(), tenantId)).willReturn(Optional.of(g));
        given(structures.findByIdAndTenantId(s.getId(), tenantId)).willReturn(Optional.of(s));
        given(bands.findByTenantIdAndGradeId(tenantId, g.getId())).willReturn(Optional.empty());

        service.removeBand(g.getId());

        verify(bands, never()).delete(any());
        verify(audit, never()).record(any());
    }

    @Test
    void removeBandRejectedWhenStructureNotDraft() {
        GradeStructureJpaEntity s = structure(GradeStructureStatus.LOCKED);
        GradeJpaEntity g = grade(s.getId(), 1, 1);
        given(grades.findByIdAndTenantId(g.getId(), tenantId)).willReturn(Optional.of(g));
        given(structures.findByIdAndTenantId(s.getId(), tenantId)).willReturn(Optional.of(s));

        assertThatThrownBy(() -> service.removeBand(g.getId()))
                .isInstanceOf(GradeStructureTransitionRejectedException.class);
        verify(bands, never()).delete(any());
    }

    @Test
    void removeBandCrossTenantGradeDenied() {
        UUID gradeId = UUID.randomUUID();
        given(grades.findByIdAndTenantId(gradeId, tenantId)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.removeBand(gradeId))
                .isInstanceOf(TenantAccessDeniedException.class);
    }
}
