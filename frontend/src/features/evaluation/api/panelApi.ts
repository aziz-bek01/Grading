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
import type {
  AssignEvaluatorPayload,
  CreatePanelPayload,
  Panel,
  PanelAssignment,
  PanelDetail,
  PanelPageResponse,
  PanelResult,
} from '../panelTypes';

export const panelKeys = {
  all: ['panels'] as const,
  list: (filters: PanelListFilters) => ['panels', 'list', filters] as const,
  detail: (id: string) => ['panels', 'detail', id] as const,
  result: (id: string) => ['panels', 'result', id] as const,
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
