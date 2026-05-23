import { useMemo } from 'react';
import { useAuthStore } from './authStore';
import {
  can,
  canAll,
  canAny,
  canApproveEvaluation,
  canExport,
  canManageMethodology,
  canViewAudit,
  canViewSalary,
} from '@/shared/auth/permissionUtils';
import type { PermissionCode } from '@/shared/types/permissions';

export function usePermission() {
  const user = useAuthStore((s) => s.user);

  return useMemo(() => {
    const ctx = { user };
    return {
      user,
      can: (code: PermissionCode) => can(ctx, code),
      canAny: (codes: PermissionCode[]) => canAny(ctx, codes),
      canAll: (codes: PermissionCode[]) => canAll(ctx, codes),
      canViewSalary: () => canViewSalary(ctx),
      canExport: () => canExport(ctx),
      canViewAudit: () => canViewAudit(ctx),
      canManageMethodology: () => canManageMethodology(ctx),
      canApproveEvaluation: () => canApproveEvaluation(ctx),
    };
  }, [user]);
}
