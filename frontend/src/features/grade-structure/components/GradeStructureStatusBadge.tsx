import { useTranslation } from 'react-i18next';
import { StatusBadge, type StatusTone } from '@/shared/components/status/StatusBadge';
import { resolveStatusSpec } from '@/shared/components/status/statusSpec';
import type { GradeStructureStatus } from '../types';

const map: Record<GradeStructureStatus, { tone: StatusTone; key: string }> = {
  DRAFT: { tone: 'draft', key: 'gradeStructure.status.draft' },
  APPROVED: { tone: 'approved', key: 'gradeStructure.status.approved' },
  LOCKED: { tone: 'locked', key: 'gradeStructure.status.locked' },
  ARCHIVED: { tone: 'archived', key: 'gradeStructure.status.archived' },
};

export function GradeStructureStatusBadge({
  status,
  className,
}: {
  status: GradeStructureStatus;
  className?: string;
}) {
  const { t } = useTranslation();
  const entry = map[status];
  const spec = entry
    ? { tone: entry.tone, label: t(entry.key) }
    : resolveStatusSpec({}, status);
  return <StatusBadge tone={spec.tone} label={spec.label} className={className} />;
}
