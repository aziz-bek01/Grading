/**
 * Tenant-wide department source for the Access-scope department tree.
 *
 * Department scope is TENANT-scoped, but the org-structure tree the app already
 * renders is PROJECT-scoped (`useDepartmentTree(projectId)` →
 * `GET /departments/tree?projectId=`). To offer the full set of a tenant's
 * departments without reimplementing anything, this hook:
 *   1. REUSES the projects list query (`useProjects`, tenant-scoped via JWT) to
 *      enumerate the tenant's projects, then
 *   2. REUSES the org feature's `fetchDepartmentTree` fetcher (same endpoint the
 *      org page uses) once per project, and
 *   3. flattens + de-duplicates the rows into a single `Department[]` that the
 *      shared `buildDepartmentTree` (org `lib/tree.ts`) turns into the same tree
 *      the org page renders.
 *
 * The backend remains the source of truth: it independently validates that every
 * saved department id belongs to the tenant + the user is a member of it.
 */
import { useQueries } from '@tanstack/react-query';
import { useProjects } from '@/features/projects/hooks/useProjects';
import { fetchDepartmentTree, orgKeys } from '@/features/organization/api/organizationApi';
import type { Department } from '@/features/organization/types/organizationTypes';

export interface TenantDepartmentsResult {
  departments: Department[];
  isLoading: boolean;
  isError: boolean;
}

export function useTenantDepartments(): TenantDepartmentsResult {
  const projectsQuery = useProjects();
  const projectIds = (projectsQuery.data?.items ?? []).map((p) => p.id);

  const deptQueries = useQueries({
    queries: projectIds.map((projectId) => ({
      queryKey: orgKeys.tree(projectId),
      queryFn: () => fetchDepartmentTree(projectId),
    })),
  });

  // De-duplicate by department id (a department belongs to exactly one project,
  // but guard against overlap defensively).
  const byId = new Map<string, Department>();
  for (const q of deptQueries) {
    for (const d of q.data ?? []) {
      if (!byId.has(d.id)) byId.set(d.id, d);
    }
  }

  return {
    departments: [...byId.values()],
    isLoading: projectsQuery.isLoading || deptQueries.some((q) => q.isLoading),
    isError: projectsQuery.isError || deptQueries.some((q) => q.isError),
  };
}
