import { Outlet } from 'react-router-dom';
import { usePermission } from '@/features/auth/usePermission';
import { NoAccessState } from '@/shared/components/feedback/NoAccessState';

export function RequireAuditPermission() {
  const { canViewAudit } = usePermission();
  if (!canViewAudit()) return <NoAccessState />;
  return <Outlet />;
}
