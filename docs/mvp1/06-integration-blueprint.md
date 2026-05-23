# 06 — Integration & Import/Export Blueprint (MVP 1 + MVP 2)

**Owner:** integration-engineer
**Product:** grading.hrlab.uz (multi-tenant SaaS — HR Laboratories)
**Scope:** MVP 1 (foundation) + MVP 2 (Excel, reports, attachments, async workers)
**Status:** Authoritative spec for backend, frontend, QA, DevOps, security agents
**Canonical source:** `D:\2026\Лойиҳалар\Грейдинг\grading.hrlab.uz\архитектура.md`

> Golden rule
> No import, export, integration, report, file, API sync, background job, BI view, object storage path or generated document may mix or expose data from one company-client to another company-client.

---

## 1. Integration Objectives

1. **Tenant-safe data exchange** — every byte that enters or leaves the platform is bound to a `(tenant_id, project_id)` pair derived from the authenticated security context, never from file content.
2. **Staged, reversible imports** — no Excel row ever reaches a core domain table without explicit user commit after structural, row, business and security validation.
3. **Permission-gated exports** — every export verifies permission (`REPORT_EXPORT`, `SALARY_EXPORT` for salary data) before generation and again before download via short-lived signed URL.
4. **Async, observable workers** — long-running parse/generate/sync work runs in `grading-*-worker` processes with idempotency, retry+backoff and a dead-letter queue.
5. **Hardened file handling** — allowlisted formats, MIME+extension validation, AV scan, zip-bomb protection, path-traversal protection, formula-injection sanitization on every cell write.
6. **Connector-ready architecture** — MVP 2 lays the data model that MVP 3/4 reuses for HRM/ERP/Payroll/SSO/BI connectors without redesign.
7. **Audit-by-default** — 25+ integration audit events emitted with `trace_id`, actor, tenant, project, scope and result.
8. **Salary protection end-to-end** — `contains_salary_data` propagated through job, file, URL and audit; no salary fields in logs, emails or unscoped BI views.

---

## 2. Scope by MVP

### MVP 1 — Integration Foundation (no Excel templates yet)

Delivers the *primitives* the rest of the product builds on.

In scope:
- ImportBatch / ImportBatchRow / ImportError data model (tables + Java types).
- ExportJob / ExportFile data model.
- FileAttachment data model.
- Object storage abstraction (`ObjectStorageClient`) with tenant/project namespace.
- File upload API (`POST /api/v1/files`) with allowlist, MIME validation, malware scan hook.
- Signed download API (`GET /api/v1/files/{id}/download`).
- ValidationFramework interfaces (`Validator<T>`, `ValidationError`, `ValidationResult`).
- AuditEvent integration topics registered.
- Async job dispatch primitive (`JobDispatcher` + `JobRecord` table) — synchronous executor in MVP 1, swappable to queue in MVP 2.
- Integration audit event catalog (skeleton, all 25 events declared even if some are not yet emitted).

Out of scope in MVP 1:
- Actual Excel templates and parsers.
- PDF/Word generation.
- Distributed queue, dead-letter queue, real workers.
- External connectors (HRM/ERP/Payroll/SSO/BI/Market).

### MVP 2 — Excel + Reports + Workers + Attachments + Report Center

In scope:
- Excel **import templates** (10 use cases listed in §5).
- Excel **export templates** (10 use cases listed in §6).
- PDF and DOCX report generation (JasperReports/OpenPDF and docx4j).
- File attachments (job descriptions, evidence, supporting docs).
- Report Center UI (request, status, download, expiry).
- 4 async workers (`grading-import-worker`, `grading-export-worker`, `grading-report-worker`, `grading-integration-worker` — last one stubbed, used in MVP 4).
- Real queue (Redis/RabbitMQ/Kafka — devops-sre choice), DLQ, retry policy.
- Upload wizard, validation preview, error table, column mapping, commit confirmation UI.

Out of scope in MVP 2 (deferred to MVP 4):
- HRM/ERP/Payroll/SSO/BI connectors.
- Market salary survey imports.
- Bi-directional sync.

---

## 3. Import/Export Architecture

```
              ┌──────────────────────────────────────────────┐
              │  Frontend SPA (upload wizard, export center) │
              └──────────────┬───────────────────────────────┘
                             │ HTTPS + JWT (tenant claim)
                             ▼
              ┌──────────────────────────────────────────────┐
              │  Spring Boot API — Integration Module        │
              │  ├─ ImportController                          │
              │  ├─ ExportController                          │
              │  ├─ FileController                            │
              │  ├─ ReportController                          │
              │  Permission check + tenant/project context   │
              └──────┬────────────────────┬─────────────────-┘
                     │                    │
        ┌────────────▼─────┐   ┌──────────▼──────────┐
        │ Object Storage   │   │ PostgreSQL          │
        │ S3-compatible    │   │ import_batches      │
        │ tenants/{t}/...  │   │ import_batch_rows   │
        │ + AV scan        │   │ import_errors       │
        └────────────┬─────┘   │ export_jobs         │
                     │         │ export_files        │
                     │         │ file_attachments    │
                     │         │ job_records         │
                     │         └──────────┬──────────┘
                     │                    │
              ┌──────▼────────────────────▼──────────────────┐
              │  Job Queue (Redis/Rabbit/Kafka)              │
              │   payload = { job_id, tenant_id, ... }       │
              │  but worker re-reads from DB.                │
              └──┬───────────┬──────────────┬───────────────-┘
                 │           │              │
       ┌─────────▼────┐ ┌────▼──────┐ ┌─────▼─────────┐ ┌──────────────┐
       │ import-worker│ │export-wkr │ │ report-worker │ │ integration- │
       │              │ │           │ │               │ │  worker      │
       └─────────────-┘ └──────────-┘ └──────────────-┘ └──────────────┘
                 │             │              │
                 ▼             ▼              ▼
              DLQ + retry, metrics, audit events
```

Key flows:
- **Import** = upload → scan → parse to staging → validate → user review → commit (transactional copy to core tables) → audit + archive.
- **Export** = request → permission → queue → worker fetches scoped data → write XLSX/PDF/DOCX → store under tenant namespace → notify → signed URL.

---

## 4. Async Worker Architecture

### 4.1 Worker catalog

| Worker | Triggers | Job types |
|---|---|---|
| `grading-import-worker` | `IMPORT_PARSE`, `IMPORT_VALIDATE`, `IMPORT_COMMIT` | parse XLSX to staging, run validators, commit transactional |
| `grading-export-worker` | `EXPORT_GENERATE` | Apache POI generation of all 10 Excel exports |
| `grading-report-worker` | `REPORT_PDF`, `REPORT_DOCX`, `REPORT_PPT_TABLES` | JasperReports/OpenPDF, docx4j |
| `grading-integration-worker` | `HRM_SYNC`, `ERP_SYNC`, `PAYROLL_SYNC`, `MARKET_IMPORT`, `SSO_PROVISION` | external connectors (stubbed in MVP 2, live MVP 4) |

### 4.2 Job contract (every worker, every job)

Queue payload contains **only**:
```json
{
  "job_id": "uuid",
  "job_type": "IMPORT_VALIDATE",
  "enqueued_at": "2026-05-23T08:00:00Z",
  "idempotency_key": "uuid",
  "trace_id": "uuid"
}
```

Worker behavior — **mandatory**:
1. **Reload** the `JobRecord` from `job_records` table by `job_id`.
2. Read `tenant_id`, `project_id`, `requested_by`, business payload (e.g. `import_batch_id`) from the DB row, **not from the queue message**.
3. Push `(tenant_id, project_id, user_id, trace_id)` into a `WorkerSecurityContext` (equivalent of the API security context).
4. Verify the requesting user still has the required permission **at execution time** (not just at enqueue).
5. Look up `idempotency_key` in `job_idempotency` table — if a `COMPLETED` row exists, return its result, do not re-execute.
6. Execute the unit of work inside a try/catch with `trace_id` set on MDC.
7. On success: write result, status `COMPLETED`, audit event, metrics.
8. On transient failure: status `RETRY_SCHEDULED`, increment attempt, schedule with exponential backoff (e.g. `min(2^n * 30s, 30min)`).
9. On exhaustion (e.g. 5 attempts) or non-retryable error: move to DLQ table `job_dead_letter`, status `DEAD`, alert.
10. Logs contain only safe metadata (job_id, type, status, counters). **Never** log Excel content, salary, names beyond row counts.

### 4.3 Retry policy matrix

| Failure | Action |
|---|---|
| AV scan failed | No retry. Mark batch `SCAN_FAILED`. Alert. |
| Validation errors found | No retry. Status `VALIDATION_FAILED` or `READY_FOR_REVIEW`. |
| Permission denied at execution | No retry. Status `FAILED`. Audit. |
| Transient DB / S3 / external timeout | Retry with backoff. |
| External API 4xx (auth) | No retry. Alert. |
| External API 5xx / 429 | Retry with backoff + jitter. |
| Out of memory / panic | No retry. DLQ. Page on-call. |

### 4.4 Metrics (per worker)
- `worker_job_started_total{worker, type}`
- `worker_job_completed_total{worker, type, status}`
- `worker_job_duration_seconds{worker, type}` (histogram)
- `worker_queue_depth{worker}`
- `worker_dlq_size{worker}`
- `worker_retry_count{worker, type}`

---

## 5. Excel Import Framework (10 use cases)

All templates share these mechanics:
- One **logical entity per workbook** (multi-sheet allowed if entity needs it).
- First row = header. Header row matched against a **named-template registry** (`template_id`, `template_version`).
- A "Notes" sheet describes columns, required/optional, data types.
- Column mapping screen lets user remap headers if their file differs.
- After parsing, every source row becomes one row in `import_batch_rows` with `raw_json`, `mapped_json` and validation outcome.
- A staged dry-run preview is presented before commit.

### 5.1 Template catalog

| # | Template | Sheets | Key required columns | Business validators |
|---|---|---|---|---|
| 1 | **Organization structure** | Departments | external_id, name, parent_external_id, level | parent exists (or null for root), no cycles, level consistent |
| 2 | **Departments + hierarchy** | Departments, Locations | external_id, name, parent_external_id, location_code | parent exists, location exists |
| 3 | **Position catalog** | Positions | external_id, title, department_external_id, status | dept exists, title not duplicated within dept |
| 4 | **Job profile fields** | Profiles | position_external_id, purpose, responsibilities, requirements | position exists, required fields populated |
| 5 | **Job analysis answers** | Answers | position_external_id, question_code, answer | question_code in active methodology, answer matches type |
| 6 | **Methodology factors + levels** | Factors, Levels | factor_code, factor_name, level_code, level_name, weight, score | weights sum to 100%, levels ordered, unique codes |
| 7 | **Grade bands** | Grades | grade_code, min_score, max_score, label | non-overlapping ranges, contiguous, ordered |
| 8 | **Salary ranges** | Ranges | grade_code, min, mid, max, currency | grade exists, min<mid<max, currency in allowlist; **requires SALARY_EDIT** |
| 9 | **Employee compensation snapshots** | Employees | employee_external_id, position_external_id, current_salary, currency, effective_date | position exists; **requires PAYROLL_IMPORT**; field-level encryption applied post-commit |
| 10 | **Market salary survey** | Market | source, role_code, region, grade_match, p25, p50, p75 | source whitelisted; isolated from client data; coefficient traceable |

### 5.2 Per-row lifecycle inside a batch

```
RAW → MAPPED → STRUCTURE_VALID → ROW_VALID → BUSINESS_VALID → READY → COMMITTED
                       │             │             │
                       └─ ERROR ─────┴─────────────┘  (errors stored, row excluded from commit)
```

### 5.3 Mapping rules

- Backend never trusts `tenant_id`, `project_id` columns in the file. If present, they are read into `raw_json` but **overridden** at commit by `WorkerSecurityContext`.
- Cross-tenant references (e.g. a `department_external_id` that maps to another tenant) are blocked at business validation with `CROSS_TENANT_REFERENCE` BLOCKER error.
- External IDs are persisted in `external_id_mappings(tenant_id, project_id, source_system, external_entity_type, external_id, internal_id)`.

---

## 6. Excel Export Framework (10 use cases)

All exports:
- Run inside `grading-export-worker`.
- Build the result from **tenant-scoped queries only** (parameterized with `tenant_id` and `project_id`).
- Apply **formula-injection sanitization** to every cell that originated from user input (see §11).
- Set `contains_salary_data = true` when any column may carry salary or compensation values; require `SALARY_EXPORT` permission for those.
- Write to `tenants/{tenantId}/projects/{projectId}/exports/{exportJobId}/result.xlsx`.

| # | Export | Permission | Contains salary | Notes |
|---|---|---|---|---|
| 1 | Position catalog | `POSITION_VIEW` | No | One row per position |
| 2 | Job profiles | `PROFILE_VIEW` | No | One sheet per profile section optional |
| 3 | Methodology snapshot | `METHODOLOGY_VIEW` | No | Factors + levels + weights |
| 4 | Evaluation matrix | `EVALUATION_VIEW` | No | Positions × factors |
| 5 | Grade structure | `GRADE_VIEW` | No | Grade bands |
| 6 | Grade distribution | `GRADE_VIEW` | No | Counts per grade |
| 7 | Salary ranges | `SALARY_EXPORT` | **Yes** | min/mid/max per grade |
| 8 | Red/green circle | `SALARY_EXPORT` | **Yes** | Out-of-range employees |
| 9 | Compensation scenarios | `SALARY_EXPORT` | **Yes** | Before/after FOT |
| 10 | Executive report tables | `REPORT_EXPORT` | Conditional | PowerPoint-ready blocks |

---

## 7. File Storage Architecture

- S3-compatible object storage (per ADR-011).
- Single bucket per environment (e.g. `grading-prod`), with **tenant/project prefixes**.
- Bucket policy: `Deny` for `Principal: *` (no public). All access via backend-signed URLs.
- Server-side encryption (SSE-KMS) enabled. KMS key rotation policy owned by security-engineer.
- Object metadata required on every PUT: `tenant_id`, `project_id`, `entity_type`, `checksum_sha256`, `content_type`, `uploaded_by`, `contains_salary_data` (bool).
- Versioning: enabled. Lifecycle: delete non-current versions after 90 days; expire `exports/` after 30 days; expire `imports/` after 180 days; `attachments/` retained for project lifetime.
- File is stored under a **generated UUID key**, not the user-supplied filename. Original filename kept only as metadata.

### 7.1 Object storage namespace pattern (mandatory)

```
tenants/{tenantId}/projects/{projectId}/imports/{importBatchId}/original.xlsx
tenants/{tenantId}/projects/{projectId}/imports/{importBatchId}/errors.xlsx
tenants/{tenantId}/projects/{projectId}/exports/{exportJobId}/result.xlsx
tenants/{tenantId}/projects/{projectId}/reports/{reportId}/report.pdf
tenants/{tenantId}/projects/{projectId}/reports/{reportId}/report.docx
tenants/{tenantId}/projects/{projectId}/attachments/{attachmentId}/{safeFilename}
```

- `{tenantId}` and `{projectId}` come from `WorkerSecurityContext` only.
- `{safeFilename}` = sanitized original filename (NFC normalized, stripped of path separators, control chars, leading dots, length-limited).
- `..` and absolute paths in any segment cause a `PATH_TRAVERSAL` rejection.

---

## 8. Import Batch Data Model (13 statuses)

`import_batches` columns (high-level — exact DDL owned by **database-architect**):

| Field | Type | Notes |
|---|---|---|
| id | UUID | PK |
| tenant_id | UUID | from context |
| project_id | UUID | from context |
| template_id | TEXT | e.g. `org-structure-v1` |
| template_version | TEXT | |
| status | ENUM | see below |
| original_filename | TEXT | metadata only |
| storage_key | TEXT | `tenants/.../imports/.../original.xlsx` |
| checksum_sha256 | TEXT | |
| file_size_bytes | BIGINT | |
| total_rows | INT | filled after parse |
| valid_rows | INT | |
| error_rows | INT | |
| warning_rows | INT | |
| committed_rows | INT | |
| contains_salary_data | BOOLEAN | |
| uploaded_by | UUID | user |
| created_at, updated_at | TIMESTAMP | |
| committed_at | TIMESTAMP | |
| trace_id | UUID | |
| idempotency_key | UUID | for commit |

`import_batch_rows` (per source row): `id, batch_id, row_number, raw_json, mapped_json, validation_status, errors_json, committed_entity_id, committed_at`.

`import_errors` (per error/warning, joinable to UI table): `id, batch_id, row_number, column, field, code, severity, message, suggested_fix, trace_id`.

### 8.1 Status state machine

```
UPLOADED
   │ AV scan starts
   ▼
SCANNING ──── fail ──► SCAN_FAILED (terminal)
   │ pass
   ▼
PARSING ──── parse error ──► FAILED
   │
   ▼
VALIDATING ──── BLOCKER ─────► VALIDATION_FAILED (terminal)
   │ has errors but no blockers
   ▼
READY_FOR_REVIEW
   │ user confirms (or fixes mapping and re-validates)
   ▼
READY_TO_COMMIT
   │ user clicks commit
   ▼
COMMITTING
   │
   ├── all rows ok ──► COMMITTED
   ├── some rows ok ─► PARTIALLY_COMMITTED
   └── all rows fail ► FAILED
   │ retention policy
   ▼
ARCHIVED
```

Plus `CANCELLED` reachable from `READY_FOR_REVIEW` or `READY_TO_COMMIT`.

**13 statuses total:** UPLOADED, SCANNING, SCAN_FAILED, PARSING, VALIDATING, VALIDATION_FAILED, READY_FOR_REVIEW, READY_TO_COMMIT, COMMITTING, COMMITTED, PARTIALLY_COMMITTED, FAILED, CANCELLED, ARCHIVED. (The brief lists 13; SCAN_FAILED is treated as a sub-state of FAILED if a flat list is preferred.)

---

## 9. Export Job Data Model (8 statuses)

`export_jobs` columns:

| Field | Type | Notes |
|---|---|---|
| id | UUID | PK |
| tenant_id, project_id | UUID | from context |
| export_type | ENUM | one of the 10 templates |
| format | ENUM | XLSX, PDF, DOCX |
| status | ENUM | see below |
| requested_by | UUID | |
| filters_json | JSONB | scope/filters used |
| contains_salary_data | BOOLEAN | |
| contains_personal_data | BOOLEAN | |
| row_count | INT | filled after generation |
| file_size_bytes | BIGINT | |
| storage_key | TEXT | |
| checksum_sha256 | TEXT | |
| created_at, generated_at, expires_at | TIMESTAMP | |
| downloaded_at | TIMESTAMP | nullable |
| idempotency_key | UUID | per (user, type, filters) hash |
| trace_id | UUID | |

### 9.1 State machine (8 statuses)

```
REQUESTED → QUEUED → GENERATING → GENERATED → DOWNLOADED
                              │              │
                              ├─► FAILED     └─► EXPIRED
                              │
                              └─► CANCELLED
```

Statuses: **REQUESTED, QUEUED, GENERATING, GENERATED, FAILED, DOWNLOADED, EXPIRED, CANCELLED**.

`EXPIRED` set by a daily sweeper after `expires_at`; signed URLs always shorter than `expires_at`.

---

## 10. File Attachment Data Model

`file_attachments`:

| Field | Type | Notes |
|---|---|---|
| id | UUID | PK |
| tenant_id, project_id | UUID | from context |
| owner_entity_type | TEXT | e.g. `POSITION`, `JOB_PROFILE`, `EVALUATION` |
| owner_entity_id | UUID | |
| original_filename | TEXT | metadata only |
| safe_filename | TEXT | used in storage path |
| storage_key | TEXT | `tenants/.../attachments/{id}/{safeFilename}` |
| content_type | TEXT | validated MIME |
| file_size_bytes | BIGINT | |
| checksum_sha256 | TEXT | |
| scan_status | ENUM | PENDING/CLEAN/INFECTED |
| uploaded_by | UUID | |
| created_at | TIMESTAMP | |
| deleted_at | TIMESTAMP | soft delete |

Attachments inherit the permission model of `owner_entity_type` + `owner_entity_id`. Downloads always go through `GET /api/v1/files/{id}/download`, which:
1. Verifies user can access the owner entity.
2. Generates a signed URL with TTL ≤ 60s.
3. Emits `FILE_DOWNLOADED` audit.

---

## 11. Validation Pipeline (5 levels)

| Level | Where | What it checks | Outcome |
|---|---|---|---|
| 1. **File** | API endpoint, before storing | MIME, extension allowlist, size cap, AV scan (clamav or vendor), zip-bomb, password-protected files rejected, macro-enabled rejected | `SCAN_FAILED` / accept |
| 2. **Structure** | `grading-import-worker` after PARSING | Required sheets present, required columns present, no duplicate columns, declared types parseable, max row count | `VALIDATION_FAILED` if BLOCKER |
| 3. **Row** | Worker, per row | Required fields not null, formats (date, decimal, currency, code), unique business keys, enum values | row-level `ERROR` |
| 4. **Business** | Worker, per row, requires DB lookups | Parent exists, no cycle, references resolve, no cross-tenant FK, ranges non-overlapping, weights sum to 100, methodology version active | `ERROR` or `WARNING` |
| 5. **Security** | Worker, at commit time | Tenant context still valid, project active, user still has permission, salary permission for salary data, no formula content in computed-from-user fields | hard `BLOCKER` |

Validators are stateless `Validator<RowDTO>` beans with priority ordering; each appends to a `ValidationResult` aggregate.

---

## 12. Error Handling Model

Every error/warning record:

```json
{
  "code": "ROW_PARENT_NOT_FOUND",
  "severity": "ERROR",
  "row": 42,
  "column": "parent_external_id",
  "field": "parentExternalId",
  "message": "Parent department PD-9 does not exist in this project.",
  "suggested_fix": "Check spelling, or import parent department first.",
  "trace_id": "f8…",
  "technical_details": null
}
```

### 12.1 Severity

| Severity | Effect |
|---|---|
| **BLOCKER** | Stops the entire batch. Commit disabled. |
| **ERROR** | Row excluded from commit. Other rows may proceed (→ `PARTIALLY_COMMITTED`). |
| **WARNING** | Row imported but flagged in summary. Requires acknowledgement. |
| **INFO** | Informational, no action. |

Frontend renders an error table with row/column anchors and a "Download error report" button (XLSX of failed rows + reasons, stored at `imports/{id}/errors.xlsx`).

---

## 13. Security Controls

### 13.1 Excel formula injection sanitization (mandatory on every cell write)

If a string cell value's first non-whitespace character is one of `= + - @ \t \r \n`, prefix with a single apostrophe (`'`) before writing, or write it as a quoted text-formatted cell. Applied in the shared `SafeCellWriter`.

Cells that **must not** be sanitized: numbers, dates, formulas the platform itself authored (which are never derived from user input).

### 13.2 File upload allowlist

| Purpose | Allowed |
|---|---|
| Imports | `.xlsx` only (no `.xls`, no `.xlsm`) |
| Attachments | `.pdf`, `.docx`, `.xlsx`, `.png`, `.jpg`, `.jpeg`, `.txt` (allowlist configurable per tenant) |
| Reports (output only) | `.pdf`, `.docx`, `.xlsx` |

MIME validated by sniff (Apache Tika), **must** match extension. Mismatch → reject with `FILE_TYPE_MISMATCH`.

### 13.3 Other controls
- Size caps: imports ≤ 25 MB / 200k rows (tunable), attachments ≤ 50 MB (tunable).
- AV scan (clamav daemon or vendor) before storage move from `quarantine/` to tenant namespace.
- Password-protected/macro-enabled workbooks rejected.
- Zip-bomb protection: cap decompressed size and per-entry ratio (e.g. ratio > 100 → reject).
- Path traversal protection: reject any storage path containing `..`, control chars, NUL byte.
- Signed URLs: TTL ≤ 60s for downloads, ≤ 300s for direct browser uploads (if used). Generated **after** permission check, scoped to a single object key, single HTTP verb.
- Public buckets forbidden by IaC policy.

---

## 14. Tenant/Project Scoping Rules (hard)

1. Backend **never** trusts `tenant_id` or `project_id` from uploaded file content. They are read into `raw_json` for audit but **overridden** at commit.
2. Active `(tenant_id, project_id)` comes only from authenticated JWT claims + URL path validation (`/api/v1/projects/{projectId}/imports`) against active tenant in `WorkerSecurityContext`.
3. Workers re-load job from DB and re-establish `WorkerSecurityContext` before any DB call.
4. Every domain query that backs an export is parameterized with `tenant_id` AND `project_id` — no `SELECT * FROM positions` without scope, period.
5. `external_id_mappings` is the only place where a cross-system ID can be resolved, and rows there carry `(tenant_id, project_id)` and are unique on `(tenant_id, source_system, external_entity_type, external_id)`.
6. Object keys without the tenant/project prefix are rejected by a storage-layer guard.

---

## 15. Salary Data Protection Rules

- `SALARY_EXPORT` permission required for exports #7, #8, #9, and any other export whose data includes salary fields.
- `PAYROLL_IMPORT` (or `SALARY_EDIT`) required for imports #8 and #9.
- `contains_salary_data` boolean is computed by the worker from the data shape (not user-declared) and stored on `export_jobs`, `import_batches`, `file_attachments`, and propagated to audit.
- The UI must show a "This export contains salary data" warning **before** generation and **before** download.
- Logs (application, worker, request, AV scan, etc.) must **never** include salary values. The `SafeLogger` masks any field whose JSON path matches `*.salary`, `*.compensation`, `*.amount`, `*.pay*`.
- Emails carrying notifications about salary exports contain only a status + link, never the file or values inline.

---

## 16. Audit Event Matrix (25+ events)

| Event | Where emitted | Required fields |
|---|---|---|
| `IMPORT_UPLOADED` | File API | batch_id, filename, size, checksum |
| `IMPORT_SCAN_STARTED` | Import worker | batch_id |
| `IMPORT_SCAN_FAILED` | Import worker | batch_id, reason |
| `IMPORT_PARSED` | Import worker | batch_id, total_rows |
| `IMPORT_VALIDATED` | Import worker | batch_id, valid/error/warning counts |
| `IMPORT_VALIDATION_FAILED` | Import worker | batch_id, blocker_count |
| `IMPORT_COMMITTED` | Import worker | batch_id, committed_rows |
| `IMPORT_CANCELLED` | API or worker | batch_id, by_user |
| `EXPORT_REQUESTED` | Export API | job_id, type, contains_salary_data |
| `EXPORT_GENERATED` | Export worker | job_id, row_count, file_size |
| `EXPORT_DOWNLOADED` | File download API | job_id, by_user |
| `FILE_UPLOADED` | File API | file_id, entity, size |
| `FILE_DOWNLOADED` | File download API | file_id, by_user |
| `INTEGRATION_CREATED` | Integration API | connection_id, type |
| `INTEGRATION_ENABLED` | Integration API | connection_id |
| `INTEGRATION_DISABLED` | Integration API | connection_id |
| `INTEGRATION_SYNC_STARTED` | Integration worker | sync_id, type |
| `INTEGRATION_SYNC_COMPLETED` | Integration worker | sync_id, stats |
| `INTEGRATION_SYNC_FAILED` | Integration worker | sync_id, reason |
| `PAYROLL_IMPORT_STARTED` | Import worker | batch_id |
| `PAYROLL_IMPORT_COMPLETED` | Import worker | batch_id, committed_rows |
| `SALARY_EXPORT_REQUESTED` | Export API | job_id, scope |
| `SALARY_EXPORT_DOWNLOADED` | File download API | job_id, by_user |
| `SSO_LOGIN` | Identity module | user_id, idp, mapping |
| `SSO_MAPPING_CHANGED` | Identity admin | mapping_id, before/after diff |

All events carry: `event_id`, `event_type`, `tenant_id`, `project_id` (nullable for SSO), `actor_user_id`, `actor_role`, `trace_id`, `occurred_at`, `outcome`, `details` (JSONB, masked for salary).

---

## 17. API Endpoints

```
# Imports
POST   /api/v1/projects/{projectId}/imports                  (multipart, template_id) → 202 + batch_id
GET    /api/v1/projects/{projectId}/imports/{batchId}        → batch summary + status
GET    /api/v1/projects/{projectId}/imports/{batchId}/rows   ?status&page → rows
GET    /api/v1/projects/{projectId}/imports/{batchId}/errors → error list
POST   /api/v1/projects/{projectId}/imports/{batchId}/mapping (column mapping) → re-validate
POST   /api/v1/projects/{projectId}/imports/{batchId}/commit  (idempotency_key header) → 202
POST   /api/v1/projects/{projectId}/imports/{batchId}/cancel  → 200
GET    /api/v1/projects/{projectId}/imports/{batchId}/error-report → signed URL

# Exports
POST   /api/v1/projects/{projectId}/exports                  (type, format, filters, idempotency_key) → 202 + job_id
GET    /api/v1/projects/{projectId}/exports/{jobId}          → status
POST   /api/v1/projects/{projectId}/exports/{jobId}/cancel
GET    /api/v1/projects/{projectId}/exports/{jobId}/download → 302 to signed URL (TTL ≤ 60s)

# Files (attachments)
POST   /api/v1/projects/{projectId}/files                    (multipart, owner_type, owner_id)
GET    /api/v1/projects/{projectId}/files/{fileId}           → metadata
GET    /api/v1/projects/{projectId}/files/{fileId}/download  → 302 signed URL
DELETE /api/v1/projects/{projectId}/files/{fileId}           → soft delete

# Reports (Report Center)
POST   /api/v1/projects/{projectId}/reports                  (report_type, format, filters)
GET    /api/v1/projects/{projectId}/reports                  → list
GET    /api/v1/projects/{projectId}/reports/{id}             → status
GET    /api/v1/projects/{projectId}/reports/{id}/download    → signed URL
```

All endpoints require JWT, tenant claim must match `projectId.tenant`, audit events emitted.

---

## 18. Frontend UX Requirements

### 18.1 Upload wizard (4 steps)
1. **Select template** — choose from registry, see example download.
2. **Upload file** — drag/drop, shows scan + parse progress (poll status).
3. **Map columns** (if structural mismatch) — drag headers to fields, save mapping.
4. **Review & commit** — summary cards (total / valid / errors / warnings), error table with row/column anchors, "Download error report", "Cancel", "Commit" (disabled if BLOCKER).

### 18.2 Validation preview table
- Server-side paginated, filterable by severity/column/code.
- Each row links to a side drawer with full row JSON and suggested fix.
- Bulk action: "Acknowledge all warnings".

### 18.3 Export center
- List of `export_jobs` and `reports` with status chips.
- Action: "Generate" (form: type, filters, format), "Download" (only when `GENERATED` and not `EXPIRED`), "Cancel".
- Salary exports show a confirm modal: "This export contains salary data. Continue?"

### 18.4 Signed download
- Frontend never holds raw object URLs. Every download goes via `/download` endpoint that returns a fresh short-lived signed URL.
- Browser performs `window.location.assign(signedUrl)` immediately; UI does not store it.

---

## 19. Observability Metrics

- `import_jobs_total{template,status}`
- `import_jobs_failed_total{template,reason}`
- `import_duration_seconds{template}` (histogram)
- `import_rows_processed_total{template,outcome}`
- `import_rows_failed_total{template}`
- `export_jobs_total{type,format,status}`
- `export_jobs_failed_total{type,reason}`
- `export_duration_seconds{type}` (histogram)
- `integration_sync_total{system,status}`
- `integration_sync_failed_total{system,reason}`
- `external_api_latency_seconds{system}` (histogram)
- `worker_queue_depth{worker}`
- `worker_dlq_size{worker}`
- `file_scan_failures_total{reason}`
- `salary_exports_total{type}` (separate counter, alertable)
- `signed_url_issued_total{purpose}`

Dashboards (owned by devops-sre):
1. Import/Export Operations
2. Integration Health
3. Worker Queue & DLQ
4. File Security (scans, rejects, formula injection blocks)
5. Payroll Import Security
6. Report Export

SLOs (initial targets, tunable):
- 95% of imports < 200k rows complete validation in < 5 min.
- 95% of XLSX exports of < 50k rows complete in < 60s.
- DLQ size = 0 sustained; any non-zero pages on-call within 15 min.

---

## 20. Test Cases (high level — full Given/When/Then owned by qa-engineer)

1. Upload valid template → COMMITTED, audit emitted, rows in core tables.
2. Upload XLSX with wrong template → BLOCKER `TEMPLATE_MISMATCH`, no rows staged.
3. Upload file with missing required column → BLOCKER, status `VALIDATION_FAILED`.
4. Upload row with invalid type (e.g. text in numeric) → ERROR on row, others valid → `PARTIALLY_COMMITTED`.
5. Upload row referencing another tenant's external_id → BLOCKER `CROSS_TENANT_REFERENCE`.
6. Upload Excel with `=cmd|'/C calc'!A1` in a name → cell sanitized on export of same data; import accepts as text.
7. Upload password-protected XLSX → reject, `SCAN_FAILED`.
8. Upload macro-enabled `.xlsm` → reject, `FILE_TYPE_NOT_ALLOWED`.
9. Upload 1GB zip-bomb → reject, `ZIP_BOMB_DETECTED`.
10. Salary import without `SALARY_EDIT` → 403 + audit, no batch created.
11. Salary export without `SALARY_EXPORT` → 403 + audit.
12. Signed URL fetched after expiry → 403/404.
13. Download attachment from another tenant's project (URL forgery) → 403 + audit.
14. Worker crashes mid-commit → on restart, idempotency key prevents double commit; batch resumes or fails cleanly.
15. Worker hits DLQ after 5 attempts → alert fired, manual replay possible.
16. Concurrent commits with same idempotency key → only one COMMITTED; the other returns same result.
17. AV scan reports infected file → status `SCAN_FAILED`, audit `IMPORT_SCAN_FAILED`, file purged.

---

## 21. Risks and Mitigations

| # | Risk | Impact | Mitigation |
|---|---|---|---|
| R1 | Cross-tenant leak via uploaded `tenant_id` column | Catastrophic | Backend overrides from context; storage-layer guard; tenant isolation tests in CI |
| R2 | Formula injection in exports | High (RCE on user machine) | `SafeCellWriter` mandatory; static analysis rule to block direct `cell.setCellValue` outside writer |
| R3 | Salary data in logs | High (privacy / regulatory) | `SafeLogger` masking; pre-commit secret-scan; audit-time scrubbing |
| R4 | Long-lived signed URLs leaked | Medium | TTL ≤ 60s; download endpoint always re-checks permission |
| R5 | Worker forgets to reload tenant context | High | Base class `TenantAwareJobHandler` enforces reload + context push; lint rule |
| R6 | Idempotency missing → duplicate commits | Medium | Idempotency key required on commit/generate endpoints; DB unique index |
| R7 | Zip bombs / DoS | Medium | Decompression ratio limits; size caps; AV pre-scan |
| R8 | Path traversal in `safeFilename` | Medium | Strict sanitization + path canonicalization check |
| R9 | Queue payload tampering | Low | Payload contains only IDs; worker reloads from DB |
| R10 | Public bucket misconfiguration | High | IaC policy `Deny *`, daily config audit; alarms on public ACLs |
| R11 | Report worker OOM on huge datasets | Medium | Streaming POI (`SXSSFWorkbook`), row limit + pagination |
| R12 | Race between commit and project archival | Low | Commit checks `project.status = ACTIVE`; otherwise BLOCKER |

---

## 22. Backlog — Backend Engineer

- B-INT-01 Implement `ObjectStorageClient` (S3) with tenant/project-prefix guard.
- B-INT-02 Implement `SafeCellWriter` (formula injection sanitization) + lint rule against raw `cell.setCellValue`.
- B-INT-03 Implement `ImportBatch`, `ImportBatchRow`, `ImportError` JPA + repositories.
- B-INT-04 Implement `ExportJob`, `ExportFile`, `FileAttachment` JPA + repositories.
- B-INT-05 Implement `JobRecord`, `JobIdempotency`, `JobDeadLetter` + `JobDispatcher`.
- B-INT-06 Implement `TenantAwareJobHandler` base class with reload + permission re-check.
- B-INT-07 Implement upload endpoint (multipart, MIME sniff via Tika, AV hook, size cap, quarantine→namespace move).
- B-INT-08 Implement signed download endpoint (TTL ≤ 60s; permission check).
- B-INT-09 Implement `ValidationFramework` (`Validator<T>`, `ValidationResult`, ordered chains).
- B-INT-10 Implement 10 Excel import parsers (Apache POI streaming) + 10 export writers.
- B-INT-11 Implement `grading-import-worker`, `grading-export-worker`, `grading-report-worker` Spring components.
- B-INT-12 Wire PDF (JasperReports/OpenPDF) and DOCX (docx4j) renderers.
- B-INT-13 Audit emitters for all 25+ events.
- B-INT-14 `SafeLogger` masking for salary/compensation fields.
- B-INT-15 Idempotency key support on commit/export/report endpoints.

## 23. Backlog — Frontend Engineer

- F-INT-01 Upload wizard (4 steps).
- F-INT-02 Validation preview table with severity filter and side drawer.
- F-INT-03 Column-mapping screen.
- F-INT-04 Commit confirmation with salary warning when applicable.
- F-INT-05 Export center with status chips, filters, salary warning modal.
- F-INT-06 Report Center page (list + request + download).
- F-INT-07 Download flow: always call `/download` endpoint, never cache signed URL.
- F-INT-08 Error report download + per-row anchor links.
- F-INT-09 Localized labels (4 languages) for template names, error codes, suggested fixes.

## 24. Backlog — QA Engineer

- Q-INT-01 Test pack: invalid file type / missing column / invalid value / duplicate key (all 10 templates).
- Q-INT-02 Formula injection: assert `=CMD()` in source becomes `'=CMD()` in re-exported cell.
- Q-INT-03 Cross-tenant reference: row referencing another tenant's external_id → BLOCKER.
- Q-INT-04 Malware: EICAR test file → reject + audit.
- Q-INT-05 Salary import without permission → 403 + audit.
- Q-INT-06 Salary export without permission → 403 + audit + no file generated.
- Q-INT-07 Signed URL expiration: wait > TTL → 403/404.
- Q-INT-08 Attachment cross-tenant: URL-forge download attempt → 403 + audit.
- Q-INT-09 Worker idempotency: trigger commit twice → one COMMITTED.
- Q-INT-10 Worker DLQ: force 5 failures → DLQ row + alert.
- Q-INT-11 Zip bomb + password-protected + macro-enabled file rejection.
- Q-INT-12 Tenant isolation proof: export from Tenant A under Tenant B JWT → 403.
- Q-INT-13 Audit completeness: every action produces exactly one audit row with required fields.

## 25. Backlog — DevOps/SRE

- D-INT-01 Provision S3 bucket + KMS key + bucket policy (Deny *) + versioning + lifecycle rules.
- D-INT-02 Deploy 4 worker deployments (HPA on queue depth).
- D-INT-03 Provision queue (Redis Streams / RabbitMQ / Kafka — pick one) + DLQ.
- D-INT-04 Prometheus scrape config + Grafana dashboards (6 dashboards in §19).
- D-INT-05 Alerts: DLQ > 0, scan failures spike, salary export rate anomaly, signed URL issuance spike.
- D-INT-06 AV scanner deployment (clamav or vendor) + freshness monitor.
- D-INT-07 Tracing: trace_id propagation API → worker → DB → audit.
- D-INT-08 Runbooks: import stuck in COMMITTING, DLQ drain, signed URL leak rotation, KMS key rotation.
- D-INT-09 IaC policy tests forbidding public buckets.

## 26. Backlog — Cybersecurity Engineer

- S-INT-01 Review `SafeCellWriter` against OWASP CSV/Excel injection cheatsheet; sign off.
- S-INT-02 Review file upload pipeline: MIME sniffing, allowlist, AV, zip-bomb, password/macro reject.
- S-INT-03 Review signed URL policy: TTL, HTTP verb scoping, key naming, permission re-check.
- S-INT-04 Review object storage IAM, bucket policy, KMS, lifecycle.
- S-INT-05 Penetration test: cross-tenant export, URL forgery, formula injection roundtrip, path traversal in filename.
- S-INT-06 Salary log scrub verification (`SafeLogger`).
- S-INT-07 Threat model for `grading-integration-worker` (MVP 4 readiness).
- S-INT-08 Sign off GO/NO-GO for MVP 2 release on integration surface.

---

## 27. Cross-Agent Dependencies (summary)

| Need | Owner |
|---|---|
| ImportBatch / ExportJob / FileAttachment DDL + indexes + tenant isolation tests | **database-architect** |
| File security, formula injection, signed URL policy, salary masking review | **security-engineer** |
| Worker deployments, queue, DLQ, dashboards, alerts, runbooks | **devops-sre** |
| Excel parsers/writers, validation framework, API endpoints, audit emitters | **backend-engineer** |
| Upload wizard, validation preview, export center, signed download flow | **frontend-engineer** |
| Negative test pack (invalid file/missing column/formula/cross-tenant/malware/salary/signed URL) | **qa-engineer** |

---

*End of blueprint.*
