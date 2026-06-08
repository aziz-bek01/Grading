/**
 * Domain types for the Users & Access module.
 *
 * Mirrors the backend `/api/v1/users` contract (10 endpoints documented in
 * MVP 1 prompt §6). Field names are snake_case to match the rest of the
 * frontend (see ProjectTypes, PositionTypes — backend uses snake_case JSON).
 *
 * Security notes:
 *   - `tenant_id` is NEVER sent in business request bodies. The backend
 *     derives tenant scope from the JWT. Tenant id appears in the URL only
 *     for explicit cross-tenant membership endpoints (POST/DELETE
 *     /users/:id/memberships/:tenantId/...) where HRLAB_SUPER_ADMIN
 *     authority is required.
 *   - `salary_data_permission` toggle is audit-sensitive — see
 *     SalaryPermissionToggle component (2-step confirmation + reason ≥ 10).
 */
import type { RoleCode } from '@/shared/auth/authTypes';
import type { Locale, PageEnvelope } from '@/shared/types/common';

/**
 * USER-level lifecycle status. Mirrors the backend user status enum
 * (`ACTIVE, INVITED, DISABLED, LOCKED`). DISABLED is the product's
 * soft-disable ("delete user" — there is intentionally no hard delete);
 * LOCKED is an IdP / security lockout surfaced read-only in the UI.
 *
 * NOTE: this is NOT the same enum as {@link MembershipStatus}. A membership
 * can be SUSPENDED / REVOKED without the user being DISABLED, and vice-versa —
 * do not conflate the two.
 */
export type UserStatus = 'ACTIVE' | 'INVITED' | 'DISABLED' | 'LOCKED';

/**
 * MEMBERSHIP-level status (per tenant). Mirrors the backend membership status
 * enum (`ACTIVE, INVITED, SUSPENDED, REVOKED`). Kept separate from
 * {@link UserStatus} on purpose.
 */
export type MembershipStatus = 'ACTIVE' | 'INVITED' | 'SUSPENDED' | 'REVOKED';

export interface UserRoleAssignment {
  /** Stable id of the role assignment row — used for DELETE. */
  id: string;
  role_code: RoleCode;
  granted_at: string;
  granted_by: string | null;
}

export interface UserMembership {
  tenant_id: string;
  tenant_brand_name: string;
  status: MembershipStatus;
  /** Salary-visibility flag — independent from any specific role. */
  salary_data_permission: boolean;
  salary_data_permission_granted_at?: string | null;
  salary_data_permission_granted_by?: string | null;
  roles: UserRoleAssignment[];
  invited_at: string;
  joined_at?: string | null;
  revoked_at?: string | null;
}

export interface User {
  id: string;
  email: string;
  full_name: string;
  locale: Locale;
  status: UserStatus;
  /** Convenience aggregate — number of tenants the user belongs to (non-revoked). */
  tenant_count: number;
  /** Convenience aggregate — total roles across all active memberships. */
  role_count: number;
  /** All RoleCodes the user holds in their active memberships (denormalised for table filters). */
  role_codes: RoleCode[];
  last_login_at: string | null;
  created_at: string;
  updated_at: string;
}

export interface UserDetails extends User {
  memberships: UserMembership[];
}

export interface UserAuditEvent {
  id: string;
  action: string;
  entity_type: string;
  entity_id: string | null;
  actor_id: string | null;
  actor_name: string | null;
  tenant_id: string | null;
  occurred_at: string;
  metadata?: Record<string, unknown>;
}

export type UserList = PageEnvelope<User>;

export interface UserAuditList {
  items: UserAuditEvent[];
}

// -------------------- Request payloads --------------------

export interface InviteUserPayload {
  email: string;
  full_name: string;
  locale: Locale;
  /**
   * Initial login password the admin issues to the new user. The backend
   * provisions the IdP (Zitadel) account from it and enforces Zitadel default
   * complexity (≥ 8 chars; upper, lower, number, symbol) — a weak password is
   * rejected with 400 `USER_INVITE_WEAK_PASSWORD`. Optional on the wire
   * (omitted when the server's IdP is disabled), required by the UI form.
   */
  password?: string;
  /**
   * Required ONLY for HRLAB_SUPER_ADMIN — they may target an arbitrary tenant.
   * For regular client admins, the backend derives tenant from the JWT and
   * the frontend MUST NOT send it.
   */
  tenant_id?: string;
  role_codes: RoleCode[];
}

export interface UpdateUserPayload {
  full_name?: string;
  locale?: Locale;
  status?: UserStatus;
}

export interface AddMembershipPayload {
  tenant_id: string;
  role_codes: RoleCode[];
}

export interface AddRolePayload {
  role_code: RoleCode;
}

export interface UpdateSalaryPermissionPayload {
  enabled: boolean;
  /** Audit reason — required ≥ 10 chars on the frontend; backend enforces ≥ 10 as well. */
  reason: string;
}

// -------------------- List query --------------------

export interface UserListQuery {
  /** Optional — only honoured for HRLAB_SUPER_ADMIN; ignored otherwise (backend uses JWT). */
  tenant_id?: string;
  status?: UserStatus;
  /** Filter by a single role code (multi-role filter is OR-joined on the client). */
  role?: RoleCode;
  search?: string;
  page?: number;
  size?: number;
}
