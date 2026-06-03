import { useTranslation } from 'react-i18next';
import { cn } from '@/shared/lib/cn';
import type { ReportType } from '../types';

interface Props {
  type: ReportType;
  className?: string;
}

/**
 * Neutral chip that renders the report type with its translated label.
 * Color is intentionally muted — status badge carries the meaningful
 * signal on each row.
 */
export function ReportTypeBadge({ type, className }: Props) {
  const { t } = useTranslation();
  return (
    <span
      className={cn(
        'inline-flex items-center text-xs font-medium px-2 py-0.5 rounded-md border border-border bg-divider text-text-secondary',
        className,
      )}
      data-testid="report-type-badge"
    >
      {t(`report.type.${type}`)}
    </span>
  );
}
