import { httpClient } from '@/shared/api/httpClient';
import { endpoints } from '@/shared/api/endpoints';
import { fetchAllPages } from '@/shared/api/fetchAllPages';
import type {
  Project,
  ProjectCreatePayload,
  ProjectList,
  ProjectUpdatePayload,
} from '../types/projectTypes';

/**
 * React-Query cache keys.
 *
 * NOTE on `list(tenantScope)`: the tenant identifier is included in the cache
 * key ONLY to bust the cache when the user switches tenants — it is NEVER
 * sent on the wire. The backend derives the active tenant from the JWT
 * (security blueprint API-13). See `fetchProjects` below.
 */
export const projectKeys = {
  all: ['projects'] as const,
  list: (tenantScope?: string) => ['projects', 'list', tenantScope ?? null] as const,
  detail: (id: string) => ['projects', 'detail', id] as const,
  /** @deprecated Phase 2 cache key — workflow progress moved to features/workflow. */
  workflowProgress: (id: string) => ['projects', 'workflow-progress', id] as const,
};

/** One page of the tenant's projects — the raw wire fetch `fetchAllPages` drives. */
async function fetchProjectsPage(page: number, size: number): Promise<ProjectList> {
  const res = await httpClient.get<ProjectList>(endpoints.projects.list, {
    params: { page, size },
  });
  return res.data;
}

/**
 * Fetch EVERY project visible to the active tenant.
 *
 * Tenant is resolved by the backend from the JWT — we MUST NOT send
 * `tenant_id` / `tenantId` in the query string or body. See D-202 / F-208.
 *
 * Pages to completion via the shared `fetchAllPages` helper instead of a
 * single unbounded GET: the backend applies its own default page size (20)
 * when none is requested, which used to silently cap this list at 20 — a
 * tenant with more projects lost the rest with no error, and pickers built on
 * top of it (AccessScopeSection's project-scope checklist) could never grant
 * access to project #21+ (EPIC-013). Bounded by `fetchAllPages`'s safety cap;
 * `truncated` surfaces if that cap is ever hit — see `ProjectList.truncated`.
 */
export async function fetchProjects(): Promise<ProjectList> {
  const { items, totalElements, truncated } = await fetchAllPages(fetchProjectsPage);
  return {
    items,
    page: 0,
    size: items.length,
    total_elements: totalElements,
    total_pages: 1,
    truncated,
  };
}

export async function fetchProject(id: string): Promise<Project> {
  const res = await httpClient.get<Project>(endpoints.projects.detail(id));
  return res.data;
}

export async function createProject(payload: ProjectCreatePayload): Promise<Project> {
  const res = await httpClient.post<Project>(endpoints.projects.list, payload);
  return res.data;
}

export async function updateProject(id: string, payload: ProjectUpdatePayload): Promise<Project> {
  const res = await httpClient.patch<Project>(endpoints.projects.detail(id), payload);
  return res.data;
}

/**
 * Archive (soft-delete) a project — status → ARCHIVED.
 *
 * POST /projects/{id}/archive (PROJECT_EDIT). The backend rejects archiving an
 * already-ARCHIVED/LOCKED project; the UI also hides the action for those
 * statuses so the round-trip is normally avoided.
 */
export async function archiveProject(id: string): Promise<Project> {
  const res = await httpClient.post<Project>(endpoints.projects.archive(id));
  return res.data;
}

// Re-export so noTenantIdLeak test + legacy callers keep working.
export { fetchWorkflowProgress } from '@/features/workflow/api/workflowApi';
