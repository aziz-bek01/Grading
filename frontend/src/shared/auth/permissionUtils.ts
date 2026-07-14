import { PERMISSIONS, type PermissionCode } from '@/shared/types/permissions';
import type { CurrentUser } from './authTypes';

export interface PermissionContext {
  user: CurrentUser | null;
}

/**
 * The HRLAB_SUPER_ADMIN role is the platform-wide owner: the backend grants it
 * every permission implicitly. Mirror that here as defense-in-depth so a super
 * admin is NEVER locked out of nav/routes by an incomplete permission list (a
 * partial /users/me, a backend that omits a freshly-added code, etc.). The
 * backend remains the source of truth on every write — this only prevents the
 * FE from hiding UI the user is in fact entitled to.
 *
 * NOTE: salary visibility (canViewSalary) is deliberately NOT short-circuited
 * by this — it additionally requires the explicit `salary_data_permission`
 * flag, which stays a hard gate even for a super admin.
 */
function isSuperAdmin(ctx: PermissionContext): boolean {
  return ctx.user?.roles.includes('HRLAB_SUPER_ADMIN') ?? false;
}

export function can(ctx: PermissionContext, code: PermissionCode): boolean {
  if (!ctx.user) return false;
  if (isSuperAdmin(ctx)) return true;
  return ctx.user.permissions.includes(code);
}

export function canAny(ctx: PermissionContext, codes: PermissionCode[]): boolean {
  if (!ctx.user) return false;
  if (isSuperAdmin(ctx)) return true;
  return codes.some((c) => ctx.user!.permissions.includes(c));
}

export function canAll(ctx: PermissionContext, codes: PermissionCode[]): boolean {
  if (!ctx.user) return false;
  if (isSuperAdmin(ctx)) return true;
  return codes.every((c) => ctx.user!.permissions.includes(c));
}

/**
 * Salary visibility is enforced via an explicit flag from the backend
 * AND via SALARY_VIEW permission. Grade access does NOT imply salary access.
 */
export function canViewSalary(ctx: PermissionContext): boolean {
  if (!ctx.user) return false;
  return ctx.user.salary_data_permission === true && can(ctx, PERMISSIONS.SALARY_VIEW);
}

export function canExport(ctx: PermissionContext): boolean {
  return canAny(ctx, [PERMISSIONS.EXPORT_GENERAL, PERMISSIONS.REPORT_EXPORT]);
}

/**
 * Salary-bearing EXPORT types (SALARY_RANGES / RED_GREEN_CIRCLE /
 * COMPENSATION_SCENARIOS) require SALARY_EXPORT. Like {@link canViewSalary},
 * this is deliberately NOT short-circuited by `isSuperAdmin` — the backend
 * grants HRLAB_SUPER_ADMIN every permission EXCEPT the salary-data trio
 * (SALARY_VIEW / SALARY_EDIT / SALARY_EXPORT), so a plain `can()` check would
 * let a super admin pick a salary export type they cannot actually run (a
 * silent 403). Salary access stays a hard gate on the explicit permission
 * grant even for a super admin.
 */
export function canExportSalaryData(ctx: PermissionContext): boolean {
  if (!ctx.user) return false;
  return ctx.user.permissions.includes(PERMISSIONS.SALARY_EXPORT);
}

export function canViewAudit(ctx: PermissionContext): boolean {
  return canAny(ctx, [PERMISSIONS.AUDIT_READ, PERMISSIONS.AUDIT_READ_CROSS_TENANT]);
}

export function canManageMethodology(ctx: PermissionContext): boolean {
  return canAny(ctx, [
    PERMISSIONS.METHODOLOGY_CREATE,
    PERMISSIONS.METHODOLOGY_EDIT,
    PERMISSIONS.METHODOLOGY_APPROVE,
    PERMISSIONS.METHODOLOGY_LOCK,
  ]);
}

export function canApproveEvaluation(ctx: PermissionContext): boolean {
  return can(ctx, PERMISSIONS.EVALUATION_APPROVE);
}
