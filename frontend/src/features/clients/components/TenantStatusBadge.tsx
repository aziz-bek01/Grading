import { useTranslation } from 'react-i18next';
import { StatusBadge, type StatusTone } from '@/shared/components/status/StatusBadge';
import type { TenantStatus } from '../types/clientTypes';

function toneFor(status: TenantStatus): StatusTone {
  switch (status) {
    case 'ACTIVE':
      return 'approved';
    case 'SUSPENDED':
      return 'needs-attention';
    case 'ARCHIVED':
      return 'archived';
    default:
      return 'draft';
  }
}

interface Props {
  status: TenantStatus;
  outline?: boolean;
}

export function TenantStatusBadge({ status, outline }: Props) {
  const { t } = useTranslation();
  const labelMap: Record<TenantStatus, string> = {
    ACTIVE: t('clients.status.active'),
    SUSPENDED: t('clients.status.suspended'),
    ARCHIVED: t('clients.status.archived'),
  };
  return <StatusBadge tone={toneFor(status)} label={labelMap[status] ?? status} outline={outline} />;
}
