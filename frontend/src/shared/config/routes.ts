/**
 * Canonical route paths — never hardcode a path string in components.
 * Use builders for parameterised routes.
 */
export const routes = {
  login: '/login',
  /** OIDC redirect callback — registered OUTSIDE RequireAuth. */
  authCallback: '/auth/callback',
  accessDenied: '/access-denied',
  noAccess: '/no-access',
  app: '/app',
  dashboard: '/app/dashboard',
  clients: '/app/clients',
  clientDetails: (tenantId: string) => `/app/clients/${tenantId}`,
  projects: '/app/projects',
  audit: '/app/audit',
  /**
   * Feature 1 — evaluator self-inbox ("My evaluations"). Global (NOT
   * project-scoped) because GET /evaluations/my returns the caller's own sheets
   * across projects. Each row deep-links to the project-scoped sheet route via
   * {@link projectEvaluationDetail} using the active project id.
   */
  myEvaluations: '/app/my-evaluations',
  usersAccess: '/app/users',
  /** Legacy slug — kept for redirect; new code MUST use `usersAccess`. */
  usersAccessLegacy: '/app/users-access',
  userDetails: (userId: string) => `/app/users/${userId}`,
  /** Roles admin (slice E2) — list + per-role permission matrix. */
  roles: '/app/roles',
  roleDetails: (roleCode: string) => `/app/roles/${encodeURIComponent(roleCode)}`,
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
  /**
   * T3 (Defect 2) — evaluation-panel detail. A created panel was previously
   * fetchable via API but had no UI surface; this route un-dead-ends both the
   * panels list and the "already paneled" wizard rows (deep-link target).
   */
  projectPanelDetail: (projectId: string, panelId: string) =>
    `/app/projects/${projectId}/evaluation/panels/${panelId}`,
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
