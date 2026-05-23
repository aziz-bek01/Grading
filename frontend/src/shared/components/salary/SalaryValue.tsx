import { useTranslation } from 'react-i18next';
import { Lock } from 'lucide-react';
import { usePermission } from '@/features/auth/usePermission';
import { cn } from '@/shared/lib/cn';

interface SalaryValueProps {
  /**
   * Numeric salary value. Backend OMITS this field (per security blueprint §8)
   * when the caller lacks SALARY_VIEW. Frontend never receives masked stubs from server.
   */
  value?: number;
  currency?: string;
  /** If true, display value with mask even when SALARY_VIEW exists (export previews, etc). */
  forceMasked?: boolean;
  className?: string;
}

/**
 * Salary rendering contract (design-foundation §11):
 *  - visible: number with locale formatting + tabular figures (requires SALARY_VIEW + value).
 *  - masked: '••••' (when forceMasked).
 *  - permission-required: lock icon + "Salary access required" (default in MVP 1).
 *
 * HARD RULES:
 *   - we NEVER log the salary value, never put it in chart tooltips outside this component,
 *   - never persist to localStorage/sessionStorage.
 */
export function SalaryValue({ value, currency = 'UZS', forceMasked = false, className }: SalaryValueProps) {
  const { t, i18n } = useTranslation();
  const { canViewSalary } = usePermission();

  if (!canViewSalary() || value === undefined || value === null) {
    return (
      <span
        className={cn('inline-flex items-center gap-1.5 text-text-muted text-sm', className)}
        data-state="permission-required"
      >
        <Lock size={14} aria-hidden />
        <span>{t('salary.access_required')}</span>
      </span>
    );
  }

  if (forceMasked) {
    return (
      <span
        className={cn('inline-flex items-center text-salary-sensitive font-medium', className)}
        data-state="masked"
      >
        {t('salary.masked')}
      </span>
    );
  }

  const formatted = new Intl.NumberFormat(i18n.language, { maximumFractionDigits: 0 }).format(value);
  return (
    <span
      className={cn('font-medium text-text-primary tabular-nums', className)}
      data-state="visible"
    >
      {formatted} {currency}
    </span>
  );
}
