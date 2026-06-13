import { useTranslation } from 'react-i18next';
import { useParams } from 'react-router-dom';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { EmptyState } from '@/shared/components/feedback/EmptyState';
import { NoAccessState } from '@/shared/components/feedback/NoAccessState';
import { Card } from '@/shared/components/ui/Card';
import { pickLocalized } from '@/shared/lib/localized';
import { shortId } from '@/shared/lib/shortId';
import { ApiError } from '@/shared/api/apiError';
import { useApprovalRequest } from '../hooks/useApprovals';
import { ApprovalStatusBadge } from '../components/ApprovalStatusBadge';
import { ApprovalStepCard } from '../components/ApprovalStepCard';
import { ApprovalDecisionsList } from '../components/ApprovalDecisionsList';
import { ApprovalActionsBar } from '../components/ApprovalActionsBar';
import { PanelApprovalSummary } from '@/features/evaluation/components/panel/PanelApprovalSummary';

export function ApprovalDetailsPage() {
  const { t, i18n } = useTranslation();
  const { approvalId } = useParams<{ approvalId: string }>();
  const approval = useApprovalRequest(approvalId);

  if (approval.isLoading) return <LoadingState />;
  if (approval.error) {
    const err = approval.error;
    // A 403 (not an approver) OR a 404 means the decider cannot reach THIS
    // request in the active company-client context — e.g. another tenant, or a
    // stale inbox row clicked after a tenant/context change (the BE maps
    // TenantAccessDeniedException → 404 by design, no existence reveal). Both
    // surface the SAME specific, non-enumerating no-access state instead of the
    // generic retryable crash card (FE-5). Genuinely unexpected errors (network
    // status 0, 5xx) still fall through to the retryable ErrorState.
    if (err instanceof ApiError && (err.isForbidden() || err.isNotFound())) {
      return <NoAccessState />;
    }
    const correlationId = err instanceof ApiError ? err.correlationId : undefined;
    return <ErrorState correlationId={correlationId} onRetry={() => approval.refetch()} />;
  }
  if (!approval.data) return <EmptyState />;

  const r = approval.data;
  // Defensive: the adapter guarantees arrays, but guard so a malformed
  // payload can never make `.sort` / `.map` throw (scout: :25 / :54).
  const steps = [...(r.steps ?? [])].sort((a, b) => a.stepOrder - b.stepOrder);
  const decisions = r.decisions ?? [];
  const currentStep = steps.find((s) => s.status === 'PENDING') ?? null;
  const entityLabelText = r.entityLabel ? pickLocalized(r.entityLabel, i18n.language) : '';
  // Missing label degrades to a SHORT id, never a raw 36-char UUID header (FE-2).
  const entityLabel = entityLabelText.length > 0 ? entityLabelText : shortId(r.entityId);

  return (
    <div className="space-y-6" data-testid="approval-details-page">
      <Breadcrumbs />
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl text-text-primary">{entityLabel}</h1>
          <p className="text-sm text-text-secondary mt-1">
            {t(`approval.entityType.${r.entityType}`)} ·{' '}
            <span title={r.entityId}>{shortId(r.entityId)}</span>
          </p>
        </div>
        <ApprovalStatusBadge status={r.status} />
      </header>

      <ApprovalActionsBar request={r} currentStep={currentStep} />

      {/* FE-6: for EVALUATION_PANEL the CEO sees a read-only averaged result
          summary (total + per-factor averages + per-evaluator breakdown) BETWEEN
          the header and the steps. Reuses the SAME PanelAveragedResult table,
          gated by CAMPAIGN_RESULTS_VIEW. entity_id IS the panel id. */}
      {r.entityType === 'EVALUATION_PANEL' && r.entityId ? (
        <PanelApprovalSummary panelId={r.entityId} />
      ) : null}

      <Card title={t('approval.steps_title')} compact>
        <ol className="space-y-3">
          {steps.map((s) => (
            <li key={s.id}>
              <ApprovalStepCard step={s} isCurrent={s.id === currentStep?.id} />
            </li>
          ))}
        </ol>
      </Card>

      <ApprovalDecisionsList decisions={decisions} />
    </div>
  );
}
