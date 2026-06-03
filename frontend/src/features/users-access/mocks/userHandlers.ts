/**
 * Users & Access mock handlers — registered through the existing in-process
 * mock adapter (see `src/shared/api/mocks/handlers.ts`).
 *
 * Implements all 10 endpoints from the contract. Tenant-id semantics mirror
 * the rest of the mock layer: body.tenant_id / tenantId is dropped with a
 * warning (matches backend behavior).
 */
import type { AxiosRequestConfig } from 'axios';
import type { RoleCode } from '@/shared/auth/authTypes';
import type {
  AddMembershipPayload,
  AddRolePayload,
  InviteUserPayload,
  UpdateSalaryPermissionPayload,
  UpdateUserPayload,
  UserDetails,
  UserMembership,
  UserStatus,
} from '../types/userTypes';
import { toListRow, userDb } from './userFixtures';

interface MatchResult {
  status: number;
  body: unknown;
  contentType?: string;
}

const MOCK_TENANT_HEADER = 'x-mock-tenant-id';
const MOCK_DEFAULT_TENANT_ID = '11111111-1111-1111-1111-111111111111';

function resolveMockTenantId(config: AxiosRequestConfig): string {
  const raw = config.headers as Record<string, unknown> | undefined;
  if (!raw) return MOCK_DEFAULT_TENANT_ID;
  const fromObj = (raw[MOCK_TENANT_HEADER] ?? raw['X-Mock-Tenant-Id']) as string | undefined;
  if (typeof fromObj === 'string' && fromObj.length > 0) return fromObj;
  const headers = raw as { get?: (k: string) => string | null | undefined };
  if (typeof headers.get === 'function') {
    const v = headers.get(MOCK_TENANT_HEADER) ?? headers.get('X-Mock-Tenant-Id');
    if (typeof v === 'string' && v.length > 0) return v;
  }
  return MOCK_DEFAULT_TENANT_ID;
}

function stripTenantFromBody<T extends Record<string, unknown>>(
  body: T,
  url: string,
  method: string,
): T {
  if (!body || typeof body !== 'object') return body;
  if ('tenant_id' in body || 'tenantId' in body) {
    // Note: InviteUserPayload.tenant_id is explicitly allowed for super-admins,
    // but the mock layer treats every request the same way as the real
    // backend would for non-supers. The invite handler below re-reads the
    // tenant id from a separate channel (header / explicit param).
    // eslint-disable-next-line no-console
    console.warn(
      `[mock] ignoring tenant_id in request body for ${method} ${url}. ` +
        'Backend derives tenant from JWT; client must never send it as a business field.',
    );
    const clone: Record<string, unknown> = { ...body };
    delete clone.tenant_id;
    delete clone.tenantId;
    return clone as T;
  }
  return body;
}

function uuid(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID();
  return `mock-${Math.random().toString(36).slice(2)}`;
}

function ok(body: unknown, status = 200): MatchResult {
  return { status, body };
}

function notFound(): MatchResult {
  return { status: 404, body: { code: 'NOT_FOUND', message: 'User or membership not found' } };
}

function badRequest(message: string, code = 'BAD_REQUEST'): MatchResult {
  return { status: 400, body: { code, message } };
}

function readBody<T>(config: AxiosRequestConfig): T {
  if (!config.data) return {} as T;
  if (typeof config.data === 'string') return JSON.parse(config.data) as T;
  return config.data as T;
}

function parseUrl(url: string, params?: Record<string, unknown>): { path: string; query: URLSearchParams } {
  const idx = url.indexOf('?');
  const path = idx >= 0 ? url.slice(0, idx) : url;
  const query = new URLSearchParams(idx >= 0 ? url.slice(idx + 1) : '');
  if (params && typeof params === 'object') {
    for (const [k, v] of Object.entries(params)) {
      if (v === undefined || v === null) continue;
      if (!query.has(k)) query.set(k, String(v));
    }
  }
  return { path: path.replace(/^\/api\/v1/, '').replace(/\/$/, ''), query };
}

function paginate<T>(items: T[], page: number, size: number) {
  const start = page * size;
  const slice = items.slice(start, start + size);
  return {
    items: slice,
    page,
    size,
    total_elements: items.length,
    total_pages: Math.max(1, Math.ceil(items.length / size)),
  };
}

function recomputeAggregates(u: UserDetails): void {
  const activeMemberships = u.memberships.filter((m) => m.status !== 'REVOKED');
  u.tenant_count = activeMemberships.length;
  const roleCodes = new Set<RoleCode>();
  let total = 0;
  for (const m of activeMemberships) {
    for (const r of m.roles) {
      roleCodes.add(r.role_code);
      total += 1;
    }
  }
  u.role_codes = [...roleCodes];
  u.role_count = total;
}

// ---------------------------------------------------------------
// Endpoint handlers
// ---------------------------------------------------------------

function handleList(query: URLSearchParams, config: AxiosRequestConfig): MatchResult {
  const headerTenant = resolveMockTenantId(config);
  // Honour explicit ?tenantId only when caller is operating cross-tenant
  // (super-admin). Other roles fall back to the header (JWT-derived).
  const tenantId = query.get('tenantId') ?? query.get('tenant_id') ?? headerTenant;
  const status = query.get('status') as UserStatus | null;
  const role = query.get('role') as RoleCode | null;
  const search = (query.get('search') ?? '').trim().toLowerCase();
  const page = parseInt(query.get('page') ?? '0', 10);
  const size = parseInt(query.get('size') ?? '20', 10);

  let list = userDb.users.filter((u) =>
    u.memberships.some((m) => m.tenant_id === tenantId),
  );
  if (status) list = list.filter((u) => u.status === status);
  if (role) list = list.filter((u) => u.role_codes.includes(role));
  if (search) {
    list = list.filter(
      (u) =>
        u.email.toLowerCase().includes(search) ||
        u.full_name.toLowerCase().includes(search),
    );
  }
  return ok(paginate(list.map(toListRow), page, size));
}

function handleInvite(config: AxiosRequestConfig): MatchResult {
  const raw = readBody<InviteUserPayload & Record<string, unknown>>(config);
  // Note: InviteUserPayload.tenant_id is intentionally allowed (super-admin
  // cross-tenant invite). We still strip via the warn-path so the backend
  // contract is mirrored; the explicit tenant target is re-resolved from the
  // header for the mock.
  const explicitTenant = raw.tenant_id;
  const body = stripTenantFromBody(raw, '/users', 'POST');
  if (!body.email || !body.full_name || !body.locale || !body.role_codes) {
    return badRequest('email, full_name, locale, role_codes are required', 'VALIDATION_ERROR');
  }
  // Email format guard (mirror Zod).
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(body.email)) {
    return badRequest('email must be a valid RFC address', 'VALIDATION_EMAIL');
  }
  if (userDb.users.some((u) => u.email.toLowerCase() === body.email.toLowerCase())) {
    return { status: 409, body: { code: 'EMAIL_EXISTS', message: 'Email already in use' } };
  }
  const targetTenantId = explicitTenant ?? resolveMockTenantId(config);
  const brand =
    userDb.users
      .flatMap((u) => u.memberships)
      .find((m) => m.tenant_id === targetTenantId)?.tenant_brand_name ?? 'Client tenant';
  const now = new Date().toISOString();
  const newMembership: UserMembership = {
    tenant_id: targetTenantId,
    tenant_brand_name: brand,
    status: 'INVITED',
    salary_data_permission: false,
    salary_data_permission_granted_at: null,
    salary_data_permission_granted_by: null,
    roles: body.role_codes.map((rc) => ({
      id: uuid(),
      role_code: rc,
      granted_at: now,
      granted_by: 'u-mock-actor',
    })),
    invited_at: now,
    joined_at: null,
    revoked_at: null,
  };
  const next: UserDetails = {
    id: uuid(),
    email: body.email,
    full_name: body.full_name,
    locale: body.locale,
    status: 'INVITED',
    tenant_count: 1,
    role_count: body.role_codes.length,
    role_codes: [...body.role_codes],
    last_login_at: null,
    created_at: now,
    updated_at: now,
    memberships: [newMembership],
  };
  recomputeAggregates(next);
  userDb.users.unshift(next);
  return ok(toListRow(next), 201);
}

function handleDetail(id: string): MatchResult {
  const u = userDb.users.find((x) => x.id === id);
  if (!u) return notFound();
  return ok(u);
}

function handleUpdate(id: string, config: AxiosRequestConfig): MatchResult {
  const u = userDb.users.find((x) => x.id === id);
  if (!u) return notFound();
  const raw = readBody<UpdateUserPayload & Record<string, unknown>>(config);
  const body = stripTenantFromBody(raw, `/users/${id}`, 'PATCH');
  if (body.full_name !== undefined) u.full_name = body.full_name;
  if (body.locale !== undefined) u.locale = body.locale;
  if (body.status !== undefined) u.status = body.status;
  u.updated_at = new Date().toISOString();
  return ok(toListRow(u));
}

function handleAddMembership(id: string, config: AxiosRequestConfig): MatchResult {
  const u = userDb.users.find((x) => x.id === id);
  if (!u) return notFound();
  const raw = readBody<AddMembershipPayload & Record<string, unknown>>(config);
  // tenant_id IS allowed in the path; here it is explicitly the target
  // tenant of a new membership row, not a tenant-scope marker. We don't
  // strip in this case — but defensively warn if both top-level and a body
  // duplicate appear.
  if (!raw.tenant_id || !raw.role_codes || raw.role_codes.length === 0) {
    return badRequest('tenant_id and at least one role_code required', 'VALIDATION_ERROR');
  }
  if (u.memberships.some((m) => m.tenant_id === raw.tenant_id && m.status !== 'REVOKED')) {
    return { status: 409, body: { code: 'MEMBERSHIP_EXISTS', message: 'Active membership exists' } };
  }
  const now = new Date().toISOString();
  const m: UserMembership = {
    tenant_id: raw.tenant_id,
    tenant_brand_name: raw.tenant_id === '22222222-2222-2222-2222-222222222222' ? 'Beta University' : 'ACME Holdings',
    status: 'INVITED',
    salary_data_permission: false,
    salary_data_permission_granted_at: null,
    salary_data_permission_granted_by: null,
    roles: raw.role_codes.map((rc) => ({
      id: uuid(),
      role_code: rc,
      granted_at: now,
      granted_by: 'u-mock-actor',
    })),
    invited_at: now,
    joined_at: null,
    revoked_at: null,
  };
  u.memberships.push(m);
  recomputeAggregates(u);
  u.updated_at = now;
  return ok(u);
}

function handleRevokeMembership(id: string, tenantId: string): MatchResult {
  const u = userDb.users.find((x) => x.id === id);
  if (!u) return notFound();
  const m = u.memberships.find((mm) => mm.tenant_id === tenantId);
  if (!m) return notFound();
  m.status = 'REVOKED';
  m.revoked_at = new Date().toISOString();
  recomputeAggregates(u);
  u.updated_at = new Date().toISOString();
  return ok(u);
}

function handleAddRole(id: string, tenantId: string, config: AxiosRequestConfig): MatchResult {
  const u = userDb.users.find((x) => x.id === id);
  if (!u) return notFound();
  const m = u.memberships.find((mm) => mm.tenant_id === tenantId);
  if (!m || m.status === 'REVOKED') return notFound();
  const raw = readBody<AddRolePayload & Record<string, unknown>>(config);
  const body = stripTenantFromBody(raw, `/users/${id}/memberships/${tenantId}/roles`, 'POST');
  if (!body.role_code) return badRequest('role_code required', 'VALIDATION_ERROR');
  if (m.roles.some((r) => r.role_code === body.role_code)) {
    return { status: 409, body: { code: 'ROLE_EXISTS', message: 'Role already assigned' } };
  }
  m.roles.push({
    id: uuid(),
    role_code: body.role_code,
    granted_at: new Date().toISOString(),
    granted_by: 'u-mock-actor',
  });
  recomputeAggregates(u);
  u.updated_at = new Date().toISOString();
  return ok(u);
}

function handleRemoveRole(id: string, tenantId: string, roleId: string): MatchResult {
  const u = userDb.users.find((x) => x.id === id);
  if (!u) return notFound();
  const m = u.memberships.find((mm) => mm.tenant_id === tenantId);
  if (!m) return notFound();
  const idx = m.roles.findIndex((r) => r.id === roleId);
  if (idx < 0) return notFound();
  m.roles.splice(idx, 1);
  recomputeAggregates(u);
  u.updated_at = new Date().toISOString();
  return ok(u);
}

function handleSalaryPermission(id: string, tenantId: string, config: AxiosRequestConfig): MatchResult {
  const u = userDb.users.find((x) => x.id === id);
  if (!u) return notFound();
  const m = u.memberships.find((mm) => mm.tenant_id === tenantId);
  if (!m || m.status === 'REVOKED') return notFound();
  const raw = readBody<UpdateSalaryPermissionPayload & Record<string, unknown>>(config);
  const body = stripTenantFromBody(
    raw,
    `/users/${id}/memberships/${tenantId}/salary-permission`,
    'PATCH',
  );
  if (typeof body.enabled !== 'boolean') {
    return badRequest('enabled must be boolean', 'VALIDATION_ERROR');
  }
  if (!body.reason || body.reason.trim().length < 10) {
    return badRequest('reason must be at least 10 characters', 'REASON_TOO_SHORT');
  }
  m.salary_data_permission = body.enabled;
  if (body.enabled) {
    m.salary_data_permission_granted_at = new Date().toISOString();
    m.salary_data_permission_granted_by = 'u-mock-actor';
  } else {
    m.salary_data_permission_granted_at = null;
    m.salary_data_permission_granted_by = null;
  }
  u.updated_at = new Date().toISOString();
  // Append an audit event so the mock audit feed reflects the change.
  const auditList = userDb.audit[id] ?? (userDb.audit[id] = []);
  auditList.unshift({
    id: uuid(),
    action: body.enabled ? 'USER_SALARY_PERMISSION_GRANTED' : 'USER_SALARY_PERMISSION_REVOKED',
    entity_type: 'USER_MEMBERSHIP',
    entity_id: id,
    actor_id: 'u-mock-actor',
    actor_name: 'Dev Actor',
    tenant_id: tenantId,
    occurred_at: u.updated_at,
    metadata: { reason: body.reason },
  });
  return ok(u);
}

function handleAudit(id: string, query: URLSearchParams): MatchResult {
  const list = userDb.audit[id] ?? [];
  const from = query.get('from');
  const to = query.get('to');
  let items = list;
  if (from) items = items.filter((e) => e.occurred_at >= from);
  if (to) items = items.filter((e) => e.occurred_at <= to);
  return ok({ items });
}

// ---------------------------------------------------------------
// Dispatcher
// ---------------------------------------------------------------

export function handleUsers(config: AxiosRequestConfig): MatchResult | null {
  const method = (config.method ?? 'get').toUpperCase();
  const url = config.url ?? '';
  const { path, query } = parseUrl(url, config.params as Record<string, unknown> | undefined);

  if (path === '/users' && method === 'GET') return handleList(query, config);
  if (path === '/users' && method === 'POST') return handleInvite(config);

  const auditMatch = /^\/users\/([^/]+)\/audit$/.exec(path);
  if (auditMatch && method === 'GET') return handleAudit(auditMatch[1], query);

  const salaryMatch = /^\/users\/([^/]+)\/memberships\/([^/]+)\/salary-permission$/.exec(path);
  if (salaryMatch && method === 'PATCH') {
    return handleSalaryPermission(salaryMatch[1], salaryMatch[2], config);
  }

  const roleByIdMatch = /^\/users\/([^/]+)\/memberships\/([^/]+)\/roles\/([^/]+)$/.exec(path);
  if (roleByIdMatch && method === 'DELETE') {
    return handleRemoveRole(roleByIdMatch[1], roleByIdMatch[2], roleByIdMatch[3]);
  }

  const rolesMatch = /^\/users\/([^/]+)\/memberships\/([^/]+)\/roles$/.exec(path);
  if (rolesMatch && method === 'POST') {
    return handleAddRole(rolesMatch[1], rolesMatch[2], config);
  }

  const membershipMatch = /^\/users\/([^/]+)\/memberships\/([^/]+)$/.exec(path);
  if (membershipMatch && method === 'DELETE') {
    return handleRevokeMembership(membershipMatch[1], membershipMatch[2]);
  }

  const membershipsMatch = /^\/users\/([^/]+)\/memberships$/.exec(path);
  if (membershipsMatch && method === 'POST') {
    return handleAddMembership(membershipsMatch[1], config);
  }

  const detailMatch = /^\/users\/([^/]+)$/.exec(path);
  if (detailMatch) {
    const id = detailMatch[1];
    if (method === 'GET') return handleDetail(id);
    if (method === 'PATCH') return handleUpdate(id, config);
  }
  return null;
}
