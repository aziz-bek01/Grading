import { useTranslation } from 'react-i18next';
import { Card } from '@/shared/components/ui/Card';
import type { WorkflowStageProgress } from './workflowTypes';

interface StageStatusCardProps {
  stage: WorkflowStageProgress;
}

/** Detail panel beside the stepper — shows completion, responsible role, blockers. */
export function StageStatusCard({ stage }: StageStatusCardProps) {
  const { t } = useTranslation();
  return (
    <Card title={t(`workflow.stage.${stage.key}`)} subtitle={t(`workflow.stageStatus.${stage.status}`)}>
      <div className="space-y-3">
        <div>
          <div className="flex items-center justify-between text-xs text-text-secondary">
            <span>{t('workflow.completion', { percent: stage.completion_percent })}</span>
            {stage.total_items !== undefined ? (
              <span>
                {stage.completed_items ?? 0} / {stage.total_items}
              </span>
            ) : null}
          </div>
          <div className="mt-2 h-2 w-full rounded-full bg-divider overflow-hidden">
            <div
              className="h-full bg-primary-500"
              style={{ width: `${Math.min(100, stage.completion_percent)}%` }}
              aria-hidden
            />
          </div>
        </div>
        {stage.responsible_role ? (
          <div className="text-sm">
            <span className="text-text-secondary">Responsible: </span>
            <span className="text-text-primary font-medium">{stage.responsible_role}</span>
          </div>
        ) : null}
        {stage.blockers.length > 0 ? (
          <div>
            <div className="text-xs uppercase tracking-wide text-text-muted mb-1">{t('workflow.blockers')}</div>
            <ul className="text-sm text-danger-700 space-y-1">
              {stage.blockers.map((b) => (
                <li key={b.code}>
                  {b.code} ({b.count})
                </li>
              ))}
            </ul>
          </div>
        ) : null}
      </div>
    </Card>
  );
}
