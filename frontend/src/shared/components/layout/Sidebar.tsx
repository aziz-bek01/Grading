import { type ReactNode } from 'react';
import { NavLink } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  LayoutDashboard,
  Building2,
  FolderKanban,
  Network as Sitemap,
  Briefcase,
  ClipboardList,
  Scale,
  CheckSquare,
  Layers,
  DollarSign,
  FileText,
  Folder,
  Sparkles,
  ScrollText,
  Users,
  ShieldCheck,
  Lock,
  Inbox,
  Upload,
  Download,
  PanelLeftClose,
  PanelLeft,
} from 'lucide-react';
import { useAuthStore } from '@/features/auth/authStore';
import { PERMISSIONS, type PermissionCode } from '@/shared/types/permissions';
import { routes } from '@/shared/config/routes';
import { LockedBadge } from '@/shared/components/status/LockedBadge';
import { useMyApprovalInbox } from '@/features/approval/hooks/useApprovals';
import { cn } from '@/shared/lib/cn';

interface NavItem {
  to: string;
  label: string;
  icon: ReactNode;
  permission?: PermissionCode | PermissionCode[];
  locked?: boolean;
  /** PO-5: per-item roadmap tooltip ("Available in MVP 2/3/4"). */
  tooltip?: string;
  /**
   * PO-3: when true the item is rendered disabled (no <NavLink>) because
   * there is no active project to scope the route to. Tooltip explains why.
   */
  disabled?: boolean;
  /** Optional unread / pending count rendered as a chip on the right. */
  badge?: number;
}

interface SidebarProps {
  /** Extra classes appended to the <aside> root (e.g. sticky positioning). */
  className?: string;
}

export function Sidebar({ className }: SidebarProps = {}) {
  const { t } = useTranslation();
  const user = useAuthStore((s) => s.user);
  const activeProject = useAuthStore((s) => s.activeProject);
  const collapsed = useAuthStore((s) => s.sidebarCollapsed);
  const setSidebarCollapsed = useAuthStore((s) => s.setSidebarCollapsed);
  // Rules of Hooks: this must run unconditionally on every render, so it has
  // to be called before the `if (!user)` early return below. The helper masks
  // the count to 0 when the user is missing or lacks approval permissions.
  const inboxCount = useApprovalInboxCount(user?.permissions);

  if (!user) return null;
  const has = (codes: PermissionCode | PermissionCode[] | undefined) => {
    if (!codes) return true;
    const arr = Array.isArray(codes) ? codes : [codes];
    return arr.some((c) => user.permissions.includes(c));
  };

  // PO-3: no more hardcoded '/demo' sentinel. When no project is active
  // every workspace nav item renders as a disabled stub with a tooltip
  // "Select a project first" so users never hit a broken /projects/demo URL.
  const activeProjectId = activeProject?.id ?? null;
  const projectIdForRoutes = activeProjectId ?? 'placeholder';
  const noProjectTooltip = t('sidebar.select_project_first');

  const portfolioItems: NavItem[] = [
    { to: routes.dashboard, label: t('nav.dashboard'), icon: <LayoutDashboard size={18} aria-hidden /> },
    { to: routes.clients, label: t('nav.clients'), icon: <Building2 size={18} aria-hidden />, permission: PERMISSIONS.TENANT_READ },
    { to: routes.projects, label: t('nav.projects'), icon: <FolderKanban size={18} aria-hidden />, permission: PERMISSIONS.PROJECT_READ },
  ];

  const projectItems: NavItem[] = [
    { to: routes.projectWorkspace(projectIdForRoutes), label: t('nav.workspace'), icon: <FolderKanban size={18} aria-hidden />, permission: PERMISSIONS.PROJECT_READ },
    { to: routes.projectOrganization(projectIdForRoutes), label: t('nav.organization'), icon: <Sitemap size={18} aria-hidden />, permission: PERMISSIONS.ORG_READ },
    { to: routes.projectPositions(projectIdForRoutes), label: t('nav.positions'), icon: <Briefcase size={18} aria-hidden />, permission: PERMISSIONS.POSITION_READ },
    { to: routes.projectPositions(projectIdForRoutes), label: t('nav.job_profiles'), icon: <ClipboardList size={18} aria-hidden />, permission: PERMISSIONS.JOB_PROFILE_READ },
    { to: routes.projectMethodology(projectIdForRoutes), label: t('nav.methodology'), icon: <Scale size={18} aria-hidden />, permission: PERMISSIONS.METHODOLOGY_READ },
    { to: routes.projectEvaluation(projectIdForRoutes), label: t('nav.evaluation'), icon: <CheckSquare size={18} aria-hidden />, permission: PERMISSIONS.EVALUATION_READ },
    { to: routes.projectGrades(projectIdForRoutes), label: t('nav.grades'), icon: <Layers size={18} aria-hidden />, permission: PERMISSIONS.GRADE_READ },
    { to: routes.projectReports(projectIdForRoutes), label: t('nav.reports'), icon: <FileText size={18} aria-hidden />, permission: PERMISSIONS.REPORT_READ },
  ].map((item) => ({
    ...item,
    disabled: activeProjectId === null,
    tooltip: activeProjectId === null ? noProjectTooltip : undefined,
  }));

  const governanceItems: NavItem[] = [
    {
      to: routes.approvalsInbox,
      label: t('nav.approvals'),
      icon: <Inbox size={18} aria-hidden />,
      permission: [
        PERMISSIONS.APPROVAL_REQUEST_CREATE,
        PERMISSIONS.APPROVAL_STEP_APPROVE,
      ],
      badge: inboxCount,
    },
    {
      to: routes.projectImports(projectIdForRoutes),
      label: t('nav.imports'),
      icon: <Upload size={18} aria-hidden />,
      permission: PERMISSIONS.IMPORT_READ,
      disabled: activeProjectId === null,
      tooltip: activeProjectId === null ? noProjectTooltip : undefined,
    },
    {
      to: routes.projectExports(projectIdForRoutes),
      label: t('nav.exports'),
      icon: <Download size={18} aria-hidden />,
      permission: PERMISSIONS.EXPORT_READ,
      disabled: activeProjectId === null,
      tooltip: activeProjectId === null ? noProjectTooltip : undefined,
    },
    { to: routes.audit, label: t('nav.audit'), icon: <ScrollText size={18} aria-hidden />, permission: PERMISSIONS.AUDIT_READ },
    // FE-UA-001: Users & Access lives at /app/users (was /app/users-access placeholder).
    { to: routes.usersAccess, label: t('nav.users_access'), icon: <Users size={18} aria-hidden />, permission: [PERMISSIONS.USER_LIST, PERMISSIONS.USER_ACCESS_MANAGE] },
    // Roles admin (slice E2) — only for users who can manage access (USER_ACCESS_MANAGE).
    { to: routes.roles, label: t('nav.roles'), icon: <ShieldCheck size={18} aria-hidden />, permission: PERMISSIONS.USER_ACCESS_MANAGE },
  ];

  // Always rendered as locked stubs for MVP 1 (design-foundation §6.3).
  // PO-5: each item carries its own roadmap tooltip explaining which
  // release brings the feature.
  const lockedItems: NavItem[] = [
    {
      to: routes.projectCompensation(projectIdForRoutes),
      label: t('nav.compensation'),
      icon: <DollarSign size={18} aria-hidden />,
      locked: true,
      tooltip: t('sidebar.soonRoadmap.compensation'),
    },
    {
      to: `/app/projects/${projectIdForRoutes}/files`,
      label: t('nav.files'),
      icon: <Folder size={18} aria-hidden />,
      locked: true,
      tooltip: t('sidebar.soonRoadmap.files'),
    },
    {
      to: `/app/projects/${projectIdForRoutes}/ai`,
      label: t('nav.ai_assist'),
      icon: <Sparkles size={18} aria-hidden />,
      locked: true,
      tooltip: t('sidebar.soonRoadmap.aiAssist'),
    },
  ];

  const toggle = (
    <SidebarToggle
      collapsed={collapsed}
      onToggle={() => setSidebarCollapsed(!collapsed)}
    />
  );

  return (
    <aside
      className={cn(
        'hidden md:flex md:flex-col shrink-0 border-r border-border bg-surface',
        'transition-[width] duration-150',
        collapsed ? 'w-16' : 'w-60',
        className,
      )}
      aria-label="Primary navigation"
      data-collapsed={collapsed || undefined}
      data-testid="app-sidebar"
    >
      <nav className="flex-1 overflow-y-auto py-3 px-2 text-sm">
        {collapsed ? (
          <div className="flex justify-center pb-2 mb-1 border-b border-divider">
            {toggle}
          </div>
        ) : null}
        <SidebarGroup
          label={t('nav.dashboard')}
          collapsed={collapsed}
          action={collapsed ? undefined : toggle}
        >
          {portfolioItems.filter((i) => has(i.permission)).map((item) => (
            <SidebarLink key={item.to} item={item} collapsed={collapsed} />
          ))}
        </SidebarGroup>

        <SidebarGroup label={t('nav.workspace')} collapsed={collapsed}>
          {projectItems.filter((i) => has(i.permission)).map((item) =>
            item.disabled
              ? <DisabledNavItem key={`${item.label}-disabled`} item={item} collapsed={collapsed} />
              : <SidebarLink key={item.to} item={item} collapsed={collapsed} />,
          )}
        </SidebarGroup>

        <SidebarGroup label={t('nav.governance')} collapsed={collapsed}>
          {governanceItems.filter((i) => has(i.permission)).map((item) =>
            item.disabled
              ? <DisabledNavItem key={`${item.label}-disabled`} item={item} collapsed={collapsed} />
              : <SidebarLink key={item.to} item={item} collapsed={collapsed} />,
          )}
        </SidebarGroup>

        <SidebarGroup label={t('nav.soon')} collapsed={collapsed}>
          {lockedItems.map((item) => (
            <LockedNavItem key={item.to} item={item} collapsed={collapsed} />
          ))}
        </SidebarGroup>
      </nav>
    </aside>
  );
}

function SidebarGroup({
  label,
  collapsed,
  action,
  children,
}: {
  label: string;
  collapsed?: boolean;
  /** Optional control rendered right-aligned in the (expanded) label row. */
  action?: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="mt-4 first:mt-0">
      {collapsed ? (
        // Collapsed: hide the text label, keep a thin divider for grouping.
        <div className="mx-2 mb-1 border-t border-divider" aria-hidden />
      ) : (
        <div className="px-3 mb-1 flex items-center justify-between">
          <span className="text-xs uppercase tracking-wide text-text-muted">{label}</span>
          {action}
        </div>
      )}
      <ul className="space-y-0.5">{children}</ul>
    </div>
  );
}

function SidebarToggle({
  collapsed,
  onToggle,
}: {
  collapsed?: boolean;
  onToggle: () => void;
}) {
  const { t } = useTranslation();
  return (
    <button
      type="button"
      onClick={onToggle}
      title={collapsed ? t('sidebar.expand') : t('sidebar.collapse')}
      aria-label={t('sidebar.toggle_aria')}
      aria-expanded={!collapsed}
      data-testid="sidebar-toggle"
      className="inline-flex items-center justify-center h-7 w-7 rounded-md text-text-muted hover:bg-divider hover:text-text-primary focus-visible:ring-2 focus-visible:ring-primary-500"
    >
      {collapsed ? (
        <PanelLeft size={16} aria-hidden />
      ) : (
        <PanelLeftClose size={16} aria-hidden />
      )}
    </button>
  );
}

function SidebarLink({ item, collapsed }: { item: NavItem; collapsed?: boolean }) {
  const hasBadge = !!(item.badge && item.badge > 0);
  return (
    <li>
      <NavLink
        to={item.to}
        title={collapsed ? item.label : undefined}
        className={({ isActive }) =>
          cn(
            'flex items-center gap-2 px-3 py-2.5 rounded-md text-sm',
            collapsed && 'justify-center px-0',
            isActive
              ? collapsed
                ? 'bg-primary-50 text-primary-700'
                : 'bg-primary-50 text-primary-700 border-l-2 border-primary-500'
              : 'text-text-primary hover:bg-divider',
          )
        }
      >
        <span className="relative text-text-secondary" aria-hidden>
          {item.icon}
          {collapsed && hasBadge ? (
            // Collapsed: badge shrinks to a small red dot anchored on the icon.
            <span
              className="absolute -top-1 -right-1 h-2 w-2 rounded-full bg-danger-500"
              data-testid="sidebar-badge-dot"
            />
          ) : null}
        </span>
        {!collapsed && (
          <span className="truncate flex-1">{item.label}</span>
        )}
        {!collapsed && hasBadge ? (
          <span
            className="inline-flex items-center justify-center min-w-[20px] h-5 px-1.5 rounded-full bg-danger-500 text-text-inverse text-xs font-medium"
            data-testid="sidebar-badge"
          >
            {item.badge! > 99 ? '99+' : item.badge}
          </span>
        ) : null}
      </NavLink>
    </li>
  );
}

/**
 * Wraps `useMyApprovalInbox` so the value is only displayed when the user
 * actually has approval permissions. The hook itself is always called
 * (Rules of Hooks), but the count is masked to 0 otherwise.
 */
function useApprovalInboxCount(permissions: PermissionCode[] | undefined): number {
  const inbox = useMyApprovalInbox();
  const canApprove =
    !!permissions &&
    (permissions.includes(PERMISSIONS.APPROVAL_STEP_APPROVE) ||
      permissions.includes(PERMISSIONS.APPROVAL_REQUEST_CREATE));
  if (!canApprove) return 0;
  return inbox.data?.items.length ?? 0;
}

/**
 * PO-3: rendered when an active project is not selected. Same visual
 * shape as `LockedNavItem` so the user sees what is available once they
 * pick a project, but no clickable link / no broken /demo URL.
 */
function DisabledNavItem({ item, collapsed }: { item: NavItem; collapsed?: boolean }) {
  return (
    <li>
      <div
        className={cn(
          'flex items-center gap-2 px-3 py-2.5 rounded-md text-sm text-text-muted cursor-not-allowed',
          collapsed && 'justify-center px-0',
        )}
        aria-disabled
        title={collapsed ? `${item.label}${item.tooltip ? ` — ${item.tooltip}` : ''}` : item.tooltip ?? ''}
        data-testid="sidebar-disabled-item"
      >
        <span className="text-text-secondary" aria-hidden>
          {item.icon}
        </span>
        {!collapsed && <span className="truncate">{item.label}</span>}
      </div>
    </li>
  );
}

function LockedNavItem({ item, collapsed }: { item: NavItem; collapsed?: boolean }) {
  return (
    <li>
      <div
        className={cn(
          'flex items-center gap-2 px-3 py-2.5 rounded-md text-sm text-text-muted cursor-not-allowed',
          collapsed ? 'justify-center px-0' : 'justify-between',
        )}
        aria-disabled
        title={collapsed ? `${item.label}${item.tooltip ? ` — ${item.tooltip}` : ''}` : item.tooltip ?? ''}
        data-testid="sidebar-locked-item"
      >
        {collapsed ? (
          <span className="relative text-text-secondary" aria-hidden>
            {item.icon}
            <Lock
              size={10}
              className="absolute -bottom-0.5 -right-1 text-text-muted"
              aria-hidden
            />
          </span>
        ) : (
          <>
            <span className="flex items-center gap-2 truncate">
              <Lock size={14} aria-hidden />
              <span className="truncate">{item.label}</span>
            </span>
            <LockedBadge variant="soon" />
          </>
        )}
      </div>
    </li>
  );
}
