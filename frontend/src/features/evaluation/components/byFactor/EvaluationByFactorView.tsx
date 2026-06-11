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
import { ScoringModeBadge } from '@/features/methodology/components/ScoringModeBadge';
import { useDepartmentTree } from '@/features/organization/hooks/useDepartmentTree';
import { pickLocalized } from '@/shared/lib/localized';
import { cn } from '@/shared/lib/cn';
import { useEvaluations } from '../../hooks/useEvaluation';
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
import {
  BY_FACTOR_FILTER_STICKY_TOP,
  BY_FACTOR_STICKY_TOP,
  BY_FACTOR_STICKY_Z,
} from './stickyOffset';
import { PositionScoreRow } from './PositionScoreRow';
import { BulkScoreDialog } from './BulkScoreDialog';
import { BulkSubmitDialog } from './BulkSubmitDialog';

interface EvaluationByFactorViewProps {
  projectId: string;
  /** Active factor id from URL. When null/invalid the view picks the first. */
  factorIdFromUrl: string | null;
  /** Callback to push the active factor id back to the URL. */
  onFactorChange: (factorId: string) => void;
  /**
   * Active methodology id from URL. When null/invalid the view picks the
   * default (the methodology with the most evaluations, else the first
   * active one). Optional so single-methodology callers can omit it.
   */
  methodologyIdFromUrl?: string | null;
  /**
   * Callback to push the selected methodology id back to the URL so the
   * parent page (and the Add-positions dialog default version) follow the
   * same selection. Switching it also clears the factor param.
   */
  onMethodologyChange?: (methodologyId: string) => void;
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
  methodologyIdFromUrl = null,
  onMethodologyChange,
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

  // ----- Resolve selectable methodologies + the active selection -----
  const methodologiesQuery = useMethodologies(projectId);
  // Evaluations are reused (NO new endpoint) only to (a) compute a sensible
  // default selection — the methodology with the most evaluations — and (b)
  // decide which methodologies are worth offering in the selector. The
  // by-factor ROWS themselves still come from the scoped by-factor endpoint
  // (BE derives the version from factorId), never from this list.
  const evaluationsQuery = useEvaluations({ projectId });

  /**
   * Methodologies the user may switch between in the K-sheet: every
   * methodology that owns an active version. Ordered by the methodology
   * list itself (stable). A selector is only RENDERED when this has >1
   * entry — single-methodology projects keep the original chrome.
   */
  const selectableMethodologies = useMemo(
    () =>
      (methodologiesQuery.data?.items ?? []).filter((m) => m.active_version_id),
    [methodologiesQuery.data],
  );

  /**
   * version_id -> methodology, bridged via the active/latest version pointers
   * the enriched list response provides (mirrors EvaluationListPage's
   * `versionToMeth`). Lets us attribute each evaluation to its methodology so
   * the default selection can favour the one with the most evaluations.
   */
  const versionToMethodologyId = useMemo(() => {
    const m = new Map<string, string>();
    for (const meth of methodologiesQuery.data?.items ?? []) {
      if (meth.active_version_id) m.set(meth.active_version_id, meth.id);
      if (meth.latest_version_id) m.set(meth.latest_version_id, meth.id);
    }
    return m;
  }, [methodologiesQuery.data]);

  /** Default selection = methodology with the MOST non-archived evaluations. */
  const defaultMethodologyId = useMemo(() => {
    if (selectableMethodologies.length === 0) return null;
    const counts = new Map<string, number>();
    for (const e of evaluationsQuery.data?.items ?? []) {
      if (e.status === 'ARCHIVED') continue;
      const methId = versionToMethodologyId.get(e.methodology_version_id);
      if (!methId) continue;
      counts.set(methId, (counts.get(methId) ?? 0) + 1);
    }
    let best = selectableMethodologies[0];
    let bestCount = counts.get(best.id) ?? 0;
    for (const m of selectableMethodologies) {
      const c = counts.get(m.id) ?? 0;
      if (c > bestCount) {
        best = m;
        bestCount = c;
      }
    }
    return best.id;
  }, [selectableMethodologies, evaluationsQuery.data, versionToMethodologyId]);

  /**
   * The methodology actually driving the K-sheet. The URL value wins when it
   * still maps to a selectable methodology (so a refresh / share keeps the
   * choice); otherwise we fall back to the data-driven default.
   */
  const activeMethodology = useMemo(() => {
    if (selectableMethodologies.length === 0) return null;
    const byUrl = methodologyIdFromUrl
      ? selectableMethodologies.find((m) => m.id === methodologyIdFromUrl)
      : null;
    return (
      byUrl ??
      selectableMethodologies.find((m) => m.id === defaultMethodologyId) ??
      selectableMethodologies[0]
    );
  }, [selectableMethodologies, methodologyIdFromUrl, defaultMethodologyId]);

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

  // Push the canonical methodology id back to URL on first auto-pick so a
  // refresh keeps the choice AND the parent's Add-positions dialog defaults
  // to the same version. Only fires when the URL value does not already
  // resolve to the active methodology (avoids an update loop).
  useEffect(() => {
    if (
      activeMethodology &&
      onMethodologyChange &&
      methodologyIdFromUrl !== activeMethodology.id
    ) {
      onMethodologyChange(activeMethodology.id);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeMethodology?.id]);

  // Push the canonical factor id back to URL on first auto-pick so a
  // refresh keeps the same tab. Avoid an infinite loop by only firing
  // when the URL value does not already match.
  useEffect(() => {
    if (activeFactor && factorIdFromUrl !== activeFactor.id) {
      onFactorChange(activeFactor.id);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeFactor?.id]);

  // Explicit user selection from the header selector. Pushing the new
  // methodology id to the URL re-derives factors → tabs → activeFactor →
  // rows request for the SELECTED version (BE scopes rows by the factor's
  // own version). The factor param is intentionally NOT preserved by the
  // parent on a methodology switch so a stale (other-version) factor never
  // leaks into the new view.
  const handleMethodologyChange = useCallback(
    (methodologyId: string) => {
      onMethodologyChange?.(methodologyId);
    },
    [onMethodologyChange],
  );

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

  const methodologyName =
    pickLocalized(activeMethodology.name_i18n, i18n.language) ||
    activeMethodology.code;
  const scoringMode = versionQuery.data.scoring_mode;

  return (
    // FE-9: flex-col page so the table region can grow (flex-1 / min-h-0) and
    // the page-level scroll carries ~200 rows without virtualization. Tighter
    // vertical rhythm (space-y-2) reclaims wasted top/bottom chrome.
    <div
      className="flex flex-col min-h-0 space-y-2"
      data-testid="evaluation-by-factor"
    >
      {/* FE-7: active-methodology header strip — name + v{n} + scoring-mode
          badge, from data ALREADY loaded (activeMethodology + versionQuery).
          No new query. Part of the sticky region, directly above the tabs.
          When the project has >1 methodology with an active version a compact
          selector replaces the static name so factor tabs + rows + bulk
          actions all follow the SELECTED version. */}
      <div
        className={cn('sticky bg-background pt-1', BY_FACTOR_STICKY_TOP, BY_FACTOR_STICKY_Z)}
        data-testid="byfactor-methodology-header"
      >
        <div className="flex flex-wrap items-center gap-2 pb-1.5">
          <span className="text-xs uppercase tracking-wide text-text-muted">
            {t('evaluation.byFactor.active_methodology')}
          </span>
          {selectableMethodologies.length > 1 ? (
            <select
              aria-label={t('evaluation.byFactor.selector.aria')}
              value={activeMethodology.id}
              onChange={(e) => handleMethodologyChange(e.target.value)}
              data-testid="byfactor-methodology-select"
              className="h-8 px-2 border border-border-strong rounded-md text-sm font-medium bg-surface text-text-primary max-w-[16rem]"
            >
              {selectableMethodologies.map((m) => (
                <option key={m.id} value={m.id}>
                  {t('evaluation.byFactor.selector.option', {
                    name: pickLocalized(m.name_i18n, i18n.language) || m.code,
                    version: m.active_version_number ?? '?',
                  })}
                </option>
              ))}
            </select>
          ) : (
            <>
              <span className="text-sm font-medium text-text-primary">
                {methodologyName}
              </span>
              {activeMethodology.active_version_number != null ? (
                <span className="text-sm text-text-secondary tabular-nums">
                  v{activeMethodology.active_version_number}
                </span>
              ) : null}
            </>
          )}
          {/* Reuse the shared ScoringModeBadge; keep the legacy testid on a
              wrapper so existing header assertions stay green. */}
          <span data-testid="byfactor-scoring-mode-badge">
            <ScoringModeBadge mode={scoringMode} />
          </span>
        </div>
        <FactorTabs
          // Sticky offset + z-index come from the SHARED constant so the tabs
          // and the header never diverge. top-20 (80px) clears the 62px TopBar.
          factors={factors}
          activeFactorId={activeFactor.id}
          completion={completionMap}
          onSelect={onFactorChange}
        />
      </div>

      {/* Filter bar — sticky just BELOW the tabs (FE-9) using the SHARED
          offset/z-index constants so it stacks correctly and never diverges. */}
      <Card
        compact
        className={cn('sticky bg-background', BY_FACTOR_FILTER_STICKY_TOP, BY_FACTOR_STICKY_Z)}
      >
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
      {/* FE-9: the table region GROWS (flex-1 / min-h-0) and scrolls
          internally; the thead sticks to the top of THIS scroll context. The
          level picker is now a centered modal (LevelDropSelect), so the
          overflow container no longer clips the picker for bottom rows. */}
      <div className="w-full flex-1 min-h-0 flex flex-col">
        <Card compact className="overflow-hidden w-full flex-1 min-h-0 flex flex-col">
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
            <div className="overflow-auto flex-1 min-h-0">
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
                <thead className="bg-divider text-text-secondary text-xs uppercase tracking-wide sticky top-0 z-[1]">
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
