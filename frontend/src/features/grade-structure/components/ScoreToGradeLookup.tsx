import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Search } from 'lucide-react';
import { Card } from '@/shared/components/ui/Card';
import { usePreviewGradeLookup } from '../hooks/useGradeStructure';
import { AssignedGradeBadge } from './AssignedGradeBadge';
import type { Grade } from '../types';

interface ScoreToGradeLookupProps {
  gradeStructureId: string;
  grades: Grade[];
}

/**
 * Tiny utility widget on the builder page:
 *   "Type a score to preview the grade"
 * Calls preview-grade-lookup with a 300ms debounce (see usePreviewGradeLookup)
 * and renders the result. The endpoint never persists / never audits.
 */
export function ScoreToGradeLookup({ gradeStructureId, grades }: ScoreToGradeLookupProps) {
  const { t } = useTranslation();
  const [input, setInput] = useState<string>('');
  const score = input.trim() === '' || Number.isNaN(Number(input)) ? null : Number(input);

  const { result, isLoading } = usePreviewGradeLookup(gradeStructureId, score);

  const matchingGrade = result?.grade_number
    ? grades.find((g) => g.grade_number === result.grade_number)
    : null;
  const bandLabel = matchingGrade?.band
    ? `[${matchingGrade.band.min_score.toFixed(4)}, ${matchingGrade.band.max_score.toFixed(4)}]`
    : null;

  return (
    <Card
      compact
      title={t('gradeStructure.score_lookup.title')}
      data-testid="score-to-grade-lookup"
    >
      <p className="text-xs text-text-secondary mb-2">
        {t('gradeStructure.score_lookup.subtitle')}
      </p>
      <label className="block">
        <span className="sr-only">{t('gradeStructure.score_lookup.input_label')}</span>
        <div className="relative">
          <Search
            size={14}
            className="absolute left-3 top-1/2 -translate-y-1/2 text-text-secondary"
            aria-hidden
          />
          <input
            type="number"
            step="0.0001"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder={t('gradeStructure.score_lookup.placeholder')}
            className="w-full h-9 pl-9 pr-3 rounded-md border border-border-strong text-sm tabular-nums focus:border-primary-500 focus:outline-none"
            data-testid="score-to-grade-lookup-input"
          />
        </div>
      </label>
      <div className="mt-3 min-h-[2rem]" aria-live="polite">
        {score == null ? (
          <p className="text-xs text-text-muted">
            {t('gradeStructure.score_lookup.empty_state')}
          </p>
        ) : isLoading ? (
          <p className="text-xs text-text-secondary" data-testid="score-to-grade-lookup-loading">
            {t('gradeStructure.score_lookup.loading')}
          </p>
        ) : result == null || (result.grade_number == null && result.out_of_range !== true) ? (
          <p className="text-xs text-text-muted">
            {t('gradeStructure.score_lookup.empty_state')}
          </p>
        ) : result.out_of_range || result.grade_number == null ? (
          <div
            className="text-xs text-warning-700"
            data-testid="score-to-grade-lookup-out-of-range"
          >
            {t('gradeStructure.score_lookup.out_of_range', {
              score: score.toFixed(4),
            })}
          </div>
        ) : (
          <div className="flex items-center gap-2" data-testid="score-to-grade-lookup-result">
            <AssignedGradeBadge gradeNumber={result.grade_number} bandLabel={bandLabel} />
            <span className="text-xs text-text-secondary tabular-nums">
              {bandLabel ?? ''}
            </span>
          </div>
        )}
      </div>
    </Card>
  );
}
