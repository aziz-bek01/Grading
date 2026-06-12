/**
 * FE-2 (2026-06-12) — every approval request (including the my-inbox poll and
 * the sidebar badge call, which share fetchMyInbox) must carry the
 * `X-Active-Tenant-Id` auth-context header when an active company-client is set.
 *
 * The header is what the backend validates against the user's memberships to
 * scope data + permissions. A poll fired WITHOUT it reached a backend that, for
 * a multi-membership user, silently defaulted to a tenant and returned its rows
 * (the cross-tenant inbox leak). FE-1 gates the poll on an active tenant and
 * mirrors the header synchronously in the auth store; this test pins the wire
 * contract: when a tenant is active, the inbox/badge call sends the header.
 *
 * Mirrors the recording-adapter pattern from noTenantIdLeak.test.ts. Tests run
 * with env.useMockApi=false (VITE_USE_MSW unset), so the real-backend interceptor
 * branch — the one that attaches X-Active-Tenant-Id — is exercised.
 */
import { describe, expect, it, beforeAll, afterAll, afterEach } from 'vitest';
import type { AxiosAdapter, AxiosRequestConfig, AxiosResponse } from 'axios';
import { httpClient, setActiveTenantHeader } from '@/shared/api/httpClient';

interface RecordedRequest {
  url: string;
  headers: Record<string, unknown>;
}

const recorded: RecordedRequest[] = [];
let originalAdapter: AxiosAdapter | undefined;

const recordingAdapter: AxiosAdapter = async (config: AxiosRequestConfig) => {
  const hdrs = config.headers as unknown;
  let headersObj: Record<string, unknown> = {};
  if (hdrs && typeof hdrs === 'object') {
    if (typeof (hdrs as { toJSON?: () => Record<string, unknown> }).toJSON === 'function') {
      headersObj = (hdrs as { toJSON: () => Record<string, unknown> }).toJSON();
    } else {
      headersObj = { ...(hdrs as Record<string, unknown>) };
    }
  }
  recorded.push({ url: config.url ?? '', headers: headersObj });
  // my-inbox returns a bare array; tolerate it.
  const res: AxiosResponse = {
    data: [],
    status: 200,
    statusText: 'OK',
    headers: {},
    config: config as never,
  } as AxiosResponse;
  return res;
};

function headerValue(req: RecordedRequest, name: string): unknown {
  const key = Object.keys(req.headers).find((k) => k.toLowerCase() === name.toLowerCase());
  return key ? req.headers[key] : undefined;
}

beforeAll(() => {
  originalAdapter = httpClient.defaults.adapter as AxiosAdapter | undefined;
  httpClient.defaults.adapter = recordingAdapter;
});

afterAll(() => {
  httpClient.defaults.adapter = originalAdapter;
  setActiveTenantHeader(null);
});

afterEach(() => {
  recorded.length = 0;
});

const ACTIVE_TENANT = '40000000-0000-0000-0000-0000000000a1';

describe('approval inbox/badge carries X-Active-Tenant-Id', () => {
  it('fetchMyInbox sends X-Active-Tenant-Id when an active tenant is set', async () => {
    setActiveTenantHeader(ACTIVE_TENANT);
    const { fetchMyInbox } = await import('@/features/approval/api/approvalApi');
    await fetchMyInbox();

    expect(recorded.length).toBe(1);
    expect(recorded[0].url).toContain('my-inbox');
    expect(headerValue(recorded[0], 'X-Active-Tenant-Id')).toBe(ACTIVE_TENANT);
  });

  it('the badge call (same fetchMyInbox) also carries X-Active-Tenant-Id', async () => {
    // The sidebar badge derives its count from the SAME fetchMyInbox result
    // (Sidebar.useApprovalInboxCount → useMyApprovalInbox → fetchMyInbox), so the
    // header guarantee is identical. A second invocation pins that.
    setActiveTenantHeader(ACTIVE_TENANT);
    const { fetchMyInbox } = await import('@/features/approval/api/approvalApi');
    await fetchMyInbox();

    expect(recorded.length).toBe(1);
    expect(headerValue(recorded[0], 'X-Active-Tenant-Id')).toBe(ACTIVE_TENANT);
  });

  it('does NOT send X-Active-Tenant-Id when no active tenant is set (fail-closed)', async () => {
    setActiveTenantHeader(null);
    const { fetchMyInbox } = await import('@/features/approval/api/approvalApi');
    await fetchMyInbox();

    expect(recorded.length).toBe(1);
    // With no active tenant the header is absent; FE-1's `enabled` gate normally
    // prevents the call from firing at all, but if it does, the backend (BE-3)
    // fails closed to zero rows rather than leaking a default tenant.
    expect(headerValue(recorded[0], 'X-Active-Tenant-Id')).toBeUndefined();
  });
});
