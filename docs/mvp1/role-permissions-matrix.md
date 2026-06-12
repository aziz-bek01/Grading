# MVP 1 — Role × Permission Default Grant Matrix

**Product:** grading.hrlab.uz
**Owner:** hr-product-owner (with security-engineer review)
**Date:** 2026-05-23
**Status:** Authoritative source for `seeds/004-default-role-permissions.yaml`
**Closes finding:** security review F-06 (`docs/mvp1/reviews/phase0-1-security-review.md` §11)
**Companion:** `docs/mvp1/01-product-prd.md` §7 (Permissions Matrix) — this document narrows the PRD's 11×16×12 action matrix into the **34 atomic permission codes** that actually exist in `seeds/001-default-permissions.yaml`.

---

## 1. Purpose

The Phase 0+1 security review (F-06) flagged that `role_permissions` is empty: no role currently grants any permission. This document defines the **default least-privilege grant** for every (role, permission) pair so the Liquibase seed `004-default-role-permissions.yaml` can be authored deterministically.

Hard rules encoded here (non-negotiable):

1. **SALARY_VIEW, SALARY_EDIT, SALARY_EXPORT, SALARY_SCENARIO_RUN** are granted to **no role** by default in MVP 1. They are individually granted to specific users per the salary blueprint (`user_tenant_memberships.salary_data_permission` boolean + per-user permission grant). Even HRLab Super Admin does not get salary permissions by default — this is separation-of-duty.
2. **AUDIT_READ** is granted only to **HRLab Super Admin** (cross-tenant) and **External Auditor** (tenant-scoped via ABAC).
3. **TENANT_CREATE** is granted only to **HRLab Super Admin**.
4. **USER_ACCESS_MANAGE** is granted to **HRLab Super Admin** and **Client Company Admin** (tenant-scoped via ABAC).
5. Frontend permission hiding never replaces backend enforcement. Every grant in this matrix is enforced server-side by `PermissionService.has(code)` + ABAC policy.

ABAC scoping is **layered on top** of RBAC: a role getting `POSITION_READ` here does not automatically see all positions in all tenants — `TenantContextFilter` plus the relevant `TenantAwarePolicy` further narrow access to the active tenant, project, and (where applicable) department scope.

---

## 2. Permission catalogue (34 codes)

| # | Code | Resource | Action | MVP |
|---|------|----------|--------|-----|
| 1 | `TENANT_READ` | TENANT | READ | 1 |
| 2 | `TENANT_CREATE` | TENANT | CREATE | 1 |
| 3 | `TENANT_EDIT` | TENANT | EDIT | 1 |
| 4 | `PROJECT_READ` | PROJECT | READ | 1 |
| 5 | `PROJECT_CREATE` | PROJECT | CREATE | 1 |
| 6 | `PROJECT_EDIT` | PROJECT | EDIT | 1 |
| 7 | `ORG_READ` | ORGANIZATION | READ | 1 |
| 8 | `ORG_EDIT` | ORGANIZATION | EDIT | 1 |
| 9 | `POSITION_READ` | POSITION | READ | 1 |
| 10 | `POSITION_CREATE` | POSITION | CREATE | 1 |
| 11 | `POSITION_EDIT` | POSITION | EDIT | 1 |
| 12 | `JOB_PROFILE_READ` | JOB_PROFILE | READ | 1 |
| 13 | `JOB_PROFILE_EDIT` | JOB_PROFILE | EDIT | 1 |
| 14 | `METHODOLOGY_READ` | METHODOLOGY | READ | 1 |
| 15 | `METHODOLOGY_EDIT` | METHODOLOGY | EDIT | 1 |
| 16 | `METHODOLOGY_APPROVE` | METHODOLOGY | APPROVE | 1 |
| 17 | `METHODOLOGY_LOCK` | METHODOLOGY | LOCK | 1 |
| 18 | `EVALUATION_READ` | EVALUATION | READ | 1 |
| 19 | `EVALUATION_EDIT` | EVALUATION | EDIT | 1 |
| 20 | `EVALUATION_APPROVE` | EVALUATION | APPROVE | 1 |
| 21 | `GRADE_READ` | GRADE | READ | 1 |
| 22 | `GRADE_EDIT` | GRADE | EDIT | 1 |
| 23 | `SALARY_VIEW` | SALARY | VIEW | 3 (foundation only) |
| 24 | `SALARY_EDIT` | SALARY | EDIT | 3 (foundation only) |
| 25 | `SALARY_EXPORT` | SALARY | EXPORT | 3 (foundation only) |
| 26 | `SALARY_SCENARIO_RUN` | SALARY | SCENARIO | 3 (foundation only) |
| 27 | `REPORT_READ` | REPORT | READ | 1 |
| 28 | `REPORT_CREATE` | REPORT | CREATE | 1 (foundation) / 2 |
| 29 | `REPORT_EXPORT` | REPORT | EXPORT | 2 |
| 30 | `AUDIT_READ` | AUDIT | READ | 1 |
| 31 | `USER_ACCESS_MANAGE` | USER | MANAGE | 1 |
| 32 | `FILE_UPLOAD` | FILE | UPLOAD | 2 |
| 33 | `FILE_DOWNLOAD` | FILE | DOWNLOAD | 2 |
| 34 | `AI_ASSIST_USE` | AI | USE | 4 |

---

## 3. Role catalogue (11 roles)

| Code | Name | Scope |
|------|------|-------|
| `HRLAB_SUPER_ADMIN` | HRLab Super Admin | PLATFORM |
| `HRLAB_PROJECT_MANAGER` | HRLab Project Manager | PLATFORM |
| `HRLAB_CONSULTANT` | HRLab Consultant | PLATFORM |
| `HRLAB_ANALYST` | HRLab Analyst | PLATFORM |
| `CLIENT_COMPANY_ADMIN` | Client Company Admin | TENANT |
| `CLIENT_HR_DIRECTOR` | Client HR Director | TENANT |
| `CLIENT_HR_SPECIALIST` | Client HR Specialist | TENANT |
| `EVALUATION_COMMITTEE_MEMBER` | Evaluation Committee Member | TENANT |
| `DEPARTMENT_MANAGER` | Department Manager | TENANT |
| `VIEWER` | Viewer (read-only) | TENANT |
| `EXTERNAL_AUDITOR` | External Auditor | TENANT |

---

## 4. Master matrix (11 roles × 34 permissions)

Legend: `Y` = granted by default; `.` = not granted by default. ABAC scoping (tenant / project / department) is enforced separately and noted in §6.

| Permission | SuperAdmin | PM | Consultant | Analyst | ClientAdmin | HRDir | HRSpec | Committee | DeptMgr | Viewer | Auditor |
|---|---|---|---|---|---|---|---|---|---|---|---|
| TENANT_READ           | Y | Y | . | . | Y | . | . | . | . | . | . |
| TENANT_CREATE         | Y | . | . | . | . | . | . | . | . | . | . |
| TENANT_EDIT           | Y | . | . | . | . | . | . | . | . | . | . |
| PROJECT_READ          | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y |
| PROJECT_CREATE        | Y | Y | . | . | . | . | . | . | . | . | . |
| PROJECT_EDIT          | Y | Y | . | . | . | . | . | . | . | . | . |
| ORG_READ              | Y | Y | Y | Y | Y | Y | Y | . | Y | Y | Y |
| ORG_EDIT              | Y | Y | Y | Y | . | . | . | . | . | . | . |
| POSITION_READ         | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y |
| POSITION_CREATE       | Y | Y | Y | Y | . | . | Y | . | . | . | . |
| POSITION_EDIT         | Y | Y | Y | Y | . | . | Y | . | . | . | . |
| JOB_PROFILE_READ      | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y |
| JOB_PROFILE_EDIT      | Y | Y | Y | Y | . | . | Y | . | . | . | . |
| METHODOLOGY_READ      | Y | Y | Y | Y | Y | Y | Y | Y | . | Y | Y |
| METHODOLOGY_EDIT      | Y | Y | Y | . | . | . | . | . | . | . | . |
| METHODOLOGY_APPROVE   | Y | Y | . | . | . | . | . | . | . | . | . |
| METHODOLOGY_LOCK      | Y | Y | . | . | . | . | . | . | . | . | . |
| EVALUATION_READ       | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y |
| EVALUATION_EDIT       | Y | Y | Y | . | . | . | . | Y | . | . | . |
| EVALUATION_APPROVE    | Y | Y | . | . | . | Y | . | . | . | . | . |
| GRADE_READ            | Y | Y | Y | Y | Y | Y | Y | . | Y | Y | Y |
| GRADE_EDIT            | Y | Y | . | . | . | . | . | . | . | . | . |
| **SALARY_VIEW**       | . | . | . | . | . | . | . | . | . | . | . |
| **SALARY_EDIT**       | . | . | . | . | . | . | . | . | . | . | . |
| **SALARY_EXPORT**     | . | . | . | . | . | . | . | . | . | . | . |
| **SALARY_SCENARIO_RUN** | . | . | . | . | . | . | . | . | . | . | . |
| REPORT_READ           | Y | Y | Y | Y | Y | Y | . | . | . | Y | Y |
| REPORT_CREATE         | Y | Y | . | Y | . | . | . | . | . | . | . |
| REPORT_EXPORT         | Y | Y | . | . | . | . | . | . | . | . | . |
| AUDIT_READ            | Y | . | . | . | . | . | . | . | . | . | Y |
| USER_ACCESS_MANAGE    | Y | . | . | . | Y | . | . | . | . | . | . |
| FILE_UPLOAD           | Y | Y | Y | Y | . | . | Y | . | . | . | . |
| FILE_DOWNLOAD         | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y |
| AI_ASSIST_USE         | . | . | . | . | . | . | . | . | . | . | . |

**Counts per role (granted permissions):**

| Role | Granted |
|---|---|
| HRLAB_SUPER_ADMIN | 65 (full current catalogue minus 7 carve-outs — see §5.1 and §4a) |
| HRLAB_PROJECT_MANAGER | 25 (MVP 1 baseline; widened by per-module MVP 2 seeds) |
| HRLAB_CONSULTANT | 16 |
| HRLAB_ANALYST | 15 |
| CLIENT_COMPANY_ADMIN | 11 |
| CLIENT_HR_DIRECTOR | 10 |
| CLIENT_HR_SPECIALIST | 12 |
| EVALUATION_COMMITTEE_MEMBER | 7 |
| DEPARTMENT_MANAGER | 7 |
| VIEWER | 9 |
| EXTERNAL_AUDITOR | 10 |
| **Total grants** | **151** |

**SALARY_* default grants:** `0` across all roles (mandatory).
**AUDIT_READ default grants:** `2` (HRLAB_SUPER_ADMIN, EXTERNAL_AUDITOR).
**AI_ASSIST_USE default grants:** `0` (MVP 4 — not yet wired).

> The §4 master matrix above is the **MVP 1 baseline snapshot (34 atomic codes,
> 151 grants)**. The catalogue has since grown across MVP 1 Phase 3–6 and MVP 2
> to **72 atomic codes**. §4a below records the current full catalogue and the
> authoritative HRLAB_SUPER_ADMIN grant after that growth.

---

## 4a. Current full catalogue (72 codes) and authoritative HRLAB_SUPER_ADMIN grant

The permission catalogue is seeded across the following Liquibase files:

| Seed file | Codes added |
|---|---|
| `seeds/001-default-permissions.yaml` | 34 (MVP 1 baseline) |
| `seeds/004` (user-management catalogue block) | +9 (`USER_LIST/VIEW/INVITE/UPDATE/MEMBERSHIP_MANAGE/ROLE_ASSIGN/ROLE_ASSIGN_HRLAB/SALARY_PERMISSION_TOGGLE`, `AUDIT_VIEW`) |
| `tenant-schema/007` | +3 (`JOB_PROFILE_APPROVE`, `JOB_ANALYSIS_READ`, `JOB_ANALYSIS_EDIT`) |
| `tenant-schema/013` | +1 (`METHODOLOGY_CREATE`) |
| `tenant-schema/016` | +2 (`CALIBRATION_EDIT`, `EVALUATION_LOCK`) |
| `tenant-schema/019` | +2 (`GRADE_STRUCTURE_APPROVE`, `GRADE_STRUCTURE_LOCK`) |
| `tenant-schema/023` | +9 (`WORKFLOW_READ/EDIT`, `APPROVAL_REQUEST_CREATE/DECIDE/CANCEL`, `COMMENT_READ/CREATE/EDIT/DELETE`) |
| `tenant-schema/027` | +9 (`ORG_IMPORT`, `POSITION_IMPORT`, `METHODOLOGY_IMPORT`, `GRADE_IMPORT`, `PAYROLL_IMPORT`, `IMPORT_READ`, `IMPORT_CANCEL`, `EXPORT_READ`, `EXPORT_REQUEST`) |
| `tenant-schema/038` | +3 (`EVALUATION_PANEL_MANAGE`, `EVALUATION_PANEL_APPROVE`, `CAMPAIGN_RESULTS_VIEW`) |
| **Total** | **72** |

> Note: `tenant-schema/029` adds **role grants** for the already-existing
> `REPORT_*` codes; it does not add new catalogue codes. The frontend constant
> map (`frontend/src/shared/types/permissions.ts`) also lists a handful of
> **aspirational / OR-fallback** codes that are NOT seeded as catalogue rows and
> are therefore intentionally excluded from the 72-code catalogue and from any
> role grant: `TENANT_ARCHIVE` (the archive endpoint enforces `TENANT_EDIT`),
> `CLIENT_LIST/CLIENT_VIEW/CLIENT_UPDATE` (each `@PreAuthorize` OR-falls-back to
> `TENANT_READ`/`TENANT_EDIT`), `AUDIT_READ_CROSS_TENANT`, `EXPORT_GENERAL`,
> `GRADE_APPROVE`, `APPROVAL_STEP_APPROVE/REJECT`, `EVALUATION_ADJUST`. These are
> tracked as FE-cleanup items; they do not affect backend authority.

**HRLAB_SUPER_ADMIN holds 65 of the 72 codes** — i.e. **every operational and
control-plane code** (tenant CRUD, project lifecycle, org/position/profile/
job-analysis, methodology incl. create/import/approve/lock, evaluation incl.
calibrate/lock, panels + campaign results, grade structure incl. approve/lock,
workflow, approval-request decide, comments, imports/exports, all `USER_*`
admin codes incl. `USER_ROLE_ASSIGN_HRLAB`, `AUDIT_READ` + `AUDIT_VIEW`,
reports incl. export, file upload/download).

**Explicitly NOT granted to HRLAB_SUPER_ADMIN (7 carve-outs):**

| Code | Reason withheld |
|---|---|
| `SALARY_VIEW` | Separation of duty — salary is per-user only (§1 rule #1). |
| `SALARY_EDIT` | Same — per-user only. |
| `SALARY_EXPORT` | Same — per-user only. |
| `SALARY_SCENARIO_RUN` | Same — per-user only (MVP 3). |
| `AI_ASSIST_USE` | MVP 4 module; granted to no role yet. |
| `PAYROLL_IMPORT` | MVP 4 payroll connector; granted to no role yet. |
| `USER_SALARY_PERMISSION_TOGGLE` | HRLAB_* roles must NEVER hold it (architecture §8.4); only `CLIENT_COMPANY_ADMIN` holds it. |

**Enforcement / corrective seed.** Because the per-module grants for newer codes
live in `tenant-schema/` changesets (owned by the per-tenant provisioning
bundle), environments where those grants did not land in the control-plane
`public.role_permissions` collapsed HRLAB_SUPER_ADMIN back toward the stale
29-code MVP 1 baseline — surfacing as the **"super admin sees everything
locked/hidden"** defect (neither backend `PermissionService` nor frontend
`permissionUtils` has a super-admin wildcard; authority == exactly the granted
union). The forward-only corrective seed
`seeds/005-superadmin-permission-backfill.yaml` grants HRLAB_SUPER_ADMIN every
catalogue code except the 7 carve-outs above (idempotent `ON CONFLICT DO
NOTHING`), and carries a post-condition invariant `DO`-block asserting both the
carve-outs and full coverage. This file is the authoritative grant going
forward; future catalogue additions are auto-included by its `NOT IN (carve-outs)`
predicate unless a new carve-out is explicitly added to that list and to the
invariant block.

---

## 5. Per-role rationale

### 5.1 HRLAB_SUPER_ADMIN — 65 grants (full current catalogue minus 7 carve-outs)

> The original MVP 1 baseline was 29 grants against a 34-code catalogue. After
> the MVP 1 Phase 3–6 and MVP 2 catalogue growth to 72 codes, HRLAB_SUPER_ADMIN
> holds **65** of them. See §4a for the authoritative grant, the seed
> provenance, and the corrective `seeds/005-superadmin-permission-backfill.yaml`.

Receives every business-and-control-plane permission required to operate the platform across tenants: tenant CRUD, project workflow, all business modules (org/position/profile/job-analysis/methodology/evaluation/panels/grade incl. approve+lock), workflow + approval-request decide + comments, imports/exports, all `USER_*` admin codes (incl. `USER_ROLE_ASSIGN_HRLAB`), audit read + view, user access management, reports (incl. export), file ops.

**Explicitly NOT granted (7 carve-outs):** `SALARY_VIEW`, `SALARY_EDIT`, `SALARY_EXPORT`, `SALARY_SCENARIO_RUN` — salary access is per-user, never role-default (separation-of-duty rule from security blueprint §8 and PRD §7.1); `AI_ASSIST_USE` and `PAYROLL_IMPORT` — MVP 4, granted to no role yet; `USER_SALARY_PERMISSION_TOGGLE` — HRLAB_* roles must never hold it (architecture §8.4). See §4a table.

### 5.2 HRLAB_PROJECT_MANAGER — 25 grants

Owns project delivery: project CRUD, full methodology lifecycle (read/edit/approve/lock), evaluation read/edit/approve, grade edit, all business module reads, audit not granted at platform level (audit is for Super Admin + External Auditor only), report create + export.

**Rationale:** PM drives the workflow but cannot manage tenants (no `TENANT_CREATE/EDIT`), cannot manage users (no `USER_ACCESS_MANAGE`), and cannot view audit logs (that is forensic / oversight, separation from delivery).

### 5.3 HRLAB_CONSULTANT — 16 grants

Hands-on methodology and evaluation worker on assigned tenants: project/org/position/profile read + edit, methodology read + edit (cannot approve — PM-only), evaluation read + edit (cannot approve — PM or Committee + HR Director), grade read, report read, file upload/download.

**Rationale:** consultants build content, never approve. They are scoped via ABAC to **assigned tenants only** (see §6).

### 5.4 HRLAB_ANALYST — 15 grants

Import-and-analysis role: org/position/profile read + edit (for data prep), evaluation read, grade read, report read + create. No methodology editing, no approvals.

### 5.5 CLIENT_COMPANY_ADMIN — 11 grants

Manages users within own tenant: `USER_ACCESS_MANAGE` + read on tenant, project, org, position, profile, methodology, evaluation, grade, report. Cannot create projects (that is HRLab side). Cannot edit business data.

**Rationale:** an internal admin for the client company; their power is user/role lifecycle within their tenant. They are explicitly **not given AUDIT_READ** (client admins should not see security audit of their own tenant — that would let them clean up their own tracks). External Auditor and HRLab Super Admin own audit.

### 5.6 CLIENT_HR_DIRECTOR — 10 grants

Owner of business outcomes for own tenant: read across all business modules, plus `EVALUATION_APPROVE` (approves the final position evaluations in their company). Does **not** edit methodology (HRLab is the methodology authority), does not approve methodology, does not edit positions/profiles directly (handled by HR Specialist + Consultant), does not get audit.

### 5.7 CLIENT_HR_SPECIALIST — 12 grants

Operational HR work: position + profile + file upload/download, evaluation read. No approvals, no methodology editing, no grade editing, no salary, no audit.

### 5.8 EVALUATION_COMMITTEE_MEMBER — 7 grants

Project-scoped scorer: project/position/profile/methodology read, evaluation read + edit (factor scoring + comments), file download. No approvals (that is HR Director or PM), no methodology editing, no grade editing.

**Rationale:** committee members evaluate but never approve their own work. Scoped to assigned projects via ABAC.

### 5.9 DEPARTMENT_MANAGER — 7 grants

Department-scoped consumer: read on project, org, position, profile, evaluation, grade, file download. **No create/edit/approve anywhere.** Department scoping enforced by ABAC `department_scope` claim, not role.

### 5.10 VIEWER — 9 grants

Pure read-only role: project, org, position, profile, methodology, evaluation, grade, report, file download. No edit, no approve, no salary, no audit, no user management.

### 5.11 EXTERNAL_AUDITOR — 10 grants

Read-everything + audit log on own tenant: project, org, position, profile, methodology, evaluation, grade, report, audit, file download. **No edit, no create, no approve, no salary, no export.** Tenant-scoped via ABAC.

---

## 6. ABAC scoping notes (NOT enforced by role grant alone)

Several permissions in the matrix are **necessary but not sufficient** — they must be combined with an ABAC policy. The role grant says "this role can perform this action class"; the ABAC policy says "but only within these tenants / projects / departments".

| Role | Permission | ABAC policy required | Source of truth |
|---|---|---|---|
| HRLAB_CONSULTANT | all granted permissions | Tenant assignment via `user_tenant_memberships` + active project membership | JWT `active_project_ids` claim |
| HRLAB_ANALYST | all granted permissions | Tenant assignment via `user_tenant_memberships` | JWT `active_tenant_id` |
| HRLAB_PROJECT_MANAGER | all granted permissions | Tenant assignment via `user_tenant_memberships` | JWT `active_tenant_id` + `active_project_ids` |
| CLIENT_COMPANY_ADMIN | USER_ACCESS_MANAGE | Restricted to own tenant; cannot assign HRLab-side roles | `roles.scope='TENANT'` filter + tenant match |
| CLIENT_HR_DIRECTOR | EVALUATION_APPROVE | Restricted to own tenant's projects | Tenant filter |
| EVALUATION_COMMITTEE_MEMBER | EVALUATION_EDIT | Restricted to assigned projects only | `user_project_memberships` (Phase 2) — until then, tenant filter |
| DEPARTMENT_MANAGER | POSITION_READ / JOB_PROFILE_READ / EVALUATION_READ / GRADE_READ / ORG_READ | Restricted to own department subtree | JWT `department_scope` claim |
| VIEWER | all reads | Restricted to "approved" entities only (no drafts) and to tenants the viewer is assigned to | Tenant filter + lifecycle status filter |
| EXTERNAL_AUDITOR | AUDIT_READ + all reads | Tenant-scoped; never cross-tenant; no export | Tenant filter + export deny |

**Open ABAC dependencies flagged for backend-engineer:**

1. **DepartmentScopePolicy** — needs to read `department_scope` from JWT and filter every department-scoped query (`Department Manager` reads). Not yet implemented. Required before any department-scoped business endpoint ships.
2. **ConsultantTenantAssignmentPolicy** — needs to filter HRLab Consultant access by their `user_tenant_memberships` rows. Mechanism exists (`UserTenantMembershipRepository.findByUserIdAndTenantId`); policy class to wire this into `AbacPolicy<T>` is not yet written.
3. **ProjectMembershipPolicy** — needs `user_project_memberships` table (not yet shipped; Phase 2). Until then, Committee Member is tenant-scoped, not project-scoped.
4. **ApprovedEntityFilterPolicy** — Viewer should only see entities with lifecycle status `APPROVED` (no drafts). Lifecycle column to be added per business entity.

These are captured as backend tasks; matrix in §4 is the role-level (RBAC) input. ABAC narrows further; it never widens.

---

## 7. What is explicitly NOT granted by default (security checklist)

1. `SALARY_VIEW`, `SALARY_EDIT`, `SALARY_EXPORT`, `SALARY_SCENARIO_RUN` → **0 roles**. Per-user only via blueprint §8.
2. `AUDIT_READ` → only `HRLAB_SUPER_ADMIN` and `EXTERNAL_AUDITOR`. Not given to PM, Consultant, Analyst, Client Admin, HR Director, HR Specialist, Committee, Dept Mgr, Viewer.
3. `TENANT_CREATE`, `TENANT_EDIT` → only `HRLAB_SUPER_ADMIN`. No other role can create or modify tenants.
4. `USER_ACCESS_MANAGE` → only `HRLAB_SUPER_ADMIN` (cross-tenant) and `CLIENT_COMPANY_ADMIN` (own tenant only).
5. `METHODOLOGY_APPROVE`, `METHODOLOGY_LOCK` → only `HRLAB_SUPER_ADMIN` and `HRLAB_PROJECT_MANAGER`. Methodology is HRLab IP — client roles cannot approve.
6. `EVALUATION_APPROVE` → only `HRLAB_SUPER_ADMIN`, `HRLAB_PROJECT_MANAGER`, `CLIENT_HR_DIRECTOR`. Consultants and committee members never approve their own work.
7. `GRADE_EDIT` → only `HRLAB_SUPER_ADMIN` and `HRLAB_PROJECT_MANAGER`. Grade bands are HRLab-controlled deliverables.
8. `AI_ASSIST_USE` → **0 roles** (MVP 4 module not yet wired).
9. `REPORT_EXPORT` → only `HRLAB_SUPER_ADMIN` and `HRLAB_PROJECT_MANAGER`. **Salary-bearing reports** further require salary permission per individual user.

---

## 8. Test acceptance criteria

The Liquibase seed `004-default-role-permissions.yaml` is considered correct only if all of these tests pass (`RolePermissionMatrixTest` — see backend-engineer task list):

| Test | Expectation |
|---|---|
| `salary_grants_count_is_zero` | Count of rows in `role_permissions` where permission code starts with `SALARY_` is 0. |
| `audit_read_only_two_roles` | Exactly two roles join `role_permissions` to `AUDIT_READ`: `HRLAB_SUPER_ADMIN` and `EXTERNAL_AUDITOR`. |
| `super_admin_has_full_catalogue_minus_carveouts` | After `seeds/005-superadmin-permission-backfill.yaml`, HRLAB_SUPER_ADMIN holds **every** `public.permissions` code EXCEPT the 7 carve-outs (`SALARY_VIEW/EDIT/EXPORT/SCENARIO_RUN`, `AI_ASSIST_USE`, `PAYROLL_IMPORT`, `USER_SALARY_PERMISSION_TOGGLE`). Equivalently: `COUNT(catalogue) - COUNT(super_admin grants) = 7` and the missing 7 are exactly that set. (Superseded the MVP 1 `super_admin_has_29_permissions` check.) |
| `tenant_create_only_super_admin` | Only HRLAB_SUPER_ADMIN has `TENANT_CREATE`. |
| `viewer_has_no_edit_permission` | VIEWER has no permission whose code ends in `_EDIT`, `_CREATE`, `_APPROVE`, `_LOCK`, or `_EXPORT`. |
| `external_auditor_no_edit` | EXTERNAL_AUDITOR has no permission ending in `_EDIT`, `_CREATE`, `_APPROVE`, `_LOCK`, `_EXPORT`, `_SCENARIO_RUN`. |
| `total_grant_count` | Total `role_permissions` rows = 151 at the MVP 1 baseline (`seeds/004` only). With the full MVP 1 Phase 3–6 + MVP 2 per-module seeds and the `seeds/005` super-admin backfill applied, the total is larger; assert per-role expectations (e.g. the super-admin row above) rather than a single global constant. |
| `client_company_admin_has_user_access_manage` | CLIENT_COMPANY_ADMIN has `USER_ACCESS_MANAGE`. |
| `committee_no_approve` | EVALUATION_COMMITTEE_MEMBER does not have `EVALUATION_APPROVE`. |
| `idempotent_seed` | Running the changeset twice does not duplicate rows (insert-on-conflict-do-nothing). |

---

## 9. Reconciliation with PRD §7.1

The PRD's master matrix expresses permissions as a 12-action vector per (role, module) cell. This document collapses to the 34 atomic permission codes that actually exist. Reconciliation notes:

* PRD uses several permission codes (e.g., `METHODOLOGY_CREATE`, `JOB_PROFILE_APPROVE`, `GRADE_APPROVE`, `EVALUATION_SUBMIT`, `AUDIT_READ_CROSS_TENANT`) that do **not** exist in the seed. Mapping applied:
  * "create" + "edit" PRD actions collapse to `*_EDIT` (since `*_CREATE` codes do not exist for most resources; `POSITION_CREATE` and `PROJECT_CREATE` are the exceptions and exist as separate codes).
  * `JOB_PROFILE_APPROVE` / `GRADE_APPROVE` / `EVALUATION_SUBMIT` → not separate codes in MVP 1; approval is gated by `*_EDIT` plus workflow state machine. To be split out in MVP 2 when workflow ships.
  * `AUDIT_READ_CROSS_TENANT` is **not** a separate code — cross-tenant audit is permitted only when the active tenant context is null, which is allowed only for HRLAB_SUPER_ADMIN via a dedicated `AuditTenantScopePolicy`. Single `AUDIT_READ` code is sufficient for MVP 1.
* PRD §7.1 shows HRLab Super Admin with `view-audit = Y` on every module — this maps to a single `AUDIT_READ` grant (the per-module breakdown is enforced by filter parameters, not by per-module permission codes).
* PRD §7.1 shows export uniformly `N` for MVP 1; this matrix grants `REPORT_EXPORT` to Super Admin and PM as forward-compatibility for MVP 2, gated at the controller level by an MVP-flag until MVP 2 ships. This is the only intentional widening over the PRD §7.1 text and is captured in §10 below.

---

## 10. Clarifications / addenda to PRD §7.1

The following clarifications resolve gaps between PRD §7.1 prose and the atomic-permission seed:

1. **`REPORT_EXPORT` grant to Super Admin + PM** — PRD §7.1 marked export `N` everywhere because MVP 1 does not yet ship a report-export endpoint. The seed pre-grants this permission to HRLAB_SUPER_ADMIN and HRLAB_PROJECT_MANAGER. The export endpoint, when it ships in MVP 2, will additionally require an MVP-2 feature flag and will continue to deny salary-bearing exports without separate `SALARY_EXPORT` (which remains unassigned).
2. **`POSITION_CREATE` and `POSITION_EDIT` are split** in the catalogue. PRD §7.1 treats them as one cell. Roles that get both: HRLAB_SUPER_ADMIN, HRLAB_PROJECT_MANAGER, HRLAB_CONSULTANT, HRLAB_ANALYST, CLIENT_HR_SPECIALIST. CLIENT_HR_DIRECTOR has neither (read + approve only).
3. **`PROJECT_CREATE` and `PROJECT_EDIT`** are HRLab-only (Super Admin + PM). PRD §7.1 already implies this — clarified here.
4. **AI_ASSIST_USE granted to nobody** by default — the AI module is MVP 4 and there is no live endpoint. Adding default grants now would be misleading.
5. **`FILE_UPLOAD` / `FILE_DOWNLOAD`** — MVP 2 module, but seeded for forward compatibility:
   * `FILE_DOWNLOAD`: every role except none — needed for evidence/document review even in read-only flows.
   * `FILE_UPLOAD`: HRLab side + Client HR Specialist (the operational data-entry role). Not granted to Client HR Director, Client Company Admin (administrative role), Committee, Dept Manager, Viewer, Auditor.

These are clarifications, not contradictions; the PRD §7.1 table remains the prose source of truth and is updated to reference this document.

---

## 11. Change control

Any change to this matrix requires:
1. PR review by **hr-product-owner** + **security-engineer**.
2. Updated `seeds/004-default-role-permissions.yaml` checked against `RolePermissionMatrixTest`.
3. Updated row in §8 (test acceptance criteria).
4. Audit event `ROLE_PERMISSION_DEFAULT_CHANGED` written by the migration (deferred to migration tooling — covered in F-04 follow-up).

— end of document —
