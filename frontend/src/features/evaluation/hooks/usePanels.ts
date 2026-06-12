/**
 * EVALUATION_PANEL — React Query hooks (MVP 2 multi-evaluator).
 *
 * The averaged-result query ({@link usePanelResult}) is gated by the caller
 * (status >= AVERAGED AND CAMPAIGN_RESULTS_VIEW) via `enabled`, so we never
 * fire a request the BE would 404/403 — and we never compute a client-side
 * average from a partial roster.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError } from '@/shared/api/apiError';
import {
  assignEvaluator,
  bulkCreatePanels,
  createPanel,
  fetchPanelDetail,
  fetchPanelResult,
  fetchPanels,
  fetchRosterSuggestions,
  lockPanelRoster,
  panelKeys,
  submitPanelToCeo,
  withdrawEvaluator,
  type PanelListFilters,
} from '../api/panelApi';
import type {
  AssignEvaluatorPayload,
  BulkCreatePanelsPayload,
  CreatePanelPayload,
} from '../panelTypes';

// ---------- Queries ----------

export function usePanels(filters: PanelListFilters = {}) {
  return useQuery({
    queryKey: panelKeys.list(filters),
    queryFn: () => fetchPanels(filters),
    enabled: !!filters.projectId || !!filters.positionId,
  });
}

export function usePanelDetail(id: string | undefined) {
  return useQuery({
    queryKey: id ? panelKeys.detail(id) : ['panels', 'detail', null],
    queryFn: () => fetchPanelDetail(id!),
    enabled: !!id,
  });
}

/**
 * Averaged result. `enabled` MUST already encode the visibility gate
 * (status >= AVERAGED AND CAMPAIGN_RESULTS_VIEW) so the request only fires when
 * the BE will serve it. A 404 (collecting) / 403 (no permission) is surfaced as
 * an ApiError — never swallowed into a fake average. We do not retry those.
 */
export function usePanelResult(id: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: id ? panelKeys.result(id) : ['panels', 'result', null],
    queryFn: () => fetchPanelResult(id!),
    enabled: !!id && enabled,
    retry: (failureCount, error) => {
      if (error instanceof ApiError && (error.isForbidden() || error.isNotFound())) {
        return false;
      }
      return failureCount < 1;
    },
  });
}

/**
 * ADVISORY dept-director suggestions for the wizard's DEPT_DIRECTOR seat. The
 * fetcher already maps a 404 (out-of-subtree) to an empty list, so a "not
 * visible" department resolves to no suggestions (seat editable) rather than an
 * error. We only fire when both ids are present. We do NOT retry — a transient
 * failure leaves the seat editable, which is the desired empty/error state.
 */
export function useRosterSuggestions(
  projectId: string | undefined,
  departmentId: string | undefined,
) {
  return useQuery({
    queryKey:
      projectId && departmentId
        ? panelKeys.rosterSuggestions(projectId, departmentId)
        : ['panels', 'roster-suggestions', null],
    queryFn: () => fetchRosterSuggestions(projectId!, departmentId!),
    enabled: !!projectId && !!departmentId,
    retry: false,
  });
}

// ---------- Mutations ----------

function invalidatePanel(
  qc: ReturnType<typeof useQueryClient>,
  id?: string,
) {
  qc.invalidateQueries({ queryKey: panelKeys.all });
  if (id) {
    qc.invalidateQueries({ queryKey: panelKeys.detail(id) });
    qc.invalidateQueries({ queryKey: panelKeys.result(id) });
  }
}

export function useCreatePanel() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreatePanelPayload) => createPanel(payload),
    onSuccess: (p) => invalidatePanel(qc, p.id),
  });
}

/**
 * Bulk-create panels (dept-first wizard). One request opens one panel per
 * position with the shared roster. Invalidates the panel list so the
 * already-paneled coverage marks + per-department progress refresh after a
 * commission. Per-position failures are surfaced by the caller from the result,
 * NOT thrown — only a transport/validation error rejects the mutation.
 */
export function useBulkCreatePanels() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: BulkCreatePanelsPayload) => bulkCreatePanels(payload),
    onSuccess: () => invalidatePanel(qc),
  });
}

export function useAssignEvaluator(panelId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: AssignEvaluatorPayload) =>
      assignEvaluator(panelId, payload),
    onSuccess: () => invalidatePanel(qc, panelId),
  });
}

export function useWithdrawEvaluator(panelId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (userId: string) => withdrawEvaluator(panelId, userId),
    onSuccess: () => invalidatePanel(qc, panelId),
  });
}

export function useLockPanelRoster(panelId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => lockPanelRoster(panelId),
    onSuccess: () => invalidatePanel(qc, panelId),
  });
}

export function useSubmitPanelToCeo(panelId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => submitPanelToCeo(panelId),
    onSuccess: () => invalidatePanel(qc, panelId),
  });
}
