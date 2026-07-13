package uz.hrlab.grading.methodology.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.methodology.domain.MethodologyStatus;
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
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Defect-1 (SERVICE LAYER) — the methodology STRUCTURE reads the scoring sheet
 * needs (version + factors + levels) must be readable by an assigned evaluator who
 * holds {@code EVALUATION_READ} but NOT {@code METHODOLOGY_READ}. The service is the
 * authoritative gate (the controller {@code @PreAuthorize} only mirrors it), so
 * this proves the broadening is enforced below the controller:
 * <ul>
 *   <li>METHODOLOGY_READ-only caller → allowed (unchanged).</li>
 *   <li>EVALUATION_READ-only caller → allowed (the fix).</li>
 *   <li>caller with NEITHER → {@link PermissionDeniedException} (403).</li>
 * </ul>
 * The methodology has a null project so the test isolates the permission gate from
 * the (unchanged) project ABAC.
 */
@Tag("security")
@ExtendWith(MockitoExtension.class)
class MethodologyQueriesEvaluatorReadTest {

    @Mock MethodologyRepository methodologies;
    @Mock MethodologyVersionRepository versions;
    @Mock FactorRepository factors;
    @Mock FactorLevelRepository levels;
    @Mock MethodologyReferencePort evaluationReference;
    @Mock AbacGate abacGate;

    MethodologyQueries queries;

    UUID tenantId;
    UUID methodologyId;
    UUID versionId;
    UUID factorId;

    @BeforeEach
    void setUp() {
        queries = new MethodologyQueries(methodologies, versions, factors, levels, evaluationReference, abacGate);
        tenantId = UUID.randomUUID();
        methodologyId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        factorId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() { TenantContextHolder.clear(); }

    // ----------------------------------------------------------- findVersionById

    @Test
    void versionReadableWithMethodologyReadOnly() {
        setContext("METHODOLOGY_READ");
        stubVersionAndMethodology();
        assertThat(queries.findVersionById(versionId)).isNotNull();
    }

    @Test
    void versionReadableWithEvaluationReadOnly() {
        // Defect-1: the assigned evaluator has EVALUATION_READ, not METHODOLOGY_READ.
        setContext("EVALUATION_READ");
        stubVersionAndMethodology();
        assertThat(queries.findVersionById(versionId)).isNotNull();
    }

    @Test
    void versionDeniedWithNeitherPermission() {
        setContext("ORG_READ");
        assertThatThrownBy(() -> queries.findVersionById(versionId))
                .isInstanceOf(PermissionDeniedException.class);
    }

    // -------------------------------------------------------- listFactorsByVersion

    @Test
    void factorsReadableWithEvaluationReadOnly() {
        setContext("EVALUATION_READ");
        stubVersionAndMethodology();
        when(factors.findAllByTenantIdAndMethodologyVersionIdOrderBySortOrderAsc(tenantId, versionId))
                .thenReturn(List.of());
        assertThat(queries.listFactorsByVersion(versionId)).isEmpty();
    }

    @Test
    void factorsDeniedWithNeitherPermission() {
        setContext("ORG_READ");
        assertThatThrownBy(() -> queries.listFactorsByVersion(versionId))
                .isInstanceOf(PermissionDeniedException.class);
    }

    // -------------------------------------------------- Defect-2: list/detail/versions
    // The by-factor scoring view's methodology selector is fed by the list /
    // detail / versions reads. An assigned committee scorer holds EVALUATION_READ,
    // not METHODOLOGY_READ, so these must use the same broadened structural gate.

    @Test
    void findMethodologyByIdReadableWithEvaluationReadOnly() {
        setContext("EVALUATION_READ");
        stubMethodologyOnly();
        assertThat(queries.findMethodologyById(methodologyId)).isNotNull();
    }

    @Test
    void findMethodologyByIdDeniedWithNeitherPermission() {
        setContext("ORG_READ");
        assertThatThrownBy(() -> queries.findMethodologyById(methodologyId))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void findByProjectReadableWithEvaluationReadOnly() {
        // null projectId → tenant-scoped paged listing, isolating the permission
        // gate from project ABAC (matching the rest of this suite's intent).
        setContext("EVALUATION_READ");
        when(methodologies.findAllByTenantId(eq(tenantId), any(Pageable.class)))
                .thenReturn(Page.empty());
        assertThat(queries.findByProject(null, Pageable.unpaged())).isEmpty();
    }

    @Test
    void findByProjectDeniedWithNeitherPermission() {
        setContext("ORG_READ");
        assertThatThrownBy(() -> queries.findByProject(null, Pageable.unpaged()))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void listVersionsReadableWithEvaluationReadOnly() {
        setContext("EVALUATION_READ");
        stubMethodologyOnly();
        when(versions.findAllByTenantIdAndMethodologyIdOrderByVersionNumberDesc(tenantId, methodologyId))
                .thenReturn(List.of());
        assertThat(queries.listVersions(methodologyId)).isEmpty();
    }

    @Test
    void listVersionsDeniedWithNeitherPermission() {
        setContext("ORG_READ");
        assertThatThrownBy(() -> queries.listVersions(methodologyId))
                .isInstanceOf(PermissionDeniedException.class);
    }

    // --------------------------------------------------------- listLevelsByFactor

    @Test
    void levelsReadableWithEvaluationReadOnly() {
        setContext("EVALUATION_READ");
        FactorJpaEntity factor = new FactorJpaEntity(
                factorId, tenantId, versionId, "F-1",
                BigDecimal.ONE, BigDecimal.TEN, 0, true);
        when(factors.findByIdAndTenantId(factorId, tenantId)).thenReturn(Optional.of(factor));
        FactorLevelJpaEntity level = new FactorLevelJpaEntity(
                UUID.randomUUID(), tenantId, factorId, "L1", 0, BigDecimal.ONE, null);
        when(levels.findAllByTenantIdAndFactorIdOrderByLevelOrderAsc(tenantId, factorId))
                .thenReturn(List.of(level));

        assertThat(queries.listLevelsByFactor(factorId)).hasSize(1);
    }

    @Test
    void levelsDeniedWithNeitherPermission() {
        setContext("ORG_READ");
        assertThatThrownBy(() -> queries.listLevelsByFactor(factorId))
                .isInstanceOf(PermissionDeniedException.class);
    }

    // --------------------------------------------------------------- helpers

    private void stubVersionAndMethodology() {
        MethodologyVersionJpaEntity v = new MethodologyVersionJpaEntity(
                versionId, tenantId, methodologyId, 1, MethodologyVersionStatus.APPROVED,
                ScoringMode.WEIGHTED_POINTS, new BigDecimal("100"), null);
        when(versions.findByIdAndTenantId(versionId, tenantId)).thenReturn(Optional.of(v));
        MethodologyJpaEntity m = new MethodologyJpaEntity(
                methodologyId, tenantId, null /* null project → isolates permission gate */,
                "M-1", MethodologyType.CLASSIC_8_FACTOR, MethodologyStatus.ACTIVE);
        when(methodologies.findByIdAndTenantId(methodologyId, tenantId)).thenReturn(Optional.of(m));
    }

    /**
     * Stubs ONLY the methodology row (null project → isolates the permission gate
     * from project ABAC). Used by the detail / versions reads that never touch
     * {@code versions.findByIdAndTenantId}, so strict stubbing stays happy.
     */
    private void stubMethodologyOnly() {
        MethodologyJpaEntity m = new MethodologyJpaEntity(
                methodologyId, tenantId, null /* null project → isolates permission gate */,
                "M-1", MethodologyType.CLASSIC_8_FACTOR, MethodologyStatus.ACTIVE);
        when(methodologies.findByIdAndTenantId(methodologyId, tenantId)).thenReturn(Optional.of(m));
    }

    private void setContext(String... permissions) {
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenantId, Set.of(), Set.of(),
                Set.of(permissions), Set.of(), false, "ru-RU"));
    }
}
