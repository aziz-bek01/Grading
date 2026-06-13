import { useTranslation } from 'react-i18next';
import { ArrowDown, ArrowUp, Pencil, Plus, Trash2 } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { StatusBadge } from '@/shared/components/status/StatusBadge';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import { cn } from '@/shared/lib/cn';
import { SUPPORTED_LOCALES } from '@/shared/types/i18n';
import type { Locale } from '@/shared/types/common';
import type { Factor, ScoringMode } from '../types';

interface FactorTableProps {
  factors: Factor[];
  scoringMode: ScoringMode;
  currentLocale: Locale;
  readOnly?: boolean;
  onEdit?: (factor: Factor) => void;
  onRemove?: (factor: Factor) => void;
  onReorder?: (factor: Factor, direction: 'up' | 'down') => void;
  onAdd?: () => void;
}

/**
 * Sortable factor grid:
 *   ORDER | CODE | NAME (current locale + 4-locale completeness dots)
 *   WEIGHT | MAX POINTS | LEVELS | REQUIRED | ACTIONS
 *
 * Drag-and-drop is intentionally deferred to MVP 2; up / down arrow buttons
 * deliver reorder UX today (per design-foundation §13 "drag handle or up/down buttons").
 */
export function FactorTable({
  factors,
  scoringMode,
  currentLocale,
  readOnly = false,
  onEdit,
  onRemove,
  onReorder,
  onAdd,
}: FactorTableProps) {
  const { t } = useTranslation();
  const sorted = [...factors].sort((a, b) => a.sort_order - b.sort_order);

  return (
    <div className="bg-surface border border-border rounded-lg overflow-hidden" data-testid="factor-table">
      <table className="w-full text-sm">
        <caption className="sr-only">{t('methodology.factor_table_caption')}</caption>
        <thead className="bg-divider text-text-secondary text-xs uppercase tracking-wide">
          <tr>
            <th scope="col" className="text-left px-3 py-3 font-medium w-16">
              {t('factor.column.order')}
            </th>
            <th scope="col" className="text-left px-3 py-3 font-medium w-24">
              {t('factor.column.code')}
            </th>
            <th scope="col" className="text-left px-3 py-3 font-medium">
              {t('factor.column.name')}
            </th>
            {scoringMode !== 'DIRECT_POINTS' ? (
              <th scope="col" className="text-right px-3 py-3 font-medium w-24 tabular-nums">
                {t('factor.column.weight')}
              </th>
            ) : null}
            <th scope="col" className="text-right px-3 py-3 font-medium w-28 tabular-nums">
              {t('factor.column.max_points')}
            </th>
            <th scope="col" className="text-right px-3 py-3 font-medium w-20 tabular-nums">
              {t('factor.column.levels')}
            </th>
            <th scope="col" className="text-center px-3 py-3 font-medium w-24">
              {t('factor.column.required')}
            </th>
            <th scope="col" className="text-right px-3 py-3 font-medium w-40">
              {t('factor.column.actions')}
            </th>
          </tr>
        </thead>
        <tbody>
          {sorted.length === 0 ? (
            <tr>
              <td colSpan={scoringMode !== 'DIRECT_POINTS' ? 8 : 7} className="px-3 py-8 text-center text-text-secondary">
                {t('methodology.no_factors')}
              </td>
            </tr>
          ) : (
            sorted.map((f, idx) => {
              const name = f.name_i18n?.[currentLocale] ?? f.name_i18n?.['ru-RU'] ?? '—';
              const deprecated = !!f.deprecated_at;
              return (
                <tr
                  key={f.id}
                  className={cn(
                    'border-t border-border',
                    !readOnly && 'hover:bg-divider/40',
                    deprecated && 'opacity-60',
                  )}
                  data-testid={`factor-row-${f.code}`}
                  data-deprecated={deprecated ? 'true' : undefined}
                >
                  <td className="px-3 py-3 text-text-secondary tabular-nums">{idx + 1}</td>
                  <td className="px-3 py-3 font-mono text-xs text-text-secondary">{f.code}</td>
                  <td className="px-3 py-3 text-text-primary">
                    <div className="flex flex-col gap-1">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span>{name}</span>
                        {deprecated ? (
                          <StatusBadge
                            tone="archived"
                            label={t('methodology.deprecated_badge')}
                            outline
                            className="text-[10px]"
                          />
                        ) : null}
                      </div>
                      <div className="inline-flex items-center gap-1.5" aria-label={t('common.completeness')}>
                        {SUPPORTED_LOCALES.map((loc) => {
                          const filled = !!f.name_i18n?.[loc] && f.name_i18n[loc]!.trim().length > 0;
                          return (
                            <span
                              key={loc}
                              data-testid={`factor-${f.code}-locale-${loc}-${filled ? 'filled' : 'empty'}`}
                              title={`${loc}: ${filled ? '✓' : '—'}`}
                              className={cn(
                                'inline-flex items-center gap-0.5 text-[10px] text-text-muted',
                                filled && 'text-success-700',
                              )}
                            >
                              <span
                                className={cn(
                                  'w-1.5 h-1.5 rounded-full',
                                  filled ? 'bg-success-500' : 'bg-border-strong',
                                )}
                              />
                              {loc.split('-')[0].toUpperCase()}
                            </span>
                          );
                        })}
                      </div>
                    </div>
                  </td>
                  {scoringMode !== 'DIRECT_POINTS' ? (
                    <td className="px-3 py-3 text-right tabular-nums">{f.weight.toString()}</td>
                  ) : null}
                  <td className="px-3 py-3 text-right tabular-nums">{f.max_points.toString()}</td>
                  <td className="px-3 py-3 text-right tabular-nums">{f.levels?.length ?? 0}</td>
                  <td className="px-3 py-3 text-center">
                    {f.required ? (
                      <span className="text-success-700 text-xs">{t('common.yes')}</span>
                    ) : (
                      <span className="text-text-muted text-xs">{t('common.no')}</span>
                    )}
                  </td>
                  <td className="px-3 py-3 text-right">
                    {readOnly ? (
                      <span className="text-xs text-text-muted">{t('methodology.read_only')}</span>
                    ) : (
                      <div className="inline-flex items-center gap-1">
                        <Button
                          variant="ghost"
                          size="sm"
                          aria-label={t('factor.action.move_up')}
                          onClick={() => onReorder?.(f, 'up')}
                          disabled={idx === 0}
                          data-testid={`factor-${f.code}-move-up`}
                        >
                          <ArrowUp size={14} />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          aria-label={t('factor.action.move_down')}
                          onClick={() => onReorder?.(f, 'down')}
                          disabled={idx === sorted.length - 1}
                          data-testid={`factor-${f.code}-move-down`}
                        >
                          <ArrowDown size={14} />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          aria-label={t('factor.action.edit')}
                          onClick={() => onEdit?.(f)}
                          data-testid={`factor-${f.code}-edit`}
                        >
                          <Pencil size={14} />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          aria-label={t('factor.action.remove')}
                          onClick={() => onRemove?.(f)}
                          data-testid={`factor-${f.code}-remove`}
                        >
                          <Trash2 size={14} className="text-danger-700" />
                        </Button>
                      </div>
                    )}
                  </td>
                </tr>
              );
            })
          )}
        </tbody>
      </table>

      {!readOnly ? (
        <div className="p-3 border-t border-border bg-divider/30">
          <PermissionGate permission={PERMISSIONS.METHODOLOGY_EDIT}>
            <Button
              variant="secondary"
              size="sm"
              onClick={onAdd}
              leadingIcon={<Plus size={14} />}
              data-testid="factor-add"
            >
              {t('methodology.add_factor')}
            </Button>
          </PermissionGate>
        </div>
      ) : null}
    </div>
  );
}
