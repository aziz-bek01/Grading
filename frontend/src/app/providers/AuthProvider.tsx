import { useEffect, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { setMockActiveTenantId, setUnauthorizedHandler } from '@/shared/api/httpClient';
import { useAuthStore } from '@/features/auth/authStore';
import { routes } from '@/shared/config/routes';

/**
 * Wires the http client's 401-handler to the auth store and the router.
 * Tokens live in memory (see tokenStorage).
 *
 * Also syncs the active tenant id into the mock-only `X-Mock-Tenant-Id`
 * header so the in-process mock adapter can simulate JWT-derived tenancy.
 * The real backend ignores this header.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const signOut = useAuthStore((s) => s.signOut);
  const activeTenantId = useAuthStore((s) => s.activeTenant?.id ?? null);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      signOut();
      navigate(routes.login, { replace: true });
    });
    return () => setUnauthorizedHandler(null);
  }, [navigate, signOut]);

  useEffect(() => {
    setMockActiveTenantId(activeTenantId);
  }, [activeTenantId]);

  return <>{children}</>;
}
