import { useTranslation } from 'react-i18next';

interface QuestionnaireProgressBarProps {
  answered: number;
  required: number;
}

/** Shows "X of Y required questions answered" + visual bar. */
export function QuestionnaireProgressBar({ answered, required }: QuestionnaireProgressBarProps) {
  const { t } = useTranslation();
  const pct = required === 0 ? 0 : Math.round((Math.min(answered, required) / required) * 100);
  return (
    <div className="space-y-1" data-testid="questionnaire-progress">
      <div className="flex items-center justify-between text-xs text-text-secondary">
        <span>{t('jobAnalysis.progress', { answered, required })}</span>
        <span data-testid="progress-percent">{pct}%</span>
      </div>
      <div className="h-2 w-full rounded-full bg-divider overflow-hidden" role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100}>
        <div
          className="h-2 bg-primary-500 transition-[width]"
          style={{ width: `${pct}%` }}
          data-testid="progress-fill"
        />
      </div>
    </div>
  );
}
