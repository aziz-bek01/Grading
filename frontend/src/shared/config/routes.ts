/**
 * Canonical route paths — never hardcode a path string in components.
 * Use builders for parameterised routes.
 */
export const routes = {
  login: '/login',
  accessDenied: '/access-denied',
  noAccess: '/no-access',
  app: '/app',
  dashboard: '/app/dashboard',
  clients: '/app/clients',
  projects: '/app/projects',
  audit: '/app/audit',
  usersAccess: '/app/users-access',
  project: (projectId: string) => `/app/projects/${projectId}/workspace`,
  projectWorkspace: (projectId: string) => `/app/projects/${projectId}/workspace`,
  projectOrganization: (projectId: string) => `/app/projects/${projectId}/organization`,
  projectPositions: (projectId: string) => `/app/projects/${projectId}/positions`,
  projectPositionDetail: (projectId: string, positionId: string) =>
    `/app/projects/${projectId}/positions/${positionId}`,
  projectMethodology: (projectId: string) => `/app/projects/${projectId}/methodology`,
  projectEvaluation: (projectId: string) => `/app/projects/${projectId}/evaluation`,
  projectGrades: (projectId: string) => `/app/projects/${projectId}/grades`,
  projectCompensation: (projectId: string) => `/app/projects/${projectId}/compensation`,
  projectReports: (projectId: string) => `/app/projects/${projectId}/reports`,
} as const;
