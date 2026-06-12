import { useTranslation } from 'react-i18next';
import { ConfirmDialog } from '@/shared/components/confirm-dialog/ConfirmDialog';

interface Props {
  open: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

/**
 * Confirm dialog shown when adding an evaluator AFTER the roster was locked
 * (panel re-opens to AWAITING_EVALUATIONS) — FE-7.
 *
 * Reuses the shared ConfirmDialog: destructive=false (this is not data loss),
 * reason optional. It warns that the change can delay the CEO step because the
 * panel must re-collect and re-average after the new evaluator completes.
 */
export function AddEvaluatorAfterStartConfirm({ open, onConfirm, onCancel }: Props) {
  const { t } = useTranslation();
  return (
    <ConfirmDialog
      open={open}
      destructive={false}
      title={t('panel.add_after_start.title')}
      body={t('panel.add_after_start.body')}
      confirmLabel={t('panel.add_after_start.confirm')}
      onConfirm={onConfirm}
      onCancel={onCancel}
    />
  );
}
