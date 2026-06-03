/**
 * HRLab portfolio Client Companies API client (F-3).
 *
 * Covers the 8-endpoint contract documented in the F-3 BE plan:
 *   1. GET    /admin/tenants                              → list (search/status/page/size)
 *   2. GET    /admin/tenants/{id}                         → details (with client + stats)
 *   3. PATCH  /admin/tenants/{id}                         → partial update
 *   4. POST   /admin/tenants/{id}/archive                 → archive (soft-delete, reason ≥ 20)
 *   5. GET    /admin/tenants/{id}/stats                   → recomputed stats
 *   6. GET    /admin/clients                              → list (search/page/size)
 *   7. GET    /admin/clients/{clientId}                   → details
 *   8. PATCH  /admin/clients/{clientId}                   → partial update
 *
 * Tenant rule (D-202 / F-208): tenant id NEVER appears as a body field. In
 * this admin module it is part of the URL path because the caller is acting
 * cross-tenant (HRLAB_SUPER_ADMIN). Outside of this module the active
 * tenant is derived from the JWT and is invisible to FE callers.
 */
import { httpClient } from '@/shared/api/httpClient';
import { endpoints } from '@/shared/api/endpoints';
import type { Locale } from '@/shared/types/common';
import type {
  ArchiveTenantPayload,
  ClientCompany,
  ClientCompanyList,
  ClientCompanyListQuery,
  TenantDetail,
  TenantList,
  TenantListQuery,
  TenantListRow,
  TenantStats,
  TenantStatus,
  UpdateClientCompanyPayload,
  UpdateTenantPayload,
} from '../types/clientTypes';

/**
 * React-Query cache keys for tenants + clients. We do NOT scope by the
 * caller's tenant id because every endpoint here is portfolio-wide and
 * lives behind super-admin authority. Cache invalidation hooks blow away
 * the whole `clientKeys.all` namespace on mutations (see useTenantMutations).
 */
export const clientKeys = {
  all: ['clients'] as const,
  tenants: (query?: TenantListQuery) =>
    ['clients', 'tenants', 'list', query ?? null] as const,
  tenant: (id: string) => ['clients', 'tenants', 'detail', id] as const,
  tenantStats: (id: string) => ['clients', 'tenants', 'stats', id] as const,
  clientList: (query?: ClientCompanyListQuery) =>
    ['clients', 'companies', 'list', query ?? null] as const,
  client: (clientId: string) =>
    ['clients', 'companies', 'detail', clientId] as const,
};

export async function fetchTenants(query: TenantListQuery = {}): Promise<TenantList> {
  const res = await httpClient.get<TenantList>(endpoints.admin.tenants, {
    params: cleanParams(query as Record<string, unknown>),
  });
  const env = (res.data ?? {}) as Partial<TenantList> & Record<string, unknown>;
  const rawItems = Array.isArray(env.items) ? (env.items as unknown[]) : [];
  return {
    items: rawItems.map((r) => normalizeTenantRow(r as Record<string, unknown>)),
    page: (env.page as number) ?? 0,
    size: (env.size as number) ?? rawItems.length,
    total_elements: (env.total_elements as number) ?? rawItems.length,
    total_pages: (env.total_pages as number) ?? 1,
  };
}

/** Stable HSL hue (0-359) derived from a string id — gives each tenant a
 *  deterministic avatar/topbar accent colour when the backend does not store
 *  one (the real `TenantListResponse` carries no `fingerprint_hue`). */
function hueFromId(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i += 1) h = (h * 31 + s.charCodeAt(i)) >>> 0;
  return h % 360;
}

/**
 * Wire → domain adapter (Contract-A) for one admin tenant list row.
 *
 * The real backend `TenantListResponse` is a lean projection:
 * `id, slug, display_name, status, isolation_mode, default_locale,
 *  client_legal_name, client_brand_name, client_industry, created_at,
 *  project_count, user_count`. It has NO `updated_at` (the tenant entity
 * only tracks `created_at`) and NO `fingerprint_hue`. The {@link TenantsTable}
 * renders an "Updated" date, a coloured avatar, and the two count pills, so
 * without this mapping the date is "Invalid Date" and the avatar hue is NaN.
 *
 * Derivations:
 *   - `updated_at`     ← `updated_at`, else `created_at` (real timestamp)
 *   - `fingerprint_hue`← explicit, else a stable hash of the id
 *   - `project_count` / `user_count` ← now provided by the backend list
 *     (added to `ListTenantsQuery`); falls back to 0 defensively
 *
 * Tolerant of BOTH the snake_case backend and the MSW fixtures (which already
 * supply the full shape) so unit tests keep passing.
 */
function normalizeTenantRow(raw: Record<string, unknown>): TenantListRow {
  const pick = <T = unknown>(snake: string, camel: string): T | undefined =>
    (raw[snake] ?? raw[camel]) as T | undefined;
  const id = String(raw.id ?? '');
  const createdAt = String(pick<string>('created_at', 'createdAt') ?? '');
  return {
    id,
    slug: String(raw.slug ?? ''),
    display_name: String(pick<string>('display_name', 'displayName') ?? ''),
    default_locale: (pick<Locale>('default_locale', 'defaultLocale') ?? 'ru-RU') as Locale,
    status: raw.status as TenantStatus,
    client_company_id: String(pick<string>('client_company_id', 'clientCompanyId') ?? ''),
    fingerprint_hue: (pick<number>('fingerprint_hue', 'fingerprintHue') ?? hueFromId(id)) as number,
    created_at: createdAt,
    updated_at: String(pick<string>('updated_at', 'updatedAt') ?? createdAt),
    client_legal_name: String(pick<string>('client_legal_name', 'clientLegalName') ?? ''),
    project_count: (pick<number>('project_count', 'projectCount') ?? 0) as number,
    user_count: (pick<number>('user_count', 'userCount') ?? 0) as number,
  };
}

/**
 * Wire → domain adapter (Contract-A) for {@code GET /admin/tenants/{id}}.
 *
 * The real backend `TenantDetailResponse` returns
 * `id, slug, display_name, status, isolation_mode, schema_name,
 *  default_locale, created_at` plus a nested `client_company`. Exactly like
 * the lean list projection it carries NO `updated_at` (the tenant entity only
 * tracks `created_at`), NO `fingerprint_hue`, NO flat `client_company_id`, and
 * NO embedded `stats` (stats live behind {@code GET /{id}/stats}, fetched by a
 * separate hook — {@link ClientDetailsPage} reads `stats` from there). The
 * detail header renders an "Updated" date and a coloured fingerprint avatar,
 * so without this mapping the date is "Invalid Date" and the avatar hue is
 * NaN — mirroring the list-row fix in {@link normalizeTenantRow}.
 *
 * Derivations (same rules as the list row):
 *   - `updated_at`       ← `updated_at`, else `created_at`
 *   - `fingerprint_hue`  ← explicit, else a stable hash of the id
 *   - `client_company`   ← normalized via {@link normalizeClientCompany}
 *   - `client_company_id`← the nested company id (detail has no flat field)
 *   - `stats`            ← embedded `stats` if present (MSW), else zeros; the
 *     page uses the dedicated /stats hook so this is a defensive fallback only
 *
 * Tolerant of BOTH the snake_case backend and the MSW fixtures (which already
 * supply the full shape incl. stats) so unit tests keep passing.
 */
function normalizeTenantDetail(raw: Record<string, unknown>): TenantDetail {
  const pick = <T = unknown>(snake: string, camel: string): T | undefined =>
    (raw[snake] ?? raw[camel]) as T | undefined;
  const id = String(raw.id ?? '');
  const createdAt = String(pick<string>('created_at', 'createdAt') ?? '');
  const company = normalizeClientCompany(
    (pick<Record<string, unknown>>('client_company', 'clientCompany') ?? {}) as Record<string, unknown>,
  );
  return {
    id,
    slug: String(raw.slug ?? ''),
    display_name: String(pick<string>('display_name', 'displayName') ?? ''),
    default_locale: (pick<Locale>('default_locale', 'defaultLocale') ?? 'ru-RU') as Locale,
    status: raw.status as TenantStatus,
    client_company_id: company.id,
    fingerprint_hue: (pick<number>('fingerprint_hue', 'fingerprintHue') ?? hueFromId(id)) as number,
    created_at: createdAt,
    updated_at: String(pick<string>('updated_at', 'updatedAt') ?? createdAt),
    client_company: company,
    stats: normalizeStats(pick<Record<string, unknown>>('stats', 'stats')),
  };
}

/**
 * Wire → domain adapter for a {@link ClientCompany} (legal entity).
 *
 * Used by {@link normalizeTenantDetail} (nested company) and by the standalone
 * {@code /admin/clients} endpoints. The backend `ClientCompanyResponse` is a
 * lean projection — `id, legal_name, brand_name, industry, country_code` and
 * (on the detail endpoint) `tax_id`; it has NO `created_at` / `updated_at` and
 * the list row additionally carries tenant_* columns we ignore here. Falls
 * back so optional fields render as null/"" instead of `undefined`.
 */
function normalizeClientCompany(raw: Record<string, unknown>): ClientCompany {
  const pick = <T = unknown>(snake: string, camel: string): T | undefined =>
    (raw[snake] ?? raw[camel]) as T | undefined;
  const createdAt = String(pick<string>('created_at', 'createdAt') ?? '');
  return {
    id: String(raw.id ?? ''),
    legal_name: String(pick<string>('legal_name', 'legalName') ?? ''),
    brand_name: String(pick<string>('brand_name', 'brandName') ?? ''),
    industry: (pick<string>('industry', 'industry') ?? null) as string | null,
    country_code: (pick<string>('country_code', 'countryCode') ?? null) as string | null,
    tax_id: (pick<string>('tax_id', 'taxId') ?? null) as string | null,
    created_at: createdAt,
    updated_at: String(pick<string>('updated_at', 'updatedAt') ?? createdAt),
  };
}

/** Wire → domain adapter for {@link TenantStats}. Tolerant of an absent object
 *  (the tenant detail payload embeds no stats) and of snake/camel keys. */
function normalizeStats(raw: Record<string, unknown> | undefined): TenantStats {
  const r = raw ?? {};
  const pick = <T = unknown>(snake: string, camel: string): T | undefined =>
    (r[snake] ?? r[camel]) as T | undefined;
  return {
    project_count: (pick<number>('project_count', 'projectCount') ?? 0) as number,
    user_count: (pick<number>('user_count', 'userCount') ?? 0) as number,
    last_activity_at: (pick<string>('last_activity_at', 'lastActivityAt') ?? null) as string | null,
  };
}

export async function fetchTenant(id: string): Promise<TenantDetail> {
  // Raw wire shape — normalised below; type as `unknown` before the adapter.
  const res = await httpClient.get<unknown>(endpoints.admin.tenant(id));
  return normalizeTenantDetail((res.data ?? {}) as Record<string, unknown>);
}

export async function fetchTenantStats(id: string): Promise<TenantStats> {
  const res = await httpClient.get<TenantStats>(endpoints.admin.tenantStats(id));
  return res.data;
}

export async function updateTenant(
  id: string,
  payload: UpdateTenantPayload,
): Promise<TenantDetail> {
  const res = await httpClient.patch<TenantDetail>(
    endpoints.admin.tenant(id),
    payload,
  );
  return res.data;
}

export async function archiveTenant(
  id: string,
  payload: ArchiveTenantPayload,
): Promise<TenantDetail> {
  const res = await httpClient.post<TenantDetail>(
    endpoints.admin.tenantArchive(id),
    payload,
  );
  return res.data;
}

export async function fetchClients(
  query: ClientCompanyListQuery = {},
): Promise<ClientCompanyList> {
  const res = await httpClient.get<ClientCompanyList>(endpoints.admin.clients, {
    params: cleanParams(query as Record<string, unknown>),
  });
  const env = (res.data ?? {}) as Partial<ClientCompanyList> & Record<string, unknown>;
  const rawItems = Array.isArray(env.items) ? (env.items as unknown[]) : [];
  return {
    items: rawItems.map((r) => normalizeClientCompany(r as Record<string, unknown>)),
    page: (env.page as number) ?? 0,
    size: (env.size as number) ?? rawItems.length,
    total_elements: (env.total_elements as number) ?? rawItems.length,
    total_pages: (env.total_pages as number) ?? 1,
  };
}

export async function fetchClient(clientId: string): Promise<ClientCompany> {
  // Raw wire shape — normalised below; type as `unknown` before the adapter.
  const res = await httpClient.get<unknown>(endpoints.admin.client(clientId));
  return normalizeClientCompany((res.data ?? {}) as Record<string, unknown>);
}

export async function updateClient(
  clientId: string,
  payload: UpdateClientCompanyPayload,
): Promise<ClientCompany> {
  const res = await httpClient.patch<ClientCompany>(
    endpoints.admin.client(clientId),
    payload,
  );
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
