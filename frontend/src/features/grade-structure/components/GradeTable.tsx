import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { ArrowDown, ArrowUp, Pencil, Plus, Trash2 } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import { SUPPORTED_LOCALES } from '@/shared/types/i18n';
import { cn } from '@/shared/lib/cn';
import { pickLocalized } from '@/shared/lib/localized';
import type { Locale } from '@/shared/types/common';
import { validateBands } from '../lib/bandValidation';
import type { Grade } from '../types';

interface GradeTableProps {
  grades: Grade[];
  currentLocale: Locale;
  readOnly?: boolean;
  onEdit?: (g: Grade) => void;
  onRemove?: (g: Grade) => void;
  onAdd?: () => void;
  /** DRAFT-only reorder by sort_order. Disabled while a reorder is in flight. */
  onMove?: (g: Grade, direction: 'up' | 'down') => void;
  reordering?: boolean;
}

/**
 * Editable grade table. Columns:
 *   # | GRADE NUMBER | NAME (+ completeness dots) | MIN | MAX | WIDTH | VALIDATION | ACTIONS
 *
 * - Width column shows (max - min) for the band, helping visually identify
 *   narrow vs. wide grades.
 * - Validation pip: red on overlap, yellow on gap-with-neighbour, green
 *   otherwise. Mirrors the GradeBandValidator banner per-row.
 */
export function GradeTable({
  grades,
  currentLocale,
  readOnly = false,
  onEdit,
  onRemove,
  onAdd,
  onMove,
  reordering = false,
}: GradeTableProps) {
  const { t } = useTranslation();
  // Display order follows persisted `sort_order` (falls back to grade_number) so
  // the up/down reorder controls map 1:1 to the row positions the user sees.
  const sorted = useMemo(
    () =>
      [...grades].sort(
        (a, b) => a.sort_order - b.sort_order || a.grade_number - b.grade_number,
      ),
    [grades],
  );

  const report = useMemo(() => validateBands(grades), [grades]);

  const overlapGradeNumbers = useMemo(() => {
    const set = new Set<number>();
    for (const o of report.overlaps) {
      set.add(o.aGradeNumber);
      set.add(o.bGradeNumber);
    }
    return set;
  }, [report.overlaps]);

  const gapNeighbourGradeNumbers = useMemo(() => {
    const set = new Set<number>();
    for (const g of report.gaps) {
      set.add(g.belowGradeNumber);
      set.add(g.aboveGradeNumber);
    }
    return set;
  }, [report.gaps]);

  return (
    <div
      className="bg-surface border border-border rounded-lg overflow-hidden"
      data-testid="grade-table"
    >
      <table className="w-full text-sm">
        <caption className="sr-only">{t('gradeStructure.table.caption')}</caption>
        <thead className="bg-divider text-text-secondary text-xs uppercase tracking-wide">
          <tr>
            <th scope="col" className="text-left px-3 py-3 font-medium w-12">
              {t('gradeStructure.table.col_idx')}
            </th>
            <th scope="col" className="text-left px-3 py-3 font-medium w-20">
              {t('gradeStructure.table.col_grade_number')}
            </th>
            <th scope="col" className="text-left px-3 py-3 font-medium">
              {t('gradeStructure.table.col_name')}
            </th>
            <th scope="col" className="text-right px-3 py-3 font-medium w-28 tabular-nums">
              {t('gradeStructure.table.col_min_score')}
            </th>
            <th scope="col" className="text-right px-3 py-3 font-medium w-28 tabular-nums">
              {t('gradeStructure.table.col_max_score')}
            </th>
            <th scope="col" className="text-right px-3 py-3 font-medium w-24 tabular-nums">
              {t('gradeStructure.table.col_width')}
            </th>
            <th scope="col" className="text-left px-3 py-3 font-medium w-32">
              {t('gradeStructure.table.col_validation')}
            </th>
            {!readOnly ? (
              <th scope="col" className="text-right px-3 py-3 font-medium w-28">
                {t('gradeStructure.table.col_actions')}
              </th>
            ) : null}
          </tr>
        </thead>
        <tbody>
          {sorted.length === 0 ? (
            <tr>
              <td
                colSpan={readOnly ? 7 : 8}
                className="px-3 py-8 text-center text-text-secondary"
              >
                {t('gradeStructure.table.empty')}
              </td>
            </tr>
          ) : (
            sorted.map((g, idx) => {
              const name = pickLocalized(g.name_i18n, currentLocale) || '—';
              const min = g.band?.min_score ?? null;
              const max = g.band?.max_score ?? null;
              const width = min != null && max != null ? max - min : null;
              const isOverlap = overlapGradeNumbers.has(g.grade_number);
              const isGap = gapNeighbourGradeNumbers.has(g.grade_number);
              const validationLabel = !g.band
                ? t('gradeStructure.table.validation_missing')
                : isOverlap
                  ? t('gradeStructure.table.validation_overlap')
                  : isGap
                    ? t('gradeStructure.table.validation_gap')
                    : t('gradeStructure.table.validation_ok');
              const validationTone = !g.band
                ? 'text-warning-700 bg-warning-50 border-warning-500/30'
                : isOverlap
                  ? 'text-danger-700 bg-danger-50 border-danger-500/30'
                  : isGap
                    ? 'text-warning-700 bg-warning-50 border-warning-500/30'
                    : 'text-success-700 bg-success-50 border-success-500/30';

              return (
                <tr
                  key={g.id}
                  className={cn('border-t border-border', !readOnly && 'hover:bg-divider/40')}
                  data-testid={`grade-row-${g.grade_number}`}
                >
                  <td className="px-3 py-3 text-text-secondary tabular-nums">{idx + 1}</td>
                  <td className="px-3 py-3 font-semibold text-text-primary tabular-nums">
                    G{g.grade_number}
                  </td>
                  <td className="px-3 py-3 text-text-primary">
                    <div className="flex flex-col gap-1">
                      <span>{name}</span>
                      <div
                        className="inline-flex items-center gap-1.5"
                        aria-label={t('common.completeness')}
                      >
                        {SUPPORTED_LOCALES.map((loc) => {
                          const filled =
                            !!g.name_i18n?.[loc] && g.name_i18n[loc]!.trim().length > 0;
                          return (
                            <span
                              key={loc}
                              data-testid={`grade-${g.grade_number}-locale-${loc}-${filled ? 'filled' : 'empty'}`}
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
                  <td className="px-3 py-3 text-right tabular-nums">
                    {min != null ? min.toFixed(4) : '—'}
                  </td>
                  <td className="px-3 py-3 text-right tabular-nums">
                    {max != null ? max.toFixed(4) : '—'}
                  </td>
                  <td className="px-3 py-3 text-right tabular-nums">
                    {width != null ? width.toFixed(4) : '—'}
                  </td>
                  <td className="px-3 py-3">
                    <span
                      className={cn(
                        'inline-flex items-center gap-1 text-[11px] font-medium px-2 py-0.5 rounded-full border',
                        validationTone,
                      )}
                      data-testid={`grade-${g.grade_number}-validation`}
                    >
                      {validationLabel}
                    </span>
                  </td>
                  {!readOnly ? (
                    <td className="px-3 py-3 text-right">
                      <div className="inline-flex items-center gap-1">
                        {onMove ? (
                          <PermissionGate permission={PERMISSIONS.GRADE_EDIT}>
                            <Button
                              variant="ghost"
                              size="sm"
                              aria-label={t('gradeStructure.table.action_move_up')}
                              disabled={reordering || idx === 0}
                              onClick={() => onMove(g, 'up')}
                              data-testid={`grade-${g.grade_number}-move-up`}
                            >
                              <ArrowUp size={14} />
                            </Button>
                            <Button
                              variant="ghost"
                              size="sm"
                              aria-label={t('gradeStructure.table.action_move_down')}
                              disabled={reordering || idx === sorted.length - 1}
                              onClick={() => onMove(g, 'down')}
                              data-testid={`grade-${g.grade_number}-move-down`}
                            >
                              <ArrowDown size={14} />
                            </Button>
                          </PermissionGate>
                        ) : null}
                        <Button
                          variant="ghost"
                          size="sm"
                          aria-label={t('gradeStructure.table.action_edit')}
                          onClick={() => onEdit?.(g)}
                          data-testid={`grade-${g.grade_number}-edit`}
                        >
                          <Pencil size={14} />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          aria-label={t('gradeStructure.table.action_remove')}
                          onClick={() => onRemove?.(g)}
                          data-testid={`grade-${g.grade_number}-remove`}
                        >
                          <Trash2 size={14} className="text-danger-700" />
                        </Button>
                      </div>
                    </td>
                  ) : null}
                </tr>
              );
            })
          )}
        </tbody>
      </table>

      {!readOnly ? (
        <div className="p-3 border-t border-border bg-divider/30">
          <PermissionGate permission={PERMISSIONS.GRADE_EDIT}>
            <Button
              variant="secondary"
              size="sm"
              onClick={onAdd}
              leadingIcon={<Plus size={14} />}
              data-testid="grade-table-add"
            >
              {t('gradeStructure.table.add_grade')}
            </Button>
          </PermissionGate>
        </div>
      ) : null}
    </div>
  );
}
