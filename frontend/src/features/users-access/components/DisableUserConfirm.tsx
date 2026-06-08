import { useTranslation } from 'react-i18next';
import { ConfirmDialog } from '@/shared/components/confirm-dialog/ConfirmDialog';

interface Props {
  open: boolean;
  userName: string;
  onConfirm: () => void;
  onCancel: () => void;
}

/**
 * Destructive confirmation for DISABLE user — the product's "delete user"
 * (soft-disable; there is intentionally no hard delete). Disabling cuts the
 * user's access across every tenant and — once the backend offboarding lands —
 * deactivates their Zitadel login. Reversible via Reactivate.
 */
export function DisableUserConfirm({ open, userName, onConfirm, onCancel }: Props) {
  const { t } = useTranslation();
  return (
    <ConfirmDialog
      open={open}
      title={t('users.disable.title')}
      body={t('users.disable.body', { user: userName })}
      confirmLabel={t('users.disable.confirm')}
      cancelLabel={t('common.cancel')}
      destructive
      onConfirm={onConfirm}
      onCancel={onCancel}
    />
  );
}
