package uz.hrlab.grading.integration.imports.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    /**
     * Coverage gap closed: no existing test asserted WHICH audit events the
     * committer actually emits (the per-entity {@code audit.record(...)} calls
     * inside {@code resolveDraftVersion}/{@code resolveFactor}/{@code
     * upsertLevel} were previously unverified — only the returned {@code
     * CommitResult.auditAction()} was checked, which is a DIFFERENT code path).
     * Audit completeness for sensitive create actions is a mandatory pack
     * (CLAUDE.md "audit ~20 events"); this locks the exact action sequence and
     * proves every audit row is scoped to the AUTHENTICATED tenant, never a
     * row-supplied one.
     */
    @Test
    void newMethodology_emitsExpectedAuditEventsForEachCreatedEntity_scopedToAuthenticatedTenant() {
        committer.commit(baseRow(), ctx);

        ArgumentCaptor<AuditEvent> cap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, times(4)).record(cap.capture());
        java.util.List<String> actions = cap.getAllValues().stream()
                .map(AuditEvent::action).toList();

        assertThat(actions).containsExactly(
                AuditAction.METHODOLOGY_CREATED,
                AuditAction.METHODOLOGY_VERSION_CREATED,
                AuditAction.FACTOR_CREATED,
                AuditAction.FACTOR_LEVEL_CREATED);
        assertThat(cap.getAllValues())
                .as("every audit row must carry the AUTHENTICATED tenant (ctx.tenantId()), "
                        + "never a tenant id sourced from the imported row")
                .allSatisfy(evt -> assertThat(evt.tenantId()).isEqualTo(tenantId));
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

    // --------------------------------------------------------- multi-row grouping

    /**
     * Coverage gap closed: the ONLY existing proof that levels group correctly
     * under their factor ACROSS ROWS (factor created once on first sighting,
     * subsequent rows for the same {@code factor_code} accumulate levels rather
     * than creating a duplicate factor) lived exclusively in
     * {@code MethodologyImportApprovableIntegrationTest}, which requires Docker
     * (Testcontainers Postgres) and cannot run in this environment. This is a
     * fast, Docker-free unit-level equivalent: it drives FOUR sequential
     * {@code commit()} calls (2 factors x 2 levels, exactly mirroring a real
     * parsed METHODOLOGY_FACTORS_V1 sheet) against the SAME committer instance,
     * re-stubbing the repository finders between rows to return the REAL objects
     * captured from the previous row's {@code save()} — faithfully simulating
     * what a real Postgres SELECT would resolve inside the same commit
     * transaction — and asserts the row count collapses to exactly 2 distinct
     * Factor ids and exactly 1 Methodology / 1 MethodologyVersion, never 4
     * factors or 4 methodologies.
     */
    @Test
    void fourRowsTwoFactorsTwoLevelsEach_groupsLevelsUnderTwoFactors_notFour() {
        // Row 1 — KNOWLEDGE / L1: creates methodology + v1 + factor + level.
        committer.commit(groupingRow("KNOWLEDGE", "Knowledge", "L1", "Basic", "40"), ctx);

        ArgumentCaptor<MethodologyJpaEntity> mCap = ArgumentCaptor.forClass(MethodologyJpaEntity.class);
        verify(methodologies, times(1)).save(mCap.capture());
        MethodologyJpaEntity savedMethodology = mCap.getValue();

        ArgumentCaptor<MethodologyVersionJpaEntity> vCap = ArgumentCaptor.forClass(MethodologyVersionJpaEntity.class);
        verify(versions, times(1)).save(vCap.capture());
        MethodologyVersionJpaEntity savedVersion = vCap.getValue();

        ArgumentCaptor<FactorJpaEntity> f1Cap = ArgumentCaptor.forClass(FactorJpaEntity.class);
        verify(factors, times(1)).save(f1Cap.capture());
        FactorJpaEntity knowledgeFactor = f1Cap.getValue();

        // From row 2 onward, the methodology/version/factor already exist —
        // reconfigure the tenant-scoped finders exactly as a real Postgres
        // SELECT would resolve them inside the same commit transaction.
        given(methodologies.findByTenantIdAndProjectIdAndCode(eq(tenantId), eq(projectId), eq("ACME-GRADING")))
                .willReturn(Optional.of(savedMethodology));
        given(versions.findFirstByTenantIdAndMethodologyIdOrderByVersionNumberDesc(
                eq(tenantId), eq(savedMethodology.getId())))
                .willReturn(Optional.of(savedVersion));
        given(factors.findByTenantIdAndMethodologyVersionIdAndCode(
                eq(tenantId), eq(savedVersion.getId()), eq("KNOWLEDGE")))
                .willReturn(Optional.of(knowledgeFactor));

        // Row 2 — SAME factor_code, a SECOND level_code: must accumulate under
        // the SAME factor, never create a duplicate KNOWLEDGE factor.
        committer.commit(groupingRow("KNOWLEDGE", "Knowledge", "L2", "Advanced", "80"), ctx);
        verify(factors, times(2)).save(any());       // row1 create + row2 resync, same entity
        verify(methodologies, times(1)).save(any());  // no re-save — name unchanged
        verify(versions, times(1)).save(any());       // no new version

        // Row 3 — a DIFFERENT factor_code: a genuinely NEW factor.
        committer.commit(groupingRow("EXPERIENCE", "Experience", "L1", "Basic", "40"), ctx);
        ArgumentCaptor<FactorJpaEntity> f2Cap = ArgumentCaptor.forClass(FactorJpaEntity.class);
        verify(factors, times(3)).save(f2Cap.capture());
        FactorJpaEntity experienceFactor = f2Cap.getValue(); // most recent = row3's create
        assertThat(experienceFactor.getId()).isNotEqualTo(knowledgeFactor.getId());

        given(factors.findByTenantIdAndMethodologyVersionIdAndCode(
                eq(tenantId), eq(savedVersion.getId()), eq("EXPERIENCE")))
                .willReturn(Optional.of(experienceFactor));

        // Row 4 — EXPERIENCE / L2: accumulates under the EXPERIENCE factor.
        committer.commit(groupingRow("EXPERIENCE", "Experience", "L2", "Advanced", "80"), ctx);

        // ------------------------------------------------------ final assertions
        verify(factors, times(4)).save(any());        // 2 creates + 2 resyncs
        verify(methodologies, times(1)).save(any());   // exactly ONE methodology, ever
        verify(versions, times(1)).save(any());        // exactly ONE version, ever

        ArgumentCaptor<FactorLevelJpaEntity> levelCap = ArgumentCaptor.forClass(FactorLevelJpaEntity.class);
        verify(levels, times(4)).save(levelCap.capture());
        Set<UUID> distinctLevelIds = levelCap.getAllValues().stream()
                .map(FactorLevelJpaEntity::getId).collect(Collectors.toSet());
        assertThat(distinctLevelIds)
                .as("4 rows across 2 factors must create 4 distinct FactorLevel rows")
                .hasSize(4);
    }

    /** Row helper for the multi-row grouping test — metadata fixed, factor/level vary. */
    private static Map<String, String> groupingRow(String factorCode, String factorName,
                                                    String levelCode, String levelName, String score) {
        Map<String, String> r = new LinkedHashMap<>();
        r.put("methodology_code", "ACME-GRADING");
        r.put("methodology_name", "ACME grading");
        r.put("methodology_type", "CLASSIC_8_FACTOR");
        r.put("scoring_mode", "WEIGHTED_POINTS");
        r.put("target_total_points", "1000");
        r.put("factor_code", factorCode);
        r.put("factor_name", factorName);
        r.put("weight", "50");
        r.put("score", score);
        r.put("level_code", levelCode);
        r.put("level_name", levelName);
        return r;
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

    // ------------------------------------------------------------- localization

    /**
     * The whole point of the optional per-locale columns: ONE upload fills a
     * methodology in every supported language. Before them, every text write was
     * {@code Map.of("ru-RU", …)}, so a bilingual (RU + UZ) methodology could not
     * be imported at all — the translation-matrix UI covers factor NAMES only,
     * never level labels or descriptions.
     */
    @Test
    void localeColumns_populateEverySupportedLocale_onCreate() {
        Map<String, String> row = baseRow();
        row.put("methodology_name_uz", "ACME грейдлаш");
        row.put("factor_name_uz", "Билим");
        row.put("factor_name_uz_latn", "Bilim");
        row.put("factor_name_en", "Knowledge");
        row.put("level_name_uz", "1-даража");
        row.put("level_description_uz", "Билим — бошланғич даража");

        committer.commit(row, ctx);

        ArgumentCaptor<MethodologyJpaEntity> mCap = ArgumentCaptor.forClass(MethodologyJpaEntity.class);
        verify(methodologies).save(mCap.capture());
        assertThat(mCap.getValue().getNameI18n())
                .containsEntry("ru-RU", "ACME grading")
                .containsEntry("uz-Cyrl-UZ", "ACME грейдлаш");

        ArgumentCaptor<FactorJpaEntity> fCap = ArgumentCaptor.forClass(FactorJpaEntity.class);
        verify(factors).save(fCap.capture());
        assertThat(fCap.getValue().getNameI18n()).containsOnly(
                entry("ru-RU", "Knowledge"),
                entry("uz-Cyrl-UZ", "Билим"),
                entry("uz-Latn-UZ", "Bilim"),
                entry("en-US", "Knowledge"));

        ArgumentCaptor<FactorLevelJpaEntity> lCap = ArgumentCaptor.forClass(FactorLevelJpaEntity.class);
        verify(levels).save(lCap.capture());
        assertThat(lCap.getValue().getLabelI18n())
                .containsEntry("ru-RU", "Basic")
                .containsEntry("uz-Cyrl-UZ", "1-даража");
        assertThat(lCap.getValue().getDescriptionI18n())
                .containsEntry("ru-RU", "Knowledge - Basic")
                .containsEntry("uz-Cyrl-UZ", "Билим — бошланғич даража");
    }

    /** {@code _uz_cyrl} is the explicit spelling of the {@code _uz} alias. */
    @Test
    void uzCyrlSuffix_isAcceptedAsExplicitAliasOfUz() {
        Map<String, String> row = baseRow();
        row.put("factor_name_uz_cyrl", "Билим");

        committer.commit(row, ctx);

        ArgumentCaptor<FactorJpaEntity> fCap = ArgumentCaptor.forClass(FactorJpaEntity.class);
        verify(factors).save(fCap.capture());
        assertThat(fCap.getValue().getNameI18n()).containsEntry("uz-Cyrl-UZ", "Билим");
    }

    /** A blank locale cell must not write an empty translation over nothing. */
    @Test
    void blankLocaleCell_isSkipped_notStoredAsEmptyTranslation() {
        Map<String, String> row = baseRow();
        row.put("factor_name_uz", "   ");

        committer.commit(row, ctx);

        ArgumentCaptor<FactorJpaEntity> fCap = ArgumentCaptor.forClass(FactorJpaEntity.class);
        verify(factors).save(fCap.capture());
        assertThat(fCap.getValue().getNameI18n()).containsOnlyKeys("ru-RU");
    }

    /**
     * Locale maps MERGE. A correction file that carries only the Russian columns
     * must not wipe Uzbek text loaded by an earlier upload — the pre-existing
     * {@code Map.of(PRIMARY_LOCALE, …)} writes did exactly that, silently
     * destroying translation work on every re-import.
     */
    @Test
    void russianOnlyReimport_preservesPreviouslyImportedUzbekTranslations() {
        MethodologyJpaEntity m = mock(MethodologyJpaEntity.class);
        given(m.getId()).willReturn(UUID.randomUUID());
        given(m.getMethodologyType()).willReturn(MethodologyType.CLASSIC_8_FACTOR);
        given(m.getNameI18n()).willReturn(Map.of("ru-RU", "Old", "uz-Cyrl-UZ", "Эски"));
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
        given(existingFactor.getNameI18n())
                .willReturn(Map.of("ru-RU", "Knowledge", "uz-Cyrl-UZ", "Билим"));
        given(factors.findByTenantIdAndMethodologyVersionIdAndCode(any(), any(), eq("KNOWLEDGE")))
                .willReturn(Optional.of(existingFactor));

        FactorLevelJpaEntity existingLevel = mock(FactorLevelJpaEntity.class);
        given(existingLevel.getId()).willReturn(UUID.randomUUID());
        given(existingLevel.getLabelI18n())
                .willReturn(Map.of("ru-RU", "Basic", "uz-Cyrl-UZ", "1-даража"));
        given(existingLevel.getDescriptionI18n())
                .willReturn(Map.of("ru-RU", "old desc", "uz-Cyrl-UZ", "эски таъриф"));
        given(levels.findByTenantIdAndFactorIdAndCode(any(), any(), eq("L1")))
                .willReturn(Optional.of(existingLevel));

        // baseRow() carries ru-RU columns ONLY — no _uz siblings at all.
        committer.commit(baseRow(), ctx);

        verify(m).setNameI18n(Map.of("ru-RU", "ACME grading", "uz-Cyrl-UZ", "Эски"));
        verify(existingFactor).setNameI18n(
                Map.of("ru-RU", "Knowledge", "uz-Cyrl-UZ", "Билим"));
        verify(existingLevel).setLabelI18n(
                Map.of("ru-RU", "Basic", "uz-Cyrl-UZ", "1-даража"));
        verify(existingLevel).setDescriptionI18n(
                Map.of("ru-RU", "Knowledge - Basic", "uz-Cyrl-UZ", "эски таъриф"));
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
