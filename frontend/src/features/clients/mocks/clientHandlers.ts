/**
 * Mock handlers for the Client Companies module (F-3).
 *
 * Endpoints handled (mirrors clientApi.ts):
 *   GET    /admin/tenants
 *   GET    /admin/tenants/{id}
 *   PATCH  /admin/tenants/{id}
 *   POST   /admin/tenants/{id}/archive
 *   GET    /admin/tenants/{id}/stats
 *   GET    /admin/clients
 *   GET    /admin/clients/{clientId}
 *   PATCH  /admin/clients/{clientId}
 *
 * All write handlers drop a `tenant_id` / `tenantId` body field with a
 * warning (matches the rest of the mock layer — see handlers.ts header).
 */
import type { AxiosRequestConfig } from 'axios';
import type {
  ArchiveTenantPayload,
  ClientCompany,
  CreateTenantPayload,
  TenantStatus,
  UpdateClientCompanyPayload,
  UpdateTenantPayload,
} from '../types/clientTypes';
import { clientsDb, toTenantDetail, toTenantListRow } from './clientFixtures';

interface MatchResult {
  status: number;
  body: unknown;
  contentType?: string;
}

function ok(body: unknown, status = 200): MatchResult {
  return { status, body };
}

function notFound(): MatchResult {
  return { status: 404, body: { code: 'NOT_FOUND', message: 'Tenant or client not found' } };
}

function badRequest(message: string, code = 'BAD_REQUEST'): MatchResult {
  return { status: 400, body: { code, message } };
}

function readBody<T>(config: AxiosRequestConfig): T {
  if (!config.data) return {} as T;
  if (typeof config.data === 'string') return JSON.parse(config.data) as T;
  return config.data as T;
}

function stripTenantFromBody<T extends Record<string, unknown>>(
  body: T,
  url: string,
  method: string,
): T {
  if (!body || typeof body !== 'object') return body;
  if ('tenant_id' in body || 'tenantId' in body) {
    // eslint-disable-next-line no-console
    console.warn(
      `[mock] ignoring tenant_id in request body for ${method} ${url}. ` +
        'Backend derives tenant from JWT; the admin endpoints take the id from the URL path.',
    );
    const clone: Record<string, unknown> = { ...body };
    delete clone.tenant_id;
    delete clone.tenantId;
    return clone as T;
  }
  return body;
}

function parseUrl(url: string, params?: Record<string, unknown>) {
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

// ---------------------------------------------------------------
// Handlers
// ---------------------------------------------------------------

function handleListTenants(query: URLSearchParams): MatchResult {
  const search = (query.get('search') ?? '').trim().toLowerCase();
  const status = query.get('status') as TenantStatus | null;
  const page = parseInt(query.get('page') ?? '0', 10);
  const size = parseInt(query.get('size') ?? '20', 10);

  let list = clientsDb.tenants;
  if (status) list = list.filter((t) => t.status === status);
  if (search) {
    list = list.filter(
      (t) =>
        t.display_name.toLowerCase().includes(search) ||
        t.slug.toLowerCase().includes(search),
    );
  }
  const rows = list.map((t) =>
    toTenantListRow(t, clientsDb.companies.find((c) => c.id === t.client_company_id)),
  );
  return ok(paginate(rows, page, size));
}

function uuid(prefix: string): string {
  const rnd = Math.random().toString(16).slice(2).padEnd(12, '0').slice(0, 12);
  return `${prefix}-${rnd.slice(0, 4)}-${rnd.slice(4, 8)}-${rnd.slice(8, 12)}-${Date.now().toString(16).padStart(12, '0').slice(-12)}`;
}

function hueFromString(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i += 1) h = (h * 31 + s.charCodeAt(i)) >>> 0;
  return h % 360;
}

function handleTenantCreate(config: AxiosRequestConfig): MatchResult {
  const raw = readBody<CreateTenantPayload & Record<string, unknown>>(config);
  const body = stripTenantFromBody(raw, '/admin/tenants', 'POST');

  const slug = String(body.slug ?? '').trim();
  if (!slug || !/^[a-z][a-z0-9_]{2,63}$/.test(slug)) {
    return badRequest('slug must match ^[a-z][a-z0-9_]{2,63}$', 'VALIDATION_ERROR');
  }
  // Slug collision → 409 TENANT_SLUG_TAKEN (matches backend contract).
  if (clientsDb.tenants.some((tn) => tn.slug === slug)) {
    return { status: 409, body: { code: 'TENANT_SLUG_TAKEN', message: `slug '${slug}' is already taken` } };
  }
  if (!body.display_name || !String(body.display_name).trim()) {
    return badRequest('display_name is required', 'VALIDATION_ERROR');
  }
  if (!body.company_legal_name || !String(body.company_legal_name).trim()) {
    return badRequest('company_legal_name is required', 'VALIDATION_ERROR');
  }

  const now = new Date().toISOString();
  const company: ClientCompany = {
    id: uuid('cccc1111'),
    legal_name: String(body.company_legal_name),
    brand_name: String(body.company_brand_name ?? body.display_name ?? ''),
    industry: body.company_industry ? String(body.company_industry) : null,
    country_code: null,
    tax_id: null,
    created_at: now,
    updated_at: now,
  };
  const tenant = {
    id: uuid('eeee1111'),
    slug,
    display_name: String(body.display_name),
    default_locale: body.default_locale,
    status: 'ACTIVE' as TenantStatus,
    client_company_id: company.id,
    fingerprint_hue: hueFromString(slug),
    created_at: now,
    updated_at: now,
    stats: { project_count: 0, user_count: 1, last_activity_at: now },
  };
  clientsDb.companies.push(company);
  clientsDb.tenants.push(tenant);
  return ok(toTenantDetail(tenant, company), 201);
}

function handleTenantDetail(id: string): MatchResult {
  const t = clientsDb.tenants.find((x) => x.id === id);
  if (!t) return notFound();
  const c = clientsDb.companies.find((x) => x.id === t.client_company_id);
  return ok(toTenantDetail(t, c));
}

function handleTenantStats(id: string): MatchResult {
  const t = clientsDb.tenants.find((x) => x.id === id);
  if (!t) return notFound();
  return ok({ ...t.stats });
}

function handleTenantUpdate(id: string, config: AxiosRequestConfig): MatchResult {
  const t = clientsDb.tenants.find((x) => x.id === id);
  if (!t) return notFound();
  if (t.status === 'ARCHIVED') {
    return { status: 409, body: { code: 'TENANT_ARCHIVED', message: 'Archived tenant is read-only' } };
  }
  const raw = readBody<UpdateTenantPayload & Record<string, unknown>>(config);
  const body = stripTenantFromBody(raw, `/admin/tenants/${id}`, 'PATCH');
  if (body.display_name !== undefined) t.display_name = body.display_name;
  if (body.default_locale !== undefined) t.default_locale = body.default_locale;
  t.updated_at = new Date().toISOString();
  const c = clientsDb.companies.find((x) => x.id === t.client_company_id);
  return ok(toTenantDetail(t, c));
}

function handleTenantArchive(id: string, config: AxiosRequestConfig): MatchResult {
  const t = clientsDb.tenants.find((x) => x.id === id);
  if (!t) return notFound();
  if (t.status === 'ARCHIVED') {
    return { status: 409, body: { code: 'ALREADY_ARCHIVED', message: 'Tenant is already archived' } };
  }
  const raw = readBody<ArchiveTenantPayload & Record<string, unknown>>(config);
  const body = stripTenantFromBody(raw, `/admin/tenants/${id}/archive`, 'POST');
  if (!body.reason || body.reason.trim().length < 20) {
    return badRequest('reason must be at least 20 characters', 'REASON_REQUIRED');
  }
  t.status = 'ARCHIVED';
  t.updated_at = new Date().toISOString();
  const c = clientsDb.companies.find((x) => x.id === t.client_company_id);
  return ok(toTenantDetail(t, c));
}

function handleListClients(query: URLSearchParams): MatchResult {
  const search = (query.get('search') ?? '').trim().toLowerCase();
  const page = parseInt(query.get('page') ?? '0', 10);
  const size = parseInt(query.get('size') ?? '50', 10);
  let list = clientsDb.companies;
  if (search) {
    list = list.filter(
      (c) =>
        c.legal_name.toLowerCase().includes(search) ||
        c.brand_name.toLowerCase().includes(search),
    );
  }
  return ok(paginate(list, page, size));
}

function handleClientDetail(clientId: string): MatchResult {
  const c = clientsDb.companies.find((x) => x.id === clientId);
  if (!c) return notFound();
  return ok(c);
}

function handleClientUpdate(clientId: string, config: AxiosRequestConfig): MatchResult {
  const c = clientsDb.companies.find((x) => x.id === clientId);
  if (!c) return notFound();
  const raw = readBody<UpdateClientCompanyPayload & Record<string, unknown>>(config);
  const body = stripTenantFromBody(raw, `/admin/clients/${clientId}`, 'PATCH');
  if (body.legal_name !== undefined) c.legal_name = body.legal_name;
  if (body.brand_name !== undefined) c.brand_name = body.brand_name;
  if (body.industry !== undefined) c.industry = body.industry;
  if (body.country_code !== undefined) c.country_code = body.country_code;
  if (body.tax_id !== undefined) c.tax_id = body.tax_id;
  c.updated_at = new Date().toISOString();
  return ok(c);
}

// ---------------------------------------------------------------
// Dispatcher
// ---------------------------------------------------------------

export function handleClients(config: AxiosRequestConfig): MatchResult | null {
  const method = (config.method ?? 'get').toUpperCase();
  const url = config.url ?? '';
  const { path, query } = parseUrl(url, config.params as Record<string, unknown> | undefined);

  if (path === '/admin/tenants' && method === 'GET') return handleListTenants(query);
  if (path === '/admin/tenants' && method === 'POST') return handleTenantCreate(config);
  if (path === '/admin/clients' && method === 'GET') return handleListClients(query);

  const tenantArchive = /^\/admin\/tenants\/([^/]+)\/archive$/.exec(path);
  if (tenantArchive && method === 'POST') return handleTenantArchive(tenantArchive[1], config);

  const tenantStats = /^\/admin\/tenants\/([^/]+)\/stats$/.exec(path);
  if (tenantStats && method === 'GET') return handleTenantStats(tenantStats[1]);

  const tenantDetail = /^\/admin\/tenants\/([^/]+)$/.exec(path);
  if (tenantDetail) {
    const id = tenantDetail[1];
    if (method === 'GET') return handleTenantDetail(id);
    if (method === 'PATCH') return handleTenantUpdate(id, config);
  }

  const clientDetail = /^\/admin\/clients\/([^/]+)$/.exec(path);
  if (clientDetail) {
    const id = clientDetail[1];
    if (method === 'GET') return handleClientDetail(id);
    if (method === 'PATCH') return handleClientUpdate(id, config);
  }

  return null;
}
