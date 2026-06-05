import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { completeSigninCallback } from '@/shared/auth/oidcClient';
import { useAuthStore } from '@/features/auth/authStore';
import { fetchCurrentUser } from '@/features/auth/authApi';
import { mapMeResponse, pickActiveTenant } from '@/features/auth/mapMeResponse';
import { routes } from '@/shared/config/routes';
import { Card } from '@/shared/components/ui/Card';
import { Button } from '@/shared/components/ui/Button';

/**
 * OIDC redirect callback (route: /auth/callback, OUTSIDE RequireAuth).
 *
 * Flow:
 *   1. signinRedirectCallback() — exchanges the auth code for tokens (PKCE),
 *      stored in memory by the UserManager.
 *   2. GET /api/v1/users/me with the access token -> CurrentUser.
 *   3. authStore.setSession(user, { value, expiresAt }).
 *   4. navigate to the originally requested route (state.returnTo) or dashboard.
 *
 * The access token is held in memory only (oidcClient + tokenStorage); it is
 * never written to web storage and never logged.
 */
export function AuthCallbackPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const setSession = useAuthStore((s) => s.setSession);
  const setActiveTenant = useAuthStore((s) => s.setActiveTenant);
  const [error, setError] = useState<string | null>(null);
  // StrictMode mounts effects twice in dev; guard the one-shot exchange.
  const ranRef = useRef(false);

  useEffect(() => {
    if (ranRef.current) return;
    ranRef.current = true;

    const run = async () => {
      try {
        // Exchanges the auth code for tokens EXACTLY ONCE (module-level guard),
        // immune to StrictMode/remount double-invocation reusing the code.
        const oidcUser = await completeSigninCallback();
        if (!oidcUser) {
          // OIDC disabled — nothing to process; send to login.
          navigate(routes.login, { replace: true });
          return;
        }
        const accessToken = oidcUser.access_token;
        if (!accessToken) {
          throw new Error('no_access_token');
        }
        // oidc-client-ts exposes expires_at in seconds (epoch); fall back to +5m.
        const expiresAt = oidcUser.expires_at
          ? oidcUser.expires_at * 1000
          : Date.now() + 5 * 60 * 1000;

        const me = await fetchCurrentUser(accessToken);
        const currentUser = mapMeResponse(me);

        setSession(currentUser, { value: accessToken, expiresAt });

        // Prefer the backend-marked active tenant when present.
        const active = pickActiveTenant(me);
        if (active) setActiveTenant(active);

        const returnTo =
          (oidcUser.state as { returnTo?: string } | undefined)?.returnTo ??
          routes.dashboard;
        navigate(returnTo, { replace: true });
      } catch {
        // Never surface token/PII in the message.
        setError(t('auth.callback_error'));
      }
    };

    void run();
  }, [navigate, setSession, setActiveTenant, t]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-background p-4">
      <Card className="w-full max-w-md" title={t('auth.signing_in')}>
        {error ? (
          <div className="space-y-4">
            <p className="text-sm text-danger">{error}</p>
            <Button fullWidth onClick={() => navigate(routes.login, { replace: true })}>
              {t('auth.back_to_login')}
            </Button>
          </div>
        ) : (
          <p className="text-sm text-text-secondary" role="status" aria-live="polite">
            {t('auth.signing_in_body')}
          </p>
        )}
      </Card>
    </div>
  );
}
