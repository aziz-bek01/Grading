import { useTranslation } from 'react-i18next';
import { StatusBadge, type StatusTone } from '@/shared/components/status/StatusBadge';
import { resolveStatusSpec } from '@/shared/components/status/statusSpec';
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
  const entry = map[status];
  // Unknown/unexpected status (new BE enum, stale cache) → neutral badge with
  // the raw code instead of `map[status].tone` throwing.
  const spec = entry
    ? { tone: entry.tone, label: t(entry.key) }
    : resolveStatusSpec({}, status);
  return <StatusBadge tone={spec.tone} label={spec.label} />;
}
