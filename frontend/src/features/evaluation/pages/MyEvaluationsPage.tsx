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
import { useAuthStore } from '@/features/auth/authStore';
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
 * filled / total factor progress chip, and deep-links to the SAME scoring sheet
 * the evaluation list opens ({@link EvaluationDetailsPage} → EvaluationMatrix).
 *
 * The inbox is project-agnostic on the wire, but the sheet route is
 * project-scoped — we resolve the deep-link via the ACTIVE project id. When no
 * project is active the row is shown but not yet linkable (a hint asks the
 * evaluator to pick a project first), so the page never produces a broken URL.
 */
export function MyEvaluationsPage() {
  const { t } = useTranslation();
  const { can } = usePermission();
  const canRead = can(PERMISSIONS.EVALUATION_READ);
  const activeProjectId = useAuthStore((s) => s.activeProject?.id ?? null);

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

      {activeProjectId === null && !query.isLoading && rows.length > 0 ? (
        <div
          role="note"
          data-testid="my-evaluations-no-project-hint"
          className="rounded-md border border-warning-500/30 bg-warning-50 text-warning-700 text-sm p-3"
        >
          {t('my_evaluations.select_project_hint')}
        </div>
      ) : null}

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

              return (
                <li key={row.evaluationId}>
                  {activeProjectId ? (
                    <Link
                      to={routes.projectEvaluationDetail(
                        activeProjectId,
                        row.evaluationId,
                      )}
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
                      title={t('my_evaluations.select_project_hint')}
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
