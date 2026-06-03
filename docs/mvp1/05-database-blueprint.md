# MVP 1 — Database Architecture Blueprint
**Project:** grading.hrlab.uz
**Owner:** database-architect
**Status:** Draft v1.0 (MVP 1)
**Date:** 2026-05-23
**Single source of truth:** `архитектура.md` (sections 7, 8.5, 9, 12, 15; ADR-001, ADR-004, ADR-005, ADR-007, ADR-008, ADR-012)

---

## 1. Database Objectives

The MVP 1 database must:

1. **Enforce strict multi-tenant isolation** between unrelated company-clients (banks, holdings, universities, telecoms, public sector) at the schema, row and column level.
2. **Provide a stable data contract** for backend JPA entities — the schema is the contract, not the Java entities.
3. **Guarantee reproducible scoring**: every approved evaluation must yield the same total_score forever, regardless of future methodology edits.
4. **Make approved methodology versions immutable** at the database level (constraint-enforced, not "trust the app").
5. **Protect salary data foundation columns** from leakage even in MVP 1 (encryption-ready columns, redacted audit, separate permission code), even though salary functionality lands in MVP 3.
6. **Provide an append-only audit log** that the runtime DB role cannot UPDATE or DELETE.
7. **Be ready for PostgreSQL RLS** to be switched on without schema rewrites — `tenant_id` everywhere, session variable contract pre-defined.
8. **Support 4 locales** (ru-RU, uz-Cyrl-UZ, uz-Latn-UZ, en-US) for every translatable methodology artifact.
9. **Be controlled exclusively by Liquibase** — no manual DDL in any environment.
10. **Support both schema-per-tenant (default) and database-per-tenant (enterprise)** isolation modes via the same Liquibase template changelog.

**Anti-goals for MVP 1:** salary range tables (MVP 3), import staging (MVP 2), AI staging (MVP 4), materialized views (post-MVP 1), partitioning of business tables (only audit_logs is partitioned in MVP 1).

---

## 2. Multi-Tenant Database Strategy (Hybrid)

Per ADR-001, MVP 1 implements **hybrid multi-tenancy** with three layers:

### 2.1 Control plane — shared `public` schema (single database `grading_control_db`)
Holds **only** HRLab-owned metadata and access mapping. Never contains a company-client's grading business data (no positions, no evaluations, no salaries).

### 2.2 Data plane — schema-per-tenant (default)
Each company-client onboarded with `isolation_mode = SCHEMA` gets a dedicated schema `tenant_{slug}` inside `grading_client_db`. All tenant business tables live there. `tenant_id` is still NOT NULL on every row (defense-in-depth).

### 2.3 Data plane — database-per-tenant (premium / enterprise)
Sensitive enterprise clients are provisioned with `isolation_mode = DATABASE`. Same tenant schema DDL is applied to a dedicated database (e.g. `grading_tenant_acme_db`) with separate backups, separate connection pool, separate encryption keys, and separate RLS policies. From the application's perspective, the routing layer selects the connection by `tenant_id`; the schema definition is identical.

### 2.4 Selection rules
| Client profile                                                    | Isolation mode |
|-------------------------------------------------------------------|----------------|
| SMB, universities, single-project pilots                          | `SCHEMA`       |
| Banks, telecoms, holdings with strict compliance                  | `DATABASE`     |
| Clients requiring separate backup/restore boundary or BYO-KMS     | `DATABASE`     |
| Clients with > ~10 000 positions or massive salary datasets       | `DATABASE`     |

The choice is recorded in `public.tenants.isolation_mode` and is **permanent** — moving between modes requires explicit migration plan and Liquibase scripts (not in MVP 1 scope).

### 2.5 Defense-in-depth layers (cumulative, all 4 active in MVP 1)
1. Connection-level: app picks the correct database/schema by tenant context.
2. Schema-level: schema-per-tenant prevents accidental cross-tenant joins.
3. Row-level: every business row has `tenant_id NOT NULL`; RLS-ready (RLS enforcement enabled at end of MVP 1 in staging).
4. Application-level: repositories MUST filter by `tenant_id` (enforced by backend-engineer + qa-engineer).

---

## 3. Control Plane Schema (MVP 1)

Database: `grading_control_db`, schema: `public`.

### 3.1 Tables in scope
1. `tenants`
2. `client_companies`
3. `users`
4. `user_tenant_memberships`
5. `roles`
6. `permissions`
7. `role_permissions`
8. `user_roles` (per-membership role assignment)
9. `methodology_templates`
10. `methodology_template_factors`
11. `methodology_template_factor_levels`
12. `localization_messages`
13. `system_audit_log`
14. `tenant_migration_status`

### 3.2 Key control-plane design rules
- **No client business data here.** This schema must never receive a row containing a position title, evaluation score, salary number, or job profile of a company-client.
- **Users are global identities.** A user record exists once; access to a tenant is granted via `user_tenant_memberships`.
- **Tenant context resolution happens here.** Login flow looks up memberships in this schema, then issues a short-lived tenant-context token (per ADR-006, architecture §8.1).
- **`system_audit_log` is partitioned by month** (range partition on `created_at`) and append-only at the role level.
- **Roles + permissions are global**, mapped via `role_permissions`. Tenant-specific role overrides will be added in MVP 2 (out of scope here).
- **`methodology_templates` are HRLab-owned global templates** (CLASSIC_8_FACTOR, EXTENDED_11_CRITERIA, CUSTOM_BLANK). They are cloned into a tenant schema as a starting point; the tenant copy is independent thereafter.

---

## 4. Tenant Schema (MVP 1)

Per provisioned tenant, schema `tenant_{slug}` (or dedicated DB) contains the following MVP 1 tables.

### 4.1 Tables in scope
1. `projects`
2. `departments`
3. `positions`
4. `job_profiles`
5. `job_profile_revisions`
6. `methodologies`
7. `methodology_versions`
8. `factors`
9. `factor_levels`
10. `factor_translations`
11. `factor_level_translations`
12. `grade_structures`
13. `grades`
14. `grade_bands`
15. `evaluations`
16. `evaluation_scores`
17. `calibration_adjustments`
18. `approvals`
19. `comments`
20. `attachments`
21. `audit_logs`

**Total: 14 control-plane tables + 21 tenant-schema tables = 35 tables in MVP 1.**

---

## 5. Detailed Table Specifications

> Notation: every column listed as "standard audit columns" expands to:
> `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`,
> `created_by UUID NULL`,
> `updated_at TIMESTAMPTZ NULL`,
> `updated_by UUID NULL`,
> `row_version BIGINT NOT NULL DEFAULT 0` (optimistic locking).
> "Soft archive columns" expands to `archived_at TIMESTAMPTZ NULL`, `archived_by UUID NULL`.

### 5.1 Control plane

#### `public.tenants`
| Column                 | Type            | Constraints / notes                                                    |
|------------------------|-----------------|------------------------------------------------------------------------|
| id                     | UUID            | PK                                                                     |
| slug                   | VARCHAR(64)     | NOT NULL, UNIQUE, regex `^[a-z][a-z0-9_]{2,63}$`, used in schema name  |
| display_name           | VARCHAR(255)    | NOT NULL                                                               |
| isolation_mode         | VARCHAR(16)     | NOT NULL, CHECK IN ('SCHEMA','DATABASE')                               |
| schema_name            | VARCHAR(80)     | NOT NULL when isolation_mode='SCHEMA'                                  |
| database_name          | VARCHAR(80)     | NOT NULL when isolation_mode='DATABASE'                                |
| status                 | VARCHAR(32)     | NOT NULL, CHECK IN ('PROVISIONING','ACTIVE','SUSPENDED','ARCHIVED')    |
| default_locale         | VARCHAR(20)     | NOT NULL, CHECK IN locale set                                          |
| encryption_key_ref     | VARCHAR(200)    | NULL in MVP 1 (KMS reference, populated in MVP 3)                      |
| object_storage_prefix  | VARCHAR(200)    | NULL in MVP 1                                                          |
| standard audit columns | —               | —                                                                      |
| soft archive columns   | —               | —                                                                      |

Constraints: `uq_tenants_slug`, `uq_tenants_schema_name`, `chk_tenants_isolation_target` (exactly one of schema_name/database_name not null based on isolation_mode).
Indexes: `idx_tenants_status`, `idx_tenants_created_at`.

#### `public.client_companies`
| Column        | Type         | Notes                                                       |
|---------------|--------------|-------------------------------------------------------------|
| id            | UUID         | PK                                                          |
| tenant_id     | UUID         | NOT NULL, FK → tenants(id), ON DELETE RESTRICT              |
| legal_name    | VARCHAR(500) | NOT NULL                                                    |
| brand_name    | VARCHAR(255) | NULL                                                        |
| industry      | VARCHAR(100) | NULL (free text in MVP 1; dictionary in MVP 2)              |
| country_code  | CHAR(2)      | NULL                                                        |
| tax_id        | VARCHAR(50)  | NULL                                                        |
| standard audit columns | —   | —                                                           |

Constraint: `uq_client_companies_tenant` (one company per tenant in MVP 1 — multi-company per tenant deferred).
Index: `idx_client_companies_tenant`.

#### `public.users`
| Column        | Type         | Notes                                                            |
|---------------|--------------|------------------------------------------------------------------|
| id            | UUID         | PK                                                               |
| email         | VARCHAR(320) | NOT NULL, UNIQUE (citext-style — apply `LOWER()` index)          |
| external_idp_subject | VARCHAR(255) | NULL — Keycloak/OIDC `sub`                                 |
| full_name     | VARCHAR(255) | NOT NULL                                                         |
| status        | VARCHAR(32)  | NOT NULL, CHECK IN ('ACTIVE','INVITED','DISABLED','LOCKED')      |
| default_locale| VARCHAR(20)  | NOT NULL                                                         |
| last_login_at | TIMESTAMPTZ  | NULL                                                             |
| standard audit columns | —   | —                                                                |

Note: `users` is **global PII**, not tenant business data. No salary fields. No client business data.

#### `public.user_tenant_memberships`
| Column            | Type        | Notes                                                                |
|-------------------|-------------|----------------------------------------------------------------------|
| id                | UUID        | PK                                                                   |
| user_id           | UUID        | NOT NULL, FK → users(id)                                             |
| tenant_id         | UUID        | NOT NULL, FK → tenants(id)                                           |
| status            | VARCHAR(32) | NOT NULL, CHECK IN ('ACTIVE','INVITED','SUSPENDED','REVOKED')         |
| invited_by        | UUID        | NULL                                                                  |
| salary_data_permission | BOOLEAN | NOT NULL DEFAULT FALSE — controls SALARY_* permission set            |
| standard audit columns | —      | —                                                                     |

Unique: `uq_user_tenant_memberships_user_tenant (user_id, tenant_id)`.
Index: `idx_utm_tenant`, `idx_utm_user`.

#### `public.roles`, `public.permissions`, `public.role_permissions`, `public.user_roles`
- `roles(id UUID PK, code VARCHAR(64) UNIQUE NOT NULL, name VARCHAR(128) NOT NULL, scope VARCHAR(16) CHECK IN ('PLATFORM','TENANT'), …)`
- `permissions(id UUID PK, code VARCHAR(100) UNIQUE NOT NULL, resource VARCHAR(64), action VARCHAR(32), …)`
- `role_permissions(role_id, permission_id)` composite PK.
- `user_roles(id UUID, user_tenant_membership_id UUID FK, role_id UUID FK)` with unique `(membership_id, role_id)`. A user can have N roles within one tenant.

#### `public.methodology_templates`, `public.methodology_template_factors`, `public.methodology_template_factor_levels`
HRLab-owned global blueprints. Cloned at project creation into tenant.methodologies (deep copy). Same column shape as tenant `factors` / `factor_levels` minus tenant/project fields.

#### `public.localization_messages`
| Column   | Type         | Notes                                              |
|----------|--------------|----------------------------------------------------|
| id       | UUID         | PK                                                 |
| key      | VARCHAR(200) | NOT NULL                                           |
| locale   | VARCHAR(20)  | NOT NULL                                           |
| value    | TEXT         | NOT NULL                                           |
| context  | VARCHAR(64)  | NULL — e.g. `UI`, `REPORT`, `ERROR`                |
| standard audit columns | —  | —                                          |

Unique: `uq_localization_messages_key_locale (key, locale)`.
Index: `idx_localization_messages_locale`.

#### `public.system_audit_log` (partitioned BY RANGE on created_at, monthly)
Captures platform-level events (tenant created, user invited, role assigned, login). Tenant business events go to `tenant_{x}.audit_logs`.

#### `public.tenant_migration_status`
Tracks which Liquibase changesets have been applied to each tenant schema/db. One row per (tenant, changeset_id, status, applied_at, checksum).

### 5.2 Tenant schema — key tables

#### `tenant_{x}.projects`
| Column        | Type         | Notes                                                        |
|---------------|--------------|--------------------------------------------------------------|
| id            | UUID         | PK                                                           |
| tenant_id     | UUID         | NOT NULL — must equal session tenant                          |
| code          | VARCHAR(64)  | NOT NULL                                                     |
| name          | VARCHAR(255) | NOT NULL                                                     |
| status        | VARCHAR(32)  | NOT NULL, CHECK IN ('DRAFT','ACTIVE','LOCKED','ARCHIVED')    |
| primary_locale | VARCHAR(20) | NOT NULL                                                     |
| start_date    | DATE         | NULL                                                         |
| end_date      | DATE         | NULL                                                         |
| standard audit columns | — | —                                                          |
| soft archive columns   | — | —                                                          |

Unique: `uq_projects_tenant_code (tenant_id, code)`.
Indexes: `idx_projects_tenant`, `idx_projects_tenant_status`.

#### `tenant_{x}.departments`
Self-referencing tree.
Key cols: `id`, `tenant_id`, `project_id` (FK → projects), `parent_id` (nullable, FK self), `code`, `name`, `type`, `path` (LTREE or materialized text path for fast subtree queries), `level INT`, `status`, standard audit.
Unique: `uq_departments_tenant_project_code (tenant_id, project_id, code)`.
Indexes: `idx_departments_tenant_project`, `idx_departments_parent`, `idx_departments_path` (GiST on LTREE).
CHECK: `chk_departments_self_parent (id <> parent_id)`.

#### `tenant_{x}.positions`
Key cols: `id`, `tenant_id`, `project_id`, `department_id` (FK), `code`, `title`, `job_family`, `status` ('DRAFT','ACTIVE','EVALUATED','APPROVED','ARCHIVED'), `headcount INT`, standard audit, soft archive.
Unique: `uq_positions_tenant_project_code`.
Indexes: `idx_positions_tenant_project`, `idx_positions_tenant_project_status`, `idx_positions_department`.

#### `tenant_{x}.job_profiles` + `tenant_{x}.job_profile_revisions`
`job_profiles` holds the latest approved revision pointer; `job_profile_revisions` keeps full history.
`job_profiles`: `id`, `tenant_id`, `project_id`, `position_id` UNIQUE per project, `current_revision_id` FK, `status`.
`job_profile_revisions`: `id`, `tenant_id`, `project_id`, `job_profile_id`, `revision_no` INT, `purpose` TEXT, `duties` JSONB, `requirements` JSONB, `interactions` JSONB, `approved_at`, `approved_by`, standard audit.
Unique: `uq_jpr_profile_revision (job_profile_id, revision_no)`.

#### `tenant_{x}.methodologies` + `tenant_{x}.methodology_versions`
`methodologies`: container — `id`, `tenant_id`, `project_id` (NULL if methodology is tenant-global library, NOT NULL if project-scoped), `name`, `model_type` CHECK IN ('CLASSIC_8_FACTOR','EXTENDED_11_CRITERIA','CUSTOM'), `current_version_id` FK, standard audit.

`methodology_versions`: **the immutability boundary.**
| Column                 | Type           | Notes                                                                                 |
|------------------------|----------------|---------------------------------------------------------------------------------------|
| id                     | UUID           | PK                                                                                    |
| tenant_id              | UUID           | NOT NULL                                                                              |
| project_id             | UUID           | NULL/NOT NULL same as parent methodology                                              |
| methodology_id         | UUID           | NOT NULL, FK → methodologies                                                          |
| version_no             | INT            | NOT NULL                                                                              |
| status                 | VARCHAR(32)    | NOT NULL, CHECK IN ('DRAFT','APPROVED','LOCKED','SUPERSEDED','ARCHIVED')              |
| scoring_mode           | VARCHAR(32)    | NOT NULL, CHECK IN ('DIRECT_POINTS','WEIGHTED_POINTS','WEIGHTED_SCALE')               |
| max_total_score        | NUMERIC(12,4)  | NOT NULL                                                                              |
| approved_at            | TIMESTAMPTZ    | NULL                                                                                  |
| approved_by            | UUID           | NULL                                                                                  |
| locked_at              | TIMESTAMPTZ    | NULL                                                                                  |
| standard audit columns | —              | —                                                                                     |

Unique: `uq_mv_methodology_version (methodology_id, version_no)`.
**Immutability enforcement (MVP 1):** dedicated PL/pgSQL trigger `trg_methodology_version_lock` BEFORE UPDATE OR DELETE: raises exception if OLD.status IN ('APPROVED','LOCKED','SUPERSEDED') and any column other than `status` (transitions APPROVED → SUPERSEDED only) is being changed. Also blocks DELETE on any non-DRAFT row. Same trigger pattern on `factors` and `factor_levels` referencing locked versions.

#### `tenant_{x}.factors`
| Column                 | Type           | Notes                                                                |
|------------------------|----------------|----------------------------------------------------------------------|
| id                     | UUID           | PK                                                                   |
| tenant_id              | UUID           | NOT NULL                                                             |
| project_id             | UUID           | NULL/NOT NULL same as parent                                          |
| methodology_version_id | UUID           | NOT NULL, FK → methodology_versions(id) — **versioned, not mutable** |
| code                   | VARCHAR(64)    | NOT NULL                                                             |
| weight                 | NUMERIC(9,4)   | NOT NULL DEFAULT 1.0                                                 |
| max_points             | NUMERIC(12,4)  | NOT NULL                                                             |
| display_order          | INT            | NOT NULL                                                             |
| is_required            | BOOLEAN        | NOT NULL DEFAULT TRUE                                                |
| standard audit columns | —              | —                                                                    |

Unique: `uq_factors_mv_code (methodology_version_id, code)`.
Indexes: `idx_factors_mv`, `idx_factors_tenant_project`.

#### `tenant_{x}.factor_levels`
| Column            | Type           | Notes                                                              |
|-------------------|----------------|--------------------------------------------------------------------|
| id                | UUID           | PK                                                                 |
| tenant_id         | UUID           | NOT NULL                                                           |
| project_id        | UUID           | NULL/NOT NULL                                                      |
| factor_id         | UUID           | NOT NULL, FK → factors                                             |
| level_code        | VARCHAR(32)    | NOT NULL — e.g. 'A','B','C1' or '1','2','3'                        |
| points            | NUMERIC(12,4)  | NOT NULL — DIRECT_POINTS / WEIGHTED_POINTS                         |
| scale_value       | NUMERIC(12,4)  | NULL — WEIGHTED_SCALE                                              |
| display_order     | INT            | NOT NULL                                                           |
| standard audit columns | —         | —                                                                  |

Unique: `uq_factor_levels_factor_code (factor_id, level_code)`.
CHECK: `chk_factor_levels_points_nonneg (points >= 0)`.

#### `tenant_{x}.factor_translations` and `tenant_{x}.factor_level_translations`
| Column        | Type         | Notes                                            |
|---------------|--------------|--------------------------------------------------|
| id            | UUID         | PK                                               |
| tenant_id     | UUID         | NOT NULL                                         |
| factor_id (or factor_level_id) | UUID | NOT NULL                            |
| locale        | VARCHAR(20)  | NOT NULL CHECK IN locale set                     |
| name / label  | VARCHAR(500) | NOT NULL                                         |
| description   | TEXT         | NULL                                             |

Unique: `uq_factor_translations_factor_locale (factor_id, locale)`.
Index: `idx_factor_translations_locale`.

#### `tenant_{x}.grade_structures`, `grades`, `grade_bands`
`grade_structures`: `id`, `tenant_id`, `project_id`, `code`, `name`, `status` ('DRAFT','APPROVED','LOCKED','ARCHIVED'), `version_no`, standard audit.
Unique: `uq_grade_structures_tenant_project_code`.

`grades`: `id`, `tenant_id`, `project_id`, `grade_structure_id` FK, `grade_no` INT, `title` VARCHAR.
Unique: `uq_grades_structure_no (grade_structure_id, grade_no)`.

`grade_bands`: maps score → grade.
| Column              | Type           | Notes                                                          |
|---------------------|----------------|----------------------------------------------------------------|
| id                  | UUID           | PK                                                             |
| tenant_id           | UUID           | NOT NULL                                                       |
| project_id          | UUID           | NOT NULL                                                       |
| grade_structure_id  | UUID           | NOT NULL FK                                                    |
| grade_id            | UUID           | NOT NULL FK                                                    |
| min_score           | NUMERIC(12,4)  | NOT NULL                                                       |
| max_score           | NUMERIC(12,4)  | NOT NULL                                                       |
| standard audit columns | —           | —                                                              |

CHECK: `chk_grade_bands_min_le_max (min_score <= max_score)`.
**No-overlap constraint:** EXCLUDE USING gist with `numrange(min_score, max_score, '[]')` and `grade_structure_id WITH =`, `tenant_id WITH =` — enforces non-overlapping bands within one structure at DB level.
Unique: `uq_grade_bands_structure_grade (grade_structure_id, grade_id)`.

#### `tenant_{x}.evaluations`
| Column                 | Type           | Notes                                                                         |
|------------------------|----------------|-------------------------------------------------------------------------------|
| id                     | UUID           | PK                                                                            |
| tenant_id              | UUID           | NOT NULL                                                                      |
| project_id             | UUID           | NOT NULL                                                                      |
| position_id            | UUID           | NOT NULL FK                                                                   |
| methodology_version_id | UUID           | NOT NULL FK — **the snapshot anchor**                                         |
| status                 | VARCHAR(32)    | NOT NULL CHECK IN ('DRAFT','SUBMITTED','CALIBRATED','APPROVED','LOCKED','INCOMPLETE') |
| raw_score              | NUMERIC(12,4)  | NULL until computed                                                           |
| displayed_score        | NUMERIC(12,2)  | NULL                                                                          |
| assigned_grade_id      | UUID           | NULL FK → grades                                                              |
| grade_band_id          | UUID           | NULL FK → grade_bands (the band that mapped raw_score)                         |
| approved_at            | TIMESTAMPTZ    | NULL                                                                          |
| approved_by            | UUID           | NULL                                                                          |
| locked_at              | TIMESTAMPTZ    | NULL                                                                          |
| standard audit columns | —              | —                                                                             |

Unique: `uq_evaluations_position_mv (position_id, methodology_version_id)` — one evaluation per (position, methodology version).
Indexes: `idx_evaluations_tenant_project`, `idx_evaluations_tenant_project_status`, `idx_evaluations_mv`, `idx_evaluations_position`.
Trigger: `trg_evaluation_lock` BEFORE UPDATE/DELETE — if OLD.status IN ('APPROVED','LOCKED'), block UPDATE on raw_score/displayed_score/assigned_grade_id/scores, and block all DELETE.

#### `tenant_{x}.evaluation_scores`
| Column            | Type           | Notes                                                              |
|-------------------|----------------|--------------------------------------------------------------------|
| id                | UUID           | PK                                                                 |
| tenant_id         | UUID           | NOT NULL                                                           |
| project_id        | UUID           | NOT NULL                                                           |
| evaluation_id     | UUID           | NOT NULL FK                                                        |
| factor_id         | UUID           | NOT NULL FK                                                        |
| factor_level_id   | UUID           | NULL FK — NULL means not yet scored                                |
| raw_factor_score  | NUMERIC(12,4)  | NULL — computed per scoring mode                                   |
| is_not_applicable | BOOLEAN        | NOT NULL DEFAULT FALSE                                             |
| na_reason         | TEXT           | NULL — required if is_not_applicable                               |
| comment           | TEXT           | NULL                                                               |
| standard audit columns | —         | —                                                                  |

Unique: `uq_evaluation_scores_eval_factor (evaluation_id, factor_id)`.
CHECK: `chk_eval_scores_na_reason (NOT is_not_applicable OR na_reason IS NOT NULL)`.

#### `tenant_{x}.calibration_adjustments`
Preserves history of manual adjustments (architecture §15.3, §8.5).
Columns: `id`, `tenant_id`, `project_id`, `evaluation_id` FK, `factor_id` FK (NULL if total-score adjustment), `original_score NUMERIC(12,4)`, `adjusted_score NUMERIC(12,4)`, `delta NUMERIC(12,4) GENERATED ALWAYS AS (adjusted_score - original_score) STORED`, `reason TEXT NOT NULL`, `actor_user_id UUID NOT NULL`, `created_at`.
Append-only — no UPDATE/DELETE trigger.

#### `tenant_{x}.approvals`
Tracks workflow approvals (project, methodology, evaluation, grade structure).
Columns: `id`, `tenant_id`, `project_id`, `entity_type VARCHAR(64)`, `entity_id UUID`, `stage VARCHAR(64)`, `status VARCHAR(32) CHECK IN ('PENDING','APPROVED','REJECTED','WITHDRAWN')`, `approver_user_id UUID`, `decided_at TIMESTAMPTZ`, `reason TEXT`, standard audit.
Index: `idx_approvals_tenant_project_entity (tenant_id, project_id, entity_type, entity_id)`.

#### `tenant_{x}.comments`
`id`, `tenant_id`, `project_id`, `entity_type`, `entity_id`, `parent_comment_id` (self FK, threading), `body TEXT NOT NULL`, `author_user_id`, standard audit, soft archive.

#### `tenant_{x}.attachments`
`id`, `tenant_id`, `project_id`, `entity_type`, `entity_id`, `storage_key VARCHAR(500) NOT NULL UNIQUE`, `checksum_sha256 CHAR(64)`, `mime_type VARCHAR(120)`, `size_bytes BIGINT`, `contains_salary_data BOOLEAN NOT NULL DEFAULT FALSE`, `uploaded_by`, standard audit.

#### `tenant_{x}.audit_logs` (per §8.5)
| Column         | Type         | Notes                                                              |
|----------------|--------------|--------------------------------------------------------------------|
| id             | UUID         | PK                                                                 |
| tenant_id      | UUID         | NOT NULL                                                           |
| project_id     | UUID         | NULL                                                               |
| actor_user_id  | UUID         | NULL (system actions allowed)                                      |
| action         | VARCHAR(100) | NOT NULL                                                           |
| entity_type    | VARCHAR(100) | NOT NULL                                                           |
| entity_id      | UUID         | NULL                                                               |
| before_json    | JSONB        | NULL — **salary fields redacted before insert**                    |
| after_json     | JSONB        | NULL — same                                                        |
| reason         | TEXT         | NULL                                                               |
| ip_address     | INET         | NULL                                                               |
| user_agent     | TEXT         | NULL                                                               |
| correlation_id | VARCHAR(100) | NULL                                                               |
| trace_id       | VARCHAR(100) | NULL                                                               |
| created_at     | TIMESTAMPTZ  | NOT NULL DEFAULT now()                                             |
| hash_prev      | TEXT         | NULL — last row's hash_current                                     |
| hash_current   | TEXT         | NOT NULL — SHA-256 of (id, action, before_json, after_json, hash_prev) computed by app/trigger |

Partition: RANGE on `created_at` monthly (`audit_logs_2026_05`, …). Default partition for safety.
Indexes: `idx_audit_tenant_project_created (tenant_id, project_id, created_at DESC)`, `idx_audit_tenant_action (tenant_id, action)`, `idx_audit_actor (actor_user_id)`, `idx_audit_entity (entity_type, entity_id)`.
**Role-level enforcement:** runtime role has only `INSERT` + `SELECT` privileges on this table. `UPDATE`, `DELETE` are revoked. See §17.

---

## 6. Primary Keys, Foreign Keys, Unique & Check Constraints

### 6.1 Primary keys
- **All PKs are `UUID`** generated by application (`gen_random_uuid()` via `pgcrypto` available as fallback default).
- No natural keys as PKs. Business codes (e.g. `positions.code`) are unique within tenant/project but not PK.

### 6.2 Foreign keys
- All FKs declared explicitly with `ON DELETE RESTRICT` by default. `ON DELETE CASCADE` allowed only on:
  - `factor_translations` → `factors`
  - `factor_level_translations` → `factor_levels`
  - `methodology_template_factor_levels` → `methodology_template_factors`
  - `job_profile_revisions` → `job_profiles`
  - `grade_bands` → `grade_structures`
- Cross-schema FKs (tenant schema → public): **forbidden in MVP 1.** Tenant tables reference `public.users.id` only by UUID stored as plain column (no FK), to keep tenant schemas independently restorable. Same for `methodology_templates`.

### 6.3 Unique constraints (all tenant-scoped)
Every business uniqueness constraint includes `tenant_id` (and `project_id` where applicable). Examples:
- `uq_projects_tenant_code (tenant_id, code)`
- `uq_departments_tenant_project_code (tenant_id, project_id, code)`
- `uq_positions_tenant_project_code (tenant_id, project_id, code)`
- `uq_methodology_versions_methodology_version (methodology_id, version_no)`
- `uq_factors_mv_code (methodology_version_id, code)`
- `uq_evaluations_position_mv (position_id, methodology_version_id)`
- `uq_grade_bands_structure_grade (grade_structure_id, grade_id)`
- `uq_localization_messages_key_locale (key, locale)`

### 6.4 Check constraints (representative)
- `chk_tenants_status IN (...)`, `chk_tenants_isolation_target` (exactly one target name based on mode).
- `chk_locale IN ('ru-RU','uz-Cyrl-UZ','uz-Latn-UZ','en-US')` reused on every `locale` column.
- `chk_grade_bands_min_le_max (min_score <= max_score)`.
- `chk_evaluation_scores_na_reason`.
- `chk_factor_levels_points_nonneg`.
- `chk_methodology_versions_status_transitions` (enforced via trigger, not pure CHECK).
- `chk_attachments_size_nonneg`.

### 6.5 Exclusion constraints
- `grade_bands`: `EXCLUDE USING gist (grade_structure_id WITH =, tenant_id WITH =, numrange(min_score, max_score, '[]') WITH &&)` — guarantees no overlap.

---

## 7. Tenant Isolation Rules

Hard rules — enforced by schema design, qa-engineer tests, and code review:

1. **Every tenant-data table has `tenant_id UUID NOT NULL`.** No exceptions.
2. **Every project-scoped table has `project_id UUID NOT NULL`** (translation tables are exception — see #8).
3. **Every unique constraint on tenant data includes `tenant_id`** (and `project_id` where the entity is project-scoped).
4. **Every index that supports query filtering includes `tenant_id` as leading or near-leading column.** Single-column indexes on `id` (PK) are fine since PK queries are always paired with tenant context in repositories.
5. **No cross-schema foreign keys** between tenant schema and another tenant schema, ever. Cross-schema FK to `public` is also forbidden in MVP 1 (UUID references only).
6. **No global tables holding tenant business data.** `public` schema must never hold a position, evaluation, score, salary, or job profile row.
7. **Methodology templates in `public`** are HRLab IP, not tenant data; cloned at project creation.
8. **Translation tables omit `project_id`** when the parent (factor/factor_level) carries it. They still carry `tenant_id`.
9. **Repositories use composite filters** (`findByIdAndTenantId…`) — enforced by backend code review (see §22).
10. **`public.tenants.slug` defines the schema name** deterministically (`tenant_{slug}`). Provisioning code is the only place that constructs schema identifiers.

---

## 8. RLS Readiness Design

Even though RLS **enforcement** is not enabled in MVP 1 production (it is exercised in staging at end of MVP 1), the schema is RLS-ready.

### 8.1 Contract
- Session variable: `app.current_tenant_id` set on each connection check-out by the application's `TenantContextFilter`.
  ```sql
  SET LOCAL app.current_tenant_id = '<uuid>';
  ```
- DB roles:
  - `grading_migrator` — Liquibase user, owns DDL. **Bypasses RLS** (`BYPASSRLS`).
  - `grading_runtime` — application runtime user. **Does NOT bypass RLS** (`NOBYPASSRLS`), `NOSUPERUSER`, `NOCREATEDB`, `NOCREATEROLE`.
  - `grading_audit_reader` — read-only role for audit/forensic queries.

### 8.2 Policy template (applied to every tenant business table)
```
ALTER TABLE tenant_{x}.positions ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_{x}.positions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_select ON tenant_{x}.positions
  FOR SELECT
  USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid);
CREATE POLICY tenant_isolation_modify ON tenant_{x}.positions
  FOR ALL
  USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid)
  WITH CHECK (tenant_id = current_setting('app.current_tenant_id', true)::uuid);
```

### 8.3 FORCE ROW LEVEL SECURITY
Applied so that even the schema owner cannot bypass policies during application requests. `BYPASSRLS` is reserved for the migrator role only (used during Liquibase changesets), and for the audit forensic role with explicit logging.

### 8.4 MVP 1 staged rollout
- Sprint N: policies created in `disable` state, smoke-tested in dev.
- Sprint N+1: enabled in test + staging; tenant-isolation test suite (qa-engineer §23) must pass.
- Sprint N+2 (end of MVP 1): enabled in production by default for newly provisioned tenants.

### 8.5 RLS-readiness assertion test
qa-engineer test asserts for every tenant table:
- table has `tenant_id` column,
- table has `ENABLE ROW LEVEL SECURITY` available (presence of `tenant_id` is the precondition),
- policy template can be applied without error.

---

## 9. Liquibase Migration Plan

### 9.1 Folder structure
```
db/
  changelog/
    db.changelog-master.yaml                    # routes by context
    control-plane/
      db.changelog-control-plane-master.yaml
      001-create-tenants.yaml
      002-create-users-access.yaml              # users, memberships, roles, permissions, user_roles
      003-create-methodology-templates.yaml
      004-create-localization.yaml
      005-create-system-audit-log.yaml          # parent + first month partition
      006-create-tenant-migration-status.yaml
    tenant-schema-template/
      db.changelog-tenant-master.yaml           # parameterized by ${tenant.schema}
      001-create-projects.yaml
      002-create-organization.yaml              # departments
      003-create-positions.yaml
      004-create-job-profiles.yaml              # + revisions
      005-create-methodology.yaml               # methodologies + versions + factors + levels + translations
      006-create-evaluation.yaml                # evaluations + scores + calibration_adjustments
      007-create-grade-structure.yaml           # grade_structures + grades + grade_bands (+ EXCLUDE constraint)
      008-create-approvals-comments.yaml
      009-create-attachments.yaml
      010-create-audit-log.yaml                 # partitioned parent + monthly partitions
      011-rls-policies.yaml                     # context='rls-enable'
      012-immutability-triggers.yaml            # methodology_version_lock, evaluation_lock
    seeds/
      001-default-permissions.yaml
      002-default-roles.yaml
      003-default-locales.yaml
      004-default-methodology-templates-classic-8.yaml
      005-default-methodology-templates-extended-11.yaml
      006-localization-bootstrap-ru.yaml
      007-localization-bootstrap-uz-cyrl.yaml
      008-localization-bootstrap-uz-latn.yaml
      009-localization-bootstrap-en.yaml
```

**Changelog file count for MVP 1: 1 master + 6 control-plane + 12 tenant-template + 9 seeds = 28 files** (not counting per-tenant materialized invocations, which are runtime).

### 9.2 Contexts and labels
- Contexts: `control-plane`, `tenant-schema`, `seeds`, `rls-enable`, `test-data`.
- Labels: `mvp1`, `mvp2`, `mvp3` for forward-compatibility.
- Production deploy executes contexts: `control-plane,tenant-schema,seeds,rls-enable`.

### 9.3 Migration rules
- Every changeset has `author`, stable `id`, `rollback` block (or explicit `<empty/>` rollback with justification documented).
- Idempotency: use `<preConditions onFail="MARK_RAN">` for create-if-not-exists patterns where safe.
- Adding NOT NULL columns to existing tables uses the phased pattern (nullable → backfill → validate → set not null).
- No `DROP TABLE`, `DROP COLUMN`, or destructive type change without explicit approval ticket referenced in the changeset comment.
- Index creation on existing populated tables uses `CREATE INDEX CONCURRENTLY` via `<sql>` block with `splitStatements="false"` (single statement, runs outside transaction).
- Every changeset added in MVP 1 must be exercised by Testcontainers (qa-engineer §23).

### 9.4 Rollback strategy
- All MVP 1 changesets are reversible (DROP TABLE / DROP COLUMN counterparts in `<rollback>`).
- Seed changesets have `<rollback>` that DELETEs by stable code/key.
- Production rollback is allowed only forward-fix → forward-deploy; literal Liquibase rollback is reserved for staging/dev.

---

## 10. Tenant Provisioning Workflow (11 steps)

Triggered by `POST /api/v1/admin/tenants` (HRLab Super Admin). Implemented as a Spring `@Transactional`-orchestrated saga with each step idempotent and recorded in `tenant_migration_status`.

1. **Validate request** — slug regex, uniqueness against `public.tenants.slug`, allowed `isolation_mode`.
2. **Insert `public.tenants` row** with `status='PROVISIONING'`, `isolation_mode`, `schema_name`/`database_name`.
3. **Insert `public.client_companies` row** linked to tenant.
4. **Create schema or database**
   - `SCHEMA` mode: `CREATE SCHEMA tenant_{slug} AUTHORIZATION grading_migrator;`
   - `DATABASE` mode: provision new DB via DBA workflow, then bootstrap.
5. **Apply baseline tenant migrations** — Liquibase runs `tenant-schema-template/db.changelog-tenant-master.yaml` against the new schema/db with parameter `${tenant.schema}` substituted.
6. **Create RLS policies** — apply `011-rls-policies.yaml` (in MVP 1 the policies are created but ENABLE/FORCE happens after staging gate).
7. **Seed default dictionaries** — locales, role catalog references, factor/level translations for cloned methodology templates.
8. **Clone default methodology templates** — copy `CLASSIC_8_FACTOR` and `EXTENDED_11_CRITERIA` from `public.methodology_templates*` into `tenant_{x}.methodologies` + `methodology_versions` + `factors` + `factor_levels` + translations (4 locales).
9. **Register encryption key reference** — write `encryption_key_ref` placeholder; actual KMS key creation happens in MVP 3 (security-engineer handoff). In MVP 1 the column is populated with a deterministic placeholder so encryption-ready code paths can be tested.
10. **Run tenant isolation smoke test** — automated check: insert a probe row with a wrong `tenant_id` into a tenant table; expect failure (FK or RLS rejection in staging).
11. **Mark tenant `ACTIVE`** — update `public.tenants.status='ACTIVE'`; emit `TENANT_PROVISIONED` system audit event; any failure rolls back to `status='PROVISIONING_FAILED'` and triggers an alert.

---

## 11. Indexing Strategy

### 11.1 Standard composite indexes (every business table)
- `idx_{table}_tenant (tenant_id)` — leading.
- `idx_{table}_tenant_project (tenant_id, project_id)` — for project-scoped tables.
- `idx_{table}_tenant_project_status (tenant_id, project_id, status)` — list endpoints with status filter.
- `idx_{table}_created_at (created_at DESC)` — for sorted-by-recency lists.

### 11.2 Domain-specific indexes
- `idx_factors_mv (methodology_version_id)` — evaluation engine joins.
- `idx_evaluation_scores_eval (evaluation_id)` — score retrieval.
- `idx_evaluations_mv (methodology_version_id)` — methodology impact analysis.
- `idx_grade_bands_structure (grade_structure_id, min_score)` — score-to-band lookup; GiST exclusion handles overlap detection.
- `idx_audit_tenant_project_created (tenant_id, project_id, created_at DESC)` — audit dashboard filters.
- `idx_audit_tenant_action (tenant_id, action)` — by action type.
- `idx_audit_entity (entity_type, entity_id)` — entity history view.
- `idx_localization_messages_key_locale` — UNIQUE doubles as index.
- `idx_factor_translations_locale (locale)` — language-filtered list.
- `idx_departments_path` — GiST on LTREE for subtree queries.

### 11.3 Partial indexes (selective)
- `idx_positions_active ON positions (tenant_id, project_id) WHERE status='ACTIVE'`.
- `idx_evaluations_open ON evaluations (tenant_id, project_id) WHERE status IN ('DRAFT','SUBMITTED','CALIBRATED')`.

### 11.4 Index hygiene rules
- No unscoped (without `tenant_id`) index on tenant business tables.
- JSONB columns (`before_json`, `after_json`, `duties`, `requirements`, `interactions`) get GIN indexes **only** when query patterns demand; in MVP 1, no JSONB GIN index unless qa-engineer benchmarks justify it.
- Index naming: `idx_{table}_{columns_or_purpose}` (snake_case).

---

## 12. Methodology Versioning Data Model

### 12.1 Principle
The methodology **version** is the unit of immutability and the snapshot anchor for every evaluation. Per ADR (architecture §15.6), an approved version is frozen forever; changes require a new version with a new `version_no`.

### 12.2 Version lifecycle
`DRAFT → APPROVED → LOCKED → SUPERSEDED → ARCHIVED`. Only `DRAFT → APPROVED` and `APPROVED → LOCKED` are normal transitions. `LOCKED → SUPERSEDED` happens automatically when a new version is approved.

### 12.3 Immutability enforcement (DB-level)
- Trigger `trg_methodology_version_lock` on `methodology_versions` BEFORE UPDATE OR DELETE.
- Trigger `trg_factor_lock` on `factors`: blocks UPDATE/DELETE if `methodology_version_id` references a non-DRAFT version.
- Trigger `trg_factor_level_lock` on `factor_levels`: same rule via parent factor.
- Translations (`factor_translations`, `factor_level_translations`) are **mutable post-approval** for typo fixes — they don't affect scoring. (Documented exception.)

### 12.4 Evaluation linkage
- `evaluations.methodology_version_id` is NOT NULL FK — every evaluation is permanently tied to the version it used.
- Recalculation under a new version is **not** an UPDATE; it creates a **new** evaluation row referencing the new version (MVP 2 workflow feature).

### 12.5 Scoring modes (architecture §15.2)
Stored in `methodology_versions.scoring_mode`:
- `DIRECT_POINTS` — `total = sum(factor_level.points)`.
- `WEIGHTED_POINTS` — `total = sum((level.points / factor.max_level_points) * factor.weight_points)`.
- `WEIGHTED_SCALE` — `total = sum(level.scale_value * factor.weight)`.
- (`FORMULA_BASED` is reserved for MVP 4; CHECK constraint forbids it in MVP 1.)

### 12.6 Translations (4 locales)
For every `factor` and `factor_level`, the application must guarantee at least one translation in `default_locale`. Missing other locales are resolved via fallback to `default_locale`, then to `en-US`, in the application layer.

---

## 13. Evaluation & Scoring Data Model

### 13.1 Storage
- `evaluations.raw_score NUMERIC(12,4)` — authoritative.
- `evaluations.displayed_score NUMERIC(12,2)` — derived, optional.
- `evaluation_scores.raw_factor_score NUMERIC(12,4)` — per-factor.
- **Grade assignment uses `raw_score`, never `displayed_score`.**

### 13.2 Required vs optional factors
- `factors.is_required` controls whether a missing `evaluation_scores` row for that factor blocks approval.
- Trigger `trg_evaluation_completeness` on `UPDATE` of `evaluations.status`: if attempting transition to `APPROVED` while any required factor lacks `factor_level_id` AND `is_not_applicable=false`, raise exception and set status to `INCOMPLETE`.

### 13.3 Manual adjustment
- Any update to `evaluation_scores.factor_level_id` or `evaluation_scores.raw_factor_score` while `evaluation.status IN ('SUBMITTED','CALIBRATED')` requires:
  - a corresponding row in `calibration_adjustments` (enforced at application layer with DB sanity check trigger `trg_score_change_requires_calibration_row`).
  - non-null `comment` on `evaluation_scores`.

### 13.4 Immutability after approval
- `trg_evaluation_lock` blocks any change to `raw_score`, `displayed_score`, `assigned_grade_id`, `grade_band_id`, and any `evaluation_scores` rows once `evaluations.status IN ('APPROVED','LOCKED')`.

### 13.5 Calibration history preservation
- `calibration_adjustments` is append-only (no UPDATE/DELETE allowed for runtime role).
- Every adjustment row carries `original_score`, `adjusted_score`, generated `delta`, `reason`, `actor_user_id`, `created_at`. Re-calibration creates a new row, never overwrites.

---

## 14. Grade Assignment Data Model

### 14.1 Band lookup
- For an `evaluation.raw_score`, find the unique `grade_band` such that `min_score <= raw_score <= max_score` and `grade_structure_id = project.active_grade_structure_id`.
- The EXCLUDE constraint (§6.5) guarantees the band is unique.

### 14.2 Boundary handling
- Bands are stored with `[min, max]` inclusive on both ends.
- Adjacent bands must differ by smallest representable step: app validation enforces `next.min_score = prev.max_score + 0.0001` (because of `NUMERIC(12,4)`).
- Documented in the application layer to avoid boundary ambiguity.

### 14.3 Versioning of grade structures
- `grade_structures.status` lifecycle: `DRAFT → APPROVED → LOCKED → ARCHIVED`.
- Once `LOCKED`, no UPDATE on `grade_bands` of that structure (trigger `trg_grade_band_lock`).
- A project can replace its active grade structure (changes the FK on evaluations only when a new evaluation is computed; existing approved evaluations keep their `grade_band_id` snapshot).

---

## 15. Audit Log Data Model

### 15.1 Append-only enforcement
- Runtime role `grading_runtime` is granted only `INSERT, SELECT` on every `audit_logs` partition (and on `public.system_audit_log`).
- `UPDATE` and `DELETE` are explicitly `REVOKE`d.
- A `BEFORE UPDATE OR DELETE` trigger raises an exception as a second line of defense.

### 15.2 Fields (architecture §8.5)
See `tenant_{x}.audit_logs` spec in §5.2. Hash-chain fields `hash_prev` / `hash_current` provide tamper evidence (ADR-008).

### 15.3 Partitioning
- `audit_logs` and `public.system_audit_log` are RANGE-partitioned on `created_at` monthly.
- Liquibase creates partitions 12 months ahead via a scheduled changeset (`maintenance` context).
- A `DEFAULT` partition catches stragglers and triggers an alert.

### 15.4 Salary field redaction
- Application's `AuditLogWriter` redacts any field whose JSON path matches the salary-sensitive registry before persisting `before_json` / `after_json`.
- DB-level safety net: CHECK constraint `chk_audit_no_salary_keys` (regex on the JSONB text) blocks insertion if a known salary key (`current_salary`, `fixed_pay`, `variable_pay`, `total_cash`, `total_compensation`, `benefits_value`) appears unredacted. Documented as best-effort, primary control is application code.

### 15.5 Retention
- Audit logs are NOT deleted by normal application flow.
- Retention policy: 7 years for `audit_logs`, defined and exported to cold storage by MVP 4 archival job. In MVP 1, retention is "keep everything".

---

## 16. Localization Data Model

### 16.1 Locales
`ru-RU`, `uz-Cyrl-UZ`, `uz-Latn-UZ`, `en-US`. Enforced by a shared CHECK constraint applied wherever `locale` appears.

### 16.2 Tables
- `public.localization_messages` — UI strings, error codes, report labels — global.
- `tenant_{x}.factor_translations` — tenant-owned factor names/descriptions.
- `tenant_{x}.factor_level_translations` — tenant-owned level labels.
- (Grade translations and report template translations deferred to MVP 2.)

### 16.3 Uniqueness
`UNIQUE (key, locale)` on `localization_messages`. `UNIQUE (factor_id, locale)` on `factor_translations`. `UNIQUE (factor_level_id, locale)` on `factor_level_translations`.

### 16.4 Fallback
Application-layer fallback chain: requested locale → tenant `default_locale` → `en-US` → raw `key`. DB does not enforce — there can be partial translations.

---

## 17. Salary Data Protection Foundation (MVP 1 scope)

Per ADR-007 and architecture §8.4, full salary functionality is MVP 3. MVP 1 prepares the foundation:

### 17.1 Permission code
- `permissions` table includes `SALARY_VIEW`, `SALARY_EDIT`, `SALARY_EXPORT`, `SALARY_SCENARIO_RUN` codes (seeded).
- `user_tenant_memberships.salary_data_permission BOOLEAN` flag exists.
- No salary table exists yet — but the permission system is in place.

### 17.2 Encryption-ready column pattern
When salary tables land in MVP 3, the column pattern will be:
- `value_encrypted BYTEA NOT NULL`,
- `value_key_id VARCHAR(100) NOT NULL`,
- `value_key_version INT NOT NULL`,
- `value_alg VARCHAR(32) NOT NULL`.
The schema-design rule is documented here so security-engineer can validate it in MVP 3.

### 17.3 Audit redaction
See §15.4. Even though no salary table exists in MVP 1, the redaction registry and constraint are deployed so that any future salary leak via audit is blocked at the database boundary.

### 17.4 Attachments
- `attachments.contains_salary_data BOOLEAN NOT NULL DEFAULT FALSE` — set true for any attachment that holds salary data. Downstream MVP 2/3 export logic enforces extra permission checks based on this flag.

---

## 18. Import/Export Staging Foundation

**Out of MVP 1 scope.** Schema reserved (no tables created), but the design intent is documented for MVP 2:
- `import_batches`, `import_batch_rows`, `import_errors`, `import_mappings`, `export_jobs`, `export_files` — all tenant- and project-scoped, with `contains_salary_data` flag on export metadata.
- The Liquibase folder `tenant-schema-template/imports/` is reserved.

In MVP 1, the only data-loading mechanism is direct API call (no batch import).

---

## 19. Reporting Views Foundation

**Out of MVP 1 scope** for materialized views. Plain views allowed if every view definition includes `tenant_id` in WHERE clause via RLS or explicit predicate.

Naming reservation for MVP 2:
- `v_grade_distribution`, `v_position_profile_completion`, `v_evaluation_completion`, `v_audit_summary`, `v_compensation_summary` (MVP 3, salary-protected).

In MVP 1, all reporting is direct table queries through repositories.

---

## 20. Data Retention & Archival Rules

| Data category                  | MVP 1 rule                                                              |
|--------------------------------|-------------------------------------------------------------------------|
| Project                        | Soft archive via `projects.status='ARCHIVED'` + `archived_at`; read-only|
| Evaluations                    | Locked + retained forever; never deleted by app                         |
| Audit logs                     | Append-only; not deleted; partitioned monthly                            |
| Attachments                    | Soft-delete via `archived_at`; physical purge requires manual workflow  |
| Tenant deactivation            | `tenants.status='SUSPENDED'` (reversible) or `ARCHIVED` (read-only)     |
| Hard delete                    | Forbidden in MVP 1; controlled GDPR-style delete added in MVP 4         |
| Backups                        | Daily full + PITR for the tenant database; retention per ADR (devops)   |

---

## 21. Database Security Rules

1. **Roles**
   - `grading_migrator`: DDL only, used by Liquibase. `BYPASSRLS`, `NOSUPERUSER`.
   - `grading_runtime`: app runtime. `NOBYPASSRLS`, `NOSUPERUSER`, `NOCREATEDB`, `NOCREATEROLE`, `NOCREATEROLE`. Granted DML only.
   - `grading_audit_reader`: read-only role for forensic queries, with `BYPASSRLS` and logged usage.
2. **Privileges**
   - Audit tables: `GRANT INSERT, SELECT` to runtime; `REVOKE UPDATE, DELETE`.
   - All other tenant tables: `GRANT SELECT, INSERT, UPDATE, DELETE` to runtime (DELETE restricted by application code).
   - System tables, `public.tenants`, `public.users`: `GRANT SELECT, INSERT, UPDATE` to runtime; DELETE revoked.
3. **TLS** — all connections in non-local environments require TLS (devops-sre enforces via `pg_hba.conf`).
4. **Parameterized queries only** — backend-engineer must use JPA / parameterized native queries. String concatenation in SQL is forbidden.
5. **No salary data in DB server logs** — `log_statement = 'none'` for runtime user; only `log_min_error_statement = 'error'`.
6. **pgaudit** — recommended for admin operations on `public.tenants`, role/permission changes (devops-sre evaluates).
7. **No superuser at runtime.** Provisioning steps that need superuser (database creation in DATABASE-mode) run via a separate operator account, not the migrator.

---

## 22. Database Performance Considerations

- Every list endpoint is paginated (`LIMIT/OFFSET` or keyset). DB indexes back the sort key.
- N+1 patterns avoided: backend-engineer uses JPA `@EntityGraph` or projection DTOs. database-architect provides confirming indexes.
- Expected MVP 1 data volumes:
  - tenants: 10–50
  - projects per tenant: 1–5
  - positions per project: 100–2 000 (large clients: up to 10 000)
  - evaluations per project: ≈ positions count
  - evaluation_scores: positions × factors (8–11) = up to 110 000 rows per large project
  - audit events: up to 10× business records → up to 1 000 000 rows per large project
- Vacuum/analyze: defaults are fine for MVP 1; tenant maintenance scheduled for MVP 2.
- Partitioning candidates: `audit_logs` (in scope, monthly), `evaluation_scores` (reserved for MVP 4 if a single tenant exceeds 5 M rows).

---

## 23. Database Test Cases (≥ 15)

| #  | Test                                                                                 | Tooling                    |
|----|--------------------------------------------------------------------------------------|----------------------------|
| 1  | All Liquibase changesets apply cleanly from empty database                           | Testcontainers + Liquibase |
| 2  | All Liquibase changesets have a `<rollback>` block or documented justification       | Static check               |
| 3  | Every tenant business table has `tenant_id NOT NULL`                                 | `information_schema` query |
| 4  | Every project-scoped table has `project_id NOT NULL`                                 | `information_schema` query |
| 5  | All unique constraints on tenant data include `tenant_id`                             | `pg_constraint` query      |
| 6  | RLS policy applied to a tenant table: Tenant A session cannot SELECT Tenant B rows   | Testcontainers + JDBC      |
| 7  | RLS policy: INSERT with wrong tenant_id fails (WITH CHECK)                            | Testcontainers + JDBC      |
| 8  | Approved methodology_version cannot be UPDATEd (immutability trigger)                | Testcontainers             |
| 9  | Approved methodology_version cannot be DELETEd                                       | Testcontainers             |
| 10 | Factor of an APPROVED methodology cannot be UPDATEd                                  | Testcontainers             |
| 11 | Evaluation status APPROVED → cannot UPDATE raw_score                                  | Testcontainers             |
| 12 | Calibration adjustment row required when score changed in SUBMITTED state            | Testcontainers             |
| 13 | grade_bands overlap insert is rejected by EXCLUDE constraint                         | Testcontainers             |
| 14 | min_score > max_score is rejected by CHECK                                            | Testcontainers             |
| 15 | raw_score column is NUMERIC(12,4); not DOUBLE/FLOAT                                  | `information_schema` query |
| 16 | Audit log INSERT works for runtime role; UPDATE and DELETE are revoked               | Testcontainers + role test |
| 17 | Audit log salary-key constraint blocks insertion of `current_salary` in `before_json`| Testcontainers             |
| 18 | localization_messages uniqueness by (key, locale)                                    | Testcontainers             |
| 19 | factor_translations uniqueness by (factor_id, locale)                                | Testcontainers             |
| 20 | Tenant provisioning workflow creates schema, applies migrations, seeds, marks ACTIVE | Integration test           |
| 21 | Cross-schema FK between two tenants is impossible (no such DDL exists)               | Static check               |
| 22 | Department `path` GiST index supports subtree query                                  | Performance test           |
| 23 | Audit logs partitioned by month; insert routes to correct partition                  | Testcontainers             |
| 24 | `evaluation.methodology_version_id` is NOT NULL FK                                   | Schema introspection       |
| 25 | Default locale CHECK rejects unknown locale string                                   | Testcontainers             |

---

## 24. Risks and Mitigations

| #  | Risk                                                                  | Severity | Mitigation                                                                                                          |
|----|-----------------------------------------------------------------------|----------|---------------------------------------------------------------------------------------------------------------------|
| R1 | Cross-tenant query escapes application filters                        | Critical | RLS-ready schema + RLS enabled in staging by end of MVP 1; tenant-isolation test suite as release gate.             |
| R2 | Approved methodology gets edited via raw SQL                          | Critical | DB-level immutability triggers (not just app logic); runtime role cannot bypass; review native SQL.                 |
| R3 | Salary data leaks via audit `before_json`/`after_json`                | High     | App-layer redaction + DB CHECK constraint blocking salary keys; encryption-ready columns; permission separation.    |
| R4 | Schema-per-tenant migration drift between tenants                     | High     | `tenant_migration_status` tracks every changeset per tenant; CI gate fails if any tenant lags > 1 changeset.        |
| R5 | grade_band overlap produces ambiguous grade assignment                | High     | EXCLUDE constraint at DB level; impossible to bypass.                                                               |
| R6 | Audit log tampering                                                   | High     | Append-only role privileges; hash chain `hash_prev/hash_current`; partition-level WORM consideration in MVP 4.      |
| R7 | UUID-only references to `public.users` lose referential integrity     | Medium   | Application-level guard + nightly orphan check job (MVP 2). Trade-off accepted for tenant DB independence.          |
| R8 | Liquibase rollback in production corrupts data                        | High     | Rollback only in dev/staging; production uses forward-fix policy; documented in DevOps runbook.                     |
| R9 | NUMERIC arithmetic precision loss in scoring                          | Medium   | All scoring columns NUMERIC(12,4); never DOUBLE; tested in scoring engine unit tests (backend-engineer + qa).       |
| R10| Database-per-tenant provisioning is operator-heavy                    | Medium   | Provisioning automated via Liquibase + scripted DB creation; only `isolation_mode=DATABASE` selection is manual.    |
| R11| Tenant schema name collision (e.g. reserved words)                    | Low      | Slug regex `^[a-z][a-z0-9_]{2,63}$` rejects digits-leading and special chars; reserved word list maintained.        |
| R12| Partition gap in audit_logs causes inserts to fail                    | Medium   | DEFAULT partition catches; monitoring alert; Liquibase maintenance changeset creates partitions 12 months ahead.    |

---

## 25. Tasks for Backend Engineer (JPA entities mirroring schema)

1. Generate JPA `@Entity` classes mirroring every MVP 1 table. Class naming: `PositionJpaEntity`, `EvaluationJpaEntity`, etc. (architecture §11.3).
2. UUID PKs with `@Id` + `@Column(columnDefinition = "uuid")`. Do not let Hibernate generate UUIDs implicitly — use application-side generation for consistency with seed scripts.
3. NUMERIC columns mapped as `BigDecimal` with explicit `precision` and `scale`. **Never `double` or `float`.**
4. TIMESTAMPTZ mapped as `OffsetDateTime` or `Instant`.
5. Every repository method that fetches tenant data must filter by `tenant_id`:
   - `findByIdAndTenantId(UUID id, UUID tenantId)`,
   - `findAllByTenantIdAndProjectId(UUID tenantId, UUID projectId, Pageable p)`.
   - No `findById(UUID id)` allowed in tenant-data repositories.
6. Implement `TenantContextFilter` to `SET LOCAL app.current_tenant_id` per request (so RLS will work when enabled).
7. Implement `AuditLogWriter` with salary-key redaction registry.
8. Implement methodology-cloning service at project creation (clone HRLab templates from `public` into tenant schema).
9. Wire optimistic locking via `@Version` mapped to `row_version`.
10. Implement enum-as-string mapping for all `status` and `model_type` and `scoring_mode` columns with explicit length matches.

---

## 26. Tasks for QA Engineer (Testcontainers tests)

1. Stand up the MVP 1 schema via Liquibase in Testcontainers Postgres and run the 25 tests in §23.
2. Build the **tenant isolation proof suite** (architecture §22.2):
   - Two tenants `T1`, `T2`. User of `T1` attempts every read/write API against `T2` resources. Expect 403/404, no data leakage.
3. **Migration tests**: every changeset applies on empty DB; subset applies on existing DB; rollback applies cleanly in dev.
4. **Immutability tests**: produce APPROVED methodology version, attempt UPDATE/DELETE — expect SQL exception.
5. **Audit append-only test**: connect as `grading_runtime`, attempt UPDATE/DELETE on `audit_logs` — expect privilege error.
6. **Salary redaction test**: write evaluation history with future salary key in payload — expect CHECK constraint to fire.
7. **Grade band overlap test**: insert overlapping bands — expect EXCLUDE constraint to fire.
8. **Performance baseline**: load 10 000 positions × 8 factors × 1 000 audit events, run typical list queries, assert < 200 ms P95 with planned indexes.
9. **Provisioning end-to-end**: invoke provisioning workflow, validate all 11 steps complete, schema is queryable, default methodology is cloned.
10. **Locale CHECK**: every locale column rejects an unknown locale.

---

## 27. Tasks for DevOps / SRE (Liquibase CI/CD execution)

1. Configure Liquibase in CI to apply `control-plane` + `tenant-schema-template` against a fresh Postgres in every PR.
2. Add a CI gate: every PR that touches `db/changelog/**` must include a Testcontainers test exercising the new changeset.
3. Configure two DB roles in every environment: `grading_migrator`, `grading_runtime`. Document role secrets in Vault.
4. Production deployment runs Liquibase under `grading_migrator`. Application connects under `grading_runtime`.
5. Implement tenant-provisioning Liquibase wrapper script: takes `--tenant.slug`, `--tenant.schema`, `--isolation.mode` args, runs the tenant-schema-template against the target.
6. Implement `tenant_migration_status` sync job: after every release, verify every active tenant has the latest applied changeset; alert otherwise.
7. Set up monthly partition-creation maintenance changeset (`audit_logs` 12 months ahead).
8. Enforce TLS for DB connections in dev/test/staging/prod via `pg_hba.conf` and connection-string flags.
9. Configure backup policy: daily full + WAL archival → PITR; verify with quarterly restore drill (devops-sre owns).
10. Prepare a runbook for tenant DB-per-tenant provisioning (DBA workflow + Liquibase execution).

---

## 28. Cross-Agent Handoff Summary

- **backend-engineer**: receives this schema as the data contract. JPA entities mirror it. No deviation without database-architect review.
- **security-engineer**: validates RLS policy template, audit append-only role privileges, salary redaction registry, and encryption-ready column plan (for MVP 3).
- **devops-sre**: owns Liquibase execution in CI/CD, role/secret provisioning, partition maintenance, backup strategy.
- **qa-engineer**: owns the 25-test suite (§23), tenant isolation proof suite, and migration regression tests.
- **hr-product-owner**: confirms entity coverage matches MVP 1 PRD scope (no missing entity, no out-of-scope entity included).
- **integration-engineer** (MVP 2): receives import/export staging foundation (§18) and uses reserved naming.

---

## 29. GO / NO-GO Database Readiness Criteria for MVP 1

Database is **GO** for MVP 1 release when:

- [ ] All 28 Liquibase changesets apply cleanly on a fresh Postgres in CI.
- [ ] All 25 tests in §23 pass.
- [ ] Two production-like tenants are provisioned and isolated by the tenant isolation proof suite.
- [ ] RLS is enabled in staging and the cross-tenant access test produces 0 leakage.
- [ ] Backup + PITR drill executed successfully on staging.
- [ ] All sensitive columns (audit `before_json`, `after_json`) demonstrate redaction in a manual test.
- [ ] backend-engineer JPA entities match the schema (verified by a checksum test comparing `information_schema` to entity metadata).
- [ ] No NUMERIC column for scoring or money is `DOUBLE PRECISION` / `REAL`.
- [ ] No tenant-data table is missing `tenant_id`.

Otherwise: **NO-GO**, with explicit list of failing items.

---

## 30. Role grants update (post-review — F-04 remediation)

**Trigger:** security review `docs/mvp1/reviews/phase0-1-security-review.md`
finding **F-04 (High)** — Audit `system_audit_log` table had no DB-level
grant restricting the runtime user to INSERT/SELECT only. The Java
repository interface `SystemAuditLogRepository extends Repository<…>`
prevented mutation at the application layer, but a compromised runtime
credential or rogue raw-SQL caller could still UPDATE or DELETE audit
rows.

**Fix shipped:** Liquibase changelog
`db/changelog/control-plane/005-db-role-grants.yaml` creates three roles
and locks down the grant matrix.

### 30.1 Roles

| Role | Purpose | Used by |
|------|---------|---------|
| `grading_migrator` | Full DDL + DML on `public`. | Liquibase migration job only. |
| `grading_runtime` | DML on business tables; **SELECT + INSERT only** on `public.system_audit_log` and `public.tenant_audit_logs`. | Spring Boot app (`spring.datasource.username`). |
| `grading_audit_reader` | SELECT only on audit tables. | Audit query API (gated by `AUDIT_READ` at app layer). |

All three roles are created `NOLOGIN`. LOGIN + password is attached per
environment by devops-sre (Vault). The `test-roles` Liquibase context
attaches dev passwords for Testcontainers + docker-compose only — never
load this context in production.

### 30.2 Grant matrix

| Role | Control Plane (`tenants`, `users`, `roles`, `permissions`, …) | Tenant Business (`positions`, `evaluations`, … — Phase 2+) | Audit (`system_audit_log`, `tenant_audit_logs`) |
|------|---|---|---|
| `grading_migrator` | ALL (DDL+DML) | ALL | ALL |
| `grading_runtime` | SELECT, INSERT, UPDATE, DELETE | SELECT, INSERT, UPDATE, DELETE | **SELECT, INSERT only** |
| `grading_audit_reader` | (none) | (none) | SELECT only |

PUBLIC is revoked from schema `public`. `ALTER DEFAULT PRIVILEGES`
auto-grants DML on future tables to `grading_runtime`; future audit
partitions of `system_audit_log` (per §15.3) must explicitly
`REVOKE UPDATE, DELETE, TRUNCATE` from `grading_runtime` in the same
changeset that creates the partition.

### 30.3 Defense-in-depth layers

1. JPA repo interface excludes delete/update (`AuditAppendOnlyTest`).
2. DB role grant lockdown — this section (`AuditRoleGrantsTest`).
3. SHA-256 hash chain over canonical payload (`JpaAuditService`).
4. Daily WORM anchor (devops-sre, deferred).

### 30.4 Test coverage

`backend/src/test/java/uz/hrlab/grading/db/AuditRoleGrantsTest.java`:

* `grading_runtime`: INSERT → ok; SELECT → ok; UPDATE → permission denied;
  DELETE → permission denied; TRUNCATE → permission denied; INSERT/UPDATE
  on tenant_audit_logs locked down equivalently; business-table DML still
  works.
* `grading_audit_reader`: SELECT on audit → ok; INSERT on audit → denied;
  any access to business tables → denied.
* `information_schema.table_privileges` reflects the documented matrix
  exactly (AUD-06 / AUD-07 acceptance).

### 30.5 Handoff to devops-sre

Each deployed environment (dev, staging, prod) requires **three** Vault
secrets, one per role:

| Vault path (suggested) | Role | Mount point |
|---|---|---|
| `secret/grading/<env>/db/migrator` | `grading_migrator` | Liquibase init-container in CI/CD. |
| `secret/grading/<env>/db/runtime` | `grading_runtime` | Spring Boot pod env vars (`SPRING_DATASOURCE_USERNAME` / `..._PASSWORD`). |
| `secret/grading/<env>/db/audit_reader` | `grading_audit_reader` | Audit query worker (Phase 1.5+) and any ops-readonly tooling. |

Password rotation policy and exact Vault paths are owned by devops-sre.
Liquibase never sets a password in deployed environments — the changeset
only creates the role principals and grants.

### 30.6 Local dev posture

`backend/docker-compose.yml` mounts `db/init/01-create-roles.sql` into
`/docker-entrypoint-initdb.d/` so the three roles exist with dev
passwords before the Spring app boots. `application-local.yml` keeps the
single bootstrap superuser for convenience but a developer can switch
the datasource to `grading_runtime` to exercise the locked-down posture.

---

## 31. Phase 2 — Implementation Notes (backend-engineer addendum)

The Phase 2 backend ships the first concrete tenant-schema tables and
exposes the `grading.tenancy.mode` flag for hybrid deployment.

### 31.1 Tables shipped
* `projects` — JSONB `name_i18n`, status DRAFT/ACTIVE/LOCKED/ARCHIVED,
  unique `(tenant_id, code)`, indexes on `(tenant_id)`,
  `(tenant_id, status)`, `(created_at DESC)`.
* `departments` — self-FK `parent_id`, JSONB `name_i18n`, type
  BRANCH/DEPARTMENT/DIVISION/UNIT, status ACTIVE/ARCHIVED,
  CHECK `id <> parent_id`, unique `(tenant_id, project_id, code)`,
  index on `(parent_id)`. LTREE / `path` column deferred (queries use
  recursive CTE for now).
* `positions` — JSONB `title_i18n`, status DRAFT/ACTIVE/ARCHIVED, FKs
  to `projects` + `departments`, unique `(tenant_id, project_id, code)`,
  indexes on `(tenant_id, project_id, status)`,
  `(tenant_id, project_id, department_id)`, `(department_id)`.

### 31.2 Translatable fields — JSONB Map for now
Per the original blueprint §16 a translation-table model is the long-term
target (full-text search, partial translations). MVP 1 ships the
simplified `name_i18n` / `title_i18n` JSONB `Map<locale, string>` for
project / department / position — adequate for Phase 2 UX, cheap to
migrate to per-entity translation tables later. Methodology factor
translations remain on the dedicated table model (§16).

### 31.3 Tenancy mode flag
`grading.tenancy.mode` (env `GRADING_TENANCY_MODE`):

* `shared` (default) — tenant tables live in `public`. Single Liquibase
  run applies them via context `mode-shared`. Used for local dev and
  CI integration tests.
* `schema_per_tenant` — production target. The provisioning saga
  (§10) ends by invoking
  `TenantSchemaProvisioner.provision(tenant)`, which runs the
  programmatic Liquibase update of
  `db/changelog/tenant-schema/db.changelog-tenant.yaml` against
  `tenant_{slug}`. devops-sre owns the env-var rollout per environment.

### 31.4 Liquibase changesets added (Phase 2)
* `tenant-schema/001-create-projects.yaml`
* `tenant-schema/002-create-organization.yaml`
* `tenant-schema/003-create-positions.yaml`
* `tenant-schema/db.changelog-tenant.yaml` (master include)
* Main `db.changelog-master.yaml` now includes the tenant-schema master
  under context `mode-shared`.

*Production rollout to schema-per-tenant is owned by database-architect
+ devops-sre and remains post-MVP 1.*

### 31.5 Phase 2 constraints (post-review remediation)

Changelog `tenant-schema/004-phase2-constraints.yaml` closes four
defense-in-depth findings from
`docs/mvp1/reviews/phase2-security-review.md`. All changes are additive
and reversible (each changeSet declares a `rollback:` block).

| Finding | DB-level control | Affected table(s) | Rationale |
|---|---|---|---|
| **F-203** | `FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE RESTRICT ON UPDATE NO ACTION` | `projects`, `departments`, `positions` | Closes the referential-integrity gap noted in §31.4: previously a typo or rogue insert could place a row under an unknown tenant_id. RESTRICT (not CASCADE) ensures tenant deletion is an explicit, controlled operation — never silently propagated through 4-10 business tables. In schema-per-tenant mode the FK target `public.tenants` is reachable from every per-tenant schema, so the same DDL still applies. |
| **F-204** | Composite FK `positions(project_id, department_id) → departments(project_id, id)` backed by `UNIQUE INDEX uq_departments_project_id ON departments(project_id, id)` | `positions`, `departments` | Forbids cross-project position/department references at the DB engine. Replaces the original single-column FK `positions.department_id → departments(id)` (dropped in the same changeSet to preserve idempotency). |
| **F-206** | Trigger `trg_prevent_department_cycle` (BEFORE INSERT OR UPDATE OF parent_id) calling PL/pgSQL function `prevent_department_cycle()` | `departments` | Walks the parent chain up to a hard cap of 50 levels and rejects any loop. Complements (does not replace) the service-layer `findDescendants` CTE check, and guards against direct SQL updates that bypass the application. |
| **F-209** | Trigger `trg_enforce_project_lock` (BEFORE UPDATE OF status) calling PL/pgSQL function `enforce_project_lock_immutability()` | `projects` | Rejects any `LOCKED → DRAFT/ACTIVE` transition. `LOCKED → ARCHIVED` is still permitted. This is defense-in-depth against direct SQL updates that bypass `UpdateProjectUseCase`. |

**Accepted risks**

* Cross-tenant consistency between `positions.tenant_id` and
  `departments.tenant_id` is **not** enforced at DB level. Both rows
  already carry `tenant_id NOT NULL` and the FK on each side links to
  `public.tenants(id)`; the application layer
  (`CreatePositionUseCase`, `UpdatePositionUseCase`) validates equality
  explicitly. Adding a composite FK on `(tenant_id, department_id)`
  would require yet another unique index and complicates the
  schema-per-tenant migration. Accepted risk — owner: database-architect,
  re-evaluation at MVP 2 entry.
* The cycle trigger is **intra-schema**. In schema-per-tenant mode, a
  recursive walk across tenant schemas is by construction impossible
  (FKs cannot cross), so this is not a gap.

**New integration test:**
`backend/src/test/java/uz/hrlab/grading/db/Phase2ConstraintsTest.java`
exercises all four findings against a real Postgres via Testcontainers
(`@Tag("integration")`, Docker-skipped on machines without Docker).

**Functions/triggers introduced** (all reversible):

* `prevent_department_cycle()` + `trg_prevent_department_cycle`
* `enforce_project_lock_immutability()` + `trg_enforce_project_lock`

Both functions run as `SECURITY INVOKER` (default). No `GRANT EXECUTE`
adjustment is required because functions invoked from a trigger are
executed implicitly by the trigger owner; the runtime role
(`grading_runtime`) only needs INSERT/UPDATE on the parent tables,
which it already has per `role-grants-matrix.md`.

### 31.6 Phase 3 constraints (post-review remediation)

Changelog `tenant-schema/008-phase3-constraints.yaml` closes the
composite-FK findings from
`docs/mvp1/reviews/phase3-security-review.md` and
`docs/mvp1/reviews/phase3-qa-review.md`. All changes are additive and
reversible (each changeSet declares a `rollback:` block).

| Finding | DB-level control | Affected table(s) | Rationale |
|---|---|---|---|
| **F-304** | `UNIQUE INDEX uq_positions_tenant_project_id ON positions(tenant_id, project_id, id)` | `positions` | Prerequisite — the (tenant_id, project_id, id) tuple must be UNIQUE so it can be the TARGET of a composite FK from `job_profiles` and `job_analysis_questionnaires`. Since `id` alone is already PK (unique), this is a logical no-op at the row level — it only exposes the tuple as an FK target. |
| **F-302 / D-315** | Composite FK `job_profiles(tenant_id, project_id, position_id) → positions(tenant_id, project_id, id)` | `job_profiles` | Replaces the single-column FK `job_profiles.position_id → positions(id)`. Forbids a job_profile from referencing a position belonging to a different (tenant, project) tuple at the DB engine — closes the defense-in-depth gap noted in the changelog 005 header comment. Application code (`CreateJobProfileUseCase`) already enforced this; DB enforcement makes direct-SQL bypass impossible. |
| **F-302** | Composite FK `job_analysis_questionnaires(tenant_id, project_id, position_id) → positions(tenant_id, project_id, id)` | `job_analysis_questionnaires` | Same upgrade for questionnaires. |
| **F-302 (extra)** | `UNIQUE INDEX uq_jaq_tenant_id ON job_analysis_questionnaires(tenant_id, id)` + composite FK `job_analysis_answers(tenant_id, questionnaire_id) → job_analysis_questionnaires(tenant_id, id) ON DELETE CASCADE` | `job_analysis_answers`, `job_analysis_questionnaires` | Closes the cross-tenant answer-smuggling gap: previously a row in `job_analysis_answers` could theoretically carry tenant B while referencing tenant A's questionnaire. The composite FK ties the tenant_id columns together. `ON DELETE CASCADE` is preserved (matches the original behaviour from changelog 006). |

**Deferred: JSONB structural validation (F-303)**

Phase 3 security finding F-303 (JSONB unknown-fields hardening for
`JobAnalysisQuestion` and multilingual Maps) is **deferred to the
application layer** with the following rationale:

* A DB-level CHECK constraint of the form
  `purpose_i18n ?| array['ru-RU', 'uz-Cyrl-UZ', 'uz-Latn-UZ', 'en-US']`
  would need to encode the 4-locale allowlist per multilingual JSONB
  column on every multilingual table. Each new locale (or schema
  evolution) would require DDL.
* PostgreSQL CHECK constraints on JSONB are evaluated row-by-row at
  write time, which is slower than Jackson rejection.
* The primary defense surface is the API perimeter: backend-engineer
  owns adding `@JsonIgnoreProperties(ignoreUnknown=false)` to
  `JobAnalysisQuestion` (F-303 backend fix) and a Bean Validation
  `@Size(min=1,max=4)` + locale-key allowlist validator
  (`@SupportedLocaleKeys`) on multilingual Map fields (F-308 backend
  fix).
* No client-write path currently reaches the `questions` JSONB column
  on `job_analysis_questionnaires` — the value is server-constructed
  from `QuestionnaireTemplate.questionsFor(...)`. Risk today is
  theoretical.

**Accepted risk:** A direct SQL update by a DBA or a future migration
could write malformed JSONB. Owner: database-architect; re-evaluation
at MVP 2 entry when methodology/factor i18n surfaces multiply the
JSONB attack surface.

**New integration test:**
`backend/src/test/java/uz/hrlab/grading/db/Phase3ConstraintsTest.java`
exercises the three composite-FK paths against a real Postgres via
Testcontainers (`@Tag("integration")`, Docker-skipped on machines
without Docker).

### 31.7 Phase 4 constraints (post-review remediation)

Changelog `tenant-schema/014-phase4-constraints.yaml` closes the
trigger-coverage finding from
`docs/mvp1/reviews/phase4-security-review.md`. The change is additive
and reversible (the `rollback:` block restores the Phase 4 baseline
trigger function and timing).

| Finding | DB-level control | Affected table | Rationale |
|---|---|---|---|
| **F-403** | Trigger `prevent_mv_status_regression` extended to `BEFORE INSERT OR UPDATE OF status ON methodology_versions`; PL/pgSQL function branches on `TG_OP`. On `INSERT`, the trigger asserts `NEW.status = 'DRAFT'` and raises `METHODOLOGY_VERSION_INVALID_INITIAL_STATUS` otherwise. On `UPDATE`, the existing regression rules (ARCHIVED→∅, LOCKED→ARCHIVED only, APPROVED→LOCKED\|ARCHIVED only) are preserved unchanged. | `methodology_versions` | Phase 4 constraints update: trigger extended to BEFORE INSERT to enforce `status=DRAFT` on creation. The application use cases (`CreateMethodologyVersionUseCase`, `CreateMethodologyFromTemplateUseCase`, `CreateMethodologyFromScratchUseCase`) already create new versions as DRAFT, but a direct DBA INSERT or future operational script could bypass that. The trigger guarantees the invariant at the storage layer — defence-in-depth for the workflow status machine. Function uses `CREATE OR REPLACE FUNCTION` and the trigger uses `DROP TRIGGER IF EXISTS` + `CREATE TRIGGER` (idempotent). |

**New integration test:**
`backend/src/test/java/uz/hrlab/grading/phase4/Phase4ConstraintsTest.java`
exercises four scenarios: direct INSERT with `status='APPROVED'` /
`'LOCKED'` / `'ARCHIVED'` → trigger rejects with
`METHODOLOGY_VERSION_INVALID_INITIAL_STATUS`; direct INSERT with
`status='DRAFT'` → succeeds; LOCKED→APPROVED UPDATE → still rejected
(baseline behaviour preserved). Testcontainers-gated.

---

*End of MVP 1 Database Blueprint.*
