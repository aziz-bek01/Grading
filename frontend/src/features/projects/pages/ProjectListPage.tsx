import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Plus } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { useAuthStore } from '@/features/auth/authStore';
import { ProjectTable } from '../components/ProjectTable';
import { ProjectFormDrawer } from '../components/ProjectFormDrawer';
import { useProjects } from '../hooks/useProjects';
import { useCreateProject } from '../hooks/useCreateProject';

export function ProjectListPage() {
  const { t } = useTranslation();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const { data, isLoading, error, refetch } = useProjects();
  const createMutation = useCreateProject();
  // FE-TI-004: make the active client-company explicit in the page header so
  // the user can immediately see which tenant scope the list reflects.
  const activeTenant = useAuthStore((s) => s.activeTenant);

  const subtitle = activeTenant
    ? t('projects.list_subtitle_for_tenant', { tenant: activeTenant.brand_name })
    : t('projects.list_subtitle');

  return (
    <div className="space-y-6">
      <Breadcrumbs />
      <header className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-2xl text-text-primary">{t('projects.list_title')}</h1>
          <p className="text-sm text-text-secondary mt-1" data-testid="projects-list-subtitle">
            {subtitle}
          </p>
        </div>
      </header>

      {error ? (
        <ErrorState onRetry={() => refetch()} />
      ) : (
        <ProjectTable
          rows={data?.items ?? []}
          loading={isLoading}
          toolbarRight={
            <PermissionGate permission={PERMISSIONS.PROJECT_CREATE}>
              <Button
                variant="primary"
                size="sm"
                leadingIcon={<Plus size={14} />}
                onClick={() => setDrawerOpen(true)}
                data-testid="new-project-button"
              >
                {t('projects.new_project')}
              </Button>
            </PermissionGate>
          }
        />
      )}

      <ProjectFormDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        onSubmit={async (input) => {
          await createMutation.mutateAsync({
            code: input.code,
            name_i18n: input.name_i18n,
            description: input.description || undefined,
            start_date: input.start_date || undefined,
            end_date: input.end_date || undefined,
          });
        }}
      />
    </div>
  );
}
