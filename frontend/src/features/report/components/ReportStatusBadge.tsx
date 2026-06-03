import { useTranslation } from 'react-i18next';
import { StatusBadge, type StatusTone } from '@/shared/components/status/StatusBadge';
import type { ReportStatus } from '../types';

/**
 * 8-status FSM (mirrors backend `ReportStatus` enum) → 9-tone StatusBadge.
 *
 *   REQUESTED   → draft (gray)
 *   QUEUED      → draft (gray)
 *   GENERATING  → in-review (info-blue, animated clock)
 *   GENERATED   → approved (success-green)
 *   FAILED      → needs-attention (danger-red)
 *   DOWNLOADED  → archived (slate)
 *   EXPIRED     → locked (locked-gray)
 *   CANCELLED   → locked (locked-gray)
 */
const TONE_MAP: Record<ReportStatus, StatusTone> = {
  REQUESTED: 'draft',
  QUEUED: 'draft',
  GENERATING: 'in-review',
  GENERATED: 'approved',
  FAILED: 'needs-attention',
  DOWNLOADED: 'archived',
  EXPIRED: 'locked',
  CANCELLED: 'locked',
};

interface Props {
  status: ReportStatus;
  className?: string;
}

export function ReportStatusBadge({ status, className }: Props) {
  const { t } = useTranslation();
  return (
    <StatusBadge
      tone={TONE_MAP[status] ?? 'draft'}
      label={t(`report.status.${status}`)}
      className={className}
    />
  );
}
