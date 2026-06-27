import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, Link } from 'react-router-dom';
import { Building2, FolderKanban, Scale, ClipboardCheck } from 'lucide-react';
import { StatCard } from '@/shared/components/cards/StatCard';
import { Card } from '@/shared/components/ui/Card';
import { Button } from '@/shared/components/ui/Button';
import { EmptyState } from '@/shared/components/feedback/EmptyState';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import { routes } from '@/shared/config/routes';
import { useAuthStore } from '@/features/auth/authStore';
import { usePortfolioSummary } from '@/features/dashboard/hooks/usePortfolioSummary';
import { AuditLogStatCard } from '@/features/dashboard/components/AuditLogStatCard';
import { useMyApprovalInbox } from '@/features/approval/hooks/useApprovals';

/** Inline number/skeleton for a StatCard value while the summary loads. */
function StatValue({
  isLoading,
  value,
}: {
  isLoading: boolean;
  value: number | string | undefined;
}) {
  if (isLoading) {
    return (
      <span
        className="inline-block h-7 w-10 rounded bg-divider animate-pulse align-middle"
        aria-hidden
        data-testid="stat-skeleton"
      />
    );
  }
  // Defensive: a partial/failed payload renders the neutral dash, never NaN.
  return <>{value ?? '—'}</>;
}

/**
 * CEO sign-off summary card — permission-gated EVALUATION_PANEL_APPROVE.
 *
 * REUSE: useMyApprovalInbox (same hook the sidebar badge uses) — counts pending
 * EVALUATION_PANEL approval requests without a new endpoint. Links to the existing
 * ApprovalsInboxPage and CeoPanelsPage.
 */
function CeoDashboardCard() {
  const { t } = useTranslation();
  const inbox = useMyApprovalInbox();
  const pendingCount = useMemo(
    () =>
      (inbox.data ?? []).filter((r) => r.entityType === 'EVALUATION_PANEL').length,
    [inbox.data],
  );

  return (
    <Card
      title={t('ceo.dashboard_card.title')}
      action={
        <Link
          to={routes.ceoPanels}
          className="text-sm text-primary-600 hover:underline"
          data-testid="ceo-dashboard-overview-link"
        >
          {t('ceo.dashboard_card.view_all')}
        </Link>
      }
      data-testid="ceo-dashboard-card"
    >
      <div className="flex items-center gap-4">
        <ClipboardCheck size={32} className="text-primary-500 shrink-0" aria-hidden />
        <div>
          {inbox.isLoading ? (
            <span
              className="inline-block h-7 w-10 rounded bg-divider animate-pulse align-middle"
              aria-hidden
              data-testid="ceo-card-skeleton"
            />
          ) : (
            <p className="text-3xl tabular-nums text-text-primary" data-testid="ceo-pending-count">
              {pendingCount}
            </p>
          )}
          <p className="text-sm text-text-secondary mt-0.5">
            {t('ceo.dashboard_card.pending_label')}
          </p>
        </div>
      </div>
      <div className="mt-4 flex gap-3">
        <Link
          to={routes.approvalsInbox}
          className="inline-flex items-center gap-1.5 text-sm text-primary-600 hover:underline"
          data-testid="ceo-dashboard-inbox-link"
        >
          {t('ceo.dashboard_card.go_inbox')}
        </Link>
      </div>
    </Card>
  );
}

export function DashboardPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const activeTenantId = useAuthStore((s) => s.activeTenant?.id);
  const summary = usePortfolioSummary(activeTenantId);
  const isLoading = summary.isLoading;
  const data = summary.data;
  const hasProjects = (data?.projectCount ?? 0) > 0;

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl text-text-primary">{t('pages.dashboard_title')}</h1>
        <p className="text-sm text-text-secondary mt-1">{t('pages.dashboard_subtitle')}</p>
      </header>

      {summary.isError ? (
        <p className="text-sm text-danger-700" role="alert">
          {t('pages.dashboard_load_error')}
        </p>
      ) : null}

      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        <StatCard
          label={t('nav.clients')}
          value={<StatValue isLoading={isLoading} value={data?.clientCompanyCount} />}
          icon={<Building2 size={18} aria-hidden />}
          data-testid="stat-clients"
        />
        <StatCard
          label={t('nav.projects')}
          value={<StatValue isLoading={isLoading} value={data?.projectCount} />}
          icon={<FolderKanban size={18} aria-hidden />}
          data-testid="stat-projects"
        />
        <StatCard
          label={t('nav.methodology')}
          value={<StatValue isLoading={isLoading} value={data?.methodologyCount} />}
          icon={<Scale size={18} aria-hidden />}
          data-testid="stat-methodology"
        />
        <AuditLogStatCard />
      </div>

      {/* CEO sign-off card — only when the user holds EVALUATION_PANEL_APPROVE. */}
      <PermissionGate permission={PERMISSIONS.EVALUATION_PANEL_APPROVE}>
        <CeoDashboardCard />
      </PermissionGate>

      <Card title={t('nav.projects')}>
        {isLoading ? (
          <div
            className="h-20 rounded bg-divider animate-pulse"
            aria-hidden
            data-testid="projects-card-skeleton"
          />
        ) : hasProjects ? (
          <EmptyState
            className="py-10"
            icon={<FolderKanban size={32} aria-hidden />}
            title={t('projects.list_title')}
            body={t('pages.dashboard_subtitle')}
            action={
              <Button
                size="sm"
                onClick={() => navigate(routes.projects)}
                data-testid="dashboard-projects-cta"
              >
                {t('pages.dashboard_projects_empty_cta')}
              </Button>
            }
          />
        ) : (
          <EmptyState
            className="py-10"
            icon={<FolderKanban size={32} aria-hidden />}
            title={t('pages.dashboard_projects_empty_title')}
            body={t('pages.dashboard_projects_empty_body')}
            action={
              <Button
                size="sm"
                onClick={() => navigate(routes.projects)}
                data-testid="dashboard-projects-cta"
              >
                {t('pages.dashboard_projects_empty_cta')}
              </Button>
            }
          />
        )}
      </Card>
    </div>
  );
}
