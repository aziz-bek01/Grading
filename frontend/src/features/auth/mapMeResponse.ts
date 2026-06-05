import type {
  CurrentUser,
  RoleCode,
  TenantSummary,
} from '@/shared/auth/authTypes';
import type { Locale } from '@/shared/types/common';
import type { PermissionCode } from '@/shared/types/permissions';

/**
 * Wire shape of `GET /api/v1/users/me` (snake_case, per backend contract).
 * Only the fields the frontend consumes are typed; extras are ignored.
 */
export interface MeUserDto {
  id: string;
  email: string;
  full_name: string;
  locale: string;
  status?: string;
}

export interface MeTenantDto {
  id: string;
  slug: string;
  brand_name: string;
  fingerprint_hue?: number;
  membership_status?: string;
  salary_data_permission?: boolean;
}

export interface MeResponseDto {
  user: MeUserDto;
  tenants: MeTenantDto[];
  active_tenant_id?: string;
  roles: string[];
  permissions: string[];
  salary_data_permission: boolean;
}

const SUPPORTED_LOCALES: readonly Locale[] = [
  'ru-RU',
  'uz-Cyrl-UZ',
  'uz-Latn-UZ',
  'en-US',
];

function normalizeLocale(raw: string | undefined): Locale {
  if (raw && (SUPPORTED_LOCALES as readonly string[]).includes(raw)) {
    return raw as Locale;
  }
  return 'ru-RU';
}

function mapTenant(dto: MeTenantDto): TenantSummary {
  return {
    id: dto.id,
    slug: dto.slug,
    brand_name: dto.brand_name,
    fingerprint_hue: dto.fingerprint_hue,
  };
}

/**
 * Map the backend `/users/me` envelope to the in-app `CurrentUser`.
 *
 * Key mappings:
 *   - `user.full_name` -> `name`
 *   - `roles` / `permissions` are passed through as opaque code strings
 *     (backend remains the source of truth; the frontend only gates UI).
 *   - `salary_data_permission` is the top-level explicit flag.
 *
 * Roles/permissions are cast to the local union types; unknown codes are kept
 * as-is so a backend-added code never breaks login (the gate utilities treat
 * unknown codes as "not granted").
 */
export function mapMeResponse(dto: MeResponseDto): CurrentUser {
  return {
    id: dto.user.id,
    email: dto.user.email,
    name: dto.user.full_name,
    locale: normalizeLocale(dto.user.locale),
    roles: (dto.roles ?? []) as RoleCode[],
    permissions: (dto.permissions ?? []) as PermissionCode[],
    salary_data_permission: Boolean(dto.salary_data_permission),
    tenants: (dto.tenants ?? []).map(mapTenant),
  };
}

/**
 * The tenant the backend marks active (if any), so the auth store can prefer it
 * over "first tenant". Returns null when absent or not in the tenant list.
 */
export function pickActiveTenant(dto: MeResponseDto): TenantSummary | null {
  if (!dto.active_tenant_id) return null;
  const match = (dto.tenants ?? []).find((t) => t.id === dto.active_tenant_id);
  return match ? mapTenant(match) : null;
}
