import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { Loader2 } from 'lucide-react';
import { Card } from '@/shared/components/ui/Card';
import { Button } from '@/shared/components/ui/Button';
import { PaginationBar } from '@/shared/components/data-table/PaginationBar';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { EmptyState } from '@/shared/components/feedback/EmptyState';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import { usePermission } from '@/features/auth/usePermission';
import { useAuthStore } from '@/features/auth/authStore';
import { useMethodologies, useMethodologyVersion } from '@/features/methodology/hooks/useMethodology';
import { useDepartmentTree } from '@/features/organization/hooks/useDepartmentTree';
import { pickLocalized } from '@/shared/lib/localized';
import { cn } from '@/shared/lib/cn';
import { upsertScore } from '../../api/evaluationApi';
import {
  useBulkScoreSet,
  useBulkSubmit,
  useEvaluationsByFactor,
} from '../../hooks/useEvaluationsByFactor';
import { evaluationKeys } from '../../api/evaluationApi';
import type {
  EvaluationByFactorRow,
  EvaluationStatus,
  EvaluationsByFactorFilters,
} from '../../types';
import { FactorTabs, type FactorCompletionMap } from './FactorTabs';
import { BY_FACTOR_STICKY_TOP, BY_FACTOR_STICKY_Z } from './stickyOffset';
import { PositionScoreRow } from './PositionScoreRow';
import { BulkScoreDialog } from './BulkScoreDialog';
import { BulkSubmitDialog } from './BulkSubmitDialog';

interface EvaluationByFactorViewProps {
  projectId: string;
  /** Active factor id from URL. When null/invalid the view picks the first. */
  factorIdFromUrl: string | null;
  /** Callback to push the active factor id back to the URL. */
  onFactorChange: (factorId: string) => void;
}

const STATUSES: EvaluationStatus[] = [
  'DRAFT',
  'INCOMPLETE',
  'COMPLETE',
  'SUBMITTED',
  'APPROVED',
  'LOCKED',
  'ARCHIVED',
];

const PAGE_SIZE = 25;

/**
 * Bulk-evaluation-by-factor view (Excel K-sheet UX).
 *
 * Layout (responsive, desktop-first) — FULL-WIDTH table since the PO
 * redesign retired the right-side rubric panel; each row reads its level
 * description in-line via LevelDropSelect:
 *   ┌───────────────── factor tabs ─────────────────┐
 *   │ filters bar                                    │
 *   │ ┌──────────── full-width table ─────────────┐  │
 *   │ │   row1  [level-drop]  comment  status      │  │
 *   │ │   row2  ...                                │  │
 *   │ └───────────────────────────────────────────┘  │
 *   │ bottom toolbar: selected N / bulk actions      │
 *   │ pagination                                      │
 *   └────────────────────────────────────────────────┘
 *
 * Methodology source: the view picks the first ACTIVE methodology for
 * the project and uses its `active_version_id`. In MVP 1 we expect one
 * canonical methodology per project; multi-methodology support is a
 * Phase 7+ concern (PRD MVP1-E7).
 */
export function EvaluationByFactorView({
  projectId,
  factorIdFromUrl,
  onFactorChange,
}: EvaluationByFactorViewProps) {
  const { t, i18n } = useTranslation();
  const qc = useQueryClient();
  const { can } = usePermission();
  const canEdit = can(PERMISSIONS.EVALUATION_EDIT);
  /**
   * Points-visibility exception (PO-ratified): plain expert evaluators must
   * judge positions by level DESCRIPTIONS only — surfacing the raw point
   * value anchors the score (anchoring bias). Project admins / HR directors
   * are exempt: they already hold `CALIBRATION_EDIT` (the manual-calibration
   * permission used by the calibration/approve flow — see CalibrationDialog
   * and EvaluationActionsBar), a role plain experts do NOT have. We reuse
   * that EXISTING permission rather than inventing a new code, derive the
   * boolean ONCE here, and thread it down to every place a level renders
   * (row control, open list, bulk dialog).
   */
  const canSeePoints = can(PERMISSIONS.CALIBRATION_EDIT);
  const setSidebarCollapsed = useAuthStore((s) => s.setSidebarCollapsed);

  // Auto-collapse the sidebar to icon-only mode while the by-factor grid is
  // mounted (the K-sheet needs the horizontal room). Restore on unmount so
  // other pages keep the user's default (expanded) layout.
  useEffect(() => {
    setSidebarCollapsed(true);
    return () => setSidebarCollapsed(false);
  }, [setSidebarCollapsed]);

  // ----- Filters (local; reset to page 0 when any filter changes) -----
  const [statusFilter, setStatusFilter] = useState<EvaluationStatus | ''>('');
  const [departmentId, setDepartmentId] = useState('');
  const [onlyUnfilled, setOnlyUnfilled] = useState(false);
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [bulkSet, setBulkSet] = useState<Set<string>>(new Set());
  const [activeRowId, setActiveRowId] = useState<string | null>(null);

  // Dialog open flags
  const [bulkScoreOpen, setBulkScoreOpen] = useState(false);
  const [bulkSubmitOpen, setBulkSubmitOpen] = useState(false);

  // ----- Resolve active methodology + version -----
  const methodologiesQuery = useMethodologies(projectId);
  const activeMethodology = useMemo(
    () =>
      (methodologiesQuery.data?.items ?? []).find(
        (m) => m.active_version_id,
      ) ?? null,
    [methodologiesQuery.data],
  );
  const versionQuery = useMethodologyVersion(
    activeMethodology?.active_version_id ?? undefined,
  );
  const factors = useMemo(
    () =>
      [...(versionQuery.data?.factors ?? [])].sort(
        (a, b) => a.sort_order - b.sort_order,
      ),
    [versionQuery.data],
  );

  // ----- Resolve active factor -----
  const activeFactor = useMemo(() => {
    if (factors.length === 0) return null;
    const byUrl = factorIdFromUrl
      ? factors.find((f) => f.id === factorIdFromUrl || f.code === factorIdFromUrl)
      : null;
    return byUrl ?? factors[0];
  }, [factors, factorIdFromUrl]);

  // Push the canonical factor id back to URL on first auto-pick so a
  // refresh keeps the same tab. Avoid an infinite loop by only firing
  // when the URL value does not already match.
  useEffect(() => {
    if (activeFactor && factorIdFromUrl !== activeFactor.id) {
      onFactorChange(activeFactor.id);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeFactor?.id]);

  // ----- Department list for filter -----
  // `fetchDepartmentTree` returns Department[] (already unwrapped — see
  // organizationApi.ts) so the data shape is a flat array, not an envelope.
  const treeQuery = useDepartmentTree(projectId);
  const departmentOptions = useMemo(
    () =>
      (treeQuery.data ?? []).map((d) => ({
        id: d.id,
        label: pickLocalized(d.name_i18n, i18n.language),
      })),
    [treeQuery.data, i18n.language],
  );

  // ----- Server query for the K-sheet rows -----
  const filters: EvaluationsByFactorFilters = {
    projectId,
    factorId: activeFactor?.id ?? '',
    status: statusFilter,
    departmentId,
    onlyUnfilled,
    search: search.trim() || undefined,
    page,
    size: PAGE_SIZE,
  };
  const rowsQuery = useEvaluationsByFactor(filters);

  const rows: EvaluationByFactorRow[] = rowsQuery.data?.items ?? [];
  const totalElements = rowsQuery.data?.total_elements ?? 0;
  const totalPages = rowsQuery.data?.total_pages ?? 1;

  // ----- Per-row mutations -----
  const bulkScoreMutation = useBulkScoreSet(activeFactor?.id ?? '');
  const bulkSubmitMutation = useBulkSubmit(activeFactor?.id ?? '');

  // Reset bulk selection / active row / page when the factor changes (per-factor
  // state). Done during render via a previous-id ref (React's "adjust state when
  // a prop changes" pattern) instead of a synchronous setState-in-effect.
  const prevFactorIdRef = useRef(activeFactor?.id ?? null);
  if (prevFactorIdRef.current !== (activeFactor?.id ?? null)) {
    prevFactorIdRef.current = activeFactor?.id ?? null;
    setBulkSet(new Set());
    setActiveRowId(null);
    setPage(0);
  }

  // ----- Factor-level completion summary (for tab indicators) -----
  // Aggregated from CURRENT PAGE only — the parent does not have a
  // project-wide aggregate endpoint in MVP 1; a future iteration can
  // expand this via a dedicated `/by-factor-completion` summary.
  const completionMap: FactorCompletionMap = useMemo(() => {
    const map: FactorCompletionMap = {};
    if (!activeFactor) return map;
    if (rows.length === 0) {
      map[activeFactor.id] = 'empty';
      return map;
    }
    const filledRows = rows.filter((r) => r.current_score_factor_level_id).length;
    if (filledRows === 0) map[activeFactor.id] = 'empty';
    else if (filledRows === rows.length) map[activeFactor.id] = 'full';
    else map[activeFactor.id] = 'partial';
    // Other factors: derive a rough estimate from filled_factors_count per row.
    for (const f of factors) {
      if (f.id === activeFactor.id) continue;
      // We don't have per-other-factor truth — leave undefined for honesty.
    }
    return map;
  }, [rows, factors, activeFactor]);

  // ----- Inline score change for a single row -----
  const handleScoreChange = useCallback(
    async (row: EvaluationByFactorRow, factorLevelId: string) => {
      if (!activeFactor) return;
      await upsertScore(row.evaluation_id, {
        factor_id: activeFactor.id,
        factor_level_id: factorLevelId,
      });
      // Invalidate the by-factor list so the row reflects the saved value
      // and the progress chip recomputes. Per-evaluation cache is
      // refreshed indirectly via the `evaluations.all` parent key.
      qc.invalidateQueries({ queryKey: evaluationKeys.all });
    },
    [activeFactor, qc],
  );

  const handleCommentChange = useCallback(
    async (row: EvaluationByFactorRow, comment: string) => {
      if (!activeFactor) return;
      // Comments require a level — skip when none.
      if (!row.current_score_factor_level_id) return;
      await upsertScore(row.evaluation_id, {
        factor_id: activeFactor.id,
        factor_level_id: row.current_score_factor_level_id,
        comment,
      });
      qc.invalidateQueries({ queryKey: evaluationKeys.all });
    },
    [activeFactor, qc],
  );

  // ----- Bulk selection helpers -----
  const allRowIds = useMemo(() => rows.map((r) => r.evaluation_id), [rows]);
  const allSelected =
    bulkSet.size > 0 && allRowIds.every((id) => bulkSet.has(id));
  const toggleAll = () => {
    setBulkSet((prev) => {
      const next = new Set(prev);
      if (allSelected) {
        for (const id of allRowIds) next.delete(id);
      } else {
        for (const id of allRowIds) next.add(id);
      }
      return next;
    });
  };
  const toggleRow = (id: string, on: boolean) => {
    setBulkSet((prev) => {
      const next = new Set(prev);
      if (on) next.add(id);
      else next.delete(id);
      return next;
    });
  };

  // The active (row-clicked) row — drives the subtle row highlight only.
  // The rubric panel that previously consumed it has been retired; the
  // highlight is a low-cost focus cue retained from the original UX.
  const activeRow = useMemo(
    () => rows.find((r) => r.evaluation_id === activeRowId) ?? rows[0] ?? null,
    [rows, activeRowId],
  );

  // ----- Loading / error / empty branches -----
  if (methodologiesQuery.isLoading || versionQuery.isLoading) {
    return <LoadingState />;
  }
  if (!activeMethodology || !versionQuery.data) {
    return (
      <EmptyState
        title={t('evaluation.byFactor.no_methodology_title')}
        body={t('evaluation.byFactor.no_methodology_body')}
      />
    );
  }
  if (!activeFactor) {
    return (
      <EmptyState
        title={t('evaluation.byFactor.no_factors_title')}
        body={t('evaluation.byFactor.no_factors_body')}
      />
    );
  }

  return (
    <div className="space-y-4" data-testid="evaluation-by-factor">
      <FactorTabs
        // Sticky offset + z-index come from the SHARED constant so the tabs
        // and the rubric never diverge again. top-20 (80px) clears the 62px
        // TopBar (was top-14/56px → tabs tucked ~6px under the header). z-10
        // keeps the tabs above table content but below the TopBar (z-20).
        className={cn(
          'sticky bg-background pt-2',
          BY_FACTOR_STICKY_TOP,
          BY_FACTOR_STICKY_Z,
        )}
        factors={factors}
        activeFactorId={activeFactor.id}
        completion={completionMap}
        onSelect={onFactorChange}
      />

      {/* Filter bar */}
      <Card compact>
        <div className="flex flex-wrap items-center gap-2">
          <select
            aria-label={t('evaluation.byFactor.filter.department')}
            value={departmentId}
            onChange={(e) => {
              setDepartmentId(e.target.value);
              setPage(0);
            }}
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
            onChange={(e) => {
              setStatusFilter(e.target.value as EvaluationStatus | '');
              setPage(0);
            }}
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
              onChange={(e) => {
                setOnlyUnfilled(e.target.checked);
                setPage(0);
              }}
              data-testid="byfactor-filter-only-unfilled"
              className="h-4 w-4 accent-primary-500"
            />
            {t('evaluation.byFactor.filter.only_unfilled')}
          </label>
          <input
            type="search"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
            placeholder={t('common.search')}
            data-testid="byfactor-filter-search"
            className="h-9 px-3 border border-border-strong rounded-md text-sm bg-surface flex-1 min-w-[200px] max-w-md"
          />
        </div>
      </Card>

      {/*
        Full-width K-sheet table. The old right-side rubric reference panel
        (and its narrow-screen slide-over Drawer) were RETIRED per the PO
        redesign: evaluators now read each level's description in-line via
        the per-row LevelDropSelect, so a separate rubric column would only
        duplicate that text and re-introduce the point-anchoring it removes.
        The table reclaims the full content width on every breakpoint.
      */}
      <div className="w-full">
        <Card compact className="overflow-hidden w-full">
          {rowsQuery.isError ? (
            <ErrorState onRetry={() => rowsQuery.refetch()} />
          ) : rowsQuery.isLoading ? (
            <LoadingState />
          ) : rows.length === 0 ? (
            <EmptyState
              title={t('evaluation.byFactor.empty_title')}
              body={t('evaluation.byFactor.empty_body')}
            />
          ) : (
            <div className="overflow-x-auto">
              <table
                className="w-full table-fixed text-sm border-collapse"
                data-testid="byfactor-table"
              >
                {/*
                  colgroup pins the column widths so `table-fixed` keeps the
                  DARAJA column dominant (~42%) regardless of cell content. The
                  retired БЎЛИМ / СЕКТОР / ТЎЛДИРИЛГАН columns are folded into
                  the merged ЛАВОЗИМ (line 2 sub-text) and the merged ҲОЛАТ
                  (stacked progress + status) columns respectively.
                */}
                <colgroup>
                  <col className="w-8" />
                  <col className="w-[27%]" />
                  <col className="w-[42%]" />
                  <col className="w-[21%]" />
                  <col className="w-[7%]" />
                </colgroup>
                <thead className="bg-divider text-text-secondary text-xs uppercase tracking-wide">
                  <tr>
                    <th className="px-2 py-3 w-8 text-left align-top">
                      <input
                        type="checkbox"
                        checked={allSelected}
                        onChange={toggleAll}
                        aria-label={t('evaluation.byFactor.row.select_all_aria')}
                        data-testid="byfactor-select-all"
                        className="h-4 w-4 accent-primary-500"
                      />
                    </th>
                    <th className="px-3 py-3 text-left font-medium align-top">
                      {t('evaluation.byFactor.table.column.position')}
                    </th>
                    <th className="px-3 py-3 text-left font-medium align-top">
                      {t('evaluation.byFactor.table.column.level')}
                    </th>
                    <th className="px-3 py-3 text-left font-medium align-top">
                      {t('evaluation.byFactor.table.column.comment')}
                    </th>
                    <th className="px-3 py-3 text-right font-medium align-top hidden lg:table-cell">
                      {t('common.status')}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <PositionScoreRow
                      key={row.evaluation_id}
                      row={row}
                      factor={activeFactor}
                      selected={activeRow?.evaluation_id === row.evaluation_id}
                      bulkSelected={bulkSet.has(row.evaluation_id)}
                      canEdit={canEdit}
                      canSeePoints={canSeePoints}
                      onScoreChange={(lvlId) => handleScoreChange(row, lvlId)}
                      onCommentChange={(c) => handleCommentChange(row, c)}
                      onRowSelect={() => setActiveRowId(row.evaluation_id)}
                      onBulkToggle={(on) => toggleRow(row.evaluation_id, on)}
                    />
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      </div>

      {/* Bottom toolbar */}
      <Card compact>
        <div className="flex flex-wrap items-center gap-3 justify-between">
          <div className="text-sm text-text-secondary">
            {t('evaluation.byFactor.toolbar.selected', {
              selected: bulkSet.size,
              total: totalElements,
            })}
          </div>
          <div className="flex items-center gap-2">
            {rowsQuery.isFetching ? (
              <Loader2
                size={16}
                className={cn('animate-spin text-text-muted')}
                aria-hidden
              />
            ) : null}
            <PermissionGate permission={PERMISSIONS.EVALUATION_EDIT}>
              <Button
                variant="secondary"
                onClick={() => setBulkScoreOpen(true)}
                disabled={bulkSet.size === 0}
                data-testid="bulk-score-open"
              >
                {t('evaluation.byFactor.bulk.set_all.cta', {
                  count: bulkSet.size,
                })}
              </Button>
            </PermissionGate>
            <PermissionGate permission={PERMISSIONS.EVALUATION_EDIT}>
              <Button
                onClick={() => setBulkSubmitOpen(true)}
                disabled={bulkSet.size === 0}
                data-testid="bulk-submit-open"
              >
                {t('evaluation.byFactor.bulk.submit.cta', {
                  count: bulkSet.size,
                })}
              </Button>
            </PermissionGate>
          </div>
        </div>
        <PaginationBar
          page={page}
          totalPages={totalPages}
          total={totalElements}
          pageSize={PAGE_SIZE}
          onPageChange={setPage}
        />
      </Card>

      <BulkScoreDialog
        open={bulkScoreOpen}
        factor={activeFactor}
        selectedCount={bulkSet.size}
        canSeePoints={canSeePoints}
        onClose={() => setBulkScoreOpen(false)}
        onConfirm={async (factorLevelId, reason) => {
          const result = await bulkScoreMutation.mutateAsync({
            evaluation_ids: Array.from(bulkSet),
            factor_level_id: factorLevelId,
            reason,
          });
          if (result.failed.length === 0) setBulkSet(new Set());
          return result;
        }}
      />
      <BulkSubmitDialog
        open={bulkSubmitOpen}
        selectedCount={bulkSet.size}
        onClose={() => setBulkSubmitOpen(false)}
        onConfirm={async (reason) => {
          const result = await bulkSubmitMutation.mutateAsync({
            evaluation_ids: Array.from(bulkSet),
            reason,
          });
          if (result.failed.length === 0) setBulkSet(new Set());
          return result;
        }}
      />
    </div>
  );
}
