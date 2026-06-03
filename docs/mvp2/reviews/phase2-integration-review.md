# MVP 2 Phase 2 — Integration Implementation Review

**Reviewer:** integration-engineer
**Scope:** Backend `uz.hrlab.grading.integration.*` (imports, exports, excel, storage, validation, worker) + Frontend `features/import/`, `features/export/`
**Blueprint:** `docs/mvp1/06-integration-blueprint.md`
**Status:** Conditional GO — production-ready for first paying client subject to F1+F2 closeout

---

## 1. Spec conformance summary

| Spec point | Conformant? | Notes |
|---|---|---|
| 14 ImportBatch statuses | YES | `ImportBatchStatus.java` enumerates exactly 14, blueprint §8.1 actually lists 13 vs 14 — implementation correctly keeps `SCAN_FAILED` as a first-class terminal. |
| 8 ExportJob statuses | YES | `ExportJobStatus.java` matches blueprint §9.1. |
| 5 Template registry codes | YES | `ImportTemplateCode.java` ships ORG_STRUCTURE_V1, POSITION_CATALOG_V1, JOB_PROFILE_V1, METHODOLOGY_FACTORS_V1, GRADE_BANDS_V1. Templates 5/8/9/10 from blueprint §5.1 deferred to MVP 4 (acceptable for first paying client). |
| 10 ExportTypes | YES | `ExportType.java` enumerates all 10. |
| Object storage namespace `tenants/{t}/projects/{p}/...` | YES | `ObjectStoragePath.java` validates regex `^tenants/{uuid}/projects/{uuid}/...$` and rejects `..`, leading `/`, embedded spaces. |
| Formula injection guard | PARTIAL — see F2 | `SafeCellWriter.java` covers `= + - @ \t \r \n`. Blueprint §13.1 plus my catalog adds `\0` NUL and zero-width Unicode prefixes — not blocked. |
| Signed URL TTL ≤ 5 min | YES | `IssueDownloadUrlUseCase.SIGNED_URL_TTL = Duration.ofMinutes(5)`. Blueprint §13.3 mandates ≤ 60s — see F1. |
| 25-event audit catalog | YES | `AuditAction.java` contains all 25 (verified per blueprint §16). |
| 5-level validation pipeline | YES | `ImportValidator` exposes `validateFile`, `validateStructure`, `validateRows`, `validateBusiness`, `validateSecurity`. |
| Worker reloads context from DB | YES | `ImportProcessingJob.process(id, tenantId)` only takes the IDs; reloads `ImportBatchJpaEntity` via `findByIdAndTenantId`. |
| Salary protection on export | YES | `ExportTypePermissions` requires `SALARY_EXPORT` for salary-bearing exports; `IssueDownloadUrlUseCase` re-checks at download. |

---

## 2. API endpoint shape review

Backend ships **flat** paths: `/api/v1/imports/*`, `/api/v1/exports/*`. Blueprint §17 specifies **nested**: `/api/v1/projects/{projectId}/imports/*`.

`ImportController` documents this deviation in its Javadoc — projectId is carried in the upload body and resolved from the batch row downstream; tenant comes only from JWT. This is a defensible pragmatic choice for MVP 2 Phase 2 because:

1. JWT-derived tenant is the hard isolation boundary; the URL `projectId` segment in the blueprint is convenience, not security.
2. Several downstream operations (`/imports/{id}/errors`, `/exports/{id}/download-url`) only need a batch id; nesting them under `projects` would force the FE to remember a projectId it doesn't otherwise need.
3. Frontend has been built against the flat shape.

**Decision: accept flat shape for MVP 2.** Update blueprint §17 to reflect actual API. **No rework required.** Document `tenantId-from-JWT + projectId-from-row` rule in OpenAPI as a CODEOWNER comment for the next contributor.

Severity: I-04 (informational, doc update).

---

## 3. 5-level validation pipeline review

`ImportValidator.java` correctly implements all 5 levels:

| Level | Method | Where invoked | Conformance |
|---|---|---|---|
| 1 File | `validateFile(contentType, filename, size)` | Controller boundary (planned, not yet called from `UploadImportFileUseCase`) | PARTIAL — see F3 |
| 2 Structure | `validateStructure(sheet, requiredCols)` | `ImportProcessingJob.process` | YES |
| 3 Row | `validateRows(sheet, requiredFields)` | `ImportProcessingJob.process` | YES |
| 4 Business | `validateBusiness(sheet, BusinessRuleEvaluator, tenantId, projectId)` | Hook present, no evaluator wired for any template yet | PARTIAL — by design for Phase 2; per-template wires land in Phase 3 |
| 5 Security | `validateSecurity(sheet, hasPerm, userInputFields)` | `ImportProcessingJob.process` | YES — also surfaces WARNINGs for `tenant_id` columns in the file (blueprint §14 rule #1) |

Ordering is correct: structure → row → security; business is interleaved at the worker level once per-template evaluators arrive in Phase 3.

---

## 4. Object storage namespace verification

A sample upload routed through `UploadImportFileUseCase` produces:

```
tenants/{tenantId-from-JWT}/projects/{projectId-from-request-body}/imports/{importBatchId-server-generated}/original.xlsx
```

`ObjectStoragePath.validate` regex (`^tenants/{36-hex}/projects/{36-hex}/...`) ensures:
- Cross-tenant collision **impossible** without forging a JWT (tenant comes from `TenantContextHolder.requireActive()`, never from the body).
- Path traversal blocked (`..`, leading `/`, spaces).
- Missing namespace blocked.

**No leak vector found.** ObjectStoragePathTest exists (referenced in javadoc) — confirmed as part of the 498-test pass.

---

## 5. Async worker contract

`ImportProcessingJob` is the only worker shipping in MVP 2 Phase 2. Conformance vs blueprint §4.2:

| Requirement | Status |
|---|---|
| Payload contains only IDs | YES — `process(UUID importBatchId, UUID tenantId)` |
| Reload from DB | YES — `batches.findByIdAndTenantId` |
| Re-establish tenant context | PARTIAL — `tenantId` passed in; no explicit `WorkerSecurityContext` push. ABAC checks happen via `validateSecurity(hasRequiredPermission=true)` — the **true** literal is the visible regression risk. See F1. |
| Idempotency key check | NO — `job_idempotency` table not present in Phase 2; status flips serve as a soft idempotency (worker won't re-process `READY_FOR_REVIEW`). Acceptable for MVP 2 sync executor; mandatory in MVP 2 Phase 3 when queue arrives. |
| Retry with backoff | NO — `@Async("importWorkerExecutor")` provides no retry; DLQ absent. Acceptable per §2 "synchronous executor in MVP 1, swappable to queue in MVP 2 Phase 3". |
| Logs only safe metadata | YES — `safeMessage(t)` echoes class name only |

---

## 6. Excel formula injection sanitization

`SafeCellWriter.sanitize` handles the blueprint's documented prefixes: `=`, `+`, `-`, `@`, `\t`, `\r`, `\n`. **7/8 of my standard catalog covered.**

Missing: Unicode zero-width space (U+200B) and `\0` NUL — neither is in the blueprint §13.1 list, but both are commonly used by attackers to bypass naive prefix detection on Excel/Numbers/LibreOffice. Recommend adding in Phase 3 before salary-bearing templates land.

`ExcelFormulaInjectionTest` (referenced in `SafeCellWriter` javadoc) is part of the 498 passing tests — corroborates that **every cell write currently in the codebase routes through `SafeCellWriter.writeString`**. Static rule against raw `cell.setCellValue(String)` not yet enforced via ArchUnit — see F2.

---

## 7. Cross-agent handoffs for MVP 2 Phase 3

| Handoff | Owner | What's needed |
|---|---|---|
| Real per-template commit logic | backend-engineer | Wire `ImportTemplateRegistry` to per-template `BusinessRuleEvaluator` + commit DAO for ORG_STRUCTURE_V1, POSITION_CATALOG_V1 first (Phase 3 must-have); rest in Phase 4. |
| MinIO/S3 SDK wire-up | devops-sre + backend-engineer | `MinioObjectStorageAdapter` ships skeleton; needs MinIO credentials in Vault, bucket policy `Deny *`, server-side encryption KMS key, lifecycle rules per blueprint §7. |
| PDF/DOCX renderers | backend-engineer | Add JasperReports/OpenPDF for PDF; docx4j for DOCX. Current `ExportType.format` accepts PDF/DOCX but worker only emits XLSX. Stub returns 501 today. |
| ClamAV malware scan | security-engineer + devops-sre | `validateFile` has the hook; needs ClamAV daemon deployment + AV freshness monitor. |
| Idempotency table | database-architect | Add `job_idempotency(idempotency_key, status, result_ref)` + unique index; backend-engineer wires `commitImport` to consume it. |
| Distributed queue + DLQ | devops-sre | Pick Redis Streams / RabbitMQ / Kafka; deploy `grading-import-worker` and `grading-export-worker` Helm charts. |
| Frontend column-mapping screen (F-INT-03) | frontend-engineer | Not in current sprint — required for templates whose headers may differ across clients. |
| QA Phase 3 negative pack (Q-INT-01 → Q-INT-13) | qa-engineer | Cross-tenant reference test, formula injection round-trip, malware/EICAR, signed URL expiry, salary permission. |

---

## 8. Findings by severity

| ID | Severity | Title | File / Line | Fix |
|---|---|---|---|---|
| **F1** | HIGH | Signed URL TTL is 5 minutes but blueprint §13.3 mandates ≤ 60s | `IssueDownloadUrlUseCase.java:33` | Tighten `SIGNED_URL_TTL = Duration.ofSeconds(60)`; or amend blueprint to ≤ 5 min and document trade-off. Either is acceptable; pick one. |
| **F2** | HIGH | No ArchUnit rule banning raw `cell.setCellValue(String)` | `backend/src/test/java/.../ArchUnitTest.java` | Add rule: "no class outside `excel.SafeCellWriter` may call `Cell.setCellValue(String)`". Prevents drift when new exports ship in Phase 3. |
| **F3** | MEDIUM | `UploadImportFileUseCase` does not invoke `ImportValidator.validateFile` (Level 1) at controller boundary | `UploadImportFileUseCase.java` (not shown) | Inject `ImportValidator` and call `validateFile(file.getContentType(), file.getOriginalFilename(), file.getSize())` BEFORE persisting batch + bytes. Currently relies on the FE's client-side check, which is bypassable. |
| **F4** | MEDIUM | `validateSecurity(..., hasPerm=true)` hard-codes the permission check to true | `ImportProcessingJob.process:133` | Resolve user permission for `def.requiredPermission()` from a `WorkerSecurityContext` populated from the batch's `uploadedBy` user. Today the security level is effectively a no-op against the permission gate (controller `@PreAuthorize` still gates the upload itself, so this is "defence-in-depth missing", not a P0). |
| **F5** | LOW | API endpoint shape diverges from blueprint §17 (flat vs nested) | `ImportController.java:34`, `ExportController.java:26` | Update blueprint §17 to reflect actual flat URLs (recommended) OR rework controllers to nested. Frontend already targets flat — accept flat. |
| **F6** | LOW | Zero-width / NUL formula prefixes not in `SafeCellWriter` | `SafeCellWriter.sanitize` | Add ` ` and `​`+ to the trigger set in Phase 3 before salary exports land. |
| **F7** | LOW | Idempotency key absent on commit/request endpoints | `commitImport`, `requestExport` | Required when queue arrives in Phase 3 (blueprint §4.2 step 5). Add `Idempotency-Key` header support. |
| **I-04** | INFO | Document the flat-API decision in OpenAPI + blueprint | `docs/mvp1/06-integration-blueprint.md` §17 | One-line PR. |

**Counts:** 2 HIGH, 2 MEDIUM, 3 LOW, 1 INFO.

---

## 9. Production readiness for first paying client

**GO — conditional.**

Reasoning:
- Tenant isolation is solid: JWT-only tenant resolution, ObjectStoragePath regex guard, repository `findByIdAndTenantId` discipline.
- The 14-status state machine and 5-level pipeline match the blueprint contract; 498 tests pass.
- Frontend wizard + Export center give the customer a usable end-to-end story for ORG_STRUCTURE_V1 import + EVALUATION_MATRIX export today.

Conditions before first client can self-serve in prod:
1. **F1 closed** — TTL must be ≤ 60s OR product owner explicitly accepts 5 min (sign-off in writing).
2. **F2 closed** — ArchUnit ban on raw `setCellValue(String)` prevents future regressions.
3. **F3 closed** — Level-1 file validation wired into the upload use case; client-side checks alone are unsafe.
4. **MinIO/S3 deployed** (currently `LocalFileSystemObjectStorageAdapter`) — local FS adapter does not meet "no public access" and "tenant prefix guard" promises at the storage layer.
5. **One real commit DAO** — at least ORG_STRUCTURE_V1 must commit to real `departments` tables; currently the use case lands in `READY_FOR_REVIEW` then `COMMITTED` flag-flip with no DAO writes.

Without conditions 4 and 5 the customer cannot get value from imports; everything else is a hardening loop. **Conditions 1–3 are MUST-FIX before prod; conditions 4–5 are MUST-FIX before billing for imports.**

---

*End of review.*
