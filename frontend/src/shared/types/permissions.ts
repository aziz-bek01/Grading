/**
 * Permission codes — mirror backend RBAC catalog (`архитектура.md` §8.2/8.3).
 * Frontend uses these strings to gate UI; backend remains source of truth.
 */
export const PERMISSIONS = {
  // Tenant / project visibility
  PROJECT_READ: 'PROJECT_READ',
  PROJECT_CREATE: 'PROJECT_CREATE',
  PROJECT_EDIT: 'PROJECT_EDIT',

  // Tenants / companies (admin/portfolio scope — HRLAB_SUPER_ADMIN)
  TENANT_READ: 'TENANT_READ',
  /** Create a new tenant / company-client. Backend `TENANT_CREATE` (POST /admin/tenants). */
  TENANT_CREATE: 'TENANT_CREATE',
  TENANT_EDIT: 'TENANT_EDIT',
  /** Archive a tenant (soft-delete). Backend `TENANT_ARCHIVE`. */
  TENANT_ARCHIVE: 'TENANT_ARCHIVE',
  /**
   * Client-company (legal entity behind a tenant) — separate code so we can
   * grant company-level edits without granting full tenant administration.
   * Backend codes mirror `CLIENT_LIST` / `CLIENT_VIEW` / `CLIENT_UPDATE`.
   */
  CLIENT_LIST: 'CLIENT_LIST',
  CLIENT_VIEW: 'CLIENT_VIEW',
  CLIENT_UPDATE: 'CLIENT_UPDATE',

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

  // Evaluation (Phase 5)
  EVALUATION_READ: 'EVALUATION_READ',
  EVALUATION_EDIT: 'EVALUATION_EDIT',
  EVALUATION_APPROVE: 'EVALUATION_APPROVE',
  EVALUATION_LOCK: 'EVALUATION_LOCK',
  /**
   * Manual calibration permission — backend code is `CALIBRATION_EDIT`
   * (see `PermissionCodes.CALIBRATION_EDIT`). Requires a reason ≥ 20 chars
   * and an audit event (`SCORE_CALIBRATED`).
   */
  CALIBRATION_EDIT: 'CALIBRATION_EDIT',
  /** @deprecated kept for transient back-compat with Phase 0 placeholders; remove after Phase 6. */
  EVALUATION_ADJUST: 'EVALUATION_ADJUST',

  // Grades + grade-structure (Phase 6)
  GRADE_READ: 'GRADE_READ',
  GRADE_EDIT: 'GRADE_EDIT',
  GRADE_APPROVE: 'GRADE_APPROVE',
  /** Approve a grade structure (DRAFT → APPROVED). Backend `GRADE_STRUCTURE_APPROVE`. */
  GRADE_STRUCTURE_APPROVE: 'GRADE_STRUCTURE_APPROVE',
  /** Lock an approved grade structure (APPROVED → LOCKED). Backend `GRADE_STRUCTURE_LOCK`. */
  GRADE_STRUCTURE_LOCK: 'GRADE_STRUCTURE_LOCK',

  // Compensation / salary
  SALARY_VIEW: 'SALARY_VIEW',
  SALARY_EDIT: 'SALARY_EDIT',
  SALARY_EXPORT: 'SALARY_EXPORT',

  // Reports + exports
  REPORT_READ: 'REPORT_READ',
  /** Request a new report (POST /reports/request) — MVP 2 Phase 3. */
  REPORT_CREATE: 'REPORT_CREATE',
  /** Issue signed download URL for a report (GET /reports/:id/download-url). */
  REPORT_EXPORT: 'REPORT_EXPORT',
  EXPORT_GENERAL: 'EXPORT_GENERAL',

  // Audit
  AUDIT_READ: 'AUDIT_READ',
  AUDIT_READ_CROSS_TENANT: 'AUDIT_READ_CROSS_TENANT',

  // Users & access
  /** List users — backend `hasAnyAuthority('USER_LIST','USER_ACCESS_MANAGE')`. */
  USER_LIST: 'USER_LIST',
  /** View a single user (GET /users/:id). */
  USER_VIEW: 'USER_VIEW',
  /**
   * Edit user profile / status (PATCH /users/:id) — backend
   * `hasAnyAuthority('USER_UPDATE','USER_ACCESS_MANAGE')`. Drives the
   * Edit-user and Disable/Reactivate actions on the user details page.
   */
  USER_UPDATE: 'USER_UPDATE',
  /**
   * Add a membership (POST /users/:id/memberships) — backend
   * `hasAnyAuthority('USER_MEMBERSHIP_MANAGE','USER_ACCESS_MANAGE')`. Also
   * gates membership-level role grant/revoke alongside USER_ROLE_ASSIGN.
   */
  USER_MEMBERSHIP_MANAGE: 'USER_MEMBERSHIP_MANAGE',
  /**
   * Assign / remove a role on a membership — backend
   * `hasAnyAuthority('USER_ROLE_ASSIGN','USER_ACCESS_MANAGE')`.
   */
  USER_ROLE_ASSIGN: 'USER_ROLE_ASSIGN',
  /**
   * Umbrella users-&-access management authority. Backend treats it as an
   * OR-fallback for the fine-grained USER_* codes (see each `@PreAuthorize`).
   */
  USER_ACCESS_MANAGE: 'USER_ACCESS_MANAGE',
  /** Invite a new user (POST /users). Backend code `USER_INVITE`. */
  USER_INVITE: 'USER_INVITE',
  /** Toggle salary_data_permission on a membership — audit-sensitive. */
  USER_SALARY_PERMISSION_TOGGLE: 'USER_SALARY_PERMISSION_TOGGLE',

  // AI
  AI_ASSIST_USE: 'AI_ASSIST_USE',

  // MVP 2 Phase 1 — Workflow / Approval / Comment
  WORKFLOW_READ: 'WORKFLOW_READ',
  WORKFLOW_EDIT: 'WORKFLOW_EDIT',
  APPROVAL_REQUEST_CREATE: 'APPROVAL_REQUEST_CREATE',
  APPROVAL_REQUEST_CANCEL: 'APPROVAL_REQUEST_CANCEL',
  /**
   * CANONICAL decide permission — the backend `@PreAuthorize` on every
   * approve / reject / request-changes endpoint AND on `GET
   * /approval-requests/{id}` / `/my-inbox` checks `APPROVAL_REQUEST_DECIDE`
   * (PermissionCodes.java + seed 023-seed-workflow-approval-permissions.yaml).
   * It is granted to CLIENT_HR_DIRECTOR / HRLAB_PROJECT_MANAGER /
   * HRLAB_SUPER_ADMIN. Previously MISSING from this map, so a decider role that
   * holds DECIDE but not CREATE/STEP_APPROVE could be served an inbox item by
   * the backend yet be blocked by the FE route/sidebar guards — the approval
   * detail "Хатолик юз берди" crash trigger (BE-7). Gates that currently key off
   * the non-canonical APPROVAL_STEP_* codes should accept this code too.
   */
  APPROVAL_REQUEST_DECIDE: 'APPROVAL_REQUEST_DECIDE',
  /** @deprecated non-canonical FE alias — backend uses APPROVAL_REQUEST_DECIDE. */
  APPROVAL_STEP_APPROVE: 'APPROVAL_STEP_APPROVE',
  /** @deprecated non-canonical FE alias — backend uses APPROVAL_REQUEST_DECIDE. */
  APPROVAL_STEP_REJECT: 'APPROVAL_STEP_REJECT',
  COMMENT_CREATE: 'COMMENT_CREATE',
  COMMENT_EDIT: 'COMMENT_EDIT',
  COMMENT_DELETE: 'COMMENT_DELETE',

  // MVP 2 Phase 2 — Integration (Excel import/export)
  POSITION_IMPORT: 'POSITION_IMPORT',
  METHODOLOGY_IMPORT: 'METHODOLOGY_IMPORT',
  GRADE_IMPORT: 'GRADE_IMPORT',
  IMPORT_READ: 'IMPORT_READ',
  IMPORT_CANCEL: 'IMPORT_CANCEL',
  EXPORT_READ: 'EXPORT_READ',
  EXPORT_REQUEST: 'EXPORT_REQUEST',
} as const;

export type PermissionCode = (typeof PERMISSIONS)[keyof typeof PERMISSIONS];
