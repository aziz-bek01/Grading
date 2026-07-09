import { useTranslation } from 'react-i18next';
import { Card } from '@/shared/components/ui/Card';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { EmptyState } from '@/shared/components/feedback/EmptyState';
import { cn } from '@/shared/lib/cn';
import { BY_FACTOR_STICKY_TOP, BY_FACTOR_STICKY_Z } from './stickyOffset';
import { ByFactorHeader } from './ByFactorHeader';
import { ByFactorFilterBar } from './ByFactorFilterBar';
import { ByFactorTable } from './ByFactorTable';
import { ByFactorToolbar } from './ByFactorToolbar';
import { BulkScoreDialog } from './BulkScoreDialog';
import { BulkSubmitDialog } from './BulkSubmitDialog';
import { useByFactorViewState } from './useByFactorViewState';
import type { EvaluatorRole } from '../../panelTypes';

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
  /**
   * Multi-evaluator (EVALUATION_PANEL) additive props — OPTIONAL so the shared
   * K-sheet stays structurally unchanged for single-evaluator callers (FE-3).
   *
   * `selfRole`  — the current evaluator's seat role; renders a "Сиз: …" chip in
   *   the header strip so the evaluator knows which seat they fill.
   * `blind`     — when true a blind banner is shown while the panel is still
   *   collecting ("other evaluators' scores hidden until completion"). This is
   *   UX ONLY — the real isolation is the BE read guard that auto-scopes rows to
   *   the current evaluator; the FE simply never receives others' columns.
   */
  selfRole?: EvaluatorRole | null;
  blind?: boolean;
}

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
 *
 * FE-041 — this view is now a thin orchestrator: methodology/factor
 * resolution, filters, the rows query, bulk selection and the score/comment
 * handlers live in {@link useByFactorViewState}; the header strip, filter
 * bar, table and bottom toolbar each live in their own file. No behaviour,
 * testid or DOM change.
 */
export function EvaluationByFactorView(props: EvaluationByFactorViewProps) {
  const { t } = useTranslation();
  const s = useByFactorViewState(props);

  // ----- Loading / error / empty branches -----
  if (s.activeMethodologiesQuery.isLoading || s.versionQuery.isLoading) {
    return <LoadingState />;
  }

  // Evaluator-specific empty state: the evaluator has EVALUATION_READ but no
  // METHODOLOGY_READ, and the /my endpoint returned an empty list — they have
  // no active methodology assigned in this project.
  if (
    !s.canMethodologyRead &&
    !s.activeMethodologiesQuery.isLoading &&
    s.selectableMethodologies.length === 0
  ) {
    return (
      <div role="status">
        <EmptyState
          title={t('evaluation.byFactor.no_assigned_methodology_title')}
          body={t('evaluation.byFactor.no_assigned_methodology_body')}
        />
      </div>
    );
  }

  if (!s.activeMethodology || !s.versionQuery.data) {
    return (
      <EmptyState
        title={t('evaluation.byFactor.no_methodology_title')}
        body={t('evaluation.byFactor.no_methodology_body')}
      />
    );
  }
  if (!s.activeFactor) {
    return (
      <EmptyState
        title={t('evaluation.byFactor.no_factors_title')}
        body={t('evaluation.byFactor.no_factors_body')}
      />
    );
  }

  return (
    // FE-9: flex-col page so the table region can grow (flex-1 / min-h-0) and
    // the page-level scroll carries ~200 rows without virtualization. Tighter
    // vertical rhythm (space-y-2) reclaims wasted top/bottom chrome.
    <div
      className="flex flex-col min-h-0 space-y-2"
      data-testid="evaluation-by-factor"
    >
      {/* Single sticky region: methodology line + factor tabs + filter bar
          stack together and stay pinned while scrolling 200 rows. ONE sticky
          wrapper (no per-element top-offset math) so the filter bar can never
          overlap/hide the factor tabs — the strip height varies (e.g. the blind
          banner adds rows), which made the old hand-computed offsets cover the
          tabs. The evaluator can always see WHICH factor they are scoring. */}
      <div className={cn('sticky bg-background', BY_FACTOR_STICKY_TOP, BY_FACTOR_STICKY_Z)}>
        <ByFactorHeader
          selectableMethodologies={s.selectableMethodologies}
          activeMethodology={s.activeMethodology}
          methodologyName={s.methodologyName}
          scoringMode={s.scoringMode!}
          onMethodologyChange={s.handleMethodologyChange}
          selfRole={s.selfRole ?? null}
          blind={s.blind ?? false}
          factors={s.factors}
          activeFactorId={s.activeFactor.id}
          completionMap={s.completionMap}
          onFactorChange={s.onFactorChange}
        />

        {/* Filter bar — part of the single sticky region above (it is no
            longer its own sticky layer, which used to overlap the factor tabs). */}
        <Card compact className="mt-2 bg-background">
          <ByFactorFilterBar
            departmentId={s.departmentId}
            onDepartmentChange={s.changeDepartmentFilter}
            departmentOptions={s.departmentOptions}
            statusFilter={s.statusFilter}
            onStatusChange={s.changeStatusFilter}
            onlyUnfilled={s.onlyUnfilled}
            onOnlyUnfilledChange={s.changeOnlyUnfilled}
            search={s.search}
            onSearchChange={s.changeSearch}
          />
        </Card>
      </div>

      {/*
        Full-width K-sheet table. The old right-side rubric reference panel
        (and its narrow-screen slide-over Drawer) were RETIRED per the PO
        redesign: evaluators now read each level's description in-line via
        the per-row LevelDropSelect, so a separate rubric column would only
        duplicate that text and re-introduce the point-anchoring it removes.
        The table reclaims the full content width on every breakpoint.
      */}
      <ByFactorTable
        isError={s.rowsQuery.isError}
        isLoading={s.rowsQuery.isLoading}
        onRetry={() => s.rowsQuery.refetch()}
        rows={s.rows}
        activeFactor={s.activeFactor}
        activeRow={s.activeRow}
        bulkSet={s.bulkSet}
        canEdit={s.canEdit}
        canSeePoints={s.canSeePoints}
        allSelected={s.allSelected}
        onToggleAll={s.toggleAll}
        onScoreChange={s.handleScoreChange}
        onCommentChange={s.handleCommentChange}
        onRowSelect={s.setActiveRowId}
        onBulkToggle={s.toggleRow}
      />

      <ByFactorToolbar
        selectedCount={s.bulkSet.size}
        totalElements={s.totalElements}
        saving={
          s.rowsQuery.isFetching ||
          s.bulkScoreMutation.isPending ||
          s.bulkSubmitMutation.isPending
        }
        onBulkScoreOpen={() => s.setBulkScoreOpen(true)}
        onBulkSubmitOpen={() => s.setBulkSubmitOpen(true)}
        page={s.page}
        totalPages={s.totalPages}
        onPageChange={s.setPage}
      />

      <BulkScoreDialog
        open={s.bulkScoreOpen}
        factor={s.activeFactor}
        selectedCount={s.bulkSet.size}
        canSeePoints={s.canSeePoints}
        onClose={() => s.setBulkScoreOpen(false)}
        onConfirm={async (factorLevelId, reason) => {
          const result = await s.bulkScoreMutation.mutateAsync({
            evaluation_ids: Array.from(s.bulkSet),
            factor_level_id: factorLevelId,
            reason,
          });
          if (result.failed.length === 0) s.clearBulkSet();
          return result;
        }}
      />
      <BulkSubmitDialog
        open={s.bulkSubmitOpen}
        selectedCount={s.bulkSet.size}
        onClose={() => s.setBulkSubmitOpen(false)}
        onConfirm={async (reason) => {
          const result = await s.bulkSubmitMutation.mutateAsync({
            evaluation_ids: Array.from(s.bulkSet),
            reason,
          });
          if (result.failed.length === 0) s.clearBulkSet();
          return result;
        }}
      />
    </div>
  );
}
