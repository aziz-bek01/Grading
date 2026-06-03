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
   * Users & Access (10 endpoints — MVP 1 prompt §6).
   *
   * Tenant id appears in URL path ONLY for explicit cross-tenant membership
   * endpoints (HRLAB_SUPER_ADMIN authority). The standard list/create
   * endpoints derive tenant from JWT — see userApi.ts header comment.
   */
  users: {
    list: '/users',
    detail: (id: string) => `/users/${id}`,
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
  },
} as const;
