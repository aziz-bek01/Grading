import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/shared/components/ui/Button';
import { cn } from '@/shared/lib/cn';

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  body: string;
  confirmLabel?: string;
  cancelLabel?: string;
  destructive?: boolean;
  /**
   * When true, a required reason textarea is rendered and Confirm stays
   * disabled until the reason reaches {@link reasonMinLength} characters. The
   * trimmed reason is passed to {@link onConfirm}. Used by audit-sensitive
   * destructive actions (e.g. delete a DRAFT evaluation — Item 1, FE-3).
   */
  requireReason?: boolean;
  /** Minimum reason length when {@link requireReason} is set. Default 5. */
  reasonMinLength?: number;
  reasonLabel?: string;
  reasonPlaceholder?: string;
  /**
   * Confirm callback. Receives the trimmed reason when {@link requireReason}
   * is set, otherwise `undefined` (the legacy no-arg contract still works —
   * existing callers ignore the argument).
   */
  onConfirm: (reason?: string) => void;
  onCancel: () => void;
  /**
   * When set, an error banner is rendered above the buttons and the dialog
   * stays open — so a failed confirm (e.g. the server rejected a delete) is
   * VISIBLE instead of silently doing nothing. The caller owns the message
   * (already localized).
   */
  error?: string | null;
  /**
   * Disables both buttons while the confirm action is in flight (the parent
   * keeps the dialog open until its mutation settles).
   */
  busy?: boolean;
}

export function ConfirmDialog({
  open,
  title,
  body,
  confirmLabel,
  cancelLabel,
  destructive,
  requireReason = false,
  reasonMinLength = 5,
  reasonLabel,
  reasonPlaceholder,
  onConfirm,
  onCancel,
  error,
  busy = false,
}: ConfirmDialogProps) {
  const { t } = useTranslation();
  const confirmRef = useRef<HTMLButtonElement>(null);
  const [reason, setReason] = useState('');

  // Reset the reason each time the dialog transitions closed → open so a
  // previous attempt's text never leaks into a new confirmation. Done during
  // render via a previous-value tracked in STATE (React's "adjust state when a
  // prop changes" pattern) rather than a setState-in-effect or a ref-in-render.
  const [seenOpen, setSeenOpen] = useState(open);
  if (seenOpen !== open) {
    setSeenOpen(open);
    if (open && reason !== '') setReason('');
  }

  useEffect(() => {
    if (!open) return;
    // Don't steal focus into the confirm button when a reason field is the
    // primary input the user must fill first.
    if (!requireReason) confirmRef.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCancel();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onCancel, requireReason]);

  if (!open) return null;

  const reasonValid = !requireReason || reason.trim().length >= reasonMinLength;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="confirm-dialog-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onClick={(e) => {
        if (e.target === e.currentTarget) onCancel();
      }}
    >
      <div
        className={cn(
          'bg-surface rounded-xl shadow-lg border border-border w-full max-w-md p-6',
        )}
      >
        <h2 id="confirm-dialog-title" className="text-lg text-text-primary">
          {title}
        </h2>
        <p className="text-sm text-text-secondary mt-2">{body}</p>

        {requireReason ? (
          <div className="mt-4">
            <label
              htmlFor="confirm-dialog-reason"
              className="block text-sm font-medium mb-1"
            >
              {reasonLabel ?? t('common.reason_label')}{' '}
              <span className="text-text-muted text-xs">
                ({t('common.reason_min_chars', { count: reasonMinLength })})
              </span>
            </label>
            <textarea
              id="confirm-dialog-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={3}
              minLength={reasonMinLength}
              maxLength={4000}
              data-testid="confirm-dialog-reason"
              placeholder={reasonPlaceholder ?? ''}
              className={cn(
                'w-full p-2 border rounded-md text-sm bg-surface',
                reason.length > 0 && !reasonValid
                  ? 'border-warning-500/50'
                  : 'border-border-strong',
              )}
            />
          </div>
        ) : null}

        {error ? (
          <div
            role="alert"
            data-testid="confirm-dialog-error"
            className="mt-4 rounded-md border border-danger-500/30 bg-danger-50 text-danger-700 text-sm p-3"
          >
            {error}
          </div>
        ) : null}

        <div className="flex justify-end gap-2 mt-6">
          <Button variant="secondary" onClick={onCancel} disabled={busy}>
            {cancelLabel ?? t('common.cancel')}
          </Button>
          <Button
            ref={confirmRef}
            variant={destructive ? 'danger' : 'primary'}
            disabled={!reasonValid || busy}
            onClick={() => onConfirm(requireReason ? reason.trim() : undefined)}
          >
            {confirmLabel ?? t('common.confirm')}
          </Button>
        </div>
      </div>
    </div>
  );
}
