/**
 * Generic wrapper over the shared `StatusBadge` for "async job" status enums
 * (export jobs, reports, ...). Extracted from `ExportStatusBadge` /
 * `ReportStatusBadge`, which declared the identical 8-status tone map +
 * `StatusBadge` render. Each feature keeps its own tone map (values happen
 * to match today, but are declared independently so a future divergence is
 * a one-line change, not a shared-component edit) and i18n label prefix.
 */
import { useTranslation } from 'react-i18next';
import { StatusBadge, type StatusTone } from '@/shared/components/status/StatusBadge';

export interface AsyncJobStatusBadgeProps<TStatus extends string> {
  status: TStatus;
  /** Status → badge tone. Unmapped statuses fall back to `draft` (never throws). */
  toneMap: Record<TStatus, StatusTone>;
  /** i18n key prefix; label resolves as `t(`${labelPrefix}.${status}`)`. */
  labelPrefix: string;
  className?: string;
}

export function AsyncJobStatusBadge<TStatus extends string>({
  status,
  toneMap,
  labelPrefix,
  className,
}: AsyncJobStatusBadgeProps<TStatus>) {
  const { t } = useTranslation();
  return (
    <StatusBadge
      tone={toneMap[status] ?? 'draft'}
      label={t(`${labelPrefix}.${status}`)}
      className={className}
    />
  );
}
