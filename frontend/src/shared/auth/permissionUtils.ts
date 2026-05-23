import { PERMISSIONS, type PermissionCode } from '@/shared/types/permissions';
import type { CurrentUser } from './authTypes';

export interface PermissionContext {
  user: CurrentUser | null;
}

export function can(ctx: PermissionContext, code: PermissionCode): boolean {
  if (!ctx.user) return false;
  return ctx.user.permissions.includes(code);
}

export function canAny(ctx: PermissionContext, codes: PermissionCode[]): boolean {
  if (!ctx.user) return false;
  return codes.some((c) => ctx.user!.permissions.includes(c));
}

export function canAll(ctx: PermissionContext, codes: PermissionCode[]): boolean {
  if (!ctx.user) return false;
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
