import { useTranslation } from 'react-i18next';
import { CheckCircle2, AlertTriangle, OctagonAlert, ListChecks } from 'lucide-react';
import { cn } from '@/shared/lib/cn';
import type { ImportBatch } from '../types';
import { ImportStatusBadge } from './ImportStatusBadge';

interface Props {
  batch: ImportBatch;
}

export function ImportSummaryCard({ batch }: Props) {
  const { t } = useTranslation();
  const total = batch.totalRowCount ?? 0;
  const errors = batch.errorRowCount ?? 0;
  const warnings = batch.warningRowCount ?? 0;
  const valid = Math.max(0, total - errors);
  const canCommit =
    batch.status === 'READY_FOR_REVIEW' || batch.status === 'READY_TO_COMMIT';

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
          {canCommit ? (
            <span className="text-xs text-success-700">{t('import.summary.commit_ready')}</span>
          ) : (
            <span className="text-xs text-text-muted">{t('import.summary.commit_blocked')}</span>
          )}
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
