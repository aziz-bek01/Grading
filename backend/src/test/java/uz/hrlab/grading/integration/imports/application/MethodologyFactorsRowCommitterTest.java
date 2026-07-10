package uz.hrlab.grading.integration.imports.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.methodology.domain.MethodologyType;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatus;
import uz.hrlab.grading.methodology.domain.ScoringMode;
import uz.hrlab.grading.methodology.infrastructure.FactorJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link MethodologyFactorsRowCommitter} (mirrors the
 * {@link GradeBandsRowCommitterTest} style — mocked repositories + audit).
 *
 * <p>Covers: create-new happy path; upsert-into-existing-DRAFT; reject an
 * APPROVED methodology ({@code METHODOLOGY_NOT_DRAFT}); invalid enum + invalid
 * number; missing project; metadata mismatch; and cross-tenant isolation (the
 * tenant-scoped lookup never resolves a foreign methodology).
 */
@Tag("imports")
class MethodologyFactorsRowCommitterTest {

    private MethodologyRepository methodologies;
    private MethodologyVersionRepository versions;
    private FactorRepository factors;
    private FactorLevelRepository levels;
    private AuditService audit;
    private MethodologyFactorsRowCommitter committer;

    private UUID tenantId;
    private UUID projectId;
    private ImportRowCommitContext ctx;

    @BeforeEach
    void setUp() {
        methodologies = mock(MethodologyRepository.class);
        versions = mock(MethodologyVersionRepository.class);
        factors = mock(FactorRepository.class);
        levels = mock(FactorLevelRepository.class);
        audit = mock(AuditService.class);
        committer = new MethodologyFactorsRowCommitter(methodologies, versions, factors, levels, audit);

        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        ctx = new ImportRowCommitContext(tenantId, projectId, UUID.randomUUID(),
                UUID.randomUUID(), "trace-m");

        given(methodologies.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(versions.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(factors.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(levels.save(any())).willAnswer(inv -> inv.getArgument(0));
        // Default: nothing exists yet (create path).
        given(methodologies.findByTenantIdAndProjectIdAndCode(any(), any(), any()))
                .willReturn(Optional.empty());
        given(factors.findByTenantIdAndMethodologyVersionIdAndCode(any(), any(), any()))
                .willReturn(Optional.empty());
        given(levels.findByTenantIdAndFactorIdAndCode(any(), any(), any()))
                .willReturn(Optional.empty());
    }

    // ------------------------------------------------------------- happy paths

    @Test
    void newMethodology_createsContainerVersionFactorAndLevel() {
        ImportRowCommitter.CommitResult result = committer.commit(baseRow(), ctx);

        assertThat(result.targetEntityType()).isEqualTo("FactorLevel");
        assertThat(result.auditAction()).isEqualTo(AuditAction.FACTOR_LEVEL_CREATED);

        // Container + version + factor + level all persisted.
        verify(methodologies).save(any(MethodologyJpaEntity.class));
        verify(versions).save(any(MethodologyVersionJpaEntity.class));
        verify(factors).save(any(FactorJpaEntity.class));
        verify(levels).save(any(FactorLevelJpaEntity.class));
    }

    @Test
    void newMethodology_persistsUnderContextTenantAndProject_withRuRuName() {
        committer.commit(baseRow(), ctx);

        ArgumentCaptor<MethodologyJpaEntity> cap = ArgumentCaptor.forClass(MethodologyJpaEntity.class);
        verify(methodologies).save(cap.capture());
        MethodologyJpaEntity saved = cap.getValue();
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getProjectId()).isEqualTo(projectId);
        assertThat(saved.getCode()).isEqualTo("ACME-GRADING");
        assertThat(saved.getMethodologyType()).isEqualTo(MethodologyType.CLASSIC_8_FACTOR);
        assertThat(saved.getNameI18n()).containsEntry("ru-RU", "ACME grading");

        ArgumentCaptor<FactorLevelJpaEntity> levelCap = ArgumentCaptor.forClass(FactorLevelJpaEntity.class);
        verify(levels).save(levelCap.capture());
        assertThat(levelCap.getValue().getTenantId()).isEqualTo(tenantId);
        assertThat(levelCap.getValue().getLabelI18n()).containsEntry("ru-RU", "Basic");
    }

    @Test
    void existingDraft_matchingMetadata_upsertsWithoutCreatingContainer() {
        MethodologyJpaEntity m = mock(MethodologyJpaEntity.class);
        given(m.getId()).willReturn(UUID.randomUUID());
        given(m.getMethodologyType()).willReturn(MethodologyType.CLASSIC_8_FACTOR);
        // Name already matches the row → the last-wins name resync is a no-op,
        // so the container is never re-saved.
        given(m.getNameI18n()).willReturn(Map.of("ru-RU", "ACME grading"));
        given(methodologies.findByTenantIdAndProjectIdAndCode(eq(tenantId), eq(projectId), eq("ACME-GRADING")))
                .willReturn(Optional.of(m));

        MethodologyVersionJpaEntity v = mock(MethodologyVersionJpaEntity.class);
        given(v.getId()).willReturn(UUID.randomUUID());
        given(v.getStatus()).willReturn(MethodologyVersionStatus.DRAFT);
        given(v.getScoringMode()).willReturn(ScoringMode.WEIGHTED_POINTS);
        given(v.getTargetTotalPoints()).willReturn(new BigDecimal("1000"));
        given(versions.findFirstByTenantIdAndMethodologyIdOrderByVersionNumberDesc(eq(tenantId), any()))
                .willReturn(Optional.of(v));

        ImportRowCommitter.CommitResult result = committer.commit(baseRow(), ctx);

        assertThat(result.targetEntityType()).isEqualTo("FactorLevel");
        // No new container / version created — reused the existing DRAFT.
        verify(methodologies, never()).save(any());
        verify(versions, never()).save(any());
        // Factor + level still written into the existing DRAFT.
        verify(factors).save(any(FactorJpaEntity.class));
        verify(levels).save(any(FactorLevelJpaEntity.class));
    }

    @Test
    void existingLevelCode_isUpdatedInPlace_lastWins() {
        MethodologyJpaEntity m = mock(MethodologyJpaEntity.class);
        given(m.getId()).willReturn(UUID.randomUUID());
        given(m.getMethodologyType()).willReturn(MethodologyType.CLASSIC_8_FACTOR);
        given(methodologies.findByTenantIdAndProjectIdAndCode(any(), any(), any()))
                .willReturn(Optional.of(m));
        MethodologyVersionJpaEntity v = mock(MethodologyVersionJpaEntity.class);
        given(v.getId()).willReturn(UUID.randomUUID());
        given(v.getStatus()).willReturn(MethodologyVersionStatus.DRAFT);
        given(v.getScoringMode()).willReturn(ScoringMode.WEIGHTED_POINTS);
        given(v.getTargetTotalPoints()).willReturn(new BigDecimal("1000"));
        given(versions.findFirstByTenantIdAndMethodologyIdOrderByVersionNumberDesc(any(), any()))
                .willReturn(Optional.of(v));

        FactorJpaEntity existingFactor = mock(FactorJpaEntity.class);
        given(existingFactor.getId()).willReturn(UUID.randomUUID());
        given(existingFactor.getMaxPoints()).willReturn(new BigDecimal("200"));
        given(factors.findByTenantIdAndMethodologyVersionIdAndCode(any(), any(), eq("KNOWLEDGE")))
                .willReturn(Optional.of(existingFactor));

        FactorLevelJpaEntity existingLevel = mock(FactorLevelJpaEntity.class);
        given(existingLevel.getId()).willReturn(UUID.randomUUID());
        given(levels.findByTenantIdAndFactorIdAndCode(any(), any(), eq("L1")))
                .willReturn(Optional.of(existingLevel));

        ImportRowCommitter.CommitResult result = committer.commit(baseRow(), ctx);

        assertThat(result.auditAction()).isEqualTo(AuditAction.FACTOR_LEVEL_UPDATED);
        verify(existingLevel).setPoints(any());
        verify(existingLevel).setLabelI18n(any());
        verify(levels).save(existingLevel);
    }

    // --------------------------------------------------------------- rejections

    @Test
    void approvedMethodology_rejectedAsNotDraft() {
        MethodologyJpaEntity m = mock(MethodologyJpaEntity.class);
        given(m.getId()).willReturn(UUID.randomUUID());
        given(methodologies.findByTenantIdAndProjectIdAndCode(any(), any(), any()))
                .willReturn(Optional.of(m));
        MethodologyVersionJpaEntity v = mock(MethodologyVersionJpaEntity.class);
        given(v.getStatus()).willReturn(MethodologyVersionStatus.APPROVED);
        given(versions.findFirstByTenantIdAndMethodologyIdOrderByVersionNumberDesc(any(), any()))
                .willReturn(Optional.of(v));

        ImportRowCommitException err = assertThrows(ImportRowCommitException.class,
                () -> committer.commit(baseRow(), ctx));
        assertThat(err.getCode()).isEqualTo("METHODOLOGY_NOT_DRAFT");
        verify(factors, never()).save(any());
        verify(levels, never()).save(any());
    }

    @Test
    void metadataMismatch_onExistingDraft_rejected() {
        MethodologyJpaEntity m = mock(MethodologyJpaEntity.class);
        given(m.getId()).willReturn(UUID.randomUUID());
        // Existing methodology has a DIFFERENT type than the row's CLASSIC_8_FACTOR.
        given(m.getMethodologyType()).willReturn(MethodologyType.CUSTOM);
        given(methodologies.findByTenantIdAndProjectIdAndCode(any(), any(), any()))
                .willReturn(Optional.of(m));
        MethodologyVersionJpaEntity v = mock(MethodologyVersionJpaEntity.class);
        given(v.getStatus()).willReturn(MethodologyVersionStatus.DRAFT);
        given(v.getScoringMode()).willReturn(ScoringMode.WEIGHTED_POINTS);
        given(v.getTargetTotalPoints()).willReturn(new BigDecimal("1000"));
        given(versions.findFirstByTenantIdAndMethodologyIdOrderByVersionNumberDesc(any(), any()))
                .willReturn(Optional.of(v));

        ImportRowCommitException err = assertThrows(ImportRowCommitException.class,
                () -> committer.commit(baseRow(), ctx));
        assertThat(err.getCode()).isEqualTo("METHODOLOGY_METADATA_MISMATCH");
        verify(factors, never()).save(any());
    }

    @Test
    void invalidMethodologyType_rejected() {
        Map<String, String> row = baseRow();
        row.put("methodology_type", "BOGUS_TYPE");
        ImportRowCommitException err = assertThrows(ImportRowCommitException.class,
                () -> committer.commit(row, ctx));
        assertThat(err.getCode()).isEqualTo("INVALID_METHODOLOGY_TYPE");
    }

    @Test
    void invalidScoringMode_rejected() {
        Map<String, String> row = baseRow();
        row.put("scoring_mode", "BOGUS_MODE");
        ImportRowCommitException err = assertThrows(ImportRowCommitException.class,
                () -> committer.commit(row, ctx));
        assertThat(err.getCode()).isEqualTo("INVALID_SCORING_MODE");
    }

    @Test
    void invalidTargetTotalPoints_rejected() {
        Map<String, String> row = baseRow();
        row.put("target_total_points", "not-a-number");
        ImportRowCommitException err = assertThrows(ImportRowCommitException.class,
                () -> committer.commit(row, ctx));
        assertThat(err.getCode()).isEqualTo("INVALID_TOTAL_POINTS");
    }

    @Test
    void nonNumericWeight_rejected() {
        Map<String, String> row = baseRow();
        row.put("weight", "heavy");
        ImportRowCommitException err = assertThrows(ImportRowCommitException.class,
                () -> committer.commit(row, ctx));
        assertThat(err.getCode()).isEqualTo("INVALID_FACTOR_WEIGHT");
    }

    @Test
    void nullProject_rejected() {
        ImportRowCommitContext noProject = new ImportRowCommitContext(
                tenantId, null, UUID.randomUUID(), UUID.randomUUID(), "trace-m");
        ImportRowCommitException err = assertThrows(ImportRowCommitException.class,
                () -> committer.commit(baseRow(), noProject));
        assertThat(err.getCode()).isEqualTo("PROJECT_REQUIRED");
    }

    // ---------------------------------------------------------- WEIGHTED_SCALE

    @Test
    void weightedScale_withScaleValue_persistsScaleValueOnLevel() {
        Map<String, String> row = baseRow();
        row.put("scoring_mode", "WEIGHTED_SCALE");
        row.put("scale_value", "3");

        committer.commit(row, ctx);

        ArgumentCaptor<FactorLevelJpaEntity> cap = ArgumentCaptor.forClass(FactorLevelJpaEntity.class);
        verify(levels).save(cap.capture());
        // The P0 fix: scaleValue is non-null and carries the parsed value, so the
        // scoring engine's weightedScale() no longer treats it as 0.
        assertThat(cap.getValue().getScaleValue()).isEqualByComparingTo(new BigDecimal("3"));
    }

    @Test
    void weightedScale_withoutScaleValue_rejected() {
        Map<String, String> row = baseRow();
        row.put("scoring_mode", "WEIGHTED_SCALE");
        // scale_value intentionally absent.
        ImportRowCommitException err = assertThrows(ImportRowCommitException.class,
                () -> committer.commit(row, ctx));
        assertThat(err.getCode()).isEqualTo("MISSING_SCALE_VALUE");
        verify(levels, never()).save(any());
    }

    @Test
    void invalidScaleValue_rejected() {
        Map<String, String> row = baseRow();
        row.put("scoring_mode", "WEIGHTED_SCALE");
        row.put("scale_value", "wide");
        ImportRowCommitException err = assertThrows(ImportRowCommitException.class,
                () -> committer.commit(row, ctx));
        assertThat(err.getCode()).isEqualTo("INVALID_SCALE_VALUE");
    }

    @Test
    void weightedPoints_blankScaleValue_isAllowed_andStoredNull() {
        // scale_value is optional for non-WEIGHTED_SCALE modes.
        committer.commit(baseRow(), ctx); // baseRow is WEIGHTED_POINTS, no scale_value
        ArgumentCaptor<FactorLevelJpaEntity> cap = ArgumentCaptor.forClass(FactorLevelJpaEntity.class);
        verify(levels).save(cap.capture());
        assertThat(cap.getValue().getScaleValue()).isNull();
    }

    // ------------------------------------------------------- re-import resync

    @Test
    void reImport_resyncsExistingFactorWeightAndName_lastWins() {
        MethodologyJpaEntity m = mock(MethodologyJpaEntity.class);
        given(m.getId()).willReturn(UUID.randomUUID());
        given(m.getMethodologyType()).willReturn(MethodologyType.CLASSIC_8_FACTOR);
        given(m.getNameI18n()).willReturn(Map.of("ru-RU", "ACME grading"));
        given(methodologies.findByTenantIdAndProjectIdAndCode(any(), any(), any()))
                .willReturn(Optional.of(m));
        MethodologyVersionJpaEntity v = mock(MethodologyVersionJpaEntity.class);
        given(v.getId()).willReturn(UUID.randomUUID());
        given(v.getStatus()).willReturn(MethodologyVersionStatus.DRAFT);
        given(v.getScoringMode()).willReturn(ScoringMode.WEIGHTED_POINTS);
        given(v.getTargetTotalPoints()).willReturn(new BigDecimal("1000"));
        given(versions.findFirstByTenantIdAndMethodologyIdOrderByVersionNumberDesc(any(), any()))
                .willReturn(Optional.of(v));

        FactorJpaEntity existingFactor = mock(FactorJpaEntity.class);
        given(existingFactor.getId()).willReturn(UUID.randomUUID());
        given(existingFactor.getMaxPoints()).willReturn(new BigDecimal("200"));
        given(factors.findByTenantIdAndMethodologyVersionIdAndCode(any(), any(), eq("KNOWLEDGE")))
                .willReturn(Optional.of(existingFactor));

        // Re-import the same factor with a CORRECTED weight + name.
        Map<String, String> row = baseRow();
        row.put("weight", "15.0");
        row.put("factor_name", "Knowledge (corrected)");

        committer.commit(row, ctx);

        // The existing factor is updated in place (not recreated) with the new
        // weight + ru-RU name — the P1 fix. The setter calls on the resolved
        // existing factor (rather than a freshly-constructed one) prove the
        // update path, not the create path.
        verify(existingFactor).setWeight(new BigDecimal("15.0"));
        verify(existingFactor).setNameI18n(Map.of("ru-RU", "Knowledge (corrected)"));
        verify(factors).save(existingFactor);
    }

    @Test
    void reImport_resyncsExistingMethodologyName_lastWins() {
        MethodologyJpaEntity m = mock(MethodologyJpaEntity.class);
        given(m.getId()).willReturn(UUID.randomUUID());
        given(m.getMethodologyType()).willReturn(MethodologyType.CLASSIC_8_FACTOR);
        given(m.getNameI18n()).willReturn(Map.of("ru-RU", "Old name"));
        given(methodologies.findByTenantIdAndProjectIdAndCode(any(), any(), any()))
                .willReturn(Optional.of(m));
        MethodologyVersionJpaEntity v = mock(MethodologyVersionJpaEntity.class);
        given(v.getId()).willReturn(UUID.randomUUID());
        given(v.getStatus()).willReturn(MethodologyVersionStatus.DRAFT);
        given(v.getScoringMode()).willReturn(ScoringMode.WEIGHTED_POINTS);
        given(v.getTargetTotalPoints()).willReturn(new BigDecimal("1000"));
        given(versions.findFirstByTenantIdAndMethodologyIdOrderByVersionNumberDesc(any(), any()))
                .willReturn(Optional.of(v));

        committer.commit(baseRow(), ctx); // baseRow name = "ACME grading"

        // The DRAFT methodology's ru-RU name resyncs last-wins.
        verify(m).setNameI18n(Map.of("ru-RU", "ACME grading"));
        verify(methodologies).save(m);
    }

    // ------------------------------------------------------------ tenant isolation

    @Test
    void crossTenant_scopedLookupNeverResolvesForeignMethodology_createsInOwnTenant() {
        // A methodology with the SAME code exists in ANOTHER tenant; the
        // tenant-scoped lookup for OUR tenant returns empty, so the committer
        // creates a brand-new methodology scoped to ctx.tenantId() — never
        // touching the foreign row.
        given(methodologies.findByTenantIdAndProjectIdAndCode(eq(tenantId), eq(projectId), eq("ACME-GRADING")))
                .willReturn(Optional.empty());

        committer.commit(baseRow(), ctx);

        // The lookup was performed with OUR tenant id (not the row's), and the
        // create wrote under OUR tenant id.
        verify(methodologies).findByTenantIdAndProjectIdAndCode(eq(tenantId), eq(projectId), eq("ACME-GRADING"));
        ArgumentCaptor<MethodologyJpaEntity> cap = ArgumentCaptor.forClass(MethodologyJpaEntity.class);
        verify(methodologies).save(cap.capture());
        assertThat(cap.getValue().getTenantId()).isEqualTo(tenantId);
    }

    // --------------------------------------------------------------------- helper

    /** One complete METHODOLOGY_FACTORS_V1 row (KNOWLEDGE / L1). */
    private static Map<String, String> baseRow() {
        Map<String, String> r = new LinkedHashMap<>();
        r.put("methodology_code", "ACME-GRADING");
        r.put("methodology_name", "ACME grading");
        r.put("methodology_type", "CLASSIC_8_FACTOR");
        r.put("scoring_mode", "WEIGHTED_POINTS");
        r.put("target_total_points", "1000");
        r.put("factor_code", "KNOWLEDGE");
        r.put("factor_name", "Knowledge");
        r.put("weight", "12.5");
        r.put("score", "40");
        r.put("level_code", "L1");
        r.put("level_name", "Basic");
        r.put("level_order", "1");
        r.put("level_description", "Knowledge - Basic");
        return r;
    }
}
