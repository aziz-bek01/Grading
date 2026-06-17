/**
 * EvaluationCompletionBar — compact always-visible progress strip (Phase 1).
 *
 * One ~40px row (no Card, not collapsible) showing:
 *   - "X/N scored" derived from the loaded evaluations set
 *   - Status count chips (reuses EvaluationStatusBadge)
 *   - "Panels: N paneled" count
 *   - "View by department ▾" popover for DepartmentPanelProgress
 *
 * Counts come from client-side data already loaded by EvaluationListPage.
 * Project-wide totals are bounded by the fetched set (MAX_PAGE_SIZE=200→500).
 *
 * NOTE: The `// TODO(phase1-backend): project-wide aggregate` comments below
 * mark computations that are bounded by the fetched page. A dedicated
 * `GET /evaluations/stats?projectId=` summary endpoint would remove these
 * limitations. No non-existent endpoint is called here.
 */
import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ChevronDown, X } from 'lucide-react';
import { cn } from '@/shared/lib/cn';
import { EvaluationStatusBadge } from './EvaluationStatusBadge';
import { DepartmentPanelProgress } from './panel/DepartmentPanelProgress';
import type { Evaluation, EvaluationStatus } from '../types';
import type { Panel } from '../panelTypes';
import type { Department } from '@/features/organization/types/organizationTypes';
import type { Position } from '@/features/positions/types/positionTypes';

interface Props {
  /** All loaded evaluations for the active project (status-filtered or not). */
  evaluations: Evaluation[];
  panels: Panel[];
  projectId: string;
  departments: Department[];
  positions: Position[];
  /** Called when user clicks the "Panels (N)" trigger to open the drawer. */
  onOpenPanelsDrawer: () => void;
}

// Statuses whose count is worth surfacing in the chip row.
const CHIP_STATUSES: EvaluationStatus[] = [
  'INCOMPLETE',
  'COMPLETE',
  'SUBMITTED',
  'APPROVED',
  'LOCKED',
];

export function EvaluationCompletionBar({
  evaluations,
  panels,
  projectId,
  departments,
  positions,
  onOpenPanelsDrawer,
}: Props) {
  const { t } = useTranslation();
  const [deptPopoverOpen, setDeptPopoverOpen] = useState(false);
  const popoverRef = useRef<HTMLDivElement>(null);

  // TODO(phase1-backend): project-wide aggregate — the scored/total counts here
  // reflect only the loaded evaluations page (up to 500 after Phase 1 bump).
  // A dedicated summary endpoint is needed for exact project-wide counts.
  const nonArchivedEvals = evaluations.filter((e) => e.status !== 'ARCHIVED');
  const scoredCount = nonArchivedEvals.filter(
    (e) => e.status === 'SUBMITTED' || e.status === 'APPROVED' || e.status === 'LOCKED',
  ).length;
  const totalCount = nonArchivedEvals.length;

  // Status chip counts — only statuses that actually have rows are shown.
  const statusCounts: Partial<Record<EvaluationStatus, number>> = {};
  for (const e of nonArchivedEvals) {
    if (CHIP_STATUSES.includes(e.status)) {
      statusCounts[e.status] = (statusCounts[e.status] ?? 0) + 1;
    }
  }

  const activeNonArchivedPanels = panels.filter((p) => p.status !== 'ARCHIVED');
  const paneledPositionCount = new Set(
    activeNonArchivedPanels.map((p) => p.position_id),
  ).size;

  return (
    <div
      className="flex flex-wrap items-center gap-2 py-2 px-3 bg-divider/40 rounded-lg text-sm"
      data-testid="evaluation-completion-bar"
    >
      {/* Scored progress */}
      <span className="font-medium text-text-primary tabular-nums" data-testid="completion-bar-scored">
        {t('evaluation.completion_bar.scored', { scored: scoredCount, total: totalCount })}
      </span>

      {/* Divider */}
      <span className="text-border-strong select-none" aria-hidden>·</span>

      {/* Status chips — only rendered for statuses that have rows */}
      <div className="flex flex-wrap items-center gap-1.5" data-testid="completion-bar-status-chips">
        {CHIP_STATUSES.map((status) => {
          const count = statusCounts[status];
          if (!count) return null;
          return (
            <span key={status} className="inline-flex items-center gap-1">
              <EvaluationStatusBadge status={status} />
              <span className="text-xs text-text-secondary tabular-nums">{count}</span>
            </span>
          );
        })}
      </div>

      {/* Panels count — clicking opens the Drawer */}
      {activeNonArchivedPanels.length > 0 ? (
        <>
          <span className="text-border-strong select-none" aria-hidden>·</span>
          <button
            type="button"
            onClick={onOpenPanelsDrawer}
            data-testid="completion-bar-panels-trigger"
            className="text-primary-600 hover:underline tabular-nums"
          >
            {t('evaluation.completion_bar.panels', { count: activeNonArchivedPanels.length })}
          </button>
        </>
      ) : null}

      {/* Department coverage popover */}
      {paneledPositionCount > 0 || departments.length > 0 ? (
        <>
          <span className="text-border-strong select-none" aria-hidden>·</span>
          <div className="relative" ref={popoverRef}>
            <button
              type="button"
              onClick={() => setDeptPopoverOpen((v) => !v)}
              aria-expanded={deptPopoverOpen}
              data-testid="completion-bar-dept-popover-trigger"
              className="inline-flex items-center gap-1 text-text-secondary hover:text-text-primary"
            >
              {t('evaluation.completion_bar.view_by_dept')}
              <ChevronDown
                size={13}
                aria-hidden
                className={cn('transition-transform', deptPopoverOpen && 'rotate-180')}
              />
            </button>

            {deptPopoverOpen ? (
              <div
                role="dialog"
                aria-label={t('evaluation.completion_bar.view_by_dept')}
                className={cn(
                  'absolute left-0 top-full mt-2 z-20',
                  'bg-surface border border-border rounded-xl shadow-lg p-4 w-[28rem] max-w-[90vw]',
                )}
                data-testid="completion-bar-dept-popover"
              >
                <div className="flex items-center justify-between mb-2">
                  <span className="text-sm font-medium text-text-primary">
                    {t('panel.dept_progress.title')}
                  </span>
                  <button
                    type="button"
                    aria-label={t('common.close')}
                    onClick={() => setDeptPopoverOpen(false)}
                    className="p-1 rounded text-text-secondary hover:bg-divider"
                  >
                    <X size={14} aria-hidden />
                  </button>
                </div>
                {/* DepartmentPanelProgress in standalone mode (no outer Card). */}
                <DepartmentPanelProgress
                  projectId={projectId}
                  departments={departments}
                  positions={positions}
                  panels={panels}
                  standalone
                />
              </div>
            ) : null}
          </div>
        </>
      ) : null}
    </div>
  );
}
