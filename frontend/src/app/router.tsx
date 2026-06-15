import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { AppShell } from '@/shared/components/layout/AppShell';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { RequireAuth } from './routing/RequireAuth';
import { RequirePermission } from './routing/RequirePermission';
import { RequireSalaryPermission } from './routing/RequireSalaryPermission';
import { RequireAuditPermission } from './routing/RequireAuditPermission';
import { PERMISSIONS } from '@/shared/types/permissions';
import { routes } from '@/shared/config/routes';

// Route-level code-splitting (P1-T1). Every routed page is loaded via
// `React.lazy` so its feature module — plus that module's transitive deps —
// lands in its own async chunk instead of the entry bundle. Vite/Rolldown
// emits one chunk per dynamic import; combined with the vendor groups in
// vite.config.ts this drops the entry chunk well below the former 1.4 MB
// monolith. The shell, guards, routes table and the small shared
// `UpcomingFeaturePage` stay eager because they are needed on first paint.
import { UpcomingFeaturePage } from '@/shared/components/UpcomingFeaturePage';

// `lazy` needs a module whose `default` export is the component. Each feature
// page is a named export, so re-map it to `default` in the import() callback.
const LoginPage = lazy(() =>
  import('@/pages/LoginPage').then((m) => ({ default: m.LoginPage })),
);
const AuthCallbackPage = lazy(() =>
  import('@/pages/AuthCallbackPage').then((m) => ({ default: m.AuthCallbackPage })),
);
const AccessDeniedPage = lazy(() =>
  import('@/pages/AccessDeniedPage').then((m) => ({ default: m.AccessDeniedPage })),
);
const NotFoundPage = lazy(() =>
  import('@/pages/NotFoundPage').then((m) => ({ default: m.NotFoundPage })),
);
const DashboardPage = lazy(() =>
  import('@/pages/DashboardPage').then((m) => ({ default: m.DashboardPage })),
);

const ClientsListPage = lazy(() =>
  import('@/features/clients/pages/ClientsListPage').then((m) => ({ default: m.ClientsListPage })),
);
const ClientDetailsPage = lazy(() =>
  import('@/features/clients/pages/ClientDetailsPage').then((m) => ({ default: m.ClientDetailsPage })),
);

const ProjectListPage = lazy(() =>
  import('@/features/projects/pages/ProjectListPage').then((m) => ({ default: m.ProjectListPage })),
);
const ProjectWorkspacePage = lazy(() =>
  import('@/features/projects/pages/ProjectWorkspacePage').then((m) => ({ default: m.ProjectWorkspacePage })),
);
const OrganizationPage = lazy(() =>
  import('@/features/organization/pages/OrganizationPage').then((m) => ({ default: m.OrganizationPage })),
);
const PositionListPage = lazy(() =>
  import('@/features/positions/pages/PositionListPage').then((m) => ({ default: m.PositionListPage })),
);
const PositionDetailsPage = lazy(() =>
  import('@/features/positions/pages/PositionDetailsPage').then((m) => ({ default: m.PositionDetailsPage })),
);
const JobProfileEditorPage = lazy(() =>
  import('@/features/job-profiles/pages/JobProfileEditorPage').then((m) => ({ default: m.JobProfileEditorPage })),
);
const QuestionnairePage = lazy(() =>
  import('@/features/job-analysis/pages/QuestionnairePage').then((m) => ({ default: m.QuestionnairePage })),
);
const MethodologyListPage = lazy(() =>
  import('@/features/methodology/pages/MethodologyListPage').then((m) => ({ default: m.MethodologyListPage })),
);
const MethodologyBuilderPage = lazy(() =>
  import('@/features/methodology/pages/MethodologyBuilderPage').then((m) => ({ default: m.MethodologyBuilderPage })),
);
const MethodologyTranslationsPage = lazy(() =>
  import('@/features/methodology/pages/MethodologyTranslationsPage').then((m) => ({ default: m.MethodologyTranslationsPage })),
);
const EvaluationListPage = lazy(() =>
  import('@/features/evaluation/pages/EvaluationListPage').then((m) => ({ default: m.EvaluationListPage })),
);
const EvaluationDetailsPage = lazy(() =>
  import('@/features/evaluation/pages/EvaluationDetailsPage').then((m) => ({ default: m.EvaluationDetailsPage })),
);
const PanelDetailPage = lazy(() =>
  import('@/features/evaluation/pages/PanelDetailPage').then((m) => ({ default: m.PanelDetailPage })),
);
const MyEvaluationsPage = lazy(() =>
  import('@/features/evaluation/pages/MyEvaluationsPage').then((m) => ({ default: m.MyEvaluationsPage })),
);
const GradeStructureListPage = lazy(() =>
  import('@/features/grade-structure/pages/GradeStructureListPage').then((m) => ({ default: m.GradeStructureListPage })),
);
const GradeStructureBuilderPage = lazy(() =>
  import('@/features/grade-structure/pages/GradeStructureBuilderPage').then((m) => ({ default: m.GradeStructureBuilderPage })),
);
const GradePyramidPage = lazy(() =>
  import('@/features/grade-structure/pages/GradePyramidPage').then((m) => ({ default: m.GradePyramidPage })),
);
const ApprovalsInboxPage = lazy(() =>
  import('@/features/approval/pages/ApprovalsInboxPage').then((m) => ({ default: m.ApprovalsInboxPage })),
);
const ApprovalDetailsPage = lazy(() =>
  import('@/features/approval/pages/ApprovalDetailsPage').then((m) => ({ default: m.ApprovalDetailsPage })),
);
const ApprovalsListPage = lazy(() =>
  import('@/features/approval/pages/ApprovalsListPage').then((m) => ({ default: m.ApprovalsListPage })),
);
const ImportListPage = lazy(() =>
  import('@/features/import/pages/ImportListPage').then((m) => ({ default: m.ImportListPage })),
);
const ImportWizardPage = lazy(() =>
  import('@/features/import/pages/ImportWizardPage').then((m) => ({ default: m.ImportWizardPage })),
);
const ImportDetailsPage = lazy(() =>
  import('@/features/import/pages/ImportDetailsPage').then((m) => ({ default: m.ImportDetailsPage })),
);
const ExportCenterPage = lazy(() =>
  import('@/features/export/pages/ExportCenterPage').then((m) => ({ default: m.ExportCenterPage })),
);
const ExportDetailsPage = lazy(() =>
  import('@/features/export/pages/ExportDetailsPage').then((m) => ({ default: m.ExportDetailsPage })),
);
const ReportsCenterPage = lazy(() =>
  import('@/features/report/pages/ReportsCenterPage').then((m) => ({ default: m.ReportsCenterPage })),
);
const UsersListPage = lazy(() =>
  import('@/features/users-access/pages/UsersListPage').then((m) => ({ default: m.UsersListPage })),
);
const UserDetailsPage = lazy(() =>
  import('@/features/users-access/pages/UserDetailsPage').then((m) => ({ default: m.UserDetailsPage })),
);
const RolesListPage = lazy(() =>
  import('@/features/roles-access/pages/RolesListPage').then((m) => ({ default: m.RolesListPage })),
);
const RoleDetailPage = lazy(() =>
  import('@/features/roles-access/pages/RoleDetailPage').then((m) => ({ default: m.RoleDetailPage })),
);
const AuditListPage = lazy(() =>
  import('@/features/audit/pages/AuditListPage').then((m) => ({ default: m.AuditListPage })),
);

export function AppRouter() {
  return (
    <Suspense fallback={<LoadingState />}>
    <Routes>
      <Route path={routes.login} element={<LoginPage />} />
      <Route path={routes.authCallback} element={<AuthCallbackPage />} />
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
              {/* T3 — panel detail. Static `panels` segment outranks the
                  dynamic `:evaluationId` so the two never collide. */}
              <Route
                path="evaluation/panels/:panelId"
                element={<PanelDetailPage />}
              />
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
                    // Canonical decide code (BE gates getById/inbox/decide on it).
                    PERMISSIONS.APPROVAL_REQUEST_DECIDE,
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

          {/* Global approvals inbox + detail. BE GET /my-inbox and
              GET /approval-requests/{id} gate on APPROVAL_REQUEST_DECIDE; we
              accept the same "any approver" set as the project-scoped
              variant and the sidebar entry so a non-decider gets the clean
              NoAccessState instead of a raw error card. */}
          <Route
            element={
              <RequirePermission
                permissions={[
                  PERMISSIONS.APPROVAL_REQUEST_CREATE,
                  PERMISSIONS.APPROVAL_REQUEST_DECIDE,
                  PERMISSIONS.APPROVAL_STEP_APPROVE,
                ]}
                mode="any"
              />
            }
          >
            <Route path="approvals" element={<ApprovalsInboxPage />} />
            <Route path="approvals/:approvalId" element={<ApprovalDetailsPage />} />
          </Route>

          {/* Feature 1 — evaluator self-inbox. Global (not project-scoped)
              because GET /evaluations/my spans the caller's projects. Gated
              EVALUATION_READ so an assigned evaluator can reach their sheets. */}
          <Route element={<RequirePermission permissions={PERMISSIONS.EVALUATION_READ} />}>
            <Route path="my-evaluations" element={<MyEvaluationsPage />} />
          </Route>

          <Route element={<RequireAuditPermission />}>
            <Route path="audit" element={<AuditListPage />} />
          </Route>

          <Route
            element={
              <RequirePermission permissions={[PERMISSIONS.USER_LIST, PERMISSIONS.USER_ACCESS_MANAGE]} mode="any" />
            }
          >
            <Route path="users" element={<UsersListPage />} />
            <Route path="users/:userId" element={<UserDetailsPage />} />
            {/* Legacy URL — redirect to the canonical /app/users. */}
            <Route path="users-access" element={<Navigate to={routes.usersAccess} replace />} />
          </Route>

          {/* Roles admin (slice E2) — gated by USER_ACCESS_MANAGE only. */}
          <Route element={<RequirePermission permissions={PERMISSIONS.USER_ACCESS_MANAGE} />}>
            <Route path="roles" element={<RolesListPage />} />
            <Route path="roles/:roleCode" element={<RoleDetailPage />} />
          </Route>
        </Route>
      </Route>

      {/* Unknown route — show a dedicated 404 with a clear "go back / go to
          dashboard" affordance instead of silently bouncing to the dashboard.
          The message is non-enumerating (security-safe). */}
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
    </Suspense>
  );
}
