/**
 * Two-stage confirmation for archiving a tenant.
 *
 * Pattern: ConfirmDialog (destructive) + reason textarea ≥ 20 chars. Reason
 * is forwarded to the backend audit log. Frontend can't perform the action
 * without a reason — backend rejects with REASON_REQUIRED otherwise.
 */
import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/shared/components/ui/Button';
import { Modal } from '@/shared/components/ui/Modal';

interface ArchiveTenantConfirmProps {
  open: boolean;
  tenantName: string;
  onCancel: () => void;
  onConfirm: (reason: string) => Promise<void> | void;
}

const MIN_REASON = 20;

export function ArchiveTenantConfirm({ open, ...rest }: ArchiveTenantConfirmProps) {
  // Mount the dialog body only while open so its state starts fresh each time
  // (no reset-on-open effect needed -> no setState-in-effect).
  if (!open) return null;
  return <ArchiveTenantConfirmBody {...rest} />;
}

function ArchiveTenantConfirmBody({
  tenantName,
  onCancel,
  onConfirm,
}: Omit<ArchiveTenantConfirmProps, 'open'>) {
  const { t } = useTranslation();
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const taRef = useRef<HTMLTextAreaElement>(null);

  const trimmed = reason.trim();
  const valid = trimmed.length >= MIN_REASON;

  const handleConfirm = async () => {
    if (!valid || busy) return;
    setBusy(true);
    try {
      await onConfirm(trimmed);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      open
      onClose={onCancel}
      labelledBy="archive-tenant-title"
      size="lg"
      initialFocusRef={taRef}
      className="bg-surface rounded-xl shadow-lg border border-border p-6"
    >
      <>
        <h2 id="archive-tenant-title" className="text-lg text-text-primary">
          {t('clients.archive.title')}
        </h2>
        <p className="text-sm text-text-secondary mt-2">
          {t('clients.archive.body', { tenant: tenantName })}
        </p>
        <div className="mt-4">
          <label htmlFor="archive-tenant-reason" className="text-sm font-medium text-text-primary">
            {t('clients.archive.reason_label')} <span className="text-danger-700">*</span>
          </label>
          <textarea
            id="archive-tenant-reason"
            ref={taRef}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            rows={4}
            className="mt-1 w-full px-3 py-2 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
            placeholder={t('clients.archive.reason_placeholder')}
            data-testid="archive-tenant-reason"
            aria-invalid={!valid}
          />
          <p className="text-xs text-text-muted mt-1">
            {t('clients.archive.reason_hint', { min: MIN_REASON, current: trimmed.length })}
          </p>
        </div>

        <div className="flex justify-end gap-2 mt-6">
          <Button variant="secondary" onClick={onCancel} disabled={busy}>
            {t('common.cancel')}
          </Button>
          <Button
            variant="danger"
            onClick={handleConfirm}
            disabled={!valid || busy}
            data-testid="archive-tenant-confirm"
          >
            {t('clients.archive.confirm')}
          </Button>
        </div>
      </>
    </Modal>
  );
}
