/**
 * Bulk Evaluation by Factor — TanStack Query hooks.
 *
 * Why a dedicated file (not extending `useEvaluation.ts`):
 *   - The by-factor list has a fundamentally different shape
 *     (`EvaluationByFactorRow` vs `Evaluation`) and would otherwise
 *     pollute the per-evaluation cache invalidations.
 *   - Bulk mutations invalidate the by-factor list AND the legacy
 *     `evaluations.all` key so the by-position view stays in sync.
 *
 * FE-TI-004 tenant scope: the query key includes the full filter
 * object (`{ projectId, factorId, status, departmentId, ... }`).
 * Tenant id itself never appears in the key — the backend derives it
 * from the JWT (security blueprint API-13).
 */
import {
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import {
  bulkScoreSet,
  bulkSubmit,
  evaluationKeys,
  fetchEvaluationsByFactor,
} from '../api/evaluationApi';
import type {
  BulkScorePayload,
  BulkSubmitPayload,
  EvaluationsByFactorFilters,
} from '../types';

export function useEvaluationsByFactor(filters: EvaluationsByFactorFilters) {
  return useQuery({
    queryKey: evaluationKeys.byFactor(filters),
    queryFn: () => fetchEvaluationsByFactor(filters),
    enabled: Boolean(filters.projectId) && Boolean(filters.factorId),
    // Keep previous data visible while the user pivots between factors
    // so the K-sheet layout does not flash an empty state on every tab.
    placeholderData: (prev) => prev,
  });
}

export function useBulkScoreSet(factorId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: BulkScorePayload) => bulkScoreSet(factorId, payload),
    onSuccess: () => {
      // Invalidate everything under `evaluations` — covers by-factor list,
      // per-evaluation detail/scores (for evaluators who drill in later),
      // and the legacy by-position list so the K-sheet and drill-down stay
      // numerically consistent without a page refresh.
      qc.invalidateQueries({ queryKey: evaluationKeys.all });
    },
  });
}

export function useBulkSubmit(factorId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: BulkSubmitPayload) => bulkSubmit(factorId, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: evaluationKeys.all });
    },
  });
}
