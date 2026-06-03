import { useTranslation } from 'react-i18next';
import { cn } from '@/shared/lib/cn';
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
  const { t } = useTranslation();
  return (
    <span
      className={cn(
        'inline-flex items-center text-xs font-medium px-2 py-0.5 rounded-md border',
        TONE[format],
        className,
      )}
      title={t(`report.format.${format}`)}
      data-testid="report-format-badge"
    >
      {format}
    </span>
  );
}
