/** Single source of truth for API endpoint paths. */
export const endpoints = {
  auth: {
    exchange: '/auth/exchange',
    me: '/users/me',
    logout: '/auth/logout',
    devLogin: '/dev-auth/login',
  },
  access: {
    tenantContext: '/access/tenant-context',
  },
  /**
   * HRLab portfolio (admin) endpoints — see F-3 backend contract.
   * `tenants`  → multi-tenant catalog (TENANT_READ).
   * `clients`  → client-company (legal entity) catalog (CLIENT_LIST).
   * `tenantId` here is part of the URL ONLY because the caller is operating
   * cross-tenant (HRLAB_SUPER_ADMIN). It is NEVER passed as a body field.
   */
  admin: {
    tenants: '/admin/tenants',
    /** POST /admin/tenants — create a new tenant + client-company (TENANT_CREATE). */
    createTenant: '/admin/tenants',
    tenant: (id: string) => `/admin/tenants/${id}`,
    tenantArchive: (id: string) => `/admin/tenants/${id}/archive`,
    tenantStats: (id: string) => `/admin/tenants/${id}/stats`,
    clients: '/admin/clients',
    client: (clientId: string) => `/admin/clients/${clientId}`,
  },
  projects: {
    list: '/projects',
    detail: (projectId: string) => `/projects/${projectId}`,
    workflowProgress: (projectId: string) => `/projects/${projectId}/workflow-progress`,
  },
  departments: {
    tree: '/departments/tree',
    list: '/departments',
    detail: (id: string) => `/departments/${id}`,
    archive: (id: string) => `/departments/${id}/archive`,
    /**
     * T4 (Defect 1) — server-authoritative per-department position counts.
     * GET /departments/position-counts?projectId= (ORG_READ). Returns
     * [{ department_id, direct_count, subtree_count }] (snake_case wire). The
     * subtree figure rolls up descendants so a parent never shows 0, and the
     * count is never truncated by a position page cap. SINGLE source for both
     * the wizard coverage badges and the dept progress strip.
     */
    positionCounts: '/departments/position-counts',
  },
  positions: {
    list: '/positions',
    detail: (id: string) => `/positions/${id}`,
    archive: (id: string) => `/positions/${id}/archive`,
  },
  analytics: {
    portfolioSummary: '/analytics/portfolio-summary',
  },
  /**
   * Audit Reader endpoints.
   *
   * `list` matches the backend contract `GET /api/v1/audit` returning
   * `Page<AuditEventResponse>`. The legacy `audit-logs` alias is kept
   * for the dashboard widget that aggregates alert counts (different
   * controller, different shape — do not confuse the two).
   */
  audit: {
    list: '/audit',
    detail: (id: string) => `/audit/${id}`,
  },
  auditAlerts: '/audit-logs',
  workflow: {
    progress: (projectId: string) => `/projects/${projectId}/workflow-progress`,
    advance: (projectId: string) => `/projects/${projectId}/workflow/advance`,
    recompute: (projectId: string) => `/projects/${projectId}/workflow/recompute`,
  },
  approval: {
    requests: '/approval-requests',
    detail: (id: string) => `/approval-requests/${id}`,
    approveStep: (id: string, stepId: string) => `/approval-requests/${id}/steps/${stepId}/approve`,
    rejectStep: (id: string, stepId: string) => `/approval-requests/${id}/steps/${stepId}/reject`,
    requestChangesStep: (id: string, stepId: string) =>
      `/approval-requests/${id}/steps/${stepId}/request-changes`,
    cancel: (id: string) => `/approval-requests/${id}/cancel`,
    myInbox: '/approval-requests/my-inbox',
  },
  comments: {
    list: '/comments',
    detail: (id: string) => `/comments/${id}`,
    myMentions: '/comments/my-mentions',
  },
  /**
   * Evaluation sheet endpoints. `my` is the evaluator self-inbox
   * (GET /evaluations/my, gated EVALUATION_READ) returning ONLY the caller's own
   * sheets — tenant + evaluator are derived server-side from the JWT, never sent.
   */
  evaluations: {
    my: '/evaluations/my',
  },
  /**
   * Multi-evaluator EVALUATION_PANEL endpoints (MVP 2). Base /panels.
   * Permissions: create/assign/withdraw/lock/submit → EVALUATION_PANEL_MANAGE;
   * list/detail → EVALUATION_READ; result → CAMPAIGN_RESULTS_VIEW (404 while
   * collecting). The CEO approval reuses the existing /approval-requests path
   * (entity_type=EVALUATION_PANEL) — no panel-specific approval endpoint.
   */
  panels: {
    list: '/panels',
    /**
     * POST /panels/bulk-create (EVALUATION_PANEL_MANAGE) — one shared roster ⇒
     * one panel per position_id (NOT a mega-panel). Returns 201 with
     * { created, failed[] } where failed[] keys on position_id. Replaces the
     * sequential create-then-loop-assign orchestration.
     */
    bulkCreate: '/panels/bulk-create',
    /**
     * GET /panels/roster-suggestions?project_id=&department_id= — ADVISORY
     * dept-director candidates (snake_case REQUIRED query params). 404 when the
     * department is outside the caller's ABAC subtree → treat as "no
     * suggestions", not a hard error.
     */
    rosterSuggestions: '/panels/roster-suggestions',
    detail: (id: string) => `/panels/${id}`,
    result: (id: string) => `/panels/${id}/result`,
    evaluators: (id: string) => `/panels/${id}/evaluators`,
    evaluator: (id: string, userId: string) => `/panels/${id}/evaluators/${userId}`,
    lockRoster: (id: string) => `/panels/${id}/lock-roster`,
    submit: (id: string) => `/panels/${id}/submit`,
    /**
     * T3 (Defect 2) panel management (all EVALUATION_PANEL_MANAGE; cross-tenant
     * → 404). Status-gated server-side — the FE mirrors the predicates so
     * non-applicable actions are not even offered (no 403 round-trip):
     *   delete  — DELETE /panels/{id} body { reason ≥ 5 }; only COLLECTING; 204.
     *   archive — POST /panels/{id}/archive body { reason };
     *             AWAITING_EVALUATIONS | AVERAGED | SUBMITTED.
     *   reopen  — POST /panels/{id}/reopen (no body); SUBMITTED | AVERAGED.
     */
    delete: (id: string) => `/panels/${id}`,
    archive: (id: string) => `/panels/${id}/archive`,
    reopen: (id: string) => `/panels/${id}/reopen`,
    /**
     * Feature 2 — reopen an APPROVED panel to add an ADDITIONAL expert
     * (POST /panels/{id}/reopen-for-expert; EVALUATION_PANEL_MANAGE). Body
     * (snake_case): { additional_evaluator_user_id, reason ≥ 5 }. Returns the
     * updated PanelResponse with status AWAITING_EVALUATIONS. 400
     * PANEL_NOT_APPROVED_FOR_REOPEN if the panel is not APPROVED.
     */
    reopenForExpert: (id: string) => `/panels/${id}/reopen-for-expert`,
  },
  /**
   * Users & Access (10 endpoints — MVP 1 prompt §6).
   *
   * Tenant id appears in URL path ONLY for explicit cross-tenant membership
   * endpoints (HRLAB_SUPER_ADMIN authority). The standard list/create
   * endpoints derive tenant from JWT — see userApi.ts header comment.
   */
  users: {
    list: '/users',
    detail: (id: string) => `/users/${id}`,
    /**
     * POST /users/{id}/reset-login — re-provisions the user's IdP (login)
     * account by their grading email (creates a fresh ACTIVE account if
     * missing / mis-linked) and sets an admin-chosen password. Recovery path
     * for users whose login was never activated. Body: { password }.
     */
    resetLogin: (id: string) => `/users/${id}/reset-login`,
    memberships: (id: string) => `/users/${id}/memberships`,
    membership: (id: string, tenantId: string) =>
      `/users/${id}/memberships/${tenantId}`,
    membershipRoles: (id: string, tenantId: string) =>
      `/users/${id}/memberships/${tenantId}/roles`,
    membershipRole: (id: string, tenantId: string, roleId: string) =>
      `/users/${id}/memberships/${tenantId}/roles/${roleId}`,
    membershipSalaryPermission: (id: string, tenantId: string) =>
      `/users/${id}/memberships/${tenantId}/salary-permission`,
    audit: (id: string) => `/users/${id}/audit`,
    /**
     * RBAC access-scope endpoints (slice E4-S1).
     *
     * Unlike the rest of the users contract, `tenant_id` is an explicit field
     * here (query param on GET, body field on the PUTs) because these are
     * cross-tenant admin surfaces — the caller names the membership being
     * edited. Both PUTs are REPLACE-set: the body carries the full desired set.
     */
    scopes: (id: string) => `/users/${id}/scopes`,
    departmentScopes: (id: string) => `/users/${id}/department-scopes`,
    projectAssignments: (id: string) => `/users/${id}/project-assignments`,
  },
} as const;
