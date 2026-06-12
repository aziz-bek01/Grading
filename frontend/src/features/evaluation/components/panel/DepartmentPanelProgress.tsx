/**
 * Per-department panel coverage strip (FE-7).
 *
 * A DERIVED column only — no new endpoint. Coverage is computed from data the
 * EvaluationListPage already loads: the department tree (useDepartmentTree),
 * the project positions, and the project panels (GET /panels). For each
 * department it shows "X of Y positions paneled" where Y is the count of
 * non-archived positions in the department and X is the count of those covered
 * by at least one active (non-ARCHIVED) panel.
 *
 * ABAC: the GET /panels response is server-scoped, so a department director
 * sees only their own department's panels — this component never adds FE-only
 * hiding; it simply reflects what the server returned.
 */
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { Card } from '@/shared/components/ui/Card';
import { cn } from '@/shared/lib/cn';
import { pickLocalized } from '@/shared/lib/localized';
import type { Department } from '@/features/organization/types/organizationTypes';
import type { Position } from '@/features/positions/types/positionTypes';
import type { Panel } from '../../panelTypes';

interface Props {
  departments: Department[];
  positions: Position[];
  panels: Panel[];
}

interface DeptCoverage {
  id: string;
  code: string;
  name: string;
  total: number;
  paneled: number;
}

export function DepartmentPanelProgress({ departments, positions, panels }: Props) {
  const { t, i18n } = useTranslation();
  const [expanded, setExpanded] = useState(false);

  // position_id -> covered by an active panel?
  const paneledPositionIds = useMemo(() => {
    const set = new Set<string>();
    for (const p of panels) {
      if (p.status === 'ARCHIVED') continue;
      set.add(p.position_id);
    }
    return set;
  }, [panels]);

  const rows = useMemo<DeptCoverage[]>(() => {
    const byDept = new Map<string, { total: number; paneled: number }>();
    for (const pos of positions) {
      if (pos.status === 'ARCHIVED') continue;
      const acc = byDept.get(pos.department_id) ?? { total: 0, paneled: 0 };
      acc.total += 1;
      if (paneledPositionIds.has(pos.id)) acc.paneled += 1;
      byDept.set(pos.department_id, acc);
    }
    return departments
      .filter((d) => d.status !== 'ARCHIVED' && byDept.has(d.id))
      .map((d) => {
        const acc = byDept.get(d.id)!;
        return {
          id: d.id,
          code: d.code,
          name: pickLocalized(d.name_i18n, i18n.language),
          total: acc.total,
          paneled: acc.paneled,
        };
      })
      .sort((a, b) => a.code.localeCompare(b.code));
  }, [departments, positions, paneledPositionIds, i18n.language]);

  if (rows.length === 0) return null;

  const visible = expanded ? rows : rows.slice(0, 6);
  const totalPaneled = rows.reduce((s, r) => s + r.paneled, 0);
  const totalPositions = rows.reduce((s, r) => s + r.total, 0);

  return (
    <Card>
      <div className="flex items-center justify-between mb-3">
        <h2 className="text-sm font-medium text-text-primary">
          {t('panel.dept_progress.title')}
        </h2>
        <span className="text-xs text-text-muted tabular-nums" data-testid="dept-progress-overall">
          {t('panel.dept_progress.coverage', {
            paneled: totalPaneled,
            total: totalPositions,
          })}
        </span>
      </div>
      <ul className="space-y-2" data-testid="dept-progress-list">
        {visible.map((r) => {
          const pct = r.total > 0 ? Math.round((r.paneled / r.total) * 100) : 0;
          const full = r.total > 0 && r.paneled >= r.total;
          return (
            <li
              key={r.id}
              className="flex items-center gap-3 text-sm"
              data-testid={`dept-progress-${r.code}`}
            >
              <span className="w-40 shrink-0 truncate">
                <span className="font-mono text-xs text-text-muted mr-1.5">{r.code}</span>
                {r.name}
              </span>
              <span className="flex-1 h-2 rounded-full bg-divider overflow-hidden">
                <span
                  className={cn(
                    'block h-full rounded-full',
                    full ? 'bg-success-500' : 'bg-primary-500',
                  )}
                  style={{ width: `${pct}%` }}
                />
              </span>
              <span
                className="w-28 shrink-0 text-right text-xs text-text-secondary tabular-nums"
                data-testid={`dept-progress-count-${r.code}`}
              >
                {t('panel.dept_progress.coverage', {
                  paneled: r.paneled,
                  total: r.total,
                })}
              </span>
            </li>
          );
        })}
      </ul>
      {rows.length > 6 ? (
        <button
          type="button"
          onClick={() => setExpanded((v) => !v)}
          data-testid="dept-progress-toggle"
          className="mt-3 inline-flex items-center gap-1 text-xs text-primary-600 hover:underline"
        >
          {expanded ? (
            <>
              <ChevronUp size={14} aria-hidden /> {t('panel.dept_progress.show_less')}
            </>
          ) : (
            <>
              <ChevronDown size={14} aria-hidden />{' '}
              {t('panel.dept_progress.show_all', { count: rows.length })}
            </>
          )}
        </button>
      ) : null}
    </Card>
  );
}
