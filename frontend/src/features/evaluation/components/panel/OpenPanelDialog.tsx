import { useTranslation } from 'react-i18next';
import { Modal } from '@/shared/components/ui/Modal';
import type { Methodology } from '@/features/methodology/types';
import type { BulkCreatePanelsResult, PanelEvaluatorDraft } from '../../panelTypes';
import { usePanelWizardState, type RosterSeed } from './usePanelWizardState';
import { WizardStepIndicator } from './WizardStepIndicator';
import { WizardStepDepartment } from './WizardStepDepartment';
import { WizardStepPositions } from './WizardStepPositions';
import { WizardStepRoster } from './WizardStepRoster';
import { WizardResultBanner } from './WizardResultBanner';
import { WizardFooterNav } from './WizardFooterNav';

export type { RosterSeed } from './usePanelWizardState';

interface Props {
  open: boolean;
  projectId: string;
  methodologies: Methodology[];
  defaultVersionId?: string | null;
  /** Optional roster seed (copy-roster from the previous department). */
  rosterSeed?: RosterSeed | null;
  /**
   * Confirm handler — fires ONE bulk-create with the shared roster. Returns the
   * BE per-position failure collector so the wizard can surface partial failures
   * inline (mirrors AddPositionsDialog's retry behaviour).
   */
  onConfirm: (
    versionId: string,
    positionIds: string[],
    roster: PanelEvaluatorDraft[],
  ) => Promise<BulkCreatePanelsResult>;
  /** Copy-roster to next department: keep HR + externals, clear dept director. */
  onCopyRosterToNext?: (seed: RosterSeed) => void;
  onClose: () => void;
}

/**
 * Dept-first 3-step panel wizard (FE-1..FE-6).
 *
 * REFACTOR of the former flat-select dialog — same shared `<Modal>` overlay
 * chrome (src/shared/components/ui/Modal.tsx), the same EvaluatorPicker rows / PanelRoleChip / makeMandatoryRows /
 * addExtra/removeExtra roster machinery, and the same partial-fail result block.
 * The flat position <select> is replaced by:
 *   Step 1 DEPARTMENT  — searchable single-select dept tree + coverage badges.
 *   Step 2 POSITIONS   — server-filtered candidate list (multi-select, select-all,
 *                        search); already-paneled rows DISABLED + marked.
 *   Step 3 COMMISSION  — shared roster (dept-director suggested, HR last-used,
 *                        3-4 externals); Confirm fires ONE bulk-create.
 *
 * FE-041 — this component is now a thin orchestrator: ALL state / derived data
 * / handlers live in {@link usePanelWizardState}, and each step's markup lives
 * in its own file (WizardStepDepartment / WizardStepPositions / WizardStepRoster
 * / WizardResultBanner / WizardFooterNav). No behaviour, testid or DOM change.
 */
export function OpenPanelDialog({ open, ...rest }: Props) {
  if (!open) return null;
  return <OpenPanelDialogBody {...rest} />;
}

function OpenPanelDialogBody(props: Omit<Props, 'open'>) {
  const { t } = useTranslation();
  const wizard = usePanelWizardState(props);

  return (
    <Modal
      open
      onClose={props.onClose}
      labelledBy="open-panel-title"
      size="lg"
      dismissible={!wizard.submitting}
      className="bg-surface rounded-xl shadow-lg border border-border max-h-[85vh] flex flex-col p-6 space-y-4"
    >
      <>
        <header className="shrink-0">
          <h2 id="open-panel-title" className="text-lg text-text-primary">
            {t('panel.dialog.title')}
          </h2>
          <WizardStepIndicator step={wizard.step} />
        </header>

        {wizard.step === 1 ? (
          <WizardStepDepartment
            deptSearch={wizard.deptSearch}
            onDeptSearchChange={wizard.setDeptSearch}
            treeQuery={wizard.treeQuery}
            departmentId={wizard.departmentId}
            onSelect={wizard.selectDepartment}
            coverageOf={wizard.coverageOf}
          />
        ) : null}

        {wizard.step === 2 ? (
          <WizardStepPositions
            departmentName={wizard.departmentName}
            versionId={wizard.versionId}
            onVersionChange={wizard.changeVersion}
            activeMethodologies={wizard.activeMethodologies}
            posSearch={wizard.posSearch}
            onPosSearchChange={wizard.setPosSearch}
            allSelected={wizard.allSelected}
            onToggleAll={wizard.toggleAll}
            selectablePositionsCount={wizard.selectablePositions.length}
            includeSubtree={wizard.includeSubtree}
            onToggleSubtree={wizard.toggleSubtree}
            positionsLoading={wizard.deptPositionsQuery.isLoading}
            candidates={wizard.candidates}
            fullyPaneled={wizard.fullyPaneled}
            isPaneled={wizard.isPaneled}
            panelIdFor={wizard.panelIdFor}
            selectedPositions={wizard.selectedPositions}
            onTogglePosition={wizard.togglePosition}
            panelDetailHref={wizard.panelDetailHref}
          />
        ) : null}

        {wizard.step === 3 ? (
          <WizardStepRoster
            selectedPositionsCount={wizard.selectedPositions.size}
            departmentName={wizard.departmentName}
            effectiveRows={wizard.effectiveRows}
            canAddExternal={wizard.canAddExternal}
            onAddExtra={wizard.addExtra}
            onRemoveExtra={wizard.removeExtra}
            suggestionsError={wizard.suggestionsQuery.isError}
            deptDirectorCandidates={wizard.suggestionsQuery.data?.dept_director_candidates}
            chosenUserIds={wizard.chosenUserIds}
            onSetRow={wizard.setRow}
          />
        ) : null}

        <WizardResultBanner
          result={wizard.result}
          error={wizard.error}
          commissionSucceeded={wizard.commissionSucceeded}
          canCopyRoster={!!wizard.onCopyRosterToNext}
          onCopyRoster={wizard.handleCopyRoster}
        />

        <WizardFooterNav
          step={wizard.step}
          submitting={wizard.submitting}
          selectedPositionsCount={wizard.selectedPositions.size}
          canAdvanceStep1={wizard.canAdvanceStep1}
          canAdvanceStep2={wizard.canAdvanceStep2}
          canConfirm={wizard.canConfirm}
          onBack={wizard.goBack}
          onNext={wizard.goNext}
          onCancel={props.onClose}
          onConfirm={wizard.handleSubmit}
        />
      </>
    </Modal>
  );
}
