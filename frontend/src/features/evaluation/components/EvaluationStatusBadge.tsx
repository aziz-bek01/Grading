import { useTranslation } from 'react-i18next';
import { StatusBadge, type StatusTone } from '@/shared/components/status/StatusBadge';
import type { EvaluationStatus } from '../types';

const STATUS_TONE: Record<EvaluationStatus, StatusTone> = {
  DRAFT: 'draft',
  INCOMPLETE: 'incomplete',
  COMPLETE: 'in-review',
  SUBMITTED: 'in-review',
  APPROVED: 'approved',
  LOCKED: 'locked',
  ARCHIVED: 'archived',
};

const STATUS_LABEL_KEY: Record<EvaluationStatus, string> = {
  DRAFT: 'evaluation.status.draft',
  INCOMPLETE: 'evaluation.status.incomplete',
  COMPLETE: 'evaluation.status.complete',
  SUBMITTED: 'evaluation.status.submitted',
  APPROVED: 'evaluation.status.approved',
  LOCKED: 'evaluation.status.locked',
  ARCHIVED: 'evaluation.status.archived',
};

interface Props {
  status: EvaluationStatus;
  className?: string;
}

/**
 * 7-state evaluation status badge. SUBMITTED uses a stronger info tone
 * than COMPLETE to highlight the committee-review state (PRD MVP1-E9-3).
 */
export function EvaluationStatusBadge({ status, className }: Props) {
  const { t } = useTranslation();
  return (
    <StatusBadge
      tone={STATUS_TONE[status]}
      label={t(STATUS_LABEL_KEY[status])}
      className={className}
    />
  );
}
