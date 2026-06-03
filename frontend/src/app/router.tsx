import { Navigate, Route, Routes } from 'react-router-dom';
import { AppShell } from '@/shared/components/layout/AppShell';
import { RequireAuth } from './routing/RequireAuth';
import { RequirePermission } from './routing/RequirePermission';
import { RequireSalaryPermission } from './routing/RequireSalaryPermission';
import { RequireAuditPermission } from './routing/RequireAuditPermission';
import { PERMISSIONS } from '@/shared/types/permissions';
import { routes } from '@/shared/config/routes';

import { LoginPage } from '@/pages/LoginPage';
import { AccessDeniedPage } from '@/pages/AccessDeniedPage';
import { DashboardPage } from '@/pages/DashboardPage';
import { UpcomingFeaturePage } from '@/shared/components/UpcomingFeaturePage';

import { ClientsListPage } from '@/features/clients/pages/ClientsListPage';
import { ClientDetailsPage } from '@/features/clients/pages/ClientDetailsPage';

import { ProjectListPage } from '@/features/projects/pages/ProjectListPage';
import { ProjectWorkspacePage } from '@/features/projects/pages/ProjectWorkspacePage';
import { OrganizationPage } from '@/features/organization/pages/OrganizationPage';
import { PositionListPage } from '@/features/positions/pages/PositionListPage';
import { PositionDetailsPage } from '@/features/positions/pages/PositionDetailsPage';
import { JobProfileEditorPage } from '@/features/job-profiles/pages/JobProfileEditorPage';
import { QuestionnairePage } from '@/features/job-analysis/pages/QuestionnairePage';
import { MethodologyListPage } from '@/features/methodology/pages/MethodologyListPage';
import { MethodologyBuilderPage } from '@/features/methodology/pages/MethodologyBuilderPage';
import { MethodologyTranslationsPage } from '@/features/methodology/pages/MethodologyTranslationsPage';
import { EvaluationListPage } from '@/features/evaluation/pages/EvaluationListPage';
import { EvaluationDetailsPage } from '@/features/evaluation/pages/EvaluationDetailsPage';
import { GradeStructureListPage } from '@/features/grade-structure/pages/GradeStructureListPage';
import { GradeStructureBuilderPage } from '@/features/grade-structure/pages/GradeStructureBuilderPage';
import { GradePyramidPage } from '@/features/grade-structure/pages/GradePyramidPage';
import { ApprovalsInboxPage } from '@/features/approval/pages/ApprovalsInboxPage';
import { ApprovalDetailsPage } from '@/features/approval/pages/ApprovalDetailsPage';
import { ApprovalsListPage } from '@/features/approval/pages/ApprovalsListPage';
import { ImportListPage } from '@/features/import/pages/ImportListPage';
import { ImportWizardPage } from '@/features/import/pages/ImportWizardPage';
import { ImportDetailsPage } from '@/features/import/pages/ImportDetailsPage';
import { ExportCenterPage } from '@/features/export/pages/ExportCenterPage';
import { ExportDetailsPage } from '@/features/export/pages/ExportDetailsPage';
import { ReportsCenterPage } from '@/features/report/pages/ReportsCenterPage';
import { UsersListPage } from '@/features/users-access/pages/UsersListPage';
import { UserDetailsPage } from '@/features/users-access/pages/UserDetailsPage';
import { AuditListPage } from '@/features/audit/pages/AuditListPage';

export function AppRouter() {
  return (
    <Routes>
      <Route path={routes.login} element={<LoginPage />} />
      <Route path={routes.accessDenied} element={<AccessDeniedPage />} />
      <Route path={routes.noAccess} element={<AccessDeniedPage />} />

      <Route element={<RequireAuth />}>
        <Route path="/" element={<Navigate to={routes.dashboard} replace />} />
        <Route path={routes.app} element={<AppShell />}>
          <Route index element={<Navigate to={routes.dashboard} replace />} />
          <Route path="dashboard" element={<DashboardPage />} />

          <Route element={<RequirePermission permissions={PERMISSIONS.TENANT_READ} />}>
            <Route path="clients" element={<ClientsListPage />} />
            <Route path="clients/:tenantId" element={<ClientDetailsPage />} />
          </Route>

          <Route element={<RequirePermission permissions={PERMISSIONS.PROJECT_READ} />}>
            <Route path="projects" element={<ProjectListPage />} />
          </Route>

          <Route path="projects/:projectId">
            <Route element={<RequirePermission permissions={PERMISSIONS.PROJECT_READ} />}>
              <Route path="workspace" element={<ProjectWorkspacePage />} />
            </Route>
            <Route element={<RequirePermission permissions={PERMISSIONS.ORG_READ} />}>
              <Route path="organization" element={<OrganizationPage />} />
            </Route>
            <Route element={<RequirePermission permissions={PERMISSIONS.POSITION_READ} />}>
              <Route path="positions" element={<PositionListPage />} />
              <Route path="positions/:positionId" element={<PositionDetailsPage />} />
            </Route>
            <Route element={<RequirePermission permissions={PERMISSIONS.JOB_PROFILE_READ} />}>
              <Route
                path="positions/:positionId/job-profile"
                element={<JobProfileEditorPage />}
              />
            </Route>
            <Route element={<RequirePermission permissions={PERMISSIONS.JOB_ANALYSIS_READ} />}>
              <Route
                path="positions/:positionId/questionnaire/:questionnaireId"
                element={<QuestionnairePage />}
              />
            </Route>
            <Route element={<RequirePermission permissions={PERMISSIONS.METHODOLOGY_READ} />}>
              <Route path="methodology" element={<MethodologyListPage />} />
              <Route
                path="methodology/:methodologyId/versions/:versionId/edit"
                element={<MethodologyBuilderPage />}
              />
              <Route
                path="methodology/:methodologyId/versions/:versionId/translations"
                element={<MethodologyTranslationsPage />}
              />
            </Route>
            <Route element={<RequirePermission permissions={PERMISSIONS.EVALUATION_READ} />}>
              <Route path="evaluation" element={<EvaluationListPage />} />
              <Route
                path="evaluation/:evaluationId"
                element={<EvaluationDetailsPage />}
              />
            </Route>
            <Route element={<RequirePermission permissions={PERMISSIONS.GRADE_READ} />}>
              <Route path="grades" element={<GradeStructureListPage />} />
              <Route
                path="grades/:gradeStructureId/edit"
                element={<GradeStructureBuilderPage />}
              />
              <Route
                path="grades/:gradeStructureId/versions/:versionId/edit"
                element={<GradeStructureBuilderPage />}
              />
              <Route
                path="grades/:gradeStructureId/pyramid"
                element={<GradePyramidPage />}
              />
            </Route>
            <Route element={<RequireSalaryPermission />}>
              <Route
                path="compensation"
                element={
                  <UpcomingFeaturePage
                    titleKey="upcoming.compensation.title"
                    subtitleKey="upcoming.compensation.subtitle"
                    featuresKeys={[
                      'upcoming.compensation.features.1',
                      'upcoming.compensation.features.2',
                      'upcoming.compensation.features.3',
                      'upcoming.compensation.features.4',
                      'upcoming.compensation.features.5',
                    ]}
                    targetSprintLabel="Sprint 14 · 06.07-20.07.2026"
                    targetDate="2026-07-06"
                    mvpPhase="MVP 3 Phase 1"
                  />
                }
              />
            </Route>
            <Route element={<RequirePermission permissions={PERMISSIONS.REPORT_READ} />}>
              <Route path="reports" element={<ReportsCenterPage />} />
            </Route>
            <Route
              element={
                <RequirePermission
                  permissions={[
                    PERMISSIONS.APPROVAL_REQUEST_CREATE,
                    PERMISSIONS.APPROVAL_STEP_APPROVE,
                  ]}
                  mode="any"
                />
              }
            >
              <Route path="approvals" element={<ApprovalsListPage />} />
            </Route>
            <Route element={<RequirePermission permissions={PERMISSIONS.IMPORT_READ} />}>
              <Route path="imports" element={<ImportListPage />} />
              <Route path="imports/new" element={<ImportWizardPage />} />
              <Route path="imports/:importId" element={<ImportDetailsPage />} />
            </Route>
            <Route element={<RequirePermission permissions={PERMISSIONS.EXPORT_READ} />}>
              <Route path="exports" element={<ExportCenterPage />} />
              <Route path="exports/:exportId" element={<ExportDetailsPage />} />
            </Route>
          </Route>

          <Route path="approvals" element={<ApprovalsInboxPage />} />
          <Route path="approvals/:approvalId" element={<ApprovalDetailsPage />} />

          <Route element={<RequireAuditPermission />}>
            <Route path="audit" element={<AuditListPage />} />
          </Route>

          <Route
            element={
              <RequirePermission permissions={[PERMISSIONS.USER_READ, PERMISSIONS.USER_ACCESS_MANAGE]} mode="any" />
            }
          >
            <Route path="users" element={<UsersListPage />} />
            <Route path="users/:userId" element={<UserDetailsPage />} />
            {/* Legacy URL — redirect to the canonical /app/users. */}
            <Route path="users-access" element={<Navigate to={routes.usersAccess} replace />} />
          </Route>
        </Route>
      </Route>

      <Route path="*" element={<Navigate to={routes.dashboard} replace />} />
    </Routes>
  );
}
