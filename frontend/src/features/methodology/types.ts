/**
 * Methodology — Phase 4 domain types.
 *
 * A `Methodology` is a project-scoped entity (e.g., "CFO Finance Grading 2026")
 * that owns one or more versions. A `MethodologyVersion` carries factors and
 * factor-levels and a scoring mode. Approved/Locked versions are read-only —
 * the only mutating affordance is "Create new version" (design-foundation §13).
 *
 * Type templates per PRD MVP1-E7-1: CLASSIC_8_FACTOR / EXTENDED_11_CRITERIA / CUSTOM.
 *
 * Tenant identifiers are NEVER carried by these types — the backend derives
 * the active tenant from the JWT (security blueprint API-13).
 */
import type { LocalizedString } from '@/shared/types/common';

export type MethodologyType = 'CLASSIC_8_FACTOR' | 'EXTENDED_11_CRITERIA' | 'CUSTOM';

export type MethodologyVersionStatus = 'DRAFT' | 'APPROVED' | 'LOCKED' | 'ARCHIVED';

export type MethodologyStatus = 'ACTIVE' | 'ARCHIVED';

/**
 * Scoring modes:
 *  - DIRECT_POINTS    : level.points used as-is; weight ignored.
 *  - WEIGHTED_POINTS  : factor weight × level.points; weights MUST sum to 100.
 *  - WEIGHTED_SCALE   : factor weight × level.scaleValue × (targetTotalPoints / 100).
 *
 * (`FORMULA_BASED` is explicitly OUT of MVP 1 scope per PRD J4 step 5.)
 */
export type ScoringMode = 'DIRECT_POINTS' | 'WEIGHTED_POINTS' | 'WEIGHTED_SCALE';

/**
 * Mirrors backend `MethodologyResponse` (Spring SNAKE_CASE Jackson strategy):
 * `{ id, project_id, code, name_i18n, description_i18n, methodology_type, status }`.
 *
 * `latest_version_id` IS now echoed by the backend on the create /
 * create-from-template responses (a UUID string, or null) — the FE uses it to
 * deep-link straight into the freshly-seeded v1 editor. It is NOT guaranteed on
 * the list endpoint, so callers must tolerate it being absent there.
 *
 * The remaining optional fields (`active_version_*`, timestamps, archive
 * metadata) are derived/detail-view data kept here for the MSW back-compat
 * surface; they are not part of the list-endpoint contract.
 */
export interface Methodology {
  id: string;
  project_id: string;
  code: string;
  name_i18n: LocalizedString;
  description_i18n?: LocalizedString;
  methodology_type: MethodologyType;
  status: MethodologyStatus;
  /** Convenience pointer to the latest version (any status). */
  latest_version_id?: string | null;
  /** Convenience pointer to the latest APPROVED/LOCKED version, if any. */
  active_version_id?: string | null;
  active_version_number?: number | null;
  active_version_status?: MethodologyVersionStatus | null;
  created_at?: string;
  updated_at?: string;
  archived_at?: string | null;
  archive_reason?: string | null;
}

/**
 * Mirrors backend `FactorLevelResponse`:
 * `{ id, factor_id, code, level_order, points, scale_value, label_i18n, description_i18n }`.
 */
export interface FactorLevel {
  id: string;
  factor_id: string;
  code: string;
  level_order: number;
  /** Used in DIRECT_POINTS and WEIGHTED_POINTS scoring modes. */
  points: number;
  /** Used in WEIGHTED_SCALE scoring mode (0..1 typically, but unbounded). */
  scale_value: number;
  label_i18n: LocalizedString;
  description_i18n?: LocalizedString;
}

/**
 * Mirrors backend `FactorResponse`:
 * `{ id, methodology_version_id, code, name_i18n, description_i18n, weight,
 *   max_points, sort_order, required }`.
 *
 * Backend does NOT include nested `levels` on FactorResponse (separate endpoint).
 * Kept here as an optional convenience field populated by the detail aggregator.
 */
export interface Factor {
  id: string;
  methodology_version_id: string;
  code: string;
  name_i18n: LocalizedString;
  description_i18n?: LocalizedString;
  /** Weight is stored as a number; arithmetic with BigNumber happens server-side. */
  weight: number;
  /** Max raw points the factor can contribute (per scoring mode rules). */
  max_points: number;
  sort_order: number;
  required: boolean;
  /**
   * Nested levels. The bare `FactorResponse` from
   * `GET /methodology-versions/{id}/factors` does NOT embed levels — the FE
   * version-detail aggregator ({@link fetchMethodologyVersion}) ALWAYS hydrates
   * this before any consumer sees a `Factor`, so the domain shape stays required.
   */
  levels: FactorLevel[];
}

export interface MethodologyVersion {
  id: string;
  methodology_id: string;
  project_id: string;
  version_number: number;
  status: MethodologyVersionStatus;
  scoring_mode: ScoringMode;
  /** WEIGHTED_SCALE only: total points the scale resolves to. */
  target_total_points: number;
  /**
   * Nested factors. The raw `GET /methodology-versions/{id}` object does NOT
   * carry them (factors live on a separate endpoint) — the FE version-detail
   * aggregator ({@link fetchMethodologyVersion}) ALWAYS hydrates this before any
   * consumer sees a `MethodologyVersion`, so the domain shape stays required.
   */
  factors: Factor[];
  /** Approval / lock metadata for the locked banner. */
  approved_at?: string | null;
  /** UUID — opaque; never displayed directly. */
  approved_by?: string | null;
  /**
   * Human-readable approver name (D-407 / PC4-5).
   *
   * Resolved server-side from the user directory. Falls back to
   * "Unknown actor" (i18n key `common.unknown_actor`) when null/missing.
   */
  approved_by_name?: string | null;
  locked_at?: string | null;
  /** UUID — opaque; never displayed directly. */
  locked_by?: string | null;
  /**
   * Human-readable locker name (D-407 / PC4-5).
   *
   * Resolved server-side from the user directory. Falls back to
   * "Unknown actor" (i18n key `common.unknown_actor`) when null/missing.
   */
  locked_by_name?: string | null;
  archived_at?: string | null;
  archive_reason?: string | null;
  parent_version_id?: string | null;
  created_at: string;
  updated_at: string;
}

/** Summary used by version lists / dropdowns. */
export interface MethodologyVersionSummary {
  id: string;
  version_number: number;
  status: MethodologyVersionStatus;
  scoring_mode: ScoringMode;
  approved_at?: string | null;
  locked_at?: string | null;
  updated_at: string;
}

/** POST body — backend snake_case contract. */
export type FactorCreatePayload = {
  code: string;
  name_i18n: LocalizedString;
  description_i18n?: LocalizedString;
  weight: number;
  max_points: number;
  sort_order?: number;
  required: boolean;
};

export type FactorUpdatePayload = Partial<FactorCreatePayload>;

/** POST body — backend snake_case contract. */
export type FactorLevelCreatePayload = {
  code: string;
  level_order?: number;
  points: number;
  scale_value: number;
  label_i18n: LocalizedString;
  description_i18n?: LocalizedString;
};

export type FactorLevelUpdatePayload = Partial<FactorLevelCreatePayload>;

/** POST body — backend snake_case contract. */
export interface MethodologyCreatePayload {
  project_id: string;
  code: string;
  name_i18n: LocalizedString;
  description_i18n?: LocalizedString;
  methodology_type: MethodologyType;
  /** Optional initial version body — backend may create v1 implicitly. */
  scoring_mode?: ScoringMode;
  target_total_points?: number;
  /** When set, backend pre-populates v1 factors+levels from the template. */
  source_template_code?: 'CLASSIC_8_FACTOR' | 'EXTENDED_11_CRITERIA' | null;
}

export type MethodologyUpdatePayload = Partial<
  Pick<MethodologyCreatePayload, 'code' | 'name_i18n' | 'description_i18n'>
>;

export interface MethodologyReasonPayload {
  reason: string;
}

export interface MethodologyTemplate {
  code: 'CLASSIC_8_FACTOR' | 'EXTENDED_11_CRITERIA' | 'CUSTOM';
  name_i18n: LocalizedString;
  description_i18n?: LocalizedString;
  factor_count: number;
  default_scoring_mode: ScoringMode;
}

export interface ReorderPayload {
  order: { id: string; sort_order: number }[];
}

export interface LevelReorderPayload {
  order: { id: string; level_order: number }[];
}
