import { useTranslation } from 'react-i18next';
import { cn } from '@/shared/lib/cn';
import type { ImportTemplateCode } from '../types';

interface Props {
  code: ImportTemplateCode | string;
  className?: string;
}

export function ImportTemplateBadge({ code, className }: Props) {
  const { t } = useTranslation();
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 text-xs font-medium px-2 py-0.5 rounded-md border border-border bg-divider text-text-secondary',
        className,
      )}
    >
      {t(`import.template.${code}`, code)}
    </span>
  );
}
