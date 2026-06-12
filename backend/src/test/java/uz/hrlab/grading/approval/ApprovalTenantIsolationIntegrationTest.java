package uz.hrlab.grading.approval;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.access.application.RoleCodes;
import uz.hrlab.grading.approval.application.ApprovalQueries;
import uz.hrlab.grading.approval.application.FindApprovalRequestByEntityQuery;
import uz.hrlab.grading.approval.application.ListMyPendingApprovalsQuery;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.approval.domain.ApprovalRequest;
import uz.hrlab.grading.approval.domain.ApprovalRequestStatus;
import uz.hrlab.grading.approval.domain.ApprovalStepStatus;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestJpaEntity;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestRepository;
import uz.hrlab.grading.approval.infrastructure.ApprovalStepJpaEntity;
import uz.hrlab.grading.approval.infrastructure.ApprovalStepRepository;
import uz.hrlab.grading.project.domain.ProjectStatus;
import uz.hrlab.grading.project.infrastructure.ProjectJpaEntity;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TI-Regression pack for the approvals read/list surface (BE-5, 2026-06-12).
 *
 * <p>Exercises the REAL query objects + real repositories (NOT a mock inbox)
 * against a two-tenant dataset to prove the cross-tenant metadata leak behind
 * the production inbox bug cannot recur:
 * <ul>
 *   <li>{@link ListMyPendingApprovalsQuery#list()} (inbox + badge source) for
 *       an active tenant A returns ONLY tenant-A PENDING requests — a tenant-B
 *       request whose PENDING step matches the SAME approver user is NEVER
 *       returned;</li>
 *   <li>{@link ApprovalRequestRepository#search} (project list endpoint) scoped
 *       to A never surfaces a B row;</li>
 *   <li>{@link FindApprovalRequestByEntityQuery#findAll} (by-entity list) scoped
 *       to A never surfaces a B row even for an identical (entityType, entityId).</li>
 * </ul>
 *
 * <p>Reuses the established {@code findByIdAndTenantId} / two-tenant fixture
 * pattern from {@code Phase2TenantIsolationIntegrationTest} — no new RLS harness,
 * no duplicated setup.
 */
@Tag("tenant-isolation")
@Tag("integration")
class ApprovalTenantIsolationIntegrationTest extends AbstractIntegrationTest {

    @Autowired ListMyPendingApprovalsQuery inboxQuery;
    @Autowired FindApprovalRequestByEntityQuery findByEntity;
    @Autowired ApprovalRequestRepository requests;
    @Autowired ApprovalStepRepository steps;
    @Autowired ApprovalQueries queries;
    @Autowired ProjectRepository projects;

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    /**
     * The same approver user holds a PENDING step in BOTH tenants. With active
     * tenant A, the inbox must return ONLY the tenant-A request — the tenant-B
     * request (whose step matches the user's permission) must NEVER appear.
     * This is the exact production leak (10 cross-tenant PENDING rows).
     */
    @Test
    void inboxReturnsOnlyActiveTenantPendingRequests() {
        UUID tenantA = newSeededTenantId();
        UUID tenantB = newSeededTenantId();
        UUID approverUser = UUID.randomUUID();

        UUID entityA = UUID.randomUUID();
        UUID entityB = UUID.randomUUID();
        UUID requestA = seedPendingRequestWithPendingStep(tenantA, entityA, approverUser);
        UUID requestB = seedPendingRequestWithPendingStep(tenantB, entityB, approverUser);

        // Active context = tenant A, the same approver user.
        withApprover(tenantA, approverUser, () -> {
            List<ApprovalRequest> inbox = inboxQuery.list();
            assertThat(inbox)
                    .as("tenant-A inbox must contain ONLY tenant-A pending requests")
                    .extracting(ApprovalRequest::id)
                    .contains(requestA)
                    .doesNotContain(requestB);
            assertThat(inbox)
                    .allSatisfy(r -> assertThat(r.tenantId()).isEqualTo(tenantA));
        });

        // Symmetric: active context = tenant B sees ONLY B's request.
        withApprover(tenantB, approverUser, () -> {
            List<ApprovalRequest> inbox = inboxQuery.list();
            assertThat(inbox)
                    .extracting(ApprovalRequest::id)
                    .contains(requestB)
                    .doesNotContain(requestA);
        });
    }

    /**
     * The project-list endpoint backs onto {@link ApprovalRequestRepository#search}.
     * A search scoped to tenant A must never return a tenant-B request, even when
     * no project filter narrows the result.
     */
    @Test
    void searchReturnsOnlyActiveTenantRequests() {
        UUID tenantA = newSeededTenantId();
        UUID tenantB = newSeededTenantId();
        UUID approverUser = UUID.randomUUID();

        UUID requestA = seedPendingRequestWithPendingStep(tenantA, UUID.randomUUID(), approverUser);
        UUID requestB = seedPendingRequestWithPendingStep(tenantB, UUID.randomUUID(), approverUser);

        Page<ApprovalRequestJpaEntity> pageA = requests.search(
                tenantA, null, null, ApprovalRequestStatus.PENDING, PageRequest.of(0, 50));
        assertThat(pageA.getContent())
                .extracting(ApprovalRequestJpaEntity::getId)
                .contains(requestA)
                .doesNotContain(requestB);

        Page<ApprovalRequestJpaEntity> pageB = requests.search(
                tenantB, null, null, ApprovalRequestStatus.PENDING, PageRequest.of(0, 50));
        assertThat(pageB.getContent())
                .extracting(ApprovalRequestJpaEntity::getId)
                .contains(requestB)
                .doesNotContain(requestA);
    }

    /**
     * The by-entity list endpoint backs onto
     * {@link FindApprovalRequestByEntityQuery#findAll} →
     * {@link ApprovalQueries#findActiveForEntity}. Even when tenant A and tenant
     * B happen to share the SAME (entityType, entityId) tuple, an A-scoped lookup
     * must return ONLY A's request.
     */
    @Test
    void findByEntityReturnsOnlyActiveTenantRequestsForSharedEntityId() {
        UUID tenantA = newSeededTenantId();
        UUID tenantB = newSeededTenantId();
        UUID approverUser = UUID.randomUUID();

        // Deliberately identical entity id across tenants — only the tenant_id
        // predicate keeps them apart.
        UUID sharedEntityId = UUID.randomUUID();
        UUID requestA = seedPendingRequestWithPendingStep(tenantA, sharedEntityId, approverUser);
        UUID requestB = seedPendingRequestWithPendingStep(tenantB, sharedEntityId, approverUser);

        withApprover(tenantA, approverUser, () -> {
            List<ApprovalRequest> rows =
                    findByEntity.findAll(ApprovalEntityType.JOB_PROFILE, sharedEntityId);
            assertThat(rows)
                    .extracting(ApprovalRequest::id)
                    .contains(requestA)
                    .doesNotContain(requestB);
        });

        withApprover(tenantB, approverUser, () -> {
            List<ApprovalRequest> rows =
                    findByEntity.findAll(ApprovalEntityType.JOB_PROFILE, sharedEntityId);
            assertThat(rows)
                    .extracting(ApprovalRequest::id)
                    .contains(requestB)
                    .doesNotContain(requestA);
        });
    }

    // ------------------------------------------------------------------ //
    // Fixtures                                                            //
    // ------------------------------------------------------------------ //

    /**
     * Seeds a project (FK target), a PENDING approval_request and a single
     * PENDING approval_step assigned to {@code approverUser}. Returns the
     * request id.
     */
    private UUID seedPendingRequestWithPendingStep(UUID tenantId, UUID entityId, UUID approverUser) {
        ProjectJpaEntity project = projects.save(new ProjectJpaEntity(
                UUID.randomUUID(), tenantId, "PRJ-" + UUID.randomUUID().toString().substring(0, 8),
                Map.of("ru-RU", "Project"), null, ProjectStatus.ACTIVE, null, null, null));

        ApprovalRequestJpaEntity request = requests.save(new ApprovalRequestJpaEntity(
                UUID.randomUUID(), tenantId, project.getId(),
                ApprovalEntityType.JOB_PROFILE, entityId, UUID.randomUUID(),
                OffsetDateTime.now(), ApprovalRequestStatus.PENDING, Map.of()));

        steps.save(new ApprovalStepJpaEntity(
                UUID.randomUUID(), tenantId, request.getId(), 1,
                approverUser, PermissionCodes.JOB_PROFILE_APPROVE, ApprovalStepStatus.PENDING));

        return request.getId();
    }

    /** Run {@code body} inside an active TenantContext for (tenant, approver). */
    private void withApprover(UUID tenantId, UUID userId, Runnable body) {
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(), Set.of(RoleCodes.HRLAB_SUPER_ADMIN),
                Set.of(PermissionCodes.APPROVAL_REQUEST_DECIDE,
                        PermissionCodes.JOB_PROFILE_APPROVE),
                Set.of(), false, "ru-RU"));
        try {
            body.run();
        } finally {
            TenantContextHolder.clear();
        }
    }
}
