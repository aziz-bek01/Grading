import { useQuery } from '@tanstack/react-query';
import { fetchProjects, projectKeys } from '../api/projectApi';
import { useAuthStore } from '@/features/auth/authStore';

/**
 * Tenant id is used ONLY as part of the React-Query cache key so the cache
 * is busted when the user switches between tenants. It is NOT passed to
 * `fetchProjects` — the backend derives the active tenant from the JWT.
 * See D-202 / D-217 / F-208.
 */
export function useProjects() {
  const tenantScope = useAuthStore((s) => s.activeTenant?.id);
  return useQuery({
    queryKey: projectKeys.list(tenantScope),
    queryFn: () => fetchProjects(),
  });
}
