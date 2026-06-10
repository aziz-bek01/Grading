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
  /**
   * Stable role id. Custom-role mutations (slice E3 — PUT/DELETE /roles/{roleId})
   * are keyed by id, whereas the E2 permission matrix is keyed by `code`. Both
   * uniquely identify the row; the UI carries the id so the E3 mutations can
   * address a row without re-deriving it. System rows may omit it.
   */
  id?: string | null;
  code: string;
  name_i18n: LocalizedString;
  scope: RoleScope;
  is_system: boolean;
  is_custom: boolean;
  assignable_by_caller: boolean;
  /** Non-null only when `assignable_by_caller` is false. */
  reason_if_not: RoleAssignBlockReason | null;
  /**
   * How many users currently have this role assigned, when the backend supplies
   * it. Used to warn before delete (a role in use cannot be deleted — 409
   * ROLE_IN_USE). Absent → unknown (the confirm dialog omits the count).
   */
  assignment_count?: number | null;
}

/**
 * Domain shape consumed by the dialogs. Adds a `name` resolved from `name_i18n`
 * against the active locale (fallback chain ru-RU → uz-* → en-US → code).
 */
export interface AssignableRole {
  /** Stable role id (used by E3 custom-role PUT/DELETE). Null for system rows. */
  id: string | null;
  code: string;
  /** Localized display label. Never empty — falls back to the code. */
  name: string;
  /** Raw localized map — the custom-role edit form seeds its per-locale inputs. */
  nameI18n: LocalizedString;
  scope: RoleScope;
  isSystem: boolean;
  isCustom: boolean;
  assignableByCaller: boolean;
  reasonIfNot: RoleAssignBlockReason | null;
  /** Assignment count when the backend supplies it (drives delete warning). */
  assignmentCount: number | null;
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
 * Stable system role used to obtain the FULL permission catalog (with
 * `restricted` flags) when building the CREATE form for a brand-new custom role
 * (slice E3). VIEWER is always present and tenant-scoped, so its matrix carries
 * the complete grid; the create form simply renders it with everything
 * unchecked. This REUSES the E2 permissions endpoint — no new backend surface.
 */
const PERMISSION_CATALOG_TEMPLATE_ROLE = 'VIEWER';

/**
 * Fetch the full permission catalog as an EMPTY template (every `granted=false`)
 * for the custom-role create form. Restricted rows keep their `restricted` flag
 * so the matrix locks them exactly as in the per-role editor.
 */
export async function fetchPermissionCatalogTemplate(): Promise<RolePermissionItem[]> {
  const matrix = await fetchRolePermissions(PERMISSION_CATALOG_TEMPLATE_ROLE);
  return matrix.items.map((i) => ({ ...i, granted: false }));
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
    id: dto.id ?? null,
    code: dto.code,
    name: localized.length > 0 ? localized : dto.code,
    nameI18n: dto.name_i18n ?? {},
    scope: dto.scope,
    isSystem: Boolean(dto.is_system),
    isCustom: Boolean(dto.is_custom),
    assignableByCaller: Boolean(dto.assignable_by_caller),
    reasonIfNot: dto.reason_if_not ?? null,
    assignmentCount: typeof dto.assignment_count === 'number' ? dto.assignment_count : null,
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

// ---------------------------------------------------------------------------
// Custom-role lifecycle (slice E3) — create / edit / delete tenant roles
// ---------------------------------------------------------------------------

/**
 * Body of `POST /roles` (snake_case wire shape). The backend creates a TENANT
 * custom role (`is_custom=true`, `scope=TENANT`). `tenant_id` is NEVER sent —
 * the backend derives tenant scope from the JWT, consistent with the rest of
 * the module. `permission_codes` is the initial granted set (restricted codes
 * must be excluded by the caller — the matrix already does this; the backend
 * re-enforces with 422 PERMISSION_RESTRICTED / PERMISSION_NOT_HELD_BY_CALLER).
 */
export interface CreateRolePayload {
  code: string;
  name_i18n: LocalizedString;
  permission_codes: string[];
}

/**
 * Body of `PUT /roles/{roleId}` (snake_case). Edits a CUSTOM role: the
 * localized name and/or the full granted permission set (replace-set). System
 * roles cannot be edited here (the backend returns 403); cross-tenant ids 404.
 */
export interface UpdateRolePayload {
  name_i18n?: LocalizedString;
  permission_codes?: string[];
}

/**
 * Create a tenant custom role. Returns the created catalog row (localized). The
 * backend errors are surfaced verbatim via {@link ApiError} for the caller to
 * map (see `mapRolePermissionError`): 409 ROLE_CODE_RESERVED / ROLE_CODE_EXISTS,
 * 422 PERMISSION_RESTRICTED / PERMISSION_NOT_HELD_BY_CALLER.
 */
export async function createRole(
  payload: CreateRolePayload,
  locale: string,
): Promise<AssignableRole> {
  const res = await httpClient.post<RoleCatalogDto>('/roles', payload);
  return toAssignableRole(res.data, locale);
}

/**
 * Edit a custom role (name and/or permissions). Addressed by `roleId` (not
 * code), matching the backend contract. Returns the updated catalog row.
 */
export async function updateRole(
  roleId: string,
  payload: UpdateRolePayload,
  locale: string,
): Promise<AssignableRole> {
  const res = await httpClient.put<RoleCatalogDto>(
    `/roles/${encodeURIComponent(roleId)}`,
    payload,
  );
  return toAssignableRole(res.data, locale);
}

/**
 * Delete a custom role. 204 on success. Errors: 409 ROLE_IN_USE (assigned to
 * users — revoke first), 403 (system role), 404 (cross-tenant / unknown id).
 */
export async function deleteRole(roleId: string): Promise<void> {
  await httpClient.delete(`/roles/${encodeURIComponent(roleId)}`);
}
