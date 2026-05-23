import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Card } from '@/shared/components/ui/Card';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { EmptyState } from '@/shared/components/feedback/EmptyState';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { WorkflowStepper } from '@/shared/components/workflow/WorkflowStepper';
import { StageStatusCard } from '@/shared/components/workflow/StageStatusCard';
import type { WorkflowStageKey } from '@/shared/components/workflow/workflowTypes';
import { useProject, useWorkflowProgress } from '../hooks/useProject';
import { ProjectStatusBadge } from '../components/ProjectStatusBadge';
import { pickLocalized } from '@/shared/lib/localized';
import { routes } from '@/shared/config/routes';
import { useAuthStore } from '@/features/auth/authStore';

export function ProjectWorkspacePage() {
  const { t, i18n } = useTranslation();
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();
  const setActiveProject = useAuthStore((s) => s.setActiveProject);
  const project = useProject(projectId);
  const workflow = useWorkflowProgress(projectId);
  const [activeStage, setActiveStage] = useState<WorkflowStageKey>('SETUP');

  useEffect(() => {
    if (project.data) {
      setActiveProject({
        id: project.data.id,
        slug: project.data.code,
        name: pickLocalized(project.data.name, i18n.language),
        tenant_id: project.data.tenant_id,
        status: project.data.status,
        archived: project.data.status === 'ARCHIVED',
      });
    }
  }, [project.data, i18n.language, setActiveProject]);

  if (project.isLoading || workflow.isLoading) return <LoadingState />;
  if (project.error || workflow.error)
    return <ErrorState onRetry={() => { project.refetch(); workflow.refetch(); }} />;
  if (!project.data || !workflow.data) return <EmptyState />;

  const stages = workflow.data.stages;
  const stage = stages.find((s) => s.key === activeStage) ?? stages[0];

  const onSelectStage = (key: WorkflowStageKey) => {
    setActiveStage(key);
    // Stages with dedicated routes: jump to them.
    if (!projectId) return;
    if (key === 'ORGANIZATION') navigate(routes.projectOrganization(projectId));
    if (key === 'POSITIONS') navigate(routes.projectPositions(projectId));
  };

  return (
    <div className="space-y-6">
      <Breadcrumbs />
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl text-text-primary">{pickLocalized(project.data.name, i18n.language)}</h1>
            <ProjectStatusBadge status={project.data.status} />
          </div>
          <p className="text-sm text-text-secondary mt-1">
            {project.data.code}
            {project.data.start_date ? ` · ${project.data.start_date}` : ''}
            {project.data.end_date ? ` → ${project.data.end_date}` : ''}
          </p>
        </div>
      </header>

      <WorkflowStepper stages={stages} activeKey={activeStage} onSelect={onSelectStage} />

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <div className="lg:col-span-2">
          <StageStatusCard stage={stage} />
        </div>
        <div className="space-y-4">
          <Card title={t('workflow.recent_activity')} compact>
            <p className="text-sm text-text-secondary">{t('workflow.no_activity')}</p>
          </Card>
          <Card title={t('workflow.next_action')} compact>
            <p className="text-sm text-text-secondary">—</p>
          </Card>
        </div>
      </div>
    </div>
  );
}
