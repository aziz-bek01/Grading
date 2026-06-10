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
  createRole,
  deleteRole,
  fetchPermissionCatalogTemplate,
  fetchRoleCatalog,
  fetchRolePermissions,
  roleKeys,
  setRolePermissions,
  updateRole,
  type CreateRolePayload,
  type UpdateRolePayload,
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
 * Full permission catalog as an empty template (every code unchecked) for the
 * custom-role CREATE form (slice E3). `enabled` is the drawer-open flag so the
 * catalog is only fetched while the create drawer is mounted.
 */
export function usePermissionCatalogTemplate(enabled: boolean) {
  return useQuery({
    queryKey: ['roles', 'permission-catalog-template'] as const,
    queryFn: () => fetchPermissionCatalogTemplate(),
    enabled,
    staleTime: 5 * 60 * 1000,
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

/**
 * Create a tenant custom role (slice E3). On success invalidates the WHOLE
 * `roles` key tree so both the admin catalog and every assignable-role picker
 * pick up the new role immediately (it becomes grantable right away).
 */
export function useCreateRole() {
  const { i18n } = useTranslation();
  const locale = i18n.language;
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateRolePayload) => createRole(payload, locale),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: roleKeys.all });
    },
  });
}

/**
 * Edit a custom role's name and/or permissions (slice E3), addressed by id.
 * Invalidates the catalog (name/grant changes) AND the per-CODE permission
 * matrix query (so the E2 detail editor re-seeds from the server) — the code is
 * passed alongside the id purely to target that matrix key.
 */
export function useUpdateRole(roleCode: string) {
  const { i18n } = useTranslation();
  const locale = i18n.language;
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { roleId: string; payload: UpdateRolePayload }) =>
      updateRole(vars.roleId, vars.payload, locale),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: roleKeys.all });
      void qc.invalidateQueries({ queryKey: roleKeys.permissions(roleCode) });
    },
  });
}

/**
 * Delete a custom role (slice E3), addressed by id. Invalidates the catalog so
 * the row disappears. A 409 ROLE_IN_USE is surfaced for the caller to map.
 */
export function useDeleteRole() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (roleId: string) => deleteRole(roleId),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: roleKeys.all });
    },
  });
}
