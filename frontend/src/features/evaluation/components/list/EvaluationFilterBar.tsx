import { useTranslation } from 'react-i18next';
import { X } from 'lucide-react';
import { cn } from '@/shared/lib/cn';
import type { TableDensity } from '../../pages/useEvaluationListState';
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

interface EvaluationFilterBarProps {
  statusFilter: EvaluationStatus | '';
  chipIncomplete: boolean;
  onStatusChange: (value: EvaluationStatus | '') => void;
  methodologyFilter: string;
  onMethodologyChange: (value: string) => void;
  methodologyMap: Map<string, string>;
  density: TableDensity;
  onDensityToggle: () => void;
  chipMine: boolean;
  showMineChip: boolean;
  onToggleChip: (chip: 'incomplete' | 'mine', currentValue: boolean) => void;
  anyChipActive: boolean;
  onClearChips: () => void;
}

/**
 * By-position table's filter bar: status/methodology dropdowns, density
 * toggle, and the "only incomplete" / "only mine" quick-filter chips.
 * Extracted from `EvaluationListPage` (FE-041) — rendered as `DataTable`'s
 * `filterBar` slot; unchanged behaviour and testids.
 */
export function EvaluationFilterBar({
  statusFilter,
  chipIncomplete,
  onStatusChange,
  methodologyFilter,
  onMethodologyChange,
  methodologyMap,
  density,
  onDensityToggle,
  chipMine,
  showMineChip,
  onToggleChip,
  anyChipActive,
  onClearChips,
}: EvaluationFilterBarProps) {
  const { t } = useTranslation();

  return (
    <div className="flex flex-col gap-2">
      {/* Dropdowns row */}
      <div className="flex items-center gap-2 flex-wrap">
        <select
          aria-label={t('evaluation.filter.status')}
          value={chipIncomplete ? '' : statusFilter}
          onChange={(e) => onStatusChange(e.target.value as EvaluationStatus | '')}
          disabled={chipIncomplete}
          data-testid="filter-status"
          className="h-9 px-3 border border-border-strong rounded-md text-sm bg-surface disabled:opacity-50"
        >
          <option value="">{t('common.all')}</option>
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {t(`evaluation.status.${s.toLowerCase()}`)}
            </option>
          ))}
        </select>
        <select
          aria-label={t('evaluation.filter.methodology')}
          value={methodologyFilter}
          onChange={(e) => onMethodologyChange(e.target.value)}
          data-testid="filter-methodology"
          className="h-9 px-3 border border-border-strong rounded-md text-sm bg-surface"
        >
          <option value="">{t('common.all')}</option>
          {Array.from(methodologyMap.entries()).map(([id, name]) => (
            <option key={id} value={id}>
              {name}
            </option>
          ))}
        </select>

        {/* Density toggle */}
        <button
          type="button"
          onClick={onDensityToggle}
          data-testid="density-toggle"
          title={t(
            density === 'comfortable'
              ? 'evaluation.filter.density_compact'
              : 'evaluation.filter.density_comfortable',
          )}
          className={cn(
            'h-9 px-3 border rounded-md text-sm transition-colors',
            density === 'compact'
              ? 'bg-primary-50 border-primary-400 text-primary-700'
              : 'bg-surface border-border-strong text-text-secondary hover:text-text-primary',
          )}
        >
          {t(
            density === 'comfortable'
              ? 'evaluation.filter.density_compact'
              : 'evaluation.filter.density_comfortable',
          )}
        </button>
      </div>

      {/* Quick-filter chips row — flex-wrap so Uzbek labels don't overflow */}
      <div className="flex flex-wrap items-center gap-2">
        {/* "Only incomplete" chip — backend `status` param supported */}
        <button
          type="button"
          onClick={() => onToggleChip('incomplete', chipIncomplete)}
          aria-pressed={chipIncomplete}
          data-testid="chip-only-incomplete"
          className={cn(
            'inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs border transition-colors',
            chipIncomplete
              ? 'bg-warning-100 border-warning-400 text-warning-800 font-medium'
              : 'bg-surface border-border-strong text-text-secondary hover:border-primary-400 hover:text-primary-700',
          )}
        >
          {t('evaluation.filter.chip_incomplete')}
          {chipIncomplete ? <X size={12} aria-hidden /> : null}
        </button>

        {/* "Only mine" chip — backend `evaluatorUserId` param supported
            (EvaluationController.java listById @RequestParam evaluatorUserId) */}
        {showMineChip ? (
          <button
            type="button"
            onClick={() => onToggleChip('mine', chipMine)}
            aria-pressed={chipMine}
            data-testid="chip-only-mine"
            className={cn(
              'inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs border transition-colors',
              chipMine
                ? 'bg-primary-100 border-primary-400 text-primary-800 font-medium'
                : 'bg-surface border-border-strong text-text-secondary hover:border-primary-400 hover:text-primary-700',
            )}
          >
            {t('evaluation.filter.chip_mine')}
            {chipMine ? <X size={12} aria-hidden /> : null}
          </button>
        ) : null}

        {/* "Clear filters" — shown when any chip is active */}
        {anyChipActive ? (
          <button
            type="button"
            onClick={onClearChips}
            data-testid="chip-clear-filters"
            className="text-xs text-text-muted hover:text-text-primary underline"
          >
            {t('evaluation.filter.clear_chips')}
          </button>
        ) : null}
      </div>
    </div>
  );
}
