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
  /**
   * Role codes to grant. Typed as `string[]` (not `RoleCode[]`) because the
   * picker is DATA-DRIVEN from `GET /roles` — custom roles created at runtime
   * are not in the build-time {@link RoleCode} union. The backend validates each
   * code against the live catalog and rejects unknown/non-grantable ones.
   */
  role_codes: string[];
}

export interface UpdateUserPayload {
  full_name?: string;
  /**
   * New email. Sent ONLY when changed from the current value. The backend
   * validates syntax + uniqueness and rejects a clash with 400
   * `USER_EMAIL_TAKEN`.
   */
  email?: string;
  /**
   * New login password. Sent ONLY when the admin entered one (omitted when the
   * field is left blank → password unchanged). The backend enforces the same
   * complexity as the invite flow (≥ 8 chars; upper, lower, number, symbol) and
   * rejects a weak value with 400 `USER_INVITE_WEAK_PASSWORD`; if the user has
   * no IdP account it returns 400 `USER_NO_IDP_ACCOUNT`.
   */
  password?: string;
  locale?: Locale;
  status?: UserStatus;
}

/**
 * POST /users/:id/reset-login body. The backend re-provisions the user's IdP
 * (login) account by their grading email — creating a fresh ACTIVE account if
 * missing / mis-linked — and sets this admin-chosen password. Same complexity
 * policy as the invite flow (≥ 8 chars; upper, lower, number, symbol); a weak
 * value → 400 `USER_INVITE_WEAK_PASSWORD`. On success the standard UserDetails
 * is returned and the user's status becomes ACTIVE.
 */
export interface ResetLoginPayload {
  password: string;
}

export interface AddMembershipPayload {
  tenant_id: string;
  /** Data-driven role codes — see {@link InviteUserPayload.role_codes}. */
  role_codes: string[];
}

export interface AddRolePayload {
  /** Data-driven role code — see {@link InviteUserPayload.role_codes}. */
  role_code: string;
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
