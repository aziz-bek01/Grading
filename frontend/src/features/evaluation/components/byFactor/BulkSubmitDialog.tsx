import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { AlertTriangle } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { Modal } from '@/shared/components/ui/Modal';
import { cn } from '@/shared/lib/cn';
import type { BulkOperationResult } from '../../types';

interface BulkSubmitDialogProps {
  open: boolean;
  selectedCount: number;
  onConfirm: (reason: string) => Promise<BulkOperationResult>;
  onClose: () => void;
}

const REASON_MIN_LEN = 20;

/**
 * Bulk transition evaluations to SUBMITTED for the current factor.
 *
 * Backend may return partial failures (e.g. an evaluation is already
 * LOCKED, or is still INCOMPLETE for required factors); we render
 * those inline so the evaluator can correct and retry without losing
 * context.
 */
export function BulkSubmitDialog({ open, ...rest }: BulkSubmitDialogProps) {
  // Mount fresh while open so each session starts clean (no reset-on-close).
  if (!open) return null;
  return <BulkSubmitDialogBody {...rest} />;
}

function BulkSubmitDialogBody({
  selectedCount,
  onConfirm,
  onClose,
}: Omit<BulkSubmitDialogProps, 'open'>) {
  const { t } = useTranslation();
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<BulkOperationResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const reasonValid = reason.trim().length >= REASON_MIN_LEN;
  const canSubmit = reasonValid && !submitting;

  const handleSubmit = async () => {
    setError(null);
    setSubmitting(true);
    try {
      const r = await onConfirm(reason.trim());
      setResult(r);
      if (r.failed.length === 0) setTimeout(() => onClose(), 400);
    } catch (e) {
      setError((e as Error).message ?? 'Bulk submit failed');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      labelledBy="bulk-submit-title"
      size="lg"
      dismissible={!submitting}
      className="bg-surface rounded-xl shadow-lg border border-border p-6 space-y-4"
    >
      <>
        <header>
          <h2 id="bulk-submit-title" className="text-lg text-text-primary">
            {t('evaluation.byFactor.bulk.submit.title', {
              count: selectedCount,
            })}
          </h2>
          <p className="text-sm text-text-secondary mt-1">
            {t('evaluation.byFactor.bulk.submit.body')}
          </p>
        </header>

        <div>
          <label
            htmlFor="bulk-submit-reason"
            className="block text-sm font-medium mb-1"
          >
            {t('evaluation.byFactor.bulk.submit.reason_label')}{' '}
            <span className="text-text-muted text-xs">
              ({t('common.reason_min_chars', { count: REASON_MIN_LEN })})
            </span>
          </label>
          <textarea
            id="bulk-submit-reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            data-testid="bulk-submit-reason"
            rows={3}
            minLength={REASON_MIN_LEN}
            maxLength={1000}
            disabled={submitting}
            className={cn(
              'w-full p-2 border rounded-md text-sm bg-surface',
              reason.length > 0 && !reasonValid
                ? 'border-warning-500/50'
                : 'border-border-strong',
            )}
            placeholder={t(
              'evaluation.byFactor.bulk.submit.reason_placeholder',
            )}
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
            data-testid="bulk-submit-result"
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
            data-testid="bulk-submit-error"
          >
            {error}
          </div>
        ) : null}

        <div className="flex justify-end gap-2 pt-2">
          <Button
            variant="secondary"
            onClick={onClose}
            disabled={submitting}
            data-testid="bulk-submit-cancel"
          >
            {t('common.cancel')}
          </Button>
          <Button
            onClick={handleSubmit}
            disabled={!canSubmit}
            data-testid="bulk-submit-confirm"
          >
            {t('evaluation.byFactor.bulk.submit.confirm')}
          </Button>
        </div>
      </>
    </Modal>
  );
}
