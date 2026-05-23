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
import { PlaceholderPage } from '@/pages/PlaceholderPage';

import { ProjectListPage } from '@/features/projects/pages/ProjectListPage';
import { ProjectWorkspacePage } from '@/features/projects/pages/ProjectWorkspacePage';
import { OrganizationPage } from '@/features/organization/pages/OrganizationPage';
import { PositionListPage } from '@/features/positions/pages/PositionListPage';
import { PositionDetailsPage } from '@/features/positions/pages/PositionDetailsPage';
import { JobProfileEditorPage } from '@/features/job-profiles/pages/JobProfileEditorPage';
import { QuestionnairePage } from '@/features/job-analysis/pages/QuestionnairePage';

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
            <Route path="clients" element={<PlaceholderPage titleKey="pages.clients_title" />} />
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
              <Route path="methodology" element={<PlaceholderPage titleKey="pages.methodology_title" />} />
            </Route>
            <Route element={<RequirePermission permissions={PERMISSIONS.EVALUATION_READ} />}>
              <Route path="evaluation" element={<PlaceholderPage titleKey="pages.evaluation_title" />} />
            </Route>
            <Route element={<RequirePermission permissions={PERMISSIONS.GRADE_READ} />}>
              <Route path="grades" element={<PlaceholderPage titleKey="pages.grades_title" />} />
            </Route>
            <Route element={<RequireSalaryPermission />}>
              <Route
                path="compensation"
                element={
                  <PlaceholderPage
                    titleKey="pages.compensation_title"
                    subtitleKey="pages.compensation_subtitle"
                  />
                }
              />
            </Route>
            <Route element={<RequirePermission permissions={PERMISSIONS.REPORT_READ} />}>
              <Route path="reports" element={<PlaceholderPage titleKey="pages.reports_title" />} />
            </Route>
          </Route>

          <Route element={<RequireAuditPermission />}>
            <Route path="audit" element={<PlaceholderPage titleKey="pages.audit_title" />} />
          </Route>

          <Route
            element={
              <RequirePermission permissions={[PERMISSIONS.USER_READ, PERMISSIONS.USER_ACCESS_MANAGE]} mode="any" />
            }
          >
            <Route path="users-access" element={<PlaceholderPage titleKey="pages.users_access_title" />} />
          </Route>
        </Route>
      </Route>

      <Route path="*" element={<Navigate to={routes.dashboard} replace />} />
    </Routes>
  );
}
