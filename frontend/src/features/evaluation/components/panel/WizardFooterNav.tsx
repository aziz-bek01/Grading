import { ArrowLeft, ArrowRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/shared/components/ui/Button';
import type { WizardStep } from './usePanelWizardState';

interface WizardFooterNavProps {
  step: WizardStep;
  submitting: boolean;
  selectedPositionsCount: number;
  canAdvanceStep1: boolean;
  canAdvanceStep2: boolean;
  canConfirm: boolean;
  onBack: () => void;
  onNext: () => void;
  onCancel: () => void;
  onConfirm: () => void;
}

/** Wizard footer: Back/Cancel + Next/Confirm nav, plus the step-2 selected count. */
export function WizardFooterNav({
  step,
  submitting,
  selectedPositionsCount,
  canAdvanceStep1,
  canAdvanceStep2,
  canConfirm,
  onBack,
  onNext,
  onCancel,
  onConfirm,
}: WizardFooterNavProps) {
  const { t } = useTranslation();

  return (
    <div className="shrink-0 flex justify-between items-center gap-2 pt-2">
      <div className="text-xs text-text-muted tabular-nums">
        {step === 2
          ? t('panel.wizard.selected_count', { count: selectedPositionsCount })
          : null}
      </div>
      <div className="flex gap-2">
        {step > 1 ? (
          <Button
            variant="secondary"
            onClick={onBack}
            disabled={submitting}
            data-testid="wizard-back"
          >
            <ArrowLeft size={14} aria-hidden /> {t('common.back')}
          </Button>
        ) : (
          <Button
            variant="secondary"
            onClick={onCancel}
            disabled={submitting}
            data-testid="open-panel-cancel"
          >
            {t('common.cancel')}
          </Button>
        )}
        {step < 3 ? (
          <Button
            onClick={onNext}
            disabled={step === 1 ? !canAdvanceStep1 : !canAdvanceStep2}
            data-testid="wizard-next"
          >
            {t('panel.wizard.next')} <ArrowRight size={14} aria-hidden />
          </Button>
        ) : (
          <Button
            onClick={onConfirm}
            disabled={!canConfirm}
            data-testid="open-panel-confirm"
          >
            {t('panel.wizard.confirm')}
          </Button>
        )}
      </div>
    </div>
  );
}
