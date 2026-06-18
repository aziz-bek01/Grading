import { useTranslation } from 'react-i18next';
import { StatusBadge } from '@/shared/components/status/StatusBadge';
import type { MethodologyStatus } from '../types';

/**
 * Badge for the METHODOLOGY CONTAINER status (ACTIVE / ARCHIVED), distinct
 * from MethodologyStatusBadge which shows the VERSION status (DRAFT / APPROVED
 * / LOCKED / ARCHIVED). Display label: "Inactive" (not "Archived") per spec.
 */
export function MethodologyContainerStatusBadge({
  status,
  className,
}: {
  status: MethodologyStatus;
  className?: string;
}) {
  const { t } = useTranslation();
  return (
    <StatusBadge
      tone={status === 'ACTIVE' ? 'approved' : 'archived'}
      label={t(
        status === 'ACTIVE'
          ? 'methodology.container_status.ACTIVE'
          : 'methodology.container_status.ARCHIVED',
      )}
      className={className}
    />
  );
}
