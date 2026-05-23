import { Link, useLocation, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ChevronRight } from 'lucide-react';
import { useAuthStore } from '@/features/auth/authStore';
import { routes } from '@/shared/config/routes';
import { cn } from '@/shared/lib/cn';

interface Crumb {
  label: string;
  to?: string;
}

/**
 * Auto-derives breadcrumbs from the current location and the active project name.
 * Never displays salary data; only navigational labels.
 */
export function Breadcrumbs({ extra, className }: { extra?: Crumb[]; className?: string }) {
  const { t } = useTranslation();
  const { pathname } = useLocation();
  const params = useParams();
  const activeProject = useAuthStore((s) => s.activeProject);

  const crumbs: Crumb[] = [{ label: t('nav.dashboard'), to: routes.dashboard }];

  if (pathname.startsWith(routes.projects)) {
    crumbs.push({ label: t('nav.projects'), to: routes.projects });
  }

  const projectId = params.projectId;
  if (projectId) {
    const label = activeProject?.name ?? projectId;
    crumbs.push({ label, to: routes.projectWorkspace(projectId) });

    if (pathname.includes('/organization')) {
      crumbs.push({ label: t('nav.organization') });
    } else if (pathname.includes('/positions')) {
      crumbs.push({ label: t('nav.positions'), to: routes.projectPositions(projectId) });
    } else if (pathname.includes('/workspace')) {
      crumbs.push({ label: t('nav.workspace') });
    }
  }

  if (extra && extra.length) crumbs.push(...extra);

  return (
    <nav aria-label="Breadcrumb" className={cn('text-sm text-text-secondary', className)}>
      <ol className="flex items-center gap-1 flex-wrap">
        {crumbs.map((c, i) => {
          const isLast = i === crumbs.length - 1;
          return (
            <li key={`${c.label}-${i}`} className="inline-flex items-center gap-1">
              {c.to && !isLast ? (
                <Link to={c.to} className="hover:text-text-primary">
                  {c.label}
                </Link>
              ) : (
                <span className={cn(isLast && 'text-text-primary font-medium')}>{c.label}</span>
              )}
              {!isLast ? <ChevronRight size={12} aria-hidden /> : null}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
