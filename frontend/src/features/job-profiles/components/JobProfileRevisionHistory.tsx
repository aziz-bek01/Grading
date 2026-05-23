import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ChevronDown, ChevronRight, History } from 'lucide-react';
import { Card } from '@/shared/components/ui/Card';
import { JobProfileStatusBadge } from './JobProfileStatusBadge';
import type { JobProfileRevisionSummary } from '../types';

interface JobProfileRevisionHistoryProps {
  revisions: JobProfileRevisionSummary[] | undefined;
  loading?: boolean;
}

export function JobProfileRevisionHistory({ revisions, loading }: JobProfileRevisionHistoryProps) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const items = revisions ?? [];

  return (
    <Card compact>
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="w-full flex items-center justify-between gap-2"
        aria-expanded={open}
        data-testid="revision-history-toggle"
      >
        <span className="inline-flex items-center gap-2 text-sm font-medium text-text-primary">
          <History size={16} aria-hidden />
          {t('jobProfile.revisions_title')} ({items.length})
        </span>
        <span className="text-text-secondary" aria-hidden>
          {open ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
        </span>
      </button>
      {open ? (
        <ol className="mt-3 space-y-2" data-testid="revision-list">
          {loading ? (
            <li className="text-xs text-text-muted">{t('app.loading')}</li>
          ) : items.length === 0 ? (
            <li className="text-xs text-text-muted">{t('jobProfile.no_revisions')}</li>
          ) : (
            items.map((rev) => (
              <li
                key={rev.id}
                className="flex items-start justify-between gap-2 text-xs border-t border-divider pt-2"
              >
                <div>
                  <div className="text-text-primary font-medium">
                    {t('jobProfile.revision_number', { number: rev.revision_number })}
                  </div>
                  <div className="text-text-muted">
                    {rev.approved_at
                      ? t('jobProfile.approved_on', { date: rev.approved_at.slice(0, 10) })
                      : t('jobProfile.updated_on', { date: rev.updated_at.slice(0, 10) })}
                    {rev.approved_by ? ` · ${rev.approved_by}` : ''}
                  </div>
                </div>
                <JobProfileStatusBadge status={rev.status} />
              </li>
            ))
          )}
        </ol>
      ) : null}
    </Card>
  );
}
