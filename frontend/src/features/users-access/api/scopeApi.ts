/**
 * Access-scope API client (slice E4-S1).
 *
 * Three endpoints:
 *   GET /users/{id}/scopes?tenantId={tenantId}  → current ACTIVE scope sets
 *   PUT /users/{id}/department-scopes           → REPLACE department scope
 *   PUT /users/{id}/project-assignments         → REPLACE project assignment
 *
 * Tenant rule (scope-specific): `tenant_id` is sent ON PURPOSE here — these are
 * cross-tenant admin surfaces, so the caller explicitly names the membership
 * being edited (query param on GET, body field on the PUTs). The backend
 * validates the departments / projects belong to that tenant and that the target
 * user is a member of it.
 *
 * Gate: caller needs USER_MEMBERSHIP_MANAGE or USER_ACCESS_MANAGE.
 */
import { httpClient } from '@/shared/api/httpClient';
import { endpoints } from '@/shared/api/endpoints';
import type {
  SetDepartmentScopesPayload,
  SetProjectAssignmentsPayload,
  UserScopes,
} from '../types/scopeTypes';

/**
 * React-Query cache keys. The scope cache is keyed by BOTH the user and the
 * tenant — a user can be a member of several tenants, each with its own scope.
 */
export const scopeKeys = {
  all: ['user-scopes'] as const,
  detail: (userId: string, tenantId: string) =>
    ['user-scopes', userId, tenantId] as const,
};

/** Wire → domain adapter. Tolerant of snake_case backend + MSW fixtures. */
function normalizeScopes(raw: Record<string, unknown>): UserScopes {
  const pick = <T = unknown>(snake: string, camel: string): T | undefined =>
    (raw[snake] ?? raw[camel]) as T | undefined;
  const asIds = (v: unknown): string[] =>
    Array.isArray(v) ? v.map((x) => String(x)) : [];
  return {
    user_id: String(pick<string>('user_id', 'userId') ?? ''),
    tenant_id: String(pick<string>('tenant_id', 'tenantId') ?? ''),
    department_ids: asIds(pick('department_ids', 'departmentIds')),
    project_ids: asIds(pick('project_ids', 'projectIds')),
  };
}

export async function fetchUserScopes(
  userId: string,
  tenantId: string,
): Promise<UserScopes> {
  const res = await httpClient.get<unknown>(endpoints.users.scopes(userId), {
    // tenantId is an explicit cross-tenant selector here (NOT a JWT-derived
    // business param) — see module header.
    params: { tenantId },
  });
  return normalizeScopes((res.data ?? {}) as Record<string, unknown>);
}

export async function setDepartmentScopes(
  userId: string,
  payload: SetDepartmentScopesPayload,
): Promise<UserScopes> {
  const res = await httpClient.put<unknown>(
    endpoints.users.departmentScopes(userId),
    payload,
  );
  return normalizeScopes((res.data ?? {}) as Record<string, unknown>);
}

export async function setProjectAssignments(
  userId: string,
  payload: SetProjectAssignmentsPayload,
): Promise<UserScopes> {
  const res = await httpClient.put<unknown>(
    endpoints.users.projectAssignments(userId),
    payload,
  );
  return normalizeScopes((res.data ?? {}) as Record<string, unknown>);
}
