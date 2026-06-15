import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Building2, FolderKanban, Scale } from 'lucide-react';
import { StatCard } from '@/shared/components/cards/StatCard';
import { Card } from '@/shared/components/ui/Card';
import { Button } from '@/shared/components/ui/Button';
import { EmptyState } from '@/shared/components/feedback/EmptyState';
import { routes } from '@/shared/config/routes';
import { useAuthStore } from '@/features/auth/authStore';
import { usePortfolioSummary } from '@/features/dashboard/hooks/usePortfolioSummary';
import { AuditLogStatCard } from '@/features/dashboard/components/AuditLogStatCard';

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
