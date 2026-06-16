/**
 * Multi-evaluator EVALUATION_PANEL domain types (MVP 2).
 *
 * CANONICAL TERM: EVALUATION_PANEL. These types mirror the BE golden-file
 * pinned wire shapes (PanelControllerWireTest) 1:1 in snake_case — the same
 * field names the components and MSW handlers read. `@JsonInclude(NON_NULL)` on
 * the BE means null fields are OMITTED from the wire; the optional `?` markers
 * here honour that (e.g. raw_total_score is absent until AVERAGED).
 *
 * Lists use the PageResponse envelope { items, page, size, total, ... } — note
 * the panel endpoints carry `total` (NOT `total_elements` like the older
 * PageEnvelope). See {@link PanelPageResponse}.
 *
 * SECURITY (server-enforced; FE mirrors, never the source of truth):
 *   - Scores/averages are absent from PanelResponse / PanelDetailResponse while
 *     a panel collects — only STATUS is returned (blind-safe).
 *   - PanelResultResponse is gated by CAMPAIGN_RESULTS_VIEW and 404s while
 *     collecting (REQ-ISO-5). The FE never computes a "live running average".
 *   - Client-supplied raw_total_score / displayed_total_score / evaluator_count
 *     / assigned_grade_number / tenant_id are IGNORED by the server
 *     (mass-assignment guard) — the FE never sends them.
 *
 * NO tenant_id is ever carried by these types — the BE derives the active
 * tenant from the JWT (security blueprint API-13).
 */
import type { LocalizedString } from '@/shared/types/common';

/** Mandatory trio + variable extras. Mirrors BE `EvaluatorRole`. */
export type EvaluatorRole =
  | 'HR_DIRECTOR'
  | 'DEPARTMENT_DIRECTOR'
  | 'EXTERNAL_EXPERT'
  | 'ADDITIONAL';

/** The three roles that MUST be present before the roster can be locked. */
export const MANDATORY_EVALUATOR_ROLES: readonly EvaluatorRole[] = [
  'HR_DIRECTOR',
  'DEPARTMENT_DIRECTOR',
  'EXTERNAL_EXPERT',
];

/** Product floor: never below 3 (the mandatory trio). UI mirror of the BE rule. */
export const PANEL_MIN_EVALUATORS = 3;

/**
 * Max position rows per bulk-create call. Mirrors the BE MAX_PAGE_SIZE guard
 * (POST /panels/bulk-create returns 400 VALIDATION_FAILED above this) — kept in
 * sync with the bulk-create-evaluations cap so the dept-first wizard never
 * submits a request the server would reject.
 */
export const MAX_BULK_PANEL_POSITIONS = 200;

/**
 * UI cap on EXTERNAL_EXPERT seats in the dept-first wizard: one mandatory
 * EXTERNAL_EXPERT seat + up to 3 ADDITIONAL externals = 4. The BE imposes NO
 * per-role uniqueness (extra externals ride the ADDITIONAL role); this cap is a
 * product convenience so the roster does not grow unbounded.
 */
export const MAX_EXTERNAL_SEATS = 4;

/**
 * Panel lifecycle. Mirrors BE `PanelStatus`:
 *   COLLECTING          — roster mutable; assign/withdraw allowed.
 *   AWAITING_EVALUATIONS — roster locked; one DRAFT evaluation per seat.
 *   AVERAGED            — all completed; stored averages exist; CEO submit allowed.
 *   SUBMITTED           — CEO approval request opened.
 *   APPROVED            — CEO approved; grade assigned; sheets locked.
 *   LOCKED / ARCHIVED   — terminal.
 */
export type PanelStatus =
  | 'COLLECTING'
  | 'AWAITING_EVALUATIONS'
  | 'AVERAGED'
  | 'SUBMITTED'
  | 'APPROVED'
  | 'LOCKED'
  | 'ARCHIVED';

/** Per-seat assignment lifecycle. Mirrors BE `AssignmentStatus`. */
export type PanelAssignmentStatus =
  | 'ASSIGNED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'WITHDRAWN';

/**
 * Status ordinal — used ONLY to gate the averaged-result display
 * ("status >= AVERAGED"). Lower numbers are earlier in the lifecycle; the
 * blind lifts at AVERAGED. Single source of truth so no component inlines a
 * status list (mirrors the `EvaluationStatus.isPreSubmission()` pattern).
 */
const PANEL_STATUS_ORDER: Record<PanelStatus, number> = {
  COLLECTING: 0,
  AWAITING_EVALUATIONS: 1,
  AVERAGED: 2,
  SUBMITTED: 3,
  APPROVED: 4,
  LOCKED: 5,
  ARCHIVED: 6,
};

/** True once the panel has reached AVERAGED (averaged result exists / blind lifted). */
export function isPanelAveraged(status: PanelStatus): boolean {
  return PANEL_STATUS_ORDER[status] >= PANEL_STATUS_ORDER.AVERAGED;
}

/** True while the panel is still collecting evaluations (blind active). */
export function isPanelCollecting(status: PanelStatus): boolean {
  return status === 'COLLECTING' || status === 'AWAITING_EVALUATIONS';
}

/** Roster may only be mutated (assign/withdraw) while COLLECTING (BE rule). */
export function isRosterMutable(status: PanelStatus): boolean {
  return status === 'COLLECTING';
}

// ============================================================
// T3 (Defect 2) — panel management gating predicates
//
// SINGLE source of truth for which management actions the FE may OFFER. They
// MIRROR the BE PanelStatus guards 1:1 (DeletePanelUseCase / ArchivePanelUseCase
// / ReopenPanelUseCase) so a non-applicable action is never even rendered — no
// 403/400 round-trip — exactly as `isEvaluationDeletable` mirrors the evaluation
// delete guard. The BE remains the source of truth (these only hide UI).
// APPROVED / LOCKED / ARCHIVED → no management action.
// ============================================================

/** Hard-delete allowed ONLY while COLLECTING (BE PANEL_NOT_DELETABLE otherwise). */
export function isPanelDeletable(status: PanelStatus): boolean {
  return status === 'COLLECTING';
}

/** Soft-cancel (archive) allowed from a working panel (BE PANEL_NOT_ARCHIVABLE otherwise). */
export function isPanelArchivable(status: PanelStatus): boolean {
  return (
    status === 'AWAITING_EVALUATIONS' ||
    status === 'AVERAGED' ||
    status === 'SUBMITTED'
  );
}

/** Manual reopen back to AWAITING_EVALUATIONS — only from SUBMITTED | AVERAGED. */
export function isPanelReopenable(status: PanelStatus): boolean {
  return status === 'SUBMITTED' || status === 'AVERAGED';
}

// ============================================================
// Wire shapes (snake_case — golden-file pinned)
// ============================================================

/** GOLDEN: PanelResponse. Scores null/omitted until AVERAGED; grade until APPROVED. */
export interface Panel {
  id: string;
  project_id: string;
  position_id: string;
  position_title_i18n: LocalizedString;
  methodology_version_id: string;
  status: PanelStatus;
  min_evaluators: number;
  evaluator_count: number;
  completed_count: number;
  /** NUMERIC(12,4) — null/omitted until AVERAGED. */
  raw_total_score?: number | null;
  /** NUMERIC(12,2) — null/omitted until AVERAGED. */
  displayed_total_score?: number | null;
  /** null/omitted until APPROVED. */
  assigned_grade_number?: number | null;
  /** null/omitted until APPROVED. */
  grade_band_id?: string | null;
  averaged_at?: string | null;
  submitted_at?: string | null;
  approved_at?: string | null;
  created_at: string;
}

/** GOLDEN: PanelAssignmentResponse. */
export interface PanelAssignment {
  id: string;
  panel_id: string;
  evaluator_user_id: string;
  /** Resolved via ActorNameResolver; null/omitted when no active membership. */
  evaluator_name?: string | null;
  evaluator_role: EvaluatorRole;
  assignment_status: PanelAssignmentStatus;
  /** null/omitted until the evaluator's sheet is started. */
  evaluation_id?: string | null;
  updated_at: string;
}

/** GOLDEN: PanelDetailResponse. Blind-safe — STATUS only, no scores. */
export interface PanelDetail {
  panel: Panel;
  roster: PanelAssignment[];
  completed_count: number;
  total_count: number;
}

/** GOLDEN: PanelResultResponse.factor_averages[].per_evaluator[]. */
export interface PanelPerEvaluatorFactorScore {
  evaluator_user_id: string;
  evaluator_name?: string | null;
  evaluator_role: EvaluatorRole;
  raw_factor_score: number;
}

/** GOLDEN: PanelResultResponse.factor_averages[]. */
export interface PanelFactorAverage {
  factor_id: string;
  factor_name_i18n: LocalizedString;
  avg_raw_factor_score: number;
  evaluator_count: number;
  per_evaluator: PanelPerEvaluatorFactorScore[];
  /** max - min across contributors; null/omitted when < 2 contributors. */
  disagreement_range?: number | null;
}

/**
 * GOLDEN: PanelResultResponse (gated by CAMPAIGN_RESULTS_VIEW; 404 while
 * collecting). Carries the STORED, server-computed averages — labelled
 * "official" in the UI (not a FE preview).
 */
export interface PanelResult {
  panel_id: string;
  displayed_total_score: number;
  raw_total_score: number;
  evaluator_count: number;
  factor_averages: PanelFactorAverage[];
  grade_band_id?: string | null;
  assigned_grade_number?: number | null;
}

/**
 * PageResponse envelope for panel lists. Note `total` (BE global strategy),
 * NOT `total_elements`. `page` / `size` mirror the request.
 */
export interface PanelPageResponse {
  items: Panel[];
  page: number;
  size: number;
  total: number;
  total_pages?: number;
}

// ============================================================
// Request payloads (snake_case — Contract-A)
// ============================================================

/**
 * POST /panels body. Mass-assignment guard: the server IGNORES any
 * client-supplied raw_total_score / displayed_total_score / evaluator_count /
 * assigned_grade_number / tenant_id — so we never send them.
 */
export interface CreatePanelPayload {
  position_id: string;
  methodology_version_id: string;
  /** Optional, >= 1, default 3. The product floor (3) is also UI-enforced. */
  min_evaluators?: number;
}

/** POST /panels/{id}/evaluators body. */
export interface AssignEvaluatorPayload {
  evaluator_user_id: string;
  evaluator_role: EvaluatorRole;
}

/** A roster draft row used by the assign-evaluators dialog (pre-submit). */
export interface PanelEvaluatorDraft {
  role: EvaluatorRole;
  evaluator_user_id: string | null;
  /** Display name resolved client-side from the people picker, for the chip. */
  evaluator_name?: string | null;
}

// ============================================================
// Bulk-create panels (dept-first wizard) — snake_case wire
// ============================================================

/** One shared roster seat carried by the bulk-create body. */
export interface BulkPanelRosterSeat {
  evaluator_user_id: string;
  evaluator_role: EvaluatorRole;
}

/**
 * POST /panels/bulk-create body. The SAME roster is applied to every position;
 * the BE opens one panel per position_id (NOT a single mega-panel). The
 * mandatory-trio / min-3 rule is NOT enforced here — that is the lock-roster
 * enforcement point; bulk-create just assigns the provided seats and, with
 * start_evaluations unset, leaves panels COLLECTING. With start_evaluations
 * the BE additionally LOCKS each fully-rostered panel right after assigning the
 * roster (→ AWAITING_EVALUATIONS, one DRAFT evaluation sheet per seat) so the
 * assigned experts can score immediately. tenant_id is NEVER sent (JWT-derived).
 */
export interface BulkCreatePanelsPayload {
  methodology_version_id: string;
  position_ids: string[];
  /** Optional; shared across every position. */
  roster?: BulkPanelRosterSeat[];
  /**
   * Optional. When true the BE locks each fully-rostered panel after assigning
   * the roster (AWAITING_EVALUATIONS + a DRAFT sheet per seat) instead of leaving
   * it COLLECTING — so the wizard creates AND starts the commission in one call.
   * A panel whose lock fails surfaces as a ROSTER_LOCK_FAILED row (panel exists).
   */
  start_evaluations?: boolean;
}

/** Stable per-position error codes returned by bulk-create. */
export type BulkCreatePanelErrorCode =
  | 'ALREADY_EXISTS'
  | 'ACCESS_DENIED'
  | 'VALIDATION'
  | 'ROSTER_PARTIAL'
  // start_evaluations was requested and the panel WAS created, but locking its
  // roster (→ AWAITING_EVALUATIONS) failed — same family as ROSTER_PARTIAL: the
  // panel exists, so a retry would just hit ALREADY_EXISTS.
  | 'ROSTER_LOCK_FAILED'
  | 'INTERNAL_ERROR';

/**
 * A seat that could not be assigned on an otherwise-created panel. PRESENT ONLY
 * when error_code == ROSTER_PARTIAL (the panel WAS opened in COLLECTING but a
 * seat failed) — Jackson NON_NULL drops the key for every other code.
 */
export interface BulkPanelSeatFailure {
  evaluator_role: EvaluatorRole;
  evaluator_user_id: string;
  reason: string;
}

/**
 * One per-position failure. Keys on `position_id` (NOT panel_id — a fully-failed
 * row has no panel), mirroring BulkCreateEvaluationsResponse. `seat_failures` is
 * absent unless error_code == ROSTER_PARTIAL.
 */
export interface BulkCreatePanelFailure {
  position_id: string;
  error_code: BulkCreatePanelErrorCode;
  message: string;
  seat_failures?: BulkPanelSeatFailure[];
}

/**
 * POST /panels/bulk-create response (HTTP 201). `created` counts panels opened
 * in COLLECTING — INCLUDING rows that also appear in `failed` as ROSTER_PARTIAL.
 */
export interface BulkCreatePanelsResult {
  created: number;
  failed: BulkCreatePanelFailure[];
}

/** GET /panels/roster-suggestions advisory dept-director candidate. */
export interface RosterDeptDirectorCandidate {
  user_id: string;
  full_name: string;
}

/**
 * GET /panels/roster-suggestions?project_id=&department_id= response. ADVISORY
 * only — the BE re-validates membership on the real assign. A 404 means the
 * department is outside the caller's ABAC subtree → FE treats it as "no
 * suggestions / not visible", NOT a hard error (the seat stays editable).
 */
export interface RosterSuggestions {
  department_id: string;
  dept_director_candidates: RosterDeptDirectorCandidate[];
}
