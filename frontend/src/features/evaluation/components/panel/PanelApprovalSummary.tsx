import { useTranslation } from 'react-i18next';
import { Card } from '@/shared/components/ui/Card';
import { AssignedGradeBadge } from '@/features/grade-structure/components/AssignedGradeBadge';
import { usePanelDetail } from '../../hooks/usePanels';
import { PanelAveragedResult } from './PanelAveragedResult';

interface Props {
  /** The approval request's entity_id IS the panel id (entity_type=EVALUATION_PANEL). */
  panelId: string;
}

/**
 * Read-only campaign-result summary block rendered on the CEO approval detail
 * page between the header and the steps list (FE-6 / UX 6.5).
 *
 * REUSES the SAME PanelAveragedResult table the result view uses (averaged
 * total + per-factor averages + per-evaluator breakdown), gated identically by
 * CAMPAIGN_RESULTS_VIEW. Loads the panel by entity id to read its status (drives
 * the gate) and the assigned grade (post-approval). NO salary data here.
 */
export function PanelApprovalSummary({ panelId }: Props) {
  const { t } = useTranslation();
  const detailQuery = usePanelDetail(panelId);
  const panel = detailQuery.data?.panel;

  if (!panel) return null;

  return (
    <Card title={t('panel.approval_summary.title')} compact data-testid="panel-approval-summary">
      <PanelAveragedResult
        panelId={panel.id}
        status={panel.status}
        defaultShowEvaluators
        gradeBadge={
          panel.assigned_grade_number != null ? (
            <AssignedGradeBadge gradeNumber={panel.assigned_grade_number} />
          ) : undefined
        }
      />
    </Card>
  );
}
