SECURITY REVIEW — grading.hrlab.uz | (1) Approval name/label enrichment isolation + (2) Multi-evaluator/CEO design. Design-only; no code in this round. Grounded in live code: MethodologyActorNameResolver.java, ApprovalQueries.java, ApprovalController.java, EvaluationQueries.java (listByFactor), EvaluationRepository.java (findForFactorGrid), UserRepository.java, AbacGate.java, AuditAction.java.

==================================================================
REVIEW 1 — APPROVAL ENRICHMENT (initiator/approver NAMES + entity labels)
==================================================================

CONTEXT VERIFIED
- ApprovalRequestResponse today returns RAW UUIDs only (requestedBy, entityId, steps[].approverUserId) — no names, no labels. The enrichment is NET-NEW. Owner Item 1 requires: initiator NAME + human entity LABEL (evaluation -> position title + methodology name; methodology version -> name+v; grade structure -> name; job profile -> title; project -> name).
- MethodologyActorNameResolver.resolve(userId) does `users.findById(userId).getFullName()` against public.users. CRITICAL PROPERTY: public.users is the control-plane PII table and has NO tenant_id. It is a cross-tenant directory primitive. It performs NO membership check.

FINDING 1A — Resolver is a cross-tenant directory primitive
- Severity: Medium (becomes High if ever fed an attacker-influenced UUID).
- Risk: If the enrichment ever resolves a UUID that is not already proven to belong to the active tenant (e.g. a future "resolve arbitrary user" endpoint, batch resolver fed client-supplied IDs, or a fallback that resolves steps[].approverUserId without re-checking the row's tenant), it leaks the existence + full name of users from OTHER company-clients.
- Why safe TODAY but must be locked: in the approval path the UUIDs (requestedBy, approverUserId, decidedBy) originate from approval_request / approval_step rows that are ALWAYS loaded via findByIdAndTenantId (ApprovalController.getById, ApprovalQueries.hydrate). So the IDs are tenant-derived, not client-derived. That invariant MUST be preserved and made explicit.

RULES THE BE ENGINEER MUST FOLLOW (Review 1):
R1 (tenant-derived IDs only). Names are resolved ONLY for UUIDs that came out of a row already loaded with findByIdAndTenantId for the active tenant (requestedBy, steps[].approverUserId, steps[].decidedBy). NEVER resolve a UUID taken from the request body / query string / path beyond {id} itself.
R2 (no raw directory exposure). Do NOT add any endpoint or response field that maps an arbitrary user UUID -> name. Resolution is an enrichment of already-authorized approval rows, not a lookup service.
R3 (batch resolver stays tenant-scoped). If a batch name resolver is introduced for the inbox list (to avoid N+1), it MUST take the set of UUIDs harvested from already-tenant-loaded approval rows. Prefer resolving via a tenant-scoped join (user_tenant_memberships JOIN users WHERE membership.tenant_id = activeTenant) so a stale/cross-tenant UUID resolves to null, not a foreign name. Do NOT pass a free UUID set straight to users.findAllById.
R4 (membership-aware fallback). When resolving requestedBy/approver: if the user has NO active membership in the active tenant (left the tenant, moved tenants), resolve to a neutral label (e.g. "Former user" / localized) rather than their live full name from another tenant context. Reuse the existing null -> "Unknown actor" FE fallback pattern; do not invent a new directory.
R5 (entity label = read-authorized only). The entity label (position title, methodology name, grade structure name, job profile title, project name) MUST be produced ONLY for entities the CALLER is allowed to read, resolved tenant-scoped:
   - Resolve via findByIdAndTenantId for the entityType+entityId on the approval row (NOT a generic getById).
   - Apply the SAME ABAC read gate the entity's own read path uses. For EVALUATION this means resolving the underlying Position and calling AbacGate.enforceCanReadPosition (exactly what EvaluationQueries.enforceReadScope does) — a department-scoped approver who can act on the step but is outside the position's subtree must NOT receive the position title; degrade to a generic label ("Evaluation") not a 404 of the whole inbox.
   - Label resolution must be FAIL-SOFT for the list (one unresolvable/forbidden label degrades that one card to a generic type label; it must NOT 500 the whole inbox — see Review-1 robustness note below).
R6 (label is i18n, 4 locales). Labels must respect ctx.locale() with the existing ru-RU fallback chain (pickLocalized pattern). Do not hardcode one language.
R7 (no sensitive data in label). Labels are titles/names/version numbers ONLY. NEVER embed salary, scores, grade numbers, totals, comments, or any sensitive field into the label string. (Grade STRUCTURE name is fine; an assigned grade VALUE is not.)
R8 (snake_case wire + NON_NULL). New fields follow the SNAKE_CASE wire contract and JsonInclude.NON_NULL already on ApprovalRequestResponse; add golden-file wire tests (initiator_name, entity_label_i18n or resolved entity_label, plus per-step approver_name/decided_by_name). This also prevents the Item-2 contract-drift class of bug on this very response.
R9 (no PII in logs). Resolved names/labels must never be written to application logs or audit before/after JSON. The enrichment is presentation-only.
R10 (robustness — Item 2 root cause class). The enrichment must not throw on a missing/forbidden referent. A null name -> FE "Unknown"; a forbidden/missing entity -> generic type label. The /approvals/{id} "Хатолик юз берди" crash is the failure mode to design against: every resolve call is wrapped so one bad referent degrades gracefully instead of bubbling a 500.

ACCEPTANCE CRITERIA (Review 1):
- AC1: Tenant A approver opening their inbox/detail sees Tenant A initiator names + Tenant A entity labels only; no Tenant B name or label is ever returned (cross-tenant test).
- AC2: A user UUID present on a Tenant A approval row but with no active Tenant A membership resolves to a neutral label, not a live name pulled from another tenant.
- AC3: A department-scoped approver gets the step (can act) but, when outside the entity's dept subtree, receives a generic type label, never the position title — and the request still succeeds (no 404/500 of the inbox).
- AC4: No new endpoint maps arbitrary userId -> name.
- AC5: Golden-file wire test asserts snake_case keys and that forbidden/missing referents degrade rather than 500.
- AC6: Grep proves no name/label is logged.

TEST CASES (Review 1):
- T1 cross-tenant: seed approval in Tenant B; Tenant A user GET /approval-requests/{B-id} -> 404 (existing findByIdAndTenantId), enrichment never runs.
- T2 stale membership: requestedBy is a user who left Tenant A -> neutral label, not live name.
- T3 dept-scope label: approver outside subtree -> generic "Evaluation" label, step still actionable.
- T4 robustness: entityId points to an archived/deleted entity -> generic label, HTTP 200, no 500.
- T5 i18n: each of ru-RU / uz-Cyrl-UZ / uz-Latn-UZ / en-US returns the right localized label with fallback.
- T6 no-PII-in-log assertion.

==================================================================
REVIEW 2 — MULTI-EVALUATOR / CEO DESIGN (Item 3, PROPOSAL ONLY)
==================================================================

Both the DB proposal (evaluation_panels) and the UX proposal (evaluation_campaign) describe the SAME aggregate under different names. SECURITY VERDICT: the aggregate-above-evaluations approach is sound and is the SAFER of the two for isolation, BUT three security properties are under-specified and MUST be hardened before ratification. The single naming term must be chosen by PO; below I use "panel/campaign" interchangeably.

CRITICAL PRE-EXISTING GAP THE DESIGN MUST CLOSE
- Today EvaluationQueries.listByFactor / EvaluationRepository.findForFactorGrid return scores for ALL evaluations in (tenant, project, methodology_version) gated ONLY by EVALUATION_READ + department scope. There is NO per-evaluator predicate. With one evaluator per position this was acceptable; the moment N evaluators share a position, this read path LEAKS evaluator B's scores to evaluator A. The UX proposal acknowledges this ("+ AND evaluator_user_id = :me") but it must be an enforced server-side requirement, not a UI affordance.

--- A. INDEPENDENT-SCORING ISOLATION (the "blind" rule) — ENFORCEMENT LAYER ---
REQ-ISO-1 (enforcement is DATA-LAYER, not UI). The blind rule MUST be enforced in the read query/service layer (the same place EVALUATION_READ + AbacGate are enforced), NOT by hiding columns in React. The owner proposals' info-banner and ScorePreviewBanner are UX only; they are NOT a control. The bias rule = a server-side read predicate.
REQ-ISO-2 (own-sheet predicate while collecting). While panel.status in {SETUP/COLLECTING, IN_PROGRESS, AWAITING_EVALUATIONS}: any read of per-evaluator scores (single-id reads in EvaluationQueries.findScoresByEvaluationId AND the grid in listByFactor/findForFactorGrid AND findById) MUST add `AND evaluation.evaluator_user_id = :currentUserId` for callers who do NOT hold the new cross-evaluator read permission. This must be in BOTH grid queries (findForFactorGrid and findForFactorGridInDepartments) and the single-id paths — not just the grid.
REQ-ISO-3 (new permission, deny-by-default). Introduce CAMPAIGN_RESULTS_VIEW (a.k.a. PANEL_RESULTS_VIEW). Plain evaluators do NOT hold it. Only result-viewers (HR director / PM / CEO) hold it. Mirror the existing canSeePoints = can(CALIBRATION_EDIT) anchoring-bias pattern, but as a distinct code. EVALUATION_READ alone MUST NOT lift the blind. Deny-by-default: absence of the permission = own-sheet-only.
REQ-ISO-4 (no IDOR around the blind). An evaluator must not bypass the blind by direct evaluation UUID: findScoresByEvaluationId(otherEvaluatorsEvaluationId) and findById must return 404/empty when the requester is not the owner and lacks CAMPAIGN_RESULTS_VIEW and the panel is still collecting. The tenant + dept gate already there is NOT sufficient — add the evaluator-ownership gate.
REQ-ISO-5 (averages also gated while collecting). panel_factor_averages / averaged totals MUST NOT be readable until panel.status reaches ALL_COMPLETE/AVERAGED, and even then only by CAMPAIGN_RESULTS_VIEW holders. No "live running average" endpoint while collecting (that would leak peers' aggregate).
REQ-ISO-6 (blind lifts only on completion). When panel reaches ALL_COMPLETE/AVERAGED, the per-evaluator breakdown becomes visible to CAMPAIGN_RESULTS_VIEW holders only; a plain evaluator still sees only their own sheet.
REQ-ISO-7 (assignment authority). Only an authorized assigner (existing EVALUATION_EDIT/admin) can create the panel and assign evaluators; an evaluator cannot self-assign to a panel nor add others. panel_assignments writes are permission-gated and audited.

--- B. AVERAGING INTEGRITY (server-side only) ---
REQ-AVG-1 (server-computed, never client-submitted). averaged_raw_total, displayed_total, and per-factor averages MUST be computed exclusively by the new ComputeCampaignAverageService server-side. The API MUST NOT accept any averaged value, evaluator_count, or per-factor average from the client (mass-assignment guard — DTO must reject these as unknown/ignored fields).
REQ-AVG-2 (reuse the pure engine — no duplication, Item 4). Averaging composes ON TOP of the existing EvaluationScoringEngine pure function (per sheet) then means the results. Do NOT re-implement scoring inside the averager. Rounding contract (NUMERIC HALF_UP) must match the engine exactly; store both per-factor averages and total for reproducibility/audit.
REQ-AVG-3 (denominator integrity). evaluator_count = number of sheets that actually COMPLETED and contributed. Withdrawn/incomplete sheets MUST be excluded deterministically. The denominator is server-derived from assignment/sheet state, never client-supplied. Define explicitly: average over COMPLETED sheets only.
REQ-AVG-4 (compute only after completion + immutability). The average is computed only when the LAST assigned (active) evaluator's sheet is COMPLETE/SUBMITTED. Once AVERAGED and sent to CEO, the contributing sheets MUST be locked (no late edit silently changing an already-CEO-submitted average). Reuse the existing EvaluationImmutabilityPolicy; a post-average edit requires an explicit reopen that resets panel to collecting, re-computes, and re-audits — never a silent recompute behind the CEO's back.
REQ-AVG-5 (min-evaluators enforced server-side). The "minimum 3 + one of each mandatory role" rule is enforced in the application layer on the SETUP/COLLECTING -> IN_PROGRESS transition AND re-checked before averaging/CEO submit (both proposals correctly reject a DB trigger). min_evaluators is data-driven (stored, default 3, CHECK >=1; never below the product floor of 3 for the mandatory-role set). A panel with <3 completed sheets MUST NOT produce an average or reach CEO.
REQ-AVG-6 (tenant/project scoping of the average). All averaging queries are tenant + project + panel scoped (defense in depth: tenant_id on evaluation_panels, panel_assignments, panel_factor_averages as both proposals specify). The averager MUST load contributing sheets via tenant-scoped finders only (no findById on raw evaluations).

--- C. CEO-STEP AUTHORITY ---
REQ-CEO-1 (reuse approval-request workflow, no parallel path). The CEO sign-off MUST be a step in the existing approval-request workflow (ApprovalEntityType + ApprovalStep, gated by requiredPermission), NOT a new bespoke approval mechanism (Item 4 no-duplication). Add a new ApprovalEntityType value for the panel (e.g. EVALUATION_PANEL / EVALUATION_CAMPAIGN) so the existing inbox/detail + the Review-1 label resolver render it.
REQ-CEO-2 (distinct CEO permission). The CEO step's requiredPermission MUST be a dedicated authority (e.g. EVALUATION_PANEL_APPROVE / CEO sign-off code), distinct from the per-sheet EVALUATION_APPROVE. A regular evaluation approver MUST NOT be able to satisfy the CEO step. Deny-by-default.
REQ-CEO-3 (approval target = the panel, not a sheet). The approval request entityId is the panel id; grade assignment on CEO approval reads the panel's averaged raw_total (reuse EvaluationGradeAssignmentService unchanged — no new grade logic). The CEO approves the AVERAGED result, not any single evaluator's sheet.
REQ-CEO-4 (separation of duties). Design SHOULD support (and PO should rule on) preventing the same user from being both a contributing evaluator AND the CEO approver on the same panel. At minimum, audit when actor overlap occurs. Tenant + step authority still strictly enforced.
REQ-CEO-5 (no CEO step until preconditions met). The CEO approval request can only be OPENED when panel.status = AVERAGED (all assigned complete, min-evaluators satisfied, average computed). Submitting to CEO earlier MUST be rejected server-side.
REQ-CEO-6 (entity-label for CEO inbox respects Review-1 rules). The CEO's inbox card label for the panel = position title + methodology name, resolved tenant-scoped and read-authorized per Review-1 R5/R7 (no scores/grade value in the label).

--- D. AUDIT EVENTS NEEDED (append-only, redacted, reuse AuditService) ---
New AuditAction codes required (none exist yet for panels):
   - EVALUATION_PANEL_CREATED
   - EVALUATION_PANEL_EVALUATOR_ASSIGNED / EVALUATION_PANEL_EVALUATOR_WITHDRAWN (with evaluator_user_id + role)
   - EVALUATION_PANEL_ROSTER_LOCKED (SETUP->IN_PROGRESS, records min-evaluators + roles satisfied)
   - EVALUATION_PANEL_EVALUATOR_COMPLETED (per sheet completion contributing to the panel)
   - EVALUATION_PANEL_AVERAGED (records evaluator_count + computed total; before/after redacted; total is a derived grade-input, store it but treat as sensitive-adjacent)
   - EVALUATION_PANEL_SUBMITTED_TO_CEO (links to approval_request id)
   - EVALUATION_PANEL_APPROVED / _REJECTED / _CHANGES_REQUESTED by CEO (reuse APPROVAL_STEP_* on the underlying request AND a panel-level outcome row)
   - EVALUATION_PANEL_REOPENED (post-average edit -> recompute path, REQ-AVG-4)
   - Reuse existing GRADE_ASSIGNED on CEO approval (panel total -> band).
AUDIT RULES:
   - REQ-AUD-1: every audit row carries tenant_id, project_id, actor_user_id, panel_id (entity_id), correlation/trace id; append-only (no update/delete) per the existing AuditService contract.
   - REQ-AUD-2: a blind-bypass attempt (evaluator tries to read a peer's sheet while collecting) MUST emit ACCESS_DENIED_BY_ABAC (or a new EVALUATION_PEER_SCORE_ACCESS_DENIED) — reuse AbacGate denial audit so SIEM can alert.
   - REQ-AUD-3: averaging audit before/after JSON must NOT embed raw per-evaluator comments; store counts + totals only (redaction discipline).

CROSS-CUTTING RELEASE GATES (Item 3 implementation, when ratified)
- GATE-1: blind-rule test pack green — evaluator A cannot read evaluator B's scores via grid, single-id, or averages endpoint while collecting (all three paths).
- GATE-2: server-only averaging proven — API rejects client-submitted average/count; recompute is deterministic and matches engine rounding.
- GATE-3: min-evaluators (>=3 + mandatory roles) cannot be bypassed; no average/CEO submit below floor.
- GATE-4: CEO step authority distinct from per-sheet approve; CEO approves panel not sheet; cannot open CEO step before AVERAGED.
- GATE-5: tenant isolation across panel/assignment/average tables (Tenant A cannot read Tenant B panel/averages by direct UUID).
- GATE-6: full audit trail present for the lifecycle (created -> assigned -> averaged -> CEO -> graded), append-only, redacted.
- GATE-7: existing single-evaluation prod rows migrate to 1-sheet panels with no behavior change (backward compatible).

TEST CASES (Review 2):
- TC-ISO-1: evaluator A GET grid by factor while panel IN_PROGRESS -> only A's row/score; B's score absent.
- TC-ISO-2: evaluator A GET /evaluations/{B-sheet-id}/scores while collecting -> 404/empty + denial audit.
- TC-ISO-3: averages endpoint while collecting -> 403/empty for everyone; after AVERAGED -> visible only to CAMPAIGN_RESULTS_VIEW.
- TC-ISO-4: EVALUATION_READ-only user never sees peers' scores (permission alone does not lift blind).
- TC-AVG-1: POST/PATCH attempting to set averaged_total/evaluator_count -> field ignored/rejected (mass-assignment).
- TC-AVG-2: average over 3 completed sheets matches mean(engine results) HALF_UP; withdrawn sheet excluded from denominator.
- TC-AVG-3: panel with 2 completed sheets cannot reach AVERAGED/CEO.
- TC-CEO-1: per-sheet EVALUATION_APPROVE holder cannot satisfy the CEO panel step.
- TC-CEO-2: CEO step cannot open before status=AVERAGED.
- TC-CEO-3: CEO approval assigns grade from panel averaged total (reuses EvaluationGradeAssignmentService).
- TC-TEN-1: Tenant A cannot read/act on Tenant B panel/assignment/average by direct UUID.
- TC-AUD-1: each lifecycle transition emits the expected append-only audit row with tenant_id+project_id+panel_id; blind-bypass emits a denial row.

RISKS IF NOT ENFORCED
- R-CRIT-1 (Critical): evaluator-bias / score leakage — peers' scores visible pre-completion via the existing un-evaluator-scoped K-sheet grid. This is the #1 issue and exists in current code the moment N evaluators are allowed.
- R-CRIT-2 (Critical): integrity loss — client-tampered average -> wrong grade -> wrong salary band. Server-only averaging closes it.
- R-HIGH-1 (High): authority confusion — per-sheet approver acting as CEO. Distinct permission closes it.
- R-HIGH-2 (High): silent recompute after CEO submit invalidating the approved result. Reopen+re-audit closes it.
- R-MED-1 (Medium): min-evaluators bypass producing an average from <3 evaluators. App-layer gate closes it.

=== O'ZBEKCHA QISQACHA XULOSA ===
1-REVIEW (tasdiqlash ekranida ism + label). Hozir javobda faqat UUID bor; ism va label qo'shilishi YANGI. MUHIM: MethodologyActorNameResolver public.users (tenant_id YO'Q) jadvalidan to'g'ridan-to'g'ri o'qiydi — bu cross-tenant direktoriya. Qoidalar: (a) ism faqat allaqachon findByIdAndTenantId orqali tenant uchun yuklangan approval qatorlaridagi UUID'lar uchun resolve qilinadi, client yuborgan UUID uchun EMAS; (b) tenant ichida membership orqali resolve qiling — boshqa tenant foydalanuvchisi null/neutral label bo'lsin; (c) entity label faqat chaqiruvchi O'QIY OLADIGAN obyekt uchun, o'sha obyektning ABAC read gate'i bilan (evaluation uchun Position dept-scope tekshiruvi); (d) label ichida maosh/ball/grade qiymati BO'LMASIN; (e) snake_case + golden-file test; (f) /approvals/{id} dagi "Xatolik" xatosiga qarshi: bitta yomon havola butun sahifani 500 qilmasin, generic label'ga tushsin.

3-REVIEW (ko'p baholovchi + CEO). Ikkala taklif (panels/campaign) xavfsizlik jihatidan to'g'ri yo'nalish, ammo 3 narsa qattiqlashtirilishi shart: (A) MUSTAQIL BAHOLASH IZOLYATSIYASI — baholovchi A baholovchi B ballarini tugaguncha KO'RMASLIGI server read qatlamida (so'rovga `evaluator_user_id = :men` prediкati) majburlanishi kerak, UI'da emas. Hozirgi K-sheet grid (findForFactorGrid) HAMMA baholarni qaytaradi — bu N baholovchi bo'lsa darhol sizib chiqadi; CAMPAIGN_RESULTS_VIEW yangi ruxsati kerak, EVALUATION_READ yetarli emas. (B) O'RTACHA HISOB — faqat serverda (ComputeCampaignAverageService), client o'rtacha/sonni yubora olmasin (mass-assignment); mavjud EvaluationScoringEngine ustiga quriladi, dublikat yo'q; minimum 3 baholovchi (har roldan bittadan) app qatlamida tekshiriladi; o'rtacha faqat hammasi tugagach. (C) CEO QADAMI — mavjud approval-request workflow'ining bir qadami sifatida, alohida CEO ruxsati bilan (per-sheet EVALUATION_APPROVE'dan farqli), CEO panelni (o'rtacha natijani) tasdiqlaydi, grade panel o'rtachasidan beriladi. AUDIT: panel created/assigned/averaged/CEO approved/reopened uchun yangi append-only audit kodlari + blind-bypass urinishi uchun denial audit. Bu round faqat TAKLIF — egasi tasdiqlamaguncha implementatsiya yo'q.