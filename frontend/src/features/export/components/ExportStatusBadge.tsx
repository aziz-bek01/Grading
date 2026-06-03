import { useTranslation } from 'react-i18next';
import { StatusBadge, type StatusTone } from '@/shared/components/status/StatusBadge';
import type { ExportJobStatus } from '../types';

const TONE_MAP: Record<ExportJobStatus, StatusTone> = {
  REQUESTED: 'draft',
  QUEUED: 'in-review',
  GENERATING: 'in-review',
  GENERATED: 'approved',
  FAILED: 'needs-attention',
  DOWNLOADED: 'archived',
  EXPIRED: 'locked',
  CANCELLED: 'locked',
};

interface Props {
  status: ExportJobStatus;
  className?: string;
}

export function ExportStatusBadge({ status, className }: Props) {
  const { t } = useTranslation();
  return (
    <StatusBadge
      tone={TONE_MAP[status] ?? 'draft'}
      label={t(`export.status.${status}`)}
      className={className}
    />
  );
}
