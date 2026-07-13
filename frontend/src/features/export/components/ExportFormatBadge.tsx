import { FormatChip } from '@/shared/components/data/FormatChip';
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
  return (
    <FormatChip value={format} labelPrefix="export.format" toneMap={TONE} className={className} />
  );
}
