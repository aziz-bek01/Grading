import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Card } from '@/shared/components/ui/Card';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { EmptyState } from '@/shared/components/feedback/EmptyState';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { NoAccessState } from '@/shared/components/feedback/NoAccessState';
import { PERMISSIONS } from '@/shared/types/permissions';
import { usePermission } from '@/features/auth/usePermission';
import { ApiError } from '@/shared/api/apiError';
import { routes } from '@/shared/config/routes';
import { useMyEvaluations } from '../hooks/useMyEvaluations';
import { EvaluationStatusBadge } from '../components/EvaluationStatusBadge';
import { ProgressChip } from '../components/byFactor/ProgressChip';

/**
 * Feature 1 — evaluator self-inbox ("My evaluations").
 *
 * Lists ONLY the caller's own scoring sheets (GET /evaluations/my, gated
 * EVALUATION_READ; the BE scopes by the JWT actor, never a client param). Each
 * row carries a localized position title (+ code), the sheet status, and a
 * filled / total factor progress chip, and deep-links to the project Evaluation
 * page in by-factor mode (the single scoring surface) for that row's project.
 *
 * The inbox is project-agnostic on the wire, but the scoring route is
 * project-scoped — each row carries its OWN project id, so we deep-link via
 * {@link routes.projectEvaluation} (`?mode=by-factor`) using `row.projectId`.
 * EVERY row is therefore linkable regardless of which project is active; a row
 * is only shown non-linkable as a graceful guard if `projectId` is unexpectedly
 * missing, so the page never produces a broken URL.
 */
export function MyEvaluationsPage() {
  const { t } = useTranslation();
  const { can } = usePermission();
  const canRead = can(PERMISSIONS.EVALUATION_READ);

  const query = useMyEvaluations();
  const rows = useMemo(() => query.data ?? [], [query.data]);

  if (!canRead) return <NoAccessState />;

  return (
    <div className="space-y-6" data-testid="my-evaluations-page">
      <Breadcrumbs extra={[{ label: t('nav.my_evaluations') }]} />
      <header>
        <h1 className="text-2xl text-text-primary">{t('my_evaluations.title')}</h1>
        <p className="text-sm text-text-secondary mt-1">
          {t('my_evaluations.subtitle')}
        </p>
      </header>

      <Card>
        {query.isLoading ? (
          <MyEvaluationsSkeleton />
        ) : query.error ? (
          <ErrorState
            correlationId={
              query.error instanceof ApiError ? query.error.correlationId : undefined
            }
            onRetry={() => query.refetch()}
          />
        ) : rows.length === 0 ? (
          <EmptyState
            title={t('my_evaluations.empty_title')}
            body={t('my_evaluations.empty_body')}
          />
        ) : (
          <ul className="divide-y divide-divider" data-testid="my-evaluations-list">
            {rows.map((row) => {
              const inner = (
                <>
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium text-text-primary truncate">
                      {row.title}
                    </p>
                    <p className="text-xs text-text-muted">{row.positionCode}</p>
                  </div>
                  <div className="flex items-center gap-3 shrink-0">
                    <ProgressChip
                      filled={row.filledFactorsCount}
                      total={row.totalFactorsCount}
                    />
                    <EvaluationStatusBadge status={row.status} />
                  </div>
                </>
              );

              // ALL evaluators score in the by-factor K-sheet — the single
              // scoring surface. The per-sheet Matrix detail page is no longer a
              // scoring entry point (it was a duplicate); it stays reachable from
              // the positions list for review (calibration history / comments /
              // audit).
              const target = `${routes.projectEvaluation(row.projectId)}?mode=by-factor`;

              return (
                <li key={row.evaluationId}>
                  {row.projectId ? (
                    <Link
                      to={target}
                      data-testid={`open-my-evaluation-${row.evaluationId}`}
                      className="flex items-center gap-4 px-1 py-3 hover:bg-divider/50 rounded-md"
                    >
                      {inner}
                    </Link>
                  ) : (
                    <div
                      data-testid={`my-evaluation-row-${row.evaluationId}`}
                      className="flex items-center gap-4 px-1 py-3 text-text-muted"
                      aria-disabled
                    >
                      {inner}
                    </div>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </Card>
    </div>
  );
}

/** Loading skeleton — three placeholder rows matching the list layout. */
function MyEvaluationsSkeleton() {
  return (
    <ul className="divide-y divide-divider" data-testid="my-evaluations-skeleton">
      {[0, 1, 2].map((i) => (
        <li key={i} className="flex items-center gap-4 px-1 py-3 animate-pulse">
          <div className="min-w-0 flex-1 space-y-2">
            <div className="h-4 w-48 rounded bg-divider" />
            <div className="h-3 w-24 rounded bg-divider" />
          </div>
          <div className="h-6 w-16 rounded-full bg-divider" />
          <div className="h-6 w-20 rounded-full bg-divider" />
        </li>
      ))}
    </ul>
  );
}
