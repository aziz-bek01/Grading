/**
 * Evaluation — REST fetchers (Phase 5).
 *
 * NO tenant identifier is ever sent — backend derives the active tenant
 * from the JWT (security blueprint API-13 / noTenantIdLeak.test.ts).
 *
 * Every mutating endpoint is audited server-side per PRD MVP1-E9-*.
 *
 * `previewScore` is intentionally exposed via {@link useMutation} (NOT
 * useQuery) because the result depends on live UI selections; caching
 * stale preview results would be misleading and there is no
 * server-side state mutation per architecture §15.
 */
import { httpClient } from '@/shared/api/httpClient';
import { pick, type Raw } from '@/shared/api/wireAdapter';
import type { PageEnvelope } from '@/shared/types/common';
import type {
  BulkCreateEvaluationPayload,
  BulkCreateEvaluationResult,
  BulkOperationResult,
  BulkScorePayload,
  BulkSubmitPayload,
  CalibrateScorePayload,
  CalibrationEvent,
  CreateEvaluationPayload,
  Evaluation,
  EvaluationByFactorRow,
  EvaluationListFilters,
  EvaluationScore,
  EvaluationsByFactorFilters,
  PreviewScorePayload,
  ReasonPayload,
  ScoringResult,
  UpsertScorePayload,
} from '../types';

const base = '/evaluations';

export const evaluationKeys = {
  all: ['evaluations'] as const,
  list: (filters: EvaluationListFilters) =>
    ['evaluations', 'list', filters] as const,
  detail: (id: string) => ['evaluations', 'detail', id] as const,
  scores: (id: string) => ['evaluations', 'scores', id] as const,
  calibrationHistory: (id: string) =>
    ['evaluations', 'calibration-history', id] as const,
  /**
   * Tenant-scoped query key for the by-factor bulk view.
   *
   * Follows FE-TI-004: the tenant scope is implicit (JWT-derived on the
   * backend) — we do NOT add tenantId to the key directly, but we DO key
   * by the full filter object so cross-project / cross-factor caches do
   * not collide when the user pivots in the UI.
   */
  byFactor: (filters: EvaluationsByFactorFilters) =>
    ['evaluations', 'by-factor', filters] as const,
};

export async function createEvaluation(
  payload: CreateEvaluationPayload,
): Promise<Evaluation> {
  const res = await httpClient.post<Evaluation>(base, payload);
  return res.data;
}

/**
 * Bulk-create one DRAFT evaluation per row (Item 1, BE-1).
 *
 * Backend contract:
 *   POST /api/v1/evaluations/bulk-create
 *   Body: { items: [{ position_id, methodology_version_id, evaluator_user_id? }] }
 *   (Contract-A snake_case — sending camelCase would null the @NotNull fields → 400.)
 *
 * Always returns HTTP 200, even on partial failure. The response carries
 * `created` (count) + `failed[]` keyed on `position_id` (NOT an evaluation id,
 * since no evaluation was created for a failed row). NO tenant_id on the wire —
 * the backend derives the active tenant from the JWT (security blueprint API-13).
 */
export async function bulkCreateEvaluations(
  payload: BulkCreateEvaluationPayload,
): Promise<BulkCreateEvaluationResult> {
  const res = await httpClient.post<BulkCreateEvaluationResult>(
    `${base}/bulk-create`,
    payload,
  );
  return res.data;
}

/**
 * Hard-delete a DRAFT-only evaluation (Item 1, BE-2).
 *
 * Backend contract:
 *   DELETE /api/v1/evaluations/{id}
 *   Body: { reason }  (ReasonRequest — @NotBlank, min 5, max 4000)
 *   Returns 204 No Content.
 *
 * DRAFT-only: a non-DRAFT evaluation is rejected with 400
 * EVALUATION_NOT_DELETABLE (use the archive path instead). Cross-tenant /
 * out-of-scope id → 404 (no existence reveal). The body travels on a DELETE
 * via the axios `data` config option.
 */
export async function deleteEvaluation(
  id: string,
  payload: ReasonPayload,
): Promise<void> {
  await httpClient.delete(`${base}/${id}`, { data: payload });
}

export async function fetchEvaluations(
  filters: EvaluationListFilters,
): Promise<PageEnvelope<Evaluation>> {
  // projectId, position_id, evaluatorUserId, status are SCOPE filters
  // (not tenant identifiers) — allowed on the wire.
  const res = await httpClient.get<PageEnvelope<Evaluation>>(base, {
    params: {
      projectId: filters.projectId,
      position_id: filters.positionId,
      evaluatorUserId: filters.evaluatorUserId,
      status: filters.status,
      page: filters.page,
      size: filters.size,
    },
  });
  // FE-027: adapt the server-resolved `methodology_version_label` (wire
  // snake_case, already-localized "Name (vN)") into the FE-derived camelCase
  // convenience field {@link Evaluation.methodologyVersionLabel}, mirroring
  // the panelId/evaluatorRole adaptation in {@link fetchEvaluation}. This
  // replaces the old N+1 where EvaluationListPage fired one
  // fetchMethodologyVersions request PER methodology (via useQueries) purely
  // to build this label client-side — the backend now resolves it once per
  // row. `pick` falls back to `null` when the field is absent (an older API
  // response), which callers must handle (see EvaluationListPage).
  // `items` defensively defaults to `[]` — mirrors fetchPanels' envelope
  // guard — so a malformed/empty envelope never throws here.
  const rawItems = Array.isArray(res.data?.items) ? res.data.items : [];
  const items = rawItems.map((row) => {
    const raw = row as unknown as Raw;
    return {
      ...row,
      methodologyVersionLabel: pick<string>(
        raw,
        'methodology_version_label',
        'methodologyVersionLabel',
      ),
    };
  });
  return { ...res.data, items };
}

/**
 * Raw single-sheet wire shape: the EvaluationResponse plus the two panel
 * context fields the BE now adds in snake_case (`panel_id` / `evaluator_role`).
 * `@JsonInclude(NON_NULL)` means both are OMITTED for a legacy single-evaluator
 * sheet, so they are optional here.
 */
type EvaluationWire = Evaluation & {
  panel_id?: string | null;
  evaluator_role?: Evaluation['evaluatorRole'];
};

/**
 * Fetch one evaluation sheet and adapt the BE's snake_case panel-context fields
 * (`panel_id` / `evaluator_role`) into the FE-derived camelCase convenience
 * fields (`panelId` / `evaluatorRole`) the detail page reads. All other fields
 * pass through unchanged (the rest of {@link Evaluation} is raw snake_case wire).
 * For a legacy single-evaluator sheet both fields are absent → mapped to `null`,
 * so the detail page renders no extra panel chrome.
 */
export async function fetchEvaluation(id: string): Promise<Evaluation> {
  const res = await httpClient.get<EvaluationWire>(`${base}/${id}`);
  const { panel_id, evaluator_role, ...rest } = res.data;
  return {
    ...rest,
    panelId: panel_id ?? null,
    evaluatorRole: evaluator_role ?? null,
  };
}

export async function fetchEvaluationScores(
  id: string,
): Promise<EvaluationScore[]> {
  const res = await httpClient.get<EvaluationScore[]>(`${base}/${id}/scores`);
  return res.data;
}

export async function fetchCalibrationHistory(
  id: string,
): Promise<CalibrationEvent[]> {
  const res = await httpClient.get<CalibrationEvent[]>(
    `${base}/${id}/calibration-history`,
  );
  return res.data;
}

export async function upsertScore(
  id: string,
  payload: UpsertScorePayload,
): Promise<EvaluationScore> {
  const res = await httpClient.post<EvaluationScore>(
    `${base}/${id}/scores`,
    payload,
  );
  return res.data;
}

export async function submitEvaluation(id: string): Promise<Evaluation> {
  const res = await httpClient.post<Evaluation>(`${base}/${id}/submit`, {});
  return res.data;
}

export async function approveEvaluation(id: string): Promise<Evaluation> {
  const res = await httpClient.post<Evaluation>(`${base}/${id}/approve`, {});
  return res.data;
}

export async function requestEvaluationChanges(
  id: string,
  payload: ReasonPayload,
): Promise<Evaluation> {
  const res = await httpClient.post<Evaluation>(
    `${base}/${id}/request-changes`,
    payload,
  );
  return res.data;
}

export async function lockEvaluation(id: string): Promise<Evaluation> {
  const res = await httpClient.post<Evaluation>(`${base}/${id}/lock`, {});
  return res.data;
}

export async function archiveEvaluation(
  id: string,
  payload: ReasonPayload,
): Promise<Evaluation> {
  const res = await httpClient.post<Evaluation>(
    `${base}/${id}/archive`,
    payload,
  );
  return res.data;
}

export async function calibrateScore(
  id: string,
  payload: CalibrateScorePayload,
): Promise<CalibrationEvent> {
  const res = await httpClient.post<CalibrationEvent>(
    `${base}/${id}/calibrate`,
    payload,
  );
  return res.data;
}

/**
 * Stateless score preview (architecture §15 — `preview only` rule).
 * NO state mutation. NO audit. Result is for UI display only;
 * backend remains the source of truth for the official total.
 */
export async function previewScore(
  payload: PreviewScorePayload,
): Promise<ScoringResult> {
  const res = await httpClient.post<ScoringResult>(
    `${base}/preview-score`,
    payload,
  );
  return res.data;
}

// ============================================================
// By-factor (Bulk Evaluation / Excel K-sheet) fetchers
// ============================================================

/**
 * Bulk Evaluation by Factor — list rows for one factor across positions.
 *
 * Backend contract:
 *   GET /api/v1/evaluations?projectId=&groupBy=factor&factorId=&status=
 *     &departmentId=&onlyUnfilled=&page=&size=
 *
 * Returns one {@link EvaluationByFactorRow} per (position, evaluation) pair.
 *
 * NO tenant_id on the wire — backend derives it from the JWT
 * (security blueprint API-13 / noTenantIdLeak.test.ts).
 */
export async function fetchEvaluationsByFactor(
  filters: EvaluationsByFactorFilters,
): Promise<PageEnvelope<EvaluationByFactorRow>> {
  const res = await httpClient.get<PageEnvelope<EvaluationByFactorRow>>(base, {
    params: {
      projectId: filters.projectId,
      groupBy: 'factor',
      factorId: filters.factorId,
      status: filters.status || undefined,
      departmentId: filters.departmentId || undefined,
      onlyUnfilled: filters.onlyUnfilled || undefined,
      search: filters.search || undefined,
      page: filters.page ?? 0,
      size: filters.size ?? 20,
    },
  });
  return res.data;
}

/**
 * Bulk set the same factor level for many evaluations at once.
 *
 * Backend contract:
 *   POST /api/v1/evaluations/factor/{factorId}/bulk-score
 *   Body: { evaluation_ids, factor_level_id, reason }  (Contract-A snake_case)
 *
 * `reason` is audit-mandatory (≥ 50 chars per UX spec) — the backend
 * also enforces this; do not bypass on the FE.
 */
export async function bulkScoreSet(
  factorId: string,
  payload: BulkScorePayload,
): Promise<BulkOperationResult> {
  const res = await httpClient.post<BulkOperationResult>(
    `${base}/factor/${factorId}/bulk-score`,
    payload,
  );
  return res.data;
}

/**
 * Bulk transition evaluations (DRAFT/COMPLETE → SUBMITTED) for one factor.
 *
 * Backend contract:
 *   POST /api/v1/evaluations/factor/{factorId}/bulk-submit
 *   Body: { evaluation_ids, reason }  (Contract-A snake_case)
 *
 * Partial-fail semantics: returns 200 with `failed[]` carrying per-row
 * error reasons (e.g. INCOMPLETE, LOCKED). The UI surfaces them in the
 * dialog so the evaluator can retry or skip them.
 */
export async function bulkSubmit(
  factorId: string,
  payload: BulkSubmitPayload,
): Promise<BulkOperationResult> {
  const res = await httpClient.post<BulkOperationResult>(
    `${base}/factor/${factorId}/bulk-submit`,
    payload,
  );
  return res.data;
}
