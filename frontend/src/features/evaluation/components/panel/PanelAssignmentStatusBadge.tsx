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
  // Unknown status → neutral tone; the i18n key falls back to the raw status.
  const label = t(`panel.assignment_status.${status}`, { defaultValue: String(status) });
  return (
    <StatusBadge
      tone={TONE[status] ?? 'draft'}
      label={label}
      className={className}
    />
  );
}
