# MVP 1 — Product Requirements Document

**Product:** grading.hrlab.uz
**Owner:** HR Laboratories
**Document type:** Product Requirements Document (PRD)
**MVP scope:** MVP 1 — Core Grading Foundation
**Author:** hr-product-owner agent
**Version:** 1.0
**Date:** 2026-05-23
**Status:** Draft for review (designer, backend, frontend, QA, security, DevOps, DB architect)

---

## 1. Product Objective

### 1.1 What MVP 1 must deliver

MVP 1 must give HR Laboratories a secure, multi-tenant digital workspace where a consultant team can run the **first real grading project for a company-client** without using Excel as the system of record.

Concretely, MVP 1 must enable, end-to-end:

1. HRLab Super Admin creates a new **company-client tenant** and provisions an isolated workspace.
2. HRLab Project Manager creates a **grading project** inside that tenant and assigns consultants and client users.
3. Client HR Specialist (or HRLab Analyst) populates a minimal **organization structure** and **position catalog** by hand (no Excel import yet — that is MVP 2).
4. Client HR Specialist creates **job profiles** for each position (purpose, duties, requirements, working conditions).
5. HRLab Consultant configures a **methodology** (8-factor classic, 11-criteria extended, or custom), defines factors, levels, weights and points, and **approves and locks** the methodology version.
6. Committee Member / HRLab Consultant runs an **evaluation** for each position using the locked methodology version; the scoring engine deterministically computes a total score.
7. HR Director **approves** evaluations; the system **assigns a grade** to each position based on configured grade bands.
8. Every sensitive action is recorded in an **append-only audit trail** with hash chaining.
9. The full UI works in **4 languages**: `ru-RU`, `uz-Cyrl-UZ`, `uz-Latn-UZ`, `en-US`.

### 1.2 What MVP 1 explicitly does NOT deliver

See Section 14 (Out of Scope) for the canonical list. Headlines: no Excel/HRM import, no PDF/Word/Excel reports, no compensation/salary ranges, no AI assistant, no calibration sessions, no advanced approvals workflow, no comments/attachments, no dashboards beyond basic counters, no archive, no SSO federation with client IdPs.

### 1.3 Success definition

MVP 1 is successful if HR Laboratories can deliver one full pilot grading project for one company-client, in one language at minimum, with:

- Zero cross-tenant data exposure (verified by tenant isolation test suite).
- A locked, reproducible methodology that yields the same total score for the same factor-level selections on every re-run.
- A complete, hash-chained audit trail for every state-changing action.
- Grade assignment that matches the consultant's manual computation 100% across a verification sample of at least 20 positions.

---

## 2. Business Value for HR Laboratories

| Dimension | Value delivered by MVP 1 |
| --- | --- |
| **Consulting delivery** | Replaces Excel chaos with a structured, repeatable workspace per project. Same methodology terminology, same scoring math, every project. |
| **Tenant safety** | Allows HR Laboratories to run multiple company-client projects in parallel without risking cross-client data leaks. |
| **Methodology IP protection** | HRLab's accumulated methodology know-how lives in versioned, locked templates instead of consultant laptops. |
| **Audit defensibility** | If a client challenges a grade decision a year later, HRLab can replay exactly which factor levels were chosen, by whom, when, and on which methodology version. |
| **Sales enablement** | A demo-able, multilingual SaaS product (instead of an Excel file) — material uplift in proposal conversion for enterprise clients. |
| **Foundation for monetization** | Tenant + project + permission scaffolding required for subscription / project-tier / enterprise-tier pricing in later MVPs. |
| **Team productivity** | Consultant onboarding time drops because the workspace itself encodes the process; juniors cannot "skip" steps. |
| **Risk reduction** | Approved methodology cannot be silently edited mid-project; approved evaluations cannot be retroactively manipulated. |

### Non-goals for business value in MVP 1

- MVP 1 is **not** expected to win clients on dashboards or reports — those land in MVP 2/3.
- MVP 1 is **not** expected to generate compensation insights — that is MVP 3.
- MVP 1 is **not** expected to provide AI-driven productivity — that is MVP 4.

MVP 1 sells **trust and safety**, not analytics.

---

## 3. Target Users and Personas

Eleven personas span two organizations: **HR Laboratories (the platform owner)** and **the company-client (the customer of a grading project)**.

For each persona we define: role context, goals in MVP 1, pains MVP 1 must solve, main screens they use, MVP 1 permissions summary, audit-sensitive actions, success metrics.

### 3.1 HRLab Super Admin

| Field | Value |
| --- | --- |
| **Belongs to** | HR Laboratories |
| **Scope** | All tenants (platform-wide), control plane |
| **Goals in MVP 1** | Provision new company-client tenants; assign HRLab staff to tenants/projects; monitor platform health and audit |
| **Pains MVP 1 solves** | No more shared Excel per client; no more "send me the file" requests; tenant boundaries enforced |
| **Main screens** | HRLab admin dashboard, Company-client list, Tenant settings, User & access management, Audit log |
| **MVP 1 permissions** | `TENANT_CREATE`, `TENANT_READ`, `TENANT_EDIT`, `USER_CREATE`, `USER_ASSIGN_ROLE`, `AUDIT_READ` (cross-tenant) |
| **Audit-sensitive actions** | Tenant create/suspend, role assignment, cross-tenant audit query |
| **Success metric** | Time to provision a new company-client tenant under 10 minutes |
| **Risks** | Can grant excessive permissions — guard with 4-eyes principle on critical roles in MVP 2 |

### 3.2 HRLab Project Manager (PM)

| Field | Value |
| --- | --- |
| **Belongs to** | HR Laboratories |
| **Scope** | Projects assigned to them (one or many tenants) |
| **Goals in MVP 1** | Create a grading project inside a tenant, set methodology, manage participants, track progress |
| **Pains MVP 1 solves** | No more scattered project files; clear ownership and status per project |
| **Main screens** | Project list, Project workspace, Methodology builder, Evaluation matrix, Audit log (project scope) |
| **MVP 1 permissions** | `PROJECT_CREATE`, `PROJECT_EDIT`, `PROJECT_READ`, `METHODOLOGY_CREATE`, `METHODOLOGY_VERSION_CREATE`, `METHODOLOGY_APPROVE`, `EVALUATION_READ`, `AUDIT_READ` (project scope) |
| **Audit-sensitive actions** | Project create, methodology approve/lock, participant assignment |
| **Success metric** | Project setup (tenant ready → methodology approved) under 1 working day |
| **Risks** | PM may approve methodology prematurely — UI must show "preview vs official" clearly |

### 3.3 HRLab Consultant

| Field | Value |
| --- | --- |
| **Belongs to** | HR Laboratories |
| **Scope** | Projects they are assigned to |
| **Goals in MVP 1** | Configure methodology (factors, levels, weights), run evaluations, ensure consistency |
| **Pains MVP 1 solves** | Methodology no longer lives in a personal Excel; same logic across clients |
| **Main screens** | Methodology builder, Factor & level editor, Evaluation matrix, Job profile (read), Audit log (project scope) |
| **MVP 1 permissions** | `METHODOLOGY_CREATE`, `METHODOLOGY_EDIT` (draft only), `EVALUATION_CREATE`, `EVALUATION_EDIT` (draft), `EVALUATION_SUBMIT`, `JOB_PROFILE_READ`, `POSITION_READ` |
| **Audit-sensitive actions** | Factor add/edit, level points change, evaluation submit, manual score adjustment with reason |
| **Success metric** | Configure a complete 8-factor methodology in under 2 hours |
| **Risks** | May try to edit approved methodology — system must block and force new version |

### 3.4 HRLab Analyst

| Field | Value |
| --- | --- |
| **Belongs to** | HR Laboratories |
| **Scope** | Projects they are assigned to |
| **Goals in MVP 1** | Populate organization structure and position catalog, support consultants, review data quality |
| **Pains MVP 1 solves** | Manual structured data entry replaces ad-hoc Excel |
| **Main screens** | Organization tree, Position catalog, Job profile (edit), Project workspace |
| **MVP 1 permissions** | `ORG_CREATE`, `ORG_EDIT`, `ORG_READ`, `POSITION_CREATE`, `POSITION_EDIT`, `POSITION_READ`, `JOB_PROFILE_CREATE`, `JOB_PROFILE_EDIT`, `JOB_PROFILE_READ` |
| **Audit-sensitive actions** | Org unit create/edit, position create/edit, profile edit |
| **Success metric** | Enter 100 positions with profiles in under 1 working day |
| **Risks** | Cannot see salary data; in MVP 1 there is no salary, so risk is moot. Risk: data entry fatigue — UX must be efficient |

### 3.5 Client Company Admin

| Field | Value |
| --- | --- |
| **Belongs to** | Company-client |
| **Scope** | Their own tenant only |
| **Goals in MVP 1** | Manage users within their company, assign internal roles, audit internal actions |
| **Pains MVP 1 solves** | Self-service user management without contacting HRLab support for every change |
| **Main screens** | User & access management (tenant-scoped), Audit log (tenant scope, no salary access) |
| **MVP 1 permissions** | `USER_CREATE` (tenant), `USER_EDIT` (tenant), `USER_ASSIGN_ROLE` (tenant, limited to client roles only), `AUDIT_READ` (tenant) |
| **Audit-sensitive actions** | Adding a user, assigning a role, deactivating a user |
| **Success metric** | Add and onboard 10 client users in under 30 minutes |
| **Risks** | Cannot create HRLab-side roles for their users — backend must enforce role catalogue per scope |

### 3.6 Client HR Director

| Field | Value |
| --- | --- |
| **Belongs to** | Company-client |
| **Scope** | Their own tenant; all departments |
| **Goals in MVP 1** | Approve job profiles, approve evaluations, approve grade assignment for their company |
| **Pains MVP 1 solves** | A formal approval record exists; no more "did we ever approve this?" disputes |
| **Main screens** | Project workspace, Job profile (review), Evaluation matrix (review), Grade pyramid (view), Audit log |
| **MVP 1 permissions** | `JOB_PROFILE_READ`, `JOB_PROFILE_APPROVE`, `EVALUATION_READ`, `EVALUATION_APPROVE`, `GRADE_READ`, `GRADE_APPROVE`, `AUDIT_READ` (tenant) |
| **Audit-sensitive actions** | Approve profile, approve evaluation, approve grade |
| **Success metric** | Approve a batch of 50 evaluations in under 2 hours |
| **Risks** | May approve without reading — system must show evaluation summary and require explicit confirmation |

### 3.7 Client HR Specialist

| Field | Value |
| --- | --- |
| **Belongs to** | Company-client |
| **Scope** | Their own tenant; typically all departments or a defined set |
| **Goals in MVP 1** | Author job profiles, ensure data quality for their company's positions |
| **Pains MVP 1 solves** | Structured profile editor instead of Word documents per position |
| **Main screens** | Position catalog, Job profile editor, Project workspace |
| **MVP 1 permissions** | `POSITION_READ`, `JOB_PROFILE_CREATE`, `JOB_PROFILE_EDIT`, `JOB_PROFILE_READ`, `JOB_PROFILE_SUBMIT` |
| **Audit-sensitive actions** | Profile create, profile edit, profile submit for approval |
| **Success metric** | Author 30 job profiles in 3 days |
| **Risks** | May submit incomplete profile — validation must enforce required fields |

### 3.8 Evaluation Committee Member

| Field | Value |
| --- | --- |
| **Belongs to** | Company-client (or mixed HRLab + client) |
| **Scope** | Project they sit on |
| **Goals in MVP 1** | Select factor levels for positions, contribute to evaluations |
| **Pains MVP 1 solves** | Single source of truth for scoring; no parallel committee spreadsheets |
| **Main screens** | Evaluation matrix, Job profile (read) |
| **MVP 1 permissions** | `POSITION_READ`, `JOB_PROFILE_READ`, `EVALUATION_CREATE`, `EVALUATION_EDIT` (draft), `EVALUATION_SUBMIT` |
| **Audit-sensitive actions** | Score selection, score change with reason |
| **Success metric** | Evaluate 50 positions in one committee session |
| **Risks** | May change scores after submit — backend must lock submitted evaluations until rejection by an approver |

### 3.9 Department Manager

| Field | Value |
| --- | --- |
| **Belongs to** | Company-client |
| **Scope** | Their own department(s) only (ABAC enforced) |
| **Goals in MVP 1** | Review job profiles and evaluations for their department |
| **Pains MVP 1 solves** | Visibility into their team's grading status without exposing other departments |
| **Main screens** | Position catalog (filtered), Job profile (read), Evaluation (read) |
| **MVP 1 permissions** | `POSITION_READ` (department scope), `JOB_PROFILE_READ` (department scope), `EVALUATION_READ` (department scope) |
| **Audit-sensitive actions** | Read events on positions/profiles outside their department (must fail with audit) |
| **Success metric** | Can review all positions in their department in under 15 minutes |
| **Risks** | Curiosity-driven access attempts to other departments — must be blocked and audited |

### 3.10 Viewer / Read-only User

| Field | Value |
| --- | --- |
| **Belongs to** | Company-client or HRLab |
| **Scope** | Defined by assignment |
| **Goals in MVP 1** | Browse approved data for reference |
| **Pains MVP 1 solves** | Lightweight access without editing risk |
| **Main screens** | Project workspace (read), Position catalog (read), Job profile (read), Grade pyramid (read) |
| **MVP 1 permissions** | `*_READ` only |
| **Audit-sensitive actions** | None creating data; reads of sensitive entities still logged where defined |
| **Success metric** | N/A — passive role |
| **Risks** | Permission creep — must NOT be granted any `EDIT`/`APPROVE` permissions |

### 3.11 External Auditor

| Field | Value |
| --- | --- |
| **Belongs to** | Third-party auditor (external to both HRLab and the company-client) |
| **Scope** | One tenant, time-bound |
| **Goals in MVP 1** | Read audit logs, validate that approved methodology and evaluations are reproducible |
| **Pains MVP 1 solves** | A defensible audit trail exists without exporting raw DB dumps |
| **Main screens** | Audit log, Project workspace (read), Methodology (read approved versions), Evaluation (read approved) |
| **MVP 1 permissions** | `AUDIT_READ` (tenant), `PROJECT_READ`, `METHODOLOGY_READ`, `EVALUATION_READ`, `GRADE_READ`. **No** salary permission (salary is out of MVP 1 anyway). |
| **Audit-sensitive actions** | All their reads must be logged (auditor-of-auditor concept) |
| **Success metric** | Auditor can re-derive any grade from inputs without contacting HRLab |
| **Risks** | Auditor accessing other tenants — strictly forbidden; ABAC must hard-deny |

---

## 4. User Journeys

Five end-to-end journeys covering MVP 1.

### Journey J1 — Provision a new company-client (HRLab Super Admin)

1. Super Admin signs in, navigates to **Company-clients**.
2. Clicks **Create company-client**. Enters legal name, brand name, industry, isolation mode (`schema-per-tenant` default), primary locale.
3. System creates `tenant_<slug>` schema, applies baseline migrations, creates tenant-specific encryption key, seeds default dictionaries.
4. Super Admin creates the first **Client Company Admin** user, assigns them to the new tenant.
5. Super Admin sends invitation email (out of scope of MVP 1 if SMTP not ready — fallback: copy temporary password).
6. Audit events: `TENANT_CREATED`, `USER_CREATED`, `USER_TENANT_MEMBERSHIP_GRANTED`, `ROLE_ASSIGNED`.

### Journey J2 — Start a grading project (HRLab PM)

1. PM signs in, sees tenants they are assigned to.
2. Switches context to a tenant. Backend issues a short-lived tenant context token (the frontend never sends raw `tenant_id`).
3. PM clicks **Create project**. Enters project name, code, target completion date, project owner.
4. PM invites HRLab Consultant, HRLab Analyst, Client HR Director, Client HR Specialist into the project.
5. Audit: `PROJECT_CREATED`, `PROJECT_MEMBER_ADDED` (per user).

### Journey J3 — Build organization structure and position catalog (HRLab Analyst + Client HR Specialist)

1. Analyst opens **Organization structure** in the project workspace.
2. Creates root organization unit (e.g., "Head Office"), then child units (departments, divisions, regional units).
3. Switches to **Position catalog**. Creates positions, attaching each to a department.
4. Client HR Specialist opens each position and authors a **Job profile**: purpose, key duties, requirements (education, experience, skills), working conditions.
5. HR Specialist submits profile for approval; HR Director approves.
6. Audit: `ORG_UNIT_CREATED`, `POSITION_CREATED`, `JOB_PROFILE_CREATED`, `JOB_PROFILE_SUBMITTED`, `JOB_PROFILE_APPROVED`.

### Journey J4 — Configure and approve methodology (HRLab Consultant + PM)

1. Consultant opens **Methodology builder** in the project workspace.
2. Picks a template: `CLASSIC_8_FACTOR`, `EXTENDED_11_CRITERIA`, or `CUSTOM`. (Templates seeded by HRLab Super Admin from control plane.)
3. Adds/edits factors: name (4 languages), description, weight, max points.
4. For each factor adds levels: code (e.g., `A`, `B`, `C`), label (4 languages), description (4 languages), points.
5. Sets scoring mode: `DIRECT_POINTS`, `WEIGHTED_POINTS`, `WEIGHTED_SCALE`. (`FORMULA_BASED` is out of scope for MVP 1 — see Section 14.)
6. Defines grade bands: grade number, title (4 languages), min_score, max_score. Validation: no overlap, no gaps (warning, not error).
7. Submits methodology for approval. PM reviews and clicks **Approve & Lock**. System transitions methodology version to status `APPROVED` then `LOCKED`. No further edits allowed.
8. Any subsequent change requires creating a new version via **Create new version from approved**.
9. Audit: `METHODOLOGY_CREATED`, `METHODOLOGY_VERSION_CREATED`, `FACTOR_CREATED`, `FACTOR_LEVEL_CREATED`, `GRADE_BAND_CREATED`, `METHODOLOGY_APPROVED`, `METHODOLOGY_LOCKED`.

### Journey J5 — Evaluate positions and assign grades (Committee + HR Director)

1. Committee Member opens **Evaluation matrix** for the project. Sees positions with approved profiles, methodology version reference.
2. For each position, selects a level per factor; the system shows raw factor scores and a running total.
3. Member submits the evaluation. Evaluation status moves to `SUBMITTED`. Further edits blocked.
4. HR Director opens **Evaluation matrix** in approval mode, reviews each submission, clicks **Approve**.
5. System computes final total score using the locked methodology version, finds matching grade band, assigns grade. If a manual adjustment is required, approver must enter a reason; this is recorded.
6. Result: each approved position has a grade. HR Director sees the **Grade pyramid** (basic counts per grade — no fancy charts in MVP 1).
7. Audit: `EVALUATION_CREATED`, `EVALUATION_SCORE_CHANGED` (per level pick), `EVALUATION_SUBMITTED`, `EVALUATION_APPROVED`, `EVALUATION_LOCKED`, `GRADE_ASSIGNED`, `SCORE_MANUAL_ADJUSTED` (if applicable, with reason).

---

## 5. Epic List

MVP 1 contains **11 epics**, each mapped to one of the 11 features specified in the request.

| ID | Epic | Architectural module | MVP 1 must-have? |
| --- | --- | --- | --- |
| E1 | Tenant isolation foundation | Tenancy | Yes |
| E2 | Users, roles, permissions | Identity & Access | Yes |
| E3 | Project workspace | Project Management | Yes |
| E4 | Organization structure (basic, manual entry) | Organization | Yes |
| E5 | Position catalog | Position Catalog | Yes |
| E6 | Job profile | Job Profile | Yes |
| E7 | Basic methodology builder | Methodology Builder | Yes |
| E8 | Scoring engine | Evaluation & Scoring | Yes |
| E9 | Grade assignment | Grade Structure | Yes |
| E10 | Audit trail | Audit | Yes |
| E11 | Localization foundation | Localization | Yes |

All eleven are mandatory. Dropping any of them collapses MVP 1's value proposition.

---

## 6. Detailed User Stories and Acceptance Criteria

Story IDs follow `MVP1-Ex-y` pattern: epic number, story number within epic.

### Epic E1 — Tenant isolation foundation

#### MVP1-E1-1 — Create company-client tenant

**As a** HRLab Super Admin,
**I want to** create a new company-client tenant with an isolated schema,
**So that** I can onboard a new client without risking data contamination with existing clients.

**Acceptance criteria:**
- **Given** I am authenticated as HRLab Super Admin and have `TENANT_CREATE` permission, **When** I submit a `POST /api/v1/admin/tenants` with legal_name, brand_name, industry, isolation_mode=`SCHEMA_PER_TENANT`, primary_locale, **Then** the system creates a new schema `tenant_<slug>`, applies baseline tenant migrations, creates a tenant-specific encryption key envelope, seeds default dictionaries, and returns the new `tenant_id`.
- **Given** the tenant has been created, **When** I list tenants, **Then** the new tenant appears with status `ACTIVE`.
- **Given** the tenant has been created, **When** any audit query is run, **Then** an `TENANT_CREATED` audit event exists with actor=Super Admin, before=`null`, after=tenant snapshot, and a hash linked to the previous audit record.
- **Given** I do NOT have `TENANT_CREATE`, **When** I call the endpoint, **Then** the response is `403` with code `PERMISSION_DENIED` and no schema is created.

#### MVP1-E1-2 — Switch tenant context

**As a** HRLab Consultant assigned to multiple tenants,
**I want to** select an active tenant,
**So that** my session is scoped to one company-client at a time.

**Acceptance criteria:**
- **Given** I am authenticated and assigned to tenants T1 and T2, **When** I call `POST /api/v1/session/tenant-context` with `tenant_id=T1`, **Then** the backend verifies my membership and issues a short-lived tenant context token containing `active_tenant_id=T1`.
- **Given** I have a context token for T1, **When** I call any data-plane endpoint passing a body field `tenant_id=T2`, **Then** the backend ignores the body and uses the token; if the token doesn't authorize T2, response is `403` with audit event `CROSS_TENANT_ACCESS_ATTEMPT`.
- **Given** my context token has expired, **When** I call any data-plane endpoint, **Then** response is `401` with code `TENANT_CONTEXT_EXPIRED`.

#### MVP1-E1-3 — Block cross-tenant access

**As a** security engineer,
**I want** every data-plane query to be filtered by `tenant_id` at repository level,
**So that** no engineer can accidentally introduce a Broken Object Level Authorization bug.

**Acceptance criteria:**
- **Given** position P belongs to tenant T2 and user U has active tenant T1, **When** U calls `GET /api/v1/positions/{P.id}`, **Then** response is `404` (not `403`, to avoid enumeration), and an audit event `CROSS_TENANT_ACCESS_ATTEMPT` is created with actor=U, attempted resource type/id, and severity=`HIGH`.
- **Given** any new repository method on a tenant-scoped entity, **When** code review runs, **Then** static analysis (or convention enforcement) blocks plain `findById(...)` in favour of `findByIdAndTenantIdAndProjectId(...)`. (DevOps/security enforce; PRD requires the rule.)
- **Given** RLS is enabled on PostgreSQL, **When** the application connects, **Then** the session variable `app.current_tenant_id` is set per request and RLS policies use it.

### Epic E2 — Users, roles, permissions

#### MVP1-E2-1 — Authenticate via OIDC

**As a** user of any role,
**I want to** sign in with OAuth2/OIDC,
**So that** I can access the platform securely with industry-standard auth.

**Acceptance criteria:**
- **Given** a valid OIDC IdP is configured, **When** I complete the OIDC flow, **Then** I receive a JWT access token containing `sub`, `email`, `roles`, `permissions`, `active_tenant_id=null` (until I switch context), `locale`.
- **Given** my JWT is expired, **When** I call any endpoint, **Then** response is `401` with code `TOKEN_EXPIRED`.
- **Given** I sign out, **When** I sign out, **Then** an audit event `LOGOUT` is recorded.

#### MVP1-E2-2 — Manage users within a tenant

**As a** Client Company Admin,
**I want to** create users for my company and assign roles,
**So that** I onboard my HR team without HRLab support.

**Acceptance criteria:**
- **Given** I am Client Company Admin of tenant T1 with `USER_CREATE` (tenant), **When** I create a user with email, name, locale, role from the client-side role catalogue, **Then** the user is created scoped to T1, with status `INVITED`.
- **Given** I attempt to assign a HRLab-side role (e.g., HRLab Consultant) to a user, **When** I submit, **Then** response is `403` with code `ROLE_OUT_OF_SCOPE`; no user record is created.
- **Given** I deactivate a user, **When** they next attempt to sign in, **Then** their JWT issuance fails with `ACCOUNT_DEACTIVATED`; audit event `USER_DEACTIVATED` is recorded.

#### MVP1-E2-3 — Enforce permissions on the backend

**As a** product owner,
**I want** the backend to enforce every permission listed in the matrix (Section 8),
**So that** frontend permission checks are never the sole gatekeeper.

**Acceptance criteria:**
- **Given** a user lacks `EVALUATION_APPROVE`, **When** they call `POST /api/v1/evaluations/{id}/approve` directly (e.g., via Postman), **Then** response is `403` with code `PERMISSION_DENIED`; no state change; audit event `PERMISSION_DENIED` recorded.
- **Given** the frontend hides a button due to missing permission, **When** the user crafts the underlying request manually, **Then** the same `403` outcome must occur (backend test verifies).

### Epic E3 — Project workspace

#### MVP1-E3-1 — Create a grading project

**As a** HRLab Project Manager,
**I want to** create a grading project inside a tenant,
**So that** all grading work for one engagement is contained in one workspace.

**Acceptance criteria:**
- **Given** I am PM with `PROJECT_CREATE` and active tenant context T1, **When** I `POST /api/v1/projects` with name, code, target_completion_date, owner, **Then** a project is created in `tenant_T1.projects` with status `DRAFT`.
- **Given** a project exists, **When** I switch to project context, **Then** all subsequent data-plane queries are filtered by `tenant_id AND project_id`.
- **Given** I add a member with role and scope, **When** I save, **Then** an audit event `PROJECT_MEMBER_ADDED` is recorded.

#### MVP1-E3-2 — Project status lifecycle (MVP 1 subset)

**As a** PM,
**I want** projects to move through `DRAFT → ACTIVE`,
**So that** the team knows what is in flight.

**Acceptance criteria:**
- **Given** a draft project, **When** I click **Activate**, **Then** status becomes `ACTIVE`, audit event `PROJECT_ACTIVATED`.
- Other transitions (paused, completed, archived) are **out of MVP 1 scope**.

### Epic E4 — Organization structure (basic, manual entry)

#### MVP1-E4-1 — Create organization unit

**As a** HRLab Analyst,
**I want to** create an organization unit (department, division, regional unit) and place it under a parent,
**So that** I model the company-client's hierarchy.

**Acceptance criteria:**
- **Given** I have `ORG_CREATE` and an active project, **When** I `POST /api/v1/departments` with name (in 4 locales optional, primary locale required), code, type, parent_id (nullable for root), **Then** the unit is created scoped to `tenant_id + project_id`.
- **Given** I attempt to set `parent_id` to a unit in a different project, **When** I submit, **Then** response is `400` with code `PARENT_OUT_OF_SCOPE`.
- **Given** I create a cycle (A → B → A), **When** I submit, **Then** response is `400` with code `ORG_CYCLE`.

#### MVP1-E4-2 — Display organization tree

**As a** Department Manager,
**I want to** see only my department(s) in the tree,
**So that** I don't accidentally browse other departments.

**Acceptance criteria:**
- **Given** my department scope is `[dept-A]`, **When** I call `GET /api/v1/departments/tree`, **Then** the response includes only `dept-A` and its descendants.
- **Given** I attempt to fetch a sibling department by id, **When** I `GET /api/v1/departments/{sibling-id}`, **Then** response is `404`; audit event `ACCESS_DENIED_DEPT`.

### Epic E5 — Position catalog

#### MVP1-E5-1 — Create position

**As a** HRLab Analyst,
**I want to** create a position attached to a department,
**So that** the position becomes evaluable.

**Acceptance criteria:**
- **Given** `POSITION_CREATE`, **When** I `POST /api/v1/positions` with title (primary locale required), department_id, job_family (free text), **Then** the position is created with status `DRAFT`.
- **Given** title is empty, **When** I submit, **Then** response is `400` with field-level validation error.
- **Given** department_id is in another project, **Then** `400` with code `DEPARTMENT_OUT_OF_SCOPE`.

#### MVP1-E5-2 — List positions with filters

**As a** Committee Member,
**I want to** filter positions by department and status,
**So that** I find what I need to evaluate.

**Acceptance criteria:**
- **Given** `POSITION_READ` and a department scope, **When** I `GET /api/v1/positions?department_id=...&status=DRAFT`, **Then** I get the filtered list, paged (default page size 50, max 500).
- **Given** I provide a `department_id` outside my scope, **Then** `403` or filtered-out (depending on enumeration safety — for department scope use 403 since identity is known; for tenant scope use 404).

### Epic E6 — Job profile

#### MVP1-E6-1 — Create job profile

**As a** Client HR Specialist,
**I want to** create a job profile for a position,
**So that** evaluators have authoritative content to base scoring on.

**Acceptance criteria:**
- **Given** `JOB_PROFILE_CREATE`, **When** I `POST /api/v1/job-profiles` with position_id, purpose, key_duties, requirements, working_conditions (all multilingual, primary locale required), **Then** the profile is created with status `DRAFT`.
- **Given** I save a draft with missing optional locales, **When** I submit, **Then** the system accepts and flags a "translation incomplete" warning (not an error).
- **Given** the position already has an active profile, **When** I attempt to create another, **Then** `409 CONFLICT` with code `PROFILE_ALREADY_EXISTS`.

#### MVP1-E6-2 — Submit and approve job profile

**As a** HR Director,
**I want to** approve job profiles before they are used for evaluation,
**So that** the company stands behind the descriptions.

**Acceptance criteria:**
- **Given** profile status `DRAFT`, **When** Specialist clicks **Submit**, status becomes `SUBMITTED`; audit `JOB_PROFILE_SUBMITTED`.
- **Given** status `SUBMITTED` and I have `JOB_PROFILE_APPROVE`, **When** I click **Approve**, status becomes `APPROVED`; audit `JOB_PROFILE_APPROVED`. Approved profile is read-only.
- **Given** status `APPROVED`, **When** anyone attempts edit, **Then** `409` with code `PROFILE_LOCKED`; to change, a new profile version is required (MVP 2 feature — for MVP 1, "Reject" returns to `DRAFT` with audit `JOB_PROFILE_REJECTED` and reason).

### Epic E7 — Basic methodology builder

#### MVP1-E7-1 — Create methodology from template

**As a** HRLab Consultant,
**I want to** start a methodology from a template (`CLASSIC_8_FACTOR`, `EXTENDED_11_CRITERIA`, or `CUSTOM`),
**So that** I don't rebuild common methodologies from scratch.

**Acceptance criteria:**
- **Given** `METHODOLOGY_CREATE` and a project, **When** I `POST /api/v1/methodologies` with name, model_type, source_template_id (nullable for CUSTOM), **Then** methodology and its first version v1 are created with status `DRAFT`. Factors and levels are pre-populated from template if specified.
- **Given** I pick `CUSTOM`, **When** I create, **Then** no factors are pre-populated.

#### MVP1-E7-2 — Edit factors, levels, weights, points

**As a** HRLab Consultant,
**I want to** edit the methodology while it is in draft,
**So that** I can tailor it to the client.

**Acceptance criteria:**
- **Given** version status `DRAFT`, **When** I add/edit/delete factor, level, weight, points, **Then** changes save and audit events `FACTOR_*`, `FACTOR_LEVEL_*` are recorded.
- **Given** I attempt the same on a `LOCKED` version, **Then** `409` with code `METHODOLOGY_LOCKED`.
- **Given** weights do not sum to the configured total (e.g., 100% for `WEIGHTED_POINTS`), **When** I attempt to submit, **Then** `400` with code `WEIGHT_SUM_INVALID`.

#### MVP1-E7-3 — Approve and lock methodology version

**As a** PM,
**I want to** approve and lock a methodology version,
**So that** subsequent evaluations are reproducible.

**Acceptance criteria:**
- **Given** `METHODOLOGY_APPROVE` and version status `DRAFT` with all validations passing, **When** I click **Approve & Lock**, **Then** status becomes `LOCKED`; audit events `METHODOLOGY_APPROVED` and `METHODOLOGY_LOCKED`.
- **Given** status `LOCKED`, **When** any edit endpoint is called, **Then** `409` with code `METHODOLOGY_LOCKED`.

#### MVP1-E7-4 — Create new version from approved

**As a** HRLab Consultant,
**I want to** clone a locked methodology version into a new draft v(n+1),
**So that** I can iterate without breaking history.

**Acceptance criteria:**
- **Given** version v1 is `LOCKED`, **When** I `POST /api/v1/methodologies/{id}/versions` with `source_version=v1`, **Then** v2 is created as a deep copy with status `DRAFT`. Audit `METHODOLOGY_VERSION_CREATED` with `parent_version_id=v1.id`.

#### MVP1-E7-5 — Multilingual factor/level content

**As a** PM,
**I want** factor names, descriptions, level labels and descriptions in 4 languages,
**So that** evaluators see the methodology in their language.

**Acceptance criteria:**
- **Given** primary locale is `ru-RU`, **When** I save a factor with only `ru-RU` content, **Then** the system saves the row and warns that 3 locales are missing.
- **Given** the methodology is approved with missing locales, **When** an evaluator in a missing locale opens the methodology, **Then** the UI falls back to the primary locale and shows a "translation not available" indicator. Approval is not blocked by missing non-primary locales in MVP 1.

### Epic E8 — Scoring engine

#### MVP1-E8-1 — Create an evaluation

**As a** Committee Member,
**I want to** start an evaluation for a position using a locked methodology version,
**So that** I can record factor-level selections.

**Acceptance criteria:**
- **Given** `EVALUATION_CREATE`, an approved job profile, and a `LOCKED` methodology version selected for the project, **When** I `POST /api/v1/evaluations` with position_id, methodology_version_id, **Then** evaluation is created with status `DRAFT`.
- **Given** the methodology version is `DRAFT`, **When** I attempt to create, **Then** `400` with code `METHODOLOGY_NOT_LOCKED`.

#### MVP1-E8-2 — Select factor levels

**As a** Committee Member,
**I want to** pick one level per factor and see live total score,
**So that** I have feedback while evaluating.

**Acceptance criteria:**
- **Given** evaluation status `DRAFT`, **When** I `PUT /api/v1/evaluations/{id}/scores` with `factor_id` and `level_id`, **Then** an `EvaluationScore` row is upserted; audit `EVALUATION_SCORE_CHANGED` with `before`/`after`.
- **Given** the level belongs to a different factor, **Then** `400` with code `LEVEL_FACTOR_MISMATCH`.
- **Given** the level belongs to a different methodology version, **Then** `400` with code `LEVEL_VERSION_MISMATCH`.
- **Given** all required factors have selections, **When** I read `GET /api/v1/evaluations/{id}`, **Then** the response includes total_score computed deterministically per the version's scoring mode.

#### MVP1-E8-3 — Submit evaluation

**As a** Committee Member,
**I want to** submit the evaluation,
**So that** it is ready for approval.

**Acceptance criteria:**
- **Given** all required factors selected, **When** I `POST /api/v1/evaluations/{id}/submit`, **Then** status becomes `SUBMITTED`; further edits blocked; audit `EVALUATION_SUBMITTED`.
- **Given** a required factor is missing, **Then** `400` with code `EVALUATION_INCOMPLETE` and the missing factor list.

#### MVP1-E8-4 — Approve evaluation and reproduce total

**As a** HR Director,
**I want to** approve a submitted evaluation,
**So that** it becomes the official record.

**Acceptance criteria:**
- **Given** `EVALUATION_APPROVE` and status `SUBMITTED`, **When** I `POST /api/v1/evaluations/{id}/approve`, **Then** status becomes `APPROVED`, then `LOCKED`. The backend re-computes total_score from raw factor-level selections using the locked methodology version and stores it as the canonical `total_score`. Audit `EVALUATION_APPROVED`, `EVALUATION_LOCKED`.
- **Given** the same evaluation is fetched any time later, **When** total_score is recomputed from inputs, **Then** the result equals the stored value (reproducibility test).
- **Given** status `LOCKED`, any edit returns `409 EVALUATION_LOCKED`.

#### MVP1-E8-5 — Manual score adjustment with reason (MVP 1 minimal)

**As a** HR Director,
**I want to** override the computed total only with an explicit reason,
**So that** committee decisions are recorded transparently.

**Acceptance criteria:**
- **Given** `EVALUATION_SCORE_OVERRIDE` (a permission distinct from `EVALUATION_APPROVE`), **When** I `POST /api/v1/evaluations/{id}/override` with `adjusted_score` and `reason` (min 20 chars), **Then** the override is stored as a side record, the canonical computed score remains visible, and the assigned grade uses the override; audit `SCORE_MANUAL_ADJUSTED` with before/after and reason.
- **Given** no reason is supplied, **Then** `400` with code `REASON_REQUIRED`.

### Epic E9 — Grade assignment

#### MVP1-E9-1 — Define grade bands

**As a** HRLab Consultant,
**I want to** define grade bands inside the methodology version,
**So that** evaluations can be mapped to grades.

**Acceptance criteria:**
- **Given** methodology version `DRAFT`, **When** I add grade bands (grade_no, title in 4 locales primary required, min_score, max_score), **Then** rows save.
- **Given** two bands overlap, **When** I submit, **Then** `400` with code `GRADE_BAND_OVERLAP`. Gaps produce a warning, not an error.
- **Given** the version is `LOCKED`, edits return `409`.

#### MVP1-E9-2 — Auto-assign grade on evaluation approval

**As a** product,
**I want** approval to automatically place the position in a grade band,
**So that** results are immediate and consistent.

**Acceptance criteria:**
- **Given** an evaluation is `APPROVED` and the canonical/override total_score matches band `B[min, max]`, **When** approval completes, **Then** the position-grade record is created with `position_id`, `grade_no`, `evaluation_id`, `methodology_version_id`; audit `GRADE_ASSIGNED`.
- **Given** no band matches the score, **When** approval is attempted, **Then** `409` with code `NO_GRADE_BAND_MATCH`; the approver is shown a list of bands and the score, and must either add a band (if methodology not locked — impossible at this stage) or override score with reason.

#### MVP1-E9-3 — View grade pyramid (basic)

**As a** HR Director,
**I want to** see how many positions are in each grade,
**So that** I get a basic shape of the result.

**Acceptance criteria:**
- **Given** `GRADE_READ`, **When** I `GET /api/v1/analytics/grade-distribution?project_id=...`, **Then** I get an array of `{grade_no, position_count}`. No fancy chart in MVP 1 — just numeric counts that the frontend renders as a simple bar.

### Epic E10 — Audit trail

#### MVP1-E10-1 — Append-only audit log with hash chaining

**As a** External Auditor,
**I want** an append-only audit log with hash chaining,
**So that** tampering is detectable.

**Acceptance criteria:**
- **Given** an action is performed that requires an audit event (see Section 9), **When** the action commits, **Then** an audit record is written with `audit_id`, `tenant_id`, `project_id`, `actor_user_id`, `action`, `entity_type`, `entity_id`, `before`, `after`, `reason` (where applicable), `ip_address`, `user_agent`, `created_at`, `hash_prev`, `hash_current`.
- **Given** an audit record exists, **When** anyone attempts to delete or update it, **Then** the operation fails (DB-level constraint, no UPDATE/DELETE grant on audit tables).
- **Given** any record's `hash_current` is changed externally, **When** the integrity verifier runs, **Then** it reports a break in the chain.

#### MVP1-E10-2 — Read audit log

**As a** External Auditor,
**I want to** query audit records filtered by date, actor, action, entity,
**So that** I can investigate concrete questions.

**Acceptance criteria:**
- **Given** `AUDIT_READ` (tenant scope) and filters, **When** I `GET /api/v1/audit-logs?from=...&to=...&action=...`, **Then** I get paged results, ordered by `created_at DESC`, each containing the full audit record (except for `before`/`after` of salary-sensitive entities — moot in MVP 1).
- **Given** my role is HRLab Super Admin with `AUDIT_READ_CROSS_TENANT`, **When** I omit the tenant filter, **Then** I see cross-tenant audit. Otherwise, audit is forcibly tenant-scoped.

#### MVP1-E10-3 — Cross-tenant access attempt detection

**As a** security,
**I want** every cross-tenant access attempt to be logged as a HIGH-severity event,
**So that** I can detect probing.

**Acceptance criteria:**
- **Given** a user with tenant T1 attempts a request for a resource in T2 (by manipulating an id), **When** the backend rejects, **Then** an audit `CROSS_TENANT_ACCESS_ATTEMPT` is written with severity=`HIGH`, including the attempted resource type/id.
- **Given** the rate of such events exceeds a configured threshold per user per hour (e.g., 5), **Then** the user is auto-suspended; audit `USER_AUTO_SUSPENDED`. (Threshold: MVP 1 may ship without the auto-suspend if security-engineer prefers manual alerting; flagged as open question.)

### Epic E11 — Localization foundation

#### MVP1-E11-1 — UI text in 4 locales

**As any** user,
**I want** all UI text in my chosen locale,
**So that** I can work in my preferred language.

**Acceptance criteria:**
- **Given** every UI string is keyed (no hardcoded text), **When** I switch locale, **Then** all interactive copy changes to the selected locale; numbers, dates and percentages format per locale rules.
- **Given** a key has no translation in my locale, **When** rendered, **Then** the UI falls back to `ru-RU` and shows a small "missing translation" indicator (dev-mode only; suppressed in production).
- **Given** an admin opens the **Localization dictionary** screen (HRLab Super Admin only in MVP 1), **When** they edit a key, **Then** the change is saved with audit `LOCALIZATION_KEY_UPDATED`.

#### MVP1-E11-2 — Per-tenant primary locale and per-user preferred locale

**As a** PM,
**I want** to set a default locale for a tenant,
**So that** users default to the right language.

**Acceptance criteria:**
- **Given** tenant primary locale is `uz-Latn-UZ`, **When** a new user signs in without a personal preference, **Then** UI loads in `uz-Latn-UZ`.
- **Given** the user sets their preferred locale to `en-US`, **When** they sign in next time, **Then** UI loads in `en-US`.

#### MVP1-E11-3 — Multilingual domain content

**As a** Consultant,
**I want** factor names, level labels, grade titles to be storable in 4 locales,
**So that** the same methodology serves multilingual committees.

**Acceptance criteria:**
- **Given** the methodology builder, **When** I edit a factor in `ru-RU`, `uz-Cyrl-UZ`, `uz-Latn-UZ`, `en-US`, **Then** all four are persisted in the respective `*_translations` tables (per architecture Section 20.3).
- **Given** primary locale is `ru-RU`, **When** I attempt to approve a methodology version missing `ru-RU` content for any factor, **Then** `400` with code `PRIMARY_LOCALE_INCOMPLETE`. Other locales are warnings only.

---

## 7. Permissions Matrix (11 roles x 16 modules x 12 actions)

> **Implementation source of truth:** `docs/mvp1/role-permissions-matrix.md` — that document maps the 11 roles to the 34 atomic permission codes seeded in `001-default-permissions.yaml` and is implemented by `seeds/004-default-role-permissions.yaml`. The prose matrix below is the **product narrative**; where the 34-code matrix narrows or clarifies (e.g., `REPORT_EXPORT` pre-granted to Super Admin + PM as MVP-2 forward-compat; `EVALUATION_SUBMIT` / `*_APPROVE` not yet separate codes), the linked document is authoritative. Hard rules unchanged: SALARY_* granted to **no role** by default; AUDIT_READ granted only to **HRLab Super Admin** and **External Auditor**; TENANT_CREATE granted only to **HRLab Super Admin**.

Notation: `Y` = allowed; `N` = denied; `Y*` = allowed with department/project scope only (ABAC); `Y!` = allowed but requires reason; `—` = action not applicable in MVP 1 (e.g., view-salary in modules without salary).

Actions:
1. `read` — view list/detail
2. `create` — create new
3. `edit` — modify draft
4. `submit` — move from draft to submitted
5. `approve` — approve submitted
6. `lock` — lock from further edits
7. `archive` — move to archive (mostly out of MVP 1)
8. `export` — download (Excel/CSV) — mostly MVP 2
9. `view-salary` — see decrypted salary fields — mostly MVP 3
10. `edit-salary` — modify salary fields — MVP 3
11. `run-salary-scenario` — execute scenarios — MVP 3
12. `view-audit` — read audit log

Sixteen modules:
1. Tenant Management
2. User & Access Management
3. Project Management
4. Organization Structure
5. Position Catalog
6. Job Profile
7. Job Analysis (MVP 2)
8. Methodology Builder
9. Evaluation & Scoring
10. Grade Structure
11. Compensation / Salary (MVP 3 — only foundation in MVP 1)
12. Workflow & Approvals (MVP 2)
13. Analytics & Dashboards
14. Reports (MVP 2)
15. Audit Log
16. Localization

In MVP 1, modules 7 (Job Analysis), 11 (Compensation), 12 (Workflow), 14 (Reports) are foundation-only: permission codes exist and are denied by default for almost everyone, but the modules themselves are not built. They are listed so that role design is forward-compatible.

### 7.1 Master matrix

Legend per role row: each cell shows the 12 actions in order `read | create | edit | submit | approve | lock | archive | export | view-salary | edit-salary | run-salary-scenario | view-audit`.

#### Role 1: HRLab Super Admin

| Module | read | create | edit | submit | approve | lock | archive | export | v-sal | e-sal | run-sal | v-aud |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1. Tenant Mgmt | Y | Y | Y | — | — | Y | Y | N | — | — | — | Y |
| 2. User & Access | Y | Y | Y | — | — | — | Y | N | — | — | — | Y |
| 3. Project | Y | Y | Y | — | — | — | Y | N | — | — | — | Y |
| 4. Org Structure | Y | Y | Y | — | — | — | Y | N | — | — | — | Y |
| 5. Position | Y | Y | Y | — | — | — | Y | N | — | — | — | Y |
| 6. Job Profile | Y | Y | Y | Y | Y | Y | Y | N | — | — | — | Y |
| 7. Job Analysis | — | — | — | — | — | — | — | — | — | — | — | Y |
| 8. Methodology | Y | Y | Y | Y | Y | Y | Y | N | — | — | — | Y |
| 9. Evaluation | Y | Y | Y | Y | Y | Y | Y | N | — | — | — | Y |
| 10. Grade | Y | Y | Y | — | Y | Y | Y | N | — | — | — | Y |
| 11. Compensation | N | N | N | N | N | N | N | N | N | N | N | Y |
| 12. Workflow | — | — | — | — | — | — | — | — | — | — | — | Y |
| 13. Analytics | Y | — | — | — | — | — | — | N | — | — | — | Y |
| 14. Reports | — | — | — | — | — | — | — | — | — | — | — | Y |
| 15. Audit Log | Y | — | — | — | — | — | — | N | — | — | — | Y |
| 16. Localization | Y | Y | Y | — | Y | — | — | N | — | — | — | Y |

Notes: Super Admin can do almost everything but **cannot view salary** in MVP 1 (separation of duty principle; salary foundation only). Export is disabled in MVP 1 across the board (MVP 2 feature).

#### Role 2: HRLab Project Manager

| Module | r | c | e | s | a | l | ar | ex | vs | es | rs | va |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1. Tenant Mgmt | Y | N | N | — | — | N | N | N | — | — | — | N |
| 2. User & Access | Y | N | N | — | — | — | N | N | — | — | — | Y* |
| 3. Project | Y* | Y | Y | — | — | N | N | N | — | — | — | Y* |
| 4. Org Structure | Y* | Y | Y | — | — | — | N | N | — | — | — | Y* |
| 5. Position | Y* | Y | Y | — | — | — | N | N | — | — | — | Y* |
| 6. Job Profile | Y* | Y | Y | Y | Y | Y | N | N | — | — | — | Y* |
| 7. Job Analysis | — | — | — | — | — | — | — | — | — | — | — | N |
| 8. Methodology | Y* | Y | Y | Y | Y | Y | N | N | — | — | — | Y* |
| 9. Evaluation | Y* | Y | Y | Y | Y | Y | N | N | — | — | — | Y* |
| 10. Grade | Y* | Y | Y | — | Y | Y | N | N | — | — | — | Y* |
| 11. Compensation | N | N | N | N | N | N | N | N | N | N | N | N |
| 12. Workflow | — | — | — | — | — | — | — | — | — | — | — | N |
| 13. Analytics | Y* | — | — | — | — | — | — | N | — | — | — | N |
| 14. Reports | — | — | — | — | — | — | — | — | — | — | — | N |
| 15. Audit Log | Y* | — | — | — | — | — | — | N | — | — | — | Y* |
| 16. Localization | Y | N | N | — | N | — | — | N | — | — | — | N |

#### Role 3: HRLab Consultant

| Module | r | c | e | s | a | l | ar | ex | vs | es | rs | va |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1. Tenant Mgmt | N | N | N | — | — | N | N | N | — | — | — | N |
| 2. User & Access | Y* | N | N | — | — | — | N | N | — | — | — | N |
| 3. Project | Y* | N | N | — | — | N | N | N | — | — | — | N |
| 4. Org Structure | Y* | N | N | — | — | — | N | N | — | — | — | N |
| 5. Position | Y* | N | N | — | — | — | N | N | — | — | — | N |
| 6. Job Profile | Y* | N | N | N | N | N | N | N | — | — | — | N |
| 8. Methodology | Y* | Y | Y | Y | N | N | N | N | — | — | — | N |
| 9. Evaluation | Y* | Y | Y | Y | N | N | N | N | — | — | — | N |
| 10. Grade | Y* | N | N | — | N | N | N | N | — | — | — | N |
| 13. Analytics | Y* | — | — | — | — | — | — | N | — | — | — | N |
| 15. Audit Log | Y* | — | — | — | — | — | — | N | — | — | — | Y* |
| 16. Localization | Y | N | N | — | N | — | — | N | — | — | — | N |

(Modules 7, 11, 12, 14 omitted — all N.)

#### Role 4: HRLab Analyst

| Module | r | c | e | s | a | l | ar | ex | vs | es | rs | va |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 3. Project | Y* | N | N | — | — | N | N | N | — | — | — | N |
| 4. Org Structure | Y* | Y | Y | — | — | — | N | N | — | — | — | N |
| 5. Position | Y* | Y | Y | — | — | — | N | N | — | — | — | N |
| 6. Job Profile | Y* | Y | Y | Y | N | N | N | N | — | — | — | N |
| 8. Methodology | Y* | N | N | N | N | N | N | N | — | — | — | N |
| 9. Evaluation | Y* | N | N | — | N | N | N | N | — | — | — | N |
| 10. Grade | Y* | N | N | — | N | N | N | N | — | — | — | N |
| 11. Compensation | N | N | N | N | N | N | N | N | N | N | N | N |
| 13. Analytics | Y* | — | — | — | — | — | — | N | — | — | — | N |
| 15. Audit Log | Y* | — | — | — | — | — | — | N | — | — | — | Y* |
| 16. Localization | Y | N | N | — | N | — | — | N | — | — | — | N |

#### Role 5: Client Company Admin

| Module | r | c | e | s | a | l | ar | ex | vs | es | rs | va |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 2. User & Access | Y (tenant) | Y (tenant) | Y (tenant) | — | — | — | N | N | — | — | — | Y (tenant) |
| 3. Project | Y (tenant) | N | N | — | — | N | N | N | — | — | — | N |
| 4. Org Structure | Y (tenant) | N | N | — | — | — | N | N | — | — | — | N |
| 5. Position | Y (tenant) | N | N | — | — | — | N | N | — | — | — | N |
| 6. Job Profile | Y (tenant) | N | N | N | N | N | N | N | — | — | — | N |
| 8. Methodology | Y (tenant) | N | N | N | N | N | N | N | — | — | — | N |
| 9. Evaluation | Y (tenant) | N | N | N | N | N | N | N | — | — | — | N |
| 10. Grade | Y (tenant) | N | N | — | N | N | N | N | — | — | — | N |
| 15. Audit Log | Y (tenant) | — | — | — | — | — | — | N | — | — | — | Y (tenant) |
| 16. Localization | Y | N | N | — | N | — | — | N | — | — | — | N |

#### Role 6: Client HR Director

| Module | r | c | e | s | a | l | ar | ex | vs | es | rs | va |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 3. Project | Y (tenant) | N | N | — | — | N | N | N | — | — | — | N |
| 4. Org Structure | Y (tenant) | N | N | — | — | — | N | N | — | — | — | N |
| 5. Position | Y (tenant) | N | N | — | — | — | N | N | — | — | — | N |
| 6. Job Profile | Y (tenant) | N | N | N | Y (tenant) | Y (tenant) | N | N | — | — | — | N |
| 8. Methodology | Y (tenant) | N | N | N | N | N | N | N | — | — | — | N |
| 9. Evaluation | Y (tenant) | N | N | N | Y (tenant) | Y (tenant) | N | N | — | — | — | N |
| 9. Evaluation (override) | — | — | — | — | Y! (tenant) | — | — | — | — | — | — | — |
| 10. Grade | Y (tenant) | N | N | — | Y (tenant) | Y (tenant) | N | N | — | — | — | N |
| 13. Analytics | Y (tenant) | — | — | — | — | — | — | N | — | — | — | N |
| 15. Audit Log | Y (tenant) | — | — | — | — | — | — | N | — | — | — | Y (tenant) |
| 16. Localization | Y | N | N | — | N | — | — | N | — | — | — | N |

#### Role 7: Client HR Specialist

| Module | r | c | e | s | a | l | ar | ex | vs | es | rs | va |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 5. Position | Y (tenant) | N | N | — | — | — | N | N | — | — | — | N |
| 6. Job Profile | Y (tenant) | Y (tenant) | Y (tenant) | Y (tenant) | N | N | N | N | — | — | — | N |
| 9. Evaluation | Y (tenant) | N | N | N | N | N | N | N | — | — | — | N |
| 10. Grade | Y (tenant) | N | N | — | N | N | N | N | — | — | — | N |
| 16. Localization | Y | N | N | — | N | — | — | N | — | — | — | N |

#### Role 8: Evaluation Committee Member

| Module | r | c | e | s | a | l | ar | ex | vs | es | rs | va |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 5. Position | Y (project) | N | N | — | — | — | N | N | — | — | — | N |
| 6. Job Profile | Y (project) | N | N | — | N | N | N | N | — | — | — | N |
| 8. Methodology | Y (project) | N | N | N | N | N | N | N | — | — | — | N |
| 9. Evaluation | Y (project) | Y (project) | Y (project) | Y (project) | N | N | N | N | — | — | — | N |
| 16. Localization | Y | N | N | — | N | — | — | N | — | — | — | N |

#### Role 9: Department Manager

| Module | r | c | e | s | a | l | ar | ex | vs | es | rs | va |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 4. Org Structure | Y* (dept) | N | N | — | — | — | N | N | — | — | — | N |
| 5. Position | Y* (dept) | N | N | — | — | — | N | N | — | — | — | N |
| 6. Job Profile | Y* (dept) | N | N | — | N | N | N | N | — | — | — | N |
| 9. Evaluation | Y* (dept) | N | N | — | N | N | N | N | — | — | — | N |
| 10. Grade | Y* (dept) | N | N | — | N | N | N | N | — | — | — | N |
| 16. Localization | Y | N | N | — | N | — | — | N | — | — | — | N |

#### Role 10: Viewer / Read-only User

| Module | r | c | e | s | a | l | ar | ex | vs | es | rs | va |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 3. Project | Y (assigned) | N | N | — | — | N | N | N | — | — | — | N |
| 4. Org Structure | Y (assigned) | N | N | — | — | — | N | N | — | — | — | N |
| 5. Position | Y (assigned) | N | N | — | — | — | N | N | — | — | — | N |
| 6. Job Profile | Y (approved only) | N | N | — | N | N | N | N | — | — | — | N |
| 8. Methodology | Y (approved only) | N | N | — | N | N | N | N | — | — | — | N |
| 9. Evaluation | Y (approved only) | N | N | — | N | N | N | N | — | — | — | N |
| 10. Grade | Y (approved only) | N | N | — | N | N | N | N | — | — | — | N |
| 16. Localization | Y | N | N | — | N | — | — | N | — | — | — | N |

#### Role 11: External Auditor

| Module | r | c | e | s | a | l | ar | ex | vs | es | rs | va |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 3. Project | Y (tenant) | N | N | — | — | N | N | N | — | — | — | N |
| 4. Org Structure | Y (tenant) | N | N | — | — | — | N | N | — | — | — | N |
| 5. Position | Y (tenant) | N | N | — | — | — | N | N | — | — | — | N |
| 6. Job Profile | Y (tenant) | N | N | — | N | N | N | N | — | — | — | N |
| 8. Methodology | Y (tenant) | N | N | — | N | N | N | N | — | — | — | N |
| 9. Evaluation | Y (tenant) | N | N | — | N | N | N | N | — | — | — | N |
| 10. Grade | Y (tenant) | N | N | — | N | N | N | N | — | — | — | N |
| 15. Audit Log | Y (tenant) | — | — | — | — | — | — | N | — | — | — | Y (tenant) |

### 7.2 Per-permission enforcement requirements

For every permission cell marked `Y`, `Y*`, or `Y!`:
- **Product reason:** documented above.
- **Backend enforcement:** mandatory; every controller method invokes the corresponding ABAC policy before any data access.
- **Frontend visibility rule:** the corresponding UI element (button, menu, route) is hidden if the permission claim is missing; if visible, it remains gated by backend re-check.
- **Audit requirement:** any action that writes state generates an audit event (Section 9).
- **Risk:** missing the backend check yields a critical vulnerability; QA's tenant-isolation test suite must cover each.

---

## 8. Audit Event Matrix

Every audit event includes: `tenant_id`, `project_id` (where applicable), `actor_user_id`, `action`, `entity_type`, `entity_id`, `before`, `after`, `reason` (when required), `ip_address`, `user_agent`, `created_at`, `hash_prev`, `hash_current`, `severity`.

| Action code | Triggered by | Severity | `before`/`after` | Reason required |
| --- | --- | --- | --- | --- |
| `LOGIN` | Successful sign-in | INFO | n/a | No |
| `LOGIN_FAILED` | Failed auth | WARN | n/a | No |
| `LOGOUT` | Sign-out | INFO | n/a | No |
| `TENANT_CONTEXT_SWITCHED` | Switch tenant context | INFO | before=prev, after=new | No |
| `CROSS_TENANT_ACCESS_ATTEMPT` | BOLA attempt | HIGH | resource type/id | No |
| `PERMISSION_DENIED` | Backend denial | WARN | resource | No |
| `USER_AUTO_SUSPENDED` | Threshold breach | HIGH | user_id | No |
| `TENANT_CREATED` | Super Admin | INFO | before=null, after=tenant | No |
| `TENANT_SUSPENDED` | Super Admin | HIGH | before=ACTIVE, after=SUSPENDED | Yes |
| `USER_CREATED` | Admin or Super Admin | INFO | user | No |
| `USER_DEACTIVATED` | Admin or Super Admin | WARN | before/after status | Yes |
| `USER_TENANT_MEMBERSHIP_GRANTED` | Admin | INFO | tenant, role | No |
| `ROLE_ASSIGNED` | Admin | WARN | before/after roles | No |
| `ROLE_REVOKED` | Admin | WARN | before/after roles | No |
| `PROJECT_CREATED` | PM | INFO | project | No |
| `PROJECT_ACTIVATED` | PM | INFO | before=DRAFT, after=ACTIVE | No |
| `PROJECT_MEMBER_ADDED` | PM | INFO | member | No |
| `PROJECT_MEMBER_REMOVED` | PM | WARN | member | No |
| `ORG_UNIT_CREATED` | Analyst | INFO | unit | No |
| `ORG_UNIT_EDITED` | Analyst | INFO | before/after | No |
| `POSITION_CREATED` | Analyst/Specialist | INFO | position | No |
| `POSITION_EDITED` | Analyst/Specialist | INFO | before/after | No |
| `JOB_PROFILE_CREATED` | Specialist | INFO | profile | No |
| `JOB_PROFILE_EDITED` | Specialist | INFO | before/after | No |
| `JOB_PROFILE_SUBMITTED` | Specialist | INFO | before=DRAFT, after=SUBMITTED | No |
| `JOB_PROFILE_APPROVED` | HR Director | INFO | before=SUBMITTED, after=APPROVED | No |
| `JOB_PROFILE_REJECTED` | HR Director | INFO | before=SUBMITTED, after=DRAFT | Yes |
| `METHODOLOGY_CREATED` | Consultant | INFO | methodology | No |
| `METHODOLOGY_VERSION_CREATED` | Consultant | INFO | parent version | No |
| `FACTOR_CREATED/EDITED/DELETED` | Consultant | INFO | before/after | No |
| `FACTOR_LEVEL_CREATED/EDITED/DELETED` | Consultant | INFO | before/after | No |
| `GRADE_BAND_CREATED/EDITED/DELETED` | Consultant | INFO | before/after | No |
| `METHODOLOGY_APPROVED` | PM | INFO | before=DRAFT, after=APPROVED | No |
| `METHODOLOGY_LOCKED` | PM (auto post-approve) | INFO | before=APPROVED, after=LOCKED | No |
| `EVALUATION_CREATED` | Committee | INFO | evaluation | No |
| `EVALUATION_SCORE_CHANGED` | Committee | INFO | before/after level | No |
| `EVALUATION_SUBMITTED` | Committee | INFO | before=DRAFT, after=SUBMITTED | No |
| `EVALUATION_APPROVED` | HR Director | INFO | before=SUBMITTED, after=APPROVED | No |
| `EVALUATION_LOCKED` | HR Director | INFO | before=APPROVED, after=LOCKED | No |
| `EVALUATION_REJECTED` | HR Director | WARN | before=SUBMITTED, after=DRAFT | Yes |
| `SCORE_MANUAL_ADJUSTED` | HR Director | HIGH | before/after | Yes |
| `GRADE_ASSIGNED` | System (post-approval) | INFO | grade_no | No |
| `LOCALIZATION_KEY_UPDATED` | Super Admin | INFO | before/after | No |
| `AUDIT_LOG_QUERIED` | Auditor / any AUDIT_READ user | INFO | filters | No |

The audit table itself disallows UPDATE and DELETE for all roles (DB privilege model). Hash chain: `hash_current = hash(hash_prev || canonical_json(record))`.

---

## 9. Data Sensitivity Classification

| Entity (MVP 1) | Sensitivity | Encryption | Cross-tenant risk if leaked | MVP 1 controls |
| --- | --- | --- | --- | --- |
| Tenant metadata | LOW | TLS in transit | Low | Control-plane scoping |
| User PII (email, name) | MEDIUM (PII) | TLS + DB at rest | Privacy regulation exposure | RBAC + tenant scoping |
| Project metadata | LOW–MEDIUM | TLS + at rest | Reveals client engagement | Tenant scoping |
| Organization unit, position | MEDIUM | TLS + at rest | Reveals client structure | Tenant + project scoping; ABAC by dept |
| Job profile (purpose, duties, requirements) | MEDIUM | TLS + at rest | Reveals client know-how | Tenant scoping |
| Methodology factor/level (in tenant scope) | MEDIUM | TLS + at rest | Reveals client-specific approach | Tenant scoping |
| Methodology template (control plane) | LOW | TLS + at rest | HRLab IP | Control plane RBAC |
| Evaluation scores | MEDIUM | TLS + at rest | Reveals position value | Tenant + project scoping |
| Grade assignment | MEDIUM | TLS + at rest | Reveals internal valuation | Tenant + project scoping |
| Audit log | HIGH (integrity) | TLS + at rest + hash chain | Tampering = compliance breach | Append-only; no UPDATE/DELETE; chain verification |
| Salary fields (MVP 3 — foundation only in MVP 1) | VERY HIGH | Field-level encryption with tenant key | Direct financial harm | Separate permission; foundation: permission codes exist, no salary write paths in MVP 1 |
| Attachments (MVP 2) | HIGH | Object storage with signed URLs | Legal exposure | Out of MVP 1 |

**MVP 1 foundation for salary, but no salary surface:**
- The permission codes `SALARY_VIEW`, `SALARY_EDIT`, `SALARY_EXPORT`, `SALARY_SCENARIO_RUN` exist in the catalogue.
- They are not granted to any role by default (including Super Admin).
- No salary data is collected, stored, or displayed in MVP 1.
- This is intentional: it guarantees that *grade access never silently confers salary access* the moment MVP 3 ships.

---

## 10. Localization Requirements

### 10.1 Required locales (mandatory from MVP 1)

| Locale code | Display name | Script | UI direction | Date format | Decimal separator |
| --- | --- | --- | --- | --- | --- |
| `ru-RU` | Русский | Cyrillic | LTR | `DD.MM.YYYY` | `,` |
| `uz-Cyrl-UZ` | Ўзбек (кирилл) | Cyrillic | LTR | `DD.MM.YYYY` | `,` |
| `uz-Latn-UZ` | O'zbek (lotin) | Latin | LTR | `DD.MM.YYYY` | `,` |
| `en-US` | English | Latin | LTR | `MM/DD/YYYY` | `.` |

### 10.2 Translation scope in MVP 1

| Surface | Required in 4 locales for MVP 1? |
| --- | --- |
| UI strings (buttons, labels, menus, validation messages, empty states, error states) | **Yes — all 4** |
| System-defined methodology templates seeded by HRLab | **Yes — all 4** |
| Tenant-defined methodology factors/levels/grade titles | Primary locale required; others warnings only |
| Tenant-defined job profile content | Primary locale required; others optional |
| Audit log action labels | **Yes — all 4** |
| Email notifications | Out of MVP 1 (no notifications module in MVP 1) |
| Reports | Out of MVP 1 (no reports in MVP 1) |

### 10.3 Locale resolution order

1. User's explicit preference (stored on User entity).
2. Tenant primary locale.
3. Browser `Accept-Language` (first match among supported locales).
4. Platform default `ru-RU`.

### 10.4 Fallback rules

- If a UI key is missing in the resolved locale, fall back to `ru-RU`. In non-production environments, render a visible indicator; in production, render silently.
- If a domain content translation (e.g., factor name) is missing in the resolved locale, render the primary locale value with a small "[primary locale]" badge.

---

## 11. Reporting Requirements for MVP 1

MVP 1 does **not** include exportable Excel/PDF/Word reports (that is MVP 2). MVP 1 provides only minimal **in-app analytics** sufficient to demo progress:

| In-app view | Audience | Data |
| --- | --- | --- |
| Project workspace home counters | PM, Director | # positions, # profiles drafted, # profiles approved, # evaluations drafted, # evaluations approved, # grades assigned |
| Grade distribution (basic) | HR Director, PM | `{grade_no, count}` array; rendered as a simple horizontal bar list |
| Audit log table | Super Admin, Auditor, HR Director (tenant), Admin (tenant) | Paged audit entries with filters |

No exports, no PDF, no Word, no PowerPoint-ready tables in MVP 1.

---

## 12. Non-Functional Requirements

### 12.1 Performance

- p95 latency under 500 ms for any list endpoint returning up to 200 records.
- p95 latency under 1500 ms for evaluation total-score recomputation on approval.
- The platform must support 500 concurrent users across tenants in MVP 1 (one tenant at a time per session).

### 12.2 Scalability

- Horizontal scaling of `grading-api` pods behind a load balancer.
- Stateless API; tenant context resolved per request from JWT/context token.
- PostgreSQL HA (primary + replica) from day one.

### 12.3 Availability

- Target 99.5% monthly availability in MVP 1 (will tighten in later MVPs).
- Planned maintenance windows announced 48 hours in advance to all tenants.

### 12.4 Security

- All traffic over TLS 1.2+; HSTS enabled.
- JWT signed with rotated keys; max token lifetime 60 minutes for access, 12 hours for refresh.
- Tenant context token max lifetime 30 minutes; rotated on switch.
- Password policy: minimum 12 chars, complexity enforced; lockout after 5 failed attempts in 10 minutes.
- Backend enforces all permissions; frontend hiding is UX-only.
- Salary endpoints exist as code-level skeletons returning `403` for all callers in MVP 1.

### 12.5 Auditability

- Every action in the audit-event matrix (Section 8) writes an audit record before the transaction commits, in the same transaction.
- Audit table has DB-level grants preventing UPDATE/DELETE for all application roles.
- Hash chain verified weekly via a worker job; mismatch raises an alert.

### 12.6 Maintainability

- Modular monolith per architecture (`uz.hrlab.grading.<module>`).
- Backend code coverage at least 70% line coverage for MVP 1 modules, 85% for tenancy and access modules.
- Liquibase changelogs for control plane and tenant schemas.

### 12.7 Localization (NFR perspective)

- All UI strings keyed; no hardcoded text in source.
- i18n bundles per locale loaded lazily.
- Locale switch updates UI without a full page reload.

### 12.8 Accessibility

- WCAG 2.1 AA baseline for primary screens (project workspace, position catalog, job profile editor, methodology builder, evaluation matrix).
- Keyboard navigation supported for all critical flows.

### 12.9 Browser support

- Latest two major versions of Chrome, Edge, Firefox, Safari.
- No IE11 support.

### 12.10 Data retention (MVP 1 minimum)

- Audit log retained at least 7 years (statutory baseline for HR).
- Soft delete only for application data; hard delete via DBA-only runbook.

---

## 13. Out of Scope for MVP 1

The following are explicitly **not** in MVP 1. They land in MVP 2/3/4 per the roadmap.

| Out of MVP 1 | Lands in |
| --- | --- |
| Excel import of org structure / positions | MVP 2 |
| Excel/PDF/Word/PPT exports and reports | MVP 2 |
| Comments and threaded discussion on entities | MVP 2 |
| Attachments (files on profiles, positions) | MVP 2 |
| Multi-stage approvals workflow with parallel approvers | MVP 2 |
| Calibration sessions (committee table view, batch adjustments) | MVP 2 |
| Job analysis questionnaires and templated interviews | MVP 2 |
| Archive of projects | MVP 2 |
| Notification emails (SMTP integration) | MVP 2 |
| Salary range modeling, compa-ratio, range penetration | MVP 3 |
| Salary data ingest and field-level encryption operationalization | MVP 3 |
| Compensation scenarios and ФОТ before/after | MVP 3 |
| Red/green circle analytics | MVP 3 |
| Compensation dashboards | MVP 3 |
| AI job profile assistant | MVP 4 |
| AI factor suggestions | MVP 4 |
| Anomaly detection | MVP 4 |
| HRM / payroll / ERP integrations | MVP 4 |
| BI connector | MVP 4 |
| Advanced dashboards beyond grade distribution counter | MVP 4 |
| Client IdP SSO federation (SAML / external OIDC) | MVP 2 (basic) / MVP 4 (advanced) |
| `FORMULA_BASED` scoring mode | MVP 2 |
| Database-per-tenant isolation provisioning UI | MVP 3 (manual via DevOps in MVP 1 if a client requires) |
| Multi-stage methodology approval workflow | MVP 2 |
| Auto-suspend on cross-tenant abuse threshold | MVP 2 (alerting only in MVP 1) |
| Methodology recalculation scenario (apply new version to old evaluations) | MVP 2 |

---

## 14. Dependencies

### 14.1 Cross-team dependencies

| Dependency | Owner | Required by | Notes |
| --- | --- | --- | --- |
| OIDC provider (Keycloak or equivalent) | DevOps + security-engineer | Sprint 1 | Required for E2 |
| PostgreSQL HA cluster | DevOps + database-architect | Sprint 1 | Required for E1 |
| Liquibase tenant provisioning runner | database-architect + DevOps | Sprint 1 | Required for E1 |
| Vault/KMS for envelope encryption keys | DevOps + security-engineer | Sprint 1 (tenant key seeding) | Foundation; salary fields not used yet |
| Object storage (S3-compatible) | DevOps | Sprint 4 (audit hash chain artifacts, file uploads later) | Not strictly MVP 1, but used by audit verifier worker |
| Localization dictionary tooling | product-designer + frontend-engineer | Sprint 1 | i18next setup |
| Component library / design system | product-designer | Sprint 1 | wireframes + tokens |
| Permission catalogue source-of-truth | security-engineer + backend-engineer | Sprint 1 | derived from Section 7 |

### 14.2 External dependencies

| Dependency | Risk | Mitigation |
| --- | --- | --- |
| IdP availability | Outage blocks all logins | HA IdP; cache JWKs |
| KMS availability | Tenant provisioning blocked | KMS HA; runbook for delayed provisioning |

---

## 15. Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
| --- | --- | --- | --- |
| Cross-tenant data leak | Medium | Critical | Schema-per-tenant + RLS + repository convention + tenant isolation test suite as release gate |
| Engineer writes `findById` instead of `findByIdAndTenantId` | High | Critical | Architectural fitness function: static check on repository signatures; code review checklist; automated test against every repository |
| Approved methodology gets edited | Low | Critical | Status transition guards in domain + audit on every edit attempt; integration tests covering all edit endpoints on LOCKED entities |
| Evaluation total score is non-reproducible | Low | High | Backend recomputes on approval; raw inputs stored; reproducibility test in CI |
| MVP 1 scope creep (e.g., "just add Excel import") | High | High | PRD is the gate; out-of-scope list (Section 13) cited at every backlog grooming |
| Localization treated as final-stage polish | High | Medium | i18next + dictionary tables stood up Sprint 1; CI fails build on hardcoded strings beyond a small allowlist |
| Permission matrix drifts between code and PRD | Medium | High | Single source: a permission catalogue file derived from Section 7; backend uses it; QA tests against it |
| Frontend-only security | Medium | Critical | QA's tenant isolation suite hits backend directly bypassing UI; every approval endpoint tested without the frontend |
| Audit gaps | Medium | High | Audit-event matrix in Section 8 maps to integration tests; release gate fails if any matrix row lacks a test |
| Department scope (ABAC) misconfigured | Medium | High | Department Manager and Viewer personas have dedicated test scenarios |
| Auditor sees other tenants | Low | Critical | Auditor role hard-coded to tenant scope; cross-tenant requires Super Admin's separate permission |
| Performance under 500 concurrent users | Medium | Medium | Indexed `(tenant_id, project_id)` on every tenant-scoped table; query plan review in Sprint 4 |
| Liquibase changelog drift between tenants | Medium | High | Tenant provisioner re-applies the canonical tenant changelog; checksum verification per tenant on startup |
| Locale mismatch in committee (one member sees missing translations) | Medium | Low | Primary-locale required; fallback rendering with badge; methodology can ship with missing non-primary locales |

---

## 16. Definition of Ready (DoR) — checklist per user story

A backlog item is **Ready** only if all 15 boxes are checked:

1. Business value is articulated (one sentence, persona-anchored).
2. User persona is named (from Section 3).
3. Scope is delimited (what is in).
4. Out of scope is delimited (what is explicitly not).
5. Required permissions are listed by code (from Section 7).
6. Audit events are listed by code (from Section 8).
7. Data entities are identified (from architecture Section 9).
8. API endpoint(s) and HTTP methods specified.
9. UI states defined: loading, empty, error, no-access, locked.
10. Localization impact stated (any new UI strings? any new translatable domain fields?).
11. Acceptance criteria written in Given/When/Then form, each testable.
12. Edge cases enumerated (missing fields, locked entity edits, cross-tenant attempts).
13. Tenant isolation impact assessed (is this entity tenant-scoped? project-scoped? department-scoped?).
14. Salary sensitivity assessed (must be "no salary" in MVP 1).
15. Dependencies (upstream/downstream stories, infra) listed.

---

## 17. Definition of Done (DoD) — checklist per user story

A backlog item is **Done** only if all 12 boxes are checked:

1. All Given/When/Then acceptance criteria pass in CI.
2. Backend permission enforcement implemented and unit-tested.
3. Tenant isolation tested with at least one positive and one negative case (cross-tenant attempt yields `404`/`403` + audit).
4. Audit events generated where required; verified by integration test.
5. UI handles loading, empty, error, no-access, and locked states.
6. Localization keys exist in all 4 locales (UI strings); primary locale required for domain content; missing non-primary domain content shows the badge.
7. Unit tests cover business rules; integration tests cover endpoints; e2e covers the happy path.
8. Sensitive data protected (in MVP 1 mostly means: PII not logged, no salary surfaces).
9. Documentation updated (API docs auto-generated; PRD changelog if scope shifted).
10. Product owner acceptance recorded in the sprint review.
11. No critical or high SAST/dependency findings open.
12. Story merged behind a feature flag where applicable.

---

## 18. 4-Sprint Plan (2-week sprints, vertical slices)

Total: 8 weeks. Each sprint ends with a working, demo-able vertical slice. Tenant isolation and audit are wired in **Sprint 1**, not deferred.

### Sprint 1 (Weeks 1–2) — Foundation: tenant + access + locale + skeleton workspace

**Sprint goal:** A HRLab Super Admin can create a tenant and a user; users can sign in via OIDC; backend enforces tenant context; UI loads in 4 locales; audit trail is live for actions executed.

**Epics in play:** E1 (Tenant isolation), E2 (Users/roles/permissions), E10 (Audit), E11 (Localization).

**User stories in scope:**
- MVP1-E1-1, MVP1-E1-2, MVP1-E1-3
- MVP1-E2-1, MVP1-E2-2, MVP1-E2-3
- MVP1-E10-1, MVP1-E10-3
- MVP1-E11-1, MVP1-E11-2

**Backend tasks:**
- B1.1 Set up Spring Boot 3 modular monolith skeleton with `tenancy`, `access`, `audit`, `localization` modules.
- B1.2 Implement control-plane Liquibase changelog (tenants, users, user_tenant_memberships, global_roles, global_permissions, system_audit_log).
- B1.3 Implement tenant-schema baseline changelog (audit_log, plus empty placeholders for projects, positions, etc.).
- B1.4 Implement tenant provisioning use case (create schema, run baseline migrations, seed dictionaries, create tenant data encryption key envelope).
- B1.5 Wire OAuth2 Resource Server with JWT validation.
- B1.6 Implement tenant context token issuance (`POST /api/v1/session/tenant-context`).
- B1.7 Implement permission enforcement aspect / interceptor at controller layer.
- B1.8 Implement ABAC policy framework (initial policies: tenant ownership; placeholder for department scope).
- B1.9 Implement append-only audit table with hash chaining and DB privilege restrictions.
- B1.10 Implement `CROSS_TENANT_ACCESS_ATTEMPT` interceptor.
- B1.11 Seed permission catalogue from Section 7.

**Frontend tasks:**
- F1.1 React 18 + Vite + TypeScript scaffold; routing skeleton.
- F1.2 i18next setup with bundles for `ru-RU`, `uz-Cyrl-UZ`, `uz-Latn-UZ`, `en-US`.
- F1.3 Sign-in via OIDC PKCE flow.
- F1.4 Tenant switcher.
- F1.5 User & access management UI (list, create, assign role) for Super Admin and Tenant Admin.
- F1.6 Audit log table with filters (date, actor, action).
- F1.7 Empty / loading / error / no-access state components in the design system.

**Designer tasks:**
- D1.1 Information architecture map of MVP 1 (which persona sees which screen).
- D1.2 Design tokens (colors, spacing, typography) anchored to the brand.
- D1.3 Sign-in, tenant switcher, user management, audit log wireframes and high-fidelity mockups.
- D1.4 Empty/loading/error/no-access state components.
- D1.5 Localization key glossary (initial 200 keys).

**QA tasks:**
- Q1.1 Tenant isolation test pack (Java + REST Assured): direct UUID probing, manipulated context token, stale token, body-injected tenant_id.
- Q1.2 OIDC end-to-end happy path.
- Q1.3 Permission denial tests: every endpoint, every role lacking the required permission.
- Q1.4 Audit-hash chain integrity test.
- Q1.5 Cross-locale UI sanity check (toggle locale, confirm strings update).

**Dependencies / risks:**
- IdP availability (Keycloak) — needed by Day 3.
- KMS availability — needed by Day 7 for tenant key seeding.

**Demo scenario:**
1. Super Admin signs in.
2. Creates tenant `Acme`.
3. Creates Client Company Admin user in Acme.
4. Client Company Admin signs in via OIDC, sees only Acme.
5. Audit log shows every step.
6. Locale switcher renders all four locales.

### Sprint 2 (Weeks 3–4) — Project workspace + organization + position catalog

**Sprint goal:** A PM can create a project and members; analysts can build an organization tree and a position catalog; HR Director and Department Manager see only what they are allowed to see.

**Epics in play:** E3 (Project workspace), E4 (Organization), E5 (Position catalog), continuing E2/E10/E11.

**User stories in scope:**
- MVP1-E3-1, MVP1-E3-2
- MVP1-E4-1, MVP1-E4-2
- MVP1-E5-1, MVP1-E5-2

**Backend tasks:**
- B2.1 `project`, `organization`, `position` modules.
- B2.2 Repository convention enforced: `findByIdAndTenantIdAndProjectId(...)` for all.
- B2.3 ABAC department-scope policy.
- B2.4 Org-cycle and parent-out-of-scope validation.
- B2.5 Audit events for org/position lifecycles.

**Frontend tasks:**
- F2.1 Project list and create-project modal.
- F2.2 Project workspace shell (counters skeleton).
- F2.3 Organization tree component (expandable, lazy-loaded).
- F2.4 Position catalog table with filters and detail panel.
- F2.5 Department scope respected in tree view.

**Designer tasks:**
- D2.1 Project list, project workspace, organization tree, position catalog wireframes & mockups.
- D2.2 Empty/locked/no-access variants for these screens.

**QA tasks:**
- Q2.1 Project creation + ABAC scoping tests.
- Q2.2 Department Manager scope tests (positive + negative).
- Q2.3 Org cycle detection test.
- Q2.4 Localization snapshot tests.

**Demo scenario:**
1. PM creates project "Acme Grading 2026 Q1".
2. PM invites Analyst, HR Director, Specialist, Department Manager.
3. Analyst builds org tree (3 levels).
4. Specialist sees positions tenant-wide.
5. Department Manager sees only their department's positions.

### Sprint 3 (Weeks 5–6) — Job profile + methodology builder

**Sprint goal:** Job profiles can be authored, submitted, approved; a methodology version can be configured, approved, locked, and a new version cloned.

**Epics in play:** E6 (Job profile), E7 (Methodology builder), continuing E10/E11.

**User stories in scope:**
- MVP1-E6-1, MVP1-E6-2
- MVP1-E7-1, MVP1-E7-2, MVP1-E7-3, MVP1-E7-4, MVP1-E7-5

**Backend tasks:**
- B3.1 `jobprofile` module: create/edit/submit/approve.
- B3.2 `methodology` module: methodology, version, factor, factor_level, grade_band entities.
- B3.3 Scoring mode enum and validation rules.
- B3.4 Status state machine for methodology version (DRAFT → APPROVED → LOCKED), with edit guards.
- B3.5 New-version-from-locked deep-copy use case.
- B3.6 Multilingual fields stored in `*_translations` tables.
- B3.7 Audit events for methodology and profile lifecycles.
- B3.8 Permission `EVALUATION_SCORE_OVERRIDE` distinct from `EVALUATION_APPROVE` (seeded, not used yet — used in Sprint 4).

**Frontend tasks:**
- F3.1 Job profile editor with multilingual tab strip (4 locales).
- F3.2 Profile submit/approve actions.
- F3.3 Methodology builder: factor table, level editor, weight inputs, scoring mode selector, grade band editor.
- F3.4 Locked state UI for methodology and profile.
- F3.5 Translation completeness indicator per content row.

**Designer tasks:**
- D3.1 Job profile editor mockups (incl. multilingual tabs).
- D3.2 Methodology builder mockups.
- D3.3 Grade band editor mockups.
- D3.4 Locked state visual.

**QA tasks:**
- Q3.1 Methodology lock enforcement tests.
- Q3.2 Grade band overlap detection test.
- Q3.3 Weight sum validation test.
- Q3.4 New-version cloning test.
- Q3.5 Multilingual content persistence tests.
- Q3.6 Profile approval lifecycle test.

**Demo scenario:**
1. Specialist authors a profile, submits.
2. HR Director approves.
3. Consultant builds an 8-factor methodology in all 4 locales.
4. PM approves; methodology locks.
5. Consultant attempts to edit — blocked.
6. Consultant clones into v2.

### Sprint 4 (Weeks 7–8) — Scoring engine + grade assignment + analytics counter + audit verification + release gate

**Sprint goal:** Evaluators can score positions, HR Director approves and grades are assigned; reproducibility test passes; tenant isolation suite passes as release gate.

**Epics in play:** E8 (Scoring engine), E9 (Grade assignment), E10 (Audit verification), continuing E11.

**User stories in scope:**
- MVP1-E8-1, MVP1-E8-2, MVP1-E8-3, MVP1-E8-4, MVP1-E8-5
- MVP1-E9-1, MVP1-E9-2, MVP1-E9-3
- MVP1-E10-2

**Backend tasks:**
- B4.1 `evaluation` module: evaluation, evaluation_score, status state machine.
- B4.2 Deterministic scoring service supporting `DIRECT_POINTS`, `WEIGHTED_POINTS`, `WEIGHTED_SCALE`.
- B4.3 Override use case with reason capture.
- B4.4 Grade assignment use case (run on approval).
- B4.5 Analytics endpoint: grade distribution count.
- B4.6 Audit hash-chain verifier worker job.
- B4.7 Reproducibility test harness (recompute on demand and compare).

**Frontend tasks:**
- F4.1 Evaluation matrix UI (positions × factors; level pickers; running total).
- F4.2 Submit/approve actions with reason capture for override.
- F4.3 Grade pyramid (basic bar list).
- F4.4 Workspace counters wired to live data.
- F4.5 Audit log polish; export-disabled placeholder.

**Designer tasks:**
- D4.1 Evaluation matrix mockups.
- D4.2 Grade pyramid (basic) mockups.
- D4.3 Workspace home with counters.
- D4.4 Final polish across all MVP 1 screens.

**QA tasks:**
- Q4.1 Scoring reproducibility test (20+ positions; recompute equals stored).
- Q4.2 Override flow tests.
- Q4.3 No-grade-band-match scenario test.
- Q4.4 Full tenant isolation regression suite (release gate).
- Q4.5 Full audit coverage regression (release gate).
- Q4.6 Permission matrix regression: every cell in Section 7 has at least one passing test.
- Q4.7 Localization regression: all 4 locales render full happy path.
- Q4.8 Performance test: 500 concurrent users, key endpoints under target latencies.

**Demo scenario:**
1. Committee Member opens evaluation matrix.
2. Picks levels for 5 positions in a session.
3. Submits; HR Director approves.
4. Grades are assigned; pyramid updates.
5. Auditor opens audit log and traces a position from creation to grade.
6. Cross-tenant probe blocked and audited.

---

## 19. Suggested Tasks per Engineering Discipline

### 19.1 Backend tasks (summary)

- Modular monolith scaffolding (Spring Boot 3, Java 21, Spring Modulith style).
- Tenancy module: provisioning, tenant context token, RLS session variable.
- Access module: OIDC, JWT, permission catalogue, ABAC policies.
- Audit module: append-only log, hash chain, verifier worker.
- Localization module: dictionary tables, locale resolution, fallback rendering.
- Project, Organization, Position, Job Profile, Methodology, Evaluation, Grade modules.
- Repository convention enforcement: `findByIdAndTenantIdAndProjectId` only.
- Status state machines with edit guards on LOCKED entities.
- Salary endpoints reserved but disabled (return 403).
- Liquibase: control-plane changelog + tenant-schema changelog + tenant provisioner.
- API documentation auto-generated (OpenAPI).

### 19.2 Frontend tasks (summary)

- React 18 + Vite + TypeScript scaffold.
- i18next with 4 locales; lazy bundles; runtime switch.
- OIDC PKCE flow; tenant switcher; permission-aware routing.
- Component library: empty/loading/error/no-access/locked states.
- Screens: sign-in, tenant switcher, user mgmt, audit log, project list, project workspace, org tree, position catalog, job profile editor, methodology builder, factor/level/grade-band editor, evaluation matrix, grade pyramid (basic).
- Backend-driven feature flags for salary surfaces (kept hidden in MVP 1).
- Multilingual tab strip pattern for domain entities.

### 19.3 Designer tasks (summary)

- Information architecture covering all 11 personas.
- Design tokens (color, type, spacing) per brand.
- High-fidelity mockups for each MVP 1 screen, in `ru-RU` and `en-US` minimum.
- Empty/loading/error/no-access/locked state patterns.
- Multilingual tab strip pattern.
- Permission-aware UI patterns (visible-but-disabled vs hidden).
- Accessibility annotations (WCAG 2.1 AA targets).
- Audit log visual treatment.

### 19.4 QA test cases (summary)

- Tenant isolation suite (release gate).
- Permission matrix coverage (per Section 7).
- Audit-event coverage (per Section 8).
- Reproducibility tests for scoring engine.
- Methodology lock enforcement.
- Profile approval lifecycle.
- Evaluation approval lifecycle.
- Grade band overlap/gap detection.
- Localization fallback tests.
- ABAC department-scope tests.
- Cross-tenant access probing (manipulated ids, body fields, stale tokens).
- Performance: 500 concurrent users, p95 latencies.
- Accessibility: WCAG 2.1 AA on primary screens.
- Browser support: latest two of Chrome, Edge, Firefox, Safari.

---

## 20. Open Questions for Other Agents

| # | Question | Owner |
| --- | --- | --- |
| 1 | Auto-suspend on cross-tenant abuse threshold — MVP 1 or MVP 2? PRD currently flags MVP 2 with MVP 1 alerting only. | security-engineer |
| 2 | Choice of IdP for MVP 1: Keycloak self-hosted vs Auth0? | security-engineer + DevOps |
| 3 | Audit table partitioning strategy (by month? by tenant?). | database-architect |
| 4 | RLS policy granularity: tenant + project + department, or tenant only with backend filtering for project/department? | database-architect + security-engineer |
| 5 | Should the methodology builder allow a `CUSTOM` template to be saved back as a HRLab global template, or is that MVP 2? PRD assumes MVP 2. | hr-product-owner (revisit) |
| 6 | Acceptable error code surface (do we expose `LEVEL_FACTOR_MISMATCH` etc. to UI, or generic `VALIDATION_FAILED` with details)? | backend-engineer + frontend-engineer |
| 7 | Department scope in JWT vs server-side lookup — token bloat vs latency tradeoff. | backend-engineer + security-engineer |
| 8 | Audit visibility of `before`/`after` for entities containing PII (e.g., user email) — should auditors see raw email? | security-engineer |
| 9 | Single tenant primary locale or multiple (e.g., bilingual tenant)? PRD currently assumes single. | product-designer + hr-product-owner |
| 10 | Translation completeness gate at approve-time: PRD currently requires only primary locale. Re-confirm with delivery team. | hr-product-owner (revisit) |

---

**End of MVP 1 PRD.**
