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
  audit: '/audit-logs',
} as const;
