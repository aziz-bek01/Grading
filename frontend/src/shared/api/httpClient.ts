import axios, {
  type AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios';
import { env } from '@/shared/config/env';
import { tokenStorage } from '@/shared/auth/tokenStorage';
import { useAuthStore } from '@/features/auth/authStore';
import { isOidcAvailable, trySilentRenew } from '@/shared/auth/oidcClient';
import { ApiError } from './apiError';
import type { ErrorEnvelope } from '@/shared/types/common';

type UnauthorizedHandler = () => void;

let onUnauthorized: UnauthorizedHandler | null = null;

export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  onUnauthorized = handler;
}

let currentLocale = env.defaultLocale;
export function setHttpLocale(locale: string): void {
  currentLocale = locale;
}

/**
 * Central HTTP client.
 *  - Authorization Bearer attached automatically (in-memory token only).
 *  - X-Correlation-Id added per request (UUID); included in error rendering.
 *  - Accept-Language follows i18next.
 *  - NEVER logs tokens or response bodies (only correlation id + status on errors).
 *  - 401 -> onUnauthorized handler (router redirect to /login).
 *  - 403 / 404 / 400 / 422 -> typed ApiError; callers branch.
 */
export const httpClient: AxiosInstance = axios.create({
  baseURL: env.apiBaseUrl,
  withCredentials: false,
  timeout: 30000,
});

// Wire in the dev mock adapter when VITE_USE_MSW=true so the UI is fully
// demoable without a running backend. The mock falls through to the real
// adapter for any path it does not match.
//
// `__ENABLE_MSW__` is a Vite compile-time `define` (see vite.config.ts).
// In production builds (default) it collapses to `false`, allowing esbuild
// to dead-code-eliminate this whole block AND the dynamic `import()` below,
// so `mocks/handlers.ts` (2977+ lines of fixtures) is NEVER emitted in
// the production chunk graph.
//
// Top-level await deliberately blocks module initialisation until the mock
// adapter is wired in. This avoids a race where consumers (TanStack Query
// hooks, tests) fire requests before the mock is registered.
if (__ENABLE_MSW__ && env.useMockApi) {
  // eslint-disable-next-line no-console
  console.info('[api] mock adapter enabled (VITE_USE_MSW=true)');
  const { createMockAdapter } = await import('./mocks/handlers');
  httpClient.defaults.adapter = createMockAdapter(httpClient.defaults.adapter as never);
}

/**
 * MOCK-ONLY: holder for the active tenant id used by the in-process mock
 * adapter to simulate JWT-derived tenancy. Set by `AuthProvider` in dev
 * mode; the value is attached as `X-Mock-Tenant-Id` on every request.
 *
 * The real backend NEVER reads this header — it always uses the JWT.
 * Therefore it is safe to leave the interceptor wired in all environments.
 */
let mockActiveTenantId: string | null = null;
export function setMockActiveTenantId(tenantId: string | null): void {
  // No-op in production builds: minifier sees `if (false) { ... }` and
  // strips the assignment, leaving an empty function body that further
  // dead-code analysis can inline-eliminate at call sites.
  if (__ENABLE_MSW__) {
    mockActiveTenantId = tenantId;
  }
}

/**
 * REAL-BACKEND: holder for the active tenant id, mirrored from the auth store
 * (see AuthProvider) so the axios interceptor can read it WITHOUT a React hook.
 *
 * The HRLab Super Admin can belong to several company-clients and switches the
 * active one via the TenantSelector. The backend reads `X-Active-Tenant-Id` and
 * validates it against the user's memberships, then scopes data + permissions to
 * that company. This is an auth-context header (like a JWT claim), NOT a
 * business `tenant_id` field — clients still never send tenant_id in path/query/
 * body (see noTenantIdLeak.test.ts, which explicitly allows this header).
 */
// COLD-BOOT priming (BE-3-FIX FE): seed from the persisted last-active tenant so
// the FIRST `/users/me` after an OIDC redirect already carries
// X-Active-Tenant-Id. Without this, a multi-membership super admin's first /me
// went out header-less, the backend failed closed to no active tenant, and the
// whole shell locked (empty permissions). Read directly from localStorage here —
// NOT via the auth store — because the store imports this module, and a
// cross-module call during init would hit a temporal-dead-zone on this very
// binding. A stale / non-member value is harmless: the backend validates the
// header against the user's memberships and ignores a non-match (no cross-tenant
// escalation), and /users/me still renders the shell via its single-tenant default.
const LAST_ACTIVE_TENANT_KEY = 'auth.lastActiveTenantId';
function readPersistedActiveTenantId(): string | null {
  if (typeof window === 'undefined') return null;
  try {
    return window.localStorage.getItem(LAST_ACTIVE_TENANT_KEY);
  } catch {
    return null;
  }
}

let activeTenantId: string | null = readPersistedActiveTenantId();
export function setActiveTenantHeader(tenantId: string | null): void {
  activeTenantId = tenantId;
}

httpClient.interceptors.request.use((req: InternalAxiosRequestConfig) => {
  // Dev mode against the REAL backend authenticates via X-Dev-* headers
  // (see below), NOT a Bearer token. The local `dev-token-*` placeholder is
  // not a real JWT, so sending it as `Authorization: Bearer` makes Spring's
  // resource-server filter reject the request with 401 before DevAuthFilter
  // runs. Only attach Bearer when NOT in dev-auth mode (prod OIDC / mock).
  const devAuthMode = !env.useMockApi && env.devAuthEnabled;
  const token = tokenStorage.get();
  if (token && !devAuthMode) {
    req.headers.set('Authorization', `Bearer ${token.value}`);
  }
  if (!req.headers.get('X-Correlation-Id')) {
    req.headers.set('X-Correlation-Id', generateCorrelationId());
  }
  req.headers.set('Accept-Language', currentLocale);
  // Mock-only: lets the in-process mock adapter simulate JWT-derived tenancy.
  // The real backend ignores this header. Guarded by `__ENABLE_MSW__` so the
  // entire branch is dead-code-eliminated in production builds.
  if (__ENABLE_MSW__ && env.useMockApi && mockActiveTenantId) {
    req.headers.set('X-Mock-Tenant-Id', mockActiveTenantId);
  }
  // Real backend: forward the active company-client so the server can scope
  // data + permissions to the tenant the user selected in the TenantSelector.
  // Auth-context header (validated against memberships server-side), NOT a
  // business tenant_id field. Sent in all non-mock environments whenever an
  // active tenant is set; the mock adapter uses X-Mock-Tenant-Id instead.
  if (!env.useMockApi && activeTenantId) {
    req.headers.set('X-Active-Tenant-Id', activeTenantId);
  }
  // Dev-only: when running against the REAL backend (MSW off), forward the
  // dev identity so DevAuthFilter can resolve roles/permissions from the DB.
  // These are auth-context headers (NOT a tenant_id business field): the
  // backend derives the active tenant from X-Dev-Tenant exactly like a JWT
  // claim. Production builds never hit this branch (env.devAuthEnabled is
  // false in prod). No permission/role header is sent — the DB is the source.
  if (devAuthMode) {
    const { user, activeTenant } = useAuthStore.getState();
    if (user) req.headers.set('X-Dev-User', user.id);
    if (activeTenant) req.headers.set('X-Dev-Tenant', activeTenant.id);
  }
  return req;
});

/** Marks a request config that already went through one silent-renew retry. */
type RetryableConfig = InternalAxiosRequestConfig & { _silentRenewRetried?: boolean };

httpClient.interceptors.response.use(
  (res) => res,
  async (error: AxiosError<ErrorEnvelope>) => {
    if (error.response) {
      const { status, data } = error.response;

      // 401 handling:
      //  - OIDC mode: try a SINGLE silent renew (in-memory refresh token), and
      //    if it yields a fresh token, replay the original request once.
      //  - On renewal failure (or non-OIDC mode): clear token + redirect.
      // Never log the token at any point.
      if (status === 401) {
        const original = error.config as RetryableConfig | undefined;
        if (isOidcAvailable() && original && !original._silentRenewRetried) {
          original._silentRenewRetried = true;
          const newToken = await trySilentRenew();
          if (newToken) {
            tokenStorage.set({ value: newToken, expiresAt: Date.now() + 5 * 60 * 1000 });
            original.headers.set('Authorization', `Bearer ${newToken}`);
            return httpClient.request(original as AxiosRequestConfig);
          }
        }
        tokenStorage.clear();
        if (onUnauthorized) onUnauthorized();
      }

      if (env.isDev) {
        // Dev-only diagnostic — never logs body / token.
        // eslint-disable-next-line no-console
        console.warn(
          `[api] ${status} ${error.config?.method?.toUpperCase()} ${error.config?.url} (corrId=${data?.correlation_id ?? 'n/a'})`,
        );
      }

      throw new ApiError(status, {
        code: data?.code,
        message: data?.message ?? error.message,
        correlation_id: data?.correlation_id,
        traceId: data?.traceId,
        fieldErrors: (data as ErrorEnvelope | undefined)?.fieldErrors,
      });
    }

    // Network / no response
    throw new ApiError(0, {
      code: 'NETWORK_ERROR',
      message: error.message,
    });
  },
);

function generateCorrelationId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return `cid-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}
