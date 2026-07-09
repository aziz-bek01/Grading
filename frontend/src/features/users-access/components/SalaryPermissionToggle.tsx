/**
 * Salary-data permission toggle for a single membership.
 *
 * 2-step confirmation:
 *   1. Toggle click → opens a warning dialog that requires a reason
 *      (≥ 10 characters) and an explicit "Confirm" press.
 *   2. The actual PATCH is only fired after the reason is supplied.
 *
 * Visual contract:
 *   - Always rendered with a red-tinted Shield icon when active.
 *   - Even if the actor LACKS USER_SALARY_PERMISSION_TOGGLE, the toggle is
 *     rendered disabled (so the security state is visible) — but no clicks
 *     are accepted.
 */
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Shield, ShieldOff } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { Modal } from '@/shared/components/ui/Modal';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import { formatDateSafe } from '@/shared/lib/dates';
import { cn } from '@/shared/lib/cn';

interface SalaryPermissionToggleProps {
  enabled: boolean;
  grantedAt?: string | null;
  /** Called when the user submits a valid reason (≥ 10 chars). */
  onSubmit: (next: boolean, reason: string) => Promise<void> | void;
  disabled?: boolean;
}

export function SalaryPermissionToggle({
  enabled,
  grantedAt,
  onSubmit,
  disabled,
}: SalaryPermissionToggleProps) {
  const { t, i18n } = useTranslation();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const closeDialog = () => {
    setDialogOpen(false);
    setReason('');
  };

  const handleSubmit = async () => {
    if (reason.trim().length < 10) return;
    try {
      setSubmitting(true);
      await onSubmit(!enabled, reason.trim());
      closeDialog();
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="rounded-md border border-salary-sensitive/30 bg-salary-sensitive-bg p-3">
      <div className="flex items-start gap-3">
        <div className={cn('mt-0.5', enabled ? 'text-salary-sensitive' : 'text-text-muted')}>
          {enabled ? <Shield size={18} aria-hidden /> : <ShieldOff size={18} aria-hidden />}
        </div>
        <div className="flex-1">
          <p className="text-sm font-medium text-text-primary">
            {t('users.salary.label')}
          </p>
          <p className="text-xs text-text-secondary mt-1">
            {enabled
              ? t('users.salary.enabledHint', { date: formatDateSafe(grantedAt, i18n.language) })
              : t('users.salary.disabledHint')}
          </p>
        </div>
        <PermissionGate permission={PERMISSIONS.USER_SALARY_PERMISSION_TOGGLE}>
          <Button
            variant={enabled ? 'secondary' : 'danger'}
            size="sm"
            onClick={() => setDialogOpen(true)}
            disabled={disabled}
            data-testid="salary-permission-toggle"
          >
            {enabled ? t('users.salary.revoke') : t('users.salary.grant')}
          </Button>
        </PermissionGate>
      </div>

      <Modal
        open={dialogOpen}
        onClose={closeDialog}
        labelledBy="salary-perm-dialog-title"
        size="md"
        dismissible={!submitting}
        className="bg-surface rounded-xl shadow-lg border border-border p-6"
      >
        <>
          <h2 id="salary-perm-dialog-title" className="text-lg text-text-primary">
            {enabled
              ? t('users.salary.confirmRevokeTitle')
              : t('users.salary.confirmGrantTitle')}
          </h2>
          <div className="mt-3 rounded-md bg-danger-50 border border-danger-500/30 p-3">
            <p className="text-sm text-danger-700 font-medium">
              {t('users.salary.warningHeader')}
            </p>
            <p className="text-xs text-text-secondary mt-1">
              {enabled
                ? t('users.salary.revokeWarning')
                : t('users.salary.grantWarning')}
            </p>
          </div>
          <label htmlFor="salary-perm-reason" className="block mt-4 text-sm font-medium text-text-primary">
            {t('common.reason_label')} <span className="text-danger-700">*</span>
          </label>
          <textarea
            id="salary-perm-reason"
            className="mt-1 w-full min-h-[96px] rounded-md border border-border-strong p-3 text-sm focus:border-primary-500 focus:outline-none"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            aria-describedby="salary-perm-reason-help"
            data-testid="salary-permission-reason"
          />
          <p id="salary-perm-reason-help" className="text-xs text-text-muted mt-1">
            {t('users.salary.reasonHint')}
          </p>
          <div className="flex justify-end gap-2 mt-6">
            <Button variant="secondary" onClick={closeDialog} disabled={submitting}>
              {t('common.cancel')}
            </Button>
            <Button
              variant="danger"
              onClick={handleSubmit}
              disabled={reason.trim().length < 10 || submitting}
              data-testid="salary-permission-confirm"
            >
              {enabled ? t('users.salary.revoke') : t('users.salary.grant')}
            </Button>
          </div>
        </>
      </Modal>
    </div>
  );
}
