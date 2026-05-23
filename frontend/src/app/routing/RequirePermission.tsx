import { Outlet } from 'react-router-dom';
import type { PermissionCode } from '@/shared/types/permissions';
import { usePermission } from '@/features/auth/usePermission';
import { NoAccessState } from '@/shared/components/feedback/NoAccessState';

interface Props {
  permissions: PermissionCode | PermissionCode[];
  mode?: 'all' | 'any';
}

export function RequirePermission({ permissions, mode = 'any' }: Props) {
  const { can, canAll, canAny } = usePermission();
  const codes = Array.isArray(permissions) ? permissions : [permissions];
  const allowed = codes.length === 1 ? can(codes[0]) : mode === 'all' ? canAll(codes) : canAny(codes);
  if (!allowed) return <NoAccessState />;
  return <Outlet />;
}
