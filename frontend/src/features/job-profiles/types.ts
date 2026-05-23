import type { LocalizedString } from '@/shared/types/common';

export type JobProfileStatus = 'DRAFT' | 'UNDER_REVIEW' | 'APPROVED' | 'ARCHIVED';

export type JobProfileMultilingualField = LocalizedString;

/** 12 long-text multilingual fields per design-foundation §16 / PRD E6. */
export interface JobProfile {
  id: string;
  position_id: string;
  project_id: string;
  status: JobProfileStatus;
  revision_number: number;
  parent_revision_id?: string | null;

  // Role & responsibilities
  purpose: JobProfileMultilingualField;
  main_duties: JobProfileMultilingualField;
  responsibility_area: JobProfileMultilingualField;
  authority: JobProfileMultilingualField;
  kpi_expected_results: JobProfileMultilingualField;

  // Requirements
  education_requirements: JobProfileMultilingualField;
  experience_requirements: JobProfileMultilingualField;
  knowledge_skills: JobProfileMultilingualField;

  // Interactions
  internal_interactions: JobProfileMultilingualField;
  external_interactions: JobProfileMultilingualField;

  // Working context
  working_conditions: JobProfileMultilingualField;
  documents_regulations: JobProfileMultilingualField;
  actualization_date?: string;

  created_at: string;
  updated_at: string;
  approved_at?: string | null;
  approved_by?: string | null;
  archived_at?: string | null;
  archived_by?: string | null;
  archive_reason?: string | null;
}

export type JobProfileLongTextFieldKey =
  | 'purpose'
  | 'main_duties'
  | 'responsibility_area'
  | 'authority'
  | 'kpi_expected_results'
  | 'education_requirements'
  | 'experience_requirements'
  | 'knowledge_skills'
  | 'internal_interactions'
  | 'external_interactions'
  | 'working_conditions'
  | 'documents_regulations';

export const JOB_PROFILE_FIELDS: JobProfileLongTextFieldKey[] = [
  'purpose',
  'main_duties',
  'responsibility_area',
  'authority',
  'kpi_expected_results',
  'education_requirements',
  'experience_requirements',
  'knowledge_skills',
  'internal_interactions',
  'external_interactions',
  'working_conditions',
  'documents_regulations',
];

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
