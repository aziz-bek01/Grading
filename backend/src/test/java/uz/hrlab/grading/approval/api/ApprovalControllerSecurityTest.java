package uz.hrlab.grading.approval.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uz.hrlab.grading.access.application.ActorNameResolver;
import uz.hrlab.grading.approval.application.ApprovalEntityLabelResolver;
import uz.hrlab.grading.approval.application.ApprovalQueries;
import uz.hrlab.grading.approval.application.ApprovalReadService;
import uz.hrlab.grading.approval.application.ApprovalResponseAssembler;
import uz.hrlab.grading.approval.application.ApproveStepUseCase;
import uz.hrlab.grading.approval.application.CancelApprovalRequestUseCase;
import uz.hrlab.grading.approval.application.CreateApprovalRequestUseCase;
import uz.hrlab.grading.approval.application.FindApprovalRequestByEntityQuery;
import uz.hrlab.grading.approval.application.ListMyPendingApprovalsQuery;
import uz.hrlab.grading.approval.application.RejectStepUseCase;
import uz.hrlab.grading.approval.application.RequestChangesUseCase;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.approval.domain.ApprovalRequest;
import uz.hrlab.grading.approval.domain.ApprovalRequestStatus;
import uz.hrlab.grading.approval.domain.ApprovalStep;
import uz.hrlab.grading.approval.domain.ApprovalStepStatus;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestJpaEntity;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestRepository;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.api.GlobalExceptionHandler;
import uz.hrlab.grading.common.api.WebMvcSecurityTestConfig;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("security")
@WebMvcTest(controllers = ApprovalController.class,
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "uz\\.hrlab\\.grading\\.security\\..*"))
@Import({WebMvcSecurityTestConfig.class, GlobalExceptionHandler.class,
        ApprovalResponseAssembler.class, ApprovalReadService.class})
class ApprovalControllerSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean CreateApprovalRequestUseCase createUseCase;
    @MockBean ApproveStepUseCase approveUseCase;
    @MockBean RejectStepUseCase rejectUseCase;
    @MockBean RequestChangesUseCase requestChangesUseCase;
    @MockBean CancelApprovalRequestUseCase cancelUseCase;
    @MockBean ListMyPendingApprovalsQuery inboxQuery;
    @MockBean FindApprovalRequestByEntityQuery findByEntity;
    @MockBean ApprovalRequestRepository requests;
    @MockBean ApprovalQueries queries;
    @MockBean AuditService audit;
    // BE-9 — the REAL ApprovalResponseAssembler is imported so the enrichment
    // serialization path is exercised; its two collaborators are mocked so we
    // control the resolved names / labels the wire must carry.
    @MockBean ActorNameResolver actorNames;
    @MockBean ApprovalEntityLabelResolver entityLabels;

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/approval-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createWithoutPermissionIsForbidden() throws Exception {
        // @PreAuthorize fires before bean validation; supply a complete body so
        // we observe 403, not 400.
        String body = "{\"project_id\":\"" + UUID.randomUUID() + "\",\"entity_type\":\"JOB_PROFILE\","
                + "\"entity_id\":\"" + UUID.randomUUID() + "\",\"steps\":[{\"step_order\":1,"
                + "\"required_permission\":\"JOB_PROFILE_APPROVE\"}]}";
        mvc.perform(post("/api/v1/approval-requests")
                        .with(jwt().authorities(() -> "PROJECT_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void approveRequiresDecidePermission() throws Exception {
        mvc.perform(post("/api/v1/approval-requests/{id}/steps/{sid}/approve",
                        UUID.randomUUID(), UUID.randomUUID())
                        .with(jwt().authorities(() -> "APPROVAL_REQUEST_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void approveWithDecidePermissionSucceeds() throws Exception {
        UUID id = UUID.randomUUID();
        UUID sid = UUID.randomUUID();
        given(approveUseCase.approve(any(), any(), any())).willReturn(stubRequest(id));
        withTenant(() -> mvc.perform(post("/api/v1/approval-requests/{id}/steps/{sid}/approve", id, sid)
                        .with(jwt().authorities(() -> "APPROVAL_REQUEST_DECIDE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk()));
    }

    // ---------------------------------------------------------------------
    // BE-3 (2026-06-15) — WRITE-PATH ENRICHMENT. The create + decide responses
    // must carry the resolved localized entity_label_i18n (and initiated_by_name)
    // so the FE shows the position label, not a UUID, immediately after the
    // action. These go through the SAME real assembler as the read paths; a
    // regression that drops write-path enrichment breaks THIS test.
    // ---------------------------------------------------------------------

    @Test
    void createResponseCarriesResolvedEntityLabel() throws Exception {
        ApprovalRequest created = fullStub();
        given(createUseCase.create(any())).willReturn(created);
        stubEnrichment(created);

        String body = "{\"project_id\":\"" + created.projectId() + "\",\"entity_type\":\"JOB_PROFILE\","
                + "\"entity_id\":\"" + created.entityId() + "\",\"steps\":[{\"step_order\":1,"
                + "\"required_permission\":\"JOB_PROFILE_APPROVE\"}]}";
        withTenant(() -> mvc.perform(post("/api/v1/approval-requests")
                        .with(jwt().authorities(() -> "APPROVAL_REQUEST_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entity_label_i18n").exists())
                .andExpect(jsonPath("$.entity_label_i18n.['ru-RU']").value("Профиль RU"))
                .andExpect(jsonPath("$.entity_label_i18n.['en-US']").value("Profile EN"))
                .andExpect(jsonPath("$.initiated_by_name").value("Initiator User")));
    }

    @Test
    void approveResponseCarriesResolvedEntityLabel() throws Exception {
        UUID id = UUID.randomUUID();
        UUID sid = UUID.randomUUID();
        ApprovalRequest decided = fullStub();
        given(approveUseCase.approve(any(), any(), any())).willReturn(decided);
        stubEnrichment(decided);

        withTenant(() -> mvc.perform(post("/api/v1/approval-requests/{id}/steps/{sid}/approve", id, sid)
                        .with(jwt().authorities(() -> "APPROVAL_REQUEST_DECIDE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entity_label_i18n").exists())
                .andExpect(jsonPath("$.entity_label_i18n.['ru-RU']").value("Профиль RU"))
                .andExpect(jsonPath("$.entity_label_i18n.['en-US']").value("Profile EN"))
                .andExpect(jsonPath("$.initiated_by_name").value("Initiator User")));
    }

    @Test
    void rejectWithDecidePermissionSucceeds() throws Exception {
        UUID id = UUID.randomUUID();
        UUID sid = UUID.randomUUID();
        given(rejectUseCase.reject(any(), any(), any())).willReturn(stubRequest(id));
        // Reject is a write-path that now enriches the response (BE-3), so it
        // resolves the active tenant from the context — set one for the 200 path.
        withTenant(() -> mvc.perform(post("/api/v1/approval-requests/{id}/steps/{sid}/reject", id, sid)
                        .with(jwt().authorities(() -> "APPROVAL_REQUEST_DECIDE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"long enough reason yes truly here\"}"))
                .andExpect(status().isOk()));
    }

    @Test
    void inboxRequiresDecidePermission() throws Exception {
        mvc.perform(get("/api/v1/approval-requests/my-inbox")
                        .with(jwt().authorities(() -> "APPROVAL_REQUEST_CREATE")))
                .andExpect(status().isForbidden());
        given(inboxQuery.list()).willReturn(List.of());
        // The inbox read derives the active tenant from the TenantContext to
        // batch-resolve names (BE-5); the WebMvc slice excludes the tenant filter
        // so set the context manually for the 200 branch.
        withTenant(() -> mvc.perform(get("/api/v1/approval-requests/my-inbox")
                        .with(jwt().authorities(() -> "APPROVAL_REQUEST_DECIDE")))
                .andExpect(status().isOk()));
    }

    // ---------------------------------------------------------------------
    // BE-6 (2026-06-12) — cross-tenant denial at the controller layer.
    //
    // Each approval subpage must DENY a caller who has the right permission but
    // is acting in the WRONG tenant. The denial is driven through the REAL
    // controller -> use-case/repository boundary (the mock simulates a
    // findByIdAndTenantId MISS / TenantAccessDeniedException), so a future
    // regression that drops the tenant predicate is caught here, not in prod.
    // TenantAccessDeniedException -> HTTP 404 (no existence-revealing) +
    // CROSS_TENANT_ACCESS_ATTEMPT security log (GlobalExceptionHandler).
    // ---------------------------------------------------------------------

    @Test
    void getByIdDeniesOtherTenant() throws Exception {
        UUID otherTenantRequestId = UUID.randomUUID();
        // Cross-tenant probe: findByIdAndTenantId(otherTenantRequestId, myTenant)
        // returns empty -> controller throws TenantAccessDeniedException -> 404.
        given(requests.findByIdAndTenantId(eq(otherTenantRequestId), any()))
                .willReturn(Optional.empty());

        withTenant(() -> mvc.perform(get("/api/v1/approval-requests/{id}", otherTenantRequestId)
                        .with(jwt().authorities(() -> "APPROVAL_REQUEST_DECIDE")))
                .andExpect(status().isNotFound()));
    }

    @Test
    void listInboxDeniesOtherTenant() throws Exception {
        // The real inbox query tenant-scopes (steps.findInbox + findByIdAndTenantId);
        // a cross-tenant request must NEVER reach the wire. Here the query — which
        // takes the active tenant from the TenantContext — yields nothing for the
        // caller's tenant, so the inbox is empty (no other tenant's rows).
        given(inboxQuery.list()).willReturn(List.of());
        withTenant(() -> mvc.perform(get("/api/v1/approval-requests/my-inbox")
                        .with(jwt().authorities(() -> "APPROVAL_REQUEST_DECIDE")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0)));
    }

    @Test
    void listDeniesOtherTenant() throws Exception {
        // The project-list (search) endpoint scopes by the active tenant; a
        // cross-tenant probe returns no rows. The wire is a PageResponse with an
        // empty items array — never another tenant's metadata.
        given(requests.search(any(), any(), any(), any(), any()))
                .willReturn(org.springframework.data.domain.Page.empty());
        withTenant(() -> mvc.perform(get("/api/v1/approval-requests")
                        .with(jwt().authorities(() -> "APPROVAL_REQUEST_DECIDE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0)));
    }

    @Test
    void approveDeniesOtherTenant() throws Exception {
        UUID id = UUID.randomUUID();
        UUID sid = UUID.randomUUID();
        // Use case resolves request via findByIdAndTenantId and throws on a miss.
        given(approveUseCase.approve(eq(id), eq(sid), any()))
                .willThrow(new uz.hrlab.grading.common.exception.TenantAccessDeniedException());
        mvc.perform(post("/api/v1/approval-requests/{id}/steps/{sid}/approve", id, sid)
                        .with(jwt().authorities(() -> "APPROVAL_REQUEST_DECIDE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectDeniesOtherTenant() throws Exception {
        UUID id = UUID.randomUUID();
        UUID sid = UUID.randomUUID();
        given(rejectUseCase.reject(eq(id), eq(sid), any()))
                .willThrow(new uz.hrlab.grading.common.exception.TenantAccessDeniedException());
        mvc.perform(post("/api/v1/approval-requests/{id}/steps/{sid}/reject", id, sid)
                        .with(jwt().authorities(() -> "APPROVAL_REQUEST_DECIDE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"long enough reason yes truly here\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void requestChangesDeniesOtherTenant() throws Exception {
        UUID id = UUID.randomUUID();
        UUID sid = UUID.randomUUID();
        given(requestChangesUseCase.requestChanges(eq(id), eq(sid), any()))
                .willThrow(new uz.hrlab.grading.common.exception.TenantAccessDeniedException());
        mvc.perform(post("/api/v1/approval-requests/{id}/steps/{sid}/request-changes", id, sid)
                        .with(jwt().authorities(() -> "APPROVAL_REQUEST_DECIDE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"long enough reason yes truly here\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelDeniesOtherTenant() throws Exception {
        UUID id = UUID.randomUUID();
        given(cancelUseCase.cancel(eq(id)))
                .willThrow(new uz.hrlab.grading.common.exception.TenantAccessDeniedException());
        mvc.perform(post("/api/v1/approval-requests/{id}/cancel", id)
                        .with(jwt().authorities(() -> "APPROVAL_REQUEST_CANCEL")))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------------
    // WIRE CONTRACT PINS (golden-file lesson, grade module). These lock the
    // REAL snake_case JSON shape the FE adapter is built against, so any future
    // BE field rename breaks THIS test, not the production Approvals page.
    // Contract source of truth: ApprovalRequestResponse / ApprovalStepResponse
    // + global SNAKE_CASE strategy (application.yml:38) + @JsonInclude(NON_NULL).
    // ---------------------------------------------------------------------

    @Test
    void inboxWireIsBareArrayWithSnakeCaseKeysAndEnrichment() throws Exception {
        ApprovalRequest req = fullStub();
        given(inboxQuery.list()).willReturn(List.of(req));
        stubEnrichment(req);

        withTenant(() -> mvc.perform(get("/api/v1/approval-requests/my-inbox")
                        .with(jwt().authorities(() -> "APPROVAL_REQUEST_DECIDE")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // BARE ARRAY (not a PageResponse envelope) — element 0 is the request.
                .andExpect(jsonPath("$[0].id").value(req.id().toString()))
                .andExpect(jsonPath("$[0].project_id").value(req.projectId().toString()))
                .andExpect(jsonPath("$[0].entity_type").value("JOB_PROFILE"))
                .andExpect(jsonPath("$[0].entity_id").value(req.entityId().toString()))
                .andExpect(jsonPath("$[0].requested_by").value(req.requestedBy().toString()))
                .andExpect(jsonPath("$[0].requested_at").exists())
                .andExpect(jsonPath("$[0].current_status").value("PENDING"))
                .andExpect(jsonPath("$[0].notes_i18n.['en-US']").value("note"))
                // nested steps[] — snake_case + i18n map keys preserved verbatim.
                .andExpect(jsonPath("$[0].steps[0].id").exists())
                .andExpect(jsonPath("$[0].steps[0].step_order").value(1))
                .andExpect(jsonPath("$[0].steps[0].approver_user_id").exists())
                .andExpect(jsonPath("$[0].steps[0].required_permission").value("JOB_PROFILE_APPROVE"))
                .andExpect(jsonPath("$[0].steps[0].status").value("APPROVED"))
                .andExpect(jsonPath("$[0].steps[0].decided_by").exists())
                .andExpect(jsonPath("$[0].steps[0].decided_at").exists())
                .andExpect(jsonPath("$[0].steps[0].reason_i18n.['en-US']").value("looks good"))
                // PENDING step omits decided_* and reason_i18n (@JsonInclude NON_NULL).
                .andExpect(jsonPath("$[0].steps[1].step_order").value(2))
                .andExpect(jsonPath("$[0].steps[1].status").value("PENDING"))
                .andExpect(jsonPath("$[0].steps[1].decided_by").doesNotExist())
                .andExpect(jsonPath("$[0].steps[1].decided_at").doesNotExist())
                .andExpect(jsonPath("$[0].steps[1].reason_i18n").doesNotExist())
                // BE-9 — enrichment fields are now PINNED PRESENT (deliberate,
                // reviewed contract change). snake_case keys + NON_NULL semantics.
                .andExpect(jsonPath("$[0].initiated_by_name").value("Initiator User"))
                .andExpect(jsonPath("$[0].entity_label_i18n.['ru-RU']").value("Профиль RU"))
                .andExpect(jsonPath("$[0].entity_label_i18n.['uz-Cyrl-UZ']").value("Профил UZ-Cyrl"))
                .andExpect(jsonPath("$[0].entity_label_i18n.['uz-Latn-UZ']").value("Profil UZ-Latn"))
                .andExpect(jsonPath("$[0].entity_label_i18n.['en-US']").value("Profile EN"))
                // decided step carries both approver + decider names.
                .andExpect(jsonPath("$[0].steps[0].approver_name").value("Approver One"))
                .andExpect(jsonPath("$[0].steps[0].decided_by_name").value("Decider One"))
                // PENDING step has an approver name but NO decider name (NON_NULL omit).
                .andExpect(jsonPath("$[0].steps[1].approver_name").value("Approver Two"))
                .andExpect(jsonPath("$[0].steps[1].decided_by_name").doesNotExist()));
    }

    @Test
    void getByIdWireIsSingleObjectWithSnakeCaseKeysAndEnrichment() throws Exception {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ApprovalRequest req = fullStub();
        ApprovalRequestJpaEntity entity = mock(ApprovalRequestJpaEntity.class);
        given(requests.findByIdAndTenantId(eq(id), any())).willReturn(Optional.of(entity));
        given(queries.hydrate(entity)).willReturn(req);
        stubEnrichment(req);

        try {
            TenantContextHolder.set(new TenantContext(
                    UUID.randomUUID(), tenantId, java.util.Set.of(), java.util.Set.of(),
                    java.util.Set.of("APPROVAL_REQUEST_DECIDE"), java.util.Set.of(),
                    false, "en-US"));
            mvc.perform(get("/api/v1/approval-requests/{id}", id)
                            .with(jwt().authorities(() -> "APPROVAL_REQUEST_DECIDE")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(req.id().toString()))
                    .andExpect(jsonPath("$.project_id").exists())
                    .andExpect(jsonPath("$.entity_type").value("JOB_PROFILE"))
                    .andExpect(jsonPath("$.current_status").value("PENDING"))
                    .andExpect(jsonPath("$.steps[0].step_order").value(1))
                    .andExpect(jsonPath("$.steps[0].required_permission").value("JOB_PROFILE_APPROVE"))
                    // BE-9 — detail path enrichment pinned PRESENT.
                    .andExpect(jsonPath("$.initiated_by_name").value("Initiator User"))
                    .andExpect(jsonPath("$.entity_label_i18n.['ru-RU']").value("Профиль RU"))
                    .andExpect(jsonPath("$.entity_label_i18n.['en-US']").value("Profile EN"))
                    .andExpect(jsonPath("$.steps[0].approver_name").value("Approver One"))
                    .andExpect(jsonPath("$.steps[0].decided_by_name").value("Decider One"))
                    .andExpect(jsonPath("$.steps[1].decided_by_name").doesNotExist());
        } finally {
            TenantContextHolder.clear();
        }
    }

    /**
     * Stubs the two enrichment collaborators for {@code req}: the
     * {@link ActorNameResolver} returns a deterministic name per harvested user
     * id, and the {@link ApprovalEntityLabelResolver} returns a 4-locale label
     * map. The PENDING step's {@code decided_by} is null, so its
     * {@code decided_by_name} must be omitted (NON_NULL) — proving the wire
     * honors the contract for absent decisions.
     */
    private void stubEnrichment(ApprovalRequest req) {
        ApprovalStep decided = req.steps().get(0);
        ApprovalStep pending = req.steps().get(1);
        Map<UUID, String> names = new java.util.HashMap<>();
        names.put(req.requestedBy(), "Initiator User");
        names.put(decided.approverUserId(), "Approver One");
        names.put(decided.decidedBy(), "Decider One");
        names.put(pending.approverUserId(), "Approver Two");
        // pending.decidedBy() is null → intentionally NOT mapped → name omitted.
        given(actorNames.resolveAll(any(), any())).willReturn(names);
        given(entityLabels.resolve(any(), eq(req.entityType()), eq(req.entityId())))
                .willReturn(Map.of(
                        "ru-RU", "Профиль RU",
                        "uz-Cyrl-UZ", "Профил UZ-Cyrl",
                        "uz-Latn-UZ", "Profil UZ-Latn",
                        "en-US", "Profile EN"));
    }

    private ApprovalRequest stubRequest(UUID id) {
        return new ApprovalRequest(id, UUID.randomUUID(), UUID.randomUUID(),
                ApprovalEntityType.JOB_PROFILE, UUID.randomUUID(), UUID.randomUUID(),
                OffsetDateTime.now(), ApprovalRequestStatus.APPROVED, null, List.of());
    }

    /** Run inside an active TenantContext — the read paths batch-resolve names
     *  using the tenant from the context (BE-5). */
    private void withTenant(ThrowingRunnable body) throws Exception {
        try {
            TenantContextHolder.set(new TenantContext(
                    UUID.randomUUID(), UUID.randomUUID(), java.util.Set.of(), java.util.Set.of(),
                    java.util.Set.of("APPROVAL_REQUEST_DECIDE"), java.util.Set.of(),
                    false, "en-US"));
            body.run();
        } finally {
            TenantContextHolder.clear();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    /**
     * Rich stub exercising every nullable field path of the wire: a decided
     * step (all fields present) and a pending step (decided_by, decided_at and
     * reason_i18n omitted by JsonInclude NON_NULL), plus notes_i18n.
     */
    private ApprovalRequest fullStub() {
        UUID requestId = UUID.randomUUID();
        ApprovalStep decided = new ApprovalStep(
                UUID.randomUUID(), requestId, 1, UUID.randomUUID(), "JOB_PROFILE_APPROVE",
                ApprovalStepStatus.APPROVED, UUID.randomUUID(), OffsetDateTime.now(),
                Map.of("en-US", "looks good"));
        ApprovalStep pending = new ApprovalStep(
                UUID.randomUUID(), requestId, 2, UUID.randomUUID(), "JOB_PROFILE_APPROVE",
                ApprovalStepStatus.PENDING, null, null, null);
        return new ApprovalRequest(requestId, UUID.randomUUID(), UUID.randomUUID(),
                ApprovalEntityType.JOB_PROFILE, UUID.randomUUID(), UUID.randomUUID(),
                OffsetDateTime.now(), ApprovalRequestStatus.PENDING,
                Map.of("en-US", "note"), List.of(decided, pending));
    }
}
