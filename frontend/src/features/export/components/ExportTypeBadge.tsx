import { useTranslation } from 'react-i18next';
import { cn } from '@/shared/lib/cn';
import type { ExportType } from '../types';

interface Props {
  type: ExportType;
  className?: string;
}

export function ExportTypeBadge({ type, className }: Props) {
  const { t } = useTranslation();
  return (
    <span
      className={cn(
        'inline-flex items-center text-xs font-medium px-2 py-0.5 rounded-md border border-border bg-divider text-text-secondary',
        className,
      )}
    >
      {t(`export.type.${type}`)}
    </span>
  );
}
