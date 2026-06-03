package uz.hrlab.grading.integration.imports.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.gradestructure.domain.GradeStructureStatus;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * PO-11 — unit tests for {@link GradeBandsRowCommitter}.
 *
 * <p>Asserts:
 * <ol>
 *   <li>Valid row creates Grade + GradeBand in the project's unique DRAFT
 *       structure.</li>
 *   <li>If no DRAFT structure exists, the commit is rejected.</li>
 *   <li>If multiple DRAFT structures exist, the commit is rejected
 *       (AMBIGUOUS_GRADE_STRUCTURE).</li>
 *   <li>min_score > max_score → INVALID_BAND_RANGE.</li>
 *   <li>Non-integer grade_code → INVALID_GRADE_NUMBER.</li>
 *   <li>Upsert path: existing grade with same grade_number updates its band.</li>
 * </ol>
 */
@Tag("imports")
class GradeBandsRowCommitterTest {

    private GradeStructureRepository structures;
    private GradeRepository grades;
    private GradeBandRepository bands;
    private AuditService audit;
    private GradeBandsRowCommitter committer;

    private UUID tenantId;
    private UUID projectId;
    private ImportRowCommitContext ctx;
    private GradeStructureJpaEntity draftStructure;

    @BeforeEach
    void setUp() {
        structures = mock(GradeStructureRepository.class);
        grades = mock(GradeRepository.class);
        bands = mock(GradeBandRepository.class);
        audit = mock(AuditService.class);
        committer = new GradeBandsRowCommitter(structures, grades, bands, audit);

        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        ctx = new ImportRowCommitContext(tenantId, projectId, UUID.randomUUID(),
                UUID.randomUUID(), "trace-g");

        draftStructure = mock(GradeStructureJpaEntity.class);
        given(draftStructure.getId()).willReturn(UUID.randomUUID());
        given(structures.findAllByTenantIdAndProjectIdAndStatusOrderByVersionNumberDesc(
                eq(tenantId), eq(projectId), eq(GradeStructureStatus.DRAFT)))
                .willReturn(List.of(draftStructure));
        given(grades.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(bands.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(grades.findByTenantIdAndGradeStructureIdAndGradeNumber(any(), any(), any(Integer.class)))
                .willReturn(Optional.empty());
        given(bands.findByTenantIdAndGradeId(any(), any())).willReturn(Optional.empty());
    }

    @Test
    void validRow_createsGradeAndBand() {
        Map<String, String> row = new HashMap<>();
        row.put("grade_code", "5");
        row.put("min_score", "500");
        row.put("max_score", "700");
        row.put("label", "Mid-senior");

        ImportRowCommitter.CommitResult result = committer.commit(row, ctx);
        assertThat(result.targetEntityType()).isEqualTo("GradeBand");
        verify(grades).save(any(GradeJpaEntity.class));
        verify(bands).save(any(GradeBandJpaEntity.class));
    }

    @Test
    void noDraftStructure_throws() {
        given(structures.findAllByTenantIdAndProjectIdAndStatusOrderByVersionNumberDesc(
                eq(tenantId), eq(projectId), eq(GradeStructureStatus.DRAFT)))
                .willReturn(List.of());
        Map<String, String> row = new HashMap<>();
        row.put("grade_code", "1");
        row.put("min_score", "0");
        row.put("max_score", "100");
        assertThatThrownBy(() -> committer.commit(row, ctx))
                .isInstanceOf(ImportRowCommitException.class)
                .hasMessageContaining("No DRAFT");
    }

    @Test
    void multipleDraftStructures_throws() {
        given(structures.findAllByTenantIdAndProjectIdAndStatusOrderByVersionNumberDesc(
                eq(tenantId), eq(projectId), eq(GradeStructureStatus.DRAFT)))
                .willReturn(List.of(draftStructure, mock(GradeStructureJpaEntity.class)));
        Map<String, String> row = new HashMap<>();
        row.put("grade_code", "1");
        row.put("min_score", "0");
        row.put("max_score", "100");
        assertThatThrownBy(() -> committer.commit(row, ctx))
                .isInstanceOf(ImportRowCommitException.class)
                .hasMessageContaining("Multiple");
    }

    @Test
    void invertedRange_throws() {
        Map<String, String> row = new HashMap<>();
        row.put("grade_code", "1");
        row.put("min_score", "200");
        row.put("max_score", "100");
        ImportRowCommitException err = (ImportRowCommitException) org.junit.jupiter.api.Assertions
                .assertThrows(ImportRowCommitException.class,
                        () -> committer.commit(row, ctx));
        assertThat(err.getCode()).isEqualTo("INVALID_BAND_RANGE");
    }

    @Test
    void nonIntegerGradeCode_throws() {
        Map<String, String> row = new HashMap<>();
        row.put("grade_code", "FIVE");
        row.put("min_score", "0");
        row.put("max_score", "100");
        ImportRowCommitException err = (ImportRowCommitException) org.junit.jupiter.api.Assertions
                .assertThrows(ImportRowCommitException.class,
                        () -> committer.commit(row, ctx));
        assertThat(err.getCode()).isEqualTo("INVALID_GRADE_NUMBER");
    }

    @Test
    void existingGradeIsUpserted_bandIsUpdated() {
        UUID gradeId = UUID.randomUUID();
        GradeJpaEntity existingGrade = mock(GradeJpaEntity.class);
        given(existingGrade.getId()).willReturn(gradeId);
        given(grades.findByTenantIdAndGradeStructureIdAndGradeNumber(
                eq(tenantId), any(), eq(7))).willReturn(Optional.of(existingGrade));

        UUID bandId = UUID.randomUUID();
        GradeBandJpaEntity existingBand = mock(GradeBandJpaEntity.class);
        given(existingBand.getId()).willReturn(bandId);
        given(bands.findByTenantIdAndGradeId(eq(tenantId), eq(gradeId)))
                .willReturn(Optional.of(existingBand));

        Map<String, String> row = new HashMap<>();
        row.put("grade_code", "7");
        row.put("min_score", "700");
        row.put("max_score", "899");
        committer.commit(row, ctx);

        // Grade NOT re-created (upsert path).
        verify(grades, org.mockito.Mockito.never()).save(any(GradeJpaEntity.class));
        // Band saved with new min/max.
        verify(existingBand).setMinScore(any());
        verify(existingBand).setMaxScore(any());
        verify(bands).save(any());
    }
}
