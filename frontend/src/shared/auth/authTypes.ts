import type { Locale } from '@/shared/types/common';
import type { PermissionCode } from '@/shared/types/permissions';

export interface TenantSummary {
  id: string;
  slug: string;
  brand_name: string;
  /** Hue (0-360) used as deterministic tenant visual fingerprint. */
  fingerprint_hue?: number;
}

export interface ProjectSummary {
  id: string;
  slug: string;
  name: string;
  tenant_id: string;
  status?: string;
  archived?: boolean;
}

/**
 * Canonical role codes — MUST match the backend `roles` table seeded in
 * `backend/.../seeds/002-default-roles.yaml`. Frontend constants that diverge
 * from this catalog cause invite/role-assign 400s in production.
 */
export type RoleCode =
  | 'HRLAB_SUPER_ADMIN'
  | 'HRLAB_PROJECT_MANAGER'
  | 'HRLAB_CONSULTANT'
  | 'HRLAB_ANALYST'
  | 'CLIENT_COMPANY_ADMIN'
  | 'CLIENT_HR_DIRECTOR'
  | 'CLIENT_HR_SPECIALIST'
  | 'EVALUATION_COMMITTEE_MEMBER'
  | 'DEPARTMENT_MANAGER'
  | 'VIEWER'
  | 'EXTERNAL_AUDITOR';

export interface CurrentUser {
  id: string;
  email: string;
  name: string;
  locale: Locale;
  roles: RoleCode[];
  permissions: PermissionCode[];
  /** Explicit salary visibility flag — independent from grade access. */
  salary_data_permission: boolean;
  tenants: TenantSummary[];
}

/**
 * Access token model.
 * IMPORTANT: tokens are held in memory only — never localStorage / sessionStorage.
 */
export interface AccessToken {
  value: string;
  expiresAt: number;
}
