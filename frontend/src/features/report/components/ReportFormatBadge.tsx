import { FormatChip } from '@/shared/components/data/FormatChip';
import type { ReportFormat } from '../types';

interface Props {
  format: ReportFormat;
  className?: string;
}

/**
 * File-format chip — color-codes the rendering backend per spec:
 *   PDF  → red    (Jasper PDF template)
 *   DOCX → blue   (docx4j Word template)
 *   XLSX → green  (Apache POI Excel template)
 */
const TONE: Record<ReportFormat, string> = {
  PDF: 'bg-danger-50 text-danger-700 border-danger-500/30',
  DOCX: 'bg-primary-50 text-primary-700 border-primary-500/30',
  XLSX: 'bg-success-50 text-success-700 border-success-500/30',
};

export function ReportFormatBadge({ format, className }: Props) {
  return (
    <FormatChip
      value={format}
      labelPrefix="report.format"
      toneMap={TONE}
      className={className}
      data-testid="report-format-badge"
    />
  );
}
