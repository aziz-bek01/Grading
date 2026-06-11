/**
 * Grade Structure — Phase 6 domain types.
 *
 * Mirrors backend GradeStructure / Grade / GradeBand DTOs (see
 * backend/src/main/java/uz/hrlab/grading/gradestructure/api/*).
 *
 * WIRE CONTRACT (verified against the BE record classes):
 *  - All bodies are SNAKE_CASE (global Jackson strategy).
 *  - GradeStructureResponse carries `name_i18n` / `description_i18n` (NOT `name`).
 *  - There is NO `approved_by_name` / `locked_by_name` / `archive_reason` on the
 *    wire — archive reason lives in the audit event only.
 *  - `grade_count` / `created_at` / `updated_at` are emitted ONLY on the list
 *    path (fromList); single-item paths (create/get/update) emit them as null.
 *  - GET /grade-structures/{id} returns an ENVELOPE
 *    `{ structure, grades:[...], bands:[...] }` — bands is a SEPARATE array,
 *    joined onto a grade by `grade_id`. The detail AGGREGATOR
 *    (fetchGradeStructure) is the SINGLE place that flattens + joins this into
 *    the hydrated flat `GradeStructure` domain shape below.
 *
 * Tenant identifiers are NEVER carried by these types — the backend derives the
 * active tenant from the JWT (security blueprint API-13).
 */
import type { LocalizedString } from '@/shared/types/common';

export type GradeStructureStatus = 'DRAFT' | 'APPROVED' | 'LOCKED' | 'ARCHIVED';
export type GradeStructureType = 'GRADE_14' | 'GRADE_16' | 'CUSTOM';
export type GradeGapPolicy = 'STRICT_NO_GAPS' | 'ALLOW_GAPS_WARN';
/**
 * Built-in registry codes. Tenant CUSTOM templates carry an open-ended code, so
 * the create-from-template payload's `template_code` is a plain `string`.
 */
export type GradeStructureTemplateCode = 'GRADE_14' | 'GRADE_16' | 'CUSTOM';

/** Band ranges are inclusive `[min, max]` BigDecimal-precision-safe values. */
export interface GradeBand {
  id: string;
  grade_id: string;
  /** decimal 18,4 — kept as `number` for UI math, rounded to 4 decimals. */
  min_score: number;
  max_score: number;
}

/**
 * Hydrated flat grade — the wire `GradeResponse` carries i18n keys
 * (`name_i18n` / `description_i18n`) and NO nested band. The detail aggregator
 * JOINs the band from the envelope's separate `bands[]` array by `grade_id`.
 */
export interface Grade {
  id: string;
  grade_structure_id: string;
  grade_number: number;
  name_i18n: LocalizedString;
  description_i18n?: LocalizedString;
  sort_order: number;
  /** Joined from the envelope's `bands[]` by grade_id (null when no band). */
  band?: GradeBand | null;
}

/**
 * Hydrated flat grade structure. The wire `GradeStructureResponse` is flattened
 * to top level by the detail aggregator, with `grades[]` joined to their bands.
 */
export interface GradeStructure {
  id: string;
  project_id?: string | null;
  code: string;
  name_i18n: LocalizedString;
  description_i18n?: LocalizedString;
  structure_type: GradeStructureType;
  status: GradeStructureStatus;
  gap_policy: GradeGapPolicy;
  version_number: number;
  previous_version_id?: string | null;
  approved_at?: string | null;
  /** UUID — opaque; never displayed directly. */
  approved_by?: string | null;
  locked_at?: string | null;
  /** UUID — opaque; never displayed directly. */
  locked_by?: string | null;
  archived_at?: string | null;
  archived_by?: string | null;
  /** Only the list path emits these (BE-1 enrichment); detail emits null. */
  created_at?: string | null;
  updated_at?: string | null;
  grades: Grade[];
}

/**
 * List-row shape. Identical to {@link GradeStructure} minus the hydrated
 * `grades[]` (the list endpoint never returns grades), plus the list-only
 * `grade_count` enrichment.
 */
export interface GradeStructureListItem
  extends Omit<GradeStructure, 'grades'> {
  /** List-only enrichment — number of grades under the structure. */
  grade_count?: number | null;
  grades?: Grade[];
}

/** Pyramid endpoint — one row per grade with downstream counts. */
export interface GradePyramidRow {
  grade_id: string;
  grade_number: number;
  grade_name: LocalizedString;
  position_count: number;
  evaluation_count: number;
  min_score?: number | null;
  max_score?: number | null;
}

export interface GradePyramidResponse {
  /** Optional — BE-2 returns only `{ rows }`; kept for MSW back-compat. */
  grade_structure_id?: string;
  rows: GradePyramidRow[];
}

/**
 * Result of the preview-grade-lookup endpoint (BE-3). `out_of_range === !matched`
 * (derived server-side). On a miss, grade_id/grade_number/grade_band_id are absent.
 */
export interface GradeLookupResult {
  matched?: boolean;
  out_of_range?: boolean;
  grade_id?: string | null;
  grade_number?: number | null;
  grade_band_id?: string | null;
}

/**
 * Grade-template catalog row (BE-9) — mirrors MethodologyTemplate. `id` is null
 * for built-ins (no DB row) and a UUID for tenant CUSTOM templates.
 */
export interface GradeStructureTemplate {
  id: string | null;
  code: string;
  source: 'BUILTIN' | 'CUSTOM';
  is_builtin: boolean;
  name_i18n: LocalizedString;
  description_i18n?: LocalizedString;
  structure_type: GradeStructureType;
  grade_count: number;
}

// ---------- Request payloads (snake_case wire contract) ----------

export interface CreateFromTemplatePayload {
  /** Built-in registry code OR a tenant CUSTOM template code (BE-9). */
  template_code: string;
  project_id?: string | null;
  code: string;
  name_i18n: LocalizedString;
  description_i18n?: LocalizedString;
  gap_policy?: GradeGapPolicy;
}

export interface CreateFromScratchPayload {
  project_id?: string | null;
  code: string;
  name_i18n: LocalizedString;
  description_i18n?: LocalizedString;
  structure_type: GradeStructureType;
  gap_policy: GradeGapPolicy;
}

/**
 * PATCH /grade-structures/{id} body. The wire record accepts `name_i18n` +
 * `description_i18n`. `gap_policy` is sent for DRAFT builder mode (Spring ignores
 * unknown fields; MSW applies it so dev/test reflect the change). `code` is the
 * immutable container code — never sent.
 */
export interface UpdateMetadataPayload {
  name_i18n?: LocalizedString;
  description_i18n?: LocalizedString;
  gap_policy?: GradeGapPolicy;
}

export interface AddGradePayload {
  grade_number: number;
  name_i18n: LocalizedString;
  description_i18n?: LocalizedString;
  sort_order?: number;
}

export type UpdateGradePayload = Partial<AddGradePayload>;

export interface UpsertBandPayload {
  min_score: number;
  max_score: number;
}

/**
 * Reorder body — reuses the methodology `ReorderRequest` contract: a single
 * `ordered_ids` array (the full grade-id set in its new order; server re-indexes).
 */
export interface ReorderPayload {
  ordered_ids: string[];
}

/** POST /grade-structures/{id}/save-as-template body (BE-9). */
export interface SaveAsGradeTemplatePayload {
  code: string;
  name_i18n: LocalizedString;
  description_i18n?: LocalizedString;
}

/** PUT /grade-structure-templates/{id} body — rename a CUSTOM template (BE-9). */
export interface UpdateGradeTemplatePayload {
  name_i18n: LocalizedString;
  description_i18n?: LocalizedString;
}

export interface ReasonPayload {
  reason: string;
}

export interface PreviewLookupPayload {
  score: number;
}

export interface GradeStructureListFilters {
  projectId?: string;
  status?: GradeStructureStatus;
}
