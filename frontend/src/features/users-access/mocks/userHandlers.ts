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
import { useAuthStore } from '@/features/auth/authStore';
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
import { ROLE_CODES } from '../schemas/userSchemas';
import { scopeKey, toListRow, userDb } from './userFixtures';

/**
 * Canonical role catalog (mirrors the backend `roles` table). The mock
 * validates incoming role codes against this set exactly as the backend does,
 * so a divergent FE role constant surfaces as a 400 in tests instead of being
 * silently accepted (which previously masked the prod invite failure).
 */
const VALID_ROLE_CODES = new Set<string>(ROLE_CODES);

function unknownRoleCodes(codes: readonly string[]): string[] {
  return codes.filter((c) => !VALID_ROLE_CODES.has(c));
}

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
  // Password complexity guard — mirrors the backend (Zitadel default policy:
  // min 8 chars, at least one upper, one lower, one number, one symbol). The
  // password is OPTIONAL on the wire (IdP may be disabled), but when present it
  // must be strong, so local stays honest against the real contract.
  if (body.password !== undefined) {
    const strong = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,}$/.test(body.password);
    if (!strong) {
      return badRequest(
        'password does not meet complexity requirements',
        'USER_INVITE_WEAK_PASSWORD',
      );
    }
  }
  // Role-code guard (mirror backend role catalog).
  const unknownInvite = unknownRoleCodes(body.role_codes);
  if (unknownInvite.length > 0) {
    return badRequest(`unknown role_code(s): ${unknownInvite.join(', ')}`, 'UNKNOWN_ROLE_CODE');
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
      role_code: rc as RoleCode,
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
    role_codes: [...body.role_codes] as RoleCode[],
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

/**
 * Statuses the PATCH endpoint accepts. Mirrors the real backend: only ACTIVE
 * and DISABLED may be set here; INVITED / LOCKED are system-managed and a
 * request to set them is rejected 400 `USER_PATCH_BAD_STATUS` so local stays
 * honest against the contract.
 */
const PATCHABLE_USER_STATUSES = new Set<UserStatus>(['ACTIVE', 'DISABLED']);

function handleUpdate(id: string, config: AxiosRequestConfig): MatchResult {
  const u = userDb.users.find((x) => x.id === id);
  if (!u) return notFound();
  const raw = readBody<UpdateUserPayload & Record<string, unknown>>(config);
  const body = stripTenantFromBody(raw, `/users/${id}`, 'PATCH');
  if (body.status !== undefined && !PATCHABLE_USER_STATUSES.has(body.status)) {
    return badRequest(
      'status must be ACTIVE or DISABLED',
      'USER_PATCH_BAD_STATUS',
    );
  }
  // Email change — validate syntax + uniqueness (mirror backend USER_EMAIL_TAKEN).
  if (body.email !== undefined) {
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(body.email)) {
      return badRequest('email must be a valid RFC address', 'VALIDATION_EMAIL');
    }
    const taken = userDb.users.some(
      (x) => x.id !== id && x.email.toLowerCase() === body.email!.toLowerCase(),
    );
    if (taken) {
      return badRequest('email already in use', 'USER_EMAIL_TAKEN');
    }
    // Sentinel: the IdP rejects this new email (mirror backend
    // USER_IDP_EMAIL_REJECTED). Any address on the `idp-reject.test` domain
    // triggers it so dev/tests can exercise the email-rejected mapping.
    if (/@idp-reject\.test$/i.test(body.email)) {
      return badRequest('identity provider rejected the email', 'USER_IDP_EMAIL_REJECTED');
    }
  }
  // Password change — enforce the same complexity as the invite flow, then
  // surface the IdP-specific failures the backend can now return. Each is keyed
  // off a sentinel that is itself complexity-valid (so it reaches the IdP step
  // instead of failing the complexity check first), keeping the mock honest.
  if (body.password !== undefined) {
    const strong = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,}$/.test(body.password);
    if (!strong) {
      return badRequest(
        'password does not meet complexity requirements',
        'USER_INVITE_WEAK_PASSWORD',
      );
    }
    // Sentinel: user's IdP (login) account isn't activated yet, so a password
    // cannot be set here (mirror backend USER_IDP_NOT_INITIALIZED).
    if (body.password.includes('IdpNotInit')) {
      return badRequest('idp account not initialized', 'USER_IDP_NOT_INITIALIZED');
    }
    // Sentinel: IdP rejected the password (e.g. provider policy) — mirror
    // backend USER_IDP_PASSWORD_REJECTED.
    if (body.password.includes('IdpReject')) {
      return badRequest('identity provider rejected the password', 'USER_IDP_PASSWORD_REJECTED');
    }
  }
  if (body.full_name !== undefined) u.full_name = body.full_name;
  if (body.email !== undefined) u.email = body.email;
  if (body.locale !== undefined) u.locale = body.locale;
  if (body.status !== undefined) u.status = body.status;
  // Note: the mock does not persist passwords — it only honours the contract
  // (validation + acceptance). No password is ever stored or logged.
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
  const unknownMembership = unknownRoleCodes(raw.role_codes);
  if (unknownMembership.length > 0) {
    return badRequest(`unknown role_code(s): ${unknownMembership.join(', ')}`, 'UNKNOWN_ROLE_CODE');
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
      role_code: rc as RoleCode,
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
  if (!VALID_ROLE_CODES.has(body.role_code)) {
    return badRequest(`unknown role_code: ${body.role_code}`, 'UNKNOWN_ROLE_CODE');
  }
  if (m.roles.some((r) => r.role_code === body.role_code)) {
    return { status: 409, body: { code: 'ROLE_EXISTS', message: 'Role already assigned' } };
  }
  m.roles.push({
    id: uuid(),
    role_code: body.role_code as RoleCode,
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

/**
 * POST /users/{id}/reset-login — re-provisions the user's IdP (login) account
 * by their grading email and sets an admin-chosen password. Mirrors the backend
 * recovery path: on success the user becomes ACTIVE and the standard
 * UserDetails is returned (same shape as PATCH).
 *
 * Error sentinels (so dev/tests stay honest against the real contract):
 *   - weak password                → 400 USER_INVITE_WEAK_PASSWORD
 *   - password contains "IdpNotInit" → 400 USER_IDP_NOT_INITIALIZED
 *   - password contains "IdpReject"  → 400 USER_IDP_PASSWORD_REJECTED
 *   - password contains "NoIdp"      → 400 USER_NO_IDP_ACCOUNT
 *   - password contains "IdpDown"    → 502 IDP_PROVISIONING_FAILED (transient)
 */
function handleResetLogin(id: string, config: AxiosRequestConfig): MatchResult {
  const u = userDb.users.find((x) => x.id === id);
  if (!u) return notFound();
  const raw = readBody<{ password?: string } & Record<string, unknown>>(config);
  const body = stripTenantFromBody(raw, `/users/${id}/reset-login`, 'POST');
  const password = typeof body.password === 'string' ? body.password : '';
  const strong = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,}$/.test(password);
  if (!strong) {
    return badRequest(
      'password does not meet complexity requirements',
      'USER_INVITE_WEAK_PASSWORD',
    );
  }
  // Sentinels reach the IdP step only because they are complexity-valid above.
  if (password.includes('IdpDown')) {
    return {
      status: 502,
      body: { code: 'IDP_PROVISIONING_FAILED', message: 'identity provider unavailable' },
    };
  }
  if (password.includes('IdpNotInit')) {
    return badRequest('idp account not initialized', 'USER_IDP_NOT_INITIALIZED');
  }
  if (password.includes('IdpReject')) {
    return badRequest('identity provider rejected the password', 'USER_IDP_PASSWORD_REJECTED');
  }
  if (password.includes('NoIdp')) {
    return badRequest('user has no idp account', 'USER_NO_IDP_ACCOUNT');
  }
  // Success: the login account is (re)provisioned and the user becomes ACTIVE.
  // The mock never stores or logs the password.
  u.status = 'ACTIVE';
  u.updated_at = new Date().toISOString();
  const auditList = userDb.audit[id] ?? (userDb.audit[id] = []);
  auditList.unshift({
    id: uuid(),
    action: 'USER_LOGIN_RESET',
    entity_type: 'USER',
    entity_id: id,
    actor_id: 'u-mock-actor',
    actor_name: 'Dev Actor',
    tenant_id: null,
    occurred_at: u.updated_at,
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
// Access-scope handlers (slice E4-S1)
// ---------------------------------------------------------------
//
// Unlike the rest of the users contract, `tenant_id` is an EXPLICIT field here
// (query param on GET, body field on the PUTs) — these are cross-tenant admin
// surfaces. The mock therefore does NOT strip it; instead it validates the
// caller named a tenant and that the user is a member of it (mirrors the
// backend's membership check).

/** Read-or-create the scope record for a (user, tenant) pair. */
function scopeRecord(userId: string, tenantId: string) {
  const key = scopeKey(userId, tenantId);
  return (userDb.scopes[key] ??= { department_ids: [], project_ids: [] });
}

function userIsMember(userId: string, tenantId: string): boolean {
  const u = userDb.users.find((x) => x.id === userId);
  if (!u) return false;
  return u.memberships.some((m) => m.tenant_id === tenantId && m.status !== 'REVOKED');
}

function handleGetScopes(userId: string, query: URLSearchParams): MatchResult {
  const u = userDb.users.find((x) => x.id === userId);
  if (!u) return notFound();
  const tenantId = query.get('tenantId') ?? query.get('tenant_id');
  if (!tenantId) {
    return badRequest('tenantId query parameter is required', 'VALIDATION_ERROR');
  }
  if (!userIsMember(userId, tenantId)) {
    // Mirror the backend "not found or no access" probe-resistant response.
    return notFound();
  }
  const rec = scopeRecord(userId, tenantId);
  return ok({
    user_id: userId,
    tenant_id: tenantId,
    department_ids: [...rec.department_ids],
    project_ids: [...rec.project_ids],
  });
}

function handleSetDepartmentScopes(userId: string, config: AxiosRequestConfig): MatchResult {
  const u = userDb.users.find((x) => x.id === userId);
  if (!u) return notFound();
  const body = readBody<{ tenant_id?: string; department_ids?: unknown }>(config);
  if (!body.tenant_id) {
    return badRequest('tenant_id is required', 'VALIDATION_ERROR');
  }
  if (!Array.isArray(body.department_ids)) {
    return badRequest('department_ids must be an array', 'VALIDATION_ERROR');
  }
  if (!userIsMember(userId, body.tenant_id)) {
    return notFound();
  }
  const rec = scopeRecord(userId, body.tenant_id);
  // REPLACE-set: overwrite the department array wholesale.
  rec.department_ids = body.department_ids.map((x) => String(x));
  u.updated_at = new Date().toISOString();
  return ok({
    user_id: userId,
    tenant_id: body.tenant_id,
    department_ids: [...rec.department_ids],
    project_ids: [...rec.project_ids],
  });
}

function handleSetProjectAssignments(userId: string, config: AxiosRequestConfig): MatchResult {
  const u = userDb.users.find((x) => x.id === userId);
  if (!u) return notFound();
  const body = readBody<{ tenant_id?: string; project_ids?: unknown }>(config);
  if (!body.tenant_id) {
    return badRequest('tenant_id is required', 'VALIDATION_ERROR');
  }
  if (!Array.isArray(body.project_ids)) {
    return badRequest('project_ids must be an array', 'VALIDATION_ERROR');
  }
  if (!userIsMember(userId, body.tenant_id)) {
    return notFound();
  }
  const rec = scopeRecord(userId, body.tenant_id);
  // REPLACE-set: overwrite the project array wholesale.
  rec.project_ids = body.project_ids.map((x) => String(x));
  u.updated_at = new Date().toISOString();
  return ok({
    user_id: userId,
    tenant_id: body.tenant_id,
    department_ids: [...rec.department_ids],
    project_ids: [...rec.project_ids],
  });
}

// ---------------------------------------------------------------
// Role catalog (GET /roles) — backs the DATA-DRIVEN role pickers (slice E1)
// ---------------------------------------------------------------

/**
 * Wire shape returned by `GET /roles` (snake_case). `assignable_by_caller` and
 * `reason_if_not` are computed below per the mock current-user role so dev/tests
 * are honest: a non-super caller sees PLATFORM/HRLAB rows disabled with a reason.
 */
interface RoleCatalogRow {
  code: RoleCode;
  scope: 'PLATFORM' | 'TENANT';
  is_system: boolean;
  name_i18n: Record<string, string>;
}

/** System role catalog (mirrors the seeded `roles` table). */
const ROLE_CATALOG: RoleCatalogRow[] = [
  {
    code: 'HRLAB_SUPER_ADMIN',
    scope: 'PLATFORM',
    is_system: true,
    name_i18n: {
      'ru-RU': 'HRLab Super Admin',
      'uz-Cyrl-UZ': 'HRLab бош администратор',
      'uz-Latn-UZ': 'HRLab bosh administrator',
      'en-US': 'HRLab Super Admin',
    },
  },
  {
    code: 'HRLAB_PROJECT_MANAGER',
    scope: 'PLATFORM',
    is_system: true,
    name_i18n: {
      'ru-RU': 'HRLab менеджер проекта',
      'uz-Cyrl-UZ': 'HRLab лойиҳа менежери',
      'uz-Latn-UZ': 'HRLab loyiha menejeri',
      'en-US': 'HRLab Project Manager',
    },
  },
  {
    code: 'HRLAB_CONSULTANT',
    scope: 'PLATFORM',
    is_system: true,
    name_i18n: {
      'ru-RU': 'HRLab консультант',
      'uz-Cyrl-UZ': 'HRLab консультант',
      'uz-Latn-UZ': 'HRLab konsultant',
      'en-US': 'HRLab Consultant',
    },
  },
  {
    code: 'HRLAB_ANALYST',
    scope: 'PLATFORM',
    is_system: true,
    name_i18n: {
      'ru-RU': 'HRLab аналитик',
      'uz-Cyrl-UZ': 'HRLab таҳлилчи',
      'uz-Latn-UZ': 'HRLab tahlilchi',
      'en-US': 'HRLab Analyst',
    },
  },
  {
    code: 'CLIENT_COMPANY_ADMIN',
    scope: 'TENANT',
    is_system: true,
    name_i18n: {
      'ru-RU': 'Администратор компании-клиента',
      'uz-Cyrl-UZ': 'Мижоз компания администратори',
      'uz-Latn-UZ': 'Mijoz kompaniya administratori',
      'en-US': 'Client Company Admin',
    },
  },
  {
    code: 'CLIENT_HR_DIRECTOR',
    scope: 'TENANT',
    is_system: true,
    name_i18n: {
      'ru-RU': 'HR-директор',
      'uz-Cyrl-UZ': 'HR директор',
      'uz-Latn-UZ': 'HR direktor',
      'en-US': 'HR Director',
    },
  },
  {
    code: 'CLIENT_HR_SPECIALIST',
    scope: 'TENANT',
    is_system: true,
    name_i18n: {
      'ru-RU': 'HR-специалист',
      'uz-Cyrl-UZ': 'HR мутахассис',
      'uz-Latn-UZ': 'HR mutaxassis',
      'en-US': 'HR Specialist',
    },
  },
  {
    code: 'EVALUATION_COMMITTEE_MEMBER',
    scope: 'TENANT',
    is_system: true,
    name_i18n: {
      'ru-RU': 'Член оценочного комитета',
      'uz-Cyrl-UZ': 'Баҳолаш қўмитаси аъзоси',
      'uz-Latn-UZ': 'Baholash qoʻmitasi aʼzosi',
      'en-US': 'Evaluation Committee Member',
    },
  },
  {
    code: 'DEPARTMENT_MANAGER',
    scope: 'TENANT',
    is_system: true,
    name_i18n: {
      'ru-RU': 'Руководитель подразделения',
      'uz-Cyrl-UZ': 'Бўлим раҳбари',
      'uz-Latn-UZ': 'Boʻlim rahbari',
      'en-US': 'Department Manager',
    },
  },
  {
    code: 'VIEWER',
    scope: 'TENANT',
    is_system: true,
    name_i18n: {
      'ru-RU': 'Наблюдатель (только чтение)',
      'uz-Cyrl-UZ': 'Кузатувчи (фақат ўқиш)',
      'uz-Latn-UZ': 'Kuzatuvchi (faqat oʻqish)',
      'en-US': 'Viewer (read-only)',
    },
  },
  {
    code: 'EXTERNAL_AUDITOR',
    scope: 'TENANT',
    is_system: true,
    name_i18n: {
      'ru-RU': 'Внешний аудитор',
      'uz-Cyrl-UZ': 'Ташқи аудитор',
      'uz-Latn-UZ': 'Tashqi auditor',
      'en-US': 'External Auditor',
    },
  },
];

// ---------------------------------------------------------------
// Custom roles (slice E3) — in-memory tenant custom-role store
// ---------------------------------------------------------------

/**
 * A tenant CUSTOM role created via POST /roles. Keyed by id (E3 mutations) but
 * also addressable by code (the E2 permission matrix). `assignmentCount` lets
 * tests drive the ROLE_IN_USE delete guard.
 */
interface CustomRoleRecord {
  id: string;
  code: string;
  name_i18n: Record<string, string>;
  granted: Set<string>;
  assignmentCount: number;
}

/** In-memory custom-role store, seeded with one example custom role. */
const customRoleDb: CustomRoleRecord[] = [
  {
    id: 'role-custom-grading-lead',
    code: 'GRADING_LEAD',
    name_i18n: {
      'ru-RU': 'Руководитель грейдинга',
      'uz-Cyrl-UZ': 'Грейдинг раҳбари',
      'uz-Latn-UZ': 'Greyding rahbari',
      'en-US': 'Grading Lead',
    },
    granted: new Set(['PROJECT_READ', 'METHODOLOGY_READ', 'EVALUATION_READ', 'GRADE_READ']),
    assignmentCount: 0,
  },
];

function customRoleByCode(code: string): CustomRoleRecord | undefined {
  return customRoleDb.find((r) => r.code === code);
}

function customRoleById(id: string): CustomRoleRecord | undefined {
  return customRoleDb.find((r) => r.id === id);
}

/** UPPER_SNAKE_CASE, leading letter, 3–64 chars (mirrors the FE schema). */
const ROLE_CODE_PATTERN = /^[A-Z][A-Z0-9_]{2,63}$/;

/**
 * GET /roles[?assignableOnly=true] — DATA-DRIVEN role catalog.
 *
 * Computes `assignable_by_caller` + `reason_if_not` from the mock current-user
 * role (read from the auth store, which `signIn()` populates):
 *   - HRLAB_SUPER_ADMIN can grant every role.
 *   - any other caller can grant TENANT-scope roles only; PLATFORM rows come
 *     back disabled — HRLAB_SUPER_ADMIN specifically with `HRLAB_ONLY`, the
 *     other HRLAB_* platform roles with `PLATFORM_SCOPE`.
 *
 * Custom (E3) roles are appended: TENANT-scope, `is_custom=true`, carrying their
 * `id` and `assignment_count` so the admin list can edit/delete them and warn
 * before deleting one that is in use.
 *
 * `assignableOnly` is honoured loosely (the mock returns the full catalog either
 * way) so the picker can still render disabled rows with reasons — exactly the
 * behavior the dialogs exercise. The real backend may trim rows more strictly.
 */
function handleRoles(): MatchResult {
  const callerRoles = useAuthStore.getState().user?.roles ?? [];
  const isSuperAdmin = callerRoles.includes('HRLAB_SUPER_ADMIN');
  const systemRows = ROLE_CATALOG.map((r) => {
    let assignable = true;
    let reason: 'HRLAB_ONLY' | 'PLATFORM_SCOPE' | null = null;
    if (!isSuperAdmin && r.scope === 'PLATFORM') {
      assignable = false;
      reason = r.code === 'HRLAB_SUPER_ADMIN' ? 'HRLAB_ONLY' : 'PLATFORM_SCOPE';
    }
    return {
      id: null,
      code: r.code,
      name_i18n: r.name_i18n,
      scope: r.scope,
      is_system: r.is_system,
      is_custom: false,
      assignable_by_caller: assignable,
      reason_if_not: reason,
      assignment_count: null,
    };
  });
  const customRows = customRoleDb.map((r) => ({
    id: r.id,
    code: r.code,
    name_i18n: r.name_i18n,
    scope: 'TENANT' as const,
    is_system: false,
    is_custom: true,
    assignable_by_caller: true,
    reason_if_not: null,
    assignment_count: r.assignmentCount,
  }));
  return ok([...systemRows, ...customRows]);
}

/**
 * POST /roles — create a TENANT custom role (slice E3). Enforces the same guards
 * as the backend so dev/tests are honest:
 *   - code format → 400 VALIDATION_ERROR
 *   - code equals a SYSTEM role code → 409 ROLE_CODE_RESERVED
 *   - code already used by a custom role → 409 ROLE_CODE_EXISTS
 *   - a restricted permission in the set → 422 PERMISSION_RESTRICTED
 *   - a permission the caller does not hold → 422 PERMISSION_NOT_HELD_BY_CALLER
 */
function handleCreateRole(config: AxiosRequestConfig): MatchResult {
  const raw = readBody<{ code?: unknown; name_i18n?: unknown; permission_codes?: unknown }>(config);
  const body = stripTenantFromBody(raw as Record<string, unknown>, '/roles', 'POST') as {
    code?: unknown;
    name_i18n?: unknown;
    permission_codes?: unknown;
  };
  const code = typeof body.code === 'string' ? body.code.trim() : '';
  if (!ROLE_CODE_PATTERN.test(code)) {
    return badRequest('code must be UPPER_SNAKE_CASE (3–64 chars)', 'VALIDATION_ERROR');
  }
  // Reserved: collides with a seeded SYSTEM role code.
  if (ROLE_CATALOG.some((r) => r.code === code)) {
    return { status: 409, body: { code: 'ROLE_CODE_RESERVED', message: 'Code reserved by a system role' } };
  }
  // Duplicate custom code within the tenant.
  if (customRoleByCode(code)) {
    return { status: 409, body: { code: 'ROLE_CODE_EXISTS', message: 'Code already used in this tenant' } };
  }
  const name_i18n =
    body.name_i18n && typeof body.name_i18n === 'object'
      ? (body.name_i18n as Record<string, string>)
      : {};
  if (!name_i18n['ru-RU'] || name_i18n['ru-RU'].trim().length === 0) {
    return badRequest('name_i18n.ru-RU is required', 'VALIDATION_ERROR');
  }
  const codes = Array.isArray(body.permission_codes) ? body.permission_codes.map(String) : [];
  const permErr = validateGrantableCodes(codes);
  if (permErr) return permErr;

  const rec: CustomRoleRecord = {
    id: uuid(),
    code,
    name_i18n,
    granted: new Set(codes),
    assignmentCount: 0,
  };
  customRoleDb.push(rec);
  return ok(
    {
      id: rec.id,
      code: rec.code,
      name_i18n: rec.name_i18n,
      scope: 'TENANT',
      is_system: false,
      is_custom: true,
      assignable_by_caller: true,
      reason_if_not: null,
      assignment_count: 0,
    },
    201,
  );
}

/**
 * PUT /roles/{roleId} — edit a CUSTOM role's name and/or permissions (slice E3).
 *   - unknown / cross-tenant id → 404
 *   - id of a SYSTEM role → 403 (system roles are not editable here)
 *   - restricted / not-held permission → 422 (as for create)
 */
function handleUpdateRole(roleId: string, config: AxiosRequestConfig): MatchResult {
  // A system role id would not be in the custom store → treat as 404 unless it
  // matches a system code (then 403, mirroring the backend's system-role guard).
  const rec = customRoleById(roleId);
  if (!rec) {
    if (ROLE_CATALOG.some((r) => r.code === roleId)) {
      return { status: 403, body: { code: 'FORBIDDEN', message: 'System role is not editable' } };
    }
    return { status: 404, body: { code: 'NOT_FOUND', message: 'Role not found' } };
  }
  const raw = readBody<{ name_i18n?: unknown; permission_codes?: unknown }>(config);
  const body = stripTenantFromBody(raw as Record<string, unknown>, `/roles/${roleId}`, 'PUT') as {
    name_i18n?: unknown;
    permission_codes?: unknown;
  };
  if (body.name_i18n && typeof body.name_i18n === 'object') {
    const name = body.name_i18n as Record<string, string>;
    if (name['ru-RU'] !== undefined && name['ru-RU'].trim().length === 0) {
      return badRequest('name_i18n.ru-RU cannot be blank', 'VALIDATION_ERROR');
    }
    rec.name_i18n = { ...rec.name_i18n, ...name };
  }
  if (Array.isArray(body.permission_codes)) {
    const codes = body.permission_codes.map(String);
    const permErr = validateGrantableCodes(codes);
    if (permErr) return permErr;
    // REPLACE-set, preserving any restricted grants (matrix never sends those).
    const restrictedCodes = new Set(PERMISSION_CATALOG.filter((p) => p.restricted).map((p) => p.code));
    const preservedRestricted = [...rec.granted].filter((c) => restrictedCodes.has(c));
    rec.granted = new Set([...codes, ...preservedRestricted]);
  }
  return ok({
    id: rec.id,
    code: rec.code,
    name_i18n: rec.name_i18n,
    scope: 'TENANT',
    is_system: false,
    is_custom: true,
    assignable_by_caller: true,
    reason_if_not: null,
    assignment_count: rec.assignmentCount,
  });
}

/**
 * DELETE /roles/{roleId} — delete a CUSTOM role (slice E3). 204 on success.
 *   - unknown / cross-tenant id → 404
 *   - system role id → 403
 *   - role assigned to users (assignmentCount > 0) → 409 ROLE_IN_USE
 */
function handleDeleteRole(roleId: string): MatchResult {
  const rec = customRoleById(roleId);
  if (!rec) {
    if (ROLE_CATALOG.some((r) => r.code === roleId)) {
      return { status: 403, body: { code: 'FORBIDDEN', message: 'System role cannot be deleted' } };
    }
    return { status: 404, body: { code: 'NOT_FOUND', message: 'Role not found' } };
  }
  if (rec.assignmentCount > 0) {
    return {
      status: 409,
      body: { code: 'ROLE_IN_USE', message: 'Role is assigned to users; revoke first' },
    };
  }
  const idx = customRoleDb.findIndex((r) => r.id === roleId);
  if (idx >= 0) customRoleDb.splice(idx, 1);
  return { status: 204, body: null };
}

// ---------------------------------------------------------------
// Role PERMISSION MATRIX (slice E2) — GET/PUT /roles/{code}/permissions
// ---------------------------------------------------------------
//
// Representative permission catalog grouped by `resource` (module). This is a
// subset of the real backend catalog, picked to exercise the matrix UX:
// multiple modules, several actions each, and a couple of RESTRICTED rows that
// the matrix must lock. No tenant_id is involved (auth-context from the JWT).

interface PermissionCatalogEntry {
  code: string;
  resource: string;
  action: string;
  /** Locked: cannot be granted/revoked via the endpoint (matrix disables it). */
  restricted: boolean;
}

const PERMISSION_CATALOG: PermissionCatalogEntry[] = [
  { code: 'PROJECT_READ', resource: 'PROJECT', action: 'READ', restricted: false },
  { code: 'PROJECT_CREATE', resource: 'PROJECT', action: 'CREATE', restricted: false },
  { code: 'PROJECT_EDIT', resource: 'PROJECT', action: 'EDIT', restricted: false },
  { code: 'POSITION_READ', resource: 'POSITION', action: 'READ', restricted: false },
  { code: 'POSITION_CREATE', resource: 'POSITION', action: 'CREATE', restricted: false },
  { code: 'POSITION_EDIT', resource: 'POSITION', action: 'EDIT', restricted: false },
  { code: 'METHODOLOGY_READ', resource: 'METHODOLOGY', action: 'READ', restricted: false },
  { code: 'METHODOLOGY_EDIT', resource: 'METHODOLOGY', action: 'EDIT', restricted: false },
  { code: 'METHODOLOGY_APPROVE', resource: 'METHODOLOGY', action: 'APPROVE', restricted: false },
  { code: 'EVALUATION_READ', resource: 'EVALUATION', action: 'READ', restricted: false },
  { code: 'EVALUATION_EDIT', resource: 'EVALUATION', action: 'EDIT', restricted: false },
  { code: 'EVALUATION_APPROVE', resource: 'EVALUATION', action: 'APPROVE', restricted: false },
  { code: 'GRADE_READ', resource: 'GRADE', action: 'READ', restricted: false },
  { code: 'GRADE_EDIT', resource: 'GRADE', action: 'EDIT', restricted: false },
  // Salary permissions are RESTRICTED here: salary visibility is granted
  // per-membership, never on a role — the matrix must lock these.
  { code: 'SALARY_VIEW', resource: 'SALARY', action: 'VIEW', restricted: true },
  { code: 'SALARY_EXPORT', resource: 'SALARY', action: 'EXPORT', restricted: true },
  { code: 'AUDIT_READ', resource: 'AUDIT', action: 'READ', restricted: false },
  // Cross-tenant audit is RESTRICTED — only super-admin platform roles hold it
  // and it is not grantable via this surface.
  { code: 'AUDIT_READ_CROSS_TENANT', resource: 'AUDIT', action: 'READ_CROSS_TENANT', restricted: true },
  { code: 'USER_LIST', resource: 'USER', action: 'LIST', restricted: false },
  { code: 'USER_INVITE', resource: 'USER', action: 'INVITE', restricted: false },
  { code: 'USER_ROLE_ASSIGN', resource: 'USER', action: 'ROLE_ASSIGN', restricted: false },
];

const ALL_CATALOG_CODES = new Set(PERMISSION_CATALOG.map((p) => p.code));

/**
 * Shared guard for a desired non-restricted permission set (used by the E2 PUT
 * permissions handler AND the E3 create/update handlers). Returns an error
 * MatchResult to short-circuit, or null when the set is grantable:
 *   - unknown code → 400 UNKNOWN_PERMISSION_CODE
 *   - restricted code present → 422 PERMISSION_RESTRICTED
 *   - caller does not hold a code (non-super) → 422 PERMISSION_NOT_HELD_BY_CALLER
 */
function validateGrantableCodes(codes: string[]): MatchResult | null {
  const unknown = codes.filter((c) => !ALL_CATALOG_CODES.has(c));
  if (unknown.length > 0) {
    return badRequest(`unknown permission code(s): ${unknown.join(', ')}`, 'UNKNOWN_PERMISSION_CODE');
  }
  const restrictedCodes = new Set(PERMISSION_CATALOG.filter((p) => p.restricted).map((p) => p.code));
  if (codes.some((c) => restrictedCodes.has(c))) {
    return {
      status: 422,
      body: { code: 'PERMISSION_RESTRICTED', message: 'A restricted permission cannot be granted' },
    };
  }
  const callerPerms = new Set<string>(useAuthStore.getState().user?.permissions ?? []);
  const callerIsSuper = (useAuthStore.getState().user?.roles ?? []).includes('HRLAB_SUPER_ADMIN');
  if (!callerIsSuper) {
    const notHeld = codes.filter((c) => !callerPerms.has(c));
    if (notHeld.length > 0) {
      return {
        status: 422,
        body: {
          code: 'PERMISSION_NOT_HELD_BY_CALLER',
          message: `Caller does not hold: ${notHeld.join(', ')}`,
        },
      };
    }
  }
  return null;
}

/**
 * In-memory per-role granted set, seeded with representative defaults. Note:
 * restricted codes may still be granted on a role (e.g. an HRLab platform role
 * holding AUDIT_READ_CROSS_TENANT) — they are simply not togglable via the
 * matrix. PUT preserves restricted grants and only replaces non-restricted ones.
 */
const roleGrantedDb: Record<string, Set<string>> = {
  CLIENT_HR_DIRECTOR: new Set([
    'PROJECT_READ',
    'POSITION_READ',
    'POSITION_CREATE',
    'POSITION_EDIT',
    'METHODOLOGY_READ',
    'EVALUATION_READ',
    'GRADE_READ',
    'AUDIT_READ',
    'USER_LIST',
  ]),
  CLIENT_HR_SPECIALIST: new Set([
    'PROJECT_READ',
    'POSITION_READ',
    'METHODOLOGY_READ',
    'EVALUATION_READ',
    'GRADE_READ',
  ]),
  VIEWER: new Set(['PROJECT_READ', 'POSITION_READ', 'METHODOLOGY_READ', 'EVALUATION_READ', 'GRADE_READ']),
  EXTERNAL_AUDITOR: new Set(['PROJECT_READ', 'AUDIT_READ', 'AUDIT_READ_CROSS_TENANT']),
};

/** Default seed for any role not explicitly listed (minimal read access). For a
 *  CUSTOM role the live grant set lives in the custom-role store. */
function seededGrants(roleCode: string): Set<string> {
  const custom = customRoleByCode(roleCode);
  if (custom) return custom.granted;
  if (!roleGrantedDb[roleCode]) {
    roleGrantedDb[roleCode] = new Set(['PROJECT_READ']);
  }
  return roleGrantedDb[roleCode];
}

/** Does a role with this code exist (system OR custom)? */
function roleExists(roleCode: string): boolean {
  return ROLE_CATALOG.some((r) => r.code === roleCode) || Boolean(customRoleByCode(roleCode));
}

/** Is the named role a system role per the catalog? (custom roles → false) */
function isSystemRole(roleCode: string): boolean {
  return ROLE_CATALOG.find((r) => r.code === roleCode)?.is_system ?? false;
}

function roleScope(roleCode: string): 'PLATFORM' | 'TENANT' {
  const sys = ROLE_CATALOG.find((r) => r.code === roleCode);
  if (sys) return sys.scope;
  return 'TENANT'; // custom roles are always tenant-scoped
}

/**
 * Whether the mock caller may edit a given role's matrix. Mirrors the backend:
 * system roles are editable only by HRLAB_SUPER_ADMIN; custom roles by anyone
 * with USER_ACCESS_MANAGE (assumed for callers reaching this surface).
 */
function callerCanEditRole(roleCode: string): boolean {
  const callerRoles = useAuthStore.getState().user?.roles ?? [];
  const isSuperAdmin = callerRoles.includes('HRLAB_SUPER_ADMIN');
  if (isSystemRole(roleCode)) return isSuperAdmin;
  return true;
}

function buildPermissionItems(roleCode: string) {
  const granted = seededGrants(roleCode);
  return PERMISSION_CATALOG.map((p) => ({
    code: p.code,
    resource: p.resource,
    action: p.action,
    granted: granted.has(p.code),
    restricted: p.restricted,
  }));
}

function handleGetRolePermissions(roleCode: string): MatchResult {
  if (!roleExists(roleCode)) {
    return { status: 404, body: { code: 'NOT_FOUND', message: 'Role not found' } };
  }
  return ok({
    role_code: roleCode,
    scope: roleScope(roleCode),
    is_system: isSystemRole(roleCode),
    editable_by_caller: callerCanEditRole(roleCode),
    items: buildPermissionItems(roleCode),
  });
}

function handlePutRolePermissions(roleCode: string, config: AxiosRequestConfig): MatchResult {
  if (!roleExists(roleCode)) {
    return { status: 404, body: { code: 'NOT_FOUND', message: 'Role not found' } };
  }
  // System role + non-super caller → 403 (mirror backend).
  if (!callerCanEditRole(roleCode)) {
    return {
      status: 403,
      body: { code: 'FORBIDDEN', message: 'System role can be edited only by super admin' },
    };
  }
  const raw = readBody<{ permission_codes?: unknown }>(config);
  const codes = Array.isArray(raw.permission_codes) ? raw.permission_codes.map(String) : null;
  if (codes === null) {
    return badRequest('permission_codes must be an array', 'VALIDATION_ERROR');
  }
  // Shared guard: unknown / restricted / not-held codes (mirrors the backend).
  const permErr = validateGrantableCodes(codes);
  if (permErr) return permErr;
  // REPLACE-set: rebuild the non-restricted grants from the payload, PRESERVING
  // any restricted grants the role already had (matrix never sends those).
  const restrictedCodes = new Set(PERMISSION_CATALOG.filter((p) => p.restricted).map((p) => p.code));
  const existing = seededGrants(roleCode);
  const preservedRestricted = [...existing].filter((c) => restrictedCodes.has(c));
  const nextGranted = new Set([...codes, ...preservedRestricted]);
  const custom = customRoleByCode(roleCode);
  if (custom) custom.granted = nextGranted;
  else roleGrantedDb[roleCode] = nextGranted;
  return ok({
    role_code: roleCode,
    scope: roleScope(roleCode),
    is_system: isSystemRole(roleCode),
    editable_by_caller: true,
    items: buildPermissionItems(roleCode),
  });
}

// ---------------------------------------------------------------
// Dispatcher
// ---------------------------------------------------------------

export function handleUsers(config: AxiosRequestConfig): MatchResult | null {
  const method = (config.method ?? 'get').toUpperCase();
  const url = config.url ?? '';
  const { path, query } = parseUrl(url, config.params as Record<string, unknown> | undefined);

  if (path === '/roles' && method === 'GET') return handleRoles();
  if (path === '/roles' && method === 'POST') return handleCreateRole(config);

  // The /permissions sub-resource (E2) must be matched BEFORE the bare
  // /roles/{id} (E3) so a code/id is never mistaken for the other surface.
  const rolePermsMatch = /^\/roles\/([^/]+)\/permissions$/.exec(path);
  if (rolePermsMatch) {
    const code = decodeURIComponent(rolePermsMatch[1]);
    if (method === 'GET') return handleGetRolePermissions(code);
    if (method === 'PUT') return handlePutRolePermissions(code, config);
  }

  const roleIdMatch = /^\/roles\/([^/]+)$/.exec(path);
  if (roleIdMatch) {
    const id = decodeURIComponent(roleIdMatch[1]);
    if (method === 'PUT') return handleUpdateRole(id, config);
    if (method === 'DELETE') return handleDeleteRole(id);
  }

  if (path === '/users' && method === 'GET') return handleList(query, config);
  if (path === '/users' && method === 'POST') return handleInvite(config);

  const auditMatch = /^\/users\/([^/]+)\/audit$/.exec(path);
  if (auditMatch && method === 'GET') return handleAudit(auditMatch[1], query);

  // Access-scope endpoints (slice E4-S1).
  const scopesMatch = /^\/users\/([^/]+)\/scopes$/.exec(path);
  if (scopesMatch && method === 'GET') return handleGetScopes(scopesMatch[1], query);

  const deptScopesMatch = /^\/users\/([^/]+)\/department-scopes$/.exec(path);
  if (deptScopesMatch && method === 'PUT') {
    return handleSetDepartmentScopes(deptScopesMatch[1], config);
  }

  const projectAssignMatch = /^\/users\/([^/]+)\/project-assignments$/.exec(path);
  if (projectAssignMatch && method === 'PUT') {
    return handleSetProjectAssignments(projectAssignMatch[1], config);
  }

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

  const resetLoginMatch = /^\/users\/([^/]+)\/reset-login$/.exec(path);
  if (resetLoginMatch && method === 'POST') {
    return handleResetLogin(resetLoginMatch[1], config);
  }

  const detailMatch = /^\/users\/([^/]+)$/.exec(path);
  if (detailMatch) {
    const id = detailMatch[1];
    if (method === 'GET') return handleDetail(id);
    if (method === 'PATCH') return handleUpdate(id, config);
  }
  return null;
}
