import { useTranslation } from 'react-i18next';
import { StatusBadge, type StatusTone } from '@/shared/components/status/StatusBadge';
import type { PanelAssignmentStatus } from '../../panelTypes';

const TONE: Record<PanelAssignmentStatus, StatusTone> = {
  ASSIGNED: 'draft',
  IN_PROGRESS: 'incomplete',
  COMPLETED: 'approved',
  WITHDRAWN: 'archived',
};

interface Props {
  status: PanelAssignmentStatus;
  className?: string;
}

/**
 * Per-seat assignment status. Reuses the shared StatusBadge (icon + label,
 * never color-only). STATUS only — this is rendered in the blind-safe progress
 * view, so it must never carry a score.
 */
export function PanelAssignmentStatusBadge({ status, className }: Props) {
  const { t } = useTranslation();
  return (
    <StatusBadge
      tone={TONE[status]}
      label={t(`panel.assignment_status.${status}`)}
      className={className}
    />
  );
}
