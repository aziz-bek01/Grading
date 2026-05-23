import { httpClient } from '@/shared/api/httpClient';
import { endpoints } from '@/shared/api/endpoints';
import type {
  Project,
  ProjectCreatePayload,
  ProjectList,
  ProjectUpdatePayload,
} from '../types/projectTypes';
import type { WorkflowProgressResponse } from '@/shared/components/workflow/workflowTypes';

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
  workflowProgress: (id: string) => ['projects', 'workflow-progress', id] as const,
};

/**
 * Fetch the projects visible to the active tenant.
 *
 * Tenant is resolved by the backend from the JWT — we MUST NOT send
 * `tenant_id` / `tenantId` in the query string or body. See D-202 / F-208.
 */
export async function fetchProjects(): Promise<ProjectList> {
  const res = await httpClient.get<ProjectList>(endpoints.projects.list);
  return res.data;
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

export async function fetchWorkflowProgress(projectId: string): Promise<WorkflowProgressResponse> {
  const res = await httpClient.get<WorkflowProgressResponse>(endpoints.projects.workflowProgress(projectId));
  return res.data;
}
