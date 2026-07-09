import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { AlertTriangle, X } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { Modal } from '@/shared/components/ui/Modal';
import { EvaluatorPicker } from './EvaluatorPicker';

/** Reason floor — mirrors the BE @Size(min = 5) on ReopenForExpertRequest. */
const REASON_MIN_LENGTH = 5;

interface Props {
  open: boolean;
  /** Whether the reopen request is in flight (disables the form + confirm). */
  busy: boolean;
  /** A failed submit — mapped to a localized message by the caller. */
  error?: string | null;
  /**
   * Evaluator user ids ALREADY on the panel roster — hidden from the picker so
   * the manager cannot re-add an existing evaluator (one person is one seat).
   */
  excludeUserIds?: readonly string[];
  onConfirm: (additionalEvaluatorUserId: string, reason: string) => void;
  onCancel: () => void;
}

/**
 * Feature 2 — "Reopen for additional expert" dialog.
 *
 * Shown on an APPROVED panel (manager holds EVALUATION_PANEL_MANAGE). Lets the
 * manager pick the additional evaluator (REUSES the SAME {@link EvaluatorPicker}
 * the COLLECTING-phase add-evaluator flow uses — tenant-derived user list) and
 * enter a required reason (≥ 5 chars). It CLEARLY communicates the consequence:
 * the approved result is reopened and must be re-approved after the expert
 * scores. The confirm stays disabled until both a user and a valid reason are
 * present (UI mirror of the BE @NotNull + @Size(min=5) — the BE re-validates).
 */
export function ReopenForExpertDialog({
  open,
  busy,
  error,
  excludeUserIds,
  onConfirm,
  onCancel,
}: Props) {
  const { t } = useTranslation();
  const [userId, setUserId] = useState('');
  const [reason, setReason] = useState('');
  const [touched, setTouched] = useState(false);

  // The form is reset by REMOUNTING from the host (a changing `key` on open) so
  // a previous selection / reason never leaks into the next reopen — no effect.
  if (!open) return null;

  const reasonTrimmed = reason.trim();
  const reasonValid = reasonTrimmed.length >= REASON_MIN_LENGTH;
  const userValid = userId.length > 0;
  const formValid = userValid && reasonValid;

  const submit = () => {
    // Reveal the inline validation messages (the confirm button stays clickable
    // so an invalid attempt SHOWS what is missing rather than silently no-op'ing
    // on a disabled control). The actual mutation only fires when valid.
    setTouched(true);
    if (!formValid || busy) return;
    onConfirm(userId, reasonTrimmed);
  };

  return (
    <Modal
      open
      onClose={onCancel}
      labelledBy="reopen-for-expert-title"
      size="md"
      dismissible={!busy}
      data-testid="reopen-for-expert-dialog"
      className="bg-surface rounded-xl shadow-lg border border-border flex flex-col p-6 space-y-4"
    >
      <>
        <header className="flex items-start justify-between gap-3">
          <h2 id="reopen-for-expert-title" className="text-lg text-text-primary">
            {t('panel.reopen_expert.title')}
          </h2>
          <button
            type="button"
            onClick={onCancel}
            disabled={busy}
            aria-label={t('common.close')}
            className="text-text-muted hover:text-text-primary disabled:opacity-50"
          >
            <X size={18} aria-hidden />
          </button>
        </header>

        {/* Consequence warning — the approved result is reopened and must be
            re-approved after the expert scores. */}
        <div
          role="note"
          data-testid="reopen-for-expert-consequence"
          className="flex items-start gap-2 rounded-md border border-warning-500/30 bg-warning-50 text-warning-700 text-sm p-3"
        >
          <AlertTriangle size={16} aria-hidden className="mt-0.5 shrink-0" />
          <span>{t('panel.reopen_expert.consequence')}</span>
        </div>

        <div className="space-y-1.5">
          <label
            htmlFor="reopen-expert-user"
            className="block text-sm font-medium text-text-primary"
          >
            {t('panel.reopen_expert.evaluator_label')}
          </label>
          <EvaluatorPicker
            selectId="reopen-expert-user"
            value={userId}
            onChange={(id) => setUserId(id)}
            excludeUserIds={excludeUserIds}
            disabled={busy}
          />
          {touched && !userValid ? (
            <p
              className="text-xs text-danger-600"
              data-testid="reopen-expert-user-error"
            >
              {t('panel.reopen_expert.evaluator_required')}
            </p>
          ) : null}
        </div>

        <div className="space-y-1.5">
          <label
            htmlFor="reopen-expert-reason"
            className="block text-sm font-medium text-text-primary"
          >
            {t('common.reason_label')}
          </label>
          <textarea
            id="reopen-expert-reason"
            data-testid="reopen-expert-reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            disabled={busy}
            rows={3}
            placeholder={t('panel.reopen_expert.reason_placeholder')}
            className="w-full px-3 py-2 border border-border-strong rounded-md text-sm bg-surface disabled:opacity-50"
          />
          {touched && !reasonValid ? (
            <p
              className="text-xs text-danger-600"
              data-testid="reopen-expert-reason-error"
            >
              {t('panel.reopen_expert.reason_invalid', { min: REASON_MIN_LENGTH })}
            </p>
          ) : null}
        </div>

        {error ? (
          <div
            role="alert"
            data-testid="reopen-for-expert-error"
            className="rounded-md border border-danger-500/30 bg-danger-50 text-danger-700 text-sm p-3"
          >
            {error}
          </div>
        ) : null}

        <footer className="flex items-center justify-end gap-2 pt-1">
          <Button variant="secondary" onClick={onCancel} disabled={busy}>
            {t('common.cancel')}
          </Button>
          <Button
            onClick={submit}
            disabled={busy}
            aria-disabled={!formValid || busy}
            data-testid="reopen-for-expert-confirm"
          >
            {t('panel.reopen_expert.confirm')}
          </Button>
        </footer>
      </>
    </Modal>
  );
}
