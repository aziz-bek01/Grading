import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/shared/components/ui/Button';

interface ReasonRequiredDialogProps {
  open: boolean;
  title: string;
  body?: string;
  minLength?: number;
  onConfirm: (reason: string) => void;
  onCancel: () => void;
}

/**
 * Modal that requires a free-text reason (default >=20 chars).
 * Used for manual calibration, score override, archiving, etc. (design foundation §14 #25).
 */
export function ReasonRequiredDialog({
  open,
  title,
  body,
  minLength = 20,
  onConfirm,
  onCancel,
}: ReasonRequiredDialogProps) {
  const { t } = useTranslation();
  const [reason, setReason] = useState('');
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (open) {
      setReason('');
      requestAnimationFrame(() => textareaRef.current?.focus());
    }
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCancel();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onCancel]);

  if (!open) return null;

  const valid = reason.trim().length >= minLength;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="reason-dialog-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
    >
      <div className="bg-surface rounded-xl shadow-lg border border-border w-full max-w-md p-6">
        <h2 id="reason-dialog-title" className="text-lg text-text-primary">
          {title}
        </h2>
        {body ? <p className="text-sm text-text-secondary mt-2">{body}</p> : null}
        <label className="block mt-4 text-sm font-medium text-text-primary">
          {t('common.reason_label')}
        </label>
        <textarea
          ref={textareaRef}
          className="mt-1 w-full min-h-[96px] rounded-md border border-border-strong p-3 text-sm focus:border-primary-500 focus:outline-none"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          aria-describedby="reason-help"
        />
        <p id="reason-help" className="text-xs text-text-muted mt-1">
          {t('common.reason_required')}
        </p>
        <div className="flex justify-end gap-2 mt-6">
          <Button variant="secondary" onClick={onCancel}>
            {t('common.cancel')}
          </Button>
          <Button onClick={() => onConfirm(reason.trim())} disabled={!valid}>
            {t('common.confirm')}
          </Button>
        </div>
      </div>
    </div>
  );
}
