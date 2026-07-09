package uz.hrlab.grading.approval;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.approval.domain.ApprovalRequestStatus;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestJpaEntity;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestRepository;
import uz.hrlab.grading.project.domain.ProjectStatus;
import uz.hrlab.grading.project.infrastructure.ProjectJpaEntity;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA-054 — DB-level regression lock for migration
 * {@code tenant-schema/047-approval-requests-nullable-requested-by.yaml}.
 *
 * <p>021 created {@code approval_requests.requested_by} as {@code NOT NULL}, which
 * is correct for the human-initiated flow (an evaluator/owner submits →
 * {@code requested_by} = that user). But the MVP2 panel reconciliation
 * ({@code PanelApprovalReconciliationRunner} / {@code BackfillPanelApprovalsMigration})
 * opens the missing {@code EVALUATION_PANEL} CEO approvals under a
 * permission-less SYSTEM context whose {@code userId} is {@code null}
 * ({@code CreateApprovalRequestUseCase#createSystem} →
 * {@code requested_by = ctx.userId() = null}). That INSERT used to hit a 23502
 * not-null violation and abort the whole reconcile run. 047 relaxed the column
 * to nullable — this test proves that relaxation holds against a REAL Postgres
 * (Testcontainers), through the exact {@link ApprovalRequestRepository} the
 * production use case saves through, not a mock.
 *
 * <p>Two proofs:
 * <ol>
 *   <li>a {@code requested_by = NULL} row round-trips through the repository —
 *       insert, flush to Postgres, clear the Hibernate first-level cache, then
 *       re-read — and is confirmed NULL both via JPA AND via a raw-SQL check
 *       (belt-and-braces against an ORM-level default masking a real NULL);</li>
 *   <li>the pre-existing human-initiated path (non-null {@code requested_by})
 *       still persists unaffected — 047 only WIDENS the constraint, it must not
 *       silently change the populated path.</li>
 * </ol>
 *
 * <p>{@code entity_id} is intentionally a bare random UUID: {@code
 * approval_requests} has no FK on {@code (entity_type, entity_id)} (it is
 * polymorphic across METHODOLOGY_VERSION / EVALUATION / JOB_PROFILE /
 * GRADE_STRUCTURE / EVALUATION_PANEL — see {@code tenant-021} /
 * {@code tenant-044}), so no methodology/panel chain needs seeding here.
 */
@Tag("integration")
@Tag("db-constraints")
class ApprovalRequestNullableRequestedByIntegrationTest extends AbstractIntegrationTest {

    @Autowired ApprovalRequestRepository requests;
    @Autowired ProjectRepository projects;
    @PersistenceContext EntityManager em;

    @Test
    @Transactional
    void requestedByNullPersistsThroughTheRealRepositoryAndRoundTripsAsNullFromPostgres() {
        UUID tenantId = newSeededTenantId();
        UUID projectId = seedProject(tenantId);
        UUID panelId = UUID.randomUUID(); // no FK on entity_id — any UUID is valid.

        ApprovalRequestJpaEntity saved = requests.save(new ApprovalRequestJpaEntity(
                UUID.randomUUID(), tenantId, projectId,
                ApprovalEntityType.EVALUATION_PANEL, panelId,
                null, // requested_by = NULL — the SYSTEM-opened case migration 047 fixed.
                OffsetDateTime.now(), ApprovalRequestStatus.PENDING, Map.of()));

        // Force a real round-trip: flush the INSERT to Postgres, then clear the
        // persistence context so the subsequent read cannot be served from the
        // Hibernate first-level cache — it MUST come back off the wire.
        em.flush();
        em.clear();

        Optional<ApprovalRequestJpaEntity> reloaded =
                requests.findByIdAndTenantId(saved.getId(), tenantId);

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getRequestedBy())
                .as("requested_by must persist as NULL through JPA — migration 047 relaxed the "
                        + "NOT NULL constraint specifically for SYSTEM-opened approvals "
                        + "(reconciliation / backfill, ctx.userId() == null)")
                .isNull();

        // Belt-and-braces: confirm the column is ACTUALLY NULL in Postgres itself
        // (not merely an unset Java field), via raw JDBC — same proof style
        // Phase2ConstraintsTest uses for other column-level DB guarantees.
        Boolean isNullInDb = jdbcTemplate.queryForObject(
                "SELECT requested_by IS NULL FROM approval_requests WHERE id = ?",
                Boolean.class, saved.getId());
        assertThat(isNullInDb)
                .as("raw SQL confirms requested_by is genuinely NULL at the Postgres column level")
                .isTrue();
    }

    /**
     * Sanity companion — the pre-047 human-initiated flow (requested_by SET)
     * must keep working unchanged; the migration only widens the constraint.
     */
    @Test
    @Transactional
    void requestedByNonNullStillPersistsUnaffectedByTheRelaxedConstraint() {
        UUID tenantId = newSeededTenantId();
        UUID projectId = seedProject(tenantId);
        UUID requesterId = seedUser(UUID.randomUUID());
        UUID entityId = UUID.randomUUID();

        ApprovalRequestJpaEntity saved = requests.save(new ApprovalRequestJpaEntity(
                UUID.randomUUID(), tenantId, projectId,
                ApprovalEntityType.EVALUATION_PANEL, entityId,
                requesterId, OffsetDateTime.now(), ApprovalRequestStatus.PENDING, Map.of()));

        em.flush();
        em.clear();

        Optional<ApprovalRequestJpaEntity> reloaded =
                requests.findByIdAndTenantId(saved.getId(), tenantId);

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getRequestedBy()).isEqualTo(requesterId);
    }

    private UUID seedProject(UUID tenantId) {
        ProjectJpaEntity project = projects.save(new ProjectJpaEntity(
                UUID.randomUUID(), tenantId,
                "QA054-" + UUID.randomUUID().toString().substring(0, 8),
                Map.of("ru-RU", "QA-054 project"), null, ProjectStatus.ACTIVE, null, null, null));
        return project.getId();
    }
}
