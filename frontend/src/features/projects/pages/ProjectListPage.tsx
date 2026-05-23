import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Plus } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { ProjectTable } from '../components/ProjectTable';
import { ProjectFormDrawer } from '../components/ProjectFormDrawer';
import { useProjects } from '../hooks/useProjects';
import { useCreateProject } from '../hooks/useCreateProject';

export function ProjectListPage() {
  const { t } = useTranslation();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const { data, isLoading, error, refetch } = useProjects();
  const createMutation = useCreateProject();

  return (
    <div className="space-y-6">
      <Breadcrumbs />
      <header className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-2xl text-text-primary">{t('projects.list_title')}</h1>
          <p className="text-sm text-text-secondary mt-1">{t('projects.list_subtitle')}</p>
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
            name: input.name,
            description: input.description || undefined,
            start_date: input.start_date || undefined,
            end_date: input.end_date || undefined,
          });
        }}
      />
    </div>
  );
}
