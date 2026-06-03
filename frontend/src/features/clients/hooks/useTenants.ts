import { useQuery } from '@tanstack/react-query';
import { clientKeys, fetchTenants } from '../api/clientApi';
import type { TenantListQuery } from '../types/clientTypes';

/**
 * Lists tenants for the HRLab portfolio (HRLAB_SUPER_ADMIN scope).
 * Not scoped by active tenant — this is portfolio-wide on purpose.
 */
export function useTenants(query: TenantListQuery = {}) {
  return useQuery({
    queryKey: clientKeys.tenants(query),
    queryFn: () => fetchTenants(query),
  });
}
