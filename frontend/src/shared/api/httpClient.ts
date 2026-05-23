import axios, {
  type AxiosError,
  type AxiosInstance,
  type InternalAxiosRequestConfig,
} from 'axios';
import { env } from '@/shared/config/env';
import { tokenStorage } from '@/shared/auth/tokenStorage';
import { ApiError } from './apiError';
import { createMockAdapter } from './mocks/handlers';
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
if (env.useMockApi) {
  // eslint-disable-next-line no-console
  console.info('[api] mock adapter enabled (VITE_USE_MSW=true)');
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
  mockActiveTenantId = tenantId;
}

httpClient.interceptors.request.use((req: InternalAxiosRequestConfig) => {
  const token = tokenStorage.get();
  if (token) {
    req.headers.set('Authorization', `Bearer ${token.value}`);
  }
  if (!req.headers.get('X-Correlation-Id')) {
    req.headers.set('X-Correlation-Id', generateCorrelationId());
  }
  req.headers.set('Accept-Language', currentLocale);
  // Mock-only: lets the in-process mock adapter simulate JWT-derived tenancy.
  // The real backend ignores this header.
  if (env.useMockApi && mockActiveTenantId) {
    req.headers.set('X-Mock-Tenant-Id', mockActiveTenantId);
  }
  return req;
});

httpClient.interceptors.response.use(
  (res) => res,
  (error: AxiosError<ErrorEnvelope>) => {
    if (error.response) {
      const { status, data } = error.response;

      // 401: trigger logout/redirect, never log the token
      if (status === 401) {
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
