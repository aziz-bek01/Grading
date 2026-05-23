import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { X } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { cn } from '@/shared/lib/cn';

export interface FilterOption {
  value: string;
  label: string;
}

export interface FilterDefinition {
  key: string;
  label: string;
  options: FilterOption[];
  value: string | null;
  onChange: (value: string | null) => void;
}

interface FilterBarProps {
  filters: FilterDefinition[];
  onReset?: () => void;
  children?: ReactNode;
  className?: string;
}

/** Reusable filter chip row — every filter is a small <select>. */
export function FilterBar({ filters, onReset, children, className }: FilterBarProps) {
  const { t } = useTranslation();
  const hasActive = filters.some((f) => f.value);

  return (
    <div className={cn('flex items-center gap-2 flex-wrap', className)}>
      {filters.map((f) => (
        <label key={f.key} className="inline-flex items-center gap-1 text-xs text-text-secondary">
          <span>{f.label}:</span>
          <select
            value={f.value ?? ''}
            onChange={(e) => f.onChange(e.target.value === '' ? null : e.target.value)}
            className="h-8 px-2 border border-border-strong rounded-md bg-surface text-text-primary text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
            aria-label={f.label}
          >
            <option value="">{t('common.all')}</option>
            {f.options.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        </label>
      ))}
      {children}
      {hasActive && onReset ? (
        <Button variant="ghost" size="sm" leadingIcon={<X size={12} />} onClick={onReset}>
          {t('common.reset_filters')}
        </Button>
      ) : null}
    </div>
  );
}
