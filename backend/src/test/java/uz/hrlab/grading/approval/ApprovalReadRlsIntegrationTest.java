package uz.hrlab.grading.approval;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.access.application.RoleCodes;
import uz.hrlab.grading.approval.api.ApprovalRequestResponse;
import uz.hrlab.grading.approval.application.ApprovalReadService;
import uz.hrlab.grading.approval.application.ListMyPendingApprovalsQuery;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.approval.domain.ApprovalRequestStatus;
import uz.hrlab.grading.approval.domain.ApprovalStepStatus;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestJpaEntity;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestRepository;
import uz.hrlab.grading.approval.infrastructure.ApprovalStepJpaEntity;
import uz.hrlab.grading.approval.infrastructure.ApprovalStepRepository;
import uz.hrlab.grading.common.api.PageResponse;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.methodology.domain.MethodologyStatus;
import uz.hrlab.grading.methodology.domain.MethodologyType;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatus;
import uz.hrlab.grading.methodology.domain.ScoringMode;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;
import uz.hrlab.grading.project.domain.ProjectStatus;
import uz.hrlab.grading.project.infrastructure.ProjectJpaEntity;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prod-fix regression pack (2026-06-22) — the approval DETAIL / INBOX / SEARCH
 * read paths must resolve BOTH the approval row AND the referenced entity label
 * under FORCE ROW LEVEL SECURITY, which only happens when the whole read runs
 * inside an aspect-advised {@code @Transactional} (so {@code RlsTenantSessionAspect}
 * binds {@code app.tenant_id}).
 *
 * <p><b>The bug:</b> {@code ApprovalController.getById} ran
 * {@code findByIdAndTenantId} + enrich DIRECTLY in the controller, OUTSIDE any
 * aspect-advised transaction, so under the deployed {@code grading_runtime} role
 * the RLS GUC was never bound → the {@code approval_requests} row was hidden
 * (404 "Рухсат йўқ") and the label-resolver entity reads were hidden (null label
 * → "Номсиз объект"). Local dev never reproduced it because dev/test runs as a
 * BYPASSRLS superuser.
 *
 * <p>This class proves the fix two ways:
 * <ol>
 *   <li><b>Functional</b> — through the REAL {@link ApprovalReadService} (a
 *       {@code @Transactional(readOnly = true)} bean the controller delegates to):
 *       detail returns the row WITH a non-null {@code entity_label_i18n}; the
 *       inbox card carries the resolved label; a cross-tenant detail read 404s.</li>
 *   <li><b>RLS-mechanism</b> — under the genuinely RLS-enforcing
 *       {@code grading_runtime} role (mirrors
 *       {@code ApprovalRlsRuntimeRoleIntegrationTest}): with NO GUC bound the
 *       approval row AND the referenced methodology read back as ZERO rows
 *       (exactly the prod failure that produced 404 + null label); with the GUC
 *       bound — what the aspect does inside the read transaction — BOTH read back.
 *       This is the load-bearing proof that the in-transaction GUC binding is
 *       what makes the functional fix work under FORCE RLS.</li>
 * </ol>
 */
@Tag("tenant-isolation")
@Tag("integration")
class ApprovalReadRlsIntegrationTest extends AbstractIntegrationTest {

    @Autowired ApprovalReadService readService;
    @Autowired ListMyPendingApprovalsQuery inboxQuery;
    @Autowired ApprovalRequestRepository requests;
    @Autowired ApprovalStepRepository steps;
    @Autowired ProjectRepository projects;
    @Autowired MethodologyRepository methodologies;
    @Autowired MethodologyVersionRepository methodologyVersions;
    @Autowired JdbcTemplate adminJdbc; // bootstrap superuser — bypasses RLS, setup only

    private final List<HikariDataSource> openSources = new ArrayList<>();

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
        openSources.forEach(HikariDataSource::close);
        openSources.clear();
    }

    // ================================================================== //
    // 1) Functional — through the real read service (the controller's path) //
    // ================================================================== //

    /**
     * DETAIL: with active tenant A, the detail read returns 200-equivalent with a
     * NON-null entity_label_i18n — proving BOTH the approval row AND the entity
     * label resolve when the read runs inside the read service's transaction.
     * Before the fix this 404'd / returned a null label under the RLS role.
     */
    @Test
    void detailReturnsRowAndResolvedLabel() {
        Seed a = seedApproval(newSeededTenantId());

        ApprovalRequestResponse detail = withTenant(a.tenantId, () ->
                readService.getById(a.tenantId, a.requestId));

        assertThat(detail.id()).isEqualTo(a.requestId);
        assertThat(detail.entityType()).isEqualTo(ApprovalEntityType.METHODOLOGY_VERSION.name());
        assertThat(detail.entityLabelI18n())
                .as("the referenced methodology_version label must resolve under RLS "
                        + "(non-null → no 'Номсиз объект')")
                .isNotNull()
                .containsKey("ru-RU");
        assertThat(detail.entityLabelI18n().get("ru-RU"))
                .as("label = '<methodology name> v<n>'")
                .contains(a.methodologyName)
                .contains("v1");
    }

    /**
     * INBOX: the approver's pending card is returned WITH a resolved label
     * (enrichInbox runs the resolution inside a read transaction).
     */
    @Test
    void inboxCardCarriesResolvedLabel() {
        Seed a = seedApproval(newSeededTenantId());

        List<ApprovalRequestResponse> inbox = withApprover(a.tenantId, a.approverUserId, () ->
                readService.enrichInbox(a.tenantId, inboxQuery.list()));

        assertThat(inbox).extracting(ApprovalRequestResponse::id).contains(a.requestId);
        ApprovalRequestResponse card = inbox.stream()
                .filter(r -> r.id().equals(a.requestId)).findFirst().orElseThrow();
        assertThat(card.entityLabelI18n())
                .as("inbox card label must resolve (not 'Номсиз объект')")
                .isNotNull()
                .containsKey("ru-RU");
    }

    /** SEARCH (project list) returns the row enriched with its label. */
    @Test
    void searchReturnsRowWithResolvedLabel() {
        Seed a = seedApproval(newSeededTenantId());

        PageResponse<ApprovalRequestResponse> page = withTenant(a.tenantId, () ->
                readService.search(a.tenantId, a.projectId, null,
                        ApprovalRequestStatus.PENDING, PageRequest.of(0, 50)));

        assertThat(page.items()).extracting(ApprovalRequestResponse::id).contains(a.requestId);
        ApprovalRequestResponse row = page.items().stream()
                .filter(r -> r.id().equals(a.requestId)).findFirst().orElseThrow();
        assertThat(row.entityLabelI18n()).isNotNull().containsKey("ru-RU");
    }

    /**
     * CROSS-TENANT: with active tenant B, the tenant-A detail read 404s
     * (TenantAccessDeniedException) — isolation intact. The fix never widens
     * visibility: the find is still {@code findByIdAndTenantId}-scoped.
     */
    @Test
    void crossTenantDetailIs404() {
        Seed a = seedApproval(newSeededTenantId());
        UUID tenantB = newSeededTenantId();

        withTenant(tenantB, () -> {
            assertThatThrownBy(() -> readService.getById(tenantB, a.requestId))
                    .isInstanceOf(TenantAccessDeniedException.class);
            return null;
        });
    }

    // ================================================================== //
    // 2) RLS-mechanism — under the genuinely RLS-enforcing runtime role   //
    // ================================================================== //

    /**
     * The load-bearing RLS proof. Under {@code grading_runtime} (NON-superuser,
     * FORCE RLS — the deployed role), a read of the approval row AND its
     * referenced methodology:
     * <ul>
     *   <li>with NO {@code app.tenant_id} bound → ZERO rows for BOTH (this is the
     *       exact prod failure: the controller ran outside a tx so the GUC was
     *       unset → 404 + null label);</li>
     *   <li>with {@code app.tenant_id} bound (what the aspect does inside the
     *       read service's transaction) → BOTH rows are visible.</li>
     * </ul>
     */
    @Test
    void runtimeRoleNeedsBoundGucForBothApprovalRowAndReferencedEntity() {
        Seed a = seedApproval(newSeededTenantId());

        JdbcTemplate runtime = connectAs("grading_runtime", "grading_runtime_pwd");

        runtime.execute((Connection conn) -> {
            // (a) Unbound GUC == the prod controller path (no aspect tx) → zero rows.
            assertThat(approvalRowVisible(conn, a.requestId))
                    .as("unbound app.tenant_id hides the approval_requests row (the 404)")
                    .isFalse();
            assertThat(methodologyVersionVisible(conn, a.methodologyVersionId))
                    .as("unbound app.tenant_id hides the referenced methodology_version "
                            + "(the null label → 'Номсиз объект')")
                    .isFalse();

            // (b) Bound GUC == what RlsTenantSessionAspect does inside the read tx.
            try (Statement st = conn.createStatement()) {
                st.execute("SET app.tenant_id = '" + a.tenantId + "'");
            }
            assertThat(approvalRowVisible(conn, a.requestId))
                    .as("bound app.tenant_id makes the approval row visible (200)")
                    .isTrue();
            assertThat(methodologyVersionVisible(conn, a.methodologyVersionId))
                    .as("bound app.tenant_id makes the referenced entity visible (label resolves)")
                    .isTrue();
            return null;
        });
    }

    // ----------------------------------------------------------------- //
    // Fixtures + helpers                                                 //
    // ----------------------------------------------------------------- //

    /**
     * Seeds a project, a methodology + its v1 (the labelled entity) and a PENDING
     * METHODOLOGY_VERSION approval request with one PENDING step assigned to a
     * fresh approver — all via the JPA repos as the (BYPASSRLS) test superuser.
     */
    private Seed seedApproval(UUID tenantId) {
        ProjectJpaEntity project = projects.save(new ProjectJpaEntity(
                UUID.randomUUID(), tenantId,
                "PRJ-" + UUID.randomUUID().toString().substring(0, 8),
                Map.of("ru-RU", "Project"), null, ProjectStatus.ACTIVE, null, null, null));

        String methodologyName = "Грейдинг " + UUID.randomUUID().toString().substring(0, 4);
        MethodologyJpaEntity m = new MethodologyJpaEntity(
                UUID.randomUUID(), tenantId, project.getId(),
                "M-" + UUID.randomUUID().toString().substring(0, 8),
                MethodologyType.CUSTOM, MethodologyStatus.ACTIVE);
        m.setNameI18n(Map.of("ru-RU", methodologyName, "en-US", methodologyName));
        methodologies.save(m);

        // DRAFT: the methodology_versions insert trigger requires the initial
        // status to be DRAFT. The label resolver only reads name + version number,
        // so the status is irrelevant to what this test asserts.
        MethodologyVersionJpaEntity v = methodologyVersions.save(new MethodologyVersionJpaEntity(
                UUID.randomUUID(), tenantId, m.getId(), 1,
                MethodologyVersionStatus.DRAFT, ScoringMode.DIRECT_POINTS,
                new BigDecimal("1000"), null));

        UUID approverUser = seedUser(UUID.randomUUID());
        ApprovalRequestJpaEntity req = requests.save(new ApprovalRequestJpaEntity(
                UUID.randomUUID(), tenantId, project.getId(),
                ApprovalEntityType.METHODOLOGY_VERSION, v.getId(), UUID.randomUUID(),
                OffsetDateTime.now(), ApprovalRequestStatus.PENDING, Map.of()));
        steps.save(new ApprovalStepJpaEntity(
                UUID.randomUUID(), tenantId, req.getId(), 1,
                approverUser, PermissionCodes.METHODOLOGY_APPROVE, ApprovalStepStatus.PENDING));

        return new Seed(tenantId, project.getId(), req.getId(), v.getId(),
                methodologyName, approverUser);
    }

    /** Run inside an active manager TenantContext for {@code tenantId}. */
    private <T> T withTenant(UUID tenantId, Supplier<T> body) {
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenantId, Set.of(),
                Set.of(RoleCodes.HRLAB_SUPER_ADMIN),
                Set.of(PermissionCodes.APPROVAL_REQUEST_CREATE,
                        PermissionCodes.APPROVAL_REQUEST_DECIDE,
                        PermissionCodes.METHODOLOGY_READ,
                        PermissionCodes.METHODOLOGY_APPROVE),
                Set.of(), false, "ru-RU"));
        try {
            return body.get();
        } finally {
            TenantContextHolder.clear();
        }
    }

    /** Run inside an active TenantContext for a specific approver user. */
    private <T> T withApprover(UUID tenantId, UUID userId, Supplier<T> body) {
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(),
                Set.of(RoleCodes.HRLAB_SUPER_ADMIN),
                Set.of(PermissionCodes.APPROVAL_REQUEST_DECIDE,
                        PermissionCodes.METHODOLOGY_READ,
                        PermissionCodes.METHODOLOGY_APPROVE),
                Set.of(), false, "ru-RU"));
        try {
            return body.get();
        } finally {
            TenantContextHolder.clear();
        }
    }

    private static boolean approvalRowVisible(Connection conn, UUID requestId) throws SQLException {
        return rowExists(conn, "SELECT 1 FROM public.approval_requests WHERE id = '"
                + requestId + "'");
    }

    private static boolean methodologyVersionVisible(Connection conn, UUID versionId)
            throws SQLException {
        return rowExists(conn, "SELECT 1 FROM public.methodology_versions WHERE id = '"
                + versionId + "'");
    }

    private static boolean rowExists(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next();
        }
    }

    private JdbcTemplate connectAs(String username, String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(POSTGRES.getJdbcUrl());
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setMaximumPoolSize(2);
        ds.setPoolName("approval-read-rls-" + username);
        openSources.add(ds);
        return new JdbcTemplate(ds);
    }

    private record Seed(UUID tenantId, UUID projectId, UUID requestId, UUID methodologyVersionId,
                        String methodologyName, UUID approverUserId) {
    }
}
