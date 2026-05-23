# MVP 1 QA Master Test Plan — grading.hrlab.uz

Document owner: QA Engineering
Status: Draft v1.0 for MVP 1
Date: 2026-05-23
Applies to: MVP 1 — Core grading foundation
Companion documents: `архитектура.md`, MVP 1 PRD (hr-product-owner), MVP 1 Security Requirements & Threat Model (security-engineer)

---

## 1. QA Objectives

1. Prove that **no data of one company-client (tenant) can leak to another tenant** through any channel: API, UI, search, dashboards, exports, attachments, AI, cache, or background jobs.
2. Prove that **grade access does not imply salary access** — even with no salary module in MVP 1, the permission foundation must be testable.
3. Prove that the **backend never trusts a tenant_id sent by the frontend** — tenant context is derived from authenticated session token.
4. Prove that **approved methodology is locked** and that **any edit creates a new version**, with old evaluations remaining linked to the old version.
5. Prove that **evaluation scores are reproducible** for the same inputs (deterministic scoring engine).
6. Prove that **audit trail covers all 20 sensitive events** and is append-only.
7. Prove that **RBAC + ABAC policies are enforced on the backend** (not on the frontend only).
8. Prove that **localization works in 4 languages** (ru-RU, uz-Cyrl-UZ, uz-Latn-UZ, en-US) without breaking core navigation.
9. Provide a **release GO/NO-GO gate** with explicit blocking conditions.
10. Provide **automation specs** so test packs run continuously in CI/CD.

---

## 2. Scope

### In scope (MVP 1)

1. Tenant isolation foundation (schema-per-tenant, tenant_id defense-in-depth, RLS).
2. Users, roles, permissions (RBAC + ABAC).
3. Project workspace (create, list, switch, archive).
4. Basic organization structure (departments, hierarchy).
5. Position catalog (CRUD with tenant + project scope).
6. Job profile (basic create/edit/approve).
7. Basic methodology builder (Classic 8-factor + Custom; factors, levels, weights, translations).
8. Scoring engine (DIRECT_POINTS, WEIGHTED_POINTS, WEIGHTED_SCALE).
9. Grade assignment (14/16/custom grade structures, grade bands, boundary mapping).
10. Audit trail (20 events; append-only; AUDIT_READ-gated).
11. Localization foundation (i18n routing, language switcher, ru/uz-Cyrl/uz-Latn/en, fallback, methodology factor translations).
12. Salary permission foundation (no salary data displayed; `SALARY_VIEW` permission absent for all default roles; permission code present and testable).

### Out of scope (MVP 1)

- Full workflow with multi-stage approvals (MVP 2).
- Excel/PDF/Word report generation (MVP 2).
- Comments and attachments (MVP 2).
- Salary range engine, compa-ratio, scenarios (MVP 3).
- AI assistant, anomaly detection, integrations (MVP 4).
- HRM/ERP/Payroll/SSO/BI integrations (MVP 4).
- Performance/load benchmarking against SLO targets (smoke-level only in MVP 1).
- Full DAST campaign with OWASP ZAP scripted scenarios (smoke scan only in MVP 1).

---

## 3. Quality Risks

| ID | Risk | Likelihood | Impact | Inherent severity | Mitigation tests |
|----|------|------------|--------|-------------------|------------------|
| QR-01 | Cross-tenant data leakage via API | Medium | Critical | Critical | Tenant Isolation Pack (18 scenarios) |
| QR-02 | Backend trusts tenant_id from frontend body/query | Medium | Critical | Critical | TIP-12, TIP-13; ArchUnit rule against `@RequestParam tenantId` |
| QR-03 | Repository method without tenant filter (BOLA/IDOR) | Medium | Critical | Critical | ArchUnit + integration tests |
| QR-04 | Approved methodology editable in place | Medium | High | High | Methodology Pack lock/version tests |
| QR-05 | Scoring non-deterministic (rounding/float drift) | Low | High | High | Scoring Pack reproducibility test |
| QR-06 | Salary permission code missing → future MVP 3 leaks | Medium | High | High | Salary Permission Foundation Pack |
| QR-07 | Audit trail missing for sensitive action | Medium | High | High | Audit Pack (20 events) |
| QR-08 | Audit log mutable through normal API | Low | Critical | Critical | Audit Pack append-only tests |
| QR-09 | Localization breaks layout in Uzbek (longer text) | Medium | Medium | Medium | Localization Pack |
| QR-10 | Frontend-only permission gating bypassed by direct API call | High | Critical | Critical | RBAC Pack (every endpoint tested directly) |
| QR-11 | Stale tenant context token allows cross-tenant access | Low | Critical | Critical | TIP-11 |
| QR-12 | Cache (Redis) leaks data across tenants | Low | Critical | Critical | TIP-16 cache key tenant scoping |
| QR-13 | JPA entities exposed in API responses (PII leak) | Medium | Medium | Medium | ArchUnit + contract tests |
| QR-14 | Grade band overlap or gap causes wrong grade | Medium | High | High | Grade Structure Pack boundary tests |
| QR-15 | Missing required factor allows evaluation approval | Low | High | High | Scoring Pack required-missing test |

---

## 4. Test Strategy — Test Pyramid

```
                     ┌─────────────────────┐
                     │   E2E (Playwright)  │   ~5% — critical user journeys
                     │   axe-core a11y     │
                     └─────────────────────┘
                ┌──────────────────────────────┐
                │  API + Security tests        │   ~20%
                │  REST Assured + WireMock     │
                │  OWASP ZAP staging smoke     │
                └──────────────────────────────┘
           ┌────────────────────────────────────────┐
           │  Integration tests (Testcontainers)    │   ~25%
           │  Tenant isolation, RLS, migrations     │
           │  Repository BOLA/IDOR proofs           │
           └────────────────────────────────────────┘
      ┌──────────────────────────────────────────────────┐
      │  Component tests (Vitest + RTL + MSW)            │   ~20%
      │  PermissionGate, SalaryValue, locked states      │
      └──────────────────────────────────────────────────┘
 ┌────────────────────────────────────────────────────────────┐
 │  Unit tests (JUnit 5 + AssertJ + Mockito) + ArchUnit       │   ~30%
 │  Scoring formulas, grade band logic, validators            │
 └────────────────────────────────────────────────────────────┘
```

**Layer-to-tool mapping:**

- Unit (backend domain): JUnit 5, AssertJ, Mockito, ArchUnit.
- Integration (backend + DB): Spring Boot Test, Testcontainers (PostgreSQL 16), Liquibase test runner.
- API: REST Assured, JSON Schema validation, contract tests.
- Component (frontend): Vitest, React Testing Library, MSW for API mocking.
- E2E: Playwright with multi-locale projects (ru/uz-Cyrl/uz-Latn/en).
- Accessibility: axe-core integrated in Playwright + RTL.
- Security: OWASP ZAP baseline + custom REST Assured BOLA/IDOR/tenant tests; Snyk/Trivy in CI.
- Performance smoke: k6 (light) for happy-path latency budgets only.

---

## 5. Test Environments

| Env | Purpose | Data | Notes |
|-----|---------|------|-------|
| local | Developer machine | Seeded Tenant A + Tenant B | docker-compose: postgres, redis, minio, keycloak |
| dev | Continuous integration | Auto-seeded on deploy | runs unit + integration + API packs on every PR |
| test (QA) | Manual + Playwright E2E | Full test data model (2 tenants, 11 users) | locked baseline; reset nightly |
| staging | Production-like | Anonymized fixtures only | OWASP ZAP smoke; tenant isolation regression on every deploy |
| production | Live | Real customers | smoke + tenant isolation + audit verification post-deploy |

**Environment rules:**

- No production data in dev/test/staging.
- Staging mirrors production schemas, RLS policies, KMS configuration.
- Tenant isolation regression suite runs on every deploy to staging and production (post-deploy verification only — read-only probes against seeded canary tenants).

---

## 6. Test Data Model

### 6.1 Tenants

**Tenant A — Alpha Holding**
- Tenant slug: `alpha-holding`
- Tenant UUID: `T-A` (placeholder)
- Project: `Alpha Grading 2026` (P-A1)
- Departments: HR (D-A-HR), Finance (D-A-FIN), IT (D-A-IT)
- Positions:
  - HR Manager (POS-A-HRM)
  - Financial Analyst (POS-A-FA)
  - IT Specialist (POS-A-ITS)
- Methodology: Classic 8-factor, version 1 (M-A-V1, status APPROVED+LOCKED)
- Grade structure: 14-grade model
- Storage namespace: `s3://grading-files/tenant-A/...`

**Tenant B — Beta Manufacturing**
- Tenant slug: `beta-manufacturing`
- Tenant UUID: `T-B`
- Project: `Beta Grading 2026` (P-B1)
- Departments: Production (D-B-PROD), Sales (D-B-SAL), Legal (D-B-LEG)
- Positions:
  - Production Manager (POS-B-PM)
  - Sales Specialist (POS-B-SS)
  - Legal Counsel (POS-B-LC)
- Methodology: Custom 6-factor, version 1 (M-B-V1, status DRAFT)
- Grade structure: 16-grade model
- Storage namespace: `s3://grading-files/tenant-B/...`

### 6.2 Personas (11 users)

| # | User | Tenant scope | Role | Salary perm | Notes |
|---|------|--------------|------|-------------|-------|
| U1 | `superadmin@hrlab.uz` | Control plane (all) | HRLab Super Admin | No | Only admin APIs |
| U2 | `pm.both@hrlab.uz` | A + B | HRLab Project Manager | No | Assigned to both projects |
| U3 | `consultant.a@hrlab.uz` | A only | HRLab Consultant | No | Must NOT see Tenant B |
| U4 | `hrdir.a@alpha.uz` | A | Client HR Director | No | Full tenant A read/write |
| U5 | `hrspec.a@alpha.uz` | A | Client HR Specialist | No | Limited write |
| U6 | `mgr.hr.a@alpha.uz` | A / dept HR only | Department Manager | No | ABAC department scope |
| U7 | `viewer.a@alpha.uz` | A | Viewer | No | Read-only |
| U8 | `auditor.a@alpha.uz` | A | External Auditor | No | Read-only + AUDIT_READ, no SALARY_VIEW |
| U9 | `hrdir.b@beta.uz` | B | Client HR Director | No | Tenant B counterpart of U4 |
| U10 | `grade.only@alpha.uz` | A | Composite (GRADE_READ only) | No | Has grade perms but NOT SALARY_VIEW |
| U11 | `salary.user@alpha.uz` | A | Composite (GRADE_READ + SALARY_VIEW) | Yes | Reserved for MVP 3 verification; in MVP 1 used to verify permission code wiring |

### 6.3 Data sensitivity classes (recap)

- Public/internal low: methodology template names, generic factor names.
- Tenant confidential: projects, org, positions, profiles, methodology config, evaluations, grades.
- Highly sensitive: salary ranges, compensation snapshots, audit logs, AI prompts containing client data.

---

## 7. Test Types

1. Unit tests
2. Integration tests
3. Contract / API tests
4. Security tests (BOLA/IDOR/auth bypass/JWT tampering)
5. Tenant isolation tests
6. RBAC/ABAC permission tests
7. Methodology lock/version tests
8. Scoring correctness + reproducibility tests
9. Grade assignment tests
10. Audit trail tests
11. Localization tests
12. Frontend component tests (permission gating, locked states, masking)
13. E2E tests (critical journeys)
14. Accessibility tests (axe-core)
15. Negative + edge tests
16. Performance smoke tests
17. Regression tests
18. Architecture tests (ArchUnit)

---

## 8. Functional Test Cases (Core)

### 8.1 Project workspace — TC-PRJ

**TC-PRJ-001 — Create project in active tenant**
- Given U4 (Client HR Director, Tenant A) authenticated with active_tenant_id = T-A.
- When POST `/api/v1/projects` with `{name: "Alpha Grading 2026"}`.
- Then HTTP 201, response contains project UUID, tenant_id NOT echoed from request body. Audit event `PROJECT_CREATED` written with actor=U4, tenant_id=T-A.

**TC-PRJ-002 — List projects shows only accessible**
- Given U3 (Consultant assigned only to Tenant A) authenticated.
- When GET `/api/v1/projects`.
- Then response contains only Tenant A projects; no Tenant B project surfaces.

**TC-PRJ-003 — Switch project requires re-issued context token**
- Given U2 (PM both tenants) currently has context token for Tenant A.
- When POST `/api/v1/auth/switch-context` with `{tenant_id: T-B, project_id: P-B1}`.
- Then backend re-validates membership and issues new short-lived context token. Audit `TENANT_CONTEXT_SWITCH` written.

### 8.2 Organization structure — TC-ORG

**TC-ORG-001 — Create department**
- Given U4 in Tenant A.
- When POST `/api/v1/departments` `{name: "HR", project_id: P-A1}`.
- Then HTTP 201, department persisted in `tenant_alpha.departments`, tenant_id auto-set from session, NOT from request.

**TC-ORG-002 — Department tree retrieval**
- Given U4, departments seeded.
- When GET `/api/v1/departments/tree?project_id=P-A1`.
- Then response returns hierarchical tree scoped to project P-A1.

### 8.3 Position catalog — TC-POS

**TC-POS-001 — Create position with department**
- Given U4, department D-A-HR exists.
- When POST `/api/v1/positions` `{title: "HR Manager", department_id: D-A-HR, project_id: P-A1}`.
- Then HTTP 201; position stored with tenant_id=T-A; audit `POSITION_CREATED`.

**TC-POS-002 — Get position by ID enforces tenant**
- Given U4 in Tenant A; position POS-B-PM belongs to Tenant B.
- When GET `/api/v1/positions/POS-B-PM`.
- Then HTTP 404 (not 403, to avoid existence leakage). Audit `CROSS_TENANT_ACCESS_ATTEMPT`.

### 8.4 Job profile — TC-JP

**TC-JP-001 — Create job profile**
- Given U5 with JOB_PROFILE_CREATE.
- When POST `/api/v1/job-profiles` `{position_id: POS-A-HRM, purpose, duties, requirements}`.
- Then HTTP 201, status=DRAFT, audit `JOB_PROFILE_CREATED`.

**TC-JP-002 — Approve job profile**
- Given U4 with JOB_PROFILE_APPROVE; profile in status UNDER_REVIEW.
- When POST `/api/v1/job-profiles/{id}/approve`.
- Then status=APPROVED, locked for edit, audit `JOB_PROFILE_APPROVED`.

### 8.5 Methodology builder — TC-MTH

**TC-MTH-001 — Create draft methodology with 8 factors and 4-language translations**
- Given U3 (Consultant, Tenant A).
- When POST `/api/v1/methodologies` `{model_type: CLASSIC_8_FACTOR, name, factors[8 with weights/levels/points/translations for ru-RU,uz-Cyrl-UZ,uz-Latn-UZ,en-US]}`.
- Then HTTP 201; status=DRAFT; 8 factors persisted with translations rows.

**TC-MTH-002 — Approve and lock methodology**
- Given draft methodology with valid factor weights summing per scoring mode rules.
- When POST `/api/v1/methodologies/{id}/approve`.
- Then status=APPROVED+LOCKED; audit `METHODOLOGY_APPROVED` and `METHODOLOGY_LOCKED`.

### 8.6 Evaluation + scoring — TC-EVAL

**TC-EVAL-001 — Submit evaluation for position**
- Given approved methodology M-A-V1; position POS-A-HRM.
- When POST `/api/v1/evaluations` with all 8 factor levels selected.
- Then HTTP 201; total_score computed; status=SUBMITTED; audit `EVALUATION_CREATED` + `SCORE_CHANGED`.

**TC-EVAL-002 — Approve evaluation makes it immutable**
- Given evaluation in SUBMITTED.
- When POST `/api/v1/evaluations/{id}/approve` by U4.
- Then status=APPROVED; subsequent PUT/PATCH returns HTTP 409. Audit `EVALUATION_APPROVED`.

### 8.7 Grade structure — TC-GR

**TC-GR-001 — Create 14-grade model with bands**
- Given U4.
- When POST `/api/v1/grade-structures` `{type: 14_GRADE, bands: [{grade:1, min:0, max:99}, ..., {grade:14, min:900, max:1000}]}`.
- Then HTTP 201; bands validated (no overlap, min ≤ max).

**TC-GR-002 — Assign grade from evaluation total_score**
- Given approved evaluation with total_score = 562.4321 (NUMERIC(12,4)).
- When grade assignment runs.
- Then grade equals the band where 562.4321 falls; raw score persisted unrounded; displayed score rounded to 2 decimals.

---

## 9. API Test Checklist (per endpoint group)

For each endpoint in `/auth`, `/admin/tenants`, `/projects`, `/departments`, `/positions`, `/job-profiles`, `/methodologies`, `/methodologies/{id}/versions`, `/evaluations`, `/grade-structures`, `/audit-logs`:

1. Unauthenticated request → 401, no body leakage.
2. Authenticated, no permission → 403 with stable error code, no entity hints.
3. Authenticated, permission, wrong tenant context → 404.
4. Authenticated, permission, wrong project context → 404.
5. Authenticated, permission, correct context → 2xx with DTO (NOT JPA entity).
6. Invalid payload (Zod/Bean Validation) → 400 with localized validation messages.
7. Missing required field → 400 with field-specific error.
8. Invalid status transition (e.g. approve a non-existent draft) → 409.
9. Pagination beyond limit (size > 100) → 400 or capped.
10. Error response format matches contract `{timestamp, traceId, code, message, details}`.
11. Sensitive action writes audit event in the same transaction.
12. Validation messages are localized per `Accept-Language`.
13. `tenant_id` in request body/query is **ignored** — backend derives from session token. Send mismatched tenant_id → result must be identical to omitting it (verified by ArchUnit: no `@RequestParam("tenantId")` in client-facing controllers).

---

## 10. Frontend (UI) Test Checklist

1. AppShell renders active company-client name and active project name.
2. Route guards redirect unauthenticated users to `/login`.
3. `PermissionGate` hides UI elements without permission AND backend still enforces (component test + API test pair).
4. `SalaryValue` component returns masked placeholder `***` or skeleton when `salary_data_permission=false`.
5. Locked methodology UI: all factor edit controls are disabled, with locked badge.
6. Locked evaluation UI: factor selections read-only, "Approved by X on Y" footer.
7. No-access page is reachable and safe (no leaked entity title in route).
8. Loading, empty, error states exist for every list/detail page.
9. Language switcher persists locale in profile + localStorage; rerenders all visible labels.
10. Forms validate with Zod; errors are localized and accessible (`aria-describedby`).
11. No JWT, access token, or refresh token logged to console in any state.
12. No salary value logged to console in any state.
13. No manual `tenant_id` input field in business forms; tenant is implicit from context.
14. Chart tooltips do not render salary unless `SALARY_VIEW` permission present.
15. Recharts/ECharts components mask salary fields in tooltip formatter when permission missing.

---

## 11. Security Test Cases (cross-cutting)

**TC-SEC-001 — JWT signature tampering**
- Given valid token for U4.
- When token signature is altered and sent.
- Then HTTP 401; audit `LOGIN_FAILED` written.

**TC-SEC-002 — JWT claim tampering (escalate to Super Admin)**
- Given valid U4 token.
- When `roles` claim is replaced with `HRLAB_SUPER_ADMIN` (signature broken).
- Then HTTP 401.

**TC-SEC-003 — Expired token**
- Given expired access token.
- Then HTTP 401 with `code: TOKEN_EXPIRED`.

**TC-SEC-004 — Stale tenant context token**
- Given U2's context token for T-A but membership was revoked.
- When any tenant-scoped API call.
- Then HTTP 401/403 with `code: TENANT_CONTEXT_INVALID`; audit `CROSS_TENANT_ACCESS_ATTEMPT`.

**TC-SEC-005 — IDOR via UUID enumeration**
- Given U4 in Tenant A.
- When GET `/api/v1/positions/{any-tenant-B-uuid}` for many guessed UUIDs.
- Then 100% return 404 with identical response shape (no timing/length leak).

**TC-SEC-006 — SQL injection in filters**
- Given U4.
- When GET `/api/v1/positions?title=' OR 1=1--`.
- Then 400 or safe empty result; no SQL error leak.

**TC-SEC-007 — Mass assignment**
- Given U4 creating a position.
- When POST includes extra fields `tenant_id`, `status: APPROVED`, `created_by`.
- Then extra fields ignored; response confirms server-controlled values.

---

## 12. TENANT ISOLATION TEST PACK (TIP) — 18 scenarios

> **Blocking release gate.** Any failure here = NO-GO.

Common precondition: Tenants A and B fully seeded; U3 (Consultant assigned ONLY to Tenant A) holds a valid session.

### TIP-01 — Cannot list Tenant B projects
- Given U3 authenticated with active_tenant_id = T-A.
- When GET `/api/v1/projects`.
- Then response contains only Tenant A projects. P-B1 must not appear. Audit: none (read of own scope). Automation: REST Assured. Severity if failed: Critical.

### TIP-02 — Cannot open Tenant B position by direct UUID
- Given U3; POS-B-PM exists in Tenant B.
- When GET `/api/v1/positions/POS-B-PM`.
- Then HTTP 404; body matches generic not-found shape. Audit: `CROSS_TENANT_ACCESS_ATTEMPT` with target=POS-B-PM. Severity if failed: Critical.

### TIP-03 — Cannot query Tenant B job profile
- Given U3; job profile JP-B-LC exists.
- When GET `/api/v1/job-profiles/JP-B-LC`.
- Then HTTP 404. Audit: `CROSS_TENANT_ACCESS_ATTEMPT`. Severity: Critical.

### TIP-04 — Cannot query Tenant B methodology
- Given U3.
- When GET `/api/v1/methodologies/M-B-V1`.
- Then HTTP 404. Audit: `CROSS_TENANT_ACCESS_ATTEMPT`. Severity: Critical.

### TIP-05 — Cannot query Tenant B evaluation
- Given U3; evaluation EV-B-001 exists.
- When GET `/api/v1/evaluations/EV-B-001`.
- Then HTTP 404. Audit: `CROSS_TENANT_ACCESS_ATTEMPT`. Severity: Critical.

### TIP-06 — Cannot assign grade to Tenant B position
- Given U3.
- When POST `/api/v1/positions/POS-B-PM/grade` `{grade: 8}`.
- Then HTTP 404. Audit: `CROSS_TENANT_ACCESS_ATTEMPT`. Severity: Critical.

### TIP-07 — Cannot export Tenant B report
- Given U3; report R-B-001 (placeholder MVP 2; in MVP 1 use evaluation aggregate endpoint).
- When GET `/api/v1/evaluations?project_id=P-B1`.
- Then HTTP 404 OR filtered to empty with project_id ignored. Audit: `CROSS_TENANT_ACCESS_ATTEMPT`. Severity: Critical.

### TIP-08 — Cannot access Tenant B attachment URL
- Given U3; signed URL minted for tenant B attachment (object storage).
- When U3 attempts to download via backend proxy `/api/v1/files/{fileId}`.
- Then HTTP 404 + audit `CROSS_TENANT_ACCESS_ATTEMPT`. Direct S3 signed URLs MUST require backend authorization re-check. Severity: Critical.

### TIP-09 — Cannot search Tenant B data
- Given U3; search index populated for both tenants.
- When GET `/api/v1/search?q=Production Manager`.
- Then no Tenant B results in response. Severity: Critical.

### TIP-10 — Cannot use guessed project_id
- Given U3.
- When GET `/api/v1/projects/P-B1/positions`.
- Then HTTP 404. Audit: `CROSS_TENANT_ACCESS_ATTEMPT`. Severity: Critical.

### TIP-11 — Stale tenant context token rejected
- Given U2's context token for T-A was issued, then U2's membership in T-A was revoked.
- When any API call with that token.
- Then HTTP 401 `TENANT_CONTEXT_INVALID`. Audit: `CROSS_TENANT_ACCESS_ATTEMPT`. Severity: Critical.

### TIP-12 — Manipulated tenant_id in request body ignored
- Given U3 (active T-A) creating a position.
- When POST `/api/v1/positions` with body `{title:"X", department_id:D-A-HR, tenant_id: T-B}`.
- Then HTTP 201 with persisted tenant_id = T-A (NOT T-B). The `tenant_id` field MUST be marked `@JsonIgnore` or absent from the DTO. Severity: Critical.

### TIP-13 — Manipulated tenant_id in query string ignored
- Given U3 (active T-A).
- When GET `/api/v1/positions?tenant_id=T-B`.
- Then response is scoped to T-A; query param has no effect. ArchUnit rule forbids `@RequestParam("tenantId"|"tenant_id")` in non-admin controllers. Severity: Critical.

### TIP-14 — Cannot see Tenant B data via dashboard aggregates
- Given U3.
- When GET `/api/v1/analytics/grade-distribution`.
- Then aggregates include only T-A. No counts/sums include T-B records even at aggregate level. Severity: Critical.

### TIP-15 — AI assistant denies cross-tenant context (foundation in MVP 1)
- Given U3 with AI endpoint stubbed (MVP 1 foundation only).
- When POST `/api/v1/ai/suggest` `{position_id: POS-B-PM}`.
- Then HTTP 404 before any prompt is constructed. No tenant B data appears in any AI prompt log. Severity: Critical.

### TIP-16 — Cache does not leak across tenants
- Given U4 (T-A) fetches `/api/v1/positions` populating Redis cache.
- When U9 (T-B) makes identical request shape.
- Then U9 receives T-B data only. Cache keys MUST include `tenant_id`. Integration test inspects Redis keys. Severity: Critical.

### TIP-17 — Background job cannot be triggered for another tenant
- Given U3.
- When POST `/api/v1/jobs/recalculate` `{project_id: P-B1}`.
- Then HTTP 404. Worker validates tenant from session, not from job payload. Severity: Critical.

### TIP-18 — Cannot download generated report from Tenant B
- Given U3; signed download URL for report R-B-001 obtained out-of-band.
- When GET `/api/v1/reports/R-B-001/download`.
- Then HTTP 404. Audit: `CROSS_TENANT_ACCESS_ATTEMPT`. Severity: Critical.

**Automation:** All 18 cases are REST Assured + Testcontainers tests executed in CI on every PR and at staging post-deploy.

---

## 13. RBAC + ABAC TEST PACK

### 13.1 Role × Permission positive/negative

For each role (HRLab Super Admin, HRLab PM, HRLab Consultant, HRLab Analyst, Client Company Admin, Client HR Director, Client HR Specialist, Evaluation Committee Member, Department Manager, Viewer, External Auditor):

For each module endpoint group:

- Positive: role with declared permission → 2xx.
- Negative: role without permission → 403.

### 13.2 Key ABAC cases

**TC-ABAC-001 — Department scope**
- Given U6 (Department Manager, HR dept only, Tenant A).
- When GET `/api/v1/positions?project_id=P-A1`.
- Then only positions in HR department returned (POS-A-HRM); Financial Analyst and IT Specialist filtered out.

**TC-ABAC-002 — External Auditor read-only**
- Given U8 with AUDIT_READ.
- When POST `/api/v1/positions`.
- Then HTTP 403 even though tenant context is valid.

**TC-ABAC-003 — External Auditor sees audit log but no salary**
- Given U8.
- When GET `/api/v1/audit-logs`.
- Then 200; salary-related entries (e.g. `SALARY_VIEW`) appear with `details.salary_value` redacted.

**TC-ABAC-004 — Consultant assigned to T-A cannot access T-B**
- See TIP-01 through TIP-18.

**TC-ABAC-005 — Super Admin uses admin endpoints only**
- Given U1.
- When GET `/api/v1/projects` (client-data endpoint).
- Then 403. Admin must call `/api/v1/admin/projects` instead.

**TC-ABAC-006 — Permission separation: SALARY_VIEW, AUDIT_READ, REPORT_EXPORT independent**
- Given U10 (GRADE_READ only, no SALARY_VIEW).
- When GET `/api/v1/positions/POS-A-HRM/salary`.
- Then HTTP 403 `SALARY_PERMISSION_REQUIRED`; if endpoint not present in MVP 1, instead verify that the response of any endpoint NEVER includes a salary field for U10.

**TC-ABAC-007 — Role with READ cannot CREATE**
- Given Viewer U7 with POSITION_READ.
- When POST `/api/v1/positions`.
- Then 403.

**TC-ABAC-008 — Role with CREATE cannot APPROVE**
- Given U5 with EVALUATION_CREATE only.
- When POST `/api/v1/evaluations/{id}/approve`.
- Then 403.

### 13.3 Frontend visibility ↔ backend enforcement parity

For every UI element that is permission-gated by `PermissionGate`, there MUST be a paired direct-API test confirming the same restriction on the backend. The QA pipeline fails if any frontend gate has no backend counterpart (tracked in a `permission-coverage.json` artifact).

---

## 14. METHODOLOGY LOCKING / VERSIONING TEST PACK

### TC-MTH-LOCK-001 — Locked methodology read-only
- Given M-A-V1 in status APPROVED+LOCKED.
- When PUT `/api/v1/methodologies/M-A-V1` `{name: "Renamed"}`.
- Then HTTP 409 `METHODOLOGY_LOCKED`. No DB mutation. Audit `METHODOLOGY_EDIT_REJECTED`.

### TC-MTH-LOCK-002 — Cannot edit locked methodology's factors
- Given M-A-V1 LOCKED.
- When PUT `/api/v1/factors/{factor_id}` `{points: 200}` for a factor of M-A-V1.
- Then HTTP 409.

### TC-MTH-LOCK-003 — Editing approved methodology creates new version
- Given M-A-V1 LOCKED.
- When POST `/api/v1/methodologies/M-A-V1/versions` `{changes: {...}}`.
- Then HTTP 201 with new version M-A-V2 in status DRAFT; M-A-V1 unchanged; audit `METHODOLOGY_VERSION_CREATED`.

### TC-MTH-LOCK-004 — Old evaluations remain linked to old version
- Given evaluation EV-A-001 was created against M-A-V1; M-A-V2 now exists.
- When GET `/api/v1/evaluations/EV-A-001`.
- Then response shows `methodology_version_id = M-A-V1`. Recalculation under V2 is a separate explicit workflow (not in MVP 1; foundation only).

### TC-MTH-LOCK-005 — Factor weight validation
- Given draft methodology in WEIGHTED_POINTS mode.
- When weights configured such that sum mismatch occurs (mode-dependent rule).
- Then validation error on approve.

### TC-MTH-LOCK-006 — Factor translation completeness
- Given draft methodology with factor missing `uz-Latn-UZ` translation.
- When POST approve.
- Then validation error: `TRANSLATION_INCOMPLETE` listing missing locale per factor.

### TC-MTH-LOCK-007 — Approve produces audit
- Given draft.
- When approve.
- Then audit events `METHODOLOGY_APPROVED` and `METHODOLOGY_LOCKED` written.

### TC-MTH-LOCK-008 — Permission required for new version
- Given user without METHODOLOGY_VERSION_CREATE.
- When POST new version.
- Then 403.

---

## 15. SCORING ENGINE TEST PACK

> **Reproducibility is a blocking release gate.** Same inputs MUST yield identical outputs every run.

### Test data fixtures

```
Methodology M-T-DIRECT (DIRECT_POINTS)
  Factor F1: levels L1=10, L2=20, L3=30 points
  Factor F2: levels L1=5,  L2=15, L3=25 points
  Selected: F1=L2, F2=L3 → expected total_score = 20 + 25 = 45.0000

Methodology M-T-WPTS (WEIGHTED_POINTS)
  Factor F1: weight_points=100, max_level_points=4, level L3=3
  Factor F2: weight_points=200, max_level_points=5, level L4=4
  F1 score = (3/4)*100 = 75.0000
  F2 score = (4/5)*200 = 160.0000
  total = 235.0000

Methodology M-T-WSCALE (WEIGHTED_SCALE)
  Factor F1: weight=10, level scale_value=4 → 40
  Factor F2: weight=20, level scale_value=3 → 60
  total = 100.0000
```

### TC-SCORE-001 — DIRECT_POINTS correctness
- Given M-T-DIRECT.
- When evaluation submitted with F1=L2, F2=L3.
- Then total_score = 45.0000 (NUMERIC(12,4)); displayed_score = 45.00.

### TC-SCORE-002 — WEIGHTED_POINTS correctness
- Given M-T-WPTS.
- When evaluation submitted.
- Then total_score = 235.0000 exactly; computed with BigDecimal, scale=4, RoundingMode.HALF_UP.

### TC-SCORE-003 — WEIGHTED_SCALE correctness
- Given M-T-WSCALE.
- When evaluation submitted.
- Then total_score = 100.0000.

### TC-SCORE-004 — BigDecimal precision (no float drift)
- Given factor with computation that would drift under `double` (e.g. (1/3)*900).
- When evaluation submitted 1000 times in a loop.
- Then total_score is identical across all 1000 runs, byte-for-byte equal in DB.

### TC-SCORE-005 — Required factor missing → incomplete
- Given methodology where F1 is required.
- When evaluation submitted without F1.
- Then HTTP 400 `EVALUATION_INCOMPLETE` listing missing required factors. No record persisted in SUBMITTED state.

### TC-SCORE-006 — Optional factor missing
- Given methodology where F2 is optional (per `factor.required=false`).
- When evaluation submitted without F2.
- Then evaluation persists; F2 contributes 0 (or excluded per methodology setting).

### TC-SCORE-007 — N/A factor requires explicit comment
- Given factor marked N/A by evaluator.
- When evaluation submitted with N/A but no comment.
- Then HTTP 400 `NA_REQUIRES_COMMENT`.

### TC-SCORE-008 — Manual adjustment requires permission + comment
- Given user with EVALUATION_ADJUST permission.
- When PATCH `/api/v1/evaluations/{id}/adjustments` `{factor_id, new_score, comment: ""}`.
- Then HTTP 400 `COMMENT_REQUIRED`. Without permission → 403. With both → audit `SCORE_CALIBRATED` with before/after/reason.

### TC-SCORE-009 — Approved evaluation immutable
- Given evaluation EV-A-001 in APPROVED.
- When PUT `/api/v1/evaluations/EV-A-001` `{...}`.
- Then HTTP 409 `EVALUATION_LOCKED`.

### TC-SCORE-010 — Grade assignment uses raw score (not rounded displayed)
- Given total_score = 562.5499 (NUMERIC(12,4)). Displayed = 562.55. Band [550, 562.55] vs [562.56, 600].
- When grade is computed.
- Then grade derived from raw 562.5499 → band [550, 562.55] (inclusive boundary semantics declared by methodology). Assert grade does NOT change due to display rounding.

### TC-SCORE-011 — Boundary score maps to single grade
- Given band B1: min=0, max=99.99, band B2: min=100, max=199.99 (no overlap).
- When total_score = 100.0000.
- Then grade = B2. When total_score = 99.9999, grade = B1.

### TC-SCORE-012 — Score change writes audit
- Given evaluation in DRAFT/SUBMITTED.
- When score changed.
- Then audit `SCORE_CHANGED` with before/after and actor.

### TC-SCORE-013 — Recalculation does not overwrite historical approved evaluation
- Given EV-A-001 APPROVED under M-A-V1; M-A-V2 created.
- When recalculation scenario run.
- Then EV-A-001 unchanged; a separate `recalculation_result` row created with old_score/new_score/delta; original requires explicit workflow approval before promotion (workflow itself is MVP 2 — MVP 1 verifies immutability).

### TC-SCORE-014 — Reproducibility golden test
- Given fixed seed dataset (10 positions × 8 factors with deterministic level selections).
- When scoring run twice on different VMs.
- Then SHA-256 of serialized evaluation outputs identical across runs.

---

## 16. GRADE STRUCTURE / ASSIGNMENT TEST PACK

### TC-GR-001 — Create 14-grade model
- Given U4 with GRADE_STRUCTURE_CREATE.
- When POST `/api/v1/grade-structures` with 14 bands, contiguous, no overlap.
- Then HTTP 201; audit `GRADE_STRUCTURE_CREATED`.

### TC-GR-002 — Create 16-grade model
- Identical to TC-GR-001 with 16 bands.

### TC-GR-003 — Create custom grade model
- Custom band count (e.g. 10) with custom widths.

### TC-GR-004 — Bands cannot overlap
- Given POST with bands {min:0,max:100} and {min:90,max:200}.
- Then HTTP 400 `GRADE_BANDS_OVERLAP`.

### TC-GR-005 — min_score ≤ max_score
- Given band {min:200, max:100}.
- Then HTTP 400 `INVALID_BAND_RANGE`.

### TC-GR-006 — No-gap warning/block
- Given bands {min:0, max:99} and {min:200, max:300} (gap 100-199).
- Then HTTP 400 (block) by default; OR warning if methodology allows gaps (configurable).

### TC-GR-007 — Score maps to correct grade
- See TC-SCORE-011.

### TC-GR-008 — Boundary scores
- Test min boundary, max boundary, exact midpoint, and ±0.0001.

### TC-GR-009 — Manual calibration requires comment
- Given user with GRADE_CALIBRATE.
- When PATCH `/api/v1/evaluations/{id}/grade` `{grade: 9, comment: ""}`.
- Then 400 `COMMENT_REQUIRED`.

### TC-GR-010 — Grade approval audit
- When POST approve.
- Then audit `GRADE_APPROVED`.

### TC-GR-011 — Locked grade structure immutable
- Given grade structure LOCKED.
- When PUT `/api/v1/grade-structures/{id}`.
- Then 409.

---

## 17. AUDIT TRAIL TEST PACK (20 events)

> Audit completeness is a blocking release gate.

For each event below: trigger the action, assert audit row exists with `{audit_id, tenant_id, project_id, actor_user_id, action, entity_type, entity_id, before, after, reason, ip_address, user_agent, created_at, hash_prev, hash_current}` and that consecutive `hash_current` chains correctly.

### Events covered

| # | Event code | Trigger | TC |
|---|------------|---------|----|
| 1 | `LOGIN_SUCCESS` | Successful login | TC-AUD-001 |
| 2 | `LOGIN_FAILED` | Bad password | TC-AUD-002 |
| 3 | `TENANT_CONTEXT_SWITCH` | switch tenant | TC-AUD-003 |
| 4 | `PROJECT_CONTEXT_SWITCH` | switch project | TC-AUD-004 |
| 5 | `PERMISSION_CHANGED` | grant/revoke perm | TC-AUD-005 |
| 6 | `ROLE_CHANGED` | assign/revoke role | TC-AUD-006 |
| 7 | `PROJECT_CREATED/EDITED/ARCHIVED` | project lifecycle | TC-AUD-007 |
| 8 | `POSITION_CREATED/EDITED` | position lifecycle | TC-AUD-008 |
| 9 | `JOB_PROFILE_CREATED/EDITED/APPROVED` | profile lifecycle | TC-AUD-009 |
| 10 | `METHODOLOGY_CREATED/EDITED/APPROVED/LOCKED` | methodology lifecycle | TC-AUD-010 |
| 11 | `FACTOR_CREATED/EDITED` | factor changes | TC-AUD-011 |
| 12 | `SCORE_CHANGED` | evaluation score edit | TC-AUD-012 |
| 13 | `EVALUATION_APPROVED` | approve evaluation | TC-AUD-013 |
| 14 | `SCORE_CALIBRATED` | manual calibration | TC-AUD-014 |
| 15 | `GRADE_STRUCTURE_APPROVED` | grade approval | TC-AUD-015 |
| 16 | `SALARY_VIEW/EXPORT/SCENARIO_RUN` | salary action (foundation: ensure permission code wired even if endpoint stubbed) | TC-AUD-016 |
| 17 | `REPORT_GENERATED/DOWNLOADED` | report lifecycle (foundation; MVP 2) | TC-AUD-017 |
| 18 | `FILE_UPLOADED/DOWNLOADED` | file actions (foundation) | TC-AUD-018 |
| 19 | `AI_SUGGESTION_GENERATED/ACCEPTED/REJECTED` | AI events (foundation; MVP 4) | TC-AUD-019 |
| 20 | `CROSS_TENANT_ACCESS_ATTEMPT` | any TIP-* trigger | TC-AUD-020 |

### TC-AUD-APPEND-001 — Audit log is append-only
- Given an existing audit row.
- When PUT/PATCH/DELETE `/api/v1/audit-logs/{id}` for any user including Super Admin.
- Then HTTP 405/403; audit table has no UPDATE/DELETE permission for application DB role (PostgreSQL grant check).

### TC-AUD-REDACT-001 — Salary fields redacted unless SALARY_VIEW
- Given audit row `SALARY_VIEW` with before/after containing salary numbers.
- When U8 (AUDIT_READ, no SALARY_VIEW) GET `/api/v1/audit-logs/{id}`.
- Then salary fields replaced with `"***"`; original retained in DB.

### TC-AUD-PERM-001 — AUDIT_READ required
- Given user without AUDIT_READ.
- When GET `/api/v1/audit-logs`.
- Then 403.

### TC-AUD-FILTER-001 — Filterable by actor/action/entity/date
- Given seeded audit log.
- When GET `/api/v1/audit-logs?action=POSITION_CREATED&actor=U4&from=2026-01-01&to=2026-12-31`.
- Then only matching rows returned.

### TC-AUD-HASH-001 — Hash chain integrity
- Given last 100 rows.
- When script verifies `sha256(prev.hash_current || row.canonical_json) == row.hash_current`.
- Then 100% match. Any failure = Critical defect.

### TC-AUD-TX-001 — Audit and business action atomic
- Given simulated DB failure after business write but before audit write.
- When transaction commits.
- Then both written or both rolled back. (Spring `@Transactional` boundary covers both.)

---

## 18. SALARY PERMISSION FOUNDATION TEST PACK

> Even though salary engine is MVP 3, MVP 1 must wire and test the permission separation.

### TC-SAL-FND-001 — `SALARY_VIEW` permission code exists
- Verify `permissions` table contains rows: `SALARY_VIEW`, `SALARY_EDIT`, `SALARY_EXPORT`, `SALARY_SCENARIO_RUN`.

### TC-SAL-FND-002 — No default role grants `SALARY_VIEW` in MVP 1
- Verify role_permission seed: no role has `SALARY_VIEW`.

### TC-SAL-FND-003 — POSITION_READ does not imply SALARY_VIEW
- Given U10 with POSITION_READ + GRADE_READ but NOT SALARY_VIEW.
- When GET `/api/v1/positions/POS-A-HRM`.
- Then response DTO has NO salary fields at all (not even `null`); JSON schema validation enforces absence.

### TC-SAL-FND-004 — GRADE_READ does not imply SALARY_VIEW
- Given U10.
- When GET `/api/v1/positions/POS-A-HRM/grade`.
- Then response contains grade only; no salary.

### TC-SAL-FND-005 — Salary value never logged
- Given any test scenario.
- When parsing application logs (CI captures log artifact).
- Then assert no line matches regex for currency-like numeric values associated with salary keys.

### TC-SAL-FND-006 — Salary not in chart tooltip
- Given dashboard chart that may receive salary in payload in future.
- When U10 hovers tooltip.
- Then tooltip formatter renders `***`.

### TC-SAL-FND-007 — Salary action audit foundation
- Given U11 (placeholder with SALARY_VIEW).
- When (mocked) salary endpoint called in test rig.
- Then audit `SALARY_VIEW` event would be created (verified via unit test of audit interceptor).

### TC-SAL-FND-008 — Salary export permission separate
- Given U11 with SALARY_VIEW but NOT SALARY_EXPORT.
- When (stub) GET `/api/v1/reports/salary` is called in MVP 2 surface.
- Then 403. (MVP 1: verify permission check is in place even if endpoint returns 501.)

### TC-SAL-FND-009 — `salary_data_permission` claim in JWT
- Given U10 token.
- When token decoded.
- Then claim `salary_data_permission = false` present.

### TC-SAL-FND-010 — Grade ≠ Salary access
- Comprehensive cross-check: across U4, U5, U6, U7, U8, U10 (all should have no salary access). Only U11 has it. Verified by parameterized JUnit test reading role matrix.

---

## 19. LOCALIZATION TEST PACK

### TC-L10N-001 — Language switcher works (UI)
- Given U4 on dashboard.
- When user switches locale ru-RU → uz-Latn-UZ.
- Then all visible labels re-render in Uzbek Latin; `Accept-Language` header on subsequent API calls = `uz-Latn-UZ`; profile.locale updated.

### TC-L10N-002 — ru-RU labels shown
- Given locale ru-RU.
- Then key UI strings: "Проекты", "Должности", "Методология", "Грейды".

### TC-L10N-003 — uz-Cyrl-UZ labels shown
- Then: "Лойиҳалар", "Лавозимлар", "Методология", "Грейдлар".

### TC-L10N-004 — uz-Latn-UZ labels shown
- Then: "Loyihalar", "Lavozimlar", "Metodologiya", "Greydlar".

### TC-L10N-005 — en-US labels shown
- Then: "Projects", "Positions", "Methodology", "Grades".

### TC-L10N-006 — Methodology factor translation works
- Given factor with translations in 4 locales.
- When U4 in locale ru-RU views methodology.
- Then factor label = ru-RU translation.

### TC-L10N-007 — Factor level translation works
- Same as above for factor levels.

### TC-L10N-008 — Missing translation fallback
- Given factor with missing uz-Latn-UZ translation.
- When viewed in uz-Latn-UZ.
- Then fallback in this order: en-US → ru-RU → factor.code. UI shows a small "translation missing" badge for admins; non-admins see the fallback transparently.

### TC-L10N-009 — UI layout handles Uzbek text length
- Given Uzbek translations that are 30% longer than English (verified by axe-core layout assertions).
- Then no overflow, no clipped buttons, no horizontal scroll on primary breakpoints (1280, 1024, 768).

### TC-L10N-010 — Validation messages localized
- Given U4 in uz-Latn-UZ submits invalid form.
- Then validation error messages are in uz-Latn-UZ (both client Zod and server Bean Validation).

### TC-L10N-011 — Status badges localized
- All status values (DRAFT, APPROVED, LOCKED, ARCHIVED) render with localized labels.

### TC-L10N-012 — Report labels localization-ready
- MVP 1: verify `report_template_translations` table exists with rows for sample template across 4 locales.

### TC-L10N-013 — `Accept-Language` honored by API for validation errors
- Send POST with invalid payload and `Accept-Language: uz-Cyrl-UZ`.
- Response error messages in uz-Cyrl-UZ.

---

## 20. Negative and Edge Cases (general)

1. Empty string fields → rejected with `VALIDATION_REQUIRED`.
2. Unicode edge: emoji in position title → accepted; surrogate pair handled.
3. Very long string > 10,000 chars → rejected with `VALIDATION_MAX_LENGTH`.
4. SQL meta-chars in filters → safely parameterized.
5. Numeric overflow (score > Integer.MAX_VALUE) → rejected; BigDecimal scale exceeded → rejected.
6. Negative weights, negative points → rejected.
7. Duplicate factor code in same methodology → rejected.
8. Duplicate department slug in same project → rejected.
9. Circular department hierarchy → rejected.
10. Pagination: page=0, page=-1, size=0, size=1001 → 400 or capped.
11. Concurrent edits (optimistic locking): two PUTs with stale version → second receives 409.
12. Time zones: created_at always stored UTC; display in user locale.
13. Daylight saving boundary in audit timestamps → monotonic via `hash_chain`.
14. Locale switching mid-request: backend uses request `Accept-Language`, not stale session.
15. Unauthenticated WebSocket / SSE attempts → 401.

---

## 21. Automation Plan

### Backend (Java 21 / Spring Boot 3.x)

| Layer | Tool | Coverage target |
|-------|------|-----------------|
| Unit | JUnit 5 + AssertJ + Mockito | ≥ 85% line in `domain` and `application` packages |
| Integration | Spring Boot Test + Testcontainers (PostgreSQL 16, Redis 7, MinIO) | ≥ 70% on `infrastructure` |
| API | REST Assured | 100% of MVP 1 endpoints |
| Architecture | ArchUnit | rules: no JPA in `api`; no `tenant_id` request params; tenant-aware repository methods only |
| Security | REST Assured + WireMock for IdP | All BOLA/IDOR/tenant tests |
| Migrations | Liquibase + Testcontainers | every changelog re-applies cleanly on a fresh DB |

**ArchUnit rules (illustrative):**

```java
classes().that().resideInAPackage("..api..")
  .should().notDependOnClassesThat().resideInAPackage("..infrastructure.persistence.jpa..");

methods().that().areDeclaredInClassesThat().resideInAPackage("..api..")
  .should().notHaveParameterOfType("java.lang.String").andShould()
  .notBeAnnotatedWith("@RequestParam(\"tenantId\")");

methods().that().areDeclaredInClassesThat().areAnnotatedWith(Repository.class)
  .and().haveNameMatching("findBy.*Id$")
  .should().haveNameMatching("findBy.*Id.*TenantId.*"); // tenant-aware names enforced
```

### Frontend (React + TS + Vite)

| Layer | Tool |
|-------|------|
| Unit / component | Vitest + React Testing Library |
| API mocking | MSW |
| E2E | Playwright (projects: ru-RU, uz-Cyrl-UZ, uz-Latn-UZ, en-US) |
| Accessibility | axe-core (integrated in Playwright + RTL) |
| Type safety | tsc --noEmit in CI |

### Security / DAST

- OWASP ZAP baseline scan on staging URL (read-only, authenticated session for U4).
- Snyk for dependency vulnerabilities (Java + JS).
- Trivy for Docker image scan.

### CI/CD pipeline

```
1. Static analysis (Spotless, Checkstyle, ESLint, tsc)
2. Unit tests (backend + frontend)
3. Integration tests (Testcontainers)
4. API + tenant isolation pack (REST Assured)
5. Component tests (Vitest)
6. ArchUnit
7. Build
8. Liquibase dry run
9. Deploy to dev
10. E2E Playwright (smoke + critical journeys)
11. Promote to staging
12. OWASP ZAP baseline + tenant isolation regression
13. Sign-off → production
```

---

## 22. Regression Suite (release-blocking)

Run on every deploy to staging and production:

1. Smoke tests (login, list projects, open position, run evaluation).
2. Full Tenant Isolation Pack (18 scenarios) — **release blocker**.
3. RBAC/ABAC matrix (all 11 personas).
4. Methodology lock/version pack.
5. Scoring reproducibility golden test (TC-SCORE-014).
6. Grade boundary tests.
7. Audit trail completeness (20 events) + hash chain integrity.
8. Salary masking smoke (TC-SAL-FND-003 to 006).
9. Localization smoke (TC-L10N-001 to 005).
10. API contract tests (JSON Schema).
11. ArchUnit rules.

---

## 23. Defect Severity Matrix

| Severity | Definition | Examples (MVP 1) | Release impact |
|----------|------------|------------------|----------------|
| **Critical** | Data leakage, auth bypass, data corruption, audit tampering | TIP-* failure; salary data appears for U10; backend persists frontend-supplied tenant_id; audit row deletable via API; scoring non-deterministic; approved methodology editable | **NO-GO. Block release.** |
| **High** | Privilege escalation; missing audit for sensitive action; BOLA/IDOR; approved methodology editable through alternate path; report export leaks fields; file access bypass | RBAC matrix gap; SCORE_CHANGED audit missing; grade band overlap accepted; required factor missing yet evaluation approved | **NO-GO. Block release.** |
| **Medium** | Validation gaps; incorrect error code; incomplete localization on core screens; missing locked/no-access UI state; inconsistent status transitions | uz-Cyrl-UZ missing for "Approve" button; 500 instead of 400 on bad payload; methodology locked banner missing | Conditional GO with documented mitigation; must be fixed within next sprint. |
| **Low** | Minor UI defects; copy issues; non-blocking accessibility issue; cosmetic alignment | Tooltip slightly offset; mismatched comma in Russian copy | GO. Backlog. |

---

## 24. RELEASE GATE CHECKLIST (MVP 1)

> **MVP 1 ships only if ALL of the following are TRUE.**

### Blocking conditions (any one FALSE = NO-GO)

- [ ] All 18 Tenant Isolation Pack scenarios PASS in staging and production canary.
- [ ] No Critical defect open.
- [ ] No High defect open.
- [ ] ArchUnit rules pass (no `tenant_id` in client-facing controllers; tenant-aware repositories enforced).
- [ ] No repository method returns data without tenant scope (verified by ArchUnit + integration tests).
- [ ] Approved methodology is provably read-only (TC-MTH-LOCK-001 PASS).
- [ ] Any edit to approved methodology creates a new version (TC-MTH-LOCK-003 PASS).
- [ ] Old evaluations remain linked to old methodology version (TC-MTH-LOCK-004 PASS).
- [ ] Evaluation scoring reproducibility golden test PASS (TC-SCORE-014).
- [ ] Required-missing prevents evaluation approval (TC-SCORE-005 PASS).
- [ ] Manual adjustment requires permission AND comment (TC-SCORE-008 PASS).
- [ ] Grade assignment uses raw score, not rounded (TC-SCORE-010 PASS).
- [ ] Grade bands validated (no overlap, no gaps unless explicitly allowed; min ≤ max).
- [ ] All 20 audit events fire (TC-AUD-001 to 020 PASS).
- [ ] Audit log is append-only (TC-AUD-APPEND-001 PASS).
- [ ] Audit hash chain verifies (TC-AUD-HASH-001 PASS).
- [ ] Salary fields redacted for non-SALARY_VIEW users in audit (TC-AUD-REDACT-001 PASS).
- [ ] No salary field present in any DTO for users without SALARY_VIEW (TC-SAL-FND-003, 004 PASS).
- [ ] No salary value or token in application logs.
- [ ] Localization works in all 4 languages on core navigation (TC-L10N-001 to 005 PASS).
- [ ] OWASP ZAP baseline shows no High/Critical findings.
- [ ] Snyk + Trivy: no Critical CVE in production dependencies.
- [ ] CI build green; coverage targets met.
- [ ] Liquibase migrations apply cleanly on fresh DB and rollback verified.
- [ ] Security Engineer ship/block decision = SHIP.
- [ ] Product Owner accept/reject decision = ACCEPT.

### Non-blocking (track but not gating)

- [ ] Medium defects have owners and target dates.
- [ ] Performance smoke meets baseline (p95 < 500ms for list endpoints with 100 items).
- [ ] Accessibility audit (axe-core) reports no Critical issues on core screens.

---

## 25. 4-Sprint QA Plan

**Sprint cadence:** 2 weeks each. QA paired with backend, frontend, and security agents.

### Sprint 1 — Foundation: Tenancy, Identity, Projects, Org

QA deliverables:
- Test data seeders (Tenant A + B; 11 users).
- Tenant Isolation Pack TIP-01 to TIP-10 automated (REST Assured).
- RBAC matrix scaffolding (roles × endpoints CSV → parameterized JUnit).
- ArchUnit rules baseline (no tenant_id params; no JPA in api).
- Audit pack: TC-AUD-001 (LOGIN), TC-AUD-003 (context switch), TC-AUD-007 (project lifecycle), append-only, hash chain.
- Playwright skeleton + login E2E across 4 locales.

Gate at sprint end: tenancy foundation, RBAC, and project CRUD demonstrably tenant-isolated.

### Sprint 2 — Position catalog, Job profile, Methodology builder, Locale dictionaries

QA deliverables:
- TIP-11 to TIP-15 automated (stale token, manipulated tenant_id body/query, dashboard aggregates, AI stub).
- Methodology Pack (TC-MTH-LOCK-001 to 008).
- Localization pack TC-L10N-001 to 008 automated (Playwright multi-project + i18n key coverage report).
- Position + Job Profile CRUD test suite.
- Component tests: PermissionGate, locked methodology UI.

Gate: methodology lock + version logic green; localization works end-to-end.

### Sprint 3 — Scoring engine, Grade structure, Evaluation lifecycle

QA deliverables:
- Scoring Pack (TC-SCORE-001 to 014) including reproducibility golden test.
- Grade Structure Pack (TC-GR-001 to 011).
- TIP-16 to TIP-18 (cache, background job, report download foundation).
- Audit pack: TC-AUD-008, 009, 010, 011, 012, 013, 014, 015.
- Salary Permission Foundation Pack TC-SAL-FND-001 to 010.
- Performance smoke for scoring (1000-evaluation batch under 30s).

Gate: scoring is reproducible; grade boundaries correct; salary permission foundation verified.

### Sprint 4 — Hardening, regression, release readiness

QA deliverables:
- Full regression suite green on staging.
- OWASP ZAP baseline scan signed off.
- Snyk + Trivy clean.
- Accessibility audit (axe-core) on all 22 core screens.
- All 11 personas executed end-to-end in Playwright.
- Defect triage burndown to zero Critical/High.
- QA sign-off package delivered (this template, signed).

Gate: MVP 1 release GO/NO-GO meeting.

---

## 26. Tasks for backend-engineer agent

1. Enforce tenant context in `SecurityContext`; remove all client-facing endpoints that accept `tenant_id` as request param or body field (mark `@JsonIgnore`).
2. Implement short-lived tenant context token endpoint `POST /api/v1/auth/switch-context` with membership re-validation.
3. All repository methods must follow `findByIdAndTenantIdAndProjectId(...)` naming; add ArchUnit rule and migrate any violators.
4. Apply PostgreSQL RLS policies for every tenant-scoped table; verify in Testcontainers integration test.
5. Implement audit interceptor that writes audit rows transactionally with business actions (`@Transactional` boundary covers both); produce hash chain (`hash_current = sha256(prev.hash_current || canonical_json(row))`).
6. Implement methodology lock workflow: approved methodology rejects all PUT/PATCH/DELETE on itself and its factors/levels; new version endpoint creates clone in DRAFT.
7. Implement scoring engine with `BigDecimal` (scale=4, `RoundingMode.HALF_UP`); expose unit test hook for golden reproducibility.
8. Define `permissions` seed: include `SALARY_VIEW`, `SALARY_EDIT`, `SALARY_EXPORT`, `SALARY_SCENARIO_RUN`; ensure no default MVP 1 role has any of them.
9. Implement DTO mapping that strips salary fields entirely when caller lacks `SALARY_VIEW` (not null — absent).
10. Cache keys MUST include tenant_id; document key format and write Redis key-shape integration test.
11. Provide Liquibase changelogs with tenant schema provisioning script and rollback.
12. Implement i18n message resolution honoring `Accept-Language` for API validation errors; localize all error codes.
13. Implement file access proxy `/api/v1/files/{fileId}` that always re-checks tenant before signing/streaming.

## 27. Tasks for frontend-engineer agent

1. AppShell shows active company-client name and active project name; no manual tenant_id input anywhere.
2. Implement `PermissionGate` component; pair every gated UI element with a backend permission code (export `permission-coverage.json` for QA).
3. Implement `SalaryValue` masked component (default mask `***`).
4. Locked methodology screens: disable all factor edit controls when `methodology.status === 'LOCKED'`, show locked badge.
5. Locked evaluation screens: read-only with approver footer.
6. Add `/no-access` page; route guards redirect there without leaking entity names.
7. Loading, empty, error states on every list and detail page; standardize via shared components.
8. Language switcher writes locale to profile API + localStorage; trigger TanStack Query refetch with new `Accept-Language`.
9. i18next setup with 4 namespaces; missing-key behavior fails the build in CI.
10. Zod schemas mirror backend constraints; validation messages keyed to i18n.
11. Recharts/ECharts tooltip formatters mask salary unless permission present.
12. Console logging linter: forbid logging of `token`, `salary`, `password` keys; CI check.
13. Playwright E2E project matrix for ru-RU / uz-Cyrl-UZ / uz-Latn-UZ / en-US.

## 28. Tasks for cybersecurity (security-engineer) agent

1. Threat model coverage for all 18 TIP scenarios — sign off mapping to STRIDE categories.
2. Define JWT structure and signing algorithm; provide test vectors for tamper/expiry tests.
3. Approve PostgreSQL RLS policies before sprint 1 closes.
4. Define encryption-at-rest strategy for salary fields (MVP 3 foundation: KMS, envelope encryption, tenant-specific DEK).
5. Approve audit hash chain algorithm and canonical JSON form.
6. OWASP ZAP baseline configuration; provide authenticated session script for U4.
7. Snyk + Trivy CI integration; define CVE blocking thresholds.
8. Secret scanning (gitleaks) in CI; pre-commit hook.
9. Review every `403` vs `404` decision; document existence-leak policy (favor 404).
10. Sign off ship/block decision at end of Sprint 4.

---

## 29. QA Sign-off Template

```
=========================================
QA Sign-off Report — grading.hrlab.uz
Release: MVP 1
Build: <git sha>
Date: <yyyy-mm-dd>
QA Lead: <name>
=========================================

1. Scope verified
   [ ] Tenant isolation foundation
   [ ] Users / roles / permissions
   [ ] Project workspace
   [ ] Organization structure
   [ ] Position catalog
   [ ] Job profile
   [ ] Methodology builder
   [ ] Scoring engine
   [ ] Grade assignment
   [ ] Audit trail
   [ ] Localization foundation

2. Test execution summary
   Total cases executed: <n>
   Passed: <n>
   Failed: <n>
   Blocked: <n>
   Skipped (with justification): <n>

3. Blocking gate
   [ ] Tenant Isolation Pack: PASS (18/18)
   [ ] RBAC/ABAC Pack: PASS
   [ ] Methodology Lock/Version Pack: PASS
   [ ] Scoring Reproducibility (TC-SCORE-014): PASS
   [ ] Audit completeness + append-only + hash chain: PASS
   [ ] Salary Permission Foundation: PASS
   [ ] Localization smoke (4 langs): PASS
   [ ] ArchUnit rules: PASS
   [ ] OWASP ZAP baseline: no High/Critical
   [ ] Snyk + Trivy: no Critical CVE
   [ ] No Critical/High defects open

4. Defect summary
   Critical: <n>  (must be 0)
   High:     <n>  (must be 0)
   Medium:   <n>
   Low:      <n>

5. Cross-agent sign-offs
   Product Owner (accept/reject): _______________
   Security Engineer (ship/block): _______________
   QA Engineer (GO/NO-GO):         _______________

6. Decision
   [ ] GO
   [ ] NO-GO
   Justification: <text>

7. Known limitations / accepted risks
   <text>

8. Post-release verification plan
   [ ] Tenant isolation smoke on production canary
   [ ] Audit hash chain spot check
   [ ] Localization quick sweep
=========================================
```

---

## 30. Cross-Agent Test Execution Dependencies

| Dependency | Provider | Consumer | When |
|------------|----------|----------|------|
| Acceptance criteria + permission matrix per story | hr-product-owner | QA | Before sprint start |
| Threat model + JWT spec + RLS policies | security-engineer | QA | Before Sprint 1 |
| API endpoint spec (OpenAPI) | backend-engineer | QA | Story-by-story |
| Permission coverage manifest (`permission-coverage.json`) | frontend-engineer | QA | Sprint end |
| Test data seeders | backend-engineer + QA | QA | Sprint 1 |
| OWASP ZAP authenticated session script | security-engineer | QA | Sprint 4 |
| QA test results + defect list | QA | hr-product-owner, security-engineer | Sprint end |
| Release GO/NO-GO recommendation | QA | hr-product-owner, security-engineer | Sprint 4 close |

---

**End of MVP 1 QA Master Test Plan v1.0**
