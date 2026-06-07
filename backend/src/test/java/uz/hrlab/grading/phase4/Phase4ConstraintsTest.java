package uz.hrlab.grading.phase4;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.methodology.domain.MethodologyStatus;
import uz.hrlab.grading.methodology.domain.MethodologyType;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatus;
import uz.hrlab.grading.methodology.domain.ScoringMode;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F-403 — verifies the {@code prevent_mv_status_regression} trigger covers
 * both INSERT and UPDATE timing.
 *
 * <p>Phase 4 baseline (010-create-methodologies.yaml) bound the trigger to
 * {@code BEFORE UPDATE OF status} only. Phase 4 remediation
 * (014-phase4-constraints.yaml) re-binds to
 * {@code BEFORE INSERT OR UPDATE OF status} and branches on {@code TG_OP} so
 * a direct INSERT with non-DRAFT status is rejected at the storage layer.
 *
 * <p>We use raw SQL via JdbcTemplate for the INSERT paths to bypass the
 * application-layer use cases (which already enforce DRAFT-at-creation). The
 * DB trigger is defence-in-depth — these tests prove it would block a rogue
 * direct write.
 *
 * <p>Testcontainers-gated via {@link AbstractIntegrationTest}.
 */
@Tag("tenant-isolation")
@Tag("integration")
class Phase4ConstraintsTest extends AbstractIntegrationTest {

    @Autowired MethodologyRepository methodologies;
    @Autowired MethodologyVersionRepository versions;

    // ============================================================ F-403 INSERT

    @Test
    void directInsertWithApprovedStatusIsRejectedByTrigger() {
        UUID tenant = newSeededTenantId();
        MethodologyJpaEntity m = methodologies.save(newMethodology(tenant, "M-INS-APP"));

        UUID newVersionId = UUID.randomUUID();
        assertThatThrownBy(() -> insertVersionRaw(newVersionId, tenant, m.getId(),
                1, "APPROVED", "DIRECT_POINTS"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("METHODOLOGY_VERSION_INVALID_INITIAL_STATUS");
    }

    @Test
    void directInsertWithLockedStatusIsRejectedByTrigger() {
        UUID tenant = newSeededTenantId();
        MethodologyJpaEntity m = methodologies.save(newMethodology(tenant, "M-INS-LCK"));

        UUID newVersionId = UUID.randomUUID();
        assertThatThrownBy(() -> insertVersionRaw(newVersionId, tenant, m.getId(),
                1, "LOCKED", "DIRECT_POINTS"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("METHODOLOGY_VERSION_INVALID_INITIAL_STATUS");
    }

    @Test
    void directInsertWithArchivedStatusIsRejectedByTrigger() {
        UUID tenant = newSeededTenantId();
        MethodologyJpaEntity m = methodologies.save(newMethodology(tenant, "M-INS-ARC"));

        UUID newVersionId = UUID.randomUUID();
        assertThatThrownBy(() -> insertVersionRaw(newVersionId, tenant, m.getId(),
                1, "ARCHIVED", "DIRECT_POINTS"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("METHODOLOGY_VERSION_INVALID_INITIAL_STATUS");
    }

    @Test
    void directInsertWithDraftStatusSucceeds() {
        UUID tenant = newSeededTenantId();
        MethodologyJpaEntity m = methodologies.save(newMethodology(tenant, "M-INS-OK"));

        UUID newVersionId = UUID.randomUUID();
        assertThatCode(() -> insertVersionRaw(newVersionId, tenant, m.getId(),
                1, "DRAFT", "DIRECT_POINTS"))
                .doesNotThrowAnyException();

        assertThat(versions.findByIdAndTenantId(newVersionId, tenant)).isPresent();
    }

    // ============================================================ UPDATE path
    // (preserved Phase 4 baseline behaviour)

    @Test
    void lockedToApprovedUpdateStillRejected() {
        UUID tenant = newSeededTenantId();
        MethodologyJpaEntity m = methodologies.save(newMethodology(tenant, "M-UPD-REG"));

        // Each save() runs in its own tx (no @Transactional here), so the
        // entity is detached between calls and save() merges, returning a NEW
        // managed copy with the incremented @Version. Track that copy or the
        // next update reuses a stale version → optimistic-lock failure.
        MethodologyVersionJpaEntity draft = versions.save(newDraftVersion(tenant, m.getId(), 1));
        draft.setStatus(MethodologyVersionStatus.APPROVED);
        MethodologyVersionJpaEntity approved = versions.save(draft);
        approved.setStatus(MethodologyVersionStatus.LOCKED);
        MethodologyVersionJpaEntity locked = versions.save(approved);

        locked.setStatus(MethodologyVersionStatus.APPROVED); // attempt regression
        assertThatThrownBy(() -> versions.save(locked))
                .hasMessageContaining("METHODOLOGY_VERSION_LOCKED");
    }

    // ============================================================ helpers

    /**
     * Raw INSERT bypassing the JPA layer — we need to exercise the trigger's
     * BEFORE INSERT branch independently of any service-layer validation.
     */
    private void insertVersionRaw(UUID id, UUID tenantId, UUID methodologyId,
                                  int versionNumber, String status,
                                  String scoringMode) {
        jdbcTemplate.update(
                "INSERT INTO methodology_versions (id, tenant_id, methodology_id, "
                        + "version_number, status, scoring_mode, target_total_points, "
                        + "previous_version_id, version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, NULL, NULL, 0)",
                id, tenantId, methodologyId, versionNumber, status, scoringMode);
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
                new java.math.BigDecimal("1000"), null);
    }
}
