import { useTranslation } from 'react-i18next';
import { StatusBadge, type StatusTone } from '@/shared/components/status/StatusBadge';
import { resolveStatusSpec } from '@/shared/components/status/statusSpec';
import type { PositionStatus } from '../types/positionTypes';

const map: Record<PositionStatus, { tone: StatusTone; key: string }> = {
  DRAFT: { tone: 'draft', key: 'status.draft' },
  ACTIVE: { tone: 'in-review', key: 'status.active' },
  ARCHIVED: { tone: 'archived', key: 'status.archived' },
};

export function PositionStatusBadge({ status }: { status: PositionStatus }) {
  const { t } = useTranslation();
  const entry = map[status];
  const spec = entry
    ? { tone: entry.tone, label: t(entry.key) }
    : resolveStatusSpec({}, status);
  return <StatusBadge tone={spec.tone} label={spec.label} />;
}
