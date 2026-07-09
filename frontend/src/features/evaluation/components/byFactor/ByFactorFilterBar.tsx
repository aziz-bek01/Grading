import { useTranslation } from 'react-i18next';
import type { EvaluationStatus } from '../../types';

const STATUSES: EvaluationStatus[] = [
  'DRAFT',
  'INCOMPLETE',
  'COMPLETE',
  'SUBMITTED',
  'APPROVED',
  'LOCKED',
  'ARCHIVED',
];

interface ByFactorFilterBarProps {
  departmentId: string;
  onDepartmentChange: (value: string) => void;
  departmentOptions: { id: string; label: string }[];
  statusFilter: EvaluationStatus | '';
  onStatusChange: (value: EvaluationStatus | '') => void;
  onlyUnfilled: boolean;
  onOnlyUnfilledChange: (value: boolean) => void;
  search: string;
  onSearchChange: (value: string) => void;
}

/**
 * K-sheet filter bar: department / status / "only unfilled" / search.
 * Extracted from `EvaluationByFactorView` (FE-041) — rendered inside the
 * sticky region's `Card`; unchanged behaviour and testids.
 */
export function ByFactorFilterBar({
  departmentId,
  onDepartmentChange,
  departmentOptions,
  statusFilter,
  onStatusChange,
  onlyUnfilled,
  onOnlyUnfilledChange,
  search,
  onSearchChange,
}: ByFactorFilterBarProps) {
  const { t } = useTranslation();

  return (
    <div className="flex flex-wrap items-center gap-2">
      <select
        aria-label={t('evaluation.byFactor.filter.department')}
        value={departmentId}
        onChange={(e) => onDepartmentChange(e.target.value)}
        data-testid="byfactor-filter-department"
        className="h-9 px-3 border border-border-strong rounded-md text-sm bg-surface"
      >
        <option value="">
          {t('evaluation.byFactor.filter.department')}: {t('common.all')}
        </option>
        {departmentOptions.map((d) => (
          <option key={d.id} value={d.id}>
            {d.label}
          </option>
        ))}
      </select>
      <select
        aria-label={t('evaluation.byFactor.filter.status')}
        value={statusFilter}
        onChange={(e) => onStatusChange(e.target.value as EvaluationStatus | '')}
        data-testid="byfactor-filter-status"
        className="h-9 px-3 border border-border-strong rounded-md text-sm bg-surface"
      >
        <option value="">
          {t('evaluation.byFactor.filter.status')}: {t('common.all')}
        </option>
        {STATUSES.map((s) => (
          <option key={s} value={s}>
            {t(`evaluation.status.${s.toLowerCase()}`)}
          </option>
        ))}
      </select>
      <label className="inline-flex items-center gap-1.5 text-sm text-text-secondary">
        <input
          type="checkbox"
          checked={onlyUnfilled}
          onChange={(e) => onOnlyUnfilledChange(e.target.checked)}
          data-testid="byfactor-filter-only-unfilled"
          className="h-4 w-4 accent-primary-500"
        />
        {t('evaluation.byFactor.filter.only_unfilled')}
      </label>
      <input
        type="search"
        value={search}
        onChange={(e) => onSearchChange(e.target.value)}
        placeholder={t('common.search')}
        data-testid="byfactor-filter-search"
        className="h-9 px-3 border border-border-strong rounded-md text-sm bg-surface flex-1 min-w-[200px] max-w-md"
      />
    </div>
  );
}
