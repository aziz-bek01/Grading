import { useTranslation } from 'react-i18next';
import { Layers, AlertCircle, Clock } from 'lucide-react';
import { cn } from '@/shared/lib/cn';

interface AssignedGradeBadgeProps {
  /** The grade number assigned (1..16 typically) — null when not yet assigned. */
  gradeNumber?: number | null;
  /** True when the evaluation total fell outside every band on the structure. */
  outOfRange?: boolean;
  /** Score band range "[min, max]" shown on hover. */
  bandLabel?: string | null;
  className?: string;
}

/**
 * Small badge for evaluation context. Three states:
 *   - "Grade N" with the band tooltip (when gradeNumber is set).
 *   - "Out of range — manual calibration needed" (when outOfRange).
 *   - "Grade pending" (when gradeNumber is null and not out-of-range).
 */
export function AssignedGradeBadge({
  gradeNumber,
  outOfRange,
  bandLabel,
  className,
}: AssignedGradeBadgeProps) {
  const { t } = useTranslation();

  if (outOfRange) {
    return (
      <span
        title={t('gradeStructure.assigned.out_of_range_hint')}
        data-testid="assigned-grade-out-of-range"
        className={cn(
          'inline-flex items-center gap-1.5 text-xs font-medium px-2 py-0.5 rounded-full border',
          'bg-warning-50 text-warning-700 border-warning-500/30',
          className,
        )}
      >
        <AlertCircle size={12} aria-hidden />
        {t('gradeStructure.assigned.out_of_range')}
      </span>
    );
  }
  if (gradeNumber == null) {
    return (
      <span
        title={t('gradeStructure.assigned.pending_hint')}
        data-testid="assigned-grade-pending"
        className={cn(
          'inline-flex items-center gap-1.5 text-xs font-medium px-2 py-0.5 rounded-full border',
          'bg-divider text-text-secondary border-border-strong',
          className,
        )}
      >
        <Clock size={12} aria-hidden />
        {t('gradeStructure.assigned.pending')}
      </span>
    );
  }
  return (
    <span
      title={bandLabel ?? ''}
      data-testid={`assigned-grade-${gradeNumber}`}
      className={cn(
        'inline-flex items-center gap-1.5 text-xs font-semibold px-2 py-0.5 rounded-full border',
        'bg-success-50 text-success-700 border-success-500/30',
        className,
      )}
    >
      <Layers size={12} aria-hidden />
      {t('gradeStructure.assigned.label', { number: gradeNumber })}
    </span>
  );
}
