import { Suspense, lazy } from 'react';
import { useTranslation } from 'react-i18next';
import { Card } from '@/shared/components/ui/Card';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { Drawer } from '@/shared/components/layout/Drawer';
import { DataTable } from '@/shared/components/data-table/DataTable';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { ConfirmDialog } from '@/shared/components/confirm-dialog/ConfirmDialog';
import { AddPositionsDialog } from '../components/AddPositionsDialog';
import { EvaluationCompletionBar } from '../components/EvaluationCompletionBar';
import { PanelListSection } from '../components/panel/PanelListSection';
import { EvaluationListHeader } from '../components/list/EvaluationListHeader';
import { EvaluationModeToggle } from '../components/list/EvaluationModeToggle';
import { EvaluationFilterBar } from '../components/list/EvaluationFilterBar';
import { useEvaluationListColumns } from '../components/list/useEvaluationListColumns';
import { useEvaluationListState } from './useEvaluationListState';
import type { Evaluation } from '../types';

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
 *
 * FE-041 — this page is now a thin orchestrator: URL/filter state, queries and
 * derived maps live in {@link useEvaluationListState}; the header, mode
 * toggle, filter bar and table columns each live in their own file under
 * `components/list/`. No behaviour, testid or DOM change.
 */
export function EvaluationListPage() {
  const { t } = useTranslation();
  const s = useEvaluationListState();

  const columns = useEvaluationListColumns({
    projectId: s.projectId,
    positionMap: s.positionMap,
    departmentNameOfPosition: s.departmentNameOfPosition,
    versionToMeth: s.versionToMeth,
    onDeleteRequest: s.setDeleteTarget,
  });

  // Density toggle: compact mode uses DataTable `dense` prop to tighten cell py.
  const denseTable = s.density === 'compact';

  return (
    <div className="space-y-4">
      <Breadcrumbs extra={[{ label: t('nav.evaluation') }]} />

      <EvaluationListHeader
        isCommitteeScorer={s.isCommitteeScorer}
        onOpenPanel={() => s.setOpeningPanel(true)}
        onAddPositions={() => s.setAdding(true)}
      />

      {/* Never silent: useAllPositions pages to completion via the shared
          fetchAllPages helper, but if its safety cap is ever hit (an
          exceptionally large project) say so instead of quietly rendering an
          incomplete department/title map and "Add positions" candidate set. */}
      {s.positionsQuery.data?.truncated ? (
        <p
          role="status"
          className="text-xs text-warning-700"
          data-testid="positions-truncated-banner"
        >
          {t('dataTable.results_truncated', {
            shown: s.positionsQuery.data.items.length,
            total: s.positionsQuery.data.totalElements,
          })}
        </p>
      ) : null}

      {/* Mode toggle — hidden for committee scorers (locked to by-factor). */}
      {!s.isCommitteeScorer ? (
        <EvaluationModeToggle mode={s.mode} onChange={s.setMode} />
      ) : null}

      {/* Phase 1 IA: CompletionBar replaces stacked DepartmentPanelProgress.
          DepartmentPanelProgress is now inside the popover within CompletionBar.
          PanelListSection is now in the Drawer below. */}
      {s.mode === 'by-position' ? (
        <EvaluationCompletionBar
          evaluations={s.evalsQuery.data?.items ?? []}
          panels={s.panelsQuery.data?.items ?? []}
          projectId={s.projectId}
          departments={s.treeQuery.data ?? []}
          positions={s.positionsQuery.data?.items ?? []}
          onOpenPanelsDrawer={() => s.setPanelsDrawerOpen(true)}
        />
      ) : null}

      {s.mode === 'by-factor' ? (
        <Suspense fallback={<LoadingState />}>
          <EvaluationByFactorView
            projectId={s.projectId}
            factorIdFromUrl={s.factorParam}
            onFactorChange={s.setFactorInUrl}
            methodologyIdFromUrl={s.methodologyParam}
            onMethodologyChange={s.setMethodologyInUrl}
          />
        </Suspense>
      ) : null}

      {s.mode === 'by-position' ? (
        <Card>
          <DataTable<Evaluation>
            columns={columns}
            rows={s.rows}
            rowKey={(row) => row.id}
            loading={s.evalsQuery.isLoading}
            dense={denseTable}
            searchPredicate={(row, q) =>
              (s.positionMap.get(row.position_id) ?? '').toLowerCase().includes(q)
            }
            emptyTitle={t('evaluation.empty_title')}
            emptyBody={t('evaluation.empty_body')}
            filterBar={
              <EvaluationFilterBar
                statusFilter={s.statusFilter}
                chipIncomplete={s.chipIncomplete}
                onStatusChange={s.handleStatusFilterChange}
                methodologyFilter={s.methodologyFilter}
                onMethodologyChange={s.setMethodologyFilter}
                methodologyMap={s.methodologyMap}
                density={s.density}
                onDensityToggle={s.handleDensityToggle}
                chipMine={s.chipMine}
                showMineChip={!!s.currentUser}
                onToggleChip={s.toggleChip}
                anyChipActive={s.anyChipActive}
                onClearChips={s.clearChips}
              />
            }
          />
        </Card>
      ) : null}

      {/* Phase 1: PanelListSection moved to Drawer (slide-over).
          No longer stacked in the vertical flow — opened via CompletionBar trigger. */}
      <Drawer
        open={s.panelsDrawerOpen}
        title={`${t('panel.list.title')} (${s.panelsQuery.data?.items.length ?? 0})`}
        onClose={() => s.setPanelsDrawerOpen(false)}
        widthClassName="max-w-2xl"
        data-testid="panels-drawer"
      >
        <PanelListSection
          projectId={s.projectId}
          panels={s.panelsQuery.data?.items ?? []}
          loading={s.panelsQuery.isLoading}
          compact
        />
      </Drawer>

      {/* FE-2: multi-select "Add positions" dialog */}
      <AddPositionsDialog
        open={s.adding}
        positions={s.positionsQuery.data?.items ?? []}
        methodologies={s.methodologiesQuery.data?.items ?? []}
        existingKeys={s.existingEvalKeys}
        departmentNameOf={s.departmentNameOfPosition}
        defaultVersionId={s.selectedVersionId}
        onConfirm={async (versionId, positionIds) => {
          const result = await s.bulkCreateMutation.mutateAsync({
            items: positionIds.map((position_id) => ({
              position_id,
              methodology_version_id: versionId,
            })),
          });
          return result;
        }}
        onClose={() => s.setAdding(false)}
      />

      {s.openingPanel ? (
        <Suspense fallback={null}>
          <OpenPanelDialog
            open={s.openingPanel}
            projectId={s.projectId}
            methodologies={s.methodologiesQuery.data?.items ?? []}
            defaultVersionId={s.selectedVersionId}
            rosterSeed={s.rosterSeed}
            onConfirm={s.handleBulkOpenPanels}
            onCopyRosterToNext={(seed) => {
              s.setRosterSeed(seed);
              s.setOpeningPanel(false);
              setTimeout(() => s.setOpeningPanel(true), 0);
            }}
            onClose={() => {
              s.setOpeningPanel(false);
              s.setRosterSeed(null);
            }}
          />
        </Suspense>
      ) : null}

      <ConfirmDialog
        open={s.deleteTarget != null}
        destructive
        requireReason
        reasonMinLength={5}
        title={t('evaluation.delete.title')}
        body={t('evaluation.delete.body')}
        confirmLabel={t('common.delete')}
        reasonLabel={t('common.reason_label')}
        reasonPlaceholder={t('evaluation.delete.reason_placeholder')}
        onCancel={() => s.setDeleteTarget(null)}
        onConfirm={async (reason) => {
          if (!s.deleteTarget || !reason) return;
          await s.deleteMutation.mutateAsync({ id: s.deleteTarget.id, reason });
          s.setDeleteTarget(null);
        }}
      />

      {s.evalsQuery.isLoading ? <LoadingState /> : null}
    </div>
  );
}
