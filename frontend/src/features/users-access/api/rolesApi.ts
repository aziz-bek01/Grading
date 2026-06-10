/**
 * Role catalog API client (slice E1).
 *
 * Backs the DATA-DRIVEN role pickers in the invite / assign-role / add-membership
 * dialogs. Previously those pickers were rendered from hardcoded grantable-role
 * arrays in `userSchemas.ts` (CLIENT_GRANTABLE_ROLES / SUPER_ADMIN_GRANTABLE_ROLES),
 * which made some roles permanently unassignable and duplicated the list 3×.
 *
 * Contract: `GET /api/v1/roles?assignableOnly=true` → array of role catalog rows
 * (snake_case on the wire). The backend is the source of truth for WHO may grant
 * WHICH role: each row carries `assignable_by_caller` (+ `reason_if_not` when the
 * caller cannot grant it), computed from the caller's permissions/scope. The UI
 * renders every returned role, enables the assignable ones, and disables the rest
 * with a localized hint. The backend still re-enforces on submit.
 *
 * Tenant rule: NO `tenant_id` is sent — the backend derives caller scope from the
 * JWT (auth-context header), exactly like the rest of the users-access module.
 */
import { httpClient } from '@/shared/api/httpClient';
import { pickLocalized } from '@/shared/lib/localized';
import type { LocalizedString } from '@/shared/types/common';

/** Why a returned role is not grantable by the current caller. */
export type RoleAssignBlockReason = 'HRLAB_ONLY' | 'PLATFORM_SCOPE';

/** Role scope — platform-level (HRLab) vs. tenant-level (client company). */
export type RoleScope = 'PLATFORM' | 'TENANT';

/**
 * Wire shape for one row of `GET /roles` (snake_case). Mirrors the backend
 * `RoleCatalogResponse`. `name_i18n` is keyed by locale; future custom roles
 * appear here automatically (the picker is no longer hardcoded).
 */
export interface RoleCatalogDto {
  code: string;
  name_i18n: LocalizedString;
  scope: RoleScope;
  is_system: boolean;
  is_custom: boolean;
  assignable_by_caller: boolean;
  /** Non-null only when `assignable_by_caller` is false. */
  reason_if_not: RoleAssignBlockReason | null;
}

/**
 * Domain shape consumed by the dialogs. Adds a `name` resolved from `name_i18n`
 * against the active locale (fallback chain ru-RU → uz-* → en-US → code).
 */
export interface AssignableRole {
  code: string;
  /** Localized display label. Never empty — falls back to the code. */
  name: string;
  scope: RoleScope;
  isSystem: boolean;
  isCustom: boolean;
  assignableByCaller: boolean;
  reasonIfNot: RoleAssignBlockReason | null;
}

export const roleKeys = {
  all: ['roles'] as const,
  /** Assignable-roles list. `locale` is in the key so labels refresh on switch. */
  assignable: (locale: string) => ['roles', 'assignable', locale] as const,
  /**
   * Full role catalog for the Roles admin list (slice E2). Separate from
   * {@link roleKeys.assignable} because the admin list is NOT trimmed to
   * `assignableOnly` — it shows every role the caller may administer.
   */
  catalog: (locale: string) => ['roles', 'catalog', locale] as const,
  /** Per-role permission matrix (slice E2). Keyed by role CODE only. */
  permissions: (roleCode: string) => ['roles', roleCode, 'permissions'] as const,
};

/**
 * Fetch the FULL role catalog for the Roles admin area (slice E2). Unlike
 * {@link fetchAssignableRoles}, this does NOT pass `assignableOnly`, so every
 * role the caller may administer is returned (system + custom). The returned
 * rows are localized exactly like the picker rows.
 */
export async function fetchRoleCatalog(locale: string): Promise<AssignableRole[]> {
  const res = await httpClient.get<RoleCatalogDto[]>('/roles');
  const rows = Array.isArray(res.data) ? res.data : [];
  return rows.map((r) => toAssignableRole(r, locale));
}

// ---------------------------------------------------------------------------
// Role permission matrix (slice E2)
// ---------------------------------------------------------------------------

/**
 * One row of the permission catalog as it applies to a role (snake_case wire
 * shape; mirrors the backend `RolePermissionItem`).
 *   - `granted`    — currently on this role.
 *   - `restricted` — cannot be changed by anyone via this endpoint (locked):
 *     the row renders disabled with a lock hint, and a restricted code is
 *     NEVER part of the PUT replace-set.
 */
export interface RolePermissionItemDto {
  code: string;
  resource: string;
  action: string;
  granted: boolean;
  restricted: boolean;
}

/** Wire shape of `GET /roles/{roleCode}/permissions` (snake_case). */
export interface RolePermissionsDto {
  role_code: string;
  scope: RoleScope;
  is_system: boolean;
  /** When false the whole matrix is read-only (system role / caller cannot edit). */
  editable_by_caller: boolean;
  items: RolePermissionItemDto[];
}

/** Domain (camelCase) projection of one permission row. */
export interface RolePermissionItem {
  code: string;
  resource: string;
  action: string;
  granted: boolean;
  restricted: boolean;
}

/** Domain projection of the matrix payload. */
export interface RolePermissions {
  roleCode: string;
  scope: RoleScope;
  isSystem: boolean;
  editableByCaller: boolean;
  items: RolePermissionItem[];
}

/** Wire → domain adapter for the permission matrix. */
export function toRolePermissions(dto: RolePermissionsDto): RolePermissions {
  return {
    roleCode: dto.role_code,
    scope: dto.scope,
    isSystem: Boolean(dto.is_system),
    editableByCaller: Boolean(dto.editable_by_caller),
    items: (Array.isArray(dto.items) ? dto.items : []).map((i) => ({
      code: i.code,
      resource: i.resource,
      action: i.action,
      granted: Boolean(i.granted),
      restricted: Boolean(i.restricted),
    })),
  };
}

/**
 * Fetch the permission matrix for one role.
 *
 * `items` is the FULL permission catalog with per-row `granted`/`restricted`
 * flags — the UI renders the union, grouped by `resource` (module). No
 * `tenant_id` is sent (auth-context comes from the JWT), consistent with the
 * rest of the users-access module.
 */
export async function fetchRolePermissions(roleCode: string): Promise<RolePermissions> {
  const res = await httpClient.get<RolePermissionsDto>(
    `/roles/${encodeURIComponent(roleCode)}/permissions`,
  );
  return toRolePermissions(res.data);
}

/**
 * Replace the granted permission set of a role.
 *
 * Sends the FULL desired set of granted, NON-restricted codes (replace-set
 * semantics, like the access-scope PUTs). The caller must exclude restricted
 * codes — the backend re-enforces and returns 422 `PERMISSION_RESTRICTED` if a
 * restricted code is present, or 422 `PERMISSION_NOT_HELD_BY_CALLER` if the
 * caller tries to grant a permission they do not themselves hold, or 403 if the
 * role is a system role the caller may not edit.
 */
export async function setRolePermissions(
  roleCode: string,
  permissionCodes: string[],
): Promise<RolePermissions> {
  const res = await httpClient.put<RolePermissionsDto>(
    `/roles/${encodeURIComponent(roleCode)}/permissions`,
    { permission_codes: permissionCodes },
  );
  return toRolePermissions(res.data);
}

/**
 * Wire → domain adapter. Resolves the localized name and normalizes the
 * snake_case booleans. Tolerant of a missing `name_i18n` (falls back to code)
 * so a malformed row never renders a blank, unselectable option.
 */
export function toAssignableRole(dto: RoleCatalogDto, locale: string): AssignableRole {
  const localized = pickLocalized(dto.name_i18n, locale);
  return {
    code: dto.code,
    name: localized.length > 0 ? localized : dto.code,
    scope: dto.scope,
    isSystem: Boolean(dto.is_system),
    isCustom: Boolean(dto.is_custom),
    assignableByCaller: Boolean(dto.assignable_by_caller),
    reasonIfNot: dto.reason_if_not ?? null,
  };
}

/**
 * Fetch the catalog of roles the caller may see in a picker. `assignableOnly`
 * trims platform-internal/non-grantable rows server-side; the rows that remain
 * still carry `assignable_by_caller` so the UI can show disabled (greyed) rows
 * with a reason for roles the caller can SEE but not GRANT.
 */
export async function fetchAssignableRoles(
  locale: string,
  assignableOnly = true,
): Promise<AssignableRole[]> {
  const res = await httpClient.get<RoleCatalogDto[]>('/roles', {
    params: assignableOnly ? { assignableOnly: true } : undefined,
  });
  const rows = Array.isArray(res.data) ? res.data : [];
  return rows.map((r) => toAssignableRole(r, locale));
}
