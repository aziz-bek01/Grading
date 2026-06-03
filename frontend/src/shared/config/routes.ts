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
  clientDetails: (tenantId: string) => `/app/clients/${tenantId}`,
  projects: '/app/projects',
  audit: '/app/audit',
  usersAccess: '/app/users',
  /** Legacy slug — kept for redirect; new code MUST use `usersAccess`. */
  usersAccessLegacy: '/app/users-access',
  userDetails: (userId: string) => `/app/users/${userId}`,
  project: (projectId: string) => `/app/projects/${projectId}/workspace`,
  projectWorkspace: (projectId: string) => `/app/projects/${projectId}/workspace`,
  projectOrganization: (projectId: string) => `/app/projects/${projectId}/organization`,
  projectPositions: (projectId: string) => `/app/projects/${projectId}/positions`,
  projectPositionDetail: (projectId: string, positionId: string) =>
    `/app/projects/${projectId}/positions/${positionId}`,
  projectMethodology: (projectId: string) => `/app/projects/${projectId}/methodology`,
  projectMethodologyBuilder: (projectId: string, methodologyId: string, versionId: string) =>
    `/app/projects/${projectId}/methodology/${methodologyId}/versions/${versionId}/edit`,
  projectMethodologyTranslations: (projectId: string, methodologyId: string, versionId: string) =>
    `/app/projects/${projectId}/methodology/${methodologyId}/versions/${versionId}/translations`,
  projectEvaluation: (projectId: string) => `/app/projects/${projectId}/evaluation`,
  projectEvaluationDetail: (projectId: string, evaluationId: string) =>
    `/app/projects/${projectId}/evaluation/${evaluationId}`,
  projectGrades: (projectId: string) => `/app/projects/${projectId}/grades`,
  projectGradeStructureEdit: (projectId: string, gradeStructureId: string) =>
    `/app/projects/${projectId}/grades/${gradeStructureId}/edit`,
  projectGradeStructureVersionEdit: (
    projectId: string,
    gradeStructureId: string,
    versionId: string,
  ) => `/app/projects/${projectId}/grades/${gradeStructureId}/versions/${versionId}/edit`,
  projectGradePyramid: (projectId: string, gradeStructureId: string) =>
    `/app/projects/${projectId}/grades/${gradeStructureId}/pyramid`,
  projectCompensation: (projectId: string) => `/app/projects/${projectId}/compensation`,
  projectReports: (projectId: string) => `/app/projects/${projectId}/reports`,
  // MVP 2 Phase 1 — Approvals
  approvalsInbox: '/app/approvals',
  approvalDetails: (approvalId: string) => `/app/approvals/${approvalId}`,
  projectApprovals: (projectId: string) => `/app/projects/${projectId}/approvals`,
  // MVP 2 Phase 2 — Imports & Exports
  projectImports: (projectId: string) => `/app/projects/${projectId}/imports`,
  projectImportNew: (projectId: string) => `/app/projects/${projectId}/imports/new`,
  projectImportDetails: (projectId: string, importId: string) =>
    `/app/projects/${projectId}/imports/${importId}`,
  projectExports: (projectId: string) => `/app/projects/${projectId}/exports`,
  projectExportDetails: (projectId: string, exportId: string) =>
    `/app/projects/${projectId}/exports/${exportId}`,
} as const;
