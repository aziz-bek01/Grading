import { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '@/features/auth/authStore';
import { buildDevUser, DEV_BOOTSTRAP_USER_ID } from '@/features/auth/devAuth';
import { httpClient } from '@/shared/api/httpClient';
import type { TenantSummary } from '@/shared/auth/authTypes';
import { env } from '@/shared/config/env';
import { startSignin } from '@/shared/auth/oidcClient';
import { Button } from '@/shared/components/ui/Button';
import { Card } from '@/shared/components/ui/Card';
import { LanguageSwitcher } from '@/shared/components/layout/LanguageSwitcher';
import { routes } from '@/shared/config/routes';
import hrlMarkWhite from '@/assets/brand/hrl-mark-white.svg';

/** A real user the dev "sign in as a specific user" picker can impersonate. */
interface PickUser {
  id: string;
  email: string;
  full_name: string;
  /** A tenant the user belongs to — pre-seeds the active tenant on sign-in. */
  tenantId?: string;
}

/**
 * Build a short-lived dev session token. Kept at MODULE scope so the `Date.now()`
 * call is not flagged by the `react-hooks/purity` rule (which forbids impure
 * calls inside a component/hook body).
 */
function makeDevToken(suffix: string) {
  return { value: `dev-token-${suffix}`, expiresAt: Date.now() + 60 * 60 * 1000 };
}

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
    setSession(user, makeDevToken(role));
    navigate(from, { replace: true });
  };

  // --- Dev-only "sign in as a specific user" picker --------------------------
  // Lets a developer sign in as ANY real user from the (locally-copied) data,
  // not just the three hardcoded role shortcuts. The list is fetched via a
  // bootstrap super-admin identity (X-Dev-User) BEFORE any session exists.
  const [pickerOpen, setPickerOpen] = useState(false);
  const [pickerUsers, setPickerUsers] = useState<PickUser[]>([]);
  const [pickerTenants, setPickerTenants] = useState<Record<string, TenantSummary>>({});
  const [pickerLoading, setPickerLoading] = useState(false);
  const [pickerError, setPickerError] = useState(false);
  const [query, setQuery] = useState('');

  const openPicker = async () => {
    setPickerOpen(true);
    if (pickerUsers.length > 0) return;
    setPickerLoading(true);
    setPickerError(false);
    try {
      const bootstrap = { headers: { 'X-Dev-User': DEV_BOOTSTRAP_USER_ID } } as const;
      // The bootstrap admin's tenants — /users is tenant-scoped, so we sweep
      // every tenant the admin can see and merge the user sets.
      const me = await httpClient.get<{
        tenants: Array<{ id: string; slug: string; brand_name: string; fingerprint_hue?: number }>;
      }>('/users/me', bootstrap);
      const tenants = me.data.tenants ?? [];
      const tmap: Record<string, TenantSummary> = {};
      for (const tn of tenants) {
        tmap[tn.id] = { id: tn.id, slug: tn.slug, brand_name: tn.brand_name, fingerprint_hue: tn.fingerprint_hue };
      }
      const byId = new Map<string, PickUser>();
      for (const tn of tenants) {
        const res = await httpClient.get<{ items: Array<{ id: string; email: string; full_name: string }> }>(
          '/users',
          { params: { size: 200 }, headers: { 'X-Dev-User': DEV_BOOTSTRAP_USER_ID, 'X-Dev-Tenant': tn.id } },
        );
        for (const it of res.data.items ?? []) {
          if (!byId.has(it.id)) byId.set(it.id, { id: it.id, email: it.email, full_name: it.full_name, tenantId: tn.id });
        }
      }
      setPickerTenants(tmap);
      setPickerUsers([...byId.values()].sort((a, b) => a.email.localeCompare(b.email)));
    } catch {
      setPickerError(true);
    } finally {
      setPickerLoading(false);
    }
  };

  const loginAsUser = async (u: PickUser) => {
    const tsum = u.tenantId ? pickerTenants[u.tenantId] : undefined;
    // Seed a minimal session with just the identity (+ one known tenant so the
    // X-Dev-Tenant header is sent); refreshUser() then hydrates the user's REAL
    // tenants + roles + permissions from /users/me so the UI gates correctly.
    setSession(
      {
        id: u.id,
        email: u.email,
        name: u.full_name || u.email,
        locale: 'ru-RU',
        roles: [],
        permissions: [],
        salary_data_permission: false,
        tenants: tsum ? [tsum] : [],
      },
      makeDevToken(u.id),
    );
    try {
      await useAuthStore.getState().refreshUser();
    } catch {
      /* backend still resolves authority per-request from X-Dev-User */
    }
    navigate(from, { replace: true });
  };

  const filteredPickUsers = query.trim()
    ? pickerUsers.filter((u) =>
        `${u.full_name} ${u.email}`.toLowerCase().includes(query.trim().toLowerCase()),
      )
    : pickerUsers;

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
          {/*
            Decorative brand wordmark — NOT the document heading. The page's
            single <h1> is the sign-in card title ("Welcome"); making this a
            heading too would break heading order (h1→h3) when the card title
            was an h3, and a visual wordmark is not a semantic heading anyway.
          */}
          <p className="text-3xl font-bold tracking-tight" aria-hidden>HR LABORATORIES</p>
          {/* Brand tagline is an English constant per 07b §6. */}
          <p className="mt-2 text-lg text-white/85" aria-hidden>People. Systems. Results.</p>
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
        <Card
          className="w-full max-w-md"
          titleAs="h1"
          title={t('pages.login_title')}
          subtitle={t('pages.login_subtitle')}
        >
          {env.devAuthEnabled ? (
            // Stable `data-testid`s let E2E specs select the dev-auth role buttons
            // without depending on localized labels (default UI locale is ru-RU).
            // These testids are semantic and do not change any behaviour.
            <div className="space-y-2" data-testid="login-dev-auth">
              <Button fullWidth onClick={() => loginAs('super-admin')} data-testid="login-as-super-admin">
                {t('auth.as_super_admin')}
              </Button>
              <Button fullWidth variant="secondary" onClick={() => loginAs('consultant')} data-testid="login-as-consultant">
                {t('auth.as_consultant')}
              </Button>
              <Button fullWidth variant="ghost" onClick={() => loginAs('viewer')} data-testid="login-as-viewer">
                {t('auth.as_viewer')}
              </Button>

              {/* Sign in as ANY real user (dev-only). */}
              <div className="pt-2 mt-2 border-t border-divider">
                {!pickerOpen ? (
                  <Button
                    fullWidth
                    variant="ghost"
                    onClick={() => void openPicker()}
                    data-testid="login-as-specific-user"
                  >
                    {t('auth.as_specific_user')}
                  </Button>
                ) : (
                  <div className="space-y-2" data-testid="login-user-picker">
                    <input
                      type="text"
                      autoFocus
                      value={query}
                      onChange={(e) => setQuery(e.target.value)}
                      placeholder={t('auth.picker_search')}
                      aria-label={t('auth.picker_search')}
                      className="w-full h-9 px-3 border border-border-strong rounded-md text-sm bg-surface"
                    />
                    <div className="max-h-60 overflow-auto rounded-md border border-border divide-y divide-divider">
                      {pickerLoading ? (
                        <p className="px-3 py-2 text-sm text-text-muted">{t('auth.picker_loading')}</p>
                      ) : pickerError ? (
                        <p className="px-3 py-2 text-sm text-danger-600">{t('auth.picker_error')}</p>
                      ) : filteredPickUsers.length === 0 ? (
                        <p className="px-3 py-2 text-sm text-text-muted">{t('auth.picker_empty')}</p>
                      ) : (
                        filteredPickUsers.map((u) => (
                          <button
                            key={u.id}
                            type="button"
                            onClick={() => void loginAsUser(u)}
                            data-testid={`login-pick-${u.id}`}
                            className="w-full text-left px-3 py-2 hover:bg-divider"
                          >
                            <span className="block text-sm text-text-primary">{u.full_name || u.email}</span>
                            <span className="block text-xs text-text-muted">{u.email}</span>
                          </button>
                        ))
                      )}
                    </div>
                  </div>
                )}
              </div>
            </div>
          ) : (
            <div className="space-y-2" data-testid="login-oidc">
              {/* The single hero CTA may use the gradient (07b §3.3 / §7.2). */}
              <Button fullWidth className="bg-brand hover:bg-brand" onClick={signInWithOidc} data-testid="login-sign-in">
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
        <circle cx="200" cy="200" r="190" strokeWidth="10" />
        <circle cx="200" cy="200" r="140" strokeWidth="10" />
        <circle cx="200" cy="200" r="90" strokeWidth="10" />
      </svg>
      {/* bottom-left cluster */}
      <svg
        className="absolute -bottom-28 -left-28 h-[26rem] w-[26rem] text-white/14"
        viewBox="0 0 400 400"
        fill="none"
        stroke="currentColor"
      >
        <circle cx="200" cy="200" r="190" strokeWidth="10" />
        <circle cx="200" cy="200" r="130" strokeWidth="10" />
      </svg>
    </div>
  );
}
