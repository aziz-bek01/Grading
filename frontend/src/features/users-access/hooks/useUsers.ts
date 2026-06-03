import { useQuery } from '@tanstack/react-query';
import { useAuthStore } from '@/features/auth/authStore';
import { fetchUsers, userKeys } from '../api/userApi';
import type { UserListQuery } from '../types/userTypes';

/**
 * Lists users for the active tenant. Tenant id is included in the cache key
 * (FE-TI-004) so the list refetches when the user switches tenants — it is
 * never sent on the wire for non-super-admin roles (backend derives it from
 * the JWT). Super-admin callers may explicitly pass `query.tenant_id` to
 * cross tenants.
 */
export function useUsers(query: UserListQuery = {}) {
  const tenantScope = useAuthStore((s) => s.activeTenant?.id);
  return useQuery({
    queryKey: userKeys.list(tenantScope, query),
    queryFn: () => fetchUsers(query),
  });
}
