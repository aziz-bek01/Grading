import { useQuery } from '@tanstack/react-query';
import { useAuthStore } from '@/features/auth/authStore';
import { fetchAllPages } from '@/shared/api/fetchAllPages';
import { fetchUsers, userKeys } from '../api/userApi';
import type { UserListQuery } from '../types/userTypes';

/**
 * Lists users for the active tenant. Tenant id is included in the cache key
 * (FE-TI-004) so the list refetches when the user switches tenants — it is
 * never sent on the wire for non-super-admin roles (backend derives it from
 * the JWT). Super-admin callers may explicitly pass `query.tenant_id` to
 * cross tenants.
 *
 * ONE server page (`query.page` / `query.size`, defaulting to the backend's
 * own default of 20) — correct for a browsable, paginated users table. Do
 * NOT use this for a picker/lookup that must see every user; use
 * {@link useAllUsers} instead.
 */
export function useUsers(query: UserListQuery = {}) {
  const tenantScope = useAuthStore((s) => s.activeTenant?.id);
  return useQuery({
    queryKey: userKeys.list(tenantScope, query),
    queryFn: () => fetchUsers(query),
  });
}

/**
 * Loads the FULL user set for the active tenant via the shared
 * `fetchAllPages` helper — for pickers (EvaluatorPicker, OpenPanelDialog's
 * roster lookup) that must be able to select ANY user, not just whichever
 * ones happen to fit on the backend's first page (default size 20). See
 * EPIC-013: `useUsers()` with no pagination silently capped these pickers at
 * 20 users with no error, making the 21st+ evaluator un-assignable.
 *
 * Bounded by `fetchAllPages`'s safety cap; `data.truncated` is set when that
 * cap is hit — callers MUST surface it (never silently drop rows).
 */
export function useAllUsers(query: Omit<UserListQuery, 'page' | 'size'> = {}) {
  const tenantScope = useAuthStore((s) => s.activeTenant?.id);
  return useQuery({
    queryKey: userKeys.listAll(tenantScope, query),
    queryFn: () => fetchAllPages((page, size) => fetchUsers({ ...query, page, size })),
  });
}
