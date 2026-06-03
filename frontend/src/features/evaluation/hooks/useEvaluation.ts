/**
 * Evaluation — React Query hooks.
 *
 * Mutations invalidate the relevant evaluation detail + scores so the
 * matrix + total banner + action bar re-render in lockstep.
 *
 * `usePreviewScore` is a mutation (NOT query) because results depend on
 * the live in-memory selections; caching stale previews would mislead
 * the evaluator. The hook returns a `debouncedPreview` helper that
 * coalesces rapid clicks within 300ms (PRD MVP1-E9-7).
 */
import {
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  approveEvaluation,
  archiveEvaluation,
  calibrateScore,
  createEvaluation,
  evaluationKeys,
  fetchCalibrationHistory,
  fetchEvaluation,
  fetchEvaluationScores,
  fetchEvaluations,
  lockEvaluation,
  previewScore,
  requestEvaluationChanges,
  submitEvaluation,
  upsertScore,
} from '../api/evaluationApi';
import type {
  CalibrateScorePayload,
  CreateEvaluationPayload,
  EvaluationListFilters,
  PreviewScorePayload,
  ReasonPayload,
  ScoringResult,
  UpsertScorePayload,
} from '../types';

// ---------- Queries ----------

export function useEvaluations(filters: EvaluationListFilters) {
  return useQuery({
    queryKey: evaluationKeys.list(filters),
    queryFn: () => fetchEvaluations(filters),
    enabled: !!filters.projectId,
  });
}

export function useEvaluation(id: string | undefined) {
  return useQuery({
    queryKey: id ? evaluationKeys.detail(id) : ['evaluations', 'detail', null],
    queryFn: () => fetchEvaluation(id!),
    enabled: !!id,
  });
}

export function useEvaluationScores(id: string | undefined) {
  return useQuery({
    queryKey: id ? evaluationKeys.scores(id) : ['evaluations', 'scores', null],
    queryFn: () => fetchEvaluationScores(id!),
    enabled: !!id,
  });
}

export function useCalibrationHistory(id: string | undefined) {
  return useQuery({
    queryKey: id
      ? evaluationKeys.calibrationHistory(id)
      : ['evaluations', 'calibration-history', null],
    queryFn: () => fetchCalibrationHistory(id!),
    enabled: !!id,
  });
}

// ---------- Mutations ----------

function invalidate(
  qc: ReturnType<typeof useQueryClient>,
  id?: string,
) {
  qc.invalidateQueries({ queryKey: evaluationKeys.all });
  if (id) {
    qc.invalidateQueries({ queryKey: evaluationKeys.detail(id) });
    qc.invalidateQueries({ queryKey: evaluationKeys.scores(id) });
    qc.invalidateQueries({ queryKey: evaluationKeys.calibrationHistory(id) });
  }
}

export function useCreateEvaluation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateEvaluationPayload) => createEvaluation(payload),
    onSuccess: (e) => invalidate(qc, e.id),
  });
}

export function useUpsertScore(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpsertScorePayload) => upsertScore(id, payload),
    onSuccess: () => invalidate(qc, id),
  });
}

export function useSubmitEvaluation(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => submitEvaluation(id),
    onSuccess: () => invalidate(qc, id),
  });
}

export function useApproveEvaluation(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => approveEvaluation(id),
    onSuccess: () => invalidate(qc, id),
  });
}

export function useRequestChanges(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: ReasonPayload) =>
      requestEvaluationChanges(id, payload),
    onSuccess: () => invalidate(qc, id),
  });
}

export function useLockEvaluation(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => lockEvaluation(id),
    onSuccess: () => invalidate(qc, id),
  });
}

export function useArchiveEvaluation(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: ReasonPayload) => archiveEvaluation(id, payload),
    onSuccess: () => invalidate(qc, id),
  });
}

export function useCalibrateScore(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CalibrateScorePayload) => calibrateScore(id, payload),
    onSuccess: () => invalidate(qc, id),
  });
}

/**
 * Live preview hook with 300ms debounce.
 *
 * Why a hook (not just a mutation): we need an in-flight cancel guard so
 * a stale request can't overwrite a fresher result if the user clicks
 * faster than the network responds.
 */
export function usePreviewScore() {
  const [data, setData] = useState<ScoringResult | null>(null);
  const [isPending, setIsPending] = useState(false);
  const requestIdRef = useRef(0);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(
    () => () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    },
    [],
  );

  const debouncedPreview = useCallback(
    (payload: PreviewScorePayload, debounceMs = 300) => {
      if (timerRef.current) clearTimeout(timerRef.current);
      const myId = ++requestIdRef.current;
      setIsPending(true);
      timerRef.current = setTimeout(() => {
        previewScore(payload)
          .then((res) => {
            // Discard stale responses.
            if (myId !== requestIdRef.current) return;
            setData(res);
            setIsPending(false);
          })
          .catch(() => {
            if (myId !== requestIdRef.current) return;
            setIsPending(false);
          });
      }, debounceMs);
    },
    [],
  );

  const reset = useCallback(() => {
    if (timerRef.current) clearTimeout(timerRef.current);
    requestIdRef.current += 1;
    setData(null);
    setIsPending(false);
  }, []);

  return { data, isPending, debouncedPreview, reset };
}
