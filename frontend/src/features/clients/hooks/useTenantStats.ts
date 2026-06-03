import { useQuery } from '@tanstack/react-query';
import { clientKeys, fetchTenantStats } from '../api/clientApi';

/**
 * Pulls aggregate stats for a tenant (project / user count + last activity).
 * Separated from useTenant so the KPI cards on the detail page can refresh
 * independently from the slower detail payload.
 */
export function useTenantStats(id: string | undefined) {
  return useQuery({
    queryKey: id ? clientKeys.tenantStats(id) : ['clients', 'tenants', 'stats', null],
    queryFn: () => fetchTenantStats(id!),
    enabled: !!id,
  });
}
