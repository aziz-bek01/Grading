/**
 * Grade Structure — Phase 6 domain types.
 *
 * Mirrors backend GradeStructure / Grade / GradeBand DTOs (see
 * backend/src/main/java/uz/hrlab/grading/gradestructure/api/*).
 *
 * Tenant identifiers are NEVER carried by these types — the backend
 * derives the active tenant from the JWT (security blueprint API-13).
 */
import type { LocalizedString } from '@/shared/types/common';

export type GradeStructureStatus = 'DRAFT' | 'APPROVED' | 'LOCKED' | 'ARCHIVED';
export type GradeStructureType = 'GRADE_14' | 'GRADE_16' | 'CUSTOM';
export type GradeGapPolicy = 'STRICT_NO_GAPS' | 'ALLOW_GAPS_WARN';
export type GradeStructureTemplateCode = 'GRADE_14' | 'GRADE_16' | 'CUSTOM';

/** Band ranges are inclusive `[min, max]` BigDecimal-precision-safe strings. */
export interface GradeBand {
  id: string;
  grade_id: string;
  /** decimal 18,4 — kept as `number` for UI math, rounded to 4 decimals. */
  min_score: number;
  max_score: number;
}

export interface Grade {
  id: string;
  grade_structure_id: string;
  grade_number: number;
  name: LocalizedString;
  description?: LocalizedString;
  sort_order: number;
  band?: GradeBand | null;
}

export interface GradeStructure {
  id: string;
  project_id?: string | null;
  code: string;
  name: LocalizedString;
  description?: LocalizedString;
  structure_type: GradeStructureType;
  status: GradeStructureStatus;
  gap_policy: GradeGapPolicy;
  version_number: number;
  parent_structure_id?: string | null;
  approved_at?: string | null;
  approved_by?: string | null;
  approved_by_name?: string | null;
  locked_at?: string | null;
  locked_by?: string | null;
  locked_by_name?: string | null;
  archived_at?: string | null;
  archive_reason?: string | null;
  created_at: string;
  updated_at: string;
  grades: Grade[];
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
  grade_structure_id: string;
  rows: GradePyramidRow[];
}

/** Result of the preview-grade-lookup endpoint. `null` means out-of-range. */
export interface GradeLookupResult {
  grade_id?: string | null;
  grade_number?: number | null;
  grade_band_id?: string | null;
  out_of_range?: boolean;
}

// ---------- Request payloads ----------

export interface CreateFromTemplatePayload {
  template_code: GradeStructureTemplateCode;
  project_id?: string | null;
  code: string;
  name: LocalizedString;
}

export interface CreateFromScratchPayload {
  project_id?: string | null;
  code: string;
  name: LocalizedString;
  description?: LocalizedString;
  structure_type: GradeStructureType;
  gap_policy: GradeGapPolicy;
}

export type UpdateMetadataPayload = Partial<
  Pick<CreateFromScratchPayload, 'code' | 'name' | 'description' | 'gap_policy'>
>;

export interface AddGradePayload {
  grade_number: number;
  name: LocalizedString;
  description?: LocalizedString;
  sort_order?: number;
}

export type UpdateGradePayload = Partial<AddGradePayload>;

export interface UpsertBandPayload {
  min_score: number;
  max_score: number;
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
