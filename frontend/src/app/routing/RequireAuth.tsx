import { useEffect, useRef } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/features/auth/authStore';
import { routes } from '@/shared/config/routes';
import { isOidcAvailable, startSignin } from '@/shared/auth/oidcClient';

export function RequireAuth() {
  const isAuth = useAuthStore((s) => s.isAuthenticated);
  const location = useLocation();
  const redirectingRef = useRef(false);
  const oidc = isOidcAvailable();

  // OIDC mode: when unauthenticated, redirect straight to the IdP and preserve
  // the originally requested route so the callback can return here.
  useEffect(() => {
    if (!isAuth && oidc && !redirectingRef.current) {
      redirectingRef.current = true;
      void startSignin(location.pathname + location.search);
    }
  }, [isAuth, oidc, location.pathname, location.search]);

  if (!isAuth) {
    // OIDC enabled -> the effect above is performing a full-page redirect to the
    // IdP; render nothing meanwhile (no in-app /login needed in prod).
    if (oidc) return null;
    // Dev / OIDC-disabled fallback: the in-app login page seeds a dev session.
    return <Navigate to={routes.login} state={{ from: location }} replace />;
  }
  return <Outlet />;
}
