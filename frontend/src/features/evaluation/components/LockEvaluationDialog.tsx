import { useEffect, useId, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Lock, AlertTriangle, Loader2 } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { cn } from '@/shared/lib/cn';

/**
 * Summary of what is about to become immutable. Every field is OPTIONAL and is
 * sourced from data ALREADY on the evaluation page — this dialog never triggers
 * a new backend call (the lock summary must not cost an extra round-trip).
 */
export interface LockSummary {
  /** Localized position name (falls back to code upstream). */
  positionName?: string | null;
  /** Human-readable position code. */
  positionCode?: string | null;
  /** Factors with a saved score. */
  scoredFactors?: number | null;
  /** Total factors in the methodology version. */
  totalFactors?: number | null;
  /** Displayed (rounded) total score, already permission-safe at the source. */
  displayedTotal?: number | null;
}

interface LockEvaluationDialogProps {
  open: boolean;
  summary?: LockSummary;
  /**
   * True while the lock mutation is in flight. The dialog stays open, both
   * buttons disable, and the confirm button shows a spinner.
   */
  busy?: boolean;
  /**
   * Localized error message from a failed lock. When set, the dialog stays
   * open with the message visible so the evaluator can retry — the sheet
   * remains editable (the parent never transitions to LOCKED on error).
   */
  error?: string | null;
  /** Fired ONLY after the user explicitly confirms (checkbox + confirm). */
  onConfirm: () => void;
  onCancel: () => void;
}

/**
 * Pre-lock REVIEW + CONFIRM step for an evaluator's sheet.
 *
 * Locking an evaluation is PERMANENT — the sheet becomes immutable and cannot
 * be edited afterward. This irreversibility was a flagged UX gap: the previous
 * generic confirm fired the mutation on a single click with no review, no
 * explicit "I understand" gate, and closed before the mutation settled (so a
 * server error was invisible).
 *
 * This dialog closes that gap:
 *   (a) summarises what is about to be locked (position / factors / total) from
 *       data already on the page — NO extra backend call;
 *   (b) warns clearly that locking is irreversible;
 *   (c) requires an explicit affirmative confirmation — a "I understand this is
 *       final" checkbox that gates the confirm button (the app's established
 *       affirmative-gate pattern, mirroring the reason-gated confirm dialogs);
 *   (d) shows a spinner while the mutation runs and surfaces success/error
 *       (the parent owns the mutation; on error the dialog stays open + editable).
 *
 * Accessibility: role="dialog" + aria-modal, labelled/described by ids, ESC
 * cancels (ignored while busy), focus moves to the irreversibility warning on
 * open, and focus is trapped within the dialog (Tab/Shift+Tab wrap).
 */
export function LockEvaluationDialog({ open, ...rest }: LockEvaluationDialogProps) {
  // Mount fresh while open so the "I understand" checkbox always starts
  // unchecked — a previous attempt's affirmative must never carry over.
  if (!open) return null;
  return <LockEvaluationDialogBody {...rest} />;
}

function LockEvaluationDialogBody({
  summary,
  busy = false,
  error,
  onConfirm,
  onCancel,
}: Omit<LockEvaluationDialogProps, 'open'>) {
  const { t } = useTranslation();
  const titleId = useId();
  const warningId = useId();
  const [understood, setUnderstood] = useState(false);

  const panelRef = useRef<HTMLDivElement>(null);
  const warningRef = useRef<HTMLParagraphElement>(null);

  // Move focus to the irreversibility warning on open so a keyboard / screen
  // reader user reads the consequence BEFORE reaching the confirm control.
  useEffect(() => {
    const id = requestAnimationFrame(() => warningRef.current?.focus());
    return () => cancelAnimationFrame(id);
  }, []);

  // ESC cancels (ignored while a lock is in flight — don't abandon a pending
  // mutation mid-request).
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !busy) onCancel();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onCancel, busy]);

  // Focus trap: keep Tab / Shift+Tab cycling within the dialog.
  const onKeyDownTrap = (e: React.KeyboardEvent) => {
    if (e.key !== 'Tab') return;
    const root = panelRef.current;
    if (!root) return;
    const focusables = root.querySelectorAll<HTMLElement>(
      'button, [href], input, textarea, select, [tabindex]:not([tabindex="-1"])',
    );
    const enabled = Array.from(focusables).filter((el) => !el.hasAttribute('disabled'));
    if (enabled.length === 0) return;
    const first = enabled[0];
    const last = enabled[enabled.length - 1];
    const active = document.activeElement as HTMLElement | null;
    if (e.shiftKey && (active === first || active === warningRef.current)) {
      e.preventDefault();
      last.focus();
    } else if (!e.shiftKey && active === last) {
      e.preventDefault();
      first.focus();
    }
  };

  const hasSummary =
    summary != null &&
    (summary.positionName ||
      summary.positionCode ||
      summary.scoredFactors != null ||
      summary.totalFactors != null ||
      summary.displayedTotal != null);

  const positionLabel =
    summary?.positionName || summary?.positionCode || null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby={titleId}
      aria-describedby={warningId}
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onClick={(e) => {
        // Backdrop click cancels — but never while busy (don't drop a pending lock).
        if (e.target === e.currentTarget && !busy) onCancel();
      }}
      onKeyDown={onKeyDownTrap}
    >
      <div
        ref={panelRef}
        className="bg-surface rounded-xl shadow-lg border border-border w-full max-w-md p-6 space-y-4"
      >
        <header className="flex items-start gap-3">
          <span className="rounded-full bg-locked-bg p-2 text-locked" aria-hidden>
            <Lock size={18} />
          </span>
          <h2 id={titleId} className="text-lg text-text-primary pt-1">
            {t('evaluation.lock.dialog_title')}
          </h2>
        </header>

        {/* (a) Review summary — built from data already on the page. */}
        {hasSummary ? (
          <section
            data-testid="lock-summary"
            className="rounded-lg border border-border bg-divider/40 p-3 text-sm"
          >
            <p className="text-text-secondary mb-2">
              {t('evaluation.lock.summary_intro')}
            </p>
            <dl className="space-y-1">
              {positionLabel ? (
                <div className="flex justify-between gap-3">
                  <dt className="text-text-muted">
                    {t('evaluation.lock.summary_position')}
                  </dt>
                  <dd className="text-text-primary text-right">{positionLabel}</dd>
                </div>
              ) : null}
              {summary?.totalFactors != null ? (
                <div className="flex justify-between gap-3">
                  <dt className="text-text-muted">
                    {t('evaluation.lock.summary_factors')}
                  </dt>
                  <dd className="text-text-primary text-right tabular-nums">
                    {t('evaluation.lock.summary_factors_value', {
                      scored: summary.scoredFactors ?? 0,
                      total: summary.totalFactors,
                    })}
                  </dd>
                </div>
              ) : null}
              {summary?.displayedTotal != null ? (
                <div className="flex justify-between gap-3">
                  <dt className="text-text-muted">
                    {t('evaluation.lock.summary_total')}
                  </dt>
                  <dd className="text-text-primary text-right tabular-nums">
                    {Number(summary.displayedTotal).toFixed(2)}
                  </dd>
                </div>
              ) : null}
            </dl>
          </section>
        ) : null}

        {/* (b) Irreversibility warning — focus lands here on open. */}
        <p
          ref={warningRef}
          id={warningId}
          tabIndex={-1}
          data-testid="lock-irreversible-warning"
          className="flex items-start gap-2 rounded-md border border-warning-500/30 bg-warning-50 text-warning-700 text-sm p-3 outline-none focus-visible:ring-2 focus-visible:ring-warning-500/40"
        >
          <AlertTriangle size={16} className="mt-0.5 shrink-0" aria-hidden />
          <span>{t('evaluation.lock.irreversible_warning')}</span>
        </p>

        {/* (c) Explicit affirmative gate. */}
        <label className="flex items-start gap-2 text-sm text-text-primary cursor-pointer select-none">
          <input
            type="checkbox"
            checked={understood}
            disabled={busy}
            onChange={(e) => setUnderstood(e.target.checked)}
            data-testid="lock-understand-checkbox"
            className="mt-0.5 h-4 w-4 rounded border-border-strong text-primary-600 focus:ring-primary-500"
          />
          <span>{t('evaluation.lock.understand_label')}</span>
        </label>

        {/* (d) Error surfaced inline; sheet stays editable upstream. */}
        {error ? (
          <div
            role="alert"
            data-testid="lock-error"
            className="rounded-md border border-danger-500/30 bg-danger-50 text-danger-700 text-sm p-3"
          >
            {error}
          </div>
        ) : null}

        <div className="flex justify-end gap-2 pt-1">
          <Button
            variant="secondary"
            onClick={onCancel}
            disabled={busy}
            data-testid="lock-cancel"
          >
            {t('common.cancel')}
          </Button>
          <Button
            variant="primary"
            onClick={onConfirm}
            disabled={!understood || busy}
            leadingIcon={
              busy ? (
                <Loader2 size={14} className={cn('animate-spin')} aria-hidden />
              ) : (
                <Lock size={14} aria-hidden />
              )
            }
            data-testid="lock-confirm"
          >
            {busy
              ? t('evaluation.lock.confirming')
              : t('evaluation.lock.confirm')}
          </Button>
        </div>
      </div>
    </div>
  );
}
