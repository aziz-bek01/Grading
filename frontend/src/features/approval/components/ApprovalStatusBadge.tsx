import { useTranslation } from 'react-i18next';
import { StatusBadge, type StatusTone } from '@/shared/components/status/StatusBadge';
import type { ApprovalRequestStatus } from '../types';

const tone: Record<ApprovalRequestStatus, StatusTone> = {
  PENDING: 'in-review',
  APPROVED: 'approved',
  REJECTED: 'needs-attention',
  CHANGES_REQUESTED: 'incomplete',
  CANCELLED: 'locked',
};

interface Props {
  status: ApprovalRequestStatus;
  outline?: boolean;
  className?: string;
}

export function ApprovalStatusBadge({ status, outline, className }: Props) {
  const { t } = useTranslation();
  return (
    <StatusBadge
      tone={tone[status]}
      label={t(`approval.status.${status}`)}
      outline={outline}
      className={className}
    />
  );
}
