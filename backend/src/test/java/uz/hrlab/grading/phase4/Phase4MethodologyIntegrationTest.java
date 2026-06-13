package uz.hrlab.grading.phase4;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.hrlab.grading.AbstractIntegrationTest;
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

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Repository-level tenant-isolation + DB-trigger immutability proofs for Phase 4
 * methodology aggregate. Requires Docker (Testcontainers); skipped without it.
 */
@Tag("tenant-isolation")
@Tag("integration")
class Phase4MethodologyIntegrationTest extends AbstractIntegrationTest {

    @Autowired MethodologyRepository methodologies;
    @Autowired MethodologyVersionRepository versions;
    @Autowired FactorRepository factors;
    @Autowired FactorLevelRepository levels;

    @Test
    void methodologyFromTenantBIsInvisibleToTenantA() {
        UUID tenantA = newSeededTenantId();
        UUID tenantB = newSeededTenantId();
        MethodologyJpaEntity mB = methodologies.save(newMethodology(tenantB, "M-PB"));
        Optional<MethodologyJpaEntity> probe =
                methodologies.findByIdAndTenantId(mB.getId(), tenantA);
        assertThat(probe).isEmpty();
    }

    @Test
    void versionFromTenantBIsInvisibleToTenantA() {
        UUID tenantA = newSeededTenantId();
        UUID tenantB = newSeededTenantId();
        MethodologyJpaEntity mB = methodologies.save(newMethodology(tenantB, "M-VB"));
        MethodologyVersionJpaEntity vB = versions.save(newDraftVersion(tenantB, mB.getId(), 1));
        Optional<MethodologyVersionJpaEntity> probe =
                versions.findByIdAndTenantId(vB.getId(), tenantA);
        assertThat(probe).isEmpty();
    }

    @Test
    void factorOnLockedVersionCannotBeInsertedDbLevel() {
        UUID tenant = newSeededTenantId();
        MethodologyJpaEntity m = methodologies.save(newMethodology(tenant, "M-LOCK"));
        // Flip to LOCKED via two transitions to satisfy trg_mv_status_immutability:
        // DRAFT -> APPROVED -> LOCKED. Each save() is its own tx, so capture the
        // merged copy to carry the incremented @Version forward (otherwise the
        // 2nd update reuses a stale version → optimistic-lock failure).
        MethodologyVersionJpaEntity draft = versions.save(newDraftVersion(tenant, m.getId(), 1));
        draft.setStatus(MethodologyVersionStatus.APPROVED);
        MethodologyVersionJpaEntity approved = versions.save(draft);
        approved.setStatus(MethodologyVersionStatus.LOCKED);
        MethodologyVersionJpaEntity locked = versions.save(approved);

        FactorJpaEntity newFactor = newFactor(tenant, locked.getId(), "F-X");
        assertThatThrownBy(() -> factors.save(newFactor))
                .hasMessageContaining("METHODOLOGY_VERSION_LOCKED");
    }

    @Test
    void lockedVersionCannotRegressToApproved() {
        UUID tenant = newSeededTenantId();
        MethodologyJpaEntity m = methodologies.save(newMethodology(tenant, "M-REG"));
        // Capture each merged copy so the @Version stays in sync across the
        // separate save() transactions (avoids a stale-version optimistic lock).
        MethodologyVersionJpaEntity draft = versions.save(newDraftVersion(tenant, m.getId(), 1));
        draft.setStatus(MethodologyVersionStatus.APPROVED);
        MethodologyVersionJpaEntity approved = versions.save(draft);
        approved.setStatus(MethodologyVersionStatus.LOCKED);
        MethodologyVersionJpaEntity locked = versions.save(approved);
        locked.setStatus(MethodologyVersionStatus.APPROVED); // attempt regression
        assertThatThrownBy(() -> versions.save(locked))
                .hasMessageContaining("METHODOLOGY_VERSION_LOCKED");
    }

    @Test
    void factorLevelOnApprovedVersionRejectedDbLevel() {
        UUID tenant = newSeededTenantId();
        MethodologyJpaEntity m = methodologies.save(newMethodology(tenant, "M-APP"));
        MethodologyVersionJpaEntity v = versions.save(newDraftVersion(tenant, m.getId(), 1));
        FactorJpaEntity f = factors.save(newFactor(tenant, v.getId(), "F-1"));
        v.setStatus(MethodologyVersionStatus.APPROVED);
        versions.save(v);

        FactorLevelJpaEntity l = newLevel(tenant, f.getId(), "L-X", 1);
        // The version is APPROVED (not LOCKED/ARCHIVED): changelog 042's carve-out
        // raises the distinct APPROVED-protected message instead of the LOCKED one.
        assertThatThrownBy(() -> levels.save(l))
                .hasMessageContaining("METHODOLOGY_VERSION_APPROVED_PROTECTED");
    }

    @Test
    void uniqueFactorCodePerVersion() {
        UUID tenant = newSeededTenantId();
        MethodologyJpaEntity m = methodologies.save(newMethodology(tenant, "M-UC"));
        MethodologyVersionJpaEntity v = versions.save(newDraftVersion(tenant, m.getId(), 1));
        factors.save(newFactor(tenant, v.getId(), "DUP"));

        assertThatThrownBy(() -> factors.save(newFactor(tenant, v.getId(), "DUP")))
                .hasMessageContaining("uq_factors_version_code");
    }

    private MethodologyJpaEntity newMethodology(UUID tenantId, String code) {
        MethodologyJpaEntity m = new MethodologyJpaEntity(
                UUID.randomUUID(), tenantId, null, code,
                MethodologyType.CUSTOM, MethodologyStatus.ACTIVE);
        m.setNameI18n(Map.of("ru-RU", code));
        return m;
    }

    private MethodologyVersionJpaEntity newDraftVersion(UUID tenantId, UUID methodologyId,
                                                       int versionNumber) {
        return new MethodologyVersionJpaEntity(
                UUID.randomUUID(), tenantId, methodologyId, versionNumber,
                MethodologyVersionStatus.DRAFT, ScoringMode.DIRECT_POINTS,
                new BigDecimal("1000"), null);
    }

    private FactorJpaEntity newFactor(UUID tenantId, UUID versionId, String code) {
        FactorJpaEntity f = new FactorJpaEntity(
                UUID.randomUUID(), tenantId, versionId, code,
                new BigDecimal("100"), new BigDecimal("100"),
                deterministicSort(code), true);
        f.setNameI18n(Map.of("ru-RU", code));
        return f;
    }

    private static int deterministicSort(String code) {
        return Math.abs(code.hashCode()) % 100_000 + 1;
    }

    private FactorLevelJpaEntity newLevel(UUID tenantId, UUID factorId, String code, int order) {
        FactorLevelJpaEntity l = new FactorLevelJpaEntity(
                UUID.randomUUID(), tenantId, factorId, code, order,
                new BigDecimal("10"), null);
        l.setLabelI18n(Map.of("ru-RU", code));
        return l;
    }
}
