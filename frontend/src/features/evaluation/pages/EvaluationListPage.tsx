import { useCallback, useMemo, useState } from 'react';
import {
  Link,
  useParams,
  useSearchParams,
} from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { LayoutGrid, TableProperties, Trash2 } from 'lucide-react';
import { Card } from '@/shared/components/ui/Card';
import { Button } from '@/shared/components/ui/Button';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { DataTable, type DataTableColumn } from '@/shared/components/data-table/DataTable';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { ConfirmDialog } from '@/shared/components/confirm-dialog/ConfirmDialog';
import { PERMISSIONS } from '@/shared/types/permissions';
import { usePermission } from '@/features/auth/usePermission';
import { pickLocalized } from '@/shared/lib/localized';
import { cn } from '@/shared/lib/cn';
import { useMethodologies } from '@/features/methodology/hooks/useMethodology';
import { usePositions } from '@/features/positions/hooks/usePositions';
import { useDepartmentTree } from '@/features/organization/hooks/useDepartmentTree';
import {
  useBulkCreateEvaluations,
  useDeleteEvaluation,
  useEvaluations,
} from '../hooks/useEvaluation';
import { EvaluationStatusBadge } from '../components/EvaluationStatusBadge';
import { AddPositionsDialog } from '../components/AddPositionsDialog';
import { OpenPanelDialog, type RosterSeed } from '../components/panel/OpenPanelDialog';
import { EvaluationByFactorView } from '../components/byFactor/EvaluationByFactorView';
import { useBulkCreatePanels, usePanels } from '../hooks/usePanels';
import { DepartmentPanelProgress } from '../components/panel/DepartmentPanelProgress';
import { isEvaluationDeletable } from '../types';
import type { BulkCreatePanelsResult, PanelEvaluatorDraft } from '../panelTypes';
import type { Evaluation, EvaluationStatus } from '../types';

type ViewMode = 'by-position' | 'by-factor';

function isViewMode(value: string | null): value is ViewMode {
  return value === 'by-position' || value === 'by-factor';
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
 */
export function EvaluationListPage() {
  const { t, i18n } = useTranslation();
  const { projectId = '' } = useParams<{ projectId: string }>();
  const { can } = usePermission();
  const canEdit = can(PERMISSIONS.EVALUATION_EDIT);
  const [searchParams, setSearchParams] = useSearchParams();
  const mode: ViewMode = isViewMode(searchParams.get('mode'))
    ? (searchParams.get('mode') as ViewMode)
    : 'by-position';
  const factorParam = searchParams.get('factor');
  // The methodology the K-sheet is scoped to. Shared via the URL so a refresh
  // keeps the choice AND the Add-positions dialog defaults to the same version.
  const methodologyParam = searchParams.get('methodology');

  const setMode = useCallback(
    (next: ViewMode) => {
      // Preserve the factor + methodology params when switching to by-factor;
      // drop the factor when going back to by-position so URLs stay minimal.
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

  const [statusFilter, setStatusFilter] = useState<EvaluationStatus | ''>('');
  const [methodologyFilter, setMethodologyFilter] = useState<string>('');
  const [adding, setAdding] = useState(false);
  const [openingPanel, setOpeningPanel] = useState(false);
  // Roster seed for the copy-roster affordance (FE-6) — kept across reopen so a
  // whole department can be commissioned then the roster reused for the next.
  const [rosterSeed, setRosterSeed] = useState<RosterSeed | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Evaluation | null>(null);

  const evalsQuery = useEvaluations({
    projectId,
    status: statusFilter || undefined,
  });
  const positionsQuery = usePositions(projectId ? { projectId, size: 200 } : null);
  const methodologiesQuery = useMethodologies(projectId);
  // FE-1: the same department tree the by-factor view consumes — used to map
  // position.department_id -> localized department name FE-side (no BE change).
  const treeQuery = useDepartmentTree(projectId);
  // FE-7: already-loaded panel set for the per-department progress column.
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

  // position_id -> department_id, then department_id -> localized name.
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
  // already have a NON-archived evaluation. The Add-positions dialog filters
  // candidates against this set for the selected version.
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

  /**
   * Evaluations reference a methodology *version* id, but the dropdown filters
   * by methodology (container) id. Bridge version → methodology via the
   * active/latest version pointers the enriched list response now provides, so
   * the column shows the methodology name and the filter actually matches.
   */
  const versionToMeth = useMemo(() => {
    const m = new Map<string, { id: string; name: string }>();
    for (const meth of methodologiesQuery.data?.items ?? []) {
      const entry = { id: meth.id, name: pickLocalized(meth.name_i18n, i18n.language) };
      if (meth.active_version_id) m.set(meth.active_version_id, entry);
      if (meth.latest_version_id) m.set(meth.latest_version_id, entry);
    }
    return m;
  }, [methodologiesQuery.data, i18n.language]);

  // The active version of the methodology the K-sheet is currently scoped to
  // (from the ?methodology= URL param). Used to default the Add-positions
  // dialog so creating evaluations follows the selected methodology version.
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

  const columns: DataTableColumn<Evaluation>[] = [
    {
      key: 'position',
      header: t('evaluation.column.position'),
      render: (row) => positionMap.get(row.position_id) ?? row.position_id,
      sortable: true,
      sortAccessor: (row) => positionMap.get(row.position_id) ?? '',
    },
    {
      // FE-1: department column, derived FE-side from already-available data
      // (positions + department tree). No change to EvaluationResponse.
      key: 'department',
      header: t('evaluation.column.department'),
      render: (row) => departmentNameOfPosition(row.position_id) || '—',
      sortable: true,
      sortAccessor: (row) => departmentNameOfPosition(row.position_id),
    },
    {
      key: 'methodology',
      header: t('evaluation.column.methodology'),
      render: (row) =>
        versionToMeth.get(row.methodology_version_id)?.name ??
        row.methodology_version_id.slice(0, 8),
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
        (row.submitted_at ?? row.approved_at ?? row.locked_at ?? '').slice(
          0,
          10,
        ) || '—',
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
          {/* FE-3: row-level delete — pre-submission rows (DRAFT / INCOMPLETE /
              COMPLETE) + EVALUATION_EDIT. Post-submission rows keep the Archive
              path on the detail page. Visibility derives from the single shared
              `isEvaluationDeletable` predicate (mirrors the BE guard). */}
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

  return (
    <div className="space-y-6">
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
        <div className="flex items-center gap-2">
          <PermissionGate permission={PERMISSIONS.EVALUATION_PANEL_MANAGE}>
            <Button
              variant="secondary"
              onClick={() => setOpeningPanel(true)}
              data-testid="open-panel-cta"
            >
              {t('panel.dialog.title')}
            </Button>
          </PermissionGate>
          <PermissionGate permission={PERMISSIONS.EVALUATION_EDIT}>
            <Button
              onClick={() => setAdding(true)}
              data-testid="add-positions-open"
            >
              {t('evaluation.add_positions.cta')}
            </Button>
          </PermissionGate>
        </div>
      </header>

      {/* Mode toggle — preserves URL state via ?mode= so refresh / share works. */}
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

      {/* FE-6: one-line helper so the two pagination/metric surfaces are not
          confused — by-position is per-position drill-down, by-factor is the
          bulk K-sheet. */}
      <p
        className="text-xs text-text-muted -mt-3"
        data-testid="evaluation-mode-hint"
      >
        {mode === 'by-position'
          ? t('evaluation.mode_hint.by_position')
          : t('evaluation.mode_hint.by_factor')}
      </p>

      {/* FE-7: per-department panel coverage (X of Y positions paneled),
          derived from the already-loaded panels + department tree + positions —
          no new endpoint. Dept directors only see their own scope because the
          BE GET /panels response is ABAC-scoped (no FE-only hiding). */}
      {mode === 'by-position' ? (
        <DepartmentPanelProgress
          departments={treeQuery.data ?? []}
          positions={positionsQuery.data?.items ?? []}
          panels={panelsQuery.data?.items ?? []}
        />
      ) : null}

      {mode === 'by-factor' ? (
        <EvaluationByFactorView
          projectId={projectId}
          factorIdFromUrl={factorParam}
          onFactorChange={setFactorInUrl}
          methodologyIdFromUrl={methodologyParam}
          onMethodologyChange={setMethodologyInUrl}
        />
      ) : null}

      {mode === 'by-position' ? (
      <Card>
        <DataTable<Evaluation>
          columns={columns}
          rows={rows}
          rowKey={(row) => row.id}
          loading={evalsQuery.isLoading}
          searchPredicate={(row, q) =>
            (positionMap.get(row.position_id) ?? '').toLowerCase().includes(q)
          }
          emptyTitle={t('evaluation.empty_title')}
          emptyBody={t('evaluation.empty_body')}
          filterBar={
            <div className="flex items-center gap-2 flex-wrap">
              <select
                aria-label={t('evaluation.filter.status')}
                value={statusFilter}
                onChange={(e) =>
                  setStatusFilter(e.target.value as EvaluationStatus | '')
                }
                data-testid="filter-status"
                className="h-9 px-3 border border-border-strong rounded-md text-sm bg-surface"
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
            </div>
          }
        />
      </Card>
      ) : null}

      {/* FE-2: multi-select "Add positions" dialog (replaces the single-create
          dialog). Reuses the BulkScoreDialog modal structure + partial-fail
          result block. Gated behind EVALUATION_EDIT. */}
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

      {/* FE-1..FE-6: dept-first 3-step panel wizard (replaces the flat
          single-position dialog). Step 1 department → Step 2 server-filtered
          positions → Step 3 shared roster, then ONE bulk-create. Gated behind
          EVALUATION_PANEL_MANAGE. */}
      <OpenPanelDialog
        open={openingPanel}
        projectId={projectId}
        methodologies={methodologiesQuery.data?.items ?? []}
        defaultVersionId={selectedVersionId}
        rosterSeed={rosterSeed}
        onConfirm={handleBulkOpenPanels}
        onCopyRosterToNext={(seed) => {
          // FE-6: keep HR + externals (dept director already cleared by the
          // wizard), reopen at Step 1 for the next department.
          setRosterSeed(seed);
          setOpeningPanel(false);
          setTimeout(() => setOpeningPanel(true), 0);
        }}
        onClose={() => {
          setOpeningPanel(false);
          setRosterSeed(null);
        }}
      />

      {/* FE-3: delete confirmation for pre-submission rows — reuses ConfirmDialog
          with the optional required-reason field (>=5 chars, matching BE ReasonRequest). */}
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
