import { useTranslation } from 'react-i18next';
import { CheckCircle2, Clock, AlertTriangle, MinusCircle } from 'lucide-react';
import { cn } from '@/shared/lib/cn';
import type { ApprovalStep, ApprovalStepStatus } from '../types';

const icon: Record<ApprovalStepStatus, React.ReactNode> = {
  PENDING: <Clock size={14} aria-hidden />,
  APPROVED: <CheckCircle2 size={14} aria-hidden />,
  REJECTED: <AlertTriangle size={14} aria-hidden />,
  SKIPPED: <MinusCircle size={14} aria-hidden />,
};

const tone: Record<ApprovalStepStatus, string> = {
  PENDING: 'border-info-500/40 bg-info-50 text-info-700',
  APPROVED: 'border-success-500/40 bg-success-50 text-success-700',
  REJECTED: 'border-danger-500/40 bg-danger-50 text-danger-700',
  SKIPPED: 'border-border-strong bg-divider text-text-muted',
};

interface Props {
  step: ApprovalStep;
  isCurrent?: boolean;
}

export function ApprovalStepCard({ step, isCurrent }: Props) {
  const { t, i18n } = useTranslation();
  const decided = step.decidedAt
    ? new Date(step.decidedAt).toLocaleString(i18n.language)
    : null;
  return (
    <div
      className={cn(
        'rounded-lg border p-4 space-y-2',
        tone[step.status],
        isCurrent && 'ring-2 ring-primary-500',
      )}
      data-testid="approval-step-card"
      data-step-status={step.status}
    >
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          {icon[step.status]}
          <span className="font-medium text-text-primary">
            {t('approval.step.order', { order: step.stepOrder })}
          </span>
        </div>
        <span className="text-xs">{t(`approval.stepStatus.${step.status}`)}</span>
      </div>
      <div className="text-sm text-text-secondary">
        {step.approverName ? (
          <span>
            {t('approval.step.approver')}: <strong>{step.approverName}</strong>
          </span>
        ) : step.requiredPermission ? (
          <span>
            {t('approval.step.required_permission')}: <code>{step.requiredPermission}</code>
          </span>
        ) : (
          <span>{t('approval.step.any_approver')}</span>
        )}
      </div>
      {step.decidedByName && decided ? (
        <div className="text-xs text-text-muted">
          {t('approval.step.decided_by', { name: step.decidedByName, time: decided })}
        </div>
      ) : null}
      {step.reason ? (
        <div className="text-sm text-text-primary border-l-2 border-danger-500 pl-3">
          <div className="text-xs uppercase tracking-wide text-text-muted">
            {t('approval.step.reason')}
          </div>
          <p className="mt-1 whitespace-pre-wrap">{step.reason}</p>
        </div>
      ) : null}
      {step.notes ? (
        <div className="text-sm text-text-primary border-l-2 border-primary-500 pl-3">
          <div className="text-xs uppercase tracking-wide text-text-muted">
            {t('approval.step.notes')}
          </div>
          <p className="mt-1 whitespace-pre-wrap">{step.notes}</p>
        </div>
      ) : null}
    </div>
  );
}
