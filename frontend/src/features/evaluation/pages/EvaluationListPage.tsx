import { Suspense, lazy, useCallback, useMemo, useState } from 'react';
import {
  Link,
  useParams,
  useSearchParams,
} from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { LayoutGrid, TableProperties, Trash2, X } from 'lucide-react';
import { Card } from '@/shared/components/ui/Card';
import { Button } from '@/shared/components/ui/Button';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { Drawer } from '@/shared/components/layout/Drawer';
import { DataTable, type DataTableColumn } from '@/shared/components/data-table/DataTable';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { ConfirmDialog } from '@/shared/components/confirm-dialog/ConfirmDialog';
import { PERMISSIONS } from '@/shared/types/permissions';
import { usePermission } from '@/features/auth/usePermission';
import { useAuthStore } from '@/features/auth/authStore';
import { pickLocalized } from '@/shared/lib/localized';
import { cn } from '@/shared/lib/cn';
import { useMethodologies } from '@/features/methodology/hooks/useMethodology';
import { useAllPositions } from '@/features/positions/hooks/usePositions';
import { useDepartmentTree } from '@/features/organization/hooks/useDepartmentTree';
import {
  useBulkCreateEvaluations,
  useDeleteEvaluation,
  useEvaluations,
} from '../hooks/useEvaluation';
import { EvaluationStatusBadge } from '../components/EvaluationStatusBadge';
import { EvaluationCompletionBar } from '../components/EvaluationCompletionBar';
import { AddPositionsDialog } from '../components/AddPositionsDialog';
import type { RosterSeed } from '../components/panel/OpenPanelDialog';
import { useBulkCreatePanels, usePanels } from '../hooks/usePanels';
import { PanelListSection } from '../components/panel/PanelListSection';
import { isEvaluationDeletable } from '../types';
import type { BulkCreatePanelsResult, PanelEvaluatorDraft } from '../panelTypes';
import type { Evaluation, EvaluationStatus } from '../types';

// Perf: the by-factor K-sheet (with its bulk dialogs + level pickers) and the
// 3-step panel-commission wizard are the two heaviest sub-trees on this page,
// yet each is shown only on demand — the K-sheet only in `mode === 'by-factor'`
// and the wizard only once the user opens it. `React.lazy` peels both into their
// own async chunks so the by-position landing view (the default) no longer pays
// to download/parse them up front. Behaviour is identical: same components, same
// props, just deferred until first needed.
const EvaluationByFactorView = lazy(() =>
  import('../components/byFactor/EvaluationByFactorView').then((m) => ({
    default: m.EvaluationByFactorView,
  })),
);
const OpenPanelDialog = lazy(() =>
  import('../components/panel/OpenPanelDialog').then((m) => ({
    default: m.OpenPanelDialog,
  })),
);

type ViewMode = 'by-position' | 'by-factor';
type TableDensity = 'comfortable' | 'compact';

const DENSITY_STORAGE_KEY = 'evaluation_table_density';

function isViewMode(value: string | null): value is ViewMode {
  return value === 'by-position' || value === 'by-factor';
}

function readDensity(): TableDensity {
  if (typeof window === 'undefined') return 'comfortable';
  try {
    const v = window.localStorage.getItem(DENSITY_STORAGE_KEY);
    return v === 'compact' ? 'compact' : 'comfortable';
  } catch {
    return 'comfortable';
  }
}

function writeDensity(v: TableDensity): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(DENSITY_STORAGE_KEY, v);
  } catch {
    /* ignore */
  }
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

/**
 * Evaluation list — table with status / methodology / position filters.
 * "+ New evaluation" CTA gated behind EVALUATION_EDIT.
 *
 * Phase 1 changes:
 * - CompletionBar replaces stacked DepartmentPanelProgress (now in popover)
 * - PanelListSection moved to Drawer (no longer in vertical stack)
 * - Quick-filter chips: "Only incomplete" (status=INCOMPLETE), "Only mine"
 *   (evaluatorUserId=current user — backend supports evaluatorUserId param)
 * - Density toggle (comfortable/compact) persisted to localStorage
 * - positionsQuery now loads the FULL position set via the shared
 *   `useAllPositions`/`fetchAllPages` helper (EPIC-013) instead of a guessed
 *   `size: 200` → `size: 500` band-aid, which still silently truncated past
 *   its ceiling.
 */
export function EvaluationListPage() {
  const { t, i18n } = useTranslation();
  const { projectId = '' } = useParams<{ projectId: string }>();
  const { can } = usePermission();
  const canEdit = can(PERMISSIONS.EVALUATION_EDIT);
  const currentUser = useAuthStore((s) => s.user);

  // Committee scorer = can read evaluations but is NOT a methodology-aware
  // manager/oversight role (HRLAB_SUPER_ADMIN / HRLAB_CONSULTANT /
  // HRLAB_PROJECT_MANAGER / CLIENT_HR_DIRECTOR all hold METHODOLOGY_READ). Such a
  // user (e.g. a Department Director assigned to a panel) gets a focused,
  // factor-by-factor scoring experience — no project-wide management surfaces
  // (by-position list, completion bar, add-positions). Backend enforces the
  // per-sheet bias isolation; this is purely the role-aware UI shaping.
  const isCommitteeScorer =
    can(PERMISSIONS.EVALUATION_READ) && !can(PERMISSIONS.METHODOLOGY_READ);

  const [searchParams, setSearchParams] = useSearchParams();
  // A committee scorer is locked to the by-factor scoring view regardless of the
  // ?mode= URL param — they never see the by-position all-positions table.
  const mode: ViewMode = isCommitteeScorer
    ? 'by-factor'
    : isViewMode(searchParams.get('mode'))
      ? (searchParams.get('mode') as ViewMode)
      : 'by-position';
  const factorParam = searchParams.get('factor');
  // The methodology the K-sheet is scoped to. Shared via the URL so a refresh
  // keeps the choice AND the Add-positions dialog defaults to the same version.
  const methodologyParam = searchParams.get('methodology');

  // Quick-filter chips — driven via URL params.
  // "Only incomplete": status=INCOMPLETE (backend supports `status` param — verified in EvaluationController.java)
  // "Only mine": evaluatorUserId=currentUser.id (backend supports `evaluatorUserId` — verified in EvaluationController.java)
  const chipIncomplete = searchParams.get('chip_incomplete') === '1';
  const chipMine = searchParams.get('chip_mine') === '1';

  const setMode = useCallback(
    (next: ViewMode) => {
      const params = new URLSearchParams(searchParams);
      params.set('mode', next);
      if (next === 'by-position') params.delete('factor');
      setSearchParams(params, { replace: true });
    },
    [searchParams, setSearchParams],
  );

  const setFactorInUrl = useCallback(
    (factorId: string) => {
      const params = new URLSearchParams(searchParams);
      params.set('mode', 'by-factor');
      params.set('factor', factorId);
      setSearchParams(params, { replace: true });
    },
    [searchParams, setSearchParams],
  );

  const setMethodologyInUrl = useCallback(
    (methodologyId: string) => {
      const params = new URLSearchParams(searchParams);
      params.set('mode', 'by-factor');
      params.set('methodology', methodologyId);
      // A methodology switch invalidates the active factor (factors belong to a
      // single version) — drop it so the view re-picks the first factor of the
      // newly-selected version instead of carrying a stale id.
      params.delete('factor');
      setSearchParams(params, { replace: true });
    },
    [searchParams, setSearchParams],
  );

  const toggleChip = useCallback(
    (chip: 'incomplete' | 'mine', currentValue: boolean) => {
      const params = new URLSearchParams(searchParams);
      if (currentValue) {
        params.delete(`chip_${chip}`);
      } else {
        params.set(`chip_${chip}`, '1');
      }
      setSearchParams(params, { replace: true });
    },
    [searchParams, setSearchParams],
  );

  const clearChips = useCallback(() => {
    const params = new URLSearchParams(searchParams);
    params.delete('chip_incomplete');
    params.delete('chip_mine');
    setSearchParams(params, { replace: true });
  }, [searchParams, setSearchParams]);

  // The chip_incomplete and chip_mine params drive the BE query.
  // chip_incomplete maps to status=INCOMPLETE; chip_mine maps to evaluatorUserId=currentUser.id.
  // When both active we pass both; the BE ANDs them.
  // NOTE: chip_mine is only active when a currentUser is available.
  const chipMineUserId = chipMine && currentUser?.id ? currentUser.id : undefined;

  const [statusFilter, setStatusFilter] = useState<EvaluationStatus | ''>('');
  const [methodologyFilter, setMethodologyFilter] = useState<string>('');
  const [density, setDensity] = useState<TableDensity>(readDensity);
  const [adding, setAdding] = useState(false);
  const [openingPanel, setOpeningPanel] = useState(false);
  const [panelsDrawerOpen, setPanelsDrawerOpen] = useState(false);
  // Roster seed for the copy-roster affordance (FE-6) — kept across reopen so a
  // whole department can be commissioned then the roster reused for the next.
  const [rosterSeed, setRosterSeed] = useState<RosterSeed | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Evaluation | null>(null);

  const handleDensityToggle = useCallback(() => {
    setDensity((prev) => {
      const next: TableDensity = prev === 'comfortable' ? 'compact' : 'comfortable';
      writeDensity(next);
      return next;
    });
  }, []);

  // The effective status for the BE query: chip_incomplete wins over dropdown
  // because the chip is more specific (forces INCOMPLETE); if neither active,
  // use the dropdown value.
  const effectiveStatus: EvaluationStatus | undefined =
    chipIncomplete ? 'INCOMPLETE' : statusFilter || undefined;

  const evalsQuery = useEvaluations({
    projectId,
    status: effectiveStatus,
    // evaluatorUserId is a supported BE param (EvaluationController.java line 141)
    evaluatorUserId: chipMineUserId,
  });

  // Position lookup for the department/title map, the "Add positions"
  // candidate picker, and the completion bar — ALL of it needs the FULL
  // project position set, not a guessed page size (the former `size: 500`
  // band-aid still silently truncated past that ceiling — see EPIC-013).
  // useAllPositions pages to completion via the shared fetchAllPages helper.
  const positionsQuery = useAllPositions(projectId ? { projectId } : null);
  const methodologiesQuery = useMethodologies(projectId);
  const treeQuery = useDepartmentTree(projectId);
  const panelsQuery = usePanels(projectId ? { projectId } : {});
  const bulkCreateMutation = useBulkCreateEvaluations();
  const deleteMutation = useDeleteEvaluation();
  const bulkCreatePanelsMutation = useBulkCreatePanels();

  /**
   * Panel-commission orchestration (FE-5): a SINGLE bulk-create carrying the
   * shared roster + every chosen position. The BE opens one panel per position
   * and returns the per-position failure collector (no sibling rollback). The
   * min-3-mandatory-roles rule is enforced server-side on lock-roster — the UI
   * mirror only disables confirm.
   */
  const handleBulkOpenPanels = useCallback(
    async (
      versionId: string,
      positionIds: string[],
      roster: PanelEvaluatorDraft[],
    ): Promise<BulkCreatePanelsResult> => {
      return bulkCreatePanelsMutation.mutateAsync({
        methodology_version_id: versionId,
        position_ids: positionIds,
        roster: roster
          .filter((r) => r.evaluator_user_id)
          .map((r) => ({
            evaluator_user_id: r.evaluator_user_id!,
            evaluator_role: r.role,
          })),
        // "Комиссия яратиш" must CREATE AND START the commission: the BE locks
        // each fully-rostered panel and creates the per-evaluator DRAFT sheets,
        // so the assigned experts can score on /app/my-evaluations right away.
        start_evaluations: true,
      });
    },
    [bulkCreatePanelsMutation],
  );

  const positionMap = useMemo(() => {
    const m = new Map<string, string>();
    for (const p of positionsQuery.data?.items ?? []) {
      m.set(p.id, pickLocalized(p.title_i18n, i18n.language));
    }
    return m;
  }, [positionsQuery.data, i18n.language]);

  const positionDeptIdMap = useMemo(() => {
    const m = new Map<string, string>();
    for (const p of positionsQuery.data?.items ?? []) {
      m.set(p.id, p.department_id);
    }
    return m;
  }, [positionsQuery.data]);

  const departmentNameMap = useMemo(() => {
    const m = new Map<string, string>();
    for (const d of treeQuery.data ?? []) {
      m.set(d.id, pickLocalized(d.name_i18n, i18n.language));
    }
    return m;
  }, [treeQuery.data, i18n.language]);

  const departmentNameOfPosition = useCallback(
    (positionId: string): string => {
      const deptId = positionDeptIdMap.get(positionId);
      if (!deptId) return '';
      return departmentNameMap.get(deptId) ?? '';
    },
    [positionDeptIdMap, departmentNameMap],
  );

  // FE-2 candidate diff: keys of (position_id|methodology_version_id) that
  // already have a NON-archived evaluation.
  const existingEvalKeys = useMemo(() => {
    const set = new Set<string>();
    for (const e of evalsQuery.data?.items ?? []) {
      if (e.status === 'ARCHIVED') continue;
      set.add(`${e.position_id}|${e.methodology_version_id}`);
    }
    return set;
  }, [evalsQuery.data]);

  const methodologyMap = useMemo(() => {
    const m = new Map<string, string>();
    for (const meth of methodologiesQuery.data?.items ?? []) {
      m.set(meth.id, pickLocalized(meth.name_i18n, i18n.language));
    }
    return m;
  }, [methodologiesQuery.data, i18n.language]);

  // FE-027: version→methodology lookup used for (a) the methodology-filter
  // dropdown match and (b) a fallback for the "methodology" column when the
  // backend-resolved `methodologyVersionLabel` is absent (older API). Built
  // ONLY from the already-fetched methodology list's active_version_id /
  // latest_version_id — NO per-methodology version fetch. This previously also
  // resolved every historical (superseded) version id by firing one
  // fetchMethodologyVersions request PER methodology (via useQueries) on every
  // page load purely to label rows — the N+1 the audit flagged as FE-027. The
  // backend now resolves the row label directly, so that fan-out is gone; a row
  // scored against a truly historical (non-active/non-latest) version now falls
  // through to the dash the "methodology" column renders and won't match the
  // filter dropdown — an accepted, narrow trade-off given the overwhelming
  // majority of evaluations are scored against the active/latest version.
  const versionToMeth = useMemo(() => {
    const m = new Map<string, { id: string; name: string }>();
    (methodologiesQuery.data?.items ?? []).forEach((meth) => {
      const entry = { id: meth.id, name: pickLocalized(meth.name_i18n, i18n.language) };
      if (meth.active_version_id) m.set(meth.active_version_id, entry);
      if (meth.latest_version_id) m.set(meth.latest_version_id, entry);
    });
    return m;
  }, [methodologiesQuery.data, i18n.language]);

  const selectedVersionId = useMemo(() => {
    if (!methodologyParam) return null;
    const meth = (methodologiesQuery.data?.items ?? []).find(
      (m) => m.id === methodologyParam,
    );
    return meth?.active_version_id ?? null;
  }, [methodologyParam, methodologiesQuery.data]);

  const rows = useMemo(() => {
    let items = evalsQuery.data?.items ?? [];
    if (methodologyFilter) {
      items = items.filter(
        (e) => versionToMeth.get(e.methodology_version_id)?.id === methodologyFilter,
      );
    }
    return items;
  }, [evalsQuery.data, methodologyFilter, versionToMeth]);

  // Whether any quick-filter chip is active — drives "Clear filters" affordance.
  const anyChipActive = chipIncomplete || chipMine;

  const columns: DataTableColumn<Evaluation>[] = [
    {
      key: 'position',
      header: t('evaluation.column.position'),
      render: (row) => positionMap.get(row.position_id) ?? row.position_id,
      sortable: true,
      sortAccessor: (row) => positionMap.get(row.position_id) ?? '',
    },
    {
      // FE-1: department column derived FE-side from already-available data.
      key: 'department',
      header: t('evaluation.column.department'),
      render: (row) => departmentNameOfPosition(row.position_id) || '—',
      sortable: true,
      sortAccessor: (row) => departmentNameOfPosition(row.position_id),
    },
    {
      key: 'methodology',
      header: t('evaluation.column.methodology'),
      // FE-027: prefer the backend-resolved `methodologyVersionLabel`
      // ("Name (vN)") — every row already carries it, no per-methodology
      // version fetch needed. Falls back to the FE-derived methodology name
      // (versionToMeth, active/latest only — see its comment) for older API
      // responses that predate the field, then a dash.
      render: (row) =>
        row.methodologyVersionLabel ??
        versionToMeth.get(row.methodology_version_id)?.name ??
        '—',
    },
    {
      key: 'status',
      header: t('common.status'),
      render: (row) => <EvaluationStatusBadge status={row.status} />,
      sortable: true,
      sortAccessor: (row) => row.status,
    },
    {
      key: 'total',
      header: t('evaluation.column.total'),
      render: (row) =>
        row.displayed_total_score != null
          ? Number(row.displayed_total_score).toFixed(2)
          : '—',
      sortable: true,
      sortAccessor: (row) => row.displayed_total_score ?? 0,
    },
    {
      key: 'updated',
      header: t('evaluation.column.updated'),
      render: (row) =>
        (row.submitted_at ?? row.approved_at ?? row.locked_at ?? '').slice(0, 10) || '—',
    },
    {
      key: 'actions',
      header: t('common.actions'),
      render: (row) => (
        <div className="flex items-center gap-3">
          <Link
            to={`/app/projects/${projectId}/evaluation/${row.id}`}
            className="text-primary-600 hover:underline text-sm"
            data-testid={`open-evaluation-${row.id}`}
          >
            {t('common.edit')}
          </Link>
          {canEdit && isEvaluationDeletable(row.status) ? (
            <button
              type="button"
              onClick={() => setDeleteTarget(row)}
              data-testid={`delete-evaluation-${row.id}`}
              aria-label={t('evaluation.delete.action')}
              className="inline-flex items-center gap-1 text-danger-600 hover:underline text-sm"
            >
              <Trash2 size={14} aria-hidden />
              {t('common.delete')}
            </button>
          ) : null}
        </div>
      ),
    },
  ];

  // Density toggle: compact mode uses DataTable `dense` prop to tighten cell py.
  const denseTable = density === 'compact';

  return (
    <div className="space-y-4">
      <Breadcrumbs extra={[{ label: t('nav.evaluation') }]} />
      <header className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-2xl text-text-primary">
            {t('evaluation.list_title')}
          </h1>
          <p className="text-sm text-text-secondary mt-1">
            {t('evaluation.list_subtitle')}
          </p>
        </div>
        <div className="flex flex-col items-end gap-1.5">
          <div className="flex items-center gap-2">
            <PermissionGate permission={PERMISSIONS.EVALUATION_PANEL_MANAGE}>
              <Button
                variant="secondary"
                onClick={() => setOpeningPanel(true)}
                data-testid="open-panel-cta"
                title={t('evaluation.cta.panel_tooltip')}
              >
                {t('panel.dialog.title')}
              </Button>
            </PermissionGate>
            {/* "+ Add positions" is a project-management action. A committee
                scorer HAS EVALUATION_EDIT (they must score), so the permission
                gate alone is not enough — additionally require they are not a
                committee scorer. Managers/oversight (METHODOLOGY_READ holders)
                keep the button. */}
            {!isCommitteeScorer ? (
              <PermissionGate permission={PERMISSIONS.EVALUATION_EDIT}>
                <Button
                  onClick={() => setAdding(true)}
                  data-testid="add-positions-open"
                  title={t('evaluation.cta.add_positions_tooltip')}
                >
                  {t('evaluation.add_positions.cta')}
                </Button>
              </PermissionGate>
            ) : null}
          </div>
          {/* The CTA helper describes the "Open panel" / "Add positions" actions —
              irrelevant for a committee scorer (who has neither button). */}
          {!isCommitteeScorer ? (
            <p
              className="text-xs text-text-muted max-w-md text-right"
              data-testid="evaluation-cta-helper"
            >
              {t('evaluation.cta.helper')}
            </p>
          ) : null}
        </div>
      </header>

      {/* Never silent: useAllPositions pages to completion via the shared
          fetchAllPages helper, but if its safety cap is ever hit (an
          exceptionally large project) say so instead of quietly rendering an
          incomplete department/title map and "Add positions" candidate set. */}
      {positionsQuery.data?.truncated ? (
        <p
          role="status"
          className="text-xs text-warning-700"
          data-testid="positions-truncated-banner"
        >
          {t('dataTable.results_truncated', {
            shown: positionsQuery.data.items.length,
            total: positionsQuery.data.totalElements,
          })}
        </p>
      ) : null}

      {/* Mode toggle — hidden for committee scorers (locked to by-factor). */}
      {!isCommitteeScorer ? (
      <>
      <div
        role="tablist"
        aria-label={t('evaluation.byFactor.mode_toggle.aria')}
        className="inline-flex gap-1 p-1 bg-divider rounded-md"
        data-testid="evaluation-mode-toggle"
      >
        <button
          type="button"
          role="tab"
          aria-selected={mode === 'by-position'}
          onClick={() => setMode('by-position')}
          data-testid="mode-by-position"
          className={cn(
            'inline-flex items-center gap-1.5 px-3 py-1.5 text-sm rounded-md transition-colors',
            mode === 'by-position'
              ? 'bg-surface text-text-primary shadow-sm'
              : 'text-text-secondary hover:text-text-primary',
          )}
        >
          <LayoutGrid size={14} aria-hidden />
          {t('evaluation.byFactor.mode_toggle.by_position')}
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={mode === 'by-factor'}
          onClick={() => setMode('by-factor')}
          data-testid="mode-by-factor"
          className={cn(
            'inline-flex items-center gap-1.5 px-3 py-1.5 text-sm rounded-md transition-colors',
            mode === 'by-factor'
              ? 'bg-surface text-text-primary shadow-sm'
              : 'text-text-secondary hover:text-text-primary',
          )}
        >
          <TableProperties size={14} aria-hidden />
          {t('evaluation.byFactor.mode_toggle.by_factor')}
        </button>
      </div>

      <p
        className="text-xs text-text-muted -mt-2"
        data-testid="evaluation-mode-hint"
      >
        {mode === 'by-position'
          ? t('evaluation.mode_hint.by_position')
          : t('evaluation.mode_hint.by_factor')}
      </p>
      </>
      ) : null}

      {/* Phase 1 IA: CompletionBar replaces stacked DepartmentPanelProgress.
          DepartmentPanelProgress is now inside the popover within CompletionBar.
          PanelListSection is now in the Drawer below. */}
      {mode === 'by-position' ? (
        <EvaluationCompletionBar
          evaluations={evalsQuery.data?.items ?? []}
          panels={panelsQuery.data?.items ?? []}
          projectId={projectId}
          departments={treeQuery.data ?? []}
          positions={positionsQuery.data?.items ?? []}
          onOpenPanelsDrawer={() => setPanelsDrawerOpen(true)}
        />
      ) : null}

      {mode === 'by-factor' ? (
        <Suspense fallback={<LoadingState />}>
          <EvaluationByFactorView
            projectId={projectId}
            factorIdFromUrl={factorParam}
            onFactorChange={setFactorInUrl}
            methodologyIdFromUrl={methodologyParam}
            onMethodologyChange={setMethodologyInUrl}
          />
        </Suspense>
      ) : null}

      {mode === 'by-position' ? (
        <Card>
          <DataTable<Evaluation>
            columns={columns}
            rows={rows}
            rowKey={(row) => row.id}
            loading={evalsQuery.isLoading}
            dense={denseTable}
            searchPredicate={(row, q) =>
              (positionMap.get(row.position_id) ?? '').toLowerCase().includes(q)
            }
            emptyTitle={t('evaluation.empty_title')}
            emptyBody={t('evaluation.empty_body')}
            filterBar={
              <div className="flex flex-col gap-2">
                {/* Dropdowns row */}
                <div className="flex items-center gap-2 flex-wrap">
                  <select
                    aria-label={t('evaluation.filter.status')}
                    value={chipIncomplete ? '' : statusFilter}
                    onChange={(e) => {
                      setStatusFilter(e.target.value as EvaluationStatus | '');
                      // Clear chip_incomplete when manually picking a status
                      if (chipIncomplete) {
                        const params = new URLSearchParams(searchParams);
                        params.delete('chip_incomplete');
                        setSearchParams(params, { replace: true });
                      }
                    }}
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
                    onChange={(e) => setMethodologyFilter(e.target.value)}
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
                    onClick={handleDensityToggle}
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
                    onClick={() => toggleChip('incomplete', chipIncomplete)}
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
                  {currentUser ? (
                    <button
                      type="button"
                      onClick={() => toggleChip('mine', chipMine)}
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
                      onClick={clearChips}
                      data-testid="chip-clear-filters"
                      className="text-xs text-text-muted hover:text-text-primary underline"
                    >
                      {t('evaluation.filter.clear_chips')}
                    </button>
                  ) : null}
                </div>
              </div>
            }
          />
        </Card>
      ) : null}

      {/* Phase 1: PanelListSection moved to Drawer (slide-over).
          No longer stacked in the vertical flow — opened via CompletionBar trigger. */}
      <Drawer
        open={panelsDrawerOpen}
        title={`${t('panel.list.title')} (${panelsQuery.data?.items.length ?? 0})`}
        onClose={() => setPanelsDrawerOpen(false)}
        widthClassName="max-w-2xl"
        data-testid="panels-drawer"
      >
        <PanelListSection
          projectId={projectId}
          panels={panelsQuery.data?.items ?? []}
          loading={panelsQuery.isLoading}
          compact
        />
      </Drawer>

      {/* FE-2: multi-select "Add positions" dialog */}
      <AddPositionsDialog
        open={adding}
        positions={positionsQuery.data?.items ?? []}
        methodologies={methodologiesQuery.data?.items ?? []}
        existingKeys={existingEvalKeys}
        departmentNameOf={departmentNameOfPosition}
        defaultVersionId={selectedVersionId}
        onConfirm={async (versionId, positionIds) => {
          const result = await bulkCreateMutation.mutateAsync({
            items: positionIds.map((position_id) => ({
              position_id,
              methodology_version_id: versionId,
            })),
          });
          return result;
        }}
        onClose={() => setAdding(false)}
      />

      {openingPanel ? (
        <Suspense fallback={null}>
          <OpenPanelDialog
            open={openingPanel}
            projectId={projectId}
            methodologies={methodologiesQuery.data?.items ?? []}
            defaultVersionId={selectedVersionId}
            rosterSeed={rosterSeed}
            onConfirm={handleBulkOpenPanels}
            onCopyRosterToNext={(seed) => {
              setRosterSeed(seed);
              setOpeningPanel(false);
              setTimeout(() => setOpeningPanel(true), 0);
            }}
            onClose={() => {
              setOpeningPanel(false);
              setRosterSeed(null);
            }}
          />
        </Suspense>
      ) : null}

      <ConfirmDialog
        open={deleteTarget != null}
        destructive
        requireReason
        reasonMinLength={5}
        title={t('evaluation.delete.title')}
        body={t('evaluation.delete.body')}
        confirmLabel={t('common.delete')}
        reasonLabel={t('common.reason_label')}
        reasonPlaceholder={t('evaluation.delete.reason_placeholder')}
        onCancel={() => setDeleteTarget(null)}
        onConfirm={async (reason) => {
          if (!deleteTarget || !reason) return;
          await deleteMutation.mutateAsync({ id: deleteTarget.id, reason });
          setDeleteTarget(null);
        }}
      />

      {evalsQuery.isLoading ? <LoadingState /> : null}
    </div>
  );
}
