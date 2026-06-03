/**
 * MSW fixtures for the Client Companies (Tenants) module.
 *
 * Five demo tenants spread across two real clients (ACME Holdings + Beta
 * University) plus three additional demo clients so the table has enough
 * rows to exercise pagination and the search predicate.
 *
 * Tenant ids match `devAuth.ts` for ACME (1111…) and Beta (2222…); the
 * remaining UUIDs are stable so links from the dashboard / users module
 * resolve consistently across reloads.
 */
import type {
  ClientCompany,
  Tenant,
  TenantDetail,
  TenantListRow,
  TenantStats,
} from '../types/clientTypes';

export const TENANT_ACME = '11111111-1111-1111-1111-111111111111';
export const TENANT_BETA = '22222222-2222-2222-2222-222222222222';
const TENANT_OZBANK = '33333333-3333-3333-3333-333333333333';
const TENANT_TASHKENT_HOSPITAL = '44444444-4444-4444-4444-444444444444';
const TENANT_UZTEL = '55555555-5555-5555-5555-555555555555';

const CLIENT_ACME = 'c-acme-001';
const CLIENT_BETA = 'c-beta-002';
const CLIENT_OZBANK = 'c-ozbank-003';
const CLIENT_HOSPITAL = 'c-hosp-004';
const CLIENT_UZTEL = 'c-uztel-005';

export const seedClientCompanies: ClientCompany[] = [
  {
    id: CLIENT_ACME,
    legal_name: 'ACME Holdings LLC',
    brand_name: 'ACME Holdings',
    industry: 'Holding / Conglomerate',
    country_code: 'UZ',
    tax_id: '301234567',
    created_at: '2025-09-01T08:00:00Z',
    updated_at: '2026-04-12T10:30:00Z',
  },
  {
    id: CLIENT_BETA,
    legal_name: 'Beta University Educational Foundation',
    brand_name: 'Beta University',
    industry: 'Higher Education',
    country_code: 'UZ',
    tax_id: '309876543',
    created_at: '2025-12-10T08:00:00Z',
    updated_at: '2026-03-05T09:15:00Z',
  },
  {
    id: CLIENT_OZBANK,
    legal_name: 'OzBank Joint-Stock Commercial Bank',
    brand_name: 'OzBank',
    industry: 'Banking',
    country_code: 'UZ',
    tax_id: '300111222',
    created_at: '2026-01-20T08:00:00Z',
    updated_at: '2026-05-10T11:00:00Z',
  },
  {
    id: CLIENT_HOSPITAL,
    legal_name: 'Tashkent City Clinical Hospital N1',
    brand_name: 'Tashkent Hospital',
    industry: 'Healthcare',
    country_code: 'UZ',
    tax_id: '300555444',
    created_at: '2026-02-12T08:00:00Z',
    updated_at: '2026-04-20T13:30:00Z',
  },
  {
    id: CLIENT_UZTEL,
    legal_name: 'UzTel Communications JSC',
    brand_name: 'UzTel',
    industry: 'Telecom',
    country_code: 'UZ',
    tax_id: '300999888',
    created_at: '2025-11-05T08:00:00Z',
    updated_at: '2026-05-18T08:45:00Z',
  },
];

interface MockTenant extends Tenant {
  client_company_id: string;
  stats: TenantStats;
}

export const seedTenants: MockTenant[] = [
  {
    id: TENANT_ACME,
    slug: 'acme',
    display_name: 'ACME Holdings',
    default_locale: 'ru-RU',
    status: 'ACTIVE',
    client_company_id: CLIENT_ACME,
    fingerprint_hue: 215,
    created_at: '2025-09-15T08:00:00Z',
    updated_at: '2026-05-22T11:30:00Z',
    stats: {
      project_count: 3,
      user_count: 14,
      last_activity_at: '2026-05-24T08:30:00Z',
    },
  },
  {
    id: TENANT_BETA,
    slug: 'beta',
    display_name: 'Beta University',
    default_locale: 'uz-Latn-UZ',
    status: 'ACTIVE',
    client_company_id: CLIENT_BETA,
    fingerprint_hue: 145,
    created_at: '2026-01-05T08:00:00Z',
    updated_at: '2026-05-19T15:00:00Z',
    stats: {
      project_count: 1,
      user_count: 6,
      last_activity_at: '2026-05-19T14:00:00Z',
    },
  },
  {
    id: TENANT_OZBANK,
    slug: 'ozbank',
    display_name: 'OzBank',
    default_locale: 'uz-Cyrl-UZ',
    status: 'ACTIVE',
    client_company_id: CLIENT_OZBANK,
    fingerprint_hue: 25,
    created_at: '2026-02-01T08:00:00Z',
    updated_at: '2026-05-12T09:30:00Z',
    stats: {
      project_count: 2,
      user_count: 22,
      last_activity_at: '2026-05-22T12:00:00Z',
    },
  },
  {
    id: TENANT_TASHKENT_HOSPITAL,
    slug: 'tashkent-hospital',
    display_name: 'Tashkent Hospital',
    default_locale: 'ru-RU',
    status: 'SUSPENDED',
    client_company_id: CLIENT_HOSPITAL,
    fingerprint_hue: 175,
    created_at: '2026-02-20T08:00:00Z',
    updated_at: '2026-04-25T10:00:00Z',
    stats: {
      project_count: 0,
      user_count: 3,
      last_activity_at: '2026-04-10T16:00:00Z',
    },
  },
  {
    id: TENANT_UZTEL,
    slug: 'uztel',
    display_name: 'UzTel',
    default_locale: 'en-US',
    status: 'ARCHIVED',
    client_company_id: CLIENT_UZTEL,
    fingerprint_hue: 285,
    created_at: '2025-12-01T08:00:00Z',
    updated_at: '2026-03-15T08:00:00Z',
    stats: {
      project_count: 1,
      user_count: 9,
      last_activity_at: '2026-03-14T12:00:00Z',
    },
  },
];

/** Mutable in-memory store mirrored from the seeds (mutations live here). */
export const clientsDb = {
  tenants: seedTenants.map((t) => ({ ...t, stats: { ...t.stats } })),
  companies: seedClientCompanies.map((c) => ({ ...c })),
};

export function toTenantListRow(t: MockTenant, c: ClientCompany | undefined): TenantListRow {
  return {
    id: t.id,
    slug: t.slug,
    display_name: t.display_name,
    default_locale: t.default_locale,
    status: t.status,
    client_company_id: t.client_company_id,
    fingerprint_hue: t.fingerprint_hue,
    created_at: t.created_at,
    updated_at: t.updated_at,
    client_legal_name: c?.legal_name ?? '',
    project_count: t.stats.project_count,
    user_count: t.stats.user_count,
  };
}

export function toTenantDetail(t: MockTenant, c: ClientCompany | undefined): TenantDetail {
  return {
    id: t.id,
    slug: t.slug,
    display_name: t.display_name,
    default_locale: t.default_locale,
    status: t.status,
    client_company_id: t.client_company_id,
    fingerprint_hue: t.fingerprint_hue,
    created_at: t.created_at,
    updated_at: t.updated_at,
    client_company: c ?? {
      id: t.client_company_id,
      legal_name: 'Unknown client',
      brand_name: 'Unknown',
      industry: null,
      country_code: null,
      tax_id: null,
      created_at: t.created_at,
      updated_at: t.updated_at,
    },
    stats: { ...t.stats },
  };
}
