import { useTranslation } from 'react-i18next';
import { StatusBadge, type StatusTone } from '@/shared/components/status/StatusBadge';
import type { JobProfileStatus } from '../types';

const tone: Record<JobProfileStatus, StatusTone> = {
  DRAFT: 'draft',
  UNDER_REVIEW: 'in-review',
  APPROVED: 'approved',
  ARCHIVED: 'archived',
};

const labelKey: Record<JobProfileStatus, string> = {
  DRAFT: 'jobProfile.status.draft',
  UNDER_REVIEW: 'jobProfile.status.under_review',
  APPROVED: 'jobProfile.status.approved',
  ARCHIVED: 'jobProfile.status.archived',
};

export function JobProfileStatusBadge({ status, className }: { status: JobProfileStatus; className?: string }) {
  const { t } = useTranslation();
  // Unknown status → neutral tone + raw code, never an empty/crashing badge.
  const key = labelKey[status];
  return (
    <StatusBadge
      tone={tone[status] ?? 'draft'}
      label={key ? t(key) : String(status)}
      className={className}
    />
  );
}
