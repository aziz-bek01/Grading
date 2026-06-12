import { useEffect, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  setActiveTenantHeader,
  setMockActiveTenantId,
  setUnauthorizedHandler,
} from '@/shared/api/httpClient';
import { useAuthStore } from '@/features/auth/authStore';
import { isOidcAvailable, startSignin } from '@/shared/auth/oidcClient';
import { routes } from '@/shared/config/routes';

/**
 * Wires the http client's 401-handler to the auth store and the router.
 * Tokens live in memory (see tokenStorage).
 *
 * 401 handling:
 *   - clears the local session (no IdP round-trip),
 *   - OIDC mode: re-initiates the IdP login (silent renew already failed in
 *     httpClient by the time this fires),
 *   - dev / OIDC-disabled: navigates to the in-app /login.
 *
 * Also syncs the active tenant id into the mock-only `X-Mock-Tenant-Id`
 * header so the in-process mock adapter can simulate JWT-derived tenancy.
 * The real backend ignores this header.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const clearSession = useAuthStore((s) => s.clearSession);
  const activeTenantId = useAuthStore((s) => s.activeTenant?.id ?? null);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      clearSession();
      if (isOidcAvailable()) {
        void startSignin(window.location.pathname + window.location.search);
      } else {
        navigate(routes.login, { replace: true });
      }
    });
    return () => setUnauthorizedHandler(null);
  }, [navigate, clearSession]);

  useEffect(() => {
    // Defensive backstop ONLY. The authoritative, SYNCHRONOUS mirror now lives
    // in the auth store setters (setSession/setActiveTenant/refreshUser/
    // clearSession/signOut → syncActiveTenantHeaders) so the header is attached
    // BEFORE any approval query/poll fires (FE-1, cross-tenant inbox leak fix).
    // This effect simply re-asserts the same value on mount / external hydration;
    // it is idempotent and must not be the only writer.
    setMockActiveTenantId(activeTenantId);
    setActiveTenantHeader(activeTenantId);
  }, [activeTenantId]);

  return <>{children}</>;
}
