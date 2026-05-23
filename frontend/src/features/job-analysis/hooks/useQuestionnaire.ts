import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  archiveQuestionnaire,
  createQuestionnaire,
  fetchQuestionnaire,
  fetchQuestionnaires,
  fetchTemplates,
  jobAnalysisKeys,
  submitQuestionnaire,
  updateAnswer,
} from '../api/jobAnalysisApi';
import type { JobAnalysisAnswerValue } from '../types';

export function useQuestionnaires(positionId: string | undefined) {
  return useQuery({
    queryKey: positionId ? jobAnalysisKeys.list(positionId) : ['job-analysis', 'list', null],
    queryFn: () => fetchQuestionnaires(positionId!),
    enabled: !!positionId,
  });
}

export function useQuestionnaire(id: string | undefined) {
  return useQuery({
    queryKey: id ? jobAnalysisKeys.detail(id) : ['job-analysis', 'detail', null],
    queryFn: () => fetchQuestionnaire(id!),
    enabled: !!id,
  });
}

export function useTemplates() {
  return useQuery({
    queryKey: jobAnalysisKeys.templates(),
    queryFn: fetchTemplates,
  });
}

export function useCreateQuestionnaire(positionId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (templateCode: string) =>
      createQuestionnaire({ position_id: positionId, template_code: templateCode }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: jobAnalysisKeys.list(positionId) });
    },
  });
}

export function useUpdateAnswer(questionnaireId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: { question_id: string; value: JobAnalysisAnswerValue }) =>
      updateAnswer(questionnaireId, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: jobAnalysisKeys.detail(questionnaireId) });
    },
  });
}

export function useSubmitQuestionnaire(questionnaireId: string, positionId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => submitQuestionnaire(questionnaireId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: jobAnalysisKeys.detail(questionnaireId) });
      if (positionId) qc.invalidateQueries({ queryKey: jobAnalysisKeys.list(positionId) });
    },
  });
}

export function useArchiveQuestionnaire(questionnaireId: string, positionId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (reason: string) => archiveQuestionnaire(questionnaireId, { reason }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: jobAnalysisKeys.detail(questionnaireId) });
      if (positionId) qc.invalidateQueries({ queryKey: jobAnalysisKeys.list(positionId) });
    },
  });
}
