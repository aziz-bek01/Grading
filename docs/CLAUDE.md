# grading.hrlab.uz — Project Context (shared)

> This file is read automatically by Claude Code and is the **single shared context** for every agent.
> Agents must NOT repeat this content. They reference it.

## Product

`grading.hrlab.uz` — a secure **multi-tenant SaaS platform owned by HR Laboratories** for running job-grading projects for many **company-clients** (banks, holdings, universities, production companies, telecoms, insurance, public sector, large enterprises).

This is **not** a system for one bank. Use the term **"company-client"**, never "bank" (except as an example).

Core flow:
`company-client setup → tenant/project workspace → organization structure → position catalog → job profile → job analysis → methodology builder → evaluation/scoring → calibration → grade structure → salary ranges → reports → audit trail → archive`

## Domain principles (canonical — all agents obey)

1. Grading evaluates the **value of a POSITION**, not an employee's personality/performance.
2. Grade ≠ organizational hierarchy. Same org level → may be different grades; different org levels → may share a grade.
3. Methodology is **configurable**: `CLASSIC_8_FACTOR`, `EXTENDED_11_CRITERIA`, 14/16-grade, `CUSTOM`.
4. Approved methodology is **immutable**; any change creates a **new version**. Evaluation references `methodology_version_id`.
5. Evaluation score must be **reproducible**. Approved evaluation is immutable. Manual calibration requires reason/comment.
6. **Salary data is a separate, highly sensitive domain. Grade access ≠ salary access.**
7. AI is advisory only — it never approves grades. Human approval is mandatory.
8. **Audit trail is mandatory from MVP 1** (append-only, no update/delete via app flow).
9. **Tenant isolation is the highest-priority requirement**, enforced in every layer.
10. Localization is mandatory from day one.

## Tenant isolation (canonical security rule)

- `tenant_id` is **never** trusted from the frontend or uploaded files for business data; it comes from the authenticated security context / JWT / validated tenant-context.
- Every business table carries `tenant_id` (defense-in-depth) and `project_id` where applicable.
- Every business query is tenant-aware. **Never** `findById(id)` for tenant data — use `findByIdAndTenantId(...)` or central tenant filtering.
- Protect against BOLA/IDOR. Cross-tenant probing returns a safe 404/403 and is logged as a security event.
- Salary / audit / export are **separate** permissions.

**Golden rule:** no user, API, export, report, attachment, AI prompt, dashboard, log, cache, background job, DB query, object-storage path, or analytics endpoint may expose one company-client's data to another.

## Languages (mandatory, 4)

`ru-RU` · `uz-Cyrl-UZ` · `uz-Latn-UZ` · `en-US`. Default admin/consultant UI: `ru-RU`. All factor names, factor-level descriptions, grade names, report labels and UI dictionaries are translatable entities. Plan for Uzbek text-length differences.

## Tech stack

- **Backend:** Java 21, Spring Boot 3.x, Spring Security, OAuth2/OIDC Resource Server, JWT, Spring Data JPA, Bean Validation, modular monolith (`uz.hrlab.grading.*`) with async workers prepared for later. (DTO↔entity mapping is hand-written — no MapStruct.)
- **Frontend:** React 18/19, TypeScript, Vite, TailwindCSS, shadcn/ui, TanStack Query, Zustand, React Hook Form, Zod, i18next, React Router, Recharts/ECharts.
- **Data:** PostgreSQL, Liquibase. Hybrid multi-tenancy: shared control plane (public schema) + schema-per-tenant by default + DB-per-tenant for sensitive enterprise clients. RLS-ready from MVP 1.
- **Infra:** Docker, Kubernetes, Helm/Kustomize, Redis, S3-compatible object storage, Vault/KMS, Prometheus/Grafana/Loki/Tempo.
- **Money:** `NUMERIC(19,4)`. **Scores:** `NUMERIC(12,4)`. Never `DOUBLE`/`FLOAT`. IDs: `UUID`. Timestamps: `TIMESTAMPTZ`/`OffsetDateTime`.

## Roles

HRLab side: Super Admin, Project Manager, Consultant, Analyst.
Client side: Company Admin, HR Director, HR Specialist, Evaluation Committee Member, Department Manager, Viewer, External Auditor.

## MVP roadmap (summary)

- **MVP 1 — Core grading foundation:** tenant isolation, users/roles/permissions, project workspace, basic org structure, position catalog, job profile, basic methodology builder, scoring engine, grade assignment, audit trail, localization foundation.
- **MVP 2 — Workflow & delivery:** full workflow, approvals, Excel import/export, PDF/Word reports, comments, attachments, report center, async workers.
- **MVP 3 — Compensation:** salary ranges, compa-ratio, range penetration, red/green circle, budget scenarios, salary permissions, compensation reports/dashboards.
- **MVP 4 — AI & integrations:** AI assist (advisory), anomaly detection, HRM/ERP/Payroll/SSO/BI integrations, advanced analytics.

## Agent workflow (who runs when)

1. `hr-product-owner` — runs FIRST. PRDs, user stories, acceptance criteria, permissions matrix, audit-event matrix.
2. `database-architect` — schema/data contract BEFORE backend writes entities.
3. `security-engineer` — security requirements/threat models/gates, in parallel with build.
4. `product-designer` — design specs BEFORE frontend implementation.
5. `backend-engineer` / `frontend-engineer` / `integration-engineer` — build under the above contracts.
6. `qa-engineer` — converts artifacts into test packs; sprint-end GO/NO-GO.
7. `devops-sre` — containerization/deploy/observability; operational GO/NO-GO.

A production release needs four gates: QA GO + Security ship + DevOps operational GO + PO accept.

## Phase roadmaps

Each agent's detailed phase-by-phase roadmap lives in `docs/agents/<agent-name>.md`. Read the relevant file when you need the implementation plan; do not inline it.

## Standard answer format (all build/review agents)

1. Summary  2. Files created/changed (or findings)  3. Key decisions  4. How to run  5. Tests  6. Next recommended step.

Work in **vertical slices**. Finish a phase, ensure it compiles + tests pass, then move on. Generate real code, not pseudocode.
