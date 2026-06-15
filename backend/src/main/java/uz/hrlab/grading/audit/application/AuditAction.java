package uz.hrlab.grading.audit.application;

/**
 * Canonical audit action codes (architecture §8.5 + PRD audit matrix).
 *
 * <p>Use these constants as the {@code action} column value of an audit
 * record. Keep the catalog flat and stable — downstream forensics search by
 * literal string.
 */
public final class AuditAction {

    private AuditAction() { }

    // Authentication
    public static final String LOGIN_SUCCESS         = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED          = "LOGIN_FAILED";
    public static final String MFA_CHALLENGE         = "MFA_CHALLENGE";
    public static final String MFA_FAILED            = "MFA_FAILED";
    public static final String LOGOUT                = "LOGOUT";
    public static final String TENANT_CONTEXT_SWITCH = "TENANT_CONTEXT_SWITCH";

    // Tenant
    public static final String TENANT_CREATED   = "TENANT_CREATED";
    public static final String TENANT_UPDATED   = "TENANT_UPDATED";
    public static final String TENANT_SUSPENDED = "TENANT_SUSPENDED";
    public static final String TENANT_ARCHIVED  = "TENANT_ARCHIVED";

    // Client company (Sprint F, E9 — F-1 BE).
    public static final String CLIENT_COMPANY_UPDATED = "CLIENT_COMPANY_UPDATED";

    // Access
    public static final String USER_INVITED       = "USER_INVITED";
    public static final String USER_ACCESS_CHANGED = "USER_ACCESS_CHANGED";
    public static final String ROLE_ASSIGNED      = "ROLE_ASSIGNED";

    // User management module (MVP 1 Phase 1.5 — access admin endpoints).
    // Every mutation in /api/v1/users/** records ONE of these; the salary
    // permission toggle has two distinct codes so SIEM dashboards can isolate
    // grants vs revocations without parsing reason JSON.
    public static final String USER_CREATED                       = "USER_CREATED";
    public static final String USER_UPDATED                       = "USER_UPDATED";
    public static final String USER_REVOKED                       = "USER_REVOKED";
    public static final String USER_MEMBERSHIP_ADDED              = "USER_MEMBERSHIP_ADDED";
    public static final String USER_MEMBERSHIP_REVOKED            = "USER_MEMBERSHIP_REVOKED";
    public static final String USER_ROLE_ASSIGNED                 = "USER_ROLE_ASSIGNED";
    public static final String USER_ROLE_REMOVED                  = "USER_ROLE_REMOVED";
    public static final String USER_SALARY_PERMISSION_GRANTED     = "USER_SALARY_PERMISSION_GRANTED";
    public static final String USER_SALARY_PERMISSION_REVOKED     = "USER_SALARY_PERMISSION_REVOKED";
    public static final String CROSS_TENANT_USER_ACCESS_ATTEMPT   = "CROSS_TENANT_USER_ACCESS_ATTEMPT";

    // Role → permission admin (E2 — admin grant/revoke of a role's permission
    // set via PUT /api/v1/roles/{roleId}/permissions). One row per individual
    // permission change so SIEM can reconstruct the exact delta of a replace-set
    // operation (mirrors the USER_*_SCOPE_* / _ASSIGNMENT_* pattern below).
    public static final String ROLE_PERMISSION_GRANTED            = "ROLE_PERMISSION_GRANTED";
    public static final String ROLE_PERMISSION_REVOKED            = "ROLE_PERMISSION_REVOKED";

    // Custom (tenant-defined) role lifecycle (E3 — create/edit/delete a TENANT
    // CUSTOM role via /api/v1/roles). ROLE_CREATED/UPDATED/DELETED bracket the
    // operation; the per-permission delta on create/edit still emits the E2
    // ROLE_PERMISSION_GRANTED/_REVOKED rows so SIEM reconstructs the full grant set.
    public static final String ROLE_CREATED                       = "ROLE_CREATED";
    public static final String ROLE_UPDATED                       = "ROLE_UPDATED";
    public static final String ROLE_DELETED                       = "ROLE_DELETED";

    // ABAC scope assignment (E4-S1 — admin grants/revokes per-row department
    // scope + project assignments). One row per individual change so SIEM can
    // reconstruct the exact delta of a replace-set operation.
    public static final String USER_DEPARTMENT_SCOPE_GRANTED      = "USER_DEPARTMENT_SCOPE_GRANTED";
    public static final String USER_DEPARTMENT_SCOPE_REVOKED      = "USER_DEPARTMENT_SCOPE_REVOKED";
    public static final String USER_PROJECT_ASSIGNMENT_ADDED      = "USER_PROJECT_ASSIGNMENT_ADDED";
    public static final String USER_PROJECT_ASSIGNMENT_REMOVED    = "USER_PROJECT_ASSIGNMENT_REMOVED";

    // Credential edits via PATCH /api/v1/users/{id} (decision doc 08 extension).
    // The IdP is the system of record for credentials, so these reflect changes
    // pushed to ZITADEL. USER_PASSWORD_RESET NEVER carries the password value;
    // USER_EMAIL_CHANGED records old→new emails (not secrets) for forensics.
    public static final String USER_PASSWORD_RESET               = "USER_PASSWORD_RESET";
    public static final String USER_EMAIL_CHANGED                = "USER_EMAIL_CHANGED";

    // Login recovery via POST /api/v1/users/{id}/reset-login (re-issue a working
    // login for a user whose IdP account is missing, in USER_STATE_INITIAL, or
    // mis-linked to the wrong subject). Re-provisions by the CURRENT grading email
    // (create-or-link), re-links external_idp_subject if it changed, sets the
    // admin password and activates. NEVER carries the password — only the
    // external subject id, the email, and whether the subject was re-linked.
    public static final String USER_LOGIN_RESET                  = "USER_LOGIN_RESET";

    // IdP provisioning (decision doc 08, Option A — admin-set-password).
    // Emitted by InviteUserUseCase when grading.idp.zitadel.enabled=true.
    // NEVER carry the password or the IdP token in the reason/payload — only the
    // external subject id and email.
    public static final String USER_IDP_ACCOUNT_CREATED    = "USER_IDP_ACCOUNT_CREATED";
    public static final String USER_IDP_ACCOUNT_LINKED     = "USER_IDP_ACCOUNT_LINKED";
    public static final String USER_IDP_PROVISIONING_FAILED = "USER_IDP_PROVISIONING_FAILED";

    // IdP offboarding symmetry (decision doc 08, US-3). Emitted when a user is
    // disabled / has their last active membership revoked (deactivate) or is
    // re-enabled (reactivate) while grading.idp.zitadel.enabled=true. The
    // FAILED variant is written in REQUIRES_NEW so the in-app cutoff is never
    // blocked by a transient IdP outage — it remains on record for retry.
    // NEVER carry the IdP token; only the external subject id.
    public static final String USER_IDP_ACCOUNT_DEACTIVATED = "USER_IDP_ACCOUNT_DEACTIVATED";
    public static final String USER_IDP_ACCOUNT_REACTIVATED  = "USER_IDP_ACCOUNT_REACTIVATED";
    public static final String USER_IDP_DEACTIVATION_FAILED  = "USER_IDP_DEACTIVATION_FAILED";

    // Project / org
    public static final String PROJECT_CREATED   = "PROJECT_CREATED";
    public static final String PROJECT_UPDATED   = "PROJECT_UPDATED";
    public static final String PROJECT_LOCKED    = "PROJECT_LOCKED";
    public static final String PROJECT_ARCHIVED  = "PROJECT_ARCHIVED";
    public static final String DEPARTMENT_CREATED = "DEPARTMENT_CREATED";
    public static final String DEPARTMENT_UPDATED = "DEPARTMENT_UPDATED";
    public static final String DEPARTMENT_ARCHIVED = "DEPARTMENT_ARCHIVED";

    // Position / profile
    public static final String POSITION_CREATED       = "POSITION_CREATED";
    public static final String POSITION_UPDATED       = "POSITION_UPDATED";
    public static final String POSITION_ARCHIVED      = "POSITION_ARCHIVED";

    // Job profile (Phase 3)
    public static final String JOB_PROFILE_CREATED            = "JOB_PROFILE_CREATED";
    public static final String JOB_PROFILE_UPDATED            = "JOB_PROFILE_UPDATED";
    public static final String JOB_PROFILE_SUBMITTED          = "JOB_PROFILE_SUBMITTED";
    public static final String JOB_PROFILE_CHANGES_REQUESTED  = "JOB_PROFILE_CHANGES_REQUESTED";
    public static final String JOB_PROFILE_APPROVED           = "JOB_PROFILE_APPROVED";
    public static final String JOB_PROFILE_ARCHIVED           = "JOB_PROFILE_ARCHIVED";
    public static final String JOB_PROFILE_REVISION_CREATED   = "JOB_PROFILE_REVISION_CREATED";

    // Job analysis (Phase 3)
    public static final String JOB_ANALYSIS_QUESTIONNAIRE_CREATED = "JOB_ANALYSIS_QUESTIONNAIRE_CREATED";
    public static final String JOB_ANALYSIS_ANSWER_UPDATED        = "JOB_ANALYSIS_ANSWER_UPDATED";
    public static final String JOB_ANALYSIS_SUBMITTED             = "JOB_ANALYSIS_SUBMITTED";
    public static final String JOB_ANALYSIS_ARCHIVED              = "JOB_ANALYSIS_ARCHIVED";

    // Methodology
    public static final String METHODOLOGY_CREATED                   = "METHODOLOGY_CREATED";
    public static final String METHODOLOGY_UPDATED                   = "METHODOLOGY_UPDATED";
    public static final String METHODOLOGY_ARCHIVED                  = "METHODOLOGY_ARCHIVED";
    public static final String METHODOLOGY_APPROVED                  = "METHODOLOGY_APPROVED";
    public static final String METHODOLOGY_LOCKED                    = "METHODOLOGY_LOCKED";
    public static final String METHODOLOGY_VERSION_CREATED           = "METHODOLOGY_VERSION_CREATED";
    public static final String METHODOLOGY_VERSION_UPDATED           = "METHODOLOGY_VERSION_UPDATED";
    public static final String METHODOLOGY_VERSION_APPROVED          = "METHODOLOGY_VERSION_APPROVED";
    public static final String METHODOLOGY_VERSION_LOCKED            = "METHODOLOGY_VERSION_LOCKED";
    public static final String METHODOLOGY_VERSION_ARCHIVED          = "METHODOLOGY_VERSION_ARCHIVED";
    public static final String METHODOLOGY_VERSION_REVISION_CREATED  = "METHODOLOGY_VERSION_REVISION_CREATED";
    public static final String FACTOR_CREATED                        = "FACTOR_CREATED";
    public static final String FACTOR_UPDATED                        = "FACTOR_UPDATED";
    public static final String FACTOR_REMOVED                        = "FACTOR_REMOVED";
    public static final String FACTOR_REORDERED                      = "FACTOR_REORDERED";
    public static final String FACTOR_LEVEL_CREATED                  = "FACTOR_LEVEL_CREATED";
    public static final String FACTOR_LEVEL_UPDATED                  = "FACTOR_LEVEL_UPDATED";
    public static final String FACTOR_LEVEL_REMOVED                  = "FACTOR_LEVEL_REMOVED";
    public static final String FACTOR_LEVEL_REORDERED                = "FACTOR_LEVEL_REORDERED";
    // Approved-methodology in-place edit (BE-4 / BE-5). DEPRECATED variants are
    // emitted when a referenced factor/level on an APPROVED version is soft-
    // deprecated (deprecated_at/by) instead of hard-deleted; the unreferenced
    // case still emits FACTOR_REMOVED / FACTOR_LEVEL_REMOVED. METHODOLOGY_APPROVED_EDIT
    // is the ONE umbrella event per approved-version edit transaction, stamped
    // with the field delta + frozenEvaluationCount blast radius (BE-5); the
    // per-field FACTOR_UPDATED / FACTOR_LEVEL_UPDATED / *_DEPRECATED events are
    // ALSO emitted (no duplication — the umbrella adds the "approved edit" framing).
    public static final String FACTOR_DEPRECATED                     = "FACTOR_DEPRECATED";
    public static final String FACTOR_LEVEL_DEPRECATED               = "FACTOR_LEVEL_DEPRECATED";
    public static final String METHODOLOGY_APPROVED_EDIT             = "METHODOLOGY_APPROVED_EDIT";

    // Methodology templates (Epic E — DB-backed tenant CUSTOM templates). A
    // template is a frozen deep copy of a version's factors/levels, "saved for
    // the company" and reusable across the tenant's projects via
    // POST /methodologies/from-template. Built-ins (MethodologyTemplateRegistry)
    // are global + read-only and emit NO template lifecycle audit.
    public static final String METHODOLOGY_TEMPLATE_CREATED          = "METHODOLOGY_TEMPLATE_CREATED";
    public static final String METHODOLOGY_TEMPLATE_UPDATED          = "METHODOLOGY_TEMPLATE_UPDATED";
    public static final String METHODOLOGY_TEMPLATE_ARCHIVED         = "METHODOLOGY_TEMPLATE_ARCHIVED";

    // Scoring (Phase 5)
    public static final String EVALUATION_CREATED            = "EVALUATION_CREATED";
    public static final String EVALUATION_UPDATED            = "EVALUATION_UPDATED";
    public static final String EVALUATION_SCORE_UPSERTED     = "EVALUATION_SCORE_UPSERTED";
    public static final String EVALUATION_SCORE_DELETED      = "EVALUATION_SCORE_DELETED";
    public static final String EVALUATION_SCORE_CHANGED      = "EVALUATION_SCORE_CHANGED";
    public static final String EVALUATION_SUBMITTED          = "EVALUATION_SUBMITTED";
    public static final String EVALUATION_CHANGES_REQUESTED  = "EVALUATION_CHANGES_REQUESTED";
    public static final String EVALUATION_APPROVED           = "EVALUATION_APPROVED";
    public static final String EVALUATION_LOCKED             = "EVALUATION_LOCKED";
    public static final String EVALUATION_ARCHIVED           = "EVALUATION_ARCHIVED";
    /** Hard delete of a DRAFT-only evaluation (Item 1, BE-2). Non-DRAFT keeps ARCHIVE. */
    public static final String EVALUATION_DELETED            = "EVALUATION_DELETED";
    public static final String EVALUATION_SCORE_CALIBRATED   = "EVALUATION_SCORE_CALIBRATED";
    /** Bulk PATCH score across multiple evaluations for a single factor (Excel K-sheet UX). */
    public static final String EVALUATION_BULK_SCORE_SET     = "EVALUATION_BULK_SCORE_SET";
    /** Bulk submit (multiple evaluations transitioned COMPLETE→SUBMITTED in one operation). */
    public static final String EVALUATION_BULK_SUBMITTED     = "EVALUATION_BULK_SUBMITTED";

    // Evaluation panel — multi-evaluator (MVP2). Append-only; every row carries
    // tenant_id + project_id + actor_user_id + panel_id (entity_id). Redacted:
    // _AVERAGED stores evaluator_count + total only, never per-evaluator comments
    // (REQ-AUD-3). Blind-bypass denials reuse ACCESS_DENIED_BY_ABAC.
    public static final String EVALUATION_PANEL_CREATED             = "EVALUATION_PANEL_CREATED";
    /**
     * BE-6 — ONE wrapper event per bulk panel create (department commission
     * setup). Emitted at the use-case level IN ADDITION to every inherited
     * per-panel {@code EVALUATION_PANEL_CREATED} and per-seat assign audit, so a
     * whole-department commission is traceable as a single operation. Its reason
     * carries methodology_version_id + requested_count + created_count +
     * failed_count. No per-panel event is replaced or deleted.
     */
    public static final String EVALUATION_PANEL_BULK_CREATED        = "EVALUATION_PANEL_BULK_CREATED";
    public static final String EVALUATION_PANEL_EVALUATOR_ASSIGNED  = "EVALUATION_PANEL_EVALUATOR_ASSIGNED";
    public static final String EVALUATION_PANEL_EVALUATOR_WITHDRAWN = "EVALUATION_PANEL_EVALUATOR_WITHDRAWN";
    public static final String EVALUATION_PANEL_ROSTER_LOCKED       = "EVALUATION_PANEL_ROSTER_LOCKED";
    public static final String EVALUATION_PANEL_EVALUATOR_COMPLETED = "EVALUATION_PANEL_EVALUATOR_COMPLETED";
    public static final String EVALUATION_PANEL_AVERAGED           = "EVALUATION_PANEL_AVERAGED";
    public static final String EVALUATION_PANEL_SUBMITTED_TO_CEO   = "EVALUATION_PANEL_SUBMITTED_TO_CEO";
    public static final String EVALUATION_PANEL_APPROVED           = "EVALUATION_PANEL_APPROVED";
    public static final String EVALUATION_PANEL_REOPENED          = "EVALUATION_PANEL_REOPENED";
    /**
     * Feature 2 — reopen an APPROVED panel to add an ADDITIONAL expert
     * ({@code APPROVED -> AWAITING_EVALUATIONS}). Distinct from the generic
     * {@link #EVALUATION_PANEL_REOPENED} (which covers SUBMITTED/AVERAGED reopen +
     * the CEO CHANGES_REQUESTED coupling) so SIEM can isolate the more sensitive
     * post-approval un-lock path: it flips prior CEO-locked contributing sheets
     * LOCKED->COMPLETE (editable in place — scores preserved, never deleted) under
     * the GUC-gated 043 carve-out, and adds the new expert seat atomically.
     * entity_id carries the panel_id; reason carries the added evaluator + the
     * justification.
     */
    public static final String EVALUATION_PANEL_REOPENED_FOR_EXPERT
            = "EVALUATION_PANEL_REOPENED_FOR_EXPERT";
    /**
     * Defect-2 BE — hard delete of a {@code COLLECTING}-only panel (mirrors
     * {@link #EVALUATION_DELETED}). Roster assignments cascade-removed; entity_id
     * carries the panel_id; before-snapshot only (afterJson null — row is gone).
     * A non-COLLECTING panel keeps its history and uses
     * {@link #EVALUATION_PANEL_ARCHIVED}.
     */
    public static final String EVALUATION_PANEL_DELETED            = "EVALUATION_PANEL_DELETED";
    /**
     * Defect-2 BE — soft cancel of a working panel ({@code AWAITING_EVALUATIONS} /
     * {@code AVERAGED} / {@code SUBMITTED} → {@code ARCHIVED}; mirrors
     * {@link #EVALUATION_ARCHIVED}). Preserves the audit trail and frees the
     * active-panel uniqueness slot so a fresh panel can be created.
     */
    public static final String EVALUATION_PANEL_ARCHIVED           = "EVALUATION_PANEL_ARCHIVED";

    // Grade structure (Phase 6)
    public static final String GRADE_STRUCTURE_CREATED          = "GRADE_STRUCTURE_CREATED";
    public static final String GRADE_STRUCTURE_UPDATED          = "GRADE_STRUCTURE_UPDATED";
    public static final String GRADE_STRUCTURE_APPROVED         = "GRADE_STRUCTURE_APPROVED";
    public static final String GRADE_STRUCTURE_LOCKED           = "GRADE_STRUCTURE_LOCKED";
    public static final String GRADE_STRUCTURE_ARCHIVED         = "GRADE_STRUCTURE_ARCHIVED";
    public static final String GRADE_STRUCTURE_REVISION_CREATED = "GRADE_STRUCTURE_REVISION_CREATED";
    /** Hard delete of a DRAFT-only grade structure (BE-4). Non-DRAFT keeps ARCHIVE. */
    public static final String GRADE_STRUCTURE_DELETED          = "GRADE_STRUCTURE_DELETED";
    public static final String GRADE_CREATED                    = "GRADE_CREATED";
    public static final String GRADE_UPDATED                    = "GRADE_UPDATED";
    public static final String GRADE_REMOVED                    = "GRADE_REMOVED";
    /** DRAFT-only atomic reorder of a structure's grades by sort_order (BE-5). */
    public static final String GRADE_REORDERED                  = "GRADE_REORDERED";
    public static final String GRADE_BAND_UPSERTED              = "GRADE_BAND_UPSERTED";
    public static final String GRADE_BAND_REMOVED               = "GRADE_BAND_REMOVED";
    /** Auto-assignment on evaluation approval (Phase 6 integration). */
    public static final String GRADE_ASSIGNED                   = "GRADE_ASSIGNED";
    /** Re-assignment when calibration changes the total score → different band. */
    public static final String GRADE_REASSIGNED                 = "GRADE_REASSIGNED";

    // Grade structure templates (BE-7/8/9 — DB-backed tenant CUSTOM templates,
    // mirrors the methodology Epic E template lifecycle). A template is a frozen
    // deep copy of a structure's grades + bands, "saved for the company" and
    // reusable across the tenant's projects via POST /grade-structures/from-template.
    // Built-ins (GradeStructureTemplateRegistry: GRADE_14/GRADE_16/CUSTOM) are
    // global + read-only and emit NO template lifecycle audit.
    public static final String GRADE_TEMPLATE_CREATED           = "GRADE_TEMPLATE_CREATED";
    public static final String GRADE_TEMPLATE_UPDATED           = "GRADE_TEMPLATE_UPDATED";
    public static final String GRADE_TEMPLATE_ARCHIVED          = "GRADE_TEMPLATE_ARCHIVED";

    // Salary / sensitive
    public static final String SALARY_VIEWED   = "SALARY_VIEWED";
    public static final String SALARY_EXPORTED = "SALARY_EXPORTED";

    // Reports / files
    public static final String REPORT_EXPORTED       = "REPORT_EXPORTED";
    public static final String ATTACHMENT_DOWNLOADED = "ATTACHMENT_DOWNLOADED";

    // Security probing
    public static final String CROSS_TENANT_OR_PROJECT_ACCESS_ATTEMPT
            = "CROSS_TENANT_OR_PROJECT_ACCESS_ATTEMPT";
    /**
     * F-205 fail-closed: JWT {@code active_tenant_id} claim points to a tenant
     * the user is NOT a member of in {@code user_tenant_memberships}. Indicates
     * a forged or stale token; request is rejected with 403 before any business
     * logic runs.
     */
    public static final String TENANT_MEMBERSHIP_MISMATCH
            = "TENANT_MEMBERSHIP_MISMATCH";
    /**
     * E4-S3: a department-scoped caller (Department Manager / Evaluation
     * Committee Member) tried to READ or WRITE a row whose department is
     * outside their assigned subtree — denied by {@code DepartmentScopePolicy}
     * via the {@link uz.hrlab.grading.access.application.AbacGate}. The request
     * is rejected with a 404 (no existence reveal). This is the literal value
     * the gate has emitted since F-06; the constant is added so call sites and
     * SIEM dashboards reference it symbolically rather than by string literal.
     */
    public static final String ACCESS_DENIED_BY_ABAC
            = "ACCESS_DENIED_BY_ABAC";

    // Workflow (MVP 2 Phase 1)
    public static final String WORKFLOW_STAGE_ADVANCED = "WORKFLOW_STAGE_ADVANCED";
    public static final String WORKFLOW_RECOMPUTED     = "WORKFLOW_RECOMPUTED";

    // Approval (MVP 2 Phase 1)
    public static final String APPROVAL_REQUEST_CREATED         = "APPROVAL_REQUEST_CREATED";
    public static final String APPROVAL_REQUEST_CANCELLED       = "APPROVAL_REQUEST_CANCELLED";
    public static final String APPROVAL_STEP_APPROVED           = "APPROVAL_STEP_APPROVED";
    public static final String APPROVAL_STEP_REJECTED           = "APPROVAL_STEP_REJECTED";
    public static final String APPROVAL_STEP_CHANGES_REQUESTED  = "APPROVAL_STEP_CHANGES_REQUESTED";

    // Comment (MVP 2 Phase 1)
    public static final String COMMENT_CREATED = "COMMENT_CREATED";
    public static final String COMMENT_UPDATED = "COMMENT_UPDATED";
    public static final String COMMENT_DELETED = "COMMENT_DELETED";
    public static final String COMMENT_MENTION = "COMMENT_MENTION";

    // Integration — imports (MVP 2 Phase 2, integration-blueprint §16)
    public static final String IMPORT_UPLOADED          = "IMPORT_UPLOADED";
    /** Pre-persistence rejection at the HTTP boundary (integration-review F3). */
    public static final String IMPORT_UPLOAD_REJECTED   = "IMPORT_UPLOAD_REJECTED";
    public static final String IMPORT_SCAN_STARTED      = "IMPORT_SCAN_STARTED";
    public static final String IMPORT_SCAN_FAILED       = "IMPORT_SCAN_FAILED";
    public static final String IMPORT_PARSED            = "IMPORT_PARSED";
    public static final String IMPORT_VALIDATED         = "IMPORT_VALIDATED";
    public static final String IMPORT_VALIDATION_FAILED = "IMPORT_VALIDATION_FAILED";
    public static final String IMPORT_COLUMN_MAPPED     = "IMPORT_COLUMN_MAPPED";
    public static final String IMPORT_COMMITTED         = "IMPORT_COMMITTED";
    public static final String IMPORT_PARTIALLY_COMMITTED = "IMPORT_PARTIALLY_COMMITTED";
    public static final String IMPORT_FAILED            = "IMPORT_FAILED";
    public static final String IMPORT_CANCELLED         = "IMPORT_CANCELLED";

    // Integration — exports (MVP 2 Phase 2)
    public static final String EXPORT_REQUESTED   = "EXPORT_REQUESTED";
    public static final String EXPORT_GENERATING  = "EXPORT_GENERATING";
    public static final String EXPORT_GENERATED   = "EXPORT_GENERATED";
    public static final String EXPORT_DOWNLOADED  = "EXPORT_DOWNLOADED";
    public static final String EXPORT_EXPIRED     = "EXPORT_EXPIRED";
    public static final String EXPORT_FAILED      = "EXPORT_FAILED";
    public static final String EXPORT_CANCELLED   = "EXPORT_CANCELLED";

    // Generic file lifecycle (MVP 2 Phase 2)
    public static final String FILE_UPLOADED   = "FILE_UPLOADED";
    public static final String FILE_DOWNLOADED = "FILE_DOWNLOADED";
    public static final String FILE_DELETED    = "FILE_DELETED";

    // Reporting (MVP 2 Phase 3 — Report aggregate lifecycle, architecture §17)
    public static final String REPORT_REQUESTED  = "REPORT_REQUESTED";
    public static final String REPORT_QUEUED     = "REPORT_QUEUED";
    public static final String REPORT_GENERATING = "REPORT_GENERATING";
    public static final String REPORT_GENERATED  = "REPORT_GENERATED";
    public static final String REPORT_FAILED     = "REPORT_FAILED";
    public static final String REPORT_DOWNLOADED = "REPORT_DOWNLOADED";
    public static final String REPORT_EXPIRED    = "REPORT_EXPIRED";
    public static final String REPORT_CANCELLED  = "REPORT_CANCELLED";
}
