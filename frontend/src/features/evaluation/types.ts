/**
 * Evaluation — Phase 5 domain types.
 *
 * Mirrors backend `EvaluationResponse`, `EvaluationScoreResponse`,
 * `CalibrationEventResponse`, and `ScoringResultResponse` (see
 * backend/src/main/java/uz/hrlab/grading/evaluation/api/*).
 *
 * Tenant identifiers are NEVER carried by these types — backend derives
 * the active tenant from the JWT (security blueprint API-13).
 */

export type EvaluationStatus =
  | 'DRAFT'
  | 'INCOMPLETE'
  | 'COMPLETE'
  | 'SUBMITTED'
  | 'APPROVED'
  | 'LOCKED'
  | 'ARCHIVED';

/**
 * Pre-submission statuses — the statuses in which an evaluation may be hard
 * deleted (mirrors backend `EvaluationStatus.isDeletable()`). Once SUBMITTED
 * (SUBMITTED / APPROVED / LOCKED / ARCHIVED) the evaluation keeps the Archive
 * path; the backend rejects a delete with `EVALUATION_NOT_DELETABLE`.
 *
 * Single source of truth: the row-level "Удалить" action visibility derives
 * from {@link isEvaluationDeletable} — do NOT inline a status list elsewhere.
 */
export const DELETABLE_EVALUATION_STATUSES: readonly EvaluationStatus[] = [
  'DRAFT',
  'INCOMPLETE',
  'COMPLETE',
];

/** True when an evaluation in this status may be hard deleted (pre-submission). */
export function isEvaluationDeletable(status: EvaluationStatus): boolean {
  return DELETABLE_EVALUATION_STATUSES.includes(status);
}

export interface Evaluation {
  id: string;
  project_id: string;
  position_id: string;
  methodology_version_id: string;
  evaluator_user_id?: string | null;
  status: EvaluationStatus;
  /** Stored to 4 decimals; only displayed via `displayed_total_score`. */
  raw_total_score?: number | null;
  /** Rounded to 2 decimals for UI. */
  displayed_total_score?: number | null;
  grade_band_id?: string | null;
  assigned_grade_number?: number | null;
  submitted_at?: string | null;
  submitted_by?: string | null;
  approved_at?: string | null;
  approved_by?: string | null;
  locked_at?: string | null;
  locked_by?: string | null;
  archived_at?: string | null;
  archived_by?: string | null;
}

export interface EvaluationScore {
  id: string;
  evaluation_id: string;
  factor_id: string;
  factor_level_id: string;
  raw_factor_score: number;
  comment_text?: string | null;
  manually_adjusted: boolean;
  original_factor_score?: number | null;
  adjusted_by_user_id?: string | null;
  adjusted_at?: string | null;
  adjustment_reason?: string | null;
}

export interface CalibrationEvent {
  id: string;
  evaluation_id: string;
  factor_id: string;
  original_score: number;
  adjusted_score: number;
  delta: number;
  reason: string;
  decided_by?: string | null;
  decided_at: string;
}

export interface PreviewSelection {
  factor_id: string;
  factor_level_id: string;
}

export interface ScoringResult {
  raw_total: number;
  displayed_total: number;
  /** Map of factor_id -> raw factor score. */
  per_factor_raw: Record<string, number>;
}

// ---------- Request payloads ----------

export interface CreateEvaluationPayload {
  position_id: string;
  methodology_version_id: string;
  evaluator_user_id?: string | null;
}

export interface UpsertScorePayload {
  factor_id: string;
  factor_level_id: string;
  comment?: string | null;
}

export interface CalibrateScorePayload {
  factor_id: string;
  new_raw_factor_score: number;
  reason: string;
}

export interface ReasonPayload {
  reason: string;
}

export interface PreviewScorePayload {
  methodology_version_id: string;
  selections: PreviewSelection[];
}

export interface EvaluationListFilters {
  projectId?: string;
  positionId?: string;
  evaluatorUserId?: string;
  status?: EvaluationStatus;
  page?: number;
  size?: number;
}

// ============================================================
// "By factor" view — Bulk Evaluation (Excel K-sheet UX)
// ============================================================

/**
 * One row in the by-factor matrix view.
 *
 * Backend contract (parallel BE agent):
 *   GET /api/v1/evaluations?projectId=&groupBy=factor&factorId=K1
 *     &status=&departmentId=&page=&size=
 *
 * The row carries ONLY the columns rendered by the K-sheet table —
 * full evaluation detail is intentionally not duplicated here.
 *
 * `currentScoreFactorLevelId` is the saved level id for the active
 * factor (null when the row has not been scored yet); the UI uses
 * it as the controlled value for the inline `<Select>`.
 *
 * Tenant identifiers are NEVER carried by the row — backend derives
 * the active tenant from the JWT (security blueprint API-13).
 */
export interface EvaluationByFactorRow {
  evaluation_id: string;
  position_id: string;
  position_code: string;
  position_title: string;
  department_name: string;
  unit_name: string | null;
  status: EvaluationStatus;
  /** How many factors in the methodology version have a saved score. */
  filled_factors_count: number;
  /** Total factor count in the methodology version. */
  total_factors_count: number;
  /** Saved level id for the active factor; null when not yet scored. */
  current_score_factor_level_id: string | null;
  /**
   * Saved raw-value display for the active factor (e.g. "4" for level 4).
   * `null` is rendered as "—". Note: this is NOT the weighted factor score —
   * the official total comes from backend (architecture §15). Field name
   * matches the BE record EvaluationByFactorRow.currentRawFactorScore
   * (global SNAKE_CASE). Currently unread by the UI (points hidden from
   * evaluators) — kept for admin surfaces.
   */
  current_raw_factor_score: number | null;
  /** Saved per-factor comment (truncated server-side to 1000 chars). */
  current_comment: string | null;
}

export interface BulkScoreFailure {
  evaluation_id: string;
  reason: string;
}

export interface BulkOperationResult {
  updated: number;
  failed: BulkScoreFailure[];
}

// ---------- Bulk-create (Item 1, FE-4) ----------

/**
 * Bulk-create request body (BE-1 contract). Travels as JSON, so it obeys
 * Contract-A (global SNAKE_CASE Jackson): `items[].position_id`,
 * `items[].methodology_version_id`, `items[].evaluator_user_id`. `items` is
 * @NotEmpty and capped at 200 server-side. `evaluator_user_id` is optional
 * (defaults to the caller, same as the single-create path).
 */
export interface BulkCreateEvaluationItem {
  position_id: string;
  methodology_version_id: string;
  evaluator_user_id?: string | null;
}

export interface BulkCreateEvaluationPayload {
  items: BulkCreateEvaluationItem[];
}

/**
 * One per-row failure of a bulk-create call. Keys on `position_id` (NOT an
 * evaluation id) because no evaluation was created for a failed row — a
 * deliberately DIFFERENT shape from {@link BulkOperationResult.failed} (which
 * keys on `evaluation_id`). `error_code` is a stable enum:
 * ALREADY_EXISTS | ACCESS_DENIED | VALIDATION | DOMAIN_REJECTED | INTERNAL_ERROR.
 */
export interface BulkCreateFailure {
  position_id: string;
  error_code: string;
  message: string;
}

/**
 * Bulk-create response (BE-1 contract). Always HTTP 200, even on partial
 * failure. `created` is the count of evaluations actually created; `failed`
 * carries one row per skipped input. The interpretation of the count field is
 * "created" (vs "updated" in the bulk-score/bulk-submit flows) — aligned to
 * the BE `BulkCreateEvaluationsResponse.created` field name.
 */
export interface BulkCreateEvaluationResult {
  created: number;
  failed: BulkCreateFailure[];
}

export interface EvaluationsByFactorFilters {
  projectId: string;
  factorId: string;
  status?: EvaluationStatus | '';
  departmentId?: string;
  onlyUnfilled?: boolean;
  search?: string;
  page?: number;
  size?: number;
}

// Request bodies travel over the wire as JSON, so they obey Contract-A
// (global SNAKE_CASE Jackson strategy): the real backend deserializes
// BulkScoreRequest(evaluationIds, factorLevelId, reason) from snake_case keys
// `evaluation_ids` / `factor_level_id`. Sending camelCase here would leave the
// @NotEmpty/@NotNull fields null → 400. (Query params are exempt — they bind to
// the literal Java param name — so EvaluationsByFactorFilters stays camelCase.)
export interface BulkScorePayload {
  evaluation_ids: string[];
  factor_level_id: string;
  reason: string;
}

export interface BulkSubmitPayload {
  evaluation_ids: string[];
  reason: string;
}
