/**
 * TanStack Query hooks for the Roles admin permission matrix (slice E2).
 *
 * Reuses the E1 `rolesApi` (extended in slice E2) — no duplicated client. The
 * full role catalog feeds the Roles LIST page; the per-role matrix feeds the
 * detail page. The mutation invalidates the matrix query so the UI reflects the
 * server's canonical set after a save.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import {
  fetchRoleCatalog,
  fetchRolePermissions,
  roleKeys,
  setRolePermissions,
} from '@/features/users-access/api/rolesApi';

/**
 * Full role catalog for the Roles admin list (NOT trimmed to assignable-only).
 * Locale is part of the cache key so localized names refresh on language switch.
 */
export function useRoleCatalog() {
  const { i18n } = useTranslation();
  const locale = i18n.language;
  return useQuery({
    queryKey: roleKeys.catalog(locale),
    queryFn: () => fetchRoleCatalog(locale),
    staleTime: 5 * 60 * 1000,
  });
}

/**
 * Permission matrix for a single role. Disabled until a non-empty role code is
 * available (the detail route always supplies one, but this keeps the hook safe
 * for conditional callers).
 */
export function useRolePermissions(roleCode: string | undefined) {
  return useQuery({
    queryKey: roleKeys.permissions(roleCode ?? ''),
    queryFn: () => fetchRolePermissions(roleCode as string),
    enabled: Boolean(roleCode),
  });
}

/**
 * Replace-set mutation for a role's granted permissions. On success it
 * invalidates the matrix query for that role so the saved state is re-fetched
 * (the server is the source of truth for the resulting granted/restricted set).
 */
export function useSetRolePermissions(roleCode: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (permissionCodes: string[]) => setRolePermissions(roleCode, permissionCodes),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: roleKeys.permissions(roleCode) });
    },
  });
}
