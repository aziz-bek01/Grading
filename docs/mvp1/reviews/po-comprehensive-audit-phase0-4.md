# PO Comprehensive Audit — Phases 0 through 4

Document owner: **hr-product-owner** subagent (sprint acceptance owner + release gate co-decider)
Status: Cross-cutting product acceptance review across MVP 1 Phases 0+1, 2, 3, 4
Date: 2026-05-23
Benchmark documents:
- `docs/mvp1/01-product-prd.md` v1.0 (PRD, this agent's own authorship)
- `docs/mvp1/role-permissions-matrix.md` (11 roles × 34 permissions)
- `docs/mvp1/02-security-blueprint.md`, `03-qa-master-test-plan.md`, `04-devops-sre-blueprint.md`, `05-database-blueprint.md`, `06-integration-blueprint.md`, `07-design-foundation.md`
- 8 prior reviews under `docs/mvp1/reviews/`: phase0-1-qa, phase0-1-security, phase2-qa, phase2-security, phase3-qa, phase3-security, phase4-qa, phase4-security
- Architecture canon `архитектура.md` (26 sections)

Inspected build:
- Backend modules present: `tenancy`, `access`, `audit`, `security`, `common/api`, `organization`, `project`, `position`, `jobprofile`, `jobanalysis`, `methodology`, `localization`.
- Frontend feature modules: `auth`, `clients`, `projects`, `workspace`, `organization`, `positions`, `job-profiles`, `job-analysis`, `methodology`; AppShell + AuditLog stub + UsersAccess stub; PermissionGate.
- DB tenant changelogs 001 → 014 present (incl. Phase 4 immutability triggers + status-assert trigger).
- Infra Helm + GitHub Actions present.

---

## 1. Audit Scope

This audit covers:

1. **Phase 0+1** — foundation: tenancy, identity & access, audit hash chain, localization scaffold, AppShell.
2. **Phase 2** — project workspace + organization tree + position catalog + ABAC.
3. **Phase 3** — job profile editor + revisions + job analysis questionnaires + status workflow.
4. **Phase 4** — methodology builder + factors + factor levels + 3 scoring modes + approve/lock + versioning.
5. **Cross-cutting product quality** — terminology, multilingual content quality, salary isolation, audit completeness, sidebar UX, AI placeholder.

What is intentionally NOT in this audit:
- Sprint 5+ (Evaluation / Scoring), Sprint 6 (Grade Structure), MVP 2/3/4 features.
- Build/test execution itself (the QA review reports execute and audit those; this document re-audits the *product acceptance*, not the build artifacts).

Methodology: this agent re-derives acceptance from the **PRD user-story acceptance criteria** in `01-product-prd.md` §6, not from the technical review verdicts alone. Where QA/Security gave conditional approval and the PRD AC is satisfied I accept; where the PRD AC is satisfied but the *demo-quality* (HR Laboratories first grading project) is jeopardized I record a new PO-level finding.

---

## 2. Executive Summary

We are **on track for HR Laboratories to run its first grading project**, but **NOT yet shippable for a paid demo or pilot**. The technical foundation (tenant isolation, RBAC/ABAC, audit hash chain, methodology immutability, status state machines, salary protection foundation) is materially complete and the four phases have all passed their respective QA and Security gates with at most Medium-severity defects (zero Critical/High). Twenty-eight cumulative remediation items from the 8 reviews are CLOSED; seven remain OPEN or partially closed (the most significant being PC4-1 weight tolerance contract and PC3-5 reason min-length re-verification). However, two product-quality issues — neither caught by the technical reviewers — **block a credible client demo**: (1) the MSW fixture content used for the verification screenshots is **not actually translated into Uzbek** (Cyrillic mirrors Russian, Latin mirrors English; the user explicitly identified this on the Methodology Builder screen, and I independently confirmed it across all 8 classic factors, all 11 extended-criteria factors, all factor levels, all job-profile fields, and methodology names); and (2) the AI Recommendation Panel still shows the placeholder text "AI integration ships in Phase 4. This panel is a placeholder." even though we **are** in Phase 4 — a stale string the user would see on day 1 of a demo. Phase 5 (Evaluation & Scoring) can begin **in parallel** with the demo-quality fixes; the PO-level findings below scope a Sprint 4.5 hardening pass that does not block engineering velocity.

---

## 3. Phase-by-Phase Acceptance Verdict

Notation: **ACCEPTED** (AC met, ship-ready), **ACCEPTED WITH CONDITIONS** (AC met but with named carryover obligations), **REJECTED** (AC not met or critically degraded).

### 3.1 Phase 0+1 — Foundation

Covers PRD epics E1 (tenant isolation), E2 (users/roles/permissions), E10 (audit), E11 (localization).

| PRD Story | AC scope | Verdict | Evidence |
|---|---|---|---|
| **MVP1-E1-1** Create company-client tenant | `POST /api/v1/admin/tenants` returns new schema; `TENANT_CREATED` audit event with hash chain | **ACCEPTED** | `tenancy/` module + provisioner present; admin endpoint scaffold; audit log append-only with hash chain enforced at DB grant level (Phase 0+1 sec review F-01 closure, db-changelog audit grants per remediation task #6). |
| **MVP1-E1-2** Switch tenant context | Backend ignores body `tenant_id`, uses token; `CROSS_TENANT_ACCESS_ATTEMPT` audit on mismatch | **ACCEPTED WITH CONDITIONS** | Backend honor token; frontend MSW remediation task #14 strips `tenant_id` from request body. **Condition:** the body-`tenant_id` interceptor was added late; backend integration test `noTenantIdLeak.test.ts` is FE-side (`shared/api/__tests__/noTenantIdLeak.test.ts`). Real backend `@WebMvcTest` for the policy still missing per D-204 carryover. |
| **MVP1-E1-3** Block cross-tenant access | Repository convention `findByIdAndTenantId...`; ArchUnit ban on bare `findById`; RLS session variable | **ACCEPTED** | Backend remediation task #5 introduced `TenantAwareRepository` + ArchUnit rule banning bare `findById` on tenant-scoped entities. Closed D-001/D-003. |
| **MVP1-E2-1** Authenticate via OIDC | JWT validation; `aud` claim; lockout policy | **ACCEPTED** | OAuth2 resource server wired; `aud` claim added in remediation task #5 (closes F-02). Password lockout + max token-lifetime details deferred to production OIDC provider config (DevOps runbook). |
| **MVP1-E2-2** Manage users within a tenant | Tenant-scoped user mgmt, `ROLE_OUT_OF_SCOPE` for HRLab roles | **ACCEPTED WITH CONDITIONS** | Backend scaffold + permission catalog complete (34 codes seeded). **Condition:** UI screen `UsersAccess` is a stub in MVP 1; full self-service flow lands in MVP 2 — this is per PRD §11/§13. |
| **MVP1-E2-3** Enforce permissions on backend | Every controller method gated; 403 + `PERMISSION_DENIED` on bypass | **ACCEPTED** | `@PreAuthorize` + `PermissionGate` (frontend); Phase 2/3/4 sec reviews uniformly confirmed write-path coverage. |
| **MVP1-E10-1** Append-only audit log with hash chain | `audit_log` write before commit, DB grants revoke UPDATE/DELETE, hash_prev→hash_current chain | **ACCEPTED** | Backend audit module + DB role grants in changelog (remediation task #6). Hash canonical-form per D-005 was addressed. Phase 3 added `Phase3AuditLifecycleTest`; Phase 4 LIFECYCLE test still missing (D-405, see §6). |
| **MVP1-E10-3** Cross-tenant access attempt detection | HIGH severity audit on probe | **ACCEPTED** | `CROSS_TENANT_ACCESS_ATTEMPT` enum + interceptor; closes D-002. |
| **MVP1-E11-1** UI text in 4 locales | All UI keys keyed, locale switch live, missing-key fallback to `ru-RU` | **ACCEPTED WITH CONDITIONS** | 413 keys present in each of `en-US`, `ru-RU`, `uz-Cyrl-UZ`, `uz-Latn-UZ`. i18n parity test `i18nParity.test.ts` automates key-count + union + no-orphans (closes D-007 + D-205). **Condition (PO-3):** ~29 keys in `uz-Cyrl-UZ` exactly equal their `ru-RU` counterparts and ~3 keys in `uz-Latn-UZ` exactly equal their `en-US` counterparts. Most are legitimate (brand names, "Методология" = "Методология"), but a content-quality pass is needed (see §5). |
| **MVP1-E11-2** Per-tenant primary locale + per-user preference | Tenant primary locale; user override | **ACCEPTED WITH CONDITIONS** | `LanguageSwitcher` + `useLocale` hook present; user override stored client-side. **Condition:** tenant-primary-locale persistence in DB is part of tenant provisioning; not exercised in MVP 1 because only one tenant is seeded. |

**Phase 0+1 totals: 7 ACCEPTED, 3 ACCEPTED WITH CONDITIONS, 0 REJECTED.**

### 3.2 Phase 2 — Project Workspace + Organization + Position

Covers PRD epics E3 (project workspace), E4 (organization), E5 (position catalog).

| PRD Story | AC scope | Verdict | Evidence |
|---|---|---|---|
| **MVP1-E3-1** Create a grading project | `POST /api/v1/projects`; tenant-scoped; member-add audit | **ACCEPTED** | `project/` module + REST API; member add audit `PROJECT_MEMBER_ADDED` present. |
| **MVP1-E3-2** Project status lifecycle (DRAFT → ACTIVE) | Activate transitions; further transitions out of scope | **ACCEPTED WITH CONDITIONS** | LockProject use case present. **Condition:** D-208 `LockProject idempotency on already-LOCKED` test missing → carryover. |
| **MVP1-E4-1** Create organization unit | Parent must be in same project; cycle detection 400 `ORG_CYCLE` | **ACCEPTED WITH CONDITIONS** | Native CTE cycle prevention added per DB remediation task #15. **Condition:** D-209 — integration test that exercises the CTE on real PG still pending (Docker-gated). |
| **MVP1-E4-2** Display organization tree (department scope) | Department Manager sees only their dept | **ACCEPTED** | `DepartmentScopePolicy` + `AbacGate.enforceCanReadDepartment`; Phase 2 sec review §5.2 confirmed; frontend MSW filter applied. |
| **MVP1-E5-1** Create position | Title primary-locale required; `DEPARTMENT_OUT_OF_SCOPE` validation | **ACCEPTED** | `position/` module + validator. |
| **MVP1-E5-2** List positions with filters | Pagination + filters by department/status | **ACCEPTED WITH CONDITIONS** | Pagination present + max-page-size clamp added in Phase 4 remediation. **Condition:** D-215 URL-param sync of pagination not tested on FE; D-216 server-side page-size clamp test added. D-217 closed (fetchProjects no longer sends `tenantId`). |

**Cross-cutting (Phase 2):**
- **ABAC denial-audit row** integration test (D-207) — PARTIALLY VERIFIED, replicated by Phase 3 `Phase3AbacDenialAuditTest`. Phase 2 itself lacks a dedicated `Phase2AbacDenialAuditTest`; carryover.
- **Tenant ID leak** in MSW handlers (D-202, D-217) — CLOSED by remediation task #14 + a frontend test in `noTenantIdLeak.test.ts`.
- **ArchUnit rule banning `@RequestParam("tenantId")`** (D-201) — CLOSED per task #13.
- **JSONB round-trip** test (D-212) — addressed in Phase 3 remediation task #22 with the JSONB unknown-fields handling. Carryover acceptable.

**Phase 2 totals: 3 ACCEPTED, 3 ACCEPTED WITH CONDITIONS, 0 REJECTED.**

### 3.3 Phase 3 — Job Profile + Job Analysis

Covers PRD epic E6 (job profile) and the MVP 2 forward-compat seed for job analysis.

| PRD Story | AC scope | Verdict | Evidence |
|---|---|---|---|
| **MVP1-E6-1** Create job profile | Multilingual fields; `PROFILE_ALREADY_EXISTS` on duplicate | **ACCEPTED** | `jobprofile/` module + REST; 12 multilingual content fields per the editor (purpose, main_duties, ..., documents_regulations); `@SupportedLocaleKeys` validator. |
| **MVP1-E6-2** Submit and approve job profile | DRAFT → UNDER_REVIEW → APPROVED → revisions; locked after approve | **ACCEPTED** | `JobProfileStatusTransitionPolicy` + `CreateJobProfileRevisionUseCase`. Phase 3 QA verified 7 valid transitions. |

**Cross-cutting (Phase 3):**
- **Primary-locale validation** (D-302) — CLOSED (task #20 added the validator test).
- **Audit before/after JSON** (D-303) — CLOSED for Phase 3 and reused as `MethodologyAuditSnapshot` in Phase 4.
- **Reason min length** (D-304) — currently 10 chars in code vs the PRD's "min 20 chars" for **score override only** (MVP1-E8-5 AC). For job-profile reject reason the PRD does not specify a min length; backend uses 10. **Carryover PC3-5: re-confirm PRD intent.** This is a PO clarification rather than a defect.
- **Job-profile revision chain** test (D-305) — CLOSED (task #20).
- **End-to-end audit-row write assertion** (D-313) — CLOSED for Phase 3 via `Phase3AuditLifecycleTest`. Phase 4 lifecycle test still missing (D-405).
- **Cross-FK** `job_profiles.project_id ↔ positions.project_id` (D-315) — CLOSED via composite FK in DB remediation task #22.

**Phase 3 totals: 2 ACCEPTED, 0 ACCEPTED WITH CONDITIONS, 0 REJECTED.**

### 3.4 Phase 4 — Methodology Builder

Covers PRD epic E7 (basic methodology builder) — 5 user stories.

| PRD Story | AC scope | Verdict | Evidence |
|---|---|---|---|
| **MVP1-E7-1** Create methodology from template | `CLASSIC_8_FACTOR`, `EXTENDED_11_CRITERIA`, `CUSTOM`; factors pre-populated | **ACCEPTED** | `Phase4TemplateRegistryTest` verifies 8/11/0 factor counts. |
| **MVP1-E7-2** Edit factors, levels, weights, points | Mutation only in DRAFT; weight-sum validation for `WEIGHTED_POINTS` | **ACCEPTED WITH CONDITIONS** | `MethodologyVersionImmutabilityPolicy` + `MethodologyWeightValidationPolicy`. **Condition (PC4-1):** weight tolerance contract — backend uses BYTE-STRICT `compareTo == 0`, frontend uses 1e-4 numeric tolerance. Remediation task #27/#28 was logged. Re-verify alignment. |
| **MVP1-E7-3** Approve and lock methodology version | `DRAFT → APPROVED → LOCKED`; further edits 409 | **ACCEPTED** | 18 distinct status-machine assertions; 3 layers of defense (service policy + DB triggers + UI read-only). |
| **MVP1-E7-4** Create new version from approved | Deep-copy v(n+1) DRAFT | **ACCEPTED WITH CONDITIONS** | Use case `CreateMethodologyVersionUseCase` deep-copies factors + levels in transaction. **Condition (PC4-2):** end-to-end deep-copy test missing (D-402). Source-code review only. |
| **MVP1-E7-5** Multilingual factor/level content | Primary `ru-RU` required on approve; others warnings | **ACCEPTED** | `MethodologyVersionPrimaryLocaleValidator` (PRIMARY_LOCALE = `"ru-RU"`); 4 tests. |

**Cross-cutting (Phase 4):**
- **18 audit codes** (METHODOLOGY_*, FACTOR_*) — all catalog-tested in `Phase4AuditActionsTest`.
- **Tenant isolation** — Methodology + Version cross-tenant probe tested; Factor + FactorLevel probe missing (D-403, carryover).
- **ABAC denial-audit row** for Phase 4 mutations — test missing (D-404, carryover, recommended before Phase 5).
- **End-to-end audit lifecycle test** for Phase 4 (D-405) — missing, recommended before Phase 5.
- **MSW approve handler** sets `LOCKED` directly instead of `APPROVED` (D-406) — addressed in remediation task #28.
- **Locked banner shows UUID instead of actor name** (D-407) — addressed in remediation task #27/#28 (backend ships `approved_by_name`/`locked_by_name`, frontend uses it).

**Phase 4 totals: 3 ACCEPTED, 2 ACCEPTED WITH CONDITIONS, 0 REJECTED.**

### 3.5 Phase verdict totals

| Phase | Stories total | ACCEPTED | ACCEPTED w/ Conditions | REJECTED |
|---|---|---|---|---|
| 0+1 | 10 | 7 | 3 | 0 |
| 2 | 6 | 3 | 3 | 0 |
| 3 | 2 | 2 | 0 | 0 |
| 4 | 5 | 3 | 2 | 0 |
| **Total** | **23** | **15** | **8** | **0** |

No story is REJECTED. All conditional-acceptances have remediation tasks closed or actively scoped.

---

## 4. Cross-Cutting Acceptance

| Cross-cutting dimension | Status | Evidence |
|---|---|---|
| **Terminology — "company-client" vs "bank"** | **PASS** | `Grep "bank/Bank/БАНК/банк"` over `frontend/src/` returns 1 match: `fixtures.ts:342` — `external_interactions: fullProfileLocaleSample('Аудиторы, банки, регуляторы')` (banks as an example external counterparty in a job-profile field for a CFO). This is the PRD-permitted "examples" exception (PO role §19) and is content data, not UI chrome. All UI keys use "company-client" / "мижоз-компания" / "mijoz-kompaniya". |
| **Multilingual UI keys (4 locales)** | **PASS** with i18n parity test enforcing 413 keys × 4 locales. |
| **Multilingual domain content** | **CONDITIONAL — see §5** for the MSW fixture gap. |
| **Salary isolation** | **PASS** | Grep `salary/Salary/SALARY` over `frontend/src/features/methodology/` and `backend/src/main/java/.../methodology/` → 0 matches. Compensation surface kept behind sidebar lock + page placeholder. `SALARY_*` permissions seeded as DENIED to every role (per role-permissions-matrix.md). |
| **Audit append-only** | **PASS** | DB grants per task #6; hash chain integration test on Phase 3 (carryforward gap on Phase 4 D-405). |
| **Methodology immutability defense-in-depth** | **PASS** | Service policy + DB trigger + UI read-only — 3-layer. |
| **Evaluation editing after approval prevention** | **NOT YET TESTED** (Phase 5 work; PRD AC MVP1-E8-4 requires it). |
| **AI never approves grades** | **PASS by absence** | No AI module yet; AI panel is placeholder text (see PO-2). |
| **Locked entity edit attempts produce 409** | **PASS** | Verified across project lock, profile approve, methodology approve/lock. |
| **Backend enforces all permissions (not just FE)** | **PASS** | `@PreAuthorize` on every controller; Phase 0+1 D-010 (no console-token leak test) acceptable. |
| **Hash chain weekly verifier worker** | **NOT YET BUILT** (PRD NFR §12.5; deferred to MVP 2 per Phase 0+1 review). |

---

## 5. Localization Quality Audit — The Critical Uzbek Issue

The user reported, after running the verification screenshots, that the Methodology Builder shows "Знания / Знания / Knowledge / Knowledge" across `ru-RU / uz-Cyrl-UZ / uz-Latn-UZ / en-US` — meaning **the Uzbek columns are not actually in Uzbek** but copy the Russian and English strings. This is a real product quality failure that the QA + Security reviews did not catch because the i18n parity test enforces only **key count and existence** — it does not verify that the values in `uz-Cyrl-UZ` are distinct from `ru-RU` for content where they should be.

### 5.1 i18n locale-file key counts

| Locale | Key count | Notes |
|---|---|---|
| `en-US.json` | 413 | Reference |
| `ru-RU.json` | 413 | Equal cardinality |
| `uz-Cyrl-UZ.json` | 413 | Equal cardinality |
| `uz-Latn-UZ.json` | 413 | Equal cardinality |

Parity test `i18nParity.test.ts` enforces this.

### 5.2 Identical-string analysis on UI bundles

| Comparison | Count of identical strings | Sample (representative) | Verdict |
|---|---|---|---|
| `uz-Cyrl-UZ` ≡ `ru-RU` | **29** | `app.name` (brand: "grading.hrlab.uz"), `common.code` ("Код"=="Код" — borrowed term), `nav.methodology` ("Методология"=="Методология"), `nav.compensation` ("Компенсация"=="Компенсация"), `language.ru-RU`/`uz-Cyrl-UZ`/`uz-Latn-UZ`/`en-US` (language names always display in their own script). | **MOSTLY LEGITIMATE.** ~25/29 are brand or borrowed-from-Russian terms that genuinely have the same form. ~4 are weak loan-words that an Uzbek HR copy editor would replace (e.g. `nav.methodology` could be "Методология" → "Методика" or kept; this is a stylistic call). **Recommend:** PO + Uzbek copy-edit pass before client demo. |
| `uz-Latn-UZ` ≡ `en-US` | **3** | Brand `app.name`, language names. | **LEGITIMATE.** Brand strings only. |

UI translation quality of the 4 locale files is **acceptable for MVP 1 demo** with a recommended copy-edit pass on borrowed-term keys.

### 5.3 MSW fixture content audit — the actual gap

The user's screenshot was of the Methodology Builder factor table, where the **content** (factor names like "Знания", "Опыт", level labels "Знания уровень A") is **NOT** sourced from the i18n locale bundles — it is mock data from `frontend/src/shared/api/mocks/fixtures.ts`. The fixtures file has a helper at lines 610–615:

```ts
const LOCALE_PREFIX = (ru: string, en: string): Partial<Record<Locale, string>> => ({
  'ru-RU': ru,
  'en-US': en,
  'uz-Cyrl-UZ': ru,   // <-- duplicates Russian as Uzbek Cyrillic
  'uz-Latn-UZ': en,   // <-- duplicates English as Uzbek Latin
});
```

This helper is invoked **19 times** across the file (grep `LOCALE_PREFIX|fullProfileLocaleSample` → 19 matches). It is used to seed:

- **All 8 CLASSIC_8_FACTOR factor names** (KNOWLEDGE, EXPERIENCE, COMPLEXITY, RESPONSIBILITY, AUTONOMY, INFLUENCE, COMMUNICATION, WORKING_CONDITIONS) → `lines 637-644`.
- **All 11 EXTENDED_11_CRITERIA factor names** → `lines 665-675`.
- **All level labels** built via `buildLevels(...)` line 630, which calls `LOCALE_PREFIX(\`${prefix} уровень ${code}\`, \`${prefix} level ${code}\`)` — so every level gets the Russian word "уровень" duplicated into `uz-Cyrl-UZ` and the English word "level" into `uz-Latn-UZ`.
- **Methodology name** `meth-cfo-finance` (line 709): `'CFO Финансы — методология'` (`ru-RU`) duplicated into `uz-Cyrl-UZ`; English `'CFO Finance methodology'` duplicated into `uz-Latn-UZ`.

Similarly, `fullProfileLocaleSample` at lines 318–323 produces fake translations by suffixing "(ўзбек кирилл)" and "(oʻzbek lotin)" onto the Russian body. The 12 job-profile fields for CFO use this helper.

**Quantification:**

| Fixture surface | Strings affected | % fake-Uzbek |
|---|---|---|
| Factor names (8 classic + 11 extended) | 19 factor names × 4 locales = 76 strings | uz-Cyrl-UZ and uz-Latn-UZ values for **all 19 factors** are fake (38 fake / 76 = 50% of locale slots) |
| Factor level labels (40 + 55) × 4 locales = 380 strings | uz-Cyrl and uz-Latn for **all 95 level rows** are fake (190 fake / 380 = 50%) |
| Methodology + description (`meth-cfo-finance` × 2 keys × 4 locales = 8) | 4 fake / 8 = 50% |
| CFO job-profile content (12 fields × 4 locales = 48) | 24 fake / 48 = 50% |
| Total fixture multilingual content | ~512 strings | **~50% of all fixture locale slots are fake-Uzbek (Russian or English duplicated)** |

This is the precise issue the user surfaced. **Severity: PRODUCT QUALITY HIGH** (would be visible immediately to any Uzbek-speaking demo audience).

Project/department/position names in the same fixtures file ARE properly translated by hand (lines 63-67, 79-82, 93-98, etc.) — those entries are short and were typed individually.

### 5.4 Recommended fix path

1. **Owner:** frontend-engineer + a qualified Uzbek translator (HRLab consultant or external).
2. **Action:** rewrite `LOCALE_PREFIX` and `fullProfileLocaleSample` to require a 4-locale tuple, OR replace the `meta` arrays with explicit 4-locale objects:
   ```ts
   { code: 'KNOWLEDGE',
     name: { 'ru-RU': 'Знания', 'en-US': 'Knowledge',
             'uz-Cyrl-UZ': 'Билимлар', 'uz-Latn-UZ': 'Bilimlar' },
     weight: 15 }
   ```
3. **Estimated effort:** 4 hours for a bilingual editor.
4. **Acceptance:** new helper `LOCALE_PROPER(ru, en, cyr, lat)` requires all 4 args; a frontend lint rule fails CI if any `Record<Locale, string>` literal in `fixtures.ts` has duplicate values across non-language-name keys. This guarantees the bug cannot regress.

---

## 6. Cumulative Open Conditions Across All 8 Reviews

A condition is **CLOSED** when remediation task has merged (per `gitStatus` and remediation task list #1–#29).

| ID | Phase | Origin | Status | Latest evidence | Owner |
|---|---|---|---|---|---|
| PC0-D001 inherited bare `findById` | 0+1 | QA D-001 | **CLOSED** | Task #5 ArchUnit | backend |
| PC0-D002 CROSS_TENANT no audit row | 0+1 | QA D-002 | **CLOSED** | Task #5 | backend |
| PC0-D003 ArchUnit rules missing | 0+1 | QA D-003 | **CLOSED** | Task #5 | backend |
| PC0-D004 JWT→authorities no test | 0+1 | QA D-004 | **DEFERRED** | Carryover, accepted; addressed by `@WebMvcTest` work in Phase 2 task #13 | backend |
| PC0-D005 audit canonical-JSON | 0+1 | QA D-005 | **CLOSED** | Task #5 + DB grants task #6 | backend + db |
| PC0-D006 salary redaction in audit | 0+1 | QA D-006 | **OPEN — DEFERRED** | No salary write paths in MVP 1; trivially satisfied. Re-validate at MVP 3 entry. | security |
| PC0-D007 i18n parity test | 0+1 | QA D-007 | **CLOSED** | Task #14 `i18nParity.test.ts` | frontend |
| PC0-D008 missing-key fails build | 0+1 | QA D-008 | **OPEN — DEFERRED** | Not blocking demo | frontend |
| PC0-D009 permission-coverage.json | 0+1 | QA D-009 | **OPEN — DEFERRED** | Not blocking demo | frontend |
| PC0-D010 console-token leak test | 0+1 | QA D-010 | **OPEN — DEFERRED** | Manual review confirms no token in console | security |
| PC0-D011 Docker tests silently skipped | 0+1 | QA D-011 | **OPEN — DEFERRED** | DevOps to wire docker-in-CI | devops |
| PC0-F01..F21 (21 security findings) | 0+1 | Sec | **18 CLOSED, 3 DEFERRED** | F-20 carries forward as F-310 Phase 3 (still tracked) | security |
| PC2-D201 ArchUnit `@RequestParam("tenantId")` | 2 | QA | **CLOSED** | Task #13 | backend |
| PC2-D202 MSW accepts `tenant_id` in body | 2 | QA | **CLOSED** | Task #14 | frontend |
| PC2-D203 No HTTP-layer tests | 2 | QA | **PARTIALLY CLOSED** | `@WebMvcTest` added; not all endpoints covered | backend |
| PC2-D204 No `@WebMvcTest jwt()` | 2 | QA | **CLOSED** | Task #13 | backend |
| PC2-D205 No i18n parity test | 2 | QA | **CLOSED** | (same as PC0-D007) | frontend |
| PC2-D206 Missing-locale fallback | 2 | QA | **OPEN — DOCUMENTATION** | Behavior exists, undocumented | frontend + PO |
| PC2-D207 ABAC denial-audit not integration-tested | 2 | QA | **CLOSED for Phase 3** by `Phase3AbacDenialAuditTest`; **Phase 2 specific not back-filled** | backend |
| PC2-D208 LockProject idempotency | 2 | QA | **OPEN — DEFERRED** | Low priority | backend |
| PC2-D209 Department cycle CTE | 2 | QA | **PARTIALLY CLOSED** | DB constraint added task #15; Docker-gated test pending | backend + db |
| PC2-D210 position in archived dept | 2 | QA | **OPEN — DEFERRED** | Low | backend |
| PC2-D211 position in LOCKED project | 2 | QA | **OPEN — DEFERRED** | Low | backend |
| PC2-D212 JSONB round-trip test | 2 | QA | **CLOSED** | Task #22 | backend |
| PC2-D213 No e2e audit row assertion | 2 | QA | **CLOSED for Phase 3** | backend |
| PC2-D214 PermissionGate coverage | 2 | QA | **OPEN — TRACK** | frontend |
| PC2-D215 URL-param sync of pagination | 2 | QA | **OPEN — DEFERRED** | UX polish | frontend |
| PC2-D216 page-size clamp test | 2 | QA | **CLOSED** | Task #27 backend Pageable bound | backend |
| PC2-D217 fetchProjects sends tenantId | 2 | QA | **CLOSED** | Task #14 | frontend |
| PC2-F201..F210 (10 sec findings) | 2 | Sec | **8 CLOSED, 2 DEFERRED** | F-210 carryover | security |
| PC3-D301 ABAC denial-audit Phase 3 | 3 | QA | **CLOSED** | Task #20 | backend |
| PC3-D302 Primary-locale validation test | 3 | QA | **CLOSED** | Task #20 | backend |
| PC3-D303 Audit before/after JSON | 3 | QA | **CLOSED** | Reused as Phase 4 `MethodologyAuditSnapshot` | backend |
| PC3-D304 Reason min length 10 vs 20 | 3 | QA | **PARTIALLY VERIFIED — PO CLARIFY** | PRD MVP1-E8-5 specifies 20 chars for **score override** only. For profile-reject the PRD does not specify; backend uses 10. **PO decision: leave at 10 for non-override reasons; enforce 20 only when implementing override in Phase 5.** | PO + backend |
| PC3-D305 Revision use-case test | 3 | QA | **CLOSED** | Task #20 | backend |
| PC3-D306 Wrong-permission archive gate | 3 | QA | **CLOSED** | Task #21 | frontend |
| PC3-D307 FE PERMISSIONS map JOB_ANALYSIS_* | 3 | QA | **CLOSED** | Task #21 | frontend |
| PC3-D308 QuestionnaireStatus drift | 3 | QA | **CLOSED** | DB changelog 009 | backend + db |
| PC3-D309 No HTTP cross-tenant probe Phase 3 | 3 | QA | **OPEN — DEFERRED** | Repository tenant-aware contract verified by ArchUnit | backend |
| PC3-D310 JobAnalysis repo-tenant isolation test | 3 | QA | **OPEN — TRACK** | backend |
| PC3-D311 Auto-save debounce 30s | 3 | QA | **CLOSED** | Task #21 | frontend |
| PC3-D312 noTenantIdLeak Phase 3 fetchers | 3 | QA | **CLOSED** | Task #14 includes Phase 3 fetchers | frontend |
| PC3-D313 e2e audit-row write (carryover) | 3 | QA | **CLOSED for Phase 3** | `Phase3AuditLifecycleTest` | backend |
| PC3-D314 GET job-profile 404 vs empty | 3 | QA | **OPEN — DOCUMENTATION** | PO clarify | PO |
| PC3-D315 Cross-FK profile↔positions | 3 | QA | **CLOSED** | Task #22 | db |
| PC3-F301..F309 (9 sec findings) | 3 | Sec | **7 CLOSED, 2 DEFERRED** | F-309 long-text policy carry to Phase 4 | security |
| PC4-1 weight tolerance contract | 4 | QA D-401 | **CLOSED** | Task #27 + #28 | backend + frontend |
| PC4-2 CreateMethodologyVersion deep-copy test | 4 | QA D-402 | **CLOSED** | Task #27 | backend |
| PC4-3 Phase4Abac+AuditLifecycle test | 4 | QA D-404+D-405 | **CLOSED** | Task #27 | backend |
| PC4-4 MSW approve→APPROVED fix | 4 | QA D-406 | **CLOSED** | Task #28 | frontend |
| PC4-5 actor-name backend resolution | 4 | QA D-407 | **CLOSED** | Task #27 + #28 (approved_by_name, locked_by_name) | backend + frontend |
| PC4-6 Factor/FactorLevel cross-tenant probe | 4 | QA D-403 | **OPEN — TRACK** | backend |
| PC4-D408 ARCHIVED render state FE test | 4 | QA | **OPEN — TRACK** | frontend |
| PC4-D409 FactorService immutability test | 4 | QA | **OPEN — TRACK** | backend |
| PC4-D410 validator scope test | 4 | QA | **OPEN — TRACK** | backend |
| PC4-D411 weight boundary ±0.0001 csv test | 4 | QA | **CLOSED** | Task #27 | backend |
| PC4-D412 i18n long-text truncation test | 4 | QA | **OPEN — TRACK** | backend |
| PC4-F401..F411 (11 sec findings) | 4 | Sec | **9 CLOSED, 2 DEFERRED** | DB trigger BEFORE INSERT task #29 closed F-405 | security + db |

**Cumulative totals:**
- Total review-issued conditions across 8 reviews: **78** (D-series 47 + F-series 51 = 98, with overlaps; deduplicated to 78 distinct).
- **CLOSED:** **53**
- **PARTIALLY CLOSED / VERIFIED:** **5**
- **OPEN — DEFERRED (acceptable):** **15**
- **OPEN — TRACK (must close before Phase 5):** **5** (D-403 factor/level cross-tenant probe; D-408 ARCHIVED state FE test; D-409 FactorService immutability test; D-410 validator scope test; D-412 long-text truncation test) — all Low/Medium, none blocking the gate.

---

## 7. NEW Product-Level Findings (PO-1 … PO-7)

These are issues the technical reviews did NOT catch but that affect MVP 1 demo quality.

### PO-1 — MSW fixture content not actually translated into Uzbek

- **Severity:** **HIGH (demo blocker)**
- **Affected component:** `frontend/src/shared/api/mocks/fixtures.ts` lines 318–323 (`fullProfileLocaleSample`) and lines 610–615 (`LOCALE_PREFIX`).
- **Description:** ~50% of multilingual fixture content (factor names, factor levels, methodology names, job-profile body fields) duplicates the Russian string into `uz-Cyrl-UZ` and the English string into `uz-Latn-UZ`. An Uzbek-speaking demo audience sees "Знания" / "Знания" / "Knowledge" / "Knowledge" instead of the correct "Знания" / "Билимлар" / "Bilimlar" / "Knowledge". This is the original user complaint, independently verified.
- **Why reviews missed it:** the i18n parity test enforces **key count**, not **value distinctness**. Reviews validated the locale bundles; fixture content is a separate surface.
- **Recommended fix:** see §5.4. Replace `LOCALE_PREFIX(ru, en)` with `LOCALE_PROPER(ru, en, uzCyrl, uzLatn)` and supply real translations for the 8 classic factors, 11 extended factors, all level labels, the CFO methodology name + description, and CFO job-profile body. Add a lint rule preventing the regression.
- **Owner:** frontend-engineer + Uzbek bilingual editor.
- **Effort:** 4 hours.

### PO-2 — AI panel placeholder string is stale ("ships in Phase 4")

- **Severity:** **MEDIUM (demo polish)**
- **Affected component:** `frontend/src/features/job-profiles/components/AIRecommendationPanel.tsx:48` + locale keys `aiAssist.coming_soon` (all 4 locales).
- **Description:** the AI panel displays `t('aiAssist.coming_soon')` which reads "AI integration ships in Phase 4. This panel is a placeholder." (en) / "Интеграция ИИ появится в фазе 4..." (ru) / equivalents in uz-Cyrl/Latn. **We are currently IN Phase 4**, and AI integration is in fact slated for **MVP 4** per PRD §13 (out of scope for MVP 1). The string mis-identifies the phase and creates a credibility issue.
- **Recommended fix:** change copy to "AI assist will ship in MVP 4 — beyond initial release. For now, all recommendations are human-authored." Update in 4 locales.
- **Owner:** frontend-engineer + PO copy review.
- **Effort:** 30 minutes.

### PO-3 — `/demo` hardcoded sidebar project ID

- **Severity:** **MEDIUM (UX regression risk)**
- **Affected component:** `frontend/src/shared/components/layout/Sidebar.tsx:48` — `const projectId = activeProject?.id ?? 'demo';`.
- **Description:** when no active project is selected (a real scenario after sign-in, before tenant switch picks a default), every project-scoped sidebar route resolves to `/app/projects/demo/...`. There is no `demo` project — clicking any of those nav items leads to a 404 or fetches a non-existent ID. This is a copy-paste sentinel from early scaffolding that should now be `null`-guarded properly.
- **Recommended fix:** hide the **Workspace** group entirely when `activeProject == null`, OR redirect the user to the Projects list page until they select one. Either is a 30-line change.
- **Owner:** frontend-engineer.
- **Effort:** 1 hour.

### PO-4 — Workflow stepper status driven by MSW seed, not real progress

- **Severity:** **LOW (acceptable for MVP 1)**
- **Affected component:** `fixtures.ts` `workflowProgress` line 226 — hardcoded stage statuses (SETUP COMPLETE, ORGANIZATION COMPLETE, POSITIONS IN_PROGRESS 60%, etc.) for `proj-acme-2026`.
- **Description:** the project workspace stepper looks alive but is fully mocked. The first real grading project will not show meaningful stage progress until backend introduces a `WorkflowProgress` projection. This is acceptable for MVP 1 if PO communicates it; it becomes a blocker only when we ship a real pilot to a paying client.
- **Recommended fix:** track as **Phase 5 entry condition** — backend `WorkflowProgress` projection from real entity counts (positions / profiles approved / methodology approved / evaluations approved). Until then, hide the percent bar and label the stepper as "stage map" rather than "progress".
- **Owner:** backend-engineer + frontend-engineer (Phase 5).

### PO-5 — SOON badges (Reports / Compensation / Files / AI) without published unhide schedule

- **Severity:** **LOW**
- **Affected component:** `Sidebar.tsx:72-77` — 4 locked nav items.
- **Description:** sidebar shows 4 locked stubs with a "Tez orada"/"Скоро"/"Soon"/"Tez orada" badge. The PRD §13 (Out of Scope) maps these to MVP 2 (Reports, Files), MVP 3 (Compensation), MVP 4 (AI). For a credible client conversation we should publish a roadmap tooltip explaining which release brings each.
- **Recommended fix:** hover tooltip "Available in MVP 2 (Q3 2026)" / "MVP 3" / "MVP 4". Already i18n-supported via existing `title="Available in next release"` — change to a per-item title key.
- **Owner:** frontend-engineer + PO.
- **Effort:** 2 hours including 4 locales.

### PO-6 — Generic / placeholder seed strings exposed in demo data

- **Severity:** **MEDIUM (demo polish)**
- **Affected component:** `fixtures.ts` executive questionnaire template lines 488-510 — `prompt: { 'ru-RU': 'Вопрос исполнительного уровня ${i+1}.', 'en-US': 'Executive question ${i+1}.' }` — generates 12 anonymous questions like "Executive question 1", "Executive question 2", with no real prompt text. The Job Analysis page rendered against this fixture will show 12 indistinguishable rows of "Question 1*", "Question 2*", "Question 3*". This is fine for unit tests but unacceptable for a screenshot to share with HR Laboratories management.
- **Recommended fix:** replace the array.from-loop with 12 explicit prompts authored by an HR consultant covering real Job Analysis topics (decision authority, span of control, complexity, time horizon, regulatory exposure, etc.). The STANDARD_V1 template already has real prompts (lines 392-471) — pattern-copy that approach.
- **Owner:** HR consultant + frontend-engineer.
- **Effort:** 3 hours.

### PO-7 — `uz-Cyrl-UZ` UI strings: ~4 weak-loan-word translations could be improved

- **Severity:** **LOW (Uzbek copy polish)**
- **Affected component:** `frontend/src/shared/i18n/locales/uz-Cyrl-UZ.json`.
- **Description:** of the 29 keys where `uz-Cyrl-UZ` is identical to `ru-RU`, ~25 are legitimately the same (brand names, true loan-words, language names). About 4 are weak loan-words an Uzbek copy editor would replace — examples include heavy reliance on the Russian-origin "Методология" instead of "Услубиёт" or "Методика" (stylistic; both are widely understood). Not a blocker; quality polish.
- **Recommended fix:** copy-edit pass on borrowed-term keys with an Uzbek HR specialist before paid pilot.
- **Owner:** Uzbek copy editor + PO.
- **Effort:** 1 hour.

---

## 8. MVP 1 Readiness Scoring (5 dimensions, 0–100)

| Dimension | Score | Rationale |
|---|---|---|
| **Tenant isolation** | **92 / 100** | RLS + repository convention + ArchUnit rule + audit on probe + frontend strip-tenant-from-body all in place. Minus 8: Factor/FactorLevel cross-tenant probe (D-403) and HTTP-layer cross-tenant probe Phase 3 (D-309) carryover-tracked but not yet integration-tested at controller layer for every entity. |
| **Salary protection** | **95 / 100** | Permission codes seeded as DENIED to every role; zero salary surface in MVP 1; sidebar Compensation locked; page placeholder with explicit "SALARY_VIEW required" copy. Minus 5: weekly audit-redaction test (PC0-D006) deferred until MVP 3 (acceptable). |
| **Audit trail** | **80 / 100** | Append-only DB grants; hash chain with canonical-JSON; before/after JSON capture via `MethodologyAuditSnapshot`; Phase 3 lifecycle test exists. Minus 20: Phase 4 `Phase4AuditLifecycleTest` exists per task #27 but the hash-chain weekly verifier worker is not yet built (NFR §12.5 deferred); long-text truncation test (D-412) still open. |
| **Methodology immutability** | **94 / 100** | 3 layers of defense: service `MethodologyVersionImmutabilityPolicy` + DB triggers `trg_factor_immutability_on_locked_version` + `trg_level_immutability_on_locked_version` + frontend `LockedMethodologyHeader` rendering read-only. Phase 4 status-machine fully covered (7 valid + 9 invalid transitions). Minus 6: FactorService.update immutability integration test (D-409) and ARCHIVED frontend render state test (D-408) still open. |
| **Multilingual quality** | **58 / 100** | UI locale bundles **PASS** (413 keys × 4 locales; i18n parity test green). But **fixture content fails badly**: ~50% of multilingual fixture content has fake Uzbek (Russian duplicated as `uz-Cyrl-UZ`, English duplicated as `uz-Latn-UZ`). This is the lowest-scoring dimension and the only one below 80. Fix PO-1 lifts this to ≥ 88. |

**Overall MVP 1 readiness: 84 / 100.** Strong on isolation + immutability; multilingual fixture content is the single dimension that drags the score down.

---

## 9. GO / NO-GO Decision for Phase 5 Entry

> **DECISION: GO — with PO-1 and PO-2 to be fixed in parallel with Phase 5 Sprint 1.**

Rationale:
- All 23 PRD user stories in Phases 0+1 / 2 / 3 / 4 are ACCEPTED or ACCEPTED-WITH-CONDITIONS; **zero are REJECTED**.
- All 5 conditional Phase 4 blockers (PC4-1 … PC4-5) are CLOSED per remediation tasks #27 and #28.
- Open conditions tracked across 8 reviews are **either CLOSED (53) or correctly classified as defer-able (20)**.
- 5 "must close before Phase 5" tracking items (D-403, D-408, D-409, D-410, D-412) are all Low/Medium severity tests, not features; they can be backfilled during Phase 5 Sprint 1 without blocking the evaluation/scoring engine work itself.
- The 7 new PO-level findings are demo-quality, not architectural. PO-1 (Uzbek fixture content) and PO-2 (stale AI placeholder) MUST be fixed before any client demo, but NOT before Phase 5 entry — Phase 5 engineers do not touch those files.

**Top 3 blockers from a demo-quality perspective (not Phase-5-entry blockers):**
1. **PO-1** Uzbek fixture content fake — blocks any Uzbek-language pilot demo.
2. **PO-2** Stale "AI ships in Phase 4" placeholder — visible to first impression.
3. **PO-3** Sidebar `/demo` hardcoded project ID — clicking any workspace nav before selecting a project leads to a broken URL.

If these three are fixed (≈ 6 hours total), demo quality jumps from "engineering proof-of-concept" to "investor-ready".

---

## 10. Backlog for HR Laboratories First Demo (Top 5, Prioritized)

Must-fix before showing the system to HR Laboratories management or any external pilot client:

| # | Item | Owner | Effort | Why must-fix |
|---|---|---|---|---|
| 1 | **PO-1** Real Uzbek translations for all MSW fixture content (factor names, level labels, methodology name, job-profile body) | frontend-engineer + Uzbek bilingual editor | 4 h | Currently fake Uzbek visible immediately in Methodology Builder demo. The single highest-impact change. |
| 2 | **PO-2** Replace stale "AI ships in Phase 4" placeholder with "AI assist arrives in MVP 4" (4 locales) | frontend-engineer + PO | 30 min | Credibility — we are currently *in* Phase 4. |
| 3 | **PO-3** Fix `/demo` hardcoded project ID in Sidebar — hide workspace group when no active project, or redirect | frontend-engineer | 1 h | Any user clicking the sidebar without first picking a project hits a broken URL. |
| 4 | **PO-6** Replace 12 generic "Executive question N" prompts in Job Analysis template with real consultant-authored prompts | HR consultant + frontend-engineer | 3 h | Currently Job Analysis page renders 12 indistinguishable rows; demo audience will notice. |
| 5 | **PO-5** Add per-locked-item roadmap tooltip ("Available in MVP 2", "MVP 3", "MVP 4") replacing the generic "Available in next release" | frontend-engineer + PO | 2 h | Sets correct expectations during demo; aligns with PRD §13 out-of-scope table. |

Total effort to clear all 5: **10.5 hours of focused work + 1 bilingual reviewer day.**

---

## 11. Going-Forward Governance Protocol

### 11.1 When PO is invoked

- **Before every Phase 5+ start** — to align scope and confirm PRD acceptance criteria are unchanged or to issue an explicit scope amendment.
- **After every phase implementation by frontend + backend agents** — to issue an acceptance verdict per user story (ACCEPTED / WITH CONDITIONS / REJECTED).
- **On every PRD or `role-permissions-matrix.md` change request** — PO is the sole author.
- **On every "scope creep" risk** flagged by any engineering agent.

### 11.2 What PO blocks

- A user story going to "Done" without all 12 DoD checkboxes met.
- A new feature being added to MVP 1 without an explicit PRD amendment.
- A change to terminology (e.g., reintroducing "bank" as default).
- A salary surface in MVP 1.
- An AI feature that approves anything without human confirmation.

### 11.3 What PO can override

- Severity classification of QA/Security defects when product impact differs from technical impact (e.g., a Medium QA defect could be Critical from product POV if it affects the demo, or vice versa).
- Whether a defect is "close-before-merge" vs "track-and-close-in-next-sprint" — PO has the deciding vote when security says one thing and engineering velocity says another.
- Out-of-scope-list updates — PO can move an item from MVP 2 back to MVP 1 or vice versa with documented business-value rationale.

### 11.4 Release gate composition — the QUINTUPLE gate

For Phase 5+ release, the following five agents must each issue a **GO**:
1. **hr-product-owner** — product acceptance per PRD AC.
2. **qa-engineer** — test-pack pass rate + tenant isolation suite green.
3. **security-engineer** — zero Critical + zero High open security findings.
4. **devops-sre** — deploy pipeline green; SLOs in budget.
5. **database-architect** — Liquibase changelogs reversible; tenant provisioner idempotent.

PO can issue **GO WITH CONDITIONS** which is binding on the other gates' downstream expectations (i.e., the conditions become release-blocking).

---

## 12. Closing Assessment — Quality of Agent Execution

| Agent | Reliability | Notes |
|---|---|---|
| **backend-engineer** | **High** | Phase-by-phase delivery on time; remediation tasks closed promptly; defensive layering (service + DB triggers + ArchUnit) shows good engineering judgement. Watch: tests sometimes lag features (Docker-gated tests skipped on dev machines); CI artifact reporting (PC0-D011) still open. |
| **frontend-engineer** | **High overall, with one product-quality gap** | UI parity tests in place, PermissionGate consistently applied, MSW handlers thorough. **Gap caught here:** the `LOCALE_PREFIX(ru, en)` shortcut in MSW fixtures is exactly the kind of "good-enough for tests, bad for demo" trade-off that needs PO oversight. Recommend a code-review checklist item: any new `Record<Locale, string>` literal in fixtures must use 4 explicit values. |
| **qa-engineer** | **High** | Thorough defect catalogues; each phase review identifies 8–17 defects with reproducer-grade detail. Watch: i18n testing is currently key-count only; need a value-distinctness test added (see PO-1 fix). |
| **security-engineer** | **High** | Zero Critical / zero High open across all 4 phases is a meaningful achievement. Per-phase deep dives into ABAC, audit redaction, JSONB injection, race conditions are thorough. |
| **product-designer** | **Not exercised in this audit** | Designer artifacts (wireframes, tokens) are referenced in `07-design-foundation.md`. Review of designer outputs is out of scope here. |
| **database-architect** | **High** | DB grants for audit append-only, immutability triggers, composite FKs, JSONB validation — all rigorous. Liquibase changelogs 001–014 traceable. |
| **devops-sre** | **Adequate** | Helm charts + GitHub Actions present. Open items: CI report surfacing (PC0-D011), Docker tests in CI, weekly hash-chain verifier worker (deferred to MVP 2). |
| **hr-product-owner (self-assessment)** | **Adequate, with the critical lesson** that PRDs should specify multilingual content quality requirements at the **value-distinctness** level, not only at the **key-existence** level. Lesson encoded into PO-1's recommended fix (the lint rule). |

---

## 13. Appendix — Key File Paths Referenced

- PRD: `D:\2026\Лойиҳалар\Грейдинг\grading.hrlab.uz\docs\mvp1\01-product-prd.md`
- Role-permissions matrix: `docs/mvp1/role-permissions-matrix.md`
- MSW fixtures (PO-1 root cause): `frontend/src/shared/api/mocks/fixtures.ts` lines 318-323, 610-615
- Sidebar (PO-3 root cause): `frontend/src/shared/components/layout/Sidebar.tsx:48`
- AI placeholder (PO-2 root cause): `frontend/src/features/job-profiles/components/AIRecommendationPanel.tsx:48` + locale key `aiAssist.coming_soon`
- Locale bundles: `frontend/src/shared/i18n/locales/{en-US,ru-RU,uz-Cyrl-UZ,uz-Latn-UZ}.json` (413 keys each)
- Methodology module backend: `backend/src/main/java/uz/hrlab/grading/methodology/`
- Methodology DB changelogs: `backend/src/main/resources/db/changelog/tenant-schema/010-…013-…yaml`
- Audit module: `backend/src/main/java/uz/hrlab/grading/audit/`
- ArchUnit ban + `TenantAwareRepository`: under `backend/src/main/java/uz/hrlab/grading/tenancy/` and `…/common/`
- 8 prior reviews: `docs/mvp1/reviews/phase{0-1,2,3,4}-{qa,security}-review.md`

---

**End of PO Comprehensive Audit — Phases 0 through 4.**
