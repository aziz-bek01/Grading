/**
 * File-format chip — renders the raw format code (e.g. `XLSX`) color-coded
 * by a caller-supplied tone map, with the translated format name as the
 * `title` tooltip.
 *
 * Extracted from `ExportFormatBadge` / `ReportFormatBadge`, which were the
 * identical markup around a per-feature format union + tone map (the two
 * features intentionally use DIFFERENT colors per format — passed in as a
 * prop, not shared).
 */
import { useTranslation } from 'react-i18next';
import { cn } from '@/shared/lib/cn';

export interface FormatChipProps {
  value: string;
  /** i18n key prefix; the `title` tooltip resolves as `t(`${labelPrefix}.${value}`)`. */
  labelPrefix: string;
  /** Format → Tailwind bg/text/border classes. */
  toneMap: Record<string, string>;
  className?: string;
  /** Optional testid — callers preserve their own existing testid (or omit it). */
  'data-testid'?: string;
}

export function FormatChip({
  value,
  labelPrefix,
  toneMap,
  className,
  'data-testid': testId,
}: FormatChipProps) {
  const { t } = useTranslation();
  return (
    <span
      className={cn(
        'inline-flex items-center text-xs font-medium px-2 py-0.5 rounded-md border',
        toneMap[value],
        className,
      )}
      title={t(`${labelPrefix}.${value}`)}
      data-testid={testId}
    >
      {value}
    </span>
  );
}
