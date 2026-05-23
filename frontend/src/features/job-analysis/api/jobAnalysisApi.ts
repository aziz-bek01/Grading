import { httpClient } from '@/shared/api/httpClient';
import type {
  JobAnalysisAnswerValue,
  JobAnalysisQuestionnaire,
  QuestionnaireListItem,
  JobAnalysisTemplate,
} from '../types';

export const jobAnalysisKeys = {
  all: ['job-analysis'] as const,
  list: (positionId: string) => ['job-analysis', 'list', positionId] as const,
  detail: (id: string) => ['job-analysis', 'detail', id] as const,
  templates: () => ['job-analysis', 'templates'] as const,
};

const base = '/job-analysis';

export async function fetchQuestionnaires(
  positionId: string,
): Promise<{ items: QuestionnaireListItem[] }> {
  const res = await httpClient.get<{ items: QuestionnaireListItem[] }>(`${base}/by-position/${positionId}`);
  return res.data;
}

export async function fetchQuestionnaire(id: string): Promise<JobAnalysisQuestionnaire> {
  const res = await httpClient.get<JobAnalysisQuestionnaire>(`${base}/${id}`);
  return res.data;
}

export async function fetchTemplates(): Promise<{ items: JobAnalysisTemplate[] }> {
  const res = await httpClient.get<{ items: JobAnalysisTemplate[] }>(`${base}/templates`);
  return res.data;
}

export async function createQuestionnaire(payload: {
  position_id: string;
  template_code: string;
}): Promise<JobAnalysisQuestionnaire> {
  const res = await httpClient.post<JobAnalysisQuestionnaire>(base, payload);
  return res.data;
}

export async function updateAnswer(
  questionnaireId: string,
  payload: { question_id: string; value: JobAnalysisAnswerValue },
): Promise<JobAnalysisQuestionnaire> {
  const res = await httpClient.patch<JobAnalysisQuestionnaire>(
    `${base}/${questionnaireId}/answers`,
    payload,
  );
  return res.data;
}

export async function submitQuestionnaire(id: string): Promise<JobAnalysisQuestionnaire> {
  const res = await httpClient.post<JobAnalysisQuestionnaire>(`${base}/${id}/submit`, {});
  return res.data;
}

export async function archiveQuestionnaire(
  id: string,
  payload: { reason: string },
): Promise<JobAnalysisQuestionnaire> {
  const res = await httpClient.post<JobAnalysisQuestionnaire>(`${base}/${id}/archive`, payload);
  return res.data;
}
