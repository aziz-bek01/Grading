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

/** Safe fallback tone for an unknown/undefined status — never index with undefined. */
const FALLBACK_TONE: StatusTone = 'draft';

interface Props {
  /** Accept a possibly-undefined status defensively (real wire / partial data). */
  status: ApprovalRequestStatus | undefined | null;
  outline?: boolean;
  className?: string;
}

export function ApprovalStatusBadge({ status, outline, className }: Props) {
  const { t } = useTranslation();
  const known = status && status in tone;
  const resolvedTone = known ? tone[status as ApprovalRequestStatus] : FALLBACK_TONE;
  const label = known
    ? t(`approval.status.${status}`)
    : t('approval.status.UNKNOWN');
  return (
    <StatusBadge
      tone={resolvedTone}
      label={label}
      outline={outline}
      className={className}
    />
  );
}
