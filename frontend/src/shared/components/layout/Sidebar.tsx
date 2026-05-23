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
  Lock,
} from 'lucide-react';
import { useAuthStore } from '@/features/auth/authStore';
import { PERMISSIONS, type PermissionCode } from '@/shared/types/permissions';
import { routes } from '@/shared/config/routes';
import { LockedBadge } from '@/shared/components/status/LockedBadge';
import { cn } from '@/shared/lib/cn';

interface NavItem {
  to: string;
  label: string;
  icon: ReactNode;
  permission?: PermissionCode | PermissionCode[];
  locked?: boolean;
}

export function Sidebar() {
  const { t } = useTranslation();
  const user = useAuthStore((s) => s.user);
  const activeProject = useAuthStore((s) => s.activeProject);

  if (!user) return null;
  const has = (codes: PermissionCode | PermissionCode[] | undefined) => {
    if (!codes) return true;
    const arr = Array.isArray(codes) ? codes : [codes];
    return arr.some((c) => user.permissions.includes(c));
  };

  const projectId = activeProject?.id ?? 'demo';

  const portfolioItems: NavItem[] = [
    { to: routes.dashboard, label: t('nav.dashboard'), icon: <LayoutDashboard size={18} aria-hidden /> },
    { to: routes.clients, label: t('nav.clients'), icon: <Building2 size={18} aria-hidden />, permission: PERMISSIONS.TENANT_READ },
    { to: routes.projects, label: t('nav.projects'), icon: <FolderKanban size={18} aria-hidden />, permission: PERMISSIONS.PROJECT_READ },
  ];

  const projectItems: NavItem[] = [
    { to: routes.projectWorkspace(projectId), label: t('nav.workspace'), icon: <FolderKanban size={18} aria-hidden />, permission: PERMISSIONS.PROJECT_READ },
    { to: routes.projectOrganization(projectId), label: t('nav.organization'), icon: <Sitemap size={18} aria-hidden />, permission: PERMISSIONS.ORG_READ },
    { to: routes.projectPositions(projectId), label: t('nav.positions'), icon: <Briefcase size={18} aria-hidden />, permission: PERMISSIONS.POSITION_READ },
    { to: routes.projectPositions(projectId), label: t('nav.job_profiles'), icon: <ClipboardList size={18} aria-hidden />, permission: PERMISSIONS.JOB_PROFILE_READ },
    { to: routes.projectMethodology(projectId), label: t('nav.methodology'), icon: <Scale size={18} aria-hidden />, permission: PERMISSIONS.METHODOLOGY_READ },
    { to: routes.projectEvaluation(projectId), label: t('nav.evaluation'), icon: <CheckSquare size={18} aria-hidden />, permission: PERMISSIONS.EVALUATION_READ },
    { to: routes.projectGrades(projectId), label: t('nav.grades'), icon: <Layers size={18} aria-hidden />, permission: PERMISSIONS.GRADE_READ },
  ];

  const governanceItems: NavItem[] = [
    { to: routes.audit, label: t('nav.audit'), icon: <ScrollText size={18} aria-hidden />, permission: PERMISSIONS.AUDIT_READ },
    { to: routes.usersAccess, label: t('nav.users_access'), icon: <Users size={18} aria-hidden />, permission: PERMISSIONS.USER_READ },
  ];

  // Always rendered as locked stubs for MVP 1 (design-foundation §6.3)
  const lockedItems: NavItem[] = [
    { to: routes.projectCompensation(projectId), label: t('nav.compensation'), icon: <DollarSign size={18} aria-hidden />, locked: true },
    { to: routes.projectReports(projectId), label: t('nav.reports'), icon: <FileText size={18} aria-hidden />, locked: true },
    { to: `/app/projects/${projectId}/files`, label: t('nav.files'), icon: <Folder size={18} aria-hidden />, locked: true },
    { to: `/app/projects/${projectId}/ai`, label: t('nav.ai_assist'), icon: <Sparkles size={18} aria-hidden />, locked: true },
  ];

  return (
    <aside
      className="hidden md:flex md:flex-col w-60 shrink-0 border-r border-border bg-surface min-h-[calc(100vh-56px-6px)]"
      aria-label="Primary navigation"
    >
      <nav className="flex-1 overflow-y-auto py-3 px-2 text-sm">
        <SidebarGroup label={t('nav.dashboard')}>
          {portfolioItems.filter((i) => has(i.permission)).map((item) => (
            <SidebarLink key={item.to} item={item} />
          ))}
        </SidebarGroup>

        <SidebarGroup label={t('nav.workspace')}>
          {projectItems.filter((i) => has(i.permission)).map((item) => (
            <SidebarLink key={item.to} item={item} />
          ))}
        </SidebarGroup>

        <SidebarGroup label={t('nav.governance')}>
          {governanceItems.filter((i) => has(i.permission)).map((item) => (
            <SidebarLink key={item.to} item={item} />
          ))}
        </SidebarGroup>

        <SidebarGroup label={t('nav.soon')}>
          {lockedItems.map((item) => (
            <LockedNavItem key={item.to} item={item} />
          ))}
        </SidebarGroup>
      </nav>
    </aside>
  );
}

function SidebarGroup({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="mt-4 first:mt-0">
      <div className="px-3 mb-1 text-xs uppercase tracking-wide text-text-muted">{label}</div>
      <ul className="space-y-0.5">{children}</ul>
    </div>
  );
}

function SidebarLink({ item }: { item: NavItem }) {
  return (
    <li>
      <NavLink
        to={item.to}
        className={({ isActive }) =>
          cn(
            'flex items-center gap-2 px-3 py-2.5 rounded-md text-sm',
            isActive
              ? 'bg-primary-50 text-primary-700 border-l-2 border-primary-500'
              : 'text-text-primary hover:bg-divider',
          )
        }
      >
        <span className="text-text-secondary" aria-hidden>
          {item.icon}
        </span>
        <span className="truncate">{item.label}</span>
      </NavLink>
    </li>
  );
}

function LockedNavItem({ item }: { item: NavItem }) {
  return (
    <li>
      <div
        className="flex items-center justify-between gap-2 px-3 py-2.5 rounded-md text-sm text-text-muted cursor-not-allowed"
        aria-disabled
        title="Available in next release"
      >
        <span className="flex items-center gap-2 truncate">
          <Lock size={14} aria-hidden />
          <span className="truncate">{item.label}</span>
        </span>
        <LockedBadge variant="soon" />
      </div>
    </li>
  );
}
