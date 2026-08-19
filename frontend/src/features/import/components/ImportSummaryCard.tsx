import { useTranslation } from 'react-i18next';
import { CheckCircle2, AlertTriangle, OctagonAlert, ListChecks } from 'lucide-react';
import { cn } from '@/shared/lib/cn';
import type { ImportBatch, ImportBatchStatus } from '../types';
import { ImportStatusBadge } from './ImportStatusBadge';

type StatusLineTone = 'success' | 'warning' | 'muted';

interface StatusLine {
  tone: StatusLineTone;
  key: string;
  params?: Record<string, unknown>;
}

const STATUS_LINE_CLASS: Record<StatusLineTone, string> = {
  success: 'text-success-700',
  warning: 'text-warning-700',
  muted: 'text-text-muted',
};

/**
 * Post-commit statuses previously fell through to `commit_blocked` ("Commit
 * blocked — fix errors first"), which is actively wrong AFTER a commit has
 * already happened — there is nothing left to "fix" and nothing to unblock.
 * Only genuinely pre-commit review states with blocker errors (VALIDATION_
 * FAILED) still show that copy; every other non-review status (still
 * in-flight in the pipeline, or a terminal non-commit outcome) shows no
 * status line here — `ImportProgressIndicator`/`ImportStatusBadge` already
 * communicate those.
 */
function summaryStatusLine(batch: ImportBatch): StatusLine | null {
  const total = batch.totalRowCount ?? 0;
  const committed = batch.committedRowCount ?? 0;
  switch (batch.status as ImportBatchStatus) {
    case 'COMMITTED':
      return {
        tone: 'success',
        key: 'import.summary.committed_all',
        params: { count: committed || total },
      };
    case 'PARTIALLY_COMMITTED':
      return {
        tone: 'warning',
        key: 'import.summary.committed_partial',
        params: { committed, failed: Math.max(0, total - committed) },
      };
    case 'READY_FOR_REVIEW':
    case 'READY_TO_COMMIT':
      return { tone: 'success', key: 'import.summary.commit_ready' };
    case 'VALIDATION_FAILED':
      return { tone: 'muted', key: 'import.summary.commit_blocked' };
    default:
      return null;
  }
}

interface Props {
  batch: ImportBatch;
}

export function ImportSummaryCard({ batch }: Props) {
  const { t } = useTranslation();
  const total = batch.totalRowCount ?? 0;
  const errors = batch.errorRowCount ?? 0;
  const warnings = batch.warningRowCount ?? 0;
  const valid = Math.max(0, total - errors);
  const statusLine = summaryStatusLine(batch);

  return (
    <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-3" data-testid="import-summary-card">
      <Tile
        icon={<ListChecks className="text-text-secondary" size={18} aria-hidden />}
        label={t('import.summary.total_rows')}
        value={total}
      />
      <Tile
        icon={<CheckCircle2 className="text-success-700" size={18} aria-hidden />}
        label={t('import.summary.valid_rows')}
        value={valid}
        tone="success"
      />
      <Tile
        icon={<AlertTriangle className="text-warning-700" size={18} aria-hidden />}
        label={t('import.summary.warning_rows')}
        value={warnings}
        tone="warning"
      />
      <Tile
        icon={<OctagonAlert className="text-danger-700" size={18} aria-hidden />}
        label={t('import.summary.error_rows')}
        value={errors}
        tone="danger"
      />
      <div className="sm:col-span-2 lg:col-span-4 flex items-center justify-between gap-3 p-3 rounded-md border border-border bg-surface">
        <div className="flex items-center gap-2">
          <ImportStatusBadge status={batch.status} />
          {statusLine ? (
            <span
              className={cn('text-xs', STATUS_LINE_CLASS[statusLine.tone])}
              data-testid="import-summary-status-line"
            >
              {t(statusLine.key, statusLine.params)}
            </span>
          ) : null}
        </div>
        {batch.containsSalaryData ? (
          <span className="text-xs px-2 py-0.5 rounded bg-salary-sensitive-bg text-salary-sensitive border border-salary-sensitive/30">
            {t('import.summary.contains_salary')}
          </span>
        ) : null}
      </div>
    </div>
  );
}

function Tile({
  icon,
  label,
  value,
  tone,
}: {
  icon: React.ReactNode;
  label: string;
  value: number;
  tone?: 'success' | 'warning' | 'danger';
}) {
  return (
    <div
      className={cn(
        'p-3 rounded-md border bg-surface flex flex-col gap-1',
        tone === 'success' && 'border-success-500/30',
        tone === 'warning' && 'border-warning-500/30',
        tone === 'danger' && 'border-danger-500/30',
        !tone && 'border-border',
      )}
    >
      <div className="flex items-center gap-2">
        {icon}
        <span className="text-xs text-text-muted uppercase tracking-wide">{label}</span>
      </div>
      <div className="text-2xl font-semibold text-text-primary">{value}</div>
    </div>
  );
}
