/**
 * 5-level validation pipeline indicator (integration-blueprint §12).
 *
 * Maps the current ImportBatchStatus to one of the 5 levels and highlights
 * progress. Each level shows the number of errors discovered at that stage.
 */
import { useTranslation } from 'react-i18next';
import { Check, ChevronRight, AlertOctagon, Loader2 } from 'lucide-react';
import { cn } from '@/shared/lib/cn';
import type { ImportBatchStatus, ImportValidationLevel } from '../types';

const LEVELS: ImportValidationLevel[] = ['FILE', 'STRUCTURE', 'ROW', 'BUSINESS', 'SECURITY'];

/** Map a batch status onto the index of the level that is currently running. */
function statusToLevelIndex(status: ImportBatchStatus): {
  current: number;
  completed: number;
  failed: boolean;
} {
  switch (status) {
    case 'UPLOADED':
      return { current: 0, completed: -1, failed: false };
    case 'SCANNING':
      return { current: 0, completed: -1, failed: false };
    case 'SCAN_FAILED':
      return { current: 0, completed: -1, failed: true };
    case 'PARSING':
      return { current: 1, completed: 0, failed: false };
    case 'VALIDATING':
      return { current: 3, completed: 2, failed: false };
    case 'VALIDATION_FAILED':
      return { current: 3, completed: 2, failed: true };
    case 'READY_FOR_REVIEW':
    case 'READY_TO_COMMIT':
    case 'COMMITTING':
    case 'COMMITTED':
    case 'PARTIALLY_COMMITTED':
      return { current: 4, completed: 4, failed: false };
    case 'FAILED':
      return { current: 4, completed: 0, failed: true };
    case 'CANCELLED':
    case 'ARCHIVED':
      return { current: 0, completed: -1, failed: false };
    default:
      return { current: 0, completed: -1, failed: false };
  }
}

interface Props {
  status: ImportBatchStatus;
  errorsByLevel?: Partial<Record<ImportValidationLevel, number>>;
}

export function ImportProgressIndicator({ status, errorsByLevel }: Props) {
  const { t } = useTranslation();
  const { current, completed, failed } = statusToLevelIndex(status);

  return (
    <ol
      className="flex flex-wrap items-stretch gap-2"
      aria-label={t('import.pipeline.aria_label')}
      data-testid="import-progress-indicator"
    >
      {LEVELS.map((level, idx) => {
        const isCompleted = idx <= completed;
        const isCurrent = idx === current;
        const errors = errorsByLevel?.[level] ?? 0;
        const failedHere = failed && isCurrent;
        return (
          <li
            key={level}
            className="flex items-center"
            data-testid={`import-progress-level-${level}`}
            data-state={
              failedHere
                ? 'failed'
                : isCompleted
                  ? 'completed'
                  : isCurrent
                    ? 'current'
                    : 'pending'
            }
          >
            <div
              className={cn(
                'flex items-center gap-2 px-3 py-2 rounded-md border min-w-[10rem]',
                failedHere
                  ? 'bg-danger-50 border-danger-500/30 text-danger-700'
                  : isCompleted
                    ? 'bg-success-50 border-success-500/30 text-success-700'
                    : isCurrent
                      ? 'bg-info-50 border-info-500/30 text-info-700'
                      : 'bg-divider border-border text-text-muted',
              )}
            >
              <span aria-hidden>
                {failedHere ? (
                  <AlertOctagon size={14} />
                ) : isCompleted ? (
                  <Check size={14} />
                ) : isCurrent ? (
                  <Loader2 className="animate-spin" size={14} />
                ) : (
                  <span className="inline-block w-3.5 h-3.5 rounded-full border border-current" />
                )}
              </span>
              <span className="text-sm font-medium">{t(`import.pipeline.level.${level}`)}</span>
              {errors > 0 ? (
                <span className="ml-auto text-xs px-1.5 py-0.5 rounded bg-danger-50 text-danger-700 border border-danger-500/30">
                  {errors}
                </span>
              ) : null}
            </div>
            {idx < LEVELS.length - 1 ? (
              <ChevronRight className="mx-1 text-text-muted" size={14} aria-hidden />
            ) : null}
          </li>
        );
      })}
    </ol>
  );
}
