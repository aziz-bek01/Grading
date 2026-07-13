import { TypeChip } from '@/shared/components/data/TypeChip';
import type { ExportType } from '../types';

interface Props {
  type: ExportType;
  className?: string;
}

export function ExportTypeBadge({ type, className }: Props) {
  return <TypeChip value={type} labelPrefix="export.type" className={className} />;
}
