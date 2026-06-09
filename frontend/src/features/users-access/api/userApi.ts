/**
 * Users & Access API client.
 *
 * Covers the 10-endpoint contract from MVP 1 prompt §6:
 *   1. GET    /users                                            → list
 *   2. POST   /users                                            → invite
 *   3. GET    /users/{id}                                       → details
 *   4. PATCH  /users/{id}                                       → update
 *   5. POST   /users/{id}/memberships                           → add membership
 *   6. DELETE /users/{id}/memberships/{tenantId}                → revoke membership
 *   7. POST   /users/{id}/memberships/{tenantId}/roles          → add role
 *   8. DELETE /users/{id}/memberships/{tenantId}/roles/{roleId} → remove role
 *   9. PATCH  /users/{id}/memberships/{tenantId}/salary-permission → toggle salary
 *   10. GET   /users/{id}/audit                                 → user-scoped audit
 *
 * Tenant rule (D-202 / F-208): the body NEVER carries `tenant_id` as a
 * business field — the backend derives it from the JWT. The only times a
 * tenant id appears on the wire are:
 *   - URL path segment for explicit cross-tenant membership endpoints
 *     (HRLAB_SUPER_ADMIN authority).
 *   - InviteUserPayload.tenant_id, sent only by super-admins (the backend
 *     ignores it for non-super-admins).
 */
import { httpClient } from '@/shared/api/httpClient';
import { endpoints } from '@/shared/api/endpoints';
import type {
  AddMembershipPayload,
  AddRolePayload,
  InviteUserPayload,
  ResetLoginPayload,
  UpdateSalaryPermissionPayload,
  UpdateUserPayload,
  User,
  UserAuditList,
  UserDetails,
  UserList,
  UserListQuery,
  UserMembership,
  UserRoleAssignment,
} from '../types/userTypes';

/**
 * React-Query cache keys. `tenantScope` is included so the cache is busted
 * when the active tenant changes; it is NEVER sent on the wire by these
 * fetchers (regular roles), only as a query param for super-admins via
 * {@link UserListQuery.tenant_id} (which the backend ignores for non-supers).
 */
export const userKeys = {
  all: ['users'] as const,
  list: (tenantScope?: string, query?: UserListQuery) =>
    ['users', 'list', tenantScope ?? null, query ?? null] as const,
  detail: (id: string) => ['users', 'detail', id] as const,
  audit: (id: string, range?: { from?: string; to?: string }) =>
    ['users', 'audit', id, range ?? null] as const,
};

export async function fetchUsers(query: UserListQuery = {}): Promise<UserList> {
  const res = await httpClient.get<UserList>(endpoints.users.list, {
    params: cleanParams(query as Record<string, unknown>),
  });
  const env = (res.data ?? {}) as Partial<UserList> & Record<string, unknown>;
  const rawItems = Array.isArray(env.items) ? (env.items as unknown[]) : [];
  return {
    items: rawItems.map((r) => normalizeUser(r as Record<string, unknown>)),
    page: (env.page as number) ?? 0,
    size: (env.size as number) ?? rawItems.length,
    total_elements: (env.total_elements as number) ?? rawItems.length,
    total_pages: (env.total_pages as number) ?? 1,
  };
}

/**
 * Wire → domain adapter (Contract-A) for one user list row.
 *
 * The real backend's {@code UserListResponse} is a FLAT user+membership tuple
 * scoped to the queried tenant: it has `tenant_id`, `membership_id`,
 * `role_codes`, `default_locale` — but NO `tenant_count` / `role_count`
 * aggregates and no `locale` field. The {@link UsersTable} renders
 * `tenant_count` / `role_count` pills, so without this mapping those pills
 * are empty.
 *
 * Derivations (each preserves an explicit value when present — e.g. the MSW
 * mock supplies `tenant_count: 0` for a revoked user — and only falls back
 * when the field is absent, since `??` ignores only null/undefined):
 *   - `role_count`   ← explicit, else `role_codes.length`
 *   - `tenant_count` ← explicit, else `1` when the row carries a tenant
 *     membership (the list is tenant-scoped, so a listed user belongs to the
 *     queried tenant; the backend does not expose cross-tenant counts).
 *   - `locale`       ← `locale` (MSW), else `default_locale` (real backend)
 *
 * Tolerant of BOTH the snake_case real backend and the MSW camelCase-free
 * fixtures so unit tests keep passing.
 */
function normalizeUser(raw: Record<string, unknown>): User {
  const pick = <T = unknown>(snake: string, camel: string): T | undefined =>
    (raw[snake] ?? raw[camel]) as T | undefined;
  const roleCodes = (pick<string[]>('role_codes', 'roleCodes') ?? []) as User['role_codes'];
  const hasTenant = (raw.tenant_id ?? raw.tenantId) != null;
  return {
    id: String(raw.id ?? ''),
    email: String(raw.email ?? ''),
    full_name: String(pick<string>('full_name', 'fullName') ?? ''),
    locale: (pick<User['locale']>('locale', 'default_locale') ??
      (raw.defaultLocale as User['locale'])) as User['locale'],
    status: pick<User['status']>('status', 'status') as User['status'],
    tenant_count: (pick<number>('tenant_count', 'tenantCount') ?? (hasTenant ? 1 : 0)) as number,
    role_count: (pick<number>('role_count', 'roleCount') ?? roleCodes.length) as number,
    role_codes: roleCodes,
    last_login_at: (pick<string>('last_login_at', 'lastLoginAt') ?? null) as string | null,
    created_at: String(pick<string>('created_at', 'createdAt') ?? ''),
    updated_at: String(pick<string>('updated_at', 'updatedAt') ?? ''),
  };
}

export async function fetchUser(id: string): Promise<UserDetails> {
  // Raw wire shape — normalised below; type as `unknown` before the adapter.
  const res = await httpClient.get<unknown>(endpoints.users.detail(id));
  return normalizeUserDetails((res.data ?? {}) as Record<string, unknown>);
}

/**
 * Wire → domain adapter (Contract-A) for {@code GET /users/{id}}.
 *
 * Reuses {@link normalizeUser} for the flat user header (id / email /
 * full_name / locale / status / last_login_at) and additionally normalizes the
 * nested `memberships[]`, which drift from the FE domain shape in three ways:
 *   1. The backend `RoleSummary` exposes `user_role_id` + `code`; the
 *      {@link MembershipCard} reads `id` + `role_code` (the i18n key
 *      `users.role.${role_code}` otherwise resolves to `users.role.undefined`,
 *      and the per-role DELETE button needs the assignment id).
 *   2. The membership "Invited" date is the row's `created_at` audit column
 *      (now surfaced by the BE as `invited_at`); without it the card showed
 *      "Invalid Date". Falls back to `created_at` defensively.
 *   3. `salary_data_permission_granted_at/by` are optional and may be absent.
 *
 * Tolerant of BOTH the snake_case backend and the MSW fixtures (which already
 * use the FE domain shape `{ id, role_code, invited_at, ... }`) so the
 * users-access unit tests keep passing.
 */
function normalizeUserDetails(raw: Record<string, unknown>): UserDetails {
  const base = normalizeUser(raw);
  const rawMemberships = Array.isArray(raw.memberships) ? (raw.memberships as unknown[]) : [];
  return {
    ...base,
    memberships: rawMemberships.map((m) => normalizeMembership(m as Record<string, unknown>)),
  };
}

function normalizeMembership(raw: Record<string, unknown>): UserMembership {
  const pick = <T = unknown>(snake: string, camel: string): T | undefined =>
    (raw[snake] ?? raw[camel]) as T | undefined;
  const rawRoles = Array.isArray(raw.roles) ? (raw.roles as unknown[]) : [];
  const invitedAt = pick<string>('invited_at', 'invitedAt');
  const createdAt = pick<string>('created_at', 'createdAt');
  return {
    tenant_id: String(pick<string>('tenant_id', 'tenantId') ?? ''),
    tenant_brand_name: String(pick<string>('tenant_brand_name', 'tenantBrandName') ?? ''),
    status: pick<UserMembership['status']>('status', 'status') as UserMembership['status'],
    salary_data_permission: Boolean(
      pick<boolean>('salary_data_permission', 'salaryDataPermission') ?? false,
    ),
    salary_data_permission_granted_at:
      (pick<string>('salary_data_permission_granted_at', 'salaryDataPermissionGrantedAt') ??
        null) as string | null,
    salary_data_permission_granted_by:
      (pick<string>('salary_data_permission_granted_by', 'salaryDataPermissionGrantedBy') ??
        null) as string | null,
    roles: rawRoles.map((r) => normalizeRole(r as Record<string, unknown>)),
    invited_at: String(invitedAt ?? createdAt ?? ''),
    joined_at: (pick<string>('joined_at', 'joinedAt') ?? null) as string | null,
    revoked_at: (pick<string>('revoked_at', 'revokedAt') ?? null) as string | null,
  };
}

function normalizeRole(raw: Record<string, unknown>): UserRoleAssignment {
  const pick = <T = unknown>(snake: string, camel: string): T | undefined =>
    (raw[snake] ?? raw[camel]) as T | undefined;
  // Assignment id: MSW uses `id`, the backend RoleSummary uses `user_role_id`.
  const id = (raw.id ?? pick<string>('user_role_id', 'userRoleId')) as string | undefined;
  // Role code: MSW uses `role_code`, the backend RoleSummary uses `code`.
  const roleCode = (pick<string>('role_code', 'roleCode') ?? pick<string>('code', 'code')) as
    | UserRoleAssignment['role_code']
    | undefined;
  return {
    id: String(id ?? ''),
    role_code: roleCode as UserRoleAssignment['role_code'],
    granted_at: String(pick<string>('granted_at', 'grantedAt') ?? ''),
    granted_by: (pick<string>('granted_by', 'grantedBy') ?? null) as string | null,
  };
}

export async function inviteUser(payload: InviteUserPayload): Promise<User> {
  const res = await httpClient.post<User>(endpoints.users.list, payload);
  return res.data;
}

export async function updateUser(id: string, payload: UpdateUserPayload): Promise<User> {
  const res = await httpClient.patch<User>(endpoints.users.detail(id), payload);
  return res.data;
}

/**
 * POST /users/:id/reset-login — re-provision the user's IdP (login) account and
 * set an admin-chosen password. Returns the standard UserDetails (same shape as
 * the detail endpoint); normalised through the same adapter so memberships /
 * aggregates stay consistent. The password is sent in the body only and is
 * never logged here.
 */
export async function resetLogin(
  userId: string,
  payload: ResetLoginPayload,
): Promise<UserDetails> {
  const res = await httpClient.post<unknown>(endpoints.users.resetLogin(userId), payload);
  return normalizeUserDetails((res.data ?? {}) as Record<string, unknown>);
}

export async function addMembership(
  userId: string,
  payload: AddMembershipPayload,
): Promise<UserDetails> {
  const res = await httpClient.post<UserDetails>(
    endpoints.users.memberships(userId),
    payload,
  );
  return res.data;
}

export async function revokeMembership(
  userId: string,
  tenantId: string,
): Promise<UserDetails> {
  const res = await httpClient.delete<UserDetails>(
    endpoints.users.membership(userId, tenantId),
  );
  return res.data;
}

export async function addRole(
  userId: string,
  tenantId: string,
  payload: AddRolePayload,
): Promise<UserDetails> {
  const res = await httpClient.post<UserDetails>(
    endpoints.users.membershipRoles(userId, tenantId),
    payload,
  );
  return res.data;
}

export async function removeRole(
  userId: string,
  tenantId: string,
  roleId: string,
): Promise<UserDetails> {
  const res = await httpClient.delete<UserDetails>(
    endpoints.users.membershipRole(userId, tenantId, roleId),
  );
  return res.data;
}

export async function updateSalaryPermission(
  userId: string,
  tenantId: string,
  payload: UpdateSalaryPermissionPayload,
): Promise<UserDetails> {
  const res = await httpClient.patch<UserDetails>(
    endpoints.users.membershipSalaryPermission(userId, tenantId),
    payload,
  );
  return res.data;
}

export async function fetchUserAudit(
  userId: string,
  range: { from?: string; to?: string } = {},
): Promise<UserAuditList> {
  const res = await httpClient.get<UserAuditList>(endpoints.users.audit(userId), {
    params: cleanParams(range as Record<string, unknown>),
  });
  return res.data;
}

function cleanParams(input: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(input)) {
    if (v === undefined || v === null || v === '') continue;
    out[k] = v;
  }
  return out;
}
