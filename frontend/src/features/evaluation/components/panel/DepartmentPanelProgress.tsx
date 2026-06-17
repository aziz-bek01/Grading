/**
 * Per-department panel coverage strip (FE-7).
 *
 * Shows "X of Y positions paneled" per department, plus the rolled-up subtree
 * figure. T4 (Defect 1): the POSITION TOTAL (Y) and the subtree roll-up now come
 * from the ONE shared server-authoritative counts hook
 * ({@link useDepartmentPositionCounts}) — NOT a FE bucketing of a page-capped
 * position list. So a parent department rolls up its descendants (never 0) and
 * the count is never truncated past the first 200 positions.
 *
 * The PANELED count (X) is still derived from the already-loaded, ABAC-scoped
 * panels (GET /panels): each panel's position is mapped to its department via the
 * project positions the host already loaded for its table — this is NOT a
 * count scan (the counts come from the BE), only a position→department lookup so
 * a panel can be attributed to a department.
 *
 * ABAC: the GET /panels response is server-scoped, so a department director sees
 * only their own department's panels — this component never adds FE-only hiding.
 */
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { Card } from '@/shared/components/ui/Card';
import { cn } from '@/shared/lib/cn';
import { pickLocalized } from '@/shared/lib/localized';
import { useDepartmentPositionCounts } from '@/features/organization/hooks/useDepartmentPositionCounts';
import type { Department } from '@/features/organization/types/organizationTypes';
import type { Position } from '@/features/positions/types/positionTypes';
import type { Panel } from '../../panelTypes';

interface Props {
  projectId: string;
  departments: Department[];
  /**
   * Project positions the host already loaded for its table — used ONLY to map a
   * panel's `position_id` to its `department_id` (panel attribution), never to
   * compute the position total (that comes from the BE counts hook).
   */
  positions?: Position[];
  panels: Panel[];
  /**
   * When true, renders without the outer Card wrapper — for use inside a popover
   * or Drawer where the host already provides the chrome. Defaults to false
   * (preserves the original standalone Card usage).
   */
  standalone?: boolean;
}

interface DeptCoverage {
  id: string;
  code: string;
  name: string;
  total: number;
  subtree: number;
  paneled: number;
}

export function DepartmentPanelProgress({
  projectId,
  departments,
  positions = [],
  panels,
  standalone = false,
}: Props) {
  const { t, i18n } = useTranslation();
  const [expanded, setExpanded] = useState(false);

  // T4 — server-authoritative per-department counts (direct + subtree roll-up).
  const countsQuery = useDepartmentPositionCounts(projectId);
  const counts = countsQuery.data;

  // position_id -> department_id (panel attribution only — NOT a count source).
  const positionDeptId = useMemo(() => {
    const m = new Map<string, string>();
    for (const p of positions) m.set(p.id, p.department_id);
    return m;
  }, [positions]);

  // department_id -> count of positions covered by an active (non-ARCHIVED) panel.
  const paneledByDept = useMemo(() => {
    const m = new Map<string, number>();
    const seen = new Set<string>();
    for (const p of panels) {
      if (p.status === 'ARCHIVED') continue;
      if (seen.has(p.position_id)) continue; // one position counts once
      seen.add(p.position_id);
      const deptId = positionDeptId.get(p.position_id);
      if (!deptId) continue;
      m.set(deptId, (m.get(deptId) ?? 0) + 1);
    }
    return m;
  }, [panels, positionDeptId]);

  const rows = useMemo<DeptCoverage[]>(() => {
    if (!counts) return [];
    return departments
      .filter((d) => d.status !== 'ARCHIVED')
      .map((d) => {
        const c = counts.get(d.id);
        return {
          id: d.id,
          code: d.code,
          name: pickLocalized(d.name_i18n, i18n.language),
          total: c?.directCount ?? 0,
          subtree: c?.subtreeCount ?? 0,
          paneled: paneledByDept.get(d.id) ?? 0,
        };
      })
      // Keep departments that have positions in their subtree (so parents that
      // only hold descendants still appear) OR already have a paneled position.
      .filter((r) => r.subtree > 0 || r.paneled > 0)
      .sort((a, b) => a.code.localeCompare(b.code));
  }, [counts, departments, paneledByDept, i18n.language]);

  if (rows.length === 0) return null;

  const visible = expanded ? rows : rows.slice(0, 6);
  const totalPaneled = rows.reduce((s, r) => s + r.paneled, 0);
  const totalPositions = rows.reduce((s, r) => s + r.total, 0);

  const content = (
    <>
      {/* Overall summary row — always visible */}
      <div className="flex items-center justify-between gap-2">
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

      {/* Per-department rows — always expanded (collapsing is handled by the
          popover / drawer host in the new IA; the standalone Card keeps the
          list visible since the host replaced the interim sectionOpen toggle). */}
      <ul className="space-y-2 mt-3" data-testid="dept-progress-list">
        {visible.map((r) => {
          const pct = r.total > 0 ? Math.round((r.paneled / r.total) * 100) : 0;
          const full = r.total > 0 && r.paneled >= r.total;
          // Show the subtree roll-up only when it adds positions beyond the
          // direct count (a parent with descendants) — per PD-1 show BOTH.
          const showSubtree = r.subtree > r.total;
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
                className="w-44 shrink-0 text-right text-xs text-text-secondary tabular-nums"
                data-testid={`dept-progress-count-${r.code}`}
              >
                {r.total === 0 && r.subtree > 0 ? (
                  // PD-3: a parent unit with NO direct positions but a non-zero
                  // subtree. "0 of 0 ↳N" reads as "empty"; instead spell out that
                  // there are no direct positions here and N live one level down.
                  <span
                    className="text-text-muted"
                    data-testid={`dept-progress-subtree-only-${r.code}`}
                    title={t('panel.dept_progress.subtree_tooltip', { count: r.subtree })}
                  >
                    {t('panel.dept_progress.no_direct_in_subtree', { count: r.subtree })}
                  </span>
                ) : (
                  <>
                    {t('panel.dept_progress.coverage', {
                      paneled: r.paneled,
                      total: r.total,
                    })}
                    {showSubtree ? (
                      <span
                        className="ml-1 text-text-muted"
                        data-testid={`dept-progress-subtree-${r.code}`}
                        title={t('panel.dept_progress.subtree_tooltip', { count: r.subtree })}
                      >
                        {t('panel.dept_progress.subtree', { count: r.subtree })}
                      </span>
                    ) : null}
                  </>
                )}
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
    </>
  );

  // `standalone` mode: no outer Card (e.g. when embedded in a popover or Drawer).
  if (standalone) {
    return <div data-testid="dept-progress-standalone">{content}</div>;
  }

  return <Card>{content}</Card>;
}
