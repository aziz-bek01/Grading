import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/features/auth/authStore';
import { routes } from '@/shared/config/routes';

export function RequireAuth() {
  const isAuth = useAuthStore((s) => s.isAuthenticated);
  const location = useLocation();
  if (!isAuth) {
    return <Navigate to={routes.login} state={{ from: location }} replace />;
  }
  return <Outlet />;
}
