import { useState, useEffect, useMemo } from 'react';
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
import { useProject } from '../hooks/useProject';
import { useWorkflowProgress } from '@/features/workflow/hooks/useWorkflowProgress';
import { useApprovalRequests } from '@/features/approval/hooks/useApprovals';
import { ApprovalRequestCard } from '@/features/approval/components/ApprovalRequestCard';
import { ProjectStatusBadge } from '../components/ProjectStatusBadge';
import { RecentActivityList } from '../components/RecentActivityList';
import { pickLocalized } from '@/shared/lib/localized';
import { routes } from '@/shared/config/routes';
import { useAuthStore } from '@/features/auth/authStore';

const STAGE_ROUTE: Partial<Record<WorkflowStageKey, (projectId: string) => string>> = {
  ORGANIZATION: routes.projectOrganization,
  POSITIONS: routes.projectPositions,
  JOB_PROFILES: routes.projectPositions,
  METHODOLOGY: routes.projectMethodology,
  EVALUATION: routes.projectEvaluation,
  GRADES: routes.projectGrades,
  COMPENSATION: routes.projectCompensation,
  REPORTS: routes.projectReports,
};

export function ProjectWorkspacePage() {
  const { t, i18n } = useTranslation();
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();
  const setActiveProject = useAuthStore((s) => s.setActiveProject);
  const activeTenant = useAuthStore((s) => s.activeTenant);
  const project = useProject(projectId);
  const workflow = useWorkflowProgress(projectId);
  const approvals = useApprovalRequests({ projectId, status: 'PENDING' });
  // User's explicit stage selection (null until they click a step). The
  // effective active stage is derived below so we never need a setState-in-effect
  // to seed it from the backend `currentStage`.
  const [selectedStage, setSelectedStage] = useState<WorkflowStageKey | null>(null);
  const activeStage: WorkflowStageKey =
    selectedStage ?? workflow.data?.currentStage ?? 'SETUP';

  useEffect(() => {
    if (project.data) {
      // Backend `ProjectResponse` never echoes `tenant_id` on the wire — derive
      // it from the JWT-resolved active tenant (security blueprint API-13).
      // Falls back to the legacy MSW shape during the Contract-A migration
      // window where `tenant_id` may still appear on mock fixtures.
      const tenantId = project.data.tenant_id ?? activeTenant?.id ?? '';
      setActiveProject({
        id: project.data.id,
        slug: project.data.code,
        name: pickLocalized(project.data.name_i18n, i18n.language),
        tenant_id: tenantId,
        status: project.data.status,
        archived: project.data.status === 'ARCHIVED',
      });
    }
  }, [project.data, i18n.language, setActiveProject, activeTenant]);

  const stage = useMemo(() => {
    const stages = workflow.data?.stages ?? [];
    return stages.find((s) => s.stage === activeStage) ?? stages[0];
  }, [workflow.data, activeStage]);

  if (project.isLoading || workflow.isLoading) return <LoadingState />;
  if (project.error || workflow.error)
    return (
      <ErrorState
        onRetry={() => {
          project.refetch();
          workflow.refetch();
        }}
      />
    );
  if (!project.data || !workflow.data || !stage) return <EmptyState />;

  const onSelectStage = (key: WorkflowStageKey) => {
    setSelectedStage(key);
  };
  const onOpenStage = () => {
    if (!projectId) return;
    const builder = STAGE_ROUTE[stage.stage];
    if (builder) navigate(builder(projectId));
  };

  const pendingApprovals = approvals.data?.items ?? [];

  return (
    <div className="space-y-6">
      <Breadcrumbs />
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl text-text-primary">
              {pickLocalized(project.data.name_i18n, i18n.language)}
            </h1>
            <ProjectStatusBadge status={project.data.status} />
          </div>
          <p className="text-sm text-text-secondary mt-1">
            {project.data.code}
            {project.data.start_date ? ` · ${project.data.start_date}` : ''}
            {project.data.end_date ? ` → ${project.data.end_date}` : ''}
          </p>
        </div>
      </header>

      <WorkflowStepper stages={workflow.data.stages} activeKey={activeStage} onSelect={onSelectStage} />

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <div className="lg:col-span-2">
          <StageStatusCard
            stage={stage}
            onOpenStage={STAGE_ROUTE[stage.stage] ? onOpenStage : undefined}
            openLabel={t('workflow.open_stage')}
          />
        </div>
        <div className="space-y-4">
          <Card
            title={t('approval.pending_panel_title', { count: pendingApprovals.length })}
            compact
          >
            {pendingApprovals.length === 0 ? (
              <p className="text-sm text-text-secondary">{t('approval.pending_panel_empty')}</p>
            ) : (
              <ul className="space-y-2">
                {pendingApprovals.slice(0, 5).map((a) => (
                  <li key={a.id}>
                    <ApprovalRequestCard request={a} />
                  </li>
                ))}
              </ul>
            )}
          </Card>
          <Card title={t('workflow.recent_activity')} compact>
            <RecentActivityList projectId={project.data.id} limit={20} />
          </Card>
        </div>
      </div>
    </div>
  );
}
