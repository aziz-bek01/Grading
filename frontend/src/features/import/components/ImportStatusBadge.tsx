/**
 * Visualises one of the 14 ImportBatch statuses.
 * Maps statuses to the existing StatusBadge tone palette + i18n labels.
 */
import { useTranslation } from 'react-i18next';
import { StatusBadge, type StatusTone } from '@/shared/components/status/StatusBadge';
import type { ImportBatchStatus } from '../types';

const TONE_MAP: Record<ImportBatchStatus, StatusTone> = {
  UPLOADED: 'draft',
  SCANNING: 'in-review',
  SCAN_FAILED: 'needs-attention',
  PARSING: 'in-review',
  VALIDATING: 'in-review',
  VALIDATION_FAILED: 'needs-attention',
  READY_FOR_REVIEW: 'incomplete',
  READY_TO_COMMIT: 'approved',
  COMMITTING: 'in-review',
  COMMITTED: 'approved',
  PARTIALLY_COMMITTED: 'incomplete',
  FAILED: 'needs-attention',
  CANCELLED: 'locked',
  ARCHIVED: 'archived',
};

interface Props {
  status: ImportBatchStatus;
  outline?: boolean;
  className?: string;
}

export function ImportStatusBadge({ status, outline, className }: Props) {
  const { t } = useTranslation();
  const tone = TONE_MAP[status] ?? 'draft';
  return (
    <StatusBadge
      tone={tone}
      label={t(`import.status.${status}`)}
      outline={outline}
      className={className}
    />
  );
}
