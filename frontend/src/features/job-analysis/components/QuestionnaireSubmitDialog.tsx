import { useTranslation } from 'react-i18next';
import { ConfirmDialog } from '@/shared/components/confirm-dialog/ConfirmDialog';

interface QuestionnaireSubmitDialogProps {
  open: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}

export function QuestionnaireSubmitDialog({ open, onCancel, onConfirm }: QuestionnaireSubmitDialogProps) {
  const { t } = useTranslation();
  return (
    <ConfirmDialog
      open={open}
      title={t('jobAnalysis.submit_title')}
      body={t('jobAnalysis.submit_body')}
      confirmLabel={t('common.submit')}
      onCancel={onCancel}
      onConfirm={onConfirm}
    />
  );
}
