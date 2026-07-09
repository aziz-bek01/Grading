import { useTranslation } from 'react-i18next';
import { cn } from '@/shared/lib/cn';
import type { WizardStep } from './usePanelWizardState';

interface WizardStepIndicatorProps {
  step: WizardStep;
}

/** Step 1/2/3 indicator strip in the wizard header (FE-1). */
export function WizardStepIndicator({ step }: WizardStepIndicatorProps) {
  const { t } = useTranslation();
  return (
    <ol
      className="mt-2 flex items-center gap-2 text-xs"
      data-testid="wizard-steps"
      aria-label={t('panel.wizard.steps_aria')}
    >
      {([1, 2, 3] as WizardStep[]).map((s) => (
        <li
          key={s}
          aria-current={step === s ? 'step' : undefined}
          className={cn(
            'flex items-center gap-1.5',
            step === s ? 'text-primary-700 font-medium' : 'text-text-muted',
          )}
        >
          <span
            className={cn(
              'inline-flex h-5 w-5 items-center justify-center rounded-full border tabular-nums',
              step === s
                ? 'border-primary-500 bg-primary-50'
                : step > s
                  ? 'border-success-500 bg-success-50 text-success-700'
                  : 'border-border-strong',
            )}
          >
            {s}
          </span>
          {t(`panel.wizard.step_${s}_title`)}
          {s < 3 ? <span aria-hidden>·</span> : null}
        </li>
      ))}
    </ol>
  );
}
