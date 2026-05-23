/**
 * Permission codes — mirror backend RBAC catalog (`архитектура.md` §8.2/8.3).
 * Frontend uses these strings to gate UI; backend remains source of truth.
 */
export const PERMISSIONS = {
  // Tenant / project visibility
  PROJECT_READ: 'PROJECT_READ',
  PROJECT_CREATE: 'PROJECT_CREATE',
  PROJECT_EDIT: 'PROJECT_EDIT',

  // Tenants / companies
  TENANT_READ: 'TENANT_READ',
  TENANT_EDIT: 'TENANT_EDIT',

  // Organization
  ORG_READ: 'ORG_READ',
  ORG_EDIT: 'ORG_EDIT',
  ORG_IMPORT: 'ORG_IMPORT',

  // Positions
  POSITION_READ: 'POSITION_READ',
  POSITION_CREATE: 'POSITION_CREATE',
  POSITION_EDIT: 'POSITION_EDIT',

  // Job profiles
  JOB_PROFILE_READ: 'JOB_PROFILE_READ',
  JOB_PROFILE_EDIT: 'JOB_PROFILE_EDIT',
  JOB_PROFILE_APPROVE: 'JOB_PROFILE_APPROVE',

  // Job analysis (questionnaire) — mirrors backend PermissionCodes (Liquibase changelog 007).
  // Distinct from JOB_PROFILE_* because the matrix (007-seed-job-analysis-permissions.yaml)
  // grants these to different role sets (e.g. CLIENT_HR_SPECIALIST has JOB_ANALYSIS_EDIT but
  // not JOB_PROFILE_EDIT).
  JOB_ANALYSIS_READ: 'JOB_ANALYSIS_READ',
  JOB_ANALYSIS_EDIT: 'JOB_ANALYSIS_EDIT',

  // Methodology
  METHODOLOGY_READ: 'METHODOLOGY_READ',
  METHODOLOGY_CREATE: 'METHODOLOGY_CREATE',
  METHODOLOGY_EDIT: 'METHODOLOGY_EDIT',
  METHODOLOGY_APPROVE: 'METHODOLOGY_APPROVE',
  METHODOLOGY_LOCK: 'METHODOLOGY_LOCK',

  // Evaluation
  EVALUATION_READ: 'EVALUATION_READ',
  EVALUATION_EDIT: 'EVALUATION_EDIT',
  EVALUATION_APPROVE: 'EVALUATION_APPROVE',
  EVALUATION_ADJUST: 'EVALUATION_ADJUST',

  // Grades
  GRADE_READ: 'GRADE_READ',
  GRADE_EDIT: 'GRADE_EDIT',
  GRADE_APPROVE: 'GRADE_APPROVE',

  // Compensation / salary
  SALARY_VIEW: 'SALARY_VIEW',
  SALARY_EDIT: 'SALARY_EDIT',
  SALARY_EXPORT: 'SALARY_EXPORT',

  // Reports + exports
  REPORT_READ: 'REPORT_READ',
  REPORT_EXPORT: 'REPORT_EXPORT',
  EXPORT_GENERAL: 'EXPORT_GENERAL',

  // Audit
  AUDIT_READ: 'AUDIT_READ',
  AUDIT_READ_CROSS_TENANT: 'AUDIT_READ_CROSS_TENANT',

  // Users & access
  USER_READ: 'USER_READ',
  USER_EDIT: 'USER_EDIT',
  USER_ACCESS_MANAGE: 'USER_ACCESS_MANAGE',

  // AI
  AI_ASSIST_USE: 'AI_ASSIST_USE',
} as const;

export type PermissionCode = (typeof PERMISSIONS)[keyof typeof PERMISSIONS];
