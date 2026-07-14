import type { LocalizedString } from '@/shared/types/common';

export type JobProfileStatus = 'DRAFT' | 'UNDER_REVIEW' | 'APPROVED' | 'ARCHIVED';

export type JobProfileMultilingualField = LocalizedString;

/**
 * Mirrors backend `JobProfileResponse` (Spring SNAKE_CASE Jackson strategy):
 * Each multilingual long-text field carries the `_i18n` suffix
 * (`purpose_i18n`, `main_duties_i18n`, ...) — backend Java field names are
 * `purposeI18n`, `mainDutiesI18n`, ... rewritten by Jackson.
 *
 * `previous_revision_id` mirrors backend `previousRevisionId` (renamed from
 * the legacy `parent_revision_id` FE field).
 */
export interface JobProfile {
  id: string;
  position_id: string;
  project_id: string;
  status: JobProfileStatus;
  revision_number: number;
  /** Backend field name — replaces legacy `parent_revision_id`. */
  previous_revision_id?: string | null;

  // Role & responsibilities
  purpose_i18n: JobProfileMultilingualField;
  main_duties_i18n: JobProfileMultilingualField;
  responsibility_area_i18n: JobProfileMultilingualField;
  authority_i18n: JobProfileMultilingualField;
  kpi_expected_results_i18n: JobProfileMultilingualField;

  // Requirements
  education_requirements_i18n: JobProfileMultilingualField;
  experience_requirements_i18n: JobProfileMultilingualField;
  knowledge_skills_i18n: JobProfileMultilingualField;

  // Interactions
  internal_interactions_i18n: JobProfileMultilingualField;
  external_interactions_i18n: JobProfileMultilingualField;

  // Working context
  working_conditions_i18n: JobProfileMultilingualField;
  documents_regulations_i18n: JobProfileMultilingualField;
  actualization_date?: string;

  // Workflow timestamps (backend names mirror snake_case).
  submitted_at?: string | null;
  submitted_by?: string | null;
  approved_at?: string | null;
  approved_by?: string | null;
  locked_at?: string | null;
  /** Optional — backend response doesn't always include; MSW back-compat. */
  created_at?: string;
  updated_at?: string;
  archived_at?: string | null;
  archived_by?: string | null;
  archive_reason?: string | null;
}

export type JobProfileLongTextFieldKey =
  | 'purpose_i18n'
  | 'main_duties_i18n'
  | 'responsibility_area_i18n'
  | 'authority_i18n'
  | 'kpi_expected_results_i18n'
  | 'education_requirements_i18n'
  | 'experience_requirements_i18n'
  | 'knowledge_skills_i18n'
  | 'internal_interactions_i18n'
  | 'external_interactions_i18n'
  | 'working_conditions_i18n'
  | 'documents_regulations_i18n';

export const JOB_PROFILE_FIELDS: JobProfileLongTextFieldKey[] = [
  'purpose_i18n',
  'main_duties_i18n',
  'responsibility_area_i18n',
  'authority_i18n',
  'kpi_expected_results_i18n',
  'education_requirements_i18n',
  'experience_requirements_i18n',
  'knowledge_skills_i18n',
  'internal_interactions_i18n',
  'external_interactions_i18n',
  'working_conditions_i18n',
  'documents_regulations_i18n',
];

/**
 * Lean wire shape returned by the BULK `GET /job-profiles/statuses` endpoint
 * (one element per position that HAS an active, non-ARCHIVED profile — see
 * `fetchJobProfileStatusesByPositions`). Deliberately NOT a `Partial<JobProfile>`:
 * it only ever carries these four fields, and must never be used to seed the
 * full-profile cache (`jobProfileKeys.byPosition` / `.detail`).
 */
export interface JobProfileStatusByPosition {
  position_id: string;
  status: JobProfileStatus;
  job_profile_id: string;
  revision_number: number;
}

export interface JobProfileRevisionSummary {
  id: string;
  revision_number: number;
  status: JobProfileStatus;
  approved_at?: string | null;
  approved_by?: string | null;
  updated_at: string;
}

export type JobProfilePatch = Partial<{
  [K in JobProfileLongTextFieldKey]: JobProfileMultilingualField;
}> & {
  actualization_date?: string | null;
};

export interface JobProfileReasonPayload {
  reason: string;
}
