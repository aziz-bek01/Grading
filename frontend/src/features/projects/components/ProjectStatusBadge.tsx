import { useTranslation } from 'react-i18next';
import { StatusBadge, type StatusTone } from '@/shared/components/status/StatusBadge';
import type { ProjectStatus } from '../types/projectTypes';

const map: Record<ProjectStatus, { tone: StatusTone; key: string }> = {
  DRAFT: { tone: 'draft', key: 'status.draft' },
  ACTIVE: { tone: 'in-review', key: 'status.active' },
  IN_REVIEW: { tone: 'in-review', key: 'status.in_review' },
  APPROVED: { tone: 'approved', key: 'status.approved' },
  LOCKED: { tone: 'locked', key: 'status.locked' },
  ARCHIVED: { tone: 'archived', key: 'status.archived' },
};

export function ProjectStatusBadge({ status }: { status: ProjectStatus }) {
  const { t } = useTranslation();
  const spec = map[status];
  return <StatusBadge tone={spec.tone} label={t(spec.key)} />;
}
