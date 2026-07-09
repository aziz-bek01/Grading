import { useTranslation } from 'react-i18next';
import { LayoutGrid, TableProperties } from 'lucide-react';
import { cn } from '@/shared/lib/cn';
import type { ViewMode } from '../../pages/useEvaluationListState';

interface EvaluationModeToggleProps {
  mode: ViewMode;
  onChange: (mode: ViewMode) => void;
}

/**
 * By-position / by-factor mode tablist + hint text. Extracted from
 * `EvaluationListPage` (FE-041). Hidden entirely for committee scorers by
 * the caller (they are locked to by-factor).
 */
export function EvaluationModeToggle({ mode, onChange }: EvaluationModeToggleProps) {
  const { t } = useTranslation();
  return (
    <>
      <div
        role="tablist"
        aria-label={t('evaluation.byFactor.mode_toggle.aria')}
        className="inline-flex gap-1 p-1 bg-divider rounded-md"
        data-testid="evaluation-mode-toggle"
      >
        <button
          type="button"
          role="tab"
          aria-selected={mode === 'by-position'}
          onClick={() => onChange('by-position')}
          data-testid="mode-by-position"
          className={cn(
            'inline-flex items-center gap-1.5 px-3 py-1.5 text-sm rounded-md transition-colors',
            mode === 'by-position'
              ? 'bg-surface text-text-primary shadow-sm'
              : 'text-text-secondary hover:text-text-primary',
          )}
        >
          <LayoutGrid size={14} aria-hidden />
          {t('evaluation.byFactor.mode_toggle.by_position')}
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={mode === 'by-factor'}
          onClick={() => onChange('by-factor')}
          data-testid="mode-by-factor"
          className={cn(
            'inline-flex items-center gap-1.5 px-3 py-1.5 text-sm rounded-md transition-colors',
            mode === 'by-factor'
              ? 'bg-surface text-text-primary shadow-sm'
              : 'text-text-secondary hover:text-text-primary',
          )}
        >
          <TableProperties size={14} aria-hidden />
          {t('evaluation.byFactor.mode_toggle.by_factor')}
        </button>
      </div>

      <p
        className="text-xs text-text-muted -mt-2"
        data-testid="evaluation-mode-hint"
      >
        {mode === 'by-position'
          ? t('evaluation.mode_hint.by_position')
          : t('evaluation.mode_hint.by_factor')}
      </p>
    </>
  );
}
