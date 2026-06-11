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
 * BE-4 — {@code DeleteGradeStructureUseCase}: DRAFT-only guard, cascade delete of
 * grades + bands, audit GRADE_STRUCTURE_DELETED, cross-tenant 404, permission
 * gate. Pure Mockito (no Docker).
 */
@Tag("workflow")
class DeleteGradeStructureUseCaseTest {

    private GradeStructureRepository structures;
    private GradeRepository grades;
    private GradeBandRepository bands;
    private AbacGate abacGate;
    private AuditService audit;
    private GradeStructureAuditSnapshot snapshot;
    private DeleteGradeStructureUseCase useCase;

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
        given(snapshot.of(any(GradeStructureJpaEntity.class))).willReturn(mock(JsonNode.class));

        useCase = new DeleteGradeStructureUseCase(structures, grades, bands,
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

    @Test
    void deletesDraftCascadingGradesAndBandsWithAudit() {
        GradeStructureJpaEntity s = structure(GradeStructureStatus.DRAFT);
        GradeJpaEntity g1 = new GradeJpaEntity(UUID.randomUUID(), tenantId, s.getId(), 1, 1);
        g1.setNameI18n(Map.of("ru-RU", "Грейд 1"));
        GradeBandJpaEntity b1 = new GradeBandJpaEntity(UUID.randomUUID(), tenantId, g1.getId(),
                s.getId(), new BigDecimal("0"), new BigDecimal("100"));
        given(structures.findByIdAndTenantId(s.getId(), tenantId)).willReturn(Optional.of(s));
        given(grades.findAllByTenantIdAndGradeStructureIdOrderBySortOrderAsc(tenantId, s.getId()))
                .willReturn(List.of(g1));
        given(bands.findByTenantIdAndGradeId(tenantId, g1.getId())).willReturn(Optional.of(b1));

        useCase.delete(s.getId());

        verify(bands).delete(b1);
        verify(grades).deleteAll(List.of(g1));
        verify(structures).delete(s);

        ArgumentCaptor<AuditEvent> ev = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, times(1)).record(ev.capture());
        assertThat(ev.getValue().action()).isEqualTo(AuditAction.GRADE_STRUCTURE_DELETED);
        assertThat(ev.getValue().entityId()).isEqualTo(s.getId());
        assertThat(ev.getValue().beforeJson()).isNotNull();
    }

    @Test
    void rejectsNonDraftDelete() {
        GradeStructureJpaEntity s = structure(GradeStructureStatus.APPROVED);
        given(structures.findByIdAndTenantId(s.getId(), tenantId)).willReturn(Optional.of(s));

        assertThatThrownBy(() -> useCase.delete(s.getId()))
                .isInstanceOf(GradeStructureTransitionRejectedException.class);
        verify(structures, never()).delete(any());
        verify(audit, never()).record(any());
    }

    @Test
    void crossTenantReturns404() {
        UUID id = UUID.randomUUID();
        given(structures.findByIdAndTenantId(id, tenantId)).willReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.delete(id))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void requiresGradeEdit() {
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(), Set.of(),
                Set.of(PermissionCodes.GRADE_READ), Set.of(), false, "ru-RU"));
        assertThatThrownBy(() -> useCase.delete(UUID.randomUUID()))
                .isInstanceOf(PermissionDeniedException.class);
    }
}
