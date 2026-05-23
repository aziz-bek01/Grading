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

export type RoleCode =
  | 'HRLAB_SUPER_ADMIN'
  | 'HRLAB_PROJECT_MANAGER'
  | 'HRLAB_CONSULTANT'
  | 'HRLAB_ANALYST'
  | 'CLIENT_ADMIN'
  | 'CLIENT_HR_DIRECTOR'
  | 'CLIENT_HR_SPECIALIST'
  | 'CLIENT_COMMITTEE_MEMBER'
  | 'CLIENT_DEPARTMENT_MANAGER'
  | 'CLIENT_VIEWER'
  | 'AUDITOR';

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
