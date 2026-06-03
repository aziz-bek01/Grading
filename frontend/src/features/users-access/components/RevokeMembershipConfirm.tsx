import { useTranslation } from 'react-i18next';
import { ConfirmDialog } from '@/shared/components/confirm-dialog/ConfirmDialog';

interface Props {
  open: boolean;
  tenantBrand: string;
  userName: string;
  onConfirm: () => void;
  onCancel: () => void;
}

/**
 * Destructive confirmation for REVOKE Membership.
 * Soft-revokes on the backend (status → REVOKED, no DB delete).
 */
export function RevokeMembershipConfirm({ open, tenantBrand, userName, onConfirm, onCancel }: Props) {
  const { t } = useTranslation();
  return (
    <ConfirmDialog
      open={open}
      title={t('users.revokeMembership.title')}
      body={t('users.revokeMembership.body', { tenant: tenantBrand, user: userName })}
      confirmLabel={t('users.revokeMembership.confirm')}
      cancelLabel={t('common.cancel')}
      destructive
      onConfirm={onConfirm}
      onCancel={onCancel}
    />
  );
}
