/**
 * EVALUATION_PANEL — REST fetchers (MVP 2 multi-evaluator).
 *
 * The wire is global SNAKE_CASE; the panel types mirror it 1:1 so no
 * case-translation adapter is needed (unlike the approval feature whose domain
 * types are camelCase). NO tenant_id is ever sent — the BE derives the active
 * tenant from the JWT.
 *
 * Mass-assignment guard: the create payload deliberately cannot carry the
 * server-computed fields (raw_total_score / displayed_total_score /
 * evaluator_count / assigned_grade_number). The server ignores them anyway.
 *
 * The averaged result fetcher 404s while collecting (REQ-ISO-5) and 403s
 * without CAMPAIGN_RESULTS_VIEW — callers must surface those, never compute a
 * client-side average.
 */
import { httpClient } from '@/shared/api/httpClient';
import { endpoints } from '@/shared/api/endpoints';
import { ApiError } from '@/shared/api/apiError';
import type {
  AssignEvaluatorPayload,
  BulkCreatePanelsPayload,
  BulkCreatePanelsResult,
  CreatePanelPayload,
  Panel,
  PanelAssignment,
  PanelDetail,
  PanelPageResponse,
  PanelResult,
  RosterSuggestions,
} from '../panelTypes';

export const panelKeys = {
  all: ['panels'] as const,
  list: (filters: PanelListFilters) => ['panels', 'list', filters] as const,
  detail: (id: string) => ['panels', 'detail', id] as const,
  result: (id: string) => ['panels', 'result', id] as const,
  rosterSuggestions: (projectId: string, departmentId: string) =>
    ['panels', 'roster-suggestions', projectId, departmentId] as const,
};

export interface PanelListFilters {
  projectId?: string;
  positionId?: string;
}

export async function createPanel(payload: CreatePanelPayload): Promise<Panel> {
  // Whitelist the fields explicitly — never spread an arbitrary object so a
  // mass-assignment field can never leak onto the wire.
  const body: CreatePanelPayload = {
    position_id: payload.position_id,
    methodology_version_id: payload.methodology_version_id,
    ...(payload.min_evaluators != null
      ? { min_evaluators: payload.min_evaluators }
      : {}),
  };
  const res = await httpClient.post<Panel>(endpoints.panels.list, body);
  return res.data;
}

/**
 * POST /panels/bulk-create — opens one panel per position with the SAME shared
 * roster in a SINGLE request (replaces the create-then-loop-assign sequence).
 * The body is whitelisted: only the three contract fields ever reach the wire,
 * so no mass-assignment / tenant_id field can leak. Returns the per-position
 * failure collector — partially-failed rows surface in `failed[]` while the rest
 * are created (no sibling rollback).
 */
export async function bulkCreatePanels(
  payload: BulkCreatePanelsPayload,
): Promise<BulkCreatePanelsResult> {
  const body: BulkCreatePanelsPayload = {
    methodology_version_id: payload.methodology_version_id,
    position_ids: payload.position_ids,
    ...(payload.roster && payload.roster.length > 0
      ? {
          roster: payload.roster.map((s) => ({
            evaluator_user_id: s.evaluator_user_id,
            evaluator_role: s.evaluator_role,
          })),
        }
      : {}),
  };
  const res = await httpClient.post<BulkCreatePanelsResult>(
    endpoints.panels.bulkCreate,
    body,
  );
  return res.data;
}

/**
 * GET /panels/roster-suggestions — ADVISORY dept-director candidates for the
 * chosen department. The query params are SNAKE_CASE and REQUIRED
 * (project_id / department_id). A 404 means the department is outside the
 * caller's ABAC subtree → we resolve to an EMPTY candidate list (seat stays
 * editable), never a hard error. Other errors propagate so the caller can show
 * the suggestion-failed (still editable) state.
 */
export async function fetchRosterSuggestions(
  projectId: string,
  departmentId: string,
): Promise<RosterSuggestions> {
  try {
    const res = await httpClient.get<RosterSuggestions>(
      endpoints.panels.rosterSuggestions,
      { params: { project_id: projectId, department_id: departmentId } },
    );
    return res.data;
  } catch (e) {
    if (e instanceof ApiError && e.isNotFound()) {
      return { department_id: departmentId, dept_director_candidates: [] };
    }
    throw e;
  }
}

export async function fetchPanels(
  filters: PanelListFilters = {},
): Promise<PanelPageResponse> {
  const res = await httpClient.get<unknown>(endpoints.panels.list, {
    params: {
      project_id: filters.projectId,
      position_id: filters.positionId,
    },
  });
  const env = (res.data ?? {}) as Partial<PanelPageResponse> & {
    items?: unknown[];
  };
  const items = Array.isArray(env.items) ? (env.items as Panel[]) : [];
  return {
    items,
    page: env.page ?? 0,
    size: env.size ?? items.length,
    total: env.total ?? items.length,
    total_pages: env.total_pages,
  };
}

export async function fetchPanelDetail(id: string): Promise<PanelDetail> {
  const res = await httpClient.get<PanelDetail>(endpoints.panels.detail(id));
  return res.data;
}

/**
 * Averaged result — gated by CAMPAIGN_RESULTS_VIEW. The backend returns 404
 * while the panel is collecting (no live running average) and 403 without the
 * permission. Callers must NOT swallow these into a fabricated average.
 */
export async function fetchPanelResult(id: string): Promise<PanelResult> {
  const res = await httpClient.get<PanelResult>(endpoints.panels.result(id));
  return res.data;
}

export async function assignEvaluator(
  panelId: string,
  payload: AssignEvaluatorPayload,
): Promise<PanelAssignment> {
  const res = await httpClient.post<PanelAssignment>(
    endpoints.panels.evaluators(panelId),
    {
      evaluator_user_id: payload.evaluator_user_id,
      evaluator_role: payload.evaluator_role,
    },
  );
  return res.data;
}

export async function withdrawEvaluator(
  panelId: string,
  userId: string,
): Promise<void> {
  await httpClient.delete(endpoints.panels.evaluator(panelId, userId));
}

export async function lockPanelRoster(panelId: string): Promise<Panel> {
  const res = await httpClient.post<Panel>(
    endpoints.panels.lockRoster(panelId),
    {},
  );
  return res.data;
}

export async function submitPanelToCeo(panelId: string): Promise<Panel> {
  const res = await httpClient.post<Panel>(endpoints.panels.submit(panelId), {});
  return res.data;
}

// ============================================================
// T3 (Defect 2) — panel management (delete / archive / reopen)
//
// Mirrors the evaluation delete/archive fetchers: DELETE carries the reason in
// the request BODY (axios `data`, exactly like {@link deleteEvaluation}), the
// archive POST carries `{ reason }`, and reopen carries no body. The BE enforces
// the status guard + EVALUATION_PANEL_MANAGE; a cross-tenant id 404s. We never
// send tenant_id (JWT-derived).
// ============================================================

export async function deletePanel(panelId: string, reason: string): Promise<void> {
  await httpClient.delete(endpoints.panels.delete(panelId), {
    data: { reason },
  });
}

export async function archivePanel(panelId: string, reason: string): Promise<Panel> {
  const res = await httpClient.post<Panel>(endpoints.panels.archive(panelId), {
    reason,
  });
  return res.data;
}

export async function reopenPanel(panelId: string): Promise<Panel> {
  const res = await httpClient.post<Panel>(endpoints.panels.reopen(panelId), {});
  return res.data;
}

/**
 * Feature 2 — reopen an APPROVED panel to add an ADDITIONAL expert.
 *
 * Backend contract:
 *   POST /panels/{panelId}/reopen-for-expert  (EVALUATION_PANEL_MANAGE)
 *   Body (snake_case): { additional_evaluator_user_id, reason ≥ 5 }
 *   Success: 200 with the updated PanelResponse (status AWAITING_EVALUATIONS).
 *   Errors: 400 PANEL_NOT_APPROVED_FOR_REOPEN (not APPROVED), 400 validation,
 *           403 (no permission), 404 (cross-tenant / out-of-subtree).
 *
 * The body is whitelisted to the two contract fields — no tenant_id / actor is
 * ever sent (JWT-derived server-side).
 */
export interface ReopenForExpertPayload {
  additionalEvaluatorUserId: string;
  reason: string;
}

export async function reopenPanelForExpert(
  panelId: string,
  payload: ReopenForExpertPayload,
): Promise<Panel> {
  const res = await httpClient.post<Panel>(
    endpoints.panels.reopenForExpert(panelId),
    {
      additional_evaluator_user_id: payload.additionalEvaluatorUserId,
      reason: payload.reason,
    },
  );
  return res.data;
}
