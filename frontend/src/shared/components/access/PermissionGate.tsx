import type { ReactNode } from 'react';
import { usePermission } from '@/features/auth/usePermission';
import type { PermissionCode } from '@/shared/types/permissions';

interface PermissionGateProps {
  permission: PermissionCode | PermissionCode[];
  mode?: 'all' | 'any';
  fallback?: ReactNode;
  children: ReactNode;
}

/**
 * Conditionally renders children based on permission(s).
 * When the user lacks permission, children are NOT mounted (no DOM presence).
 * Frontend gating is UX-only; backend remains source of truth.
 */
export function PermissionGate({ permission, mode = 'all', fallback = null, children }: PermissionGateProps) {
  const { can, canAll, canAny } = usePermission();
  const allowed = Array.isArray(permission)
    ? mode === 'all'
      ? canAll(permission)
      : canAny(permission)
    : can(permission);

  if (!allowed) return <>{fallback}</>;
  return <>{children}</>;
}
