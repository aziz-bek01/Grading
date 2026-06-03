import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '@/features/auth/authStore';
import { cn } from '@/shared/lib/cn';
import { pickLocalized } from '@/shared/lib/localized';
import type { Locale } from '@/shared/types/common';
import type { GradePyramidRow } from '../types';

interface GradePyramidProps {
  rows: GradePyramidRow[];
  /** When true, also render evaluation counts under each bar. */
  showEvaluationCount?: boolean;
}

/**
 * Vertical "pyramid" visualization. Top = highest grade (e.g. G14/G16);
 * bottom = G1. Each row's bar width is proportional to position_count.
 *
 * - Empty grades render with "0 positions" placeholder.
 * - Hover tooltip shows full grade name + score band range.
 * - No salary data — salary widgets ship Phase 7+ only when permitted.
 */
export function GradePyramid({ rows, showEvaluationCount = true }: GradePyramidProps) {
  const { t, i18n } = useTranslation();
  const currentLocale = (useAuthStore((s) => s.user?.locale) ??
    (i18n.language as Locale)) as Locale;

  // Sort high → low
  const sorted = useMemo(
    () => [...rows].sort((a, b) => b.grade_number - a.grade_number),
    [rows],
  );

  const maxCount = useMemo(() => {
    let m = 0;
    for (const r of rows) if (r.position_count > m) m = r.position_count;
    return Math.max(1, m);
  }, [rows]);

  if (rows.length === 0) {
    return (
      <div
        data-testid="grade-pyramid-empty"
        className="rounded-lg border border-border bg-divider/30 px-6 py-8 text-center text-sm text-text-secondary"
      >
        {t('gradeStructure.pyramid.empty')}
      </div>
    );
  }

  return (
    <div
      role="figure"
      aria-label={t('gradeStructure.pyramid.aria_label')}
      data-testid="grade-pyramid"
      className="space-y-1.5"
    >
      {sorted.map((row, idx) => {
        const widthPercent = Math.max(6, (row.position_count / maxCount) * 100);
        // gradient: top rows darker, bottom lighter
        const intensity = sorted.length === 1 ? 0 : idx / (sorted.length - 1);
        const bandLabel =
          row.min_score != null && row.max_score != null
            ? `[${row.min_score.toFixed(4)}, ${row.max_score.toFixed(4)}]`
            : '';
        const name = pickLocalized(row.grade_name, currentLocale) || `G${row.grade_number}`;

        return (
          <div
            key={row.grade_id}
            data-testid={`pyramid-row-${row.grade_number}`}
            data-position-count={row.position_count}
            className="flex items-center gap-3"
            title={`${name} ${bandLabel}`.trim()}
          >
            <span className="w-12 shrink-0 text-right text-xs text-text-secondary tabular-nums">
              G{row.grade_number}
            </span>
            <div className="flex-1 min-w-0">
              <div
                className={cn(
                  'h-7 rounded-md flex items-center px-2 text-xs font-medium text-text-inverse transition-all',
                  row.position_count === 0
                    ? 'bg-divider text-text-secondary border border-border-strong'
                    : '',
                )}
                style={
                  row.position_count > 0
                    ? {
                        width: `${widthPercent}%`,
                        // approximate gradient: navy primary at top, slate at bottom
                        background: `linear-gradient(90deg, hsl(${214 - intensity * 20}, ${64 - intensity * 20}%, ${30 + intensity * 30}%), hsl(${214 - intensity * 20}, ${50 - intensity * 25}%, ${40 + intensity * 35}%))`,
                      }
                    : { width: '14rem' }
                }
              >
                <span className="truncate">{name}</span>
                {row.position_count > 0 ? (
                  <span className="ml-auto pl-2 tabular-nums">
                    {row.position_count}
                  </span>
                ) : (
                  <span className="ml-auto pl-2 text-xs tabular-nums">
                    {t('gradeStructure.pyramid.zero_positions')}
                  </span>
                )}
              </div>
              {showEvaluationCount && row.evaluation_count > 0 ? (
                <p className="text-[10px] text-text-muted mt-0.5 ml-1 tabular-nums">
                  {t('gradeStructure.pyramid.evaluation_count', {
                    count: row.evaluation_count,
                  })}
                </p>
              ) : null}
            </div>
          </div>
        );
      })}
    </div>
  );
}
