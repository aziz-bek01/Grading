import { Link } from 'react-router-dom';
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
  const activeTenant = useAuthStore((s) => s.activeTenant);
  const activeProject = useAuthStore((s) => s.activeProject);

  const ariaActive = `Active company-client: ${activeTenant?.brand_name ?? 'All company-clients'}. Active project: ${activeProject?.name ?? 'none'}.`;

  const hue = activeTenant?.fingerprint_hue;
  const fingerprintColor = hue !== undefined ? `hsl(${hue} 70% 45%)` : 'transparent';

  return (
    <header className="sticky top-0 z-20 bg-surface border-b border-border" aria-label={ariaActive}>
      <div className="h-14 flex items-center justify-between gap-4 px-4">
        <div className="flex items-center gap-3">
          <Link to={routes.dashboard} className="flex items-center gap-2" aria-label="grading.hrlab.uz home">
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
      {/* Tenant fingerprint bar */}
      <div className="h-1.5 w-full" style={{ backgroundColor: fingerprintColor }} aria-hidden />
    </header>
  );
}
