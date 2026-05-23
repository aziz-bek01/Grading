import type { LocalizedString } from '@/shared/types/common';

export type QuestionnaireStatus = 'DRAFT' | 'IN_PROGRESS' | 'COMPLETED' | 'ARCHIVED';

export type QuestionType =
  | 'TEXT'
  | 'LONG_TEXT'
  | 'SINGLE_CHOICE'
  | 'MULTI_CHOICE'
  | 'RATING_SCALE'
  | 'NUMBER';

export interface JobAnalysisChoice {
  code: string;
  label: LocalizedString;
}

export interface JobAnalysisQuestion {
  id: string;
  code: string;
  question_type: QuestionType;
  prompt: LocalizedString;
  help_text?: LocalizedString;
  required: boolean;
  sort_order: number;
  choices?: JobAnalysisChoice[];
  scale_min?: number;
  scale_max?: number;
}

/** Answer value is union: string | number | string[] depending on type. */
export type JobAnalysisAnswerValue = string | number | string[] | null;

export interface JobAnalysisAnswer {
  question_id: string;
  value: JobAnalysisAnswerValue;
}

export interface JobAnalysisQuestionnaire {
  id: string;
  position_id: string;
  project_id: string;
  template_code: string;
  name: LocalizedString;
  status: QuestionnaireStatus;
  questions: JobAnalysisQuestion[];
  answers: JobAnalysisAnswer[];
  created_at: string;
  updated_at: string;
}

export interface JobAnalysisTemplate {
  code: string;
  name: LocalizedString;
  description?: LocalizedString;
  question_count: number;
}

export interface QuestionnaireListItem {
  id: string;
  name: LocalizedString;
  status: QuestionnaireStatus;
  template_code: string;
  updated_at: string;
  answered_count: number;
  required_count: number;
}
