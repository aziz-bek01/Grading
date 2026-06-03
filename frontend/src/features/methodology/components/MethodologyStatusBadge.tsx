import { useTranslation } from 'react-i18next';
import { StatusBadge, type StatusTone } from '@/shared/components/status/StatusBadge';
import type { MethodologyVersionStatus } from '../types';

const map: Record<MethodologyVersionStatus, { tone: StatusTone; key: string }> = {
  DRAFT: { tone: 'draft', key: 'methodology.status.draft' },
  APPROVED: { tone: 'approved', key: 'methodology.status.approved' },
  LOCKED: { tone: 'locked', key: 'methodology.status.locked' },
  ARCHIVED: { tone: 'archived', key: 'methodology.status.archived' },
};

/** Methodology-version status pill — uses the global StatusBadge tones. */
export function MethodologyStatusBadge({
  status,
  className,
}: {
  status: MethodologyVersionStatus;
  className?: string;
}) {
  const { t } = useTranslation();
  const spec = map[status];
  return <StatusBadge tone={spec.tone} label={t(spec.key)} className={className} />;
}
