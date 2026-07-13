/**
 * Neutral chip that renders a translated "type" label. Color is
 * intentionally muted — a status badge (see `AsyncJobStatusBadge`) carries
 * the meaningful signal alongside it in table rows.
 *
 * Extracted from `ExportTypeBadge` / `ReportTypeBadge`, which were the
 * identical markup around a per-feature type union + i18n prefix.
 */
import { useTranslation } from 'react-i18next';
import { cn } from '@/shared/lib/cn';

export interface TypeChipProps {
  value: string;
  /** i18n key prefix; label resolves as `t(`${labelPrefix}.${value}`)`. */
  labelPrefix: string;
  className?: string;
  /** Optional testid — callers preserve their own existing testid (or omit it). */
  'data-testid'?: string;
}

export function TypeChip({ value, labelPrefix, className, 'data-testid': testId }: TypeChipProps) {
  const { t } = useTranslation();
  return (
    <span
      className={cn(
        'inline-flex items-center text-xs font-medium px-2 py-0.5 rounded-md border border-border bg-divider text-text-secondary',
        className,
      )}
      data-testid={testId}
    >
      {t(`${labelPrefix}.${value}`)}
    </span>
  );
}
