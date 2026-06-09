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
};

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
