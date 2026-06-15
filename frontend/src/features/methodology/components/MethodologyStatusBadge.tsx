import { useTranslation } from 'react-i18next';
import { StatusBadge, type StatusTone } from '@/shared/components/status/StatusBadge';
import { resolveStatusSpec } from '@/shared/components/status/statusSpec';
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
  const entry = map[status];
  const spec = entry
    ? { tone: entry.tone, label: t(entry.key) }
    : resolveStatusSpec({}, status);
  return <StatusBadge tone={spec.tone} label={spec.label} className={className} />;
}
