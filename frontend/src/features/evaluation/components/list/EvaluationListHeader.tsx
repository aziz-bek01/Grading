import { useTranslation } from 'react-i18next';
import { Button } from '@/shared/components/ui/Button';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';

interface EvaluationListHeaderProps {
  isCommitteeScorer: boolean;
  onOpenPanel: () => void;
  onAddPositions: () => void;
}

/**
 * Page title + "Open panel" / "Add positions" CTAs. Extracted from
 * `EvaluationListPage` (FE-041) — both CTAs are hidden from a committee
 * scorer (they never manage project-wide panels/positions).
 */
export function EvaluationListHeader({
  isCommitteeScorer,
  onOpenPanel,
  onAddPositions,
}: EvaluationListHeaderProps) {
  const { t } = useTranslation();
  return (
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
              onClick={onOpenPanel}
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
                onClick={onAddPositions}
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
  );
}
