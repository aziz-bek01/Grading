import { httpClient } from '@/shared/api/httpClient';
import type { LocalizedString } from '@/shared/types/common';
import type {
  JobAnalysisAnswer,
  JobAnalysisAnswerValue,
  JobAnalysisChoice,
  JobAnalysisQuestion,
  JobAnalysisQuestionnaire,
  JobAnalysisTemplate,
  QuestionnaireListItem,
  QuestionnaireStatus,
  QuestionType,
} from '../types';

export const jobAnalysisKeys = {
  all: ['job-analysis'] as const,
  list: (positionId: string) => ['job-analysis', 'list', positionId] as const,
  detail: (id: string) => ['job-analysis', 'detail', id] as const,
  templates: () => ['job-analysis', 'templates'] as const,
};

/**
 * Backend wire shapes (global SNAKE_CASE Jackson). These are the ONLY place we
 * speak the server's vocabulary; everything below this section adapts them to
 * the FE domain types. Three controllers are involved:
 *   - PositionQuestionnaireController → /positions/{positionId}/questionnaires
 *   - QuestionnaireController          → /questionnaires/{id}[...]
 *   - QuestionnaireTemplateController  → /questionnaire-templates
 */
type I18nMap = LocalizedString;

interface BackendQuestion {
  id: string;
  code: string;
  prompt_i18n: I18nMap;
  help_text_i18n: I18nMap;
  question_type: QuestionType;
  /** Choice codes for *_CHOICE; numeric scale points ("1".."5") for RATING_SCALE. */
  options: string[] | null;
  required: boolean;
  sort_order: number;
}

interface BackendQuestionnaire {
  id: string;
  project_id: string;
  position_id: string;
  template_code: string;
  name_i18n: I18nMap;
  status: QuestionnaireStatus;
  version: number;
  questions: BackendQuestion[];
}

interface BackendAnswer {
  id: string;
  questionnaire_id: string;
  question_id: string;
  answer_text: string | null;
  answer_choices: string[] | null;
  answer_number: number | null;
  respondent_user_id: string | null;
  submitted_at: string | null;
}

interface BackendTemplateListItem {
  code: string;
  name_i18n: I18nMap;
  question_count: number;
}

// ---------------------------------------------------------------------------
// Adapters (ONE per resource; no duplication elsewhere)
// ---------------------------------------------------------------------------

const CHOICE_TYPES: ReadonlySet<QuestionType> = new Set(['SINGLE_CHOICE', 'MULTI_CHOICE']);

/**
 * Backend exposes only `options: string[]` (no per-locale labels). For choice
 * questions we surface each option code as a `JobAnalysisChoice` whose label is
 * the code itself across locales (a readable, deterministic fallback).
 */
function adaptChoices(options: string[] | null | undefined): JobAnalysisChoice[] | undefined {
  if (!options || options.length === 0) return undefined;
  return options.map((code) => ({
    code,
    label: {
      'ru-RU': code,
      'uz-Cyrl-UZ': code,
      'uz-Latn-UZ': code,
      'en-US': code,
    },
  }));
}

/** RATING_SCALE has no explicit bounds; derive from numeric `options`. */
function adaptScale(options: string[] | null | undefined): { min: number; max: number } {
  const nums = (options ?? []).map((o) => Number(o)).filter((n) => Number.isFinite(n));
  if (nums.length === 0) return { min: 1, max: 5 };
  return { min: Math.min(...nums), max: Math.max(...nums) };
}

function adaptQuestion(q: BackendQuestion): JobAnalysisQuestion {
  const out: JobAnalysisQuestion = {
    id: q.id,
    code: q.code,
    question_type: q.question_type,
    prompt: q.prompt_i18n ?? {},
    help_text:
      q.help_text_i18n && Object.keys(q.help_text_i18n).length > 0 ? q.help_text_i18n : undefined,
    required: q.required,
    sort_order: q.sort_order,
  };
  if (CHOICE_TYPES.has(q.question_type)) {
    out.choices = adaptChoices(q.options);
  } else if (q.question_type === 'RATING_SCALE') {
    const scale = adaptScale(q.options);
    out.scale_min = scale.min;
    out.scale_max = scale.max;
  }
  return out;
}

/** Reduce a typed backend answer down to the FE union `value`. */
function answerToValue(question: JobAnalysisQuestion | undefined, a: BackendAnswer): JobAnalysisAnswerValue {
  const type = question?.question_type;
  switch (type) {
    case 'SINGLE_CHOICE':
      return a.answer_choices?.[0] ?? null;
    case 'MULTI_CHOICE':
      return a.answer_choices ?? [];
    case 'NUMBER':
    case 'RATING_SCALE':
      return a.answer_number ?? null;
    default:
      // TEXT / LONG_TEXT (and unknown) → text.
      return a.answer_text ?? null;
  }
}

/** Project the FE union `value` onto the right backend request field by type. */
function valueToRequestBody(
  questionType: QuestionType,
  value: JobAnalysisAnswerValue,
): { answer_text?: string | null; answer_choices?: string[] | null; answer_number?: number | null } {
  switch (questionType) {
    case 'SINGLE_CHOICE':
      return { answer_choices: typeof value === 'string' ? [value] : value == null ? [] : (value as string[]) };
    case 'MULTI_CHOICE':
      return { answer_choices: Array.isArray(value) ? value : value == null ? [] : [String(value)] };
    case 'NUMBER':
    case 'RATING_SCALE':
      return { answer_number: typeof value === 'number' ? value : null };
    default:
      // TEXT / LONG_TEXT.
      return { answer_text: typeof value === 'string' ? value : null };
  }
}

/**
 * Single questionnaire adapter — merges the separately-served answers
 * (`GET /questionnaires/{id}/answers`) into the FE questionnaire shape.
 * Questions are mapped exactly once (here) and reused to type the answers.
 */
function adaptQuestionnaire(
  q: BackendQuestionnaire,
  answers: BackendAnswer[],
): JobAnalysisQuestionnaire {
  const questions = q.questions.map(adaptQuestion).sort((a, b) => a.sort_order - b.sort_order);
  const questionById = new Map(questions.map((qq) => [qq.id, qq]));
  const mappedAnswers: JobAnalysisAnswer[] = answers.map((a) => ({
    question_id: a.question_id,
    value: answerToValue(questionById.get(a.question_id), a),
  }));
  return {
    id: q.id,
    position_id: q.position_id,
    project_id: q.project_id,
    template_code: q.template_code,
    name: q.name_i18n ?? {},
    status: q.status,
    questions,
    answers: mappedAnswers,
    // Backend QuestionnaireResponse has no timestamps; leave empty (FE only
    // shows them cosmetically and the list endpoint also lacks them).
    created_at: '',
    updated_at: '',
  };
}

// ---------------------------------------------------------------------------
// API functions
// ---------------------------------------------------------------------------

/**
 * GET /positions/{positionId}/questionnaires → bare `QuestionnaireResponse[]`.
 * The list endpoint carries no answer/timestamp counts, so `answered_count` is
 * not derivable here (set 0); `required_count` is derived from the questions.
 */
export async function fetchQuestionnaires(
  positionId: string,
): Promise<{ items: QuestionnaireListItem[] }> {
  const res = await httpClient.get<BackendQuestionnaire[]>(
    `/positions/${positionId}/questionnaires`,
  );
  const items: QuestionnaireListItem[] = (res.data ?? []).map((q) => ({
    id: q.id,
    name: q.name_i18n ?? {},
    status: q.status,
    template_code: q.template_code,
    // Backend QuestionnaireResponse has no updated_at — list-level timestamp
    // is unavailable; leave empty until a richer list DTO exists.
    updated_at: '',
    // Not exposed by the list endpoint; would require N answer fetches.
    answered_count: 0,
    required_count: q.questions.filter((qq) => qq.required).length,
  }));
  return { items };
}

/**
 * GET /questionnaires/{id} + GET /questionnaires/{id}/answers, merged.
 * Two calls because the backend serves answers from a separate endpoint.
 */
export async function fetchQuestionnaire(id: string): Promise<JobAnalysisQuestionnaire> {
  const [qRes, aRes] = await Promise.all([
    httpClient.get<BackendQuestionnaire>(`/questionnaires/${id}`),
    httpClient.get<BackendAnswer[]>(`/questionnaires/${id}/answers`),
  ]);
  return adaptQuestionnaire(qRes.data, aRes.data ?? []);
}

/** GET /questionnaire-templates → `{ items: BackendTemplateListItem[] }`. */
export async function fetchTemplates(): Promise<{ items: JobAnalysisTemplate[] }> {
  const res = await httpClient.get<{ items: BackendTemplateListItem[] }>('/questionnaire-templates');
  const items: JobAnalysisTemplate[] = (res.data?.items ?? []).map((t) => ({
    code: t.code,
    name: t.name_i18n ?? {},
    // Backend template catalogue has no description.
    description: undefined,
    question_count: t.question_count,
  }));
  return { items };
}

/**
 * POST /positions/{positionId}/questionnaires with body `{ template_code }`.
 * The create response has no answers yet, so we merge an empty answer list.
 */
export async function createQuestionnaire(payload: {
  position_id: string;
  template_code: string;
}): Promise<JobAnalysisQuestionnaire> {
  const res = await httpClient.post<BackendQuestionnaire>(
    `/positions/${payload.position_id}/questionnaires`,
    { template_code: payload.template_code },
  );
  return adaptQuestionnaire(res.data, []);
}

/**
 * PATCH /questionnaires/{questionnaireId}/answers/{questionId}.
 * The backend body field depends on the question type (text/choices/number),
 * so we re-fetch the questionnaire to resolve the type, then PATCH and re-read
 * the merged questionnaire (the PATCH returns only the single AnswerResponse).
 */
export async function updateAnswer(
  questionnaireId: string,
  payload: { question_id: string; value: JobAnalysisAnswerValue },
): Promise<JobAnalysisQuestionnaire> {
  const current = await fetchQuestionnaire(questionnaireId);
  const question = current.questions.find((q) => q.id === payload.question_id);
  const body = valueToRequestBody(question?.question_type ?? 'TEXT', payload.value);
  await httpClient.patch<BackendAnswer>(
    `/questionnaires/${questionnaireId}/answers/${payload.question_id}`,
    body,
  );
  return fetchQuestionnaire(questionnaireId);
}

export async function submitQuestionnaire(id: string): Promise<JobAnalysisQuestionnaire> {
  const res = await httpClient.post<BackendQuestionnaire>(`/questionnaires/${id}/submit`, {});
  // Submit returns the questionnaire (no answers); re-merge current answers.
  const answers = await httpClient.get<BackendAnswer[]>(`/questionnaires/${id}/answers`);
  return adaptQuestionnaire(res.data, answers.data ?? []);
}

export async function archiveQuestionnaire(
  id: string,
  payload: { reason: string },
): Promise<JobAnalysisQuestionnaire> {
  const res = await httpClient.post<BackendQuestionnaire>(`/questionnaires/${id}/archive`, payload);
  return adaptQuestionnaire(res.data, []);
}
