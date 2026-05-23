import { useTranslation } from 'react-i18next';
import { Star } from 'lucide-react';
import { pickLocalized } from '@/shared/lib/localized';
import { cn } from '@/shared/lib/cn';
import type {
  JobAnalysisAnswerValue,
  JobAnalysisQuestion,
} from '../types';

interface QuestionRendererProps {
  question: JobAnalysisQuestion;
  value: JobAnalysisAnswerValue;
  onChange: (value: JobAnalysisAnswerValue) => void;
  readOnly?: boolean;
}

/** Renders one question based on its `question_type`. */
export function QuestionRenderer({ question, value, onChange, readOnly }: QuestionRendererProps) {
  const { t, i18n } = useTranslation();
  const prompt = pickLocalized(question.prompt, i18n.language);
  const help = question.help_text ? pickLocalized(question.help_text, i18n.language) : undefined;
  const inputId = `q-${question.id}`;

  const render = () => {
    switch (question.question_type) {
      case 'TEXT':
        return (
          <input
            id={inputId}
            type="text"
            disabled={readOnly}
            value={typeof value === 'string' ? value : ''}
            onChange={(e) => onChange(e.target.value)}
            className="w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500 disabled:bg-divider"
            data-testid={`q-input-${question.id}`}
          />
        );
      case 'LONG_TEXT':
        return (
          <textarea
            id={inputId}
            rows={4}
            disabled={readOnly}
            value={typeof value === 'string' ? value : ''}
            onChange={(e) => onChange(e.target.value)}
            className="w-full p-2 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500 disabled:bg-divider"
            data-testid={`q-textarea-${question.id}`}
          />
        );
      case 'SINGLE_CHOICE':
        return (
          <div className="space-y-2" role="radiogroup" aria-labelledby={`label-${question.id}`}>
            {(question.choices ?? []).map((c) => (
              <label key={c.code} className="flex items-center gap-2 text-sm">
                <input
                  type="radio"
                  name={inputId}
                  value={c.code}
                  disabled={readOnly}
                  checked={value === c.code}
                  onChange={() => onChange(c.code)}
                  data-testid={`q-radio-${question.id}-${c.code}`}
                />
                <span>{pickLocalized(c.label, i18n.language)}</span>
              </label>
            ))}
          </div>
        );
      case 'MULTI_CHOICE': {
        const arr = Array.isArray(value) ? value : [];
        return (
          <div className="space-y-2">
            {(question.choices ?? []).map((c) => {
              const checked = arr.includes(c.code);
              return (
                <label key={c.code} className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    disabled={readOnly}
                    checked={checked}
                    onChange={(e) => {
                      const next = e.target.checked
                        ? [...arr, c.code]
                        : arr.filter((x) => x !== c.code);
                      onChange(next);
                    }}
                    data-testid={`q-check-${question.id}-${c.code}`}
                  />
                  <span>{pickLocalized(c.label, i18n.language)}</span>
                </label>
              );
            })}
          </div>
        );
      }
      case 'RATING_SCALE': {
        const max = question.scale_max ?? 5;
        const numeric = typeof value === 'number' ? value : 0;
        return (
          <div className="flex items-center gap-1" role="radiogroup" aria-label={prompt}>
            {Array.from({ length: max }, (_, i) => i + 1).map((n) => (
              <button
                key={n}
                type="button"
                disabled={readOnly}
                onClick={() => onChange(n)}
                aria-label={`${n}/${max}`}
                aria-pressed={n <= numeric}
                className={cn(
                  'p-1 rounded',
                  n <= numeric ? 'text-warning-500' : 'text-text-disabled',
                  readOnly && 'cursor-not-allowed',
                )}
                data-testid={`q-rating-${question.id}-${n}`}
              >
                <Star size={20} fill={n <= numeric ? 'currentColor' : 'none'} aria-hidden />
              </button>
            ))}
          </div>
        );
      }
      case 'NUMBER':
        return (
          <input
            id={inputId}
            type="number"
            disabled={readOnly}
            value={typeof value === 'number' ? value : ''}
            onChange={(e) => onChange(e.target.value === '' ? null : Number(e.target.value))}
            className="w-40 h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500 disabled:bg-divider"
            data-testid={`q-number-${question.id}`}
          />
        );
      default:
        return null;
    }
  };

  return (
    <div className="space-y-2" data-testid={`question-${question.id}`}>
      <label id={`label-${question.id}`} htmlFor={inputId} className="text-sm font-medium text-text-primary">
        {prompt}
        {question.required ? <span className="ml-1 text-danger-700">*</span> : null}
        {!question.required ? (
          <span className="ml-2 text-xs text-text-muted">({t('common.optional')})</span>
        ) : null}
      </label>
      {help ? <p className="text-xs text-text-secondary">{help}</p> : null}
      {render()}
    </div>
  );
}
