import { useTranslation } from 'react-i18next';
import { CheckCircle2, AlertTriangle, OctagonAlert } from 'lucide-react';
import { cn } from '@/shared/lib/cn';
import type { Factor, ScoringMode } from '../types';

interface WeightSumVisualizerProps {
  factors: Factor[];
  scoringMode: ScoringMode;
  /**
   * Target sum:
   *   - WEIGHTED_POINTS: 100 (weights are percentages, must total 100).
   *   - WEIGHTED_SCALE : `targetTotalPoints` (the version's scale target).
   * Hidden in DIRECT_POINTS — weights are ignored.
   */
  target: number;
  className?: string;
}

/**
 * Weight-sum tolerance for "at target" determination.
 *
 * Aligned with backend `MethodologyWeightValidationPolicy` (Phase 4 remediation
 * PC4-1 / D-401). Backend remediation adopts 1e-4 tolerance; frontend visualiser
 * shares the same constant so the green-light UX guarantees backend acceptance.
 *
 * DO NOT CHANGE WITHOUT BACKEND ALIGNMENT.
 */
export const WEIGHT_SUM_TOLERANCE = 0.0001;

/**
 * Horizontal stacked bar showing the sum of factor weights vs the target.
 *
 * - GREEN  when `|sum - target| <= WEIGHT_SUM_TOLERANCE` (effectively equal).
 * - AMBER  warning when sum drifts (any other case).
 * - RED    when deviation > 10 % of the target.
 * Hidden entirely in DIRECT_POINTS — weights have no effect on scoring.
 *
 * Design-foundation §13 spec.
 */
export function WeightSumVisualizer({
  factors,
  scoringMode,
  target,
  className,
}: WeightSumVisualizerProps) {
  const { t } = useTranslation();

  if (scoringMode === 'DIRECT_POINTS') return null;

  const sum = factors.reduce((acc, f) => acc + (Number.isFinite(f.weight) ? f.weight : 0), 0);
  const delta = sum - target;
  const absDelta = Math.abs(delta);
  const atTarget = absDelta <= WEIGHT_SUM_TOLERANCE;
  const overTenPercent = target > 0 && absDelta / target > 0.1;

  type Tone = 'success' | 'warning' | 'danger';
  const tone: Tone = atTarget ? 'success' : overTenPercent ? 'danger' : 'warning';

  const toneClasses: Record<Tone, { bar: string; bg: string; text: string; border: string }> = {
    success: {
      bar: 'bg-success-500',
      bg: 'bg-success-50',
      text: 'text-success-700',
      border: 'border-success-500/30',
    },
    warning: {
      bar: 'bg-warning-500',
      bg: 'bg-warning-50',
      text: 'text-warning-700',
      border: 'border-warning-500/30',
    },
    danger: {
      bar: 'bg-danger-500',
      bg: 'bg-danger-50',
      text: 'text-danger-700',
      border: 'border-danger-500/30',
    },
  };

  const tc = toneClasses[tone];

  // Bar width: 0 .. 100% of the container; cap visually at 120 % so over-target
  // is still legible without overflowing.
  const visualPct = target <= 0 ? 0 : Math.min(120, (sum / target) * 100);

  const fmt = (n: number) =>
    Number.isInteger(n) ? n.toFixed(0) : n.toFixed(4).replace(/0+$/, '').replace(/\.$/, '');

  return (
    <section
      role="status"
      aria-live="polite"
      data-testid="weight-sum-visualizer"
      data-tone={tone}
      className={cn('rounded-lg border', tc.bg, tc.border, 'p-4 space-y-3', className)}
    >
      <header className="flex items-center justify-between gap-3 flex-wrap">
        <div className="inline-flex items-center gap-2">
          {tone === 'success' ? (
            <CheckCircle2 size={16} className={tc.text} aria-hidden />
          ) : tone === 'warning' ? (
            <AlertTriangle size={16} className={tc.text} aria-hidden />
          ) : (
            <OctagonAlert size={16} className={tc.text} aria-hidden />
          )}
          <h3 className="text-sm font-semibold text-text-primary">
            {t('methodology.weight_sum_title')}
          </h3>
        </div>
        <p className={cn('text-xs font-medium tabular-nums', tc.text)} data-testid="weight-sum-label">
          {t('methodology.weight_sum_label', { current: fmt(sum), target: fmt(target) })}
        </p>
      </header>

      <div
        className="h-3 w-full rounded-full bg-divider overflow-hidden"
        aria-hidden
      >
        <div
          className={cn('h-full', tc.bar, 'transition-all')}
          style={{ width: `${Math.min(visualPct, 100)}%` }}
          data-testid="weight-sum-bar"
        />
      </div>

      <p className="text-xs text-text-secondary">
        {atTarget
          ? t('methodology.weight_sum_at_target')
          : delta > 0
            ? t('methodology.weight_sum_over', { amount: fmt(absDelta) })
            : t('methodology.weight_sum_under', { amount: fmt(absDelta) })}
      </p>
    </section>
  );
}
