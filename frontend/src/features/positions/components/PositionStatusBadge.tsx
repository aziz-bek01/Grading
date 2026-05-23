import { useTranslation } from 'react-i18next';
import { StatusBadge, type StatusTone } from '@/shared/components/status/StatusBadge';
import type { PositionStatus } from '../types/positionTypes';

const map: Record<PositionStatus, { tone: StatusTone; key: string }> = {
  DRAFT: { tone: 'draft', key: 'status.draft' },
  ACTIVE: { tone: 'in-review', key: 'status.active' },
  ARCHIVED: { tone: 'archived', key: 'status.archived' },
};

export function PositionStatusBadge({ status }: { status: PositionStatus }) {
  const { t } = useTranslation();
  const spec = map[status];
  return <StatusBadge tone={spec.tone} label={t(spec.key)} />;
}
