import { Outlet } from 'react-router-dom';
import { usePermission } from '@/features/auth/usePermission';
import { NoAccessState } from '@/shared/components/feedback/NoAccessState';

/**
 * Salary route guard.
 * Salary access is independent of grade access (security blueprint §11).
 */
export function RequireSalaryPermission() {
  const { canViewSalary } = usePermission();
  if (!canViewSalary()) return <NoAccessState />;
  return <Outlet />;
}
