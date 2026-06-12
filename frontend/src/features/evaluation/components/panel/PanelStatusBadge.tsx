import { useTranslation } from 'react-i18next';
import { StatusBadge, type StatusTone } from '@/shared/components/status/StatusBadge';
import type { PanelStatus } from '../../panelTypes';

const STATUS_TONE: Record<PanelStatus, StatusTone> = {
  COLLECTING: 'draft',
  AWAITING_EVALUATIONS: 'in-review',
  AVERAGED: 'in-review',
  SUBMITTED: 'in-review',
  APPROVED: 'approved',
  LOCKED: 'locked',
  ARCHIVED: 'archived',
};

interface Props {
  status: PanelStatus;
  className?: string;
}

/** Panel lifecycle badge — reuses the shared StatusBadge tone system. */
export function PanelStatusBadge({ status, className }: Props) {
  const { t } = useTranslation();
  return (
    <StatusBadge
      tone={STATUS_TONE[status]}
      label={t(`panel.status.${status}`)}
      className={className}
    />
  );
}
