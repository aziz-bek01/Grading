package uz.hrlab.grading.gradestructure.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.gradestructure.domain.GradeBandLookupResult;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for the KEY integration component: GradeBandLookupService.
 * Uses lightweight stubs for the JPA repositories so we exercise the lookup
 * logic without a database (or Mockito).
 */
@Tag("workflow")
class GradeBandLookupServiceTest {

    private StubBandRepo bandRepo;
    private StubGradeRepo gradeRepo;
    private GradeBandLookupService service;
    private UUID tenantId;
    private UUID structureId;
    private UUID g1, g2, g3, b1, b2, b3;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        structureId = UUID.randomUUID();
        g1 = UUID.randomUUID(); g2 = UUID.randomUUID(); g3 = UUID.randomUUID();
        b1 = UUID.randomUUID(); b2 = UUID.randomUUID(); b3 = UUID.randomUUID();

        bandRepo = new StubBandRepo();
        gradeRepo = new StubGradeRepo();

        // 3 bands: G1: 0-99.9999, G2: 100-199.9999, G3: 200-300
        bandRepo.add(b1, tenantId, g1, structureId, "0",        "99.9999");
        bandRepo.add(b2, tenantId, g2, structureId, "100.0000", "199.9999");
        bandRepo.add(b3, tenantId, g3, structureId, "200.0000", "300.0000");

        gradeRepo.add(g1, tenantId, 1);
        gradeRepo.add(g2, tenantId, 2);
        gradeRepo.add(g3, tenantId, 3);

        service = new GradeBandLookupService(bandRepo, gradeRepo);
    }

    @Test
    void lookupReturnsBandForScoreInMiddle() {
        Optional<GradeBandLookupResult> r =
                service.lookup(tenantId, structureId, new BigDecimal("150"));
        assertThat(r).isPresent();
        assertThat(r.get().gradeNumber()).isEqualTo(2);
        assertThat(r.get().gradeBandId()).isEqualTo(b2);
    }

    @Test
    void lookupReturnsBandForScoreAtMinBoundary() {
        Optional<GradeBandLookupResult> r =
                service.lookup(tenantId, structureId, new BigDecimal("0"));
        assertThat(r).isPresent();
        assertThat(r.get().gradeNumber()).isEqualTo(1);
    }

    @Test
    void lookupReturnsBandForScoreAtMaxBoundary() {
        Optional<GradeBandLookupResult> r =
                service.lookup(tenantId, structureId, new BigDecimal("300"));
        assertThat(r).isPresent();
        assertThat(r.get().gradeNumber()).isEqualTo(3);
    }

    @Test
    void lookupReturnsBandForExactInteriorBoundary() {
        // 100 is start of G2
        Optional<GradeBandLookupResult> r =
                service.lookup(tenantId, structureId, new BigDecimal("100"));
        assertThat(r).isPresent();
        assertThat(r.get().gradeNumber()).isEqualTo(2);
        // 99.9999 is end of G1
        r = service.lookup(tenantId, structureId, new BigDecimal("99.9999"));
        assertThat(r).isPresent();
        assertThat(r.get().gradeNumber()).isEqualTo(1);
    }

    @Test
    void lookupReturnsEmptyWhenScoreBelowLowestBand() {
        // G1 starts at 0; negative is below.
        assertThat(service.lookup(tenantId, structureId, new BigDecimal("-1"))).isEmpty();
    }

    @Test
    void lookupReturnsEmptyWhenScoreAboveHighestBand() {
        assertThat(service.lookup(tenantId, structureId, new BigDecimal("301"))).isEmpty();
    }

    @Test
    void lookupReturnsEmptyForNullArguments() {
        assertThat(service.lookup(null, structureId, BigDecimal.TEN)).isEmpty();
        assertThat(service.lookup(tenantId, null, BigDecimal.TEN)).isEmpty();
        assertThat(service.lookup(tenantId, structureId, null)).isEmpty();
    }

    @Test
    void lookupReturnsEmptyWhenScoreInGap() {
        // Replace bands with gap between G1 and G2.
        StubBandRepo gapped = new StubBandRepo();
        UUID g1b = UUID.randomUUID(); UUID g2b = UUID.randomUUID();
        gapped.add(UUID.randomUUID(), tenantId, g1, structureId, "0",   "100");
        gapped.add(UUID.randomUUID(), tenantId, g2, structureId, "200", "300");
        GradeBandLookupService svc = new GradeBandLookupService(gapped, gradeRepo);
        assertThat(svc.lookup(tenantId, structureId, new BigDecimal("150"))).isEmpty();
    }

    @Test
    void lookupIsTenantScoped() {
        UUID otherTenant = UUID.randomUUID();
        assertThat(service.lookup(otherTenant, structureId, new BigDecimal("150"))).isEmpty();
    }

    // ---- Stub repositories ----

    static class StubBandRepo implements GradeBandRepository {
        final Map<String, List<GradeBandJpaEntity>> byStructure = new HashMap<>();

        void add(UUID id, UUID tenantId, UUID gradeId, UUID structureId,
                 String min, String max) {
            GradeBandJpaEntity b = new GradeBandJpaEntity(id, tenantId, gradeId, structureId,
                    new BigDecimal(min), new BigDecimal(max));
            byStructure.computeIfAbsent(key(tenantId, structureId), k -> new ArrayList<>()).add(b);
        }
        private String key(UUID t, UUID s) { return t + "/" + s; }

        @Override public Optional<GradeBandJpaEntity> findByTenantIdAndGradeId(UUID t, UUID g) {
            for (List<GradeBandJpaEntity> list : byStructure.values()) {
                for (GradeBandJpaEntity b : list) {
                    if (b.getTenantId().equals(t) && b.getGradeId().equals(g)) {
                        return Optional.of(b);
                    }
                }
            }
            return Optional.empty();
        }
        @Override public List<GradeBandJpaEntity> findAllByTenantIdAndGradeStructureIdOrderByMinScoreAsc(
                UUID tenantId, UUID gradeStructureId) {
            return byStructure.getOrDefault(key(tenantId, gradeStructureId), List.of());
        }
        @Override public void delete(GradeBandJpaEntity e) { /* unused */ }
        @Override public Optional<GradeBandJpaEntity> findByIdAndTenantId(UUID id, UUID t) {
            return Optional.empty();
        }
        @Override public org.springframework.data.domain.Page<GradeBandJpaEntity>
                findAllByTenantId(UUID t, org.springframework.data.domain.Pageable p) {
            return org.springframework.data.domain.Page.empty();
        }
        @Override public boolean existsByIdAndTenantId(UUID id, UUID t) { return false; }
        @Override public <S extends GradeBandJpaEntity> S save(S entity) { return entity; }
        @Override public long count() { return 0; }
    }

    static class StubGradeRepo implements GradeRepository {
        final Map<UUID, GradeJpaEntity> byId = new HashMap<>();
        void add(UUID id, UUID tenantId, int gradeNumber) {
            GradeJpaEntity g = new GradeJpaEntity(id, tenantId, UUID.randomUUID(),
                    gradeNumber, gradeNumber);
            byId.put(id, g);
        }
        @Override public Optional<GradeJpaEntity> findByIdAndTenantId(UUID id, UUID t) {
            GradeJpaEntity g = byId.get(id);
            return g != null && g.getTenantId().equals(t) ? Optional.of(g) : Optional.empty();
        }
        @Override public List<GradeJpaEntity>
                findAllByTenantIdAndGradeStructureIdOrderBySortOrderAsc(UUID t, UUID s) {
            return List.of();
        }
        @Override public Optional<GradeJpaEntity>
                findByTenantIdAndGradeStructureIdAndGradeNumber(UUID t, UUID s, int n) {
            return Optional.empty();
        }
        @Override public boolean
                existsByTenantIdAndGradeStructureIdAndGradeNumber(UUID t, UUID s, int n) {
            return false;
        }
        @Override public void delete(GradeJpaEntity e) { }
        @Override public org.springframework.data.domain.Page<GradeJpaEntity>
                findAllByTenantId(UUID t, org.springframework.data.domain.Pageable p) {
            return org.springframework.data.domain.Page.empty();
        }
        @Override public boolean existsByIdAndTenantId(UUID id, UUID t) { return false; }
        @Override public <S extends GradeJpaEntity> S save(S entity) { return entity; }
        @Override public long count() { return 0; }
    }
}
