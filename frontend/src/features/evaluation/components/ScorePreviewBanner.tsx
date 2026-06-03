import { useTranslation } from 'react-i18next';
import { Info } from 'lucide-react';
import { cn } from '@/shared/lib/cn';

interface Props {
  rawTotal: number | null | undefined;
  displayedTotal: number | null | undefined;
  isPending?: boolean;
  className?: string;
}

function fmt(n: number | null | undefined, digits = 2): string {
  if (n === null || n === undefined || Number.isNaN(n)) return '—';
  return n.toFixed(digits);
}

/**
 * Sticky preview banner with the "Preview only" disclaimer (PRD
 * MVP1-E9-7 hard rule). ALWAYS shows the disclaimer text, even when
 * no scores are selected yet — frontend score is NEVER authoritative;
 * backend is the source of truth per architecture §15 "Frontend score
 * can be preview only".
 */
export function ScorePreviewBanner({
  rawTotal,
  displayedTotal,
  isPending,
  className,
}: Props) {
  const { t } = useTranslation();
  return (
    <div
      data-testid="score-preview-banner"
      className={cn(
        'rounded-lg border-l-4 border-l-primary-500 border border-border bg-primary-50/50 p-4 flex items-start gap-3',
        className,
      )}
      aria-live="polite"
    >
      <Info size={18} className="text-primary-600 shrink-0 mt-0.5" aria-hidden />
      <div className="space-y-1 flex-1">
        <div className="flex items-baseline gap-3 flex-wrap">
          <span className="text-xs uppercase tracking-wide text-text-secondary">
            {t('evaluation.preview.total_label')}
          </span>
          <span
            className="text-xl font-semibold text-text-primary tabular-nums"
            data-testid="preview-displayed-total"
          >
            {fmt(displayedTotal, 2)}
          </span>
          <span className="text-xs text-text-muted">
            {t('evaluation.preview.raw_label')}{' '}
            <span data-testid="preview-raw-total" className="tabular-nums">
              {fmt(rawTotal, 4)}
            </span>
          </span>
          {isPending ? (
            <span className="text-xs text-text-muted italic">
              {t('evaluation.preview.recalculating')}
            </span>
          ) : null}
        </div>
        <p
          className="text-xs text-text-secondary"
          data-testid="preview-only-disclaimer"
        >
          {t('evaluation.preview.disclaimer')}
        </p>
      </div>
    </div>
  );
}
