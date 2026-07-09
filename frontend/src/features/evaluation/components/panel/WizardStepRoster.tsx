import { Plus, X } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/shared/components/ui/Button';
import {
  MANDATORY_EVALUATOR_ROLES,
  type PanelEvaluatorDraft,
  type RosterDeptDirectorCandidate,
} from '../../panelTypes';
import { PanelRoleChip } from './PanelRoleChip';
import { EvaluatorPicker } from './EvaluatorPicker';

interface WizardStepRosterProps {
  selectedPositionsCount: number;
  departmentName: string;
  effectiveRows: PanelEvaluatorDraft[];
  canAddExternal: boolean;
  onAddExtra: () => void;
  onRemoveExtra: (index: number) => void;
  suggestionsError: boolean;
  deptDirectorCandidates: RosterDeptDirectorCandidate[] | undefined;
  chosenUserIds: string[];
  onSetRow: (index: number, userId: string, userName: string | null) => void;
}

/**
 * Step 3 — COMMISSION ROSTER: shared roster (dept-director suggested, HR
 * last-used, 3-4 externals). Confirm (rendered by the footer nav) fires ONE
 * bulk-create for the whole selection.
 */
export function WizardStepRoster({
  selectedPositionsCount,
  departmentName,
  effectiveRows,
  canAddExternal,
  onAddExtra,
  onRemoveExtra,
  suggestionsError,
  deptDirectorCandidates,
  chosenUserIds,
  onSetRow,
}: WizardStepRosterProps) {
  const { t } = useTranslation();

  return (
    <div className="flex flex-col flex-1 min-h-0 space-y-2" data-testid="wizard-step-3">
      <p className="shrink-0 text-sm text-text-secondary">
        {t('panel.wizard.step_3_body', {
          count: selectedPositionsCount,
          department: departmentName,
        })}
      </p>
      <div className="shrink-0 flex items-center justify-between">
        <span className="text-sm font-medium">{t('panel.dialog.evaluators')}</span>
        <Button
          variant="secondary"
          onClick={onAddExtra}
          disabled={!canAddExternal}
          data-testid="open-panel-add-evaluator"
          className="h-8 px-2 text-xs"
        >
          <Plus size={14} aria-hidden /> {t('panel.dialog.add_evaluator')}
        </Button>
      </div>
      <p className="shrink-0 text-xs text-text-muted" data-testid="open-panel-helper">
        {t('panel.dialog.min_helper')}
      </p>
      {suggestionsError ? (
        <p
          className="shrink-0 text-xs text-warning-700"
          data-testid="wizard-suggestion-error"
        >
          {t('panel.wizard.roster.suggestion_error')}
        </p>
      ) : null}

      <ul
        className="flex-1 min-h-0 overflow-y-auto space-y-2"
        data-testid="open-panel-roster"
      >
        {effectiveRows.map((row, index) => {
          const isMandatory = MANDATORY_EVALUATOR_ROLES.includes(row.role);
          const isDeptDirSuggested =
            row.role === 'DEPARTMENT_DIRECTOR' &&
            !!row.evaluator_user_id &&
            (deptDirectorCandidates ?? []).some(
              (c) => c.user_id === row.evaluator_user_id,
            );
          return (
            <li
              key={`${row.role}-${index}`}
              data-testid={`open-panel-row-${index}`}
              className="flex items-center gap-2"
            >
              <span className="min-w-[9rem] shrink-0">
                <PanelRoleChip role={row.role} />
                {isDeptDirSuggested ? (
                  <span
                    className="block text-[10px] text-text-muted mt-0.5"
                    data-testid={`open-panel-suggested-${index}`}
                  >
                    {t('panel.wizard.roster.dept_director_suggested')}
                  </span>
                ) : null}
              </span>
              <span className="flex-1 min-w-0">
                <EvaluatorPicker
                  selectId={`open-panel-picker-${index}`}
                  value={row.evaluator_user_id ?? ''}
                  onChange={(userId, name) => onSetRow(index, userId, name)}
                  excludeUserIds={chosenUserIds.filter(
                    (id) => id !== row.evaluator_user_id,
                  )}
                />
              </span>
              {!isMandatory ? (
                <button
                  type="button"
                  onClick={() => onRemoveExtra(index)}
                  aria-label={t('panel.dialog.remove_evaluator')}
                  data-testid={`open-panel-remove-${index}`}
                  className="text-text-muted hover:text-danger-600 p-1"
                >
                  <X size={16} aria-hidden />
                </button>
              ) : (
                <span className="w-7" aria-hidden />
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
