import { useTranslation } from 'react-i18next';
import { cn } from '@/shared/lib/cn';
import type { ExportFormat } from '../types';

interface Props {
  format: ExportFormat;
  className?: string;
}

const TONE: Record<ExportFormat, string> = {
  XLSX: 'bg-success-50 text-success-700 border-success-500/30',
  CSV: 'bg-info-50 text-info-700 border-info-500/30',
  PDF: 'bg-warning-50 text-warning-700 border-warning-500/30',
  DOCX: 'bg-primary-50 text-primary-700 border-primary-500/30',
};

export function ExportFormatBadge({ format, className }: Props) {
  const { t } = useTranslation();
  return (
    <span
      className={cn(
        'inline-flex items-center text-xs font-medium px-2 py-0.5 rounded-md border',
        TONE[format],
        className,
      )}
      title={t(`export.format.${format}`)}
    >
      {format}
    </span>
  );
}
