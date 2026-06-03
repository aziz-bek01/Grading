import { useTranslation } from 'react-i18next';
import { Check, Circle, Zap } from 'lucide-react';
import { cn } from '@/shared/lib/cn';

interface ProgressChipProps {
  filled: number;
  total: number;
  /**
   * Optional list of factor codes filled / missing — used for the tooltip
   * "К1, К5, К8 заполнено. К2 не заполнен." When omitted the chip just
   * shows the {filled}/{total} ratio.
   */
  filledCodes?: string[];
  missingCodes?: string[];
  className?: string;
}

/**
 * Inline progress chip used inside the K-sheet "{filled}/{total}" column.
 * Three states:
 *   - full     → green   + check
 *   - partial  → warning + zap
 *   - empty    → muted   + circle
 *
 * Color is never the only carrier (a11y) — each state also pairs an icon
 * and `aria-label` so screen readers announce the same information.
 */
export function ProgressChip({
  filled,
  total,
  filledCodes,
  missingCodes,
  className,
}: ProgressChipProps) {
  const { t } = useTranslation();
  const safeTotal = Math.max(0, total);
  const safeFilled = Math.max(0, Math.min(filled, safeTotal));
  const state =
    safeTotal === 0 || safeFilled === 0
      ? 'empty'
      : safeFilled === safeTotal
        ? 'full'
        : 'partial';

  const tooltip = (() => {
    if (!filledCodes && !missingCodes) {
      return t('evaluation.byFactor.progress.label', {
        filled: safeFilled,
        total: safeTotal,
      });
    }
    const parts: string[] = [];
    if (filledCodes && filledCodes.length > 0) {
      parts.push(
        t('evaluation.byFactor.progress.tooltip_filled', {
          codes: filledCodes.join(', '),
        }),
      );
    }
    if (missingCodes && missingCodes.length > 0) {
      parts.push(
        t('evaluation.byFactor.progress.tooltip_missing', {
          codes: missingCodes.join(', '),
        }),
      );
    }
    return parts.join(' ');
  })();

  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs border tabular-nums',
        state === 'full' &&
          'bg-success-50 text-success-700 border-success-500/30',
        state === 'partial' &&
          'bg-warning-50 text-warning-700 border-warning-500/30',
        state === 'empty' && 'bg-divider text-text-muted border-border-strong',
        className,
      )}
      title={tooltip}
      aria-label={tooltip}
      data-testid="progress-chip"
      data-state={state}
    >
      {state === 'full' ? (
        <Check size={12} aria-hidden />
      ) : state === 'partial' ? (
        <Zap size={12} aria-hidden />
      ) : (
        <Circle size={12} aria-hidden />
      )}
      <span>
        {safeFilled}/{safeTotal}
      </span>
    </span>
  );
}
