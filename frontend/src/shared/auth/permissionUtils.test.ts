import { describe, it, expect } from 'vitest';
import {
  can,
  canAll,
  canAny,
  canViewSalary,
  canViewAudit,
  canManageMethodology,
} from './permissionUtils';
import { PERMISSIONS } from '@/shared/types/permissions';
import type { CurrentUser, RoleCode } from './authTypes';

function makeUser(perms: string[], salary = false, roles: RoleCode[] = []): CurrentUser {
  return {
    id: 'u1',
    email: 'u@example.com',
    name: 'User',
    locale: 'ru-RU',
    roles,
    permissions: perms as CurrentUser['permissions'],
    salary_data_permission: salary,
    tenants: [],
  };
}

describe('permissionUtils', () => {
  it('returns false for everything when no user', () => {
    const ctx = { user: null };
    expect(can(ctx, PERMISSIONS.PROJECT_READ)).toBe(false);
    expect(canAll(ctx, [PERMISSIONS.PROJECT_READ])).toBe(false);
    expect(canAny(ctx, [PERMISSIONS.PROJECT_READ])).toBe(false);
    expect(canViewSalary(ctx)).toBe(false);
  });

  it('can() checks single permission', () => {
    const ctx = { user: makeUser([PERMISSIONS.PROJECT_READ]) };
    expect(can(ctx, PERMISSIONS.PROJECT_READ)).toBe(true);
    expect(can(ctx, PERMISSIONS.METHODOLOGY_EDIT)).toBe(false);
  });

  it('canAll() requires every permission to be present', () => {
    const ctx = { user: makeUser([PERMISSIONS.PROJECT_READ, PERMISSIONS.ORG_READ]) };
    expect(canAll(ctx, [PERMISSIONS.PROJECT_READ, PERMISSIONS.ORG_READ])).toBe(true);
    expect(canAll(ctx, [PERMISSIONS.PROJECT_READ, PERMISSIONS.METHODOLOGY_EDIT])).toBe(false);
  });

  it('canAny() requires at least one', () => {
    const ctx = { user: makeUser([PERMISSIONS.AUDIT_READ]) };
    expect(canAny(ctx, [PERMISSIONS.AUDIT_READ, PERMISSIONS.METHODOLOGY_EDIT])).toBe(true);
    expect(canAny(ctx, [PERMISSIONS.METHODOLOGY_EDIT, PERMISSIONS.SALARY_VIEW])).toBe(false);
  });

  it('canViewSalary() requires BOTH SALARY_VIEW and salary_data_permission flag', () => {
    expect(canViewSalary({ user: makeUser([PERMISSIONS.SALARY_VIEW], false) })).toBe(false);
    expect(canViewSalary({ user: makeUser([], true) })).toBe(false);
    expect(canViewSalary({ user: makeUser([PERMISSIONS.SALARY_VIEW], true) })).toBe(true);
  });

  it('canViewAudit() any of AUDIT_READ / AUDIT_READ_CROSS_TENANT', () => {
    expect(canViewAudit({ user: makeUser([PERMISSIONS.AUDIT_READ]) })).toBe(true);
    expect(canViewAudit({ user: makeUser([PERMISSIONS.AUDIT_READ_CROSS_TENANT]) })).toBe(true);
    expect(canViewAudit({ user: makeUser([]) })).toBe(false);
  });

  it('canManageMethodology() requires methodology-class permission', () => {
    expect(canManageMethodology({ user: makeUser([PERMISSIONS.METHODOLOGY_EDIT]) })).toBe(true);
    expect(canManageMethodology({ user: makeUser([PERMISSIONS.PROJECT_READ]) })).toBe(false);
  });

  it('HRLAB_SUPER_ADMIN short-circuits can/canAny/canAll even with no explicit permissions', () => {
    const superAdmin = { user: makeUser([], false, ['HRLAB_SUPER_ADMIN']) };
    // No permission codes granted, but the role implies all of them.
    expect(can(superAdmin, PERMISSIONS.METHODOLOGY_EDIT)).toBe(true);
    expect(canAny(superAdmin, [PERMISSIONS.AUDIT_READ, PERMISSIONS.PROJECT_READ])).toBe(true);
    expect(canAll(superAdmin, [PERMISSIONS.PROJECT_READ, PERMISSIONS.ORG_READ])).toBe(true);
    expect(canManageMethodology(superAdmin)).toBe(true);
    expect(canViewAudit(superAdmin)).toBe(true);
  });

  it('the bypass does NOT relax salary gating — salary still needs the explicit flag', () => {
    // Super admin without the salary flag must NOT see salary, even though the
    // role short-circuits the SALARY_VIEW permission check.
    expect(canViewSalary({ user: makeUser([], false, ['HRLAB_SUPER_ADMIN']) })).toBe(false);
    expect(canViewSalary({ user: makeUser([], true, ['HRLAB_SUPER_ADMIN']) })).toBe(true);
  });
});
