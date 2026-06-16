import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { TenantSelector } from './TenantSelector';
import { ProjectSelector } from './ProjectSelector';
import { LanguageSwitcher } from './LanguageSwitcher';
import { UserMenu } from './UserMenu';
import { useAuthStore } from '@/features/auth/authStore';
import { routes } from '@/shared/config/routes';
import hrlMark from '@/assets/brand/hrl-mark-gradient.svg';

/**
 * TopBar — always visible. Shows active company-client + active project (design foundation §5).
 * The 6px coloured bar under the header is the tenant visual fingerprint.
 */
export function TopBar() {
  const { t } = useTranslation();
  const activeTenant = useAuthStore((s) => s.activeTenant);
  const activeProject = useAuthStore((s) => s.activeProject);

  // a11y (Batch 6): the banner landmark's accessible name is now localized in
  // all 4 locales (was a hardcoded English template literal).
  const ariaActive = t('topBar.active_context', {
    tenant: activeTenant?.brand_name ?? t('topBar.no_tenant'),
    project: activeProject?.name ?? t('topBar.no_project'),
  });

  const hue = activeTenant?.fingerprint_hue;
  const fingerprintColor = hue !== undefined ? `hsl(${hue} 70% 45%)` : 'transparent';

  return (
    <header className="sticky top-0 z-20 bg-surface border-b border-border" aria-label={ariaActive}>
      <div className="h-14 flex items-center justify-between gap-4 px-4">
        <div className="flex items-center gap-3">
          <Link to={routes.dashboard} className="flex items-center gap-2" aria-label={t('topBar.home_aria')}>
            <img src={hrlMark} alt="HR LABORATORIES" className="h-7 w-auto" />
          </Link>
          <TenantSelector />
          <ProjectSelector />
        </div>
        <div className="flex items-center gap-2">
          <LanguageSwitcher />
          <UserMenu />
        </div>
      </div>
      {/*
        Tenant fingerprint bar — ALWAYS present at 4px (h-1) so the header
        height is a constant 56+4=60px (single source of truth, see AppShell
        sticky math). Coloured when a tenant is active, transparent otherwise.
      */}
      <div className="h-1 w-full" style={{ backgroundColor: fingerprintColor }} aria-hidden />
    </header>
  );
}
