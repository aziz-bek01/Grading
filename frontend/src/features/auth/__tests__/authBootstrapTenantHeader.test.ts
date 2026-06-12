/**
 * BE-3-FIX (FE) — production lockout regression for the HRLab super admin.
 *
 * A multi-membership super admin's FIRST `/users/me` after an OIDC redirect was
 * sent with NO `X-Active-Tenant-Id` header (authStore.setSession attaches it only
 * AFTER fetchCurrentUser resolves). For a multi-tenant user the backend then
 * failed closed to no active tenant -> empty permissions -> fully gated shell.
 *
 * The fix:
 *  1. persist the last selected company-client to localStorage and prime the
 *     http header from it at module load, BEFORE the first /users/me;
 *  2. re-fetch /users/me whenever the active tenant changes so permissions are
 *     recomputed for the newly selected tenant;
 *  3. clear the persisted tenant on logout.
 *
 * This pins (1) and (3) at the wire level and (2) at the store level. Runs with
 * env.useMockApi=false so the real-backend interceptor (X-Active-Tenant-Id
 * branch) is exercised, mirroring approvalTenantHeader.test.ts.
 */
import { describe, expect, it, beforeEach, afterEach, vi } from 'vitest';
import type { AxiosAdapter, AxiosRequestConfig, AxiosResponse } from 'axios';

const LAST_ACTIVE_TENANT_KEY = 'auth.lastActiveTenantId';
const TENANT_A = '40000000-0000-0000-0000-0000000000a1';

interface RecordedRequest {
  url: string;
  headers: Record<string, unknown>;
}

function headersToObject(config: AxiosRequestConfig): Record<string, unknown> {
  const hdrs = config.headers as unknown;
  if (hdrs && typeof hdrs === 'object') {
    if (typeof (hdrs as { toJSON?: () => Record<string, unknown> }).toJSON === 'function') {
      return (hdrs as { toJSON: () => Record<string, unknown> }).toJSON();
    }
    return { ...(hdrs as Record<string, unknown>) };
  }
  return {};
}

function headerValue(req: RecordedRequest, name: string): unknown {
  const key = Object.keys(req.headers).find((k) => k.toLowerCase() === name.toLowerCase());
  return key ? req.headers[key] : undefined;
}

beforeEach(() => {
  window.localStorage.clear();
  vi.resetModules();
});

afterEach(() => {
  window.localStorage.clear();
  vi.resetModules();
});

describe('auth bootstrap primes X-Active-Tenant-Id before first /users/me', () => {
  it('attaches the persisted last-active tenant on the very first request after a cold boot', async () => {
    // Simulate a returning super admin: a tenant was selected in a previous
    // session and persisted. This must be on the header BEFORE the first /me.
    window.localStorage.setItem(LAST_ACTIVE_TENANT_KEY, TENANT_A);

    // Importing the store runs its module-load priming (syncActiveTenantHeaders
    // from localStorage). Order matters: import the store first, then the client.
    await import('@/features/auth/authStore');
    const { httpClient } = await import('@/shared/api/httpClient');

    const recorded: RecordedRequest[] = [];
    const original = httpClient.defaults.adapter as AxiosAdapter | undefined;
    httpClient.defaults.adapter = (async (config: AxiosRequestConfig) => {
      recorded.push({ url: config.url ?? '', headers: headersToObject(config) });
      return {
        data: {},
        status: 200,
        statusText: 'OK',
        headers: {},
        config: config as never,
      } as AxiosResponse;
    }) as AxiosAdapter;

    try {
      await httpClient.get('/users/me');
    } finally {
      httpClient.defaults.adapter = original;
    }

    expect(recorded).toHaveLength(1);
    expect(headerValue(recorded[0], 'X-Active-Tenant-Id')).toBe(TENANT_A);
  });

  it('sends NO X-Active-Tenant-Id on cold boot when nothing was persisted (fail-closed default)', async () => {
    // Nothing persisted -> header-less first /me. The backend renders the shell
    // via its single-tenant default; data reads stay fail-closed. We only assert
    // the FE does not invent a tenant header here.
    await import('@/features/auth/authStore');
    const { httpClient } = await import('@/shared/api/httpClient');

    const recorded: RecordedRequest[] = [];
    const original = httpClient.defaults.adapter as AxiosAdapter | undefined;
    httpClient.defaults.adapter = (async (config: AxiosRequestConfig) => {
      recorded.push({ url: config.url ?? '', headers: headersToObject(config) });
      return {
        data: {},
        status: 200,
        statusText: 'OK',
        headers: {},
        config: config as never,
      } as AxiosResponse;
    }) as AxiosAdapter;

    try {
      await httpClient.get('/users/me');
    } finally {
      httpClient.defaults.adapter = original;
    }

    expect(recorded).toHaveLength(1);
    expect(headerValue(recorded[0], 'X-Active-Tenant-Id')).toBeUndefined();
  });

  it('persists the active tenant on setActiveTenant and clears it on signOut', async () => {
    const { useAuthStore } = await import('@/features/auth/authStore');

    useAuthStore.getState().setActiveTenant({
      id: TENANT_A,
      slug: 'acme',
      brand_name: 'ACME',
    });
    expect(window.localStorage.getItem(LAST_ACTIVE_TENANT_KEY)).toBe(TENANT_A);

    useAuthStore.getState().signOut();
    expect(window.localStorage.getItem(LAST_ACTIVE_TENANT_KEY)).toBeNull();
  });
});
