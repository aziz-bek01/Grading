import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { CheckCircle2, AlertTriangle, OctagonAlert } from 'lucide-react';
import { cn } from '@/shared/lib/cn';
import { hasErrors, validateBands } from '../lib/bandValidation';
import type { Grade, GradeGapPolicy } from '../types';

interface GradeBandValidatorProps {
  grades: Grade[];
  gapPolicy: GradeGapPolicy;
  className?: string;
}

/**
 * Aggregate band-validation banner.
 *  - green   "All bands valid (no overlaps, no gaps)" when clean.
 *  - red     for any overlap or invalid (min > max) row.
 *  - yellow  for gaps when policy = ALLOW_GAPS_WARN.
 *  - red     for gaps when policy = STRICT_NO_GAPS.
 *
 * Client-side preview only — the backend remains the source of truth at
 * approve time (it re-runs the same algorithm with BigDecimal precision).
 */
export function GradeBandValidator({
  grades,
  gapPolicy,
  className,
}: GradeBandValidatorProps) {
  const { t } = useTranslation();
  const report = useMemo(() => validateBands(grades), [grades]);
  const isError = hasErrors(report, gapPolicy);
  const isWarn = !isError && report.gaps.length > 0 && gapPolicy === 'ALLOW_GAPS_WARN';
  const isClean = !isError && !isWarn && report.missingBands.length === 0;

  if (grades.length === 0) {
    return (
      <div
        data-testid="grade-band-validator"
        data-state="empty"
        className={cn(
          'rounded-lg border border-border bg-divider/30 px-4 py-3 text-xs text-text-secondary',
          className,
        )}
      >
        {t('gradeStructure.validator.empty')}
      </div>
    );
  }

  const tone = isError
    ? {
        wrap: 'bg-danger-50 border-danger-500/30 text-danger-700',
        icon: <OctagonAlert size={16} className="text-danger-600" aria-hidden />,
      }
    : isWarn
      ? {
          wrap: 'bg-warning-50 border-warning-500/30 text-warning-700',
          icon: <AlertTriangle size={16} className="text-warning-600" aria-hidden />,
        }
      : {
          wrap: 'bg-success-50 border-success-500/30 text-success-700',
          icon: <CheckCircle2 size={16} className="text-success-700" aria-hidden />,
        };

  const title = isError
    ? t('gradeStructure.validator.has_errors')
    : isWarn
      ? t('gradeStructure.validator.has_warnings')
      : isClean
        ? t('gradeStructure.validator.all_valid')
        : t('gradeStructure.validator.has_warnings');

  return (
    <section
      role="status"
      data-testid="grade-band-validator"
      data-state={isError ? 'error' : isWarn ? 'warn' : 'ok'}
      className={cn('rounded-lg border px-4 py-3', tone.wrap, className)}
    >
      <header className="flex items-center gap-2">
        {tone.icon}
        <h3 className="text-sm font-semibold">{title}</h3>
      </header>
      <ul className="mt-2 space-y-1 text-xs">
        {report.overlaps.length > 0 ? (
          <li data-testid="validator-overlap-line">
            <strong>{t('gradeStructure.validator.overlap_count', { count: report.overlaps.length })}</strong>
            <ul className="ml-4 list-disc space-y-0.5 mt-1">
              {report.overlaps.map((o, i) => (
                <li key={`o-${i}`} className="tabular-nums">
                  {t('gradeStructure.validator.overlap_item', {
                    a: o.aGradeNumber,
                    b: o.bGradeNumber,
                    from: o.fromScore.toFixed(4),
                    to: o.toScore.toFixed(4),
                  })}
                </li>
              ))}
            </ul>
          </li>
        ) : null}
        {report.gaps.length > 0 ? (
          <li data-testid="validator-gap-line">
            <strong>{t('gradeStructure.validator.gap_count', { count: report.gaps.length })}</strong>
            <ul className="ml-4 list-disc space-y-0.5 mt-1">
              {report.gaps.map((g, i) => (
                <li key={`g-${i}`} className="tabular-nums">
                  {t('gradeStructure.validator.gap_item', {
                    below: g.belowGradeNumber,
                    above: g.aboveGradeNumber,
                    from: g.fromScore.toFixed(4),
                    to: g.toScore.toFixed(4),
                  })}
                </li>
              ))}
            </ul>
          </li>
        ) : null}
        {report.missingBands.length > 0 ? (
          <li data-testid="validator-missing-line">
            {t('gradeStructure.validator.missing_bands', {
              list: report.missingBands.join(', '),
            })}
          </li>
        ) : null}
        {report.invalidBands.length > 0 ? (
          <li data-testid="validator-invalid-line">
            {t('gradeStructure.validator.invalid_bands', {
              list: report.invalidBands.join(', '),
            })}
          </li>
        ) : null}
      </ul>
    </section>
  );
}
