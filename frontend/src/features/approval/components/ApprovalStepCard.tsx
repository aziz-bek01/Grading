import { useTranslation } from 'react-i18next';
import { CheckCircle2, Clock, AlertTriangle, MinusCircle } from 'lucide-react';
import { cn } from '@/shared/lib/cn';
import { formatDateSafe } from '@/shared/lib/dates';
import { shortId } from '@/shared/lib/shortId';
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
    ? formatDateSafe(step.decidedAt, i18n.language, t('common.dash'))
    : null;
  // Prefer the BE-resolved name; fall back to a SHORT id, never a raw UUID (FE-3).
  const decidedByDisplay = step.decidedByName ?? shortId(step.decidedByUserId);
  // Approver name prefers the BE-resolved name; falls back to a short id only
  // when there is an explicit approver but no resolved name.
  const approverDisplay =
    step.approverName ?? (step.approverUserId ? shortId(step.approverUserId) : null);
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
        {approverDisplay ? (
          <span>
            {t('approval.step.approver')}: <strong>{approverDisplay}</strong>
          </span>
        ) : step.requiredPermission ? (
          <span>
            {t('approval.step.required_permission')}: <code>{step.requiredPermission}</code>
          </span>
        ) : (
          <span>{t('approval.step.any_approver')}</span>
        )}
      </div>
      {decidedByDisplay && decided ? (
        <div className="text-xs text-text-muted">
          {t('approval.step.decided_by', { name: decidedByDisplay, time: decided })}
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
