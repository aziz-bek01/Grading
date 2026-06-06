/**
 * Domain types for the HRLab portfolio Client Companies module (F-3).
 *
 * Mirrors the backend `/api/v1/admin/tenants` + `/api/v1/admin/clients`
 * contract. Field names are snake_case (rest of the codebase mirrors backend
 * JSON shape — see Project / Position / User types).
 *
 * Security notes:
 *   - These endpoints require HRLAB_SUPER_ADMIN authority. The tenant id
 *     appears in the URL path only because the caller is operating
 *     cross-tenant. It is NEVER read from a body field.
 *   - For ordinary multi-tenant traffic the tenant is still derived from
 *     the JWT (see noTenantIdLeak.test.ts).
 */
import type { Locale, PageEnvelope } from '@/shared/types/common';

export type TenantStatus = 'ACTIVE' | 'SUSPENDED' | 'ARCHIVED';

/**
 * Client company (legal entity) — separated from `Tenant` because one
 * customer may run multiple tenants (test/staging/prod) under a single
 * legal entity. The frontend mostly works with Tenant but uses
 * ClientCompany when an admin is editing legal details.
 */
export interface ClientCompany {
  id: string;
  legal_name: string;
  brand_name: string;
  industry: string | null;
  country_code: string | null;
  tax_id: string | null;
  created_at: string;
  updated_at: string;
}

export interface Tenant {
  id: string;
  slug: string;
  display_name: string;
  default_locale: Locale;
  status: TenantStatus;
  client_company_id: string;
  /** Brand colour fingerprint (HSL hue 0-359) used by topbar/avatar accents. */
  fingerprint_hue: number;
  created_at: string;
  updated_at: string;
}

export interface TenantStats {
  project_count: number;
  user_count: number;
  /** ISO 8601; null when the tenant has never seen any activity yet. */
  last_activity_at: string | null;
}

export interface TenantDetail extends Tenant {
  client_company: ClientCompany;
  stats: TenantStats;
}

export type TenantList = PageEnvelope<TenantListRow>;
export type ClientCompanyList = PageEnvelope<ClientCompany>;

/** Lightweight row used by the list table — embeds the client brand name for sorting. */
export interface TenantListRow extends Tenant {
  client_legal_name: string;
  project_count: number;
  user_count: number;
}

// -------------------- Query inputs --------------------

export interface TenantListQuery {
  search?: string;
  status?: TenantStatus;
  page?: number;
  size?: number;
}

export interface ClientCompanyListQuery {
  search?: string;
  page?: number;
  size?: number;
}

// -------------------- Request payloads --------------------

/**
 * Isolation strategy for a freshly created tenant. Mirrors the backend
 * `CreateTenantRequest.isolationMode`. The deployment runs shared mode, so
 * SCHEMA is the safe default value the form submits.
 */
export type TenantIsolationMode = 'SCHEMA' | 'DATABASE';

/**
 * Body for `POST /admin/tenants` — matches `CreateTenantRequest.java` exactly.
 * snake_case to mirror the backend JSON contract (rest of this module is
 * snake_case). tenant_id is NEVER part of this body — the new tenant id is
 * minted by the backend and returned in the response.
 */
export interface CreateTenantPayload {
  slug: string;
  display_name: string;
  default_locale: Locale;
  isolation_mode: TenantIsolationMode;
  company_legal_name: string;
  company_brand_name?: string;
  company_industry?: string;
}

export interface UpdateTenantPayload {
  display_name?: string;
  default_locale?: Locale;
}

export interface UpdateClientCompanyPayload {
  legal_name?: string;
  brand_name?: string;
  industry?: string | null;
  country_code?: string | null;
  tax_id?: string | null;
}

export interface ArchiveTenantPayload {
  reason: string;
}
