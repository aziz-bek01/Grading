import { useTranslation } from 'react-i18next';
import { useParams } from 'react-router-dom';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { EmptyState } from '@/shared/components/feedback/EmptyState';
import { Card } from '@/shared/components/ui/Card';
import { pickLocalized } from '@/shared/lib/localized';
import { useApprovalRequest } from '../hooks/useApprovals';
import { ApprovalStatusBadge } from '../components/ApprovalStatusBadge';
import { ApprovalStepCard } from '../components/ApprovalStepCard';
import { ApprovalDecisionsList } from '../components/ApprovalDecisionsList';
import { ApprovalActionsBar } from '../components/ApprovalActionsBar';

export function ApprovalDetailsPage() {
  const { t, i18n } = useTranslation();
  const { approvalId } = useParams<{ approvalId: string }>();
  const approval = useApprovalRequest(approvalId);

  if (approval.isLoading) return <LoadingState />;
  if (approval.error) return <ErrorState onRetry={() => approval.refetch()} />;
  if (!approval.data) return <EmptyState />;

  const r = approval.data;
  const steps = [...r.steps].sort((a, b) => a.stepOrder - b.stepOrder);
  const currentStep = steps.find((s) => s.status === 'PENDING') ?? null;
  const entityLabel = r.entityLabel ? pickLocalized(r.entityLabel, i18n.language) : r.entityId;

  return (
    <div className="space-y-6" data-testid="approval-details-page">
      <Breadcrumbs />
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl text-text-primary">{entityLabel}</h1>
          <p className="text-sm text-text-secondary mt-1">
            {t(`approval.entityType.${r.entityType}`)} · {r.entityId}
          </p>
        </div>
        <ApprovalStatusBadge status={r.status} />
      </header>

      <ApprovalActionsBar request={r} currentStep={currentStep} />

      <Card title={t('approval.steps_title')} compact>
        <ol className="space-y-3">
          {steps.map((s) => (
            <li key={s.id}>
              <ApprovalStepCard step={s} isCurrent={s.id === currentStep?.id} />
            </li>
          ))}
        </ol>
      </Card>

      <ApprovalDecisionsList decisions={r.decisions} />
    </div>
  );
}
