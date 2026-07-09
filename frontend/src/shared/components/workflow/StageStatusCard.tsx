import { useTranslation } from 'react-i18next';
import { Card } from '@/shared/components/ui/Card';
import { formatDateSafe } from '@/shared/lib/dates';
import type { WorkflowStageProgress } from './workflowTypes';

interface StageStatusCardProps {
  stage: WorkflowStageProgress;
  /** Optional CTA button to jump into the stage. */
  onOpenStage?: () => void;
  openLabel?: string;
}

/** Detail panel beside the stepper — shows completion, responsible person, last activity. */
export function StageStatusCard({ stage, onOpenStage, openLabel }: StageStatusCardProps) {
  const { t, i18n } = useTranslation();
  const last = stage.lastUpdatedAt
    ? formatDateSafe(stage.lastUpdatedAt, i18n.language)
    : null;
  return (
    <Card
      title={t(`workflow.stage.${stage.stage}`)}
      subtitle={t(`workflow.stageStatus.${stage.status}`)}
    >
      <div className="space-y-3">
        <div>
          <div className="flex items-center justify-between text-xs text-text-secondary">
            <span>{t('workflow.completion', { percent: stage.completionPercent })}</span>
          </div>
          <div className="mt-2 h-2 w-full rounded-full bg-divider overflow-hidden">
            <div
              className="h-full bg-primary-500"
              style={{ width: `${Math.min(100, stage.completionPercent)}%` }}
              aria-hidden
            />
          </div>
        </div>
        {stage.responsibleUserName ? (
          <div className="text-sm">
            <span className="text-text-secondary">{t('workflow.responsible')}: </span>
            <span className="text-text-primary font-medium">{stage.responsibleUserName}</span>
          </div>
        ) : null}
        {last ? (
          <div className="text-xs text-text-muted">
            {t('workflow.last_activity', { time: last, actor: stage.lastUpdatedBy ?? '—' })}
          </div>
        ) : null}
        {onOpenStage ? (
          <button
            type="button"
            onClick={onOpenStage}
            className="text-sm text-primary-700 hover:underline"
            disabled={stage.status === 'LOCKED_FUTURE'}
          >
            {openLabel ?? t('workflow.open_stage')}
          </button>
        ) : null}
      </div>
    </Card>
  );
}
