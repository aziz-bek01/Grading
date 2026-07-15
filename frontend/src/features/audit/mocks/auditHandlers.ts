/**
 * Audit Reader mock handlers (D-1 FE).
 *
 * Implements the contract:
 *   GET /audit?from=&to=&actorUserId=&tenantId=&action=&entityType=&entityId=&page=&size=
 *   GET /audit/:id
 *
 * Tenant scope mirrors backend behaviour: for non-cross-tenant callers
 * the active tenant is derived from the JWT-substitute header
 * (X-Mock-Tenant-Id) and any `tenantId` query param is honoured only
 * when the caller explicitly opts into cross-tenant view. The mock does
 * NOT enforce the AUDIT_READ_CROSS_TENANT permission — the UI does.
 */
import type { AxiosRequestConfig } from 'axios';
import type { AuditEvent } from '../types/auditTypes';
import { auditDb } from './auditFixtures';

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

function ok(body: unknown, status = 200): MatchResult {
  return { status, body };
}

function notFound(): MatchResult {
  return { status: 404, body: { code: 'NOT_FOUND', message: 'Audit event not found' } };
}

function parseUrl(
  url: string,
  params?: Record<string, unknown>,
): { path: string; query: URLSearchParams } {
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

function handleList(query: URLSearchParams, config: AxiosRequestConfig): MatchResult {
  const headerTenant = resolveMockTenantId(config);
  // Cross-tenant: only when the caller explicitly passed `tenantId`. Real
  // backend additionally checks AUDIT_READ_CROSS_TENANT — we leave that to
  // the UI's RequireAuditPermission guard plus the cross-tenant filter
  // visibility logic in `AuditListPage`.
  const tenantId = query.get('tenantId') ?? headerTenant;

  const from = query.get('from');
  const to = query.get('to');
  const actorUserId = query.get('actorUserId');
  const action = query.get('action');
  const entityType = query.get('entityType');
  const entityId = query.get('entityId');
  const page = parseInt(query.get('page') ?? '0', 10);
  const size = Math.min(parseInt(query.get('size') ?? '20', 10), 200);

  let list: AuditEvent[] = auditDb.events;
  if (tenantId) list = list.filter((e) => e.tenantId === tenantId || e.tenantId == null);
  if (from) list = list.filter((e) => e.createdAt >= from);
  if (to) list = list.filter((e) => e.createdAt <= to);
  if (actorUserId) list = list.filter((e) => e.actorUserId === actorUserId);
  if (action) list = list.filter((e) => e.action === action);
  if (entityType) list = list.filter((e) => e.entityType === entityType);
  if (entityId) list = list.filter((e) => e.entityId === entityId);

  return ok(paginate(list, page, size));
}

function handleDetail(id: string): MatchResult {
  const e = auditDb.events.find((x) => x.id === id);
  if (!e) return notFound();
  return ok(e);
}

/**
 * MVP1-E10-1 — mock for `GET /audit/integrity`. Recomputes a plausible
 * result from the in-memory fixture chain for the caller's mock tenant:
 * every seed row is treated as "current format" (fully independently
 * re-hashable) and the run is never bounded, so the default dev experience
 * is an honest full INTACT pass. Individual component tests override this
 * with `vi.spyOn(httpClient, 'get')` to exercise BROKEN / partial / empty.
 */
function handleIntegrity(config: AxiosRequestConfig): MatchResult {
  const tenantId = resolveMockTenantId(config);
  const rows = auditDb.events.filter((e) => e.tenantId === tenantId || e.tenantId == null);
  const chainLength = rows.length;
  const sorted = [...rows].sort((a, b) => (a.createdAt < b.createdAt ? -1 : 1));
  const now = new Date().toISOString();
  return ok({
    tenant_id: tenantId,
    status: chainLength === 0 ? 'EMPTY' : 'INTACT',
    intact: true,
    checked_count: chainLength,
    chain_length: chainLength,
    independently_verified_count: chainLength,
    legacy_unverifiable_count: 0,
    verifiable_from: sorted[0]?.createdAt ?? null,
    verified_through: sorted[sorted.length - 1]?.createdAt ?? null,
    bounded: false,
    max_rows: 50000,
    first_break: null,
    verified_at: now,
  });
}

export function handleAudit(config: AxiosRequestConfig): MatchResult | null {
  const method = (config.method ?? 'get').toUpperCase();
  const url = config.url ?? '';
  const { path, query } = parseUrl(url, config.params as Record<string, unknown> | undefined);

  if (path === '/audit/integrity' && method === 'GET') return handleIntegrity(config);
  if (path === '/audit' && method === 'GET') return handleList(query, config);

  const detail = /^\/audit\/([^/]+)$/.exec(path);
  if (detail && method === 'GET') return handleDetail(detail[1]);

  return null;
}
