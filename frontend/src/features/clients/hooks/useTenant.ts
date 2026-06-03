import { useQuery } from '@tanstack/react-query';
import { clientKeys, fetchTenant } from '../api/clientApi';

export function useTenant(id: string | undefined) {
  return useQuery({
    queryKey: id ? clientKeys.tenant(id) : ['clients', 'tenants', 'detail', null],
    queryFn: () => fetchTenant(id!),
    enabled: !!id,
  });
}
