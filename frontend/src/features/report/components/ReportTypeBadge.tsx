import { TypeChip } from '@/shared/components/data/TypeChip';
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
  return (
    <TypeChip
      value={type}
      labelPrefix="report.type"
      className={className}
      data-testid="report-type-badge"
    />
  );
}
