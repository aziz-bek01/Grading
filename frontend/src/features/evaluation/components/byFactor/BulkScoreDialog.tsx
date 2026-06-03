import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { AlertTriangle } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { cn } from '@/shared/lib/cn';
import { pickLocalized } from '@/shared/lib/localized';
import type { Factor } from '@/features/methodology/types';
import type { BulkOperationResult } from '../../types';

interface BulkScoreDialogProps {
  open: boolean;
  factor: Factor;
  /** How many evaluation rows the user picked. */
  selectedCount: number;
  /**
   * Submit handler. Returns the backend result so the dialog can show
   * partial failures inline before closing.
   */
  onConfirm: (
    factorLevelId: string,
    reason: string,
  ) => Promise<BulkOperationResult>;
  onClose: () => void;
}

const REASON_MIN_LEN = 50;

/**
 * Bulk set the same factor level for N evaluations. Audit-mandatory
 * reason (≥50 chars, matches UX brief and PRD audit policy).
 *
 * The dialog shows a partial-fail summary inline if the backend returns
 * any failures, so the evaluator can decide whether to close or retry.
 */
export function BulkScoreDialog({
  open,
  factor,
  selectedCount,
  onConfirm,
  onClose,
}: BulkScoreDialogProps) {
  const { t, i18n } = useTranslation();
  const [levelId, setLevelId] = useState('');
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<BulkOperationResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) {
      // Reset on close — no stale level/reason between sessions.
      setLevelId('');
      setReason('');
      setResult(null);
      setError(null);
      setSubmitting(false);
    }
  }, [open]);

  const sortedLevels = useMemo(
    () => [...factor.levels].sort((a, b) => a.level_order - b.level_order),
    [factor.levels],
  );

  if (!open) return null;

  const reasonValid = reason.trim().length >= REASON_MIN_LEN;
  const canSubmit = Boolean(levelId) && reasonValid && !submitting;

  const handleSubmit = async () => {
    setError(null);
    setSubmitting(true);
    try {
      const r = await onConfirm(levelId, reason.trim());
      setResult(r);
      if (r.failed.length === 0) {
        // Clean close after a tiny delay so the user can see the success counter.
        setTimeout(() => onClose(), 400);
      }
    } catch (e) {
      setError((e as Error).message ?? 'Bulk score failed');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="bulk-score-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onClick={(e) => {
        if (e.target === e.currentTarget && !submitting) onClose();
      }}
    >
      <div className="bg-surface rounded-xl shadow-lg border border-border w-full max-w-lg p-6 space-y-4">
        <header>
          <h2 id="bulk-score-title" className="text-lg text-text-primary">
            {t('evaluation.byFactor.bulk.set_all.title', {
              count: selectedCount,
            })}
          </h2>
          <p className="text-sm text-text-secondary mt-1">
            {t('evaluation.byFactor.bulk.set_all.body', {
              factor: pickLocalized(factor.name_i18n, i18n.language) || factor.code,
            })}
          </p>
        </header>

        <div>
          <label
            htmlFor="bulk-score-level"
            className="block text-sm font-medium mb-1"
          >
            {t('evaluation.byFactor.bulk.set_all.level_label')}
          </label>
          <select
            id="bulk-score-level"
            value={levelId}
            onChange={(e) => setLevelId(e.target.value)}
            data-testid="bulk-score-level"
            className="w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface"
            disabled={submitting}
          >
            <option value="">—</option>
            {sortedLevels.map((lvl, idx) => (
              <option key={lvl.id} value={lvl.id}>
                {idx + 1} ·{' '}
                {pickLocalized(lvl.label_i18n, i18n.language) || lvl.code} ·{' '}
                {lvl.points} pts
              </option>
            ))}
          </select>
        </div>

        <div>
          <label
            htmlFor="bulk-score-reason"
            className="block text-sm font-medium mb-1"
          >
            {t('evaluation.byFactor.bulk.set_all.reason_label')}{' '}
            <span className="text-text-muted text-xs">
              ({t('common.reason_min_chars', { count: REASON_MIN_LEN })})
            </span>
          </label>
          <textarea
            id="bulk-score-reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            data-testid="bulk-score-reason"
            rows={4}
            minLength={REASON_MIN_LEN}
            maxLength={1000}
            disabled={submitting}
            className={cn(
              'w-full p-2 border rounded-md text-sm bg-surface',
              reason.length > 0 && !reasonValid
                ? 'border-warning-500/50'
                : 'border-border-strong',
            )}
            placeholder={t('evaluation.byFactor.bulk.set_all.reason_placeholder')}
          />
          <p className="text-xs text-text-muted mt-1 tabular-nums">
            {reason.trim().length} / {REASON_MIN_LEN}
          </p>
        </div>

        {result ? (
          <div
            role="status"
            className={cn(
              'rounded-md border p-3 text-sm',
              result.failed.length === 0
                ? 'bg-success-50 border-success-500/30 text-success-700'
                : 'bg-warning-50 border-warning-500/30 text-warning-700',
            )}
            data-testid="bulk-score-result"
          >
            <div className="flex items-center gap-2">
              {result.failed.length > 0 ? (
                <AlertTriangle size={14} aria-hidden />
              ) : null}
              <span>
                {t('evaluation.byFactor.bulk.result_summary', {
                  updated: result.updated,
                  failed: result.failed.length,
                })}
              </span>
            </div>
            {result.failed.length > 0 ? (
              <ul className="mt-2 text-xs list-disc pl-5 space-y-0.5 max-h-32 overflow-y-auto">
                {result.failed.slice(0, 10).map((f) => (
                  <li key={f.evaluation_id}>
                    <span className="font-mono">
                      {f.evaluation_id.slice(0, 8)}
                    </span>{' '}
                    — {f.reason}
                  </li>
                ))}
              </ul>
            ) : null}
          </div>
        ) : null}

        {error ? (
          <div
            role="alert"
            className="rounded-md border border-danger-500/30 bg-danger-50 text-danger-700 text-sm p-3"
            data-testid="bulk-score-error"
          >
            {error}
          </div>
        ) : null}

        <div className="flex justify-end gap-2 pt-2">
          <Button
            variant="secondary"
            onClick={onClose}
            disabled={submitting}
            data-testid="bulk-score-cancel"
          >
            {t('common.cancel')}
          </Button>
          <Button
            onClick={handleSubmit}
            disabled={!canSubmit}
            data-testid="bulk-score-confirm"
          >
            {t('evaluation.byFactor.bulk.set_all.confirm')}
          </Button>
        </div>
      </div>
    </div>
  );
}
