import { useTranslation } from 'react-i18next';
import {
  Settings,
  Network as Sitemap,
  Briefcase,
  ClipboardList,
  Scale,
  CheckSquare,
  Sliders,
  Layers,
  DollarSign,
  FileText,
  Archive,
  CheckCircle2,
  Clock,
  Lock,
  AlertTriangle,
  Circle,
} from 'lucide-react';
import {
  WORKFLOW_STAGES,
  type WorkflowStageKey,
  type WorkflowStageProgress,
  type WorkflowStageStatus,
} from './workflowTypes';
import { cn } from '@/shared/lib/cn';

const stageIcon: Record<WorkflowStageKey, React.ReactNode> = {
  SETUP: <Settings size={16} aria-hidden />,
  ORGANIZATION: <Sitemap size={16} aria-hidden />,
  POSITIONS: <Briefcase size={16} aria-hidden />,
  JOB_PROFILES: <ClipboardList size={16} aria-hidden />,
  METHODOLOGY: <Scale size={16} aria-hidden />,
  EVALUATION: <CheckSquare size={16} aria-hidden />,
  CALIBRATION: <Sliders size={16} aria-hidden />,
  GRADES: <Layers size={16} aria-hidden />,
  COMPENSATION: <DollarSign size={16} aria-hidden />,
  REPORTS: <FileText size={16} aria-hidden />,
  ARCHIVE: <Archive size={16} aria-hidden />,
};

const statusIcon: Record<WorkflowStageStatus, React.ReactNode> = {
  PENDING: <Circle size={12} aria-hidden />,
  IN_PROGRESS: <Clock size={12} aria-hidden />,
  COMPLETE: <CheckCircle2 size={12} aria-hidden />,
  BLOCKED: <AlertTriangle size={12} aria-hidden />,
  LOCKED_FUTURE: <Lock size={12} aria-hidden />,
};

const statusClass: Record<WorkflowStageStatus, string> = {
  PENDING: 'border-border-strong text-text-secondary bg-surface',
  IN_PROGRESS: 'border-info-500/40 text-info-600 bg-info-50',
  COMPLETE: 'border-success-500/40 text-success-700 bg-success-50',
  BLOCKED: 'border-danger-500/40 text-danger-700 bg-danger-50',
  LOCKED_FUTURE: 'border-border-strong text-text-muted bg-divider',
};

interface WorkflowStepperProps {
  stages: WorkflowStageProgress[];
  activeKey?: WorkflowStageKey;
  onSelect?: (key: WorkflowStageKey) => void;
  className?: string;
}

/**
 * 11-stage horizontal stepper from design-foundation §16.4.
 * Wraps to 2 rows below 1366px (handled by parent grid via Tailwind).
 * Color is always doubled by icon/text (§16.11 a11y).
 */
export function WorkflowStepper({ stages, activeKey, onSelect, className }: WorkflowStepperProps) {
  const { t } = useTranslation();
  // Ensure all 11 stages are present in canonical order, even if API returned a subset.
  const byKey = new Map(stages.map((s) => [s.key, s] as const));
  const ordered = WORKFLOW_STAGES.map<WorkflowStageProgress>(
    (key) =>
      byKey.get(key) ?? {
        key,
        status: 'PENDING',
        completion_percent: 0,
        blockers: [],
      },
  );

  return (
    <nav aria-label={t('workflow.title')} className={className} data-testid="workflow-stepper">
      <ol className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6 2xl:grid-cols-11 gap-2">
        {ordered.map((stage, idx) => {
          const isActive = activeKey === stage.key;
          const isLocked = stage.status === 'LOCKED_FUTURE';
          return (
            <li key={stage.key}>
              <button
                type="button"
                onClick={() => onSelect?.(stage.key)}
                disabled={isLocked}
                aria-current={isActive ? 'step' : undefined}
                aria-disabled={isLocked || undefined}
                data-stage={stage.key}
                data-status={stage.status}
                className={cn(
                  'w-full text-left rounded-lg border p-3 transition-colors',
                  statusClass[stage.status],
                  isActive && 'ring-2 ring-primary-500',
                  isLocked ? 'cursor-not-allowed opacity-80' : 'hover:bg-primary-50/40',
                )}
              >
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <span aria-hidden>{stageIcon[stage.key]}</span>
                    <span className="text-xs uppercase tracking-wide font-medium text-text-muted">{idx + 1}</span>
                  </div>
                  <span aria-hidden>{statusIcon[stage.status]}</span>
                </div>
                <div className="mt-2 text-sm font-medium text-text-primary truncate">
                  {t(`workflow.stage.${stage.key}`)}
                </div>
                <div className="mt-1 text-xs text-text-secondary">
                  {t(`workflow.stageStatus.${stage.status}`)}
                </div>
                {stage.completion_percent > 0 && stage.status !== 'LOCKED_FUTURE' ? (
                  <div className="mt-2 h-1.5 w-full rounded-full bg-divider overflow-hidden">
                    <div
                      className="h-full bg-primary-500"
                      style={{ width: `${Math.min(100, stage.completion_percent)}%` }}
                      aria-hidden
                    />
                  </div>
                ) : null}
              </button>
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
