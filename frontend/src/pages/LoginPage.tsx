import { useNavigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '@/features/auth/authStore';
import { buildDevUser } from '@/features/auth/devAuth';
import { env } from '@/shared/config/env';
import { startSignin } from '@/shared/auth/oidcClient';
import { Button } from '@/shared/components/ui/Button';
import { Card } from '@/shared/components/ui/Card';
import { LanguageSwitcher } from '@/shared/components/layout/LanguageSwitcher';
import { routes } from '@/shared/config/routes';
import hrlMarkWhite from '@/assets/brand/hrl-mark-white.svg';

/**
 * Login page — HR LABORATORIES branded.
 *  - Production (env.devAuthEnabled === false): a single "Sign in" hero CTA that
 *    redirects to the OIDC provider (ZITADEL) via startSignin().
 *  - Local dev (env.devAuthEnabled === true): the existing buildDevUser buttons
 *    seed the auth store directly. Unchanged logic — only restyled.
 *
 * Layout: a `bg-brand` gradient hero panel (white mark + tagline + concentric
 * corner rings) beside the sign-in card. See docs/mvp1/07b-hrl-brand-tokens.md §7.2.
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

  const signInWithOidc = () => {
    void startSignin(from);
  };

  return (
    <div className="min-h-screen grid grid-cols-1 lg:grid-cols-2 bg-brand-wash">
      {/* Brand hero — gradient panel with white mark, tagline, and corner rings */}
      <section className="relative hidden lg:flex flex-col justify-between overflow-hidden bg-brand p-12 text-text-inverse">
        <BrandRings />
        <div className="relative z-10">
          <img src={hrlMarkWhite} alt="HR LABORATORIES" className="h-12 w-auto" />
        </div>
        <div className="relative z-10">
          <h1 className="text-3xl font-semibold tracking-tight">HR LABORATORIES</h1>
          {/* Brand tagline is an English constant per 07b §6. */}
          <p className="mt-2 text-lg text-white/85">People. Systems. Results.</p>
        </div>
        <div className="relative z-10 text-sm text-white/70">grading.hrlab.uz</div>
      </section>

      {/* Sign-in panel */}
      <section className="relative flex items-center justify-center p-4">
        <div className="absolute top-4 right-4">
          <LanguageSwitcher />
        </div>
        {/* Mobile-only brand mark (hero panel is hidden < lg) */}
        <div className="lg:hidden absolute top-4 left-4">
          <span className="inline-flex h-10 w-10 items-center justify-center rounded-xl bg-brand">
            <img src={hrlMarkWhite} alt="HR LABORATORIES" className="h-5 w-auto" />
          </span>
        </div>
        <Card className="w-full max-w-md" title={t('pages.login_title')} subtitle={t('pages.login_subtitle')}>
          {env.devAuthEnabled ? (
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
          ) : (
            <div className="space-y-2">
              {/* The single hero CTA may use the gradient (07b §3.3 / §7.2). */}
              <Button fullWidth className="bg-brand hover:bg-brand" onClick={signInWithOidc}>
                {t('auth.sign_in')}
              </Button>
            </div>
          )}
        </Card>
      </section>
    </div>
  );
}

/**
 * Signature concentric gradient corner rings. Decorative only (aria-hidden),
 * sit behind content (z-0), drawn with the brand gradient at low opacity over
 * the gradient hero. See 07b §3.4.
 */
function BrandRings() {
  return (
    <div aria-hidden className="pointer-events-none absolute inset-0 z-0 overflow-hidden">
      {/* top-right cluster */}
      <svg
        className="absolute -top-24 -right-24 h-[28rem] w-[28rem] text-white/15"
        viewBox="0 0 400 400"
        fill="none"
        stroke="currentColor"
      >
        <circle cx="200" cy="200" r="190" strokeWidth="8" />
        <circle cx="200" cy="200" r="140" strokeWidth="8" />
        <circle cx="200" cy="200" r="90" strokeWidth="8" />
      </svg>
      {/* bottom-left cluster */}
      <svg
        className="absolute -bottom-28 -left-28 h-[26rem] w-[26rem] text-white/10"
        viewBox="0 0 400 400"
        fill="none"
        stroke="currentColor"
      >
        <circle cx="200" cy="200" r="190" strokeWidth="8" />
        <circle cx="200" cy="200" r="130" strokeWidth="8" />
      </svg>
    </div>
  );
}
