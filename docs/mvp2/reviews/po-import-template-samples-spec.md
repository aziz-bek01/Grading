# PO Spec — Import Template Samples + Export Download Fix

**Owner:** hr-product-owner
**Status:** Authoritative requirements for integration-engineer
**Date:** 2026-05-24
**MVP:** 2 (Phase 3+)
**Predecessor docs:** `docs/mvp1/06-integration-blueprint.md` §5–6, §18

> Business value: Demo users (HRLab sales, prospective company-clients) currently cannot complete an end-to-end flow. They click "Download empty template" — nothing happens. They click "Download" in Export Center — nothing happens. The product looks broken in every demo. Until they can fill a template and download an export, the platform cannot be sold.

---

## 1. Problem Statement

### Gap A — Export Center "Download" produces nothing
* **User expects:** clicking the green "Download" button in `/app/projects/{id}/exports` triggers a browser `.xlsx`/`.pdf`/`.docx` download for the generated export.
* **What happens:** MSW handler returns `https://mock-storage.local/exports/.../result.xlsx?sig=stub`, the browser navigates to a non-existent host, and the download silently fails (or shows a CORS / DNS error). On a real backend the flow works, but during demos and on the staging build (MSW-backed) nothing downloads.
* **User impact:** product appears non-functional. Trust collapses in seconds.

### Gap B — Import wizard has no template samples
* **User expects:** when a user picks "Organization structure" template in `/app/projects/{id}/imports/new`, they expect a "Download empty template" button (XLSX with columns and column hints) AND a "Download sample" button (XLSX with example rows they can edit). Today's wizard description text ("Departments, divisions, units and parent hierarchy") is not enough — users do not know what columns are required, what data formats are accepted, or how parent-child references are encoded.
* **What happens:** no download buttons exist. Users must read the integration blueprint to construct an XLSX from scratch. Most demo users give up.
* **User impact:** import is unusable in self-service mode; HRLab consultants must hand-craft Excel files for every prospect.

---

## 2. Personas & Business Value

* **HRLab Consultant** (delivery): needs ready-to-fill templates so they can collect company-client structure data without writing column lists in email.
* **Client HR Specialist** (data entry): receives the empty template, fills it for their company, sends it back. Currently impossible without a hand-made example.
* **HRLab Sales/Demo runner**: needs the demo flow to *just work* end-to-end — pick template, see real download, click "Download sample" → file appears, fill it locally, upload back, click Download in Export Center → real file appears.

Business value: unblocks every demo path; reduces consultant hand-holding per project from hours to minutes; gives client HR specialists a self-service onboarding artefact.

---

## 3. User Journey Changes

### 3.1 Import wizard step 1 (template chooser)
Each template card now exposes **two new buttons** under the description:
* `[ Download empty template ]` — primary secondary button with download icon. Produces a 1-row XLSX (headers only + cell comments describing column).
* `[ Download sample ]` — ghost button. Produces a 5–40 row XLSX with realistic ACME-themed data the user can edit and re-upload.

Clicking either button navigates the browser to a download endpoint (`window.location.href = /api/v1/imports/templates/{code}/empty.xlsx`); the platform never holds the raw bytes in JS memory beyond the demo-mode blob.

### 3.2 Import list page (`/imports`)
A new `[ Download template ▼ ]` dropdown sits next to the existing `+ New import` CTA. Dropdown shows the 5 templates × 2 variants (empty / sample) = 10 entries. Same backend endpoint pair drives each item.

### 3.3 Export Center
* Download button must actually trigger a browser download in both real-backend mode and MSW mode. In MSW mode, the handler must generate real XLSX/PDF/DOCX bytes (no stub URL).
* `SignedDownloadButton` gains an explicit "Generating signed URL..." loading state plus an informative error message: *"Download failed. Try refresh or contact support."* (instead of the current generic "Failed to fetch download URL").
* In demo mode (when MSW is active), the button additionally shows a subtle "DEMO mode — file generated client-side" caption so users understand the bytes are synthetic.

---

## 4. Template Inventory

The 5 in-scope templates and their column schemas. All column codes are the canonical English IDs the backend parser keys on; localized labels are user-facing only and appear in cell comments.

### 4.1 ORG_STRUCTURE_V1 — 8 columns (5 required, 3 optional)

| Code | Type | Required | Validation | Locale labels (ru / uz-Cyrl / uz-Latn / en) |
|---|---|---|---|---|
| `external_id` | TEXT(64) | ✓ | unique within project, `[A-Z0-9_-]+` | "Внешний ID" / "Ташқи ID" / "Tashqi ID" / "External ID" |
| `name` | TEXT(255) | ✓ | non-empty trim | "Наименование" / "Номланиш" / "Nomlanish" / "Name" |
| `parent_external_id` | TEXT(64) | — | must resolve to another row in same batch or existing dept | "Родитель (внешний ID)" / "Ота-ташкилот" / "Ota tashkilot" / "Parent external ID" |
| `level` | NUMBER(int) | ✓ | 1–10 | "Уровень" / "Даража" / "Daraja" / "Level" |
| `type` | ENUM | ✓ | `OFFICE` \| `DEPARTMENT` \| `DIVISION` \| `UNIT` \| `TEAM` | "Тип" / "Тури" / "Turi" / "Type" |
| `location_code` | TEXT(32) | — | matches location registry | "Локация" / "Жойлашув" / "Joylashuv" / "Location" |
| `cost_center_code` | TEXT(32) | — | free text | "Центр затрат" / "Харажат маркази" / "Xarajat markazi" / "Cost center" |
| `description` | MULTILINE_TEXT(1000) | — | — | "Описание" / "Изоҳ" / "Izoh" / "Description" |

### 4.2 POSITION_CATALOG_V1 — 9 columns (5 required, 4 optional)

| Code | Type | Required | Validation |
|---|---|---|---|
| `external_id` | TEXT(64) | ✓ | unique within project |
| `title` | TEXT(255) | ✓ | non-empty |
| `department_external_id` | TEXT(64) | ✓ | **FK → ORG_STRUCTURE_V1.external_id** |
| `status` | ENUM | ✓ | `DRAFT` \| `ACTIVE` \| `ARCHIVED` |
| `function` | ENUM | — | `MANAGEMENT` \| `OPERATIONAL` \| `SUPPORT` \| `SPECIALIST` |
| `category` | ENUM | — | `MANAGER` \| `SPECIALIST` \| `WORKER` |
| `job_family` | TEXT(64) | — | free text (e.g. `Finance`, `Engineering`) |
| `job_level` | TEXT(16) | — | free text (e.g. `Senior`, `Lead`) |
| `headcount` | NUMBER(int) | ✓ | ≥ 0, ≤ 9999 |

### 4.3 JOB_PROFILE_V1 — 11 columns (4 required, 7 optional)

| Code | Type | Required | Notes |
|---|---|---|---|
| `position_external_id` | TEXT(64) | ✓ | FK → POSITION_CATALOG_V1.external_id |
| `purpose` | MULTILINE_TEXT(2000) | ✓ | |
| `main_duties` | MULTILINE_TEXT(4000) | ✓ | bullet-separated, `;` or `\n` |
| `responsibility_area` | MULTILINE_TEXT(2000) | ✓ | |
| `authority` | MULTILINE_TEXT(2000) | — | |
| `kpi_expected_results` | MULTILINE_TEXT(2000) | — | |
| `education_requirements` | MULTILINE_TEXT(1000) | — | |
| `experience_requirements` | MULTILINE_TEXT(1000) | — | |
| `knowledge_skills` | MULTILINE_TEXT(2000) | — | |
| `working_conditions` | MULTILINE_TEXT(1000) | — | |
| `actualization_date` | DATE (`YYYY-MM-DD`) | — | |

### 4.4 METHODOLOGY_FACTORS_V1 — 8 columns (6 required, 2 optional)

| Code | Type | Required | Notes |
|---|---|---|---|
| `factor_code` | TEXT(32) | ✓ | groups rows into one factor; e.g. `KNOWLEDGE` |
| `factor_name` | TEXT(255) | ✓ | |
| `factor_weight` | NUMBER(decimal 0–100, 4 dp) | ✓ | sum across all distinct factor_codes = 100 (WEIGHTED_POINTS) |
| `level_code` | TEXT(32) | ✓ | unique within factor; e.g. `L1` |
| `level_name` | TEXT(255) | ✓ | |
| `level_points` | NUMBER(decimal, 4 dp) | ✓ | 0 ≤ points ≤ factor_max_points |
| `level_order` | NUMBER(int) | — | default = row order within factor |
| `level_description` | MULTILINE_TEXT(1000) | — | |

### 4.5 GRADE_BANDS_V1 — 5 columns (4 required, 1 optional)

| Code | Type | Required | Notes |
|---|---|---|---|
| `grade_code` | TEXT(16) | ✓ | e.g. `G1`…`G14` |
| `min_score` | NUMBER(decimal, 4 dp) | ✓ | |
| `max_score` | NUMBER(decimal, 4 dp) | ✓ | non-overlapping with neighbours; contiguous |
| `label` | TEXT(255) | ✓ | e.g. "Junior" |
| `description` | MULTILINE_TEXT(500) | — | |

---

## 5. Sample Data Themes (ACME Holdings)

All samples use **ACME Holdings** — the same fictional company used in the seeded demo data (`mockDb.projects['proj-acme-2026']`).

### 5.1 ORG_STRUCTURE_V1 (12 rows)
1. `CEO-OFFICE` — CEO Office (root, level 1, OFFICE)
2. `CFO-OFFICE` — CFO Office (parent: CEO-OFFICE, level 2, OFFICE)
3. `CTO-OFFICE` — CTO Office (parent: CEO-OFFICE, level 2, OFFICE)
4. `HR-OFFICE` — HR Office (parent: CEO-OFFICE, level 2, OFFICE)
5. `OPS-OFFICE` — Operations Office (parent: CEO-OFFICE, level 2, OFFICE)
6. `FIN-DEPT` — Finance Department (parent: CFO-OFFICE, level 3, DEPARTMENT)
7. `TREASURY-DEPT` — Treasury Department (parent: CFO-OFFICE, level 3, DEPARTMENT)
8. `IT-INFRA-DEPT` — IT Infrastructure (parent: CTO-OFFICE, level 3, DEPARTMENT)
9. `SW-ENG-DEPT` — Software Engineering (parent: CTO-OFFICE, level 3, DEPARTMENT)
10. `HR-OPS-DEPT` — HR Operations (parent: HR-OFFICE, level 3, DEPARTMENT)
11. `TALENT-DEPT` — Talent Acquisition (parent: HR-OFFICE, level 3, DEPARTMENT)
12. `OPS-DEPT` — Operations (parent: OPS-OFFICE, level 3, DEPARTMENT)

### 5.2 POSITION_CATALOG_V1 (15 rows)
CFO, Senior Financial Analyst, Financial Analyst (→ FIN-DEPT); Head of Treasury, Treasury Specialist (→ TREASURY-DEPT); CTO, VP Engineering (→ CTO-OFFICE); Senior Software Engineer, Software Engineer, Junior Engineer (→ SW-ENG-DEPT); HR Director (→ HR-OFFICE); HR Business Partner, Recruiter (→ HR-OPS-DEPT / TALENT-DEPT); Operations Manager, Operations Specialist (→ OPS-DEPT).

### 5.3 METHODOLOGY_FACTORS_V1 (40 rows = 8 factors × 5 levels)
Matches `CLASSIC_8_FACTOR` seed: KNOWLEDGE / EXPERIENCE / COMPLEXITY / RESPONSIBILITY / AUTONOMY / INFLUENCE / COMMUNICATION / WORKING_CONDITIONS. Weight = 12.5% each. Levels: L1 Basic (40 pts) → L2 Beginning (80) → L3 Intermediate (120) → L4 Advanced (160) → L5 Expert (200). factor_max_points = 200.

### 5.4 GRADE_BANDS_V1 (14 rows)
G1: 0–50, G2: 50.0001–100, G3: 100.0001–150, …, G14: 950.0001–1000. Labels: G1 Junior, G2 Junior+, G3 Specialist, G4 Specialist+, G5 Senior Specialist, G6 Lead Specialist, G7 Team Lead, G8 Senior Lead, G9 Manager, G10 Senior Manager, G11 Department Head, G12 Director, G13 VP, G14 C-level.

### 5.5 JOB_PROFILE_V1 (5 rows)
For CFO, CTO, Senior Software Engineer, HR Director, Operations Manager — each row carries multilingual purpose/duties/KPI/education/experience values realistic for an ACME-sized holding.

---

## 6. Export Center Fix Requirements

### 6.1 Real backend
* Click `Download` → call existing `GET /api/v1/exports/{id}/download-url` → backend returns short-lived (≤ 60s) signed URL → frontend triggers `window.location.assign(signedUrl)` → browser downloads file. **This already works in production.** No change needed beyond the UX polish in §6.3.

### 6.2 MSW / demo mode
The MSW handler for `GET /api/v1/exports/:id/download-url` must:
1. Synthesise real XLSX/PDF/DOCX bytes matching the export type using a client-side generator (the `xlsx` npm package is sufficient for XLSX; PDF/DOCX may fall back to a simple text-marker XLSX with a DEMO notice).
2. Wrap the bytes in a `Blob` and produce a `URL.createObjectURL(blob)` URL.
3. Return that blob URL in the `{ url }` response shape (same contract as the backend).
4. **Content per export type:**
   * `METHODOLOGY` → 40 rows of factor + level data (sample from §5.3)
   * `EVALUATION_MATRIX` → 15 positions × 8 factor scores grid
   * `GRADE_STRUCTURE` → 14 grade rows (from §5.4)
   * `POSITION_CATALOG` → 15 positions (from §5.2)
   * `JOB_PROFILES` → 5 job profiles (from §5.5)
   * `GRADE_PYRAMID` → counts per grade (synthetic bell curve)
   * `SALARY_RANGES`, `RED_GREEN_CIRCLE`, `COMPENSATION_SCENARIOS` → 14 synthetic rows with a `[SALARY MASKED IN DEMO]` watermark
   * `REPORT_EXECUTIVE` → cover page + summary table
5. All MSW-generated workbooks include a header cell `"Generated by grading.hrlab.uz (DEMO) — ACME Holdings"` on row 1, with the real data starting on row 2 (or as a separate `_meta` sheet).

### 6.3 UX polish (`SignedDownloadButton`)
* New translation key `export.download.generating` shows "Generating signed URL..." instead of generic "Generating...".
* New translation key `export.download.error_v2` ("Download failed. Try refresh or contact support.") replaces the current generic message.
* In MSW mode, surface a small caption "DEMO mode — file generated locally" beneath the button when `import.meta.env.VITE_USE_MSW === 'true'`.

---

## 7. User Stories

* **US-T1**: As an HRLab Consultant, I want to download an empty XLSX template for each of the 5 import types, so that I can email it to a company-client without explaining the column schema in words.
* **US-T2**: As a Client HR Specialist, I want to download a sample XLSX with realistic example rows, so that I see exactly how to fill it in for my own organization.
* **US-T3**: As a Sales/Demo runner, I want the Download button in Export Center to produce a real file in demo mode, so that my prospect sees the platform work end-to-end without backend setup.
* **US-T4**: As any user, when a download fails, I want a clear error message ("Download failed. Try refresh or contact support.") so that I know to act, not wonder why nothing happened.

---

## 8. Acceptance Criteria (Given/When/Then)

**AC1 — Empty template download (wizard)**
* GIVEN user opens `/app/projects/{id}/imports/new` step 1
* WHEN they click `Download empty template` on the `ORG_STRUCTURE_V1` card
* THEN the browser downloads `ORG_STRUCTURE_V1_empty.xlsx`, file opens in Excel, sheet 1 has exactly 8 columns matching the §4.1 schema, row 1 is the header, no data rows, each header cell has a comment describing the column.

**AC2 — Sample template download (wizard)**
* GIVEN user opens `/app/projects/{id}/imports/new` step 1
* WHEN they click `Download sample` on the `ORG_STRUCTURE_V1` card
* THEN the browser downloads `ORG_STRUCTURE_V1_sample.xlsx`, file opens in Excel, sheet 1 has 13 rows (1 header + 12 ACME Holdings departments per §5.1), all parent-child references resolve within the sheet.

**AC3 — Import list dropdown**
* GIVEN user opens `/app/projects/{id}/imports`
* WHEN they click `Download template ▼` next to `+ New import`
* THEN a dropdown appears with 10 items (5 templates × 2 variants), each link triggers a download.

**AC4 — Export Center download (MSW mode)**
* GIVEN an export job in `GENERATED` status
* WHEN user clicks `Download` in MSW mode
* THEN the browser downloads a real `.xlsx` file matching the export type (no `mock-storage.local` 404).

**AC5 — Export Center download (real backend)**
* GIVEN an export job in `GENERATED` status on a real backend
* WHEN user clicks `Download`
* THEN backend issues a signed URL with TTL ≤ 60s and the file downloads. (No regression — existing behaviour preserved.)

**AC6 — Loading and error states**
* GIVEN user clicks `Download`
* WHEN the download-url fetch is in flight
* THEN the button label shows "Generating signed URL..."
* AND IF the fetch fails THEN an inline error "Download failed. Try refresh or contact support." appears below the button.

**AC7 — Template registry coverage**
* GIVEN the 5 supported import templates
* THEN both backend endpoints `/api/v1/imports/templates/{code}/empty.xlsx` AND `/api/v1/imports/templates/{code}/sample.xlsx` exist and return 200 for each code.

**AC8 — Permission gate**
* GIVEN the template download endpoints
* WHEN a user lacks `IMPORT_READ` permission
* THEN backend returns 403, audit emits a `TEMPLATE_DOWNLOAD_DENIED` event (audit nice-to-have, not blocker).

**AC9 — All exports contain ACME header**
* GIVEN any MSW-generated export
* WHEN the file is opened
* THEN cell A1 (or a `_meta` sheet) carries "Generated by grading.hrlab.uz (DEMO) — ACME Holdings".

**AC10 — Formula injection safety**
* GIVEN the template generator
* WHEN sample data contains a value starting with `=`, `+`, `-`, or `@`
* THEN the produced XLSX prefixes the value with an apostrophe (via the existing `SafeCellWriter`).

---

## 9. Permissions & Audit

* **Permission**: `IMPORT_READ` (already exists per `PermissionCodes`). Anyone who can see the import list can download templates — the templates contain no tenant data, only schema + synthetic ACME examples.
* **Audit**: emit `TEMPLATE_DOWNLOADED` (new optional action) carrying `templateCode`, `variant` (`empty` | `sample`), `actorUserId`. If audit-action enum addition is out of scope this sprint, log-only is acceptable.

---

## 10. Localization Requirements

The 4 new translation keys must exist in all four locales (`en-US`, `ru-RU`, `uz-Cyrl-UZ`, `uz-Latn-UZ`):
* `import.template.download_empty` — "Download empty template" / "Скачать пустой шаблон" / "Бўш шаблонни юклаб олиш" / "Bo'sh shablonni yuklab olish"
* `import.template.download_sample` — "Download sample" / "Скачать пример" / "Намунани юклаб олиш" / "Namunani yuklab olish"
* `import.template.dropdown_label` — "Download template" / "Скачать шаблон" / "Шаблонни юклаб олиш" / "Shablonni yuklab olish"
* `export.download.demo_warning` — "DEMO mode — file generated locally" / "Демо-режим — файл сгенерирован локально" / "Демо режим — файл маҳаллий яратилган" / "Demo rejim — fayl mahalliy yaratilgan"
* `export.download.error_v2` — replaces `export.download.error` with the more actionable copy
* `export.download.generating_url` — "Generating signed URL..." / "Генерация ссылки..." / "Ҳавола яратилмоқда..." / "Havola yaratilmoqda..."

The column headers inside the XLSX templates themselves are written in **English only** (canonical codes); column hints in cell comments are added in `ru-RU` as the primary HRLab consultant language. (Future MVP can localize the comments.)

---

## 11. Out of Scope (NOT to implement)

* Real ClamAV scan of the generated template files (templates are platform-authored, no scanning needed).
* Password-protecting downloaded templates.
* Per-tenant custom templates — only the 5 canonical templates are supported here.
* Backend-side localization of column-comment cell text (defer to MVP 3).
* Replacing `export.download.error` everywhere — the i18n key swap is scoped to this feature.
* PDF/DOCX rich rendering for MSW-mode exports — a simple XLSX-with-DEMO-watermark is acceptable for non-XLSX export types.
* New audit action enum entry (if it requires a DB migration, defer to next sprint).
* Salary export demo data being numerically realistic — show "[SALARY MASKED IN DEMO]" placeholders.
* Tenant-specific sample data — every sample is ACME Holdings.

---

## 12. Definition of Done

* Backend endpoints `/api/v1/imports/templates/{templateCode}/empty.xlsx` and `/api/v1/imports/templates/{templateCode}/sample.xlsx` return 200 for all 5 templates with correct `Content-Disposition`.
* Frontend Import Wizard step 1 cards show both buttons; clicks navigate to backend URLs.
* Frontend Import List page shows `Download template ▼` dropdown next to `+ New import`.
* Frontend `SignedDownloadButton` shows new loading state + improved error message; MSW mode produces real bytes.
* All 4 locales have the new keys (i18n parity test passes).
* Backend `ImportTemplateSamplesTest` covers empty + sample generation for all 5 templates with row-count assertions.
* Frontend `ImportWizardTemplateDownload.test` covers the two new button clicks.
* `./mvnw test` and `npx vitest run` both green.
* ArchUnit `excelCellWritesMustGoThroughSafeCellWriter` still passes (the new generator uses `SafeCellWriter`).
* Manual smoke: download empty + sample for ORG_STRUCTURE_V1, open in Excel, verify schema and ACME data.

---

## 13. Suggested Tasks

* **Backend (integration-engineer)**: `ImportTemplateSamples` service, two endpoints on `ImportController`, 5 sample-data generators, unit tests, audit-action consideration.
* **Frontend (integration-engineer)**: two new buttons on template cards, dropdown on list page, `SignedDownloadButton` polish, MSW handler updates for templates + export download blob, i18n keys, tests.

---

*End of spec. Handing off to integration-engineer.*
