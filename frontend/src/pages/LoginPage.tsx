import { useNavigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '@/features/auth/authStore';
import { buildDevUser } from '@/features/auth/devAuth';
import { Button } from '@/shared/components/ui/Button';
import { Card } from '@/shared/components/ui/Card';
import { LanguageSwitcher } from '@/shared/components/layout/LanguageSwitcher';
import { routes } from '@/shared/config/routes';

/**
 * Login page — Phase 0+1 ships a dev login that seeds the auth store directly.
 * Backend will replace with OIDC code exchange (POST /auth/exchange).
 */
export function LoginPage() {
  const { t } = useTranslation();
  const setSession = useAuthStore((s) => s.setSession);
  const navigate = useNavigate();
  const location = useLocation();

  const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname ?? routes.dashboard;

  const loginAs = (role: 'super-admin' | 'consultant' | 'viewer') => {
    const user = buildDevUser(role);
    setSession(user, {
      value: `dev-token-${role}`,
      expiresAt: Date.now() + 60 * 60 * 1000,
    });
    navigate(from, { replace: true });
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-background p-4">
      <div className="absolute top-4 right-4">
        <LanguageSwitcher />
      </div>
      <Card className="w-full max-w-md" title={t('pages.login_title')} subtitle={t('pages.login_subtitle')}>
        <div className="space-y-2">
          <Button fullWidth onClick={() => loginAs('super-admin')}>
            {t('auth.as_super_admin')}
          </Button>
          <Button fullWidth variant="secondary" onClick={() => loginAs('consultant')}>
            {t('auth.as_consultant')}
          </Button>
          <Button fullWidth variant="ghost" onClick={() => loginAs('viewer')}>
            {t('auth.as_viewer')}
          </Button>
        </div>
      </Card>
    </div>
  );
}
