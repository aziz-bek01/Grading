import { useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Card } from '@/shared/components/ui/Card';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { EmptyState } from '@/shared/components/feedback/EmptyState';
import { AIRecommendationPanel } from '@/features/job-profiles/components/AIRecommendationPanel';
import { useMethodologyVersion } from '@/features/methodology/hooks/useMethodology';
import { cn } from '@/shared/lib/cn';
import {
  useApproveEvaluation,
  useArchiveEvaluation,
  useCalibrateScore,
  useCalibrationHistory,
  useEvaluation,
  useEvaluationScores,
  useLockEvaluation,
  useRequestChanges,
  useSubmitEvaluation,
  useUpsertScore,
} from '../hooks/useEvaluation';
import { EvaluationStatusBadge } from '../components/EvaluationStatusBadge';
import { EvaluationMatrix } from '../components/EvaluationMatrix';
import { EvaluationActionsBar } from '../components/EvaluationActionsBar';
import { EvaluationScoreBreakdown } from '../components/EvaluationScoreBreakdown';
import { CalibrationTable } from '../components/CalibrationTable';
import { CalibrationDialog } from '../components/CalibrationDialog';
import { AssignedGradeBadge } from '@/features/grade-structure/components/AssignedGradeBadge';
import { CommentThread } from '@/features/comment/components/CommentThread';
import { ApprovalStatusBadge } from '@/features/approval/components/ApprovalStatusBadge';
import { useApprovalRequests } from '@/features/approval/hooks/useApprovals';
import { usePositions } from '@/features/positions/hooks/usePositions';
import { pickLocalized } from '@/shared/lib/localized';

type Tab = 'matrix' | 'breakdown' | 'calibration' | 'comments' | 'audit';

/**
 * Evaluation details page — Matrix / Breakdown / Calibration history /
 * Audit tabs + status-aware action bar.
 *
 * Status-aware rendering:
 *   - DRAFT/INCOMPLETE/COMPLETE -> editable matrix
 *   - SUBMITTED/APPROVED/LOCKED/ARCHIVED -> read-only matrix (divs, not
 *     disabled inputs). Calibration available only for APPROVED/LOCKED.
 */
export function EvaluationDetailsPage() {
  const { t, i18n } = useTranslation();
  // Param names MUST match router.tsx exactly: the route declares
  // `evaluation/:evaluationId` (and `:projectId`). A mismatch here yields an
  // undefined id -> '' -> disabled queries -> the page falls through to the
  // EmptyState for everyone (regression guarded by EvaluationDetailsPage.test).
  const { projectId = '', evaluationId = '' } = useParams<{
    projectId: string;
    evaluationId: string;
  }>();
  const [tab, setTab] = useState<Tab>('matrix');
  const [calibrateOpen, setCalibrateOpen] = useState(false);

  const evaluation = useEvaluation(evaluationId);
  const scores = useEvaluationScores(evaluationId);
  const calibration = useCalibrationHistory(evaluationId);
  const version = useMethodologyVersion(
    evaluation.data?.methodology_version_id,
  );
  // T3: resolve position_id -> localized name + human code, mirroring the map
  // EvaluationListPage builds from the same positions query. A raw UUID must
  // never surface as the page heading; it only ever appears in a title=
  // tooltip when the position is genuinely unresolved.
  const positions = usePositions(projectId ? { projectId, size: 200 } : null);
  const positionInfo = useMemo(() => {
    const positionId = evaluation.data?.position_id;
    if (!positionId) return null;
    const match = (positions.data?.items ?? []).find((p) => p.id === positionId);
    if (!match) return null;
    return {
      name: pickLocalized(match.title_i18n, i18n.language),
      code: match.code,
    };
  }, [evaluation.data?.position_id, positions.data, i18n.language]);

  const upsertMutation = useUpsertScore(evaluationId);
  const submitMutation = useSubmitEvaluation(evaluationId);
  const approveMutation = useApproveEvaluation(evaluationId);
  const requestChangesMutation = useRequestChanges(evaluationId);
  const lockMutation = useLockEvaluation(evaluationId);
  const archiveMutation = useArchiveEvaluation(evaluationId);
  const calibrateMutation = useCalibrateScore(evaluationId);

  const canSubmit = useMemo(() => {
    if (!evaluation.data || !version.data) return false;
    const requiredFactors = version.data.factors.filter((f) => f.required);
    const scoredFactorIds = new Set(
      (scores.data ?? []).map((s) => s.factor_id),
    );
    return requiredFactors.every((f) => scoredFactorIds.has(f.id));
  }, [evaluation.data, version.data, scores.data]);

  if (evaluation.isLoading || version.isLoading) return <LoadingState />;
  if (evaluation.error)
    return <ErrorState onRetry={() => evaluation.refetch()} />;
  if (!evaluation.data || !version.data) return <EmptyState />;

  const tabs: { key: Tab; label: string }[] = [
    { key: 'matrix', label: t('evaluation.tab.matrix') },
    { key: 'breakdown', label: t('evaluation.tab.breakdown') },
    { key: 'calibration', label: t('evaluation.tab.calibration') },
    { key: 'comments', label: t('evaluation.tab.comments') },
    { key: 'audit', label: t('evaluation.tab.audit') },
  ];

  return (
    <div className="space-y-6">
      <Breadcrumbs extra={[{ label: t('nav.evaluation') }]} />
      <header className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <div className="flex items-center gap-3 flex-wrap">
            <h1
              className="text-2xl text-text-primary"
              title={
                positionInfo ? undefined : evaluation.data.position_id ?? undefined
              }
            >
              {positionInfo?.name ||
                positionInfo?.code ||
                t('evaluation.details_title')}
            </h1>
            <EvaluationStatusBadge status={evaluation.data.status} />
            <EvaluationApprovalInline
              evaluation_id={evaluation.data.id}
              status={evaluation.data.status}
            />
            {(evaluation.data.status === 'APPROVED' ||
              evaluation.data.status === 'LOCKED') ? (
              <AssignedGradeBadge
                gradeNumber={evaluation.data.assigned_grade_number ?? null}
                outOfRange={
                  evaluation.data.assigned_grade_number == null &&
                  evaluation.data.grade_band_id == null
                }
              />
            ) : null}
          </div>
          <p className="text-sm text-text-secondary mt-1">
            {/* T3: show the human-readable position code as secondary text,
                never the raw UUID. Fall back to a localized placeholder; the
                UUID lives only in the title= tooltip for support. */}
            {t('evaluation.position_id')}:{' '}
            <span
              title={positionInfo ? undefined : evaluation.data.position_id ?? undefined}
            >
              {positionInfo?.code || t('common.untitled')}
            </span>{' '}
            · {t('evaluation.displayed_total')}:{' '}
            <span data-testid="header-total" className="tabular-nums">
              {evaluation.data.displayed_total_score != null
                ? Number(evaluation.data.displayed_total_score).toFixed(2)
                : '—'}
            </span>
          </p>
        </div>
        <EvaluationActionsBar
          status={evaluation.data.status}
          canSubmit={canSubmit}
          onSubmit={() => submitMutation.mutate()}
          onApprove={() => approveMutation.mutate()}
          onRequestChanges={(reason) =>
            requestChangesMutation.mutate({ reason })
          }
          onLock={() => lockMutation.mutate()}
          onArchive={(reason) => archiveMutation.mutate({ reason })}
          onCalibrate={() => setCalibrateOpen(true)}
        />
      </header>

      <div role="tablist" className="border-b border-border flex gap-2 flex-wrap">
        {tabs.map((tInfo) => (
          <button
            key={tInfo.key}
            role="tab"
            aria-selected={tab === tInfo.key}
            onClick={() => setTab(tInfo.key)}
            data-testid={`tab-${tInfo.key}`}
            className={cn(
              'px-3 py-2 text-sm border-b-2 -mb-px',
              tab === tInfo.key
                ? 'border-primary-500 text-primary-700 font-medium'
                : 'border-transparent text-text-secondary hover:text-text-primary',
            )}
          >
            {tInfo.label}
          </button>
        ))}
      </div>

      {tab === 'matrix' ? (
        <div className="grid grid-cols-1 lg:grid-cols-[1fr_18rem] gap-4">
          <EvaluationMatrix
            version={version.data}
            scores={scores.data ?? []}
            status={evaluation.data.status}
            initialDisplayedTotal={evaluation.data.displayed_total_score}
            initialRawTotal={evaluation.data.raw_total_score}
            onSelectLevel={(factor_id, factor_level_id) =>
              upsertMutation.mutate({ factor_id, factor_level_id })
            }
            onCommentChange={(factor_id, factor_level_id, comment) =>
              upsertMutation.mutate({ factor_id, factor_level_id, comment })
            }
          />
          <aside>
            <AIRecommendationPanel
              placeholderBody={t('evaluation.ai.placeholder')}
            />
          </aside>
        </div>
      ) : null}

      {tab === 'breakdown' ? (
        <EvaluationScoreBreakdown
          version={version.data}
          scores={scores.data ?? []}
        />
      ) : null}

      {tab === 'calibration' ? (
        <Card title={t('evaluation.calibration.history_title')}>
          <CalibrationTable
            events={calibration.data ?? []}
            factors={version.data.factors}
          />
        </Card>
      ) : null}

      {tab === 'comments' ? (
        <Card title={t('comment.thread_title')}>
          <CommentThread entityType="EVALUATION" entityId={evaluation.data.id} />
        </Card>
      ) : null}

      {tab === 'audit' ? (
        <Card title={t('audit.log')}>
          <EmptyState body={t('evaluation.audit_placeholder')} />
        </Card>
      ) : null}

      <CalibrationDialog
        open={calibrateOpen}
        factors={version.data.factors}
        onConfirm={(input) => {
          setCalibrateOpen(false);
          calibrateMutation.mutate(input);
        }}
        onCancel={() => setCalibrateOpen(false)}
      />
    </div>
  );
}

/** When evaluation is SUBMITTED, backend has spawned an approval request — surface its status. */
function EvaluationApprovalInline({
  evaluation_id,
  status,
}: {
  evaluation_id: string;
  status: string;
}) {
  const requests = useApprovalRequests({
    entityType: 'EVALUATION',
    entityId: evaluation_id,
  });
  if (status !== 'SUBMITTED') return null;
  const latest = requests.data?.items[0];
  if (!latest) return null;
  return <ApprovalStatusBadge status={latest.status} />;
}
