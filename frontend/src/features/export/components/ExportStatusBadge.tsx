import { AsyncJobStatusBadge } from '@/shared/components/data/AsyncJobStatusBadge';
import type { StatusTone } from '@/shared/components/status/StatusBadge';
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
  return (
    <AsyncJobStatusBadge
      status={status}
      toneMap={TONE_MAP}
      labelPrefix="export.status"
      className={className}
    />
  );
}
