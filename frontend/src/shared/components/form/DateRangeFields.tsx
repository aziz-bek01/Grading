/**
 * Shared from/to date-range control.
 *
 * Extracted from `AuditFilterBar.tsx` (the original, shipped from/to
 * date-range pattern: two native `<input type="date">` controlled inputs,
 * `value.slice(0, 10)` normalisation, `onChange` emitting a partial
 * `{ from?: string; to?: string }` patch) so the audit filter bar AND the
 * report request dialog's EVALUATION_SUMMARY filter panel share ONE
 * implementation instead of two copies of the same JSX.
 *
 * Callers own the surrounding state; this component is fully controlled.
 * `testIdPrefix` defaults to the original `audit-filter-*` convention so the
 * audit filter bar keeps its existing data-testids untouched after
 * extraction; new call sites (e.g. the report dialog) pass their own prefix.
 */
import { useTranslation } from 'react-i18next';

export interface DateRangeValue {
  from?: string;
  to?: string;
}

interface DateRangeFieldsProps {
  value: DateRangeValue;
  onChange: (next: DateRangeValue) => void;
  /** data-testid / aria-label prefix, e.g. `audit-filter` -> `audit-filter-from`. */
  testIdPrefix?: string;
  /** i18n key for the "from" label. Defaults to the audit filter bar's key. */
  fromLabelKey?: string;
  /** i18n key for the "to" label. Defaults to the audit filter bar's key. */
  toLabelKey?: string;
}

export function DateRangeFields({
  value,
  onChange,
  testIdPrefix = 'audit-filter',
  fromLabelKey = 'audit.filter.from',
  toLabelKey = 'audit.filter.to',
}: DateRangeFieldsProps) {
  const { t } = useTranslation();

  return (
    <>
      <label className="inline-flex items-center gap-1 text-xs text-text-secondary">
        <span>{t(fromLabelKey)}:</span>
        <input
          type="date"
          value={(value.from ?? '').slice(0, 10)}
          onChange={(e) => onChange({ ...value, from: e.target.value || undefined })}
          aria-label={t(fromLabelKey)}
          data-testid={`${testIdPrefix}-from`}
          className="h-8 px-2 border border-border-strong rounded-md bg-surface text-text-primary text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
        />
      </label>
      <label className="inline-flex items-center gap-1 text-xs text-text-secondary">
        <span>{t(toLabelKey)}:</span>
        <input
          type="date"
          value={(value.to ?? '').slice(0, 10)}
          onChange={(e) => onChange({ ...value, to: e.target.value || undefined })}
          aria-label={t(toLabelKey)}
          data-testid={`${testIdPrefix}-to`}
          className="h-8 px-2 border border-border-strong rounded-md bg-surface text-text-primary text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
        />
      </label>
    </>
  );
}
