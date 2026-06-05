import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  __resetRuntimeConfigForTests,
  getOidcConfig,
  isOidcEnabled,
  loadRuntimeConfig,
} from '@/shared/config/runtimeConfig';

const VALID_OIDC = {
  issuer: 'https://auth.hrlab.uz',
  clientId: '376051607294509061',
  redirectUri: 'https://grading.hrlab.uz/auth/callback',
  postLogoutRedirectUri: 'https://grading.hrlab.uz',
  scopes: 'openid profile email offline_access',
};

function mockFetchOk(body: unknown) {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(body),
    } as Response),
  );
}

describe('runtimeConfig', () => {
  afterEach(() => {
    __resetRuntimeConfigForTests();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('parses a full config with a valid oidc block', async () => {
    mockFetchOk({ apiBaseUrl: '/api', environment: 'production', oidc: VALID_OIDC });
    const cfg = await loadRuntimeConfig();
    expect(cfg.apiBaseUrl).toBe('/api');
    expect(cfg.environment).toBe('production');
    expect(getOidcConfig()).toEqual(VALID_OIDC);
    expect(isOidcEnabled()).toBe(true);
  });

  it('disables OIDC when the oidc block is absent', async () => {
    mockFetchOk({ apiBaseUrl: '/api', environment: 'production' });
    await loadRuntimeConfig();
    expect(getOidcConfig()).toBeNull();
    expect(isOidcEnabled()).toBe(false);
  });

  it('disables OIDC when the oidc block is incomplete', async () => {
    mockFetchOk({ oidc: { issuer: 'https://auth.hrlab.uz' } });
    await loadRuntimeConfig();
    expect(getOidcConfig()).toBeNull();
    expect(isOidcEnabled()).toBe(false);
  });

  it('falls back to OIDC-disabled when /config.json is unreachable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('network')));
    const cfg = await loadRuntimeConfig();
    expect(cfg.oidc).toBeNull();
    expect(isOidcEnabled()).toBe(false);
  });

  it('falls back to OIDC-disabled on non-ok response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false } as Response));
    const cfg = await loadRuntimeConfig();
    expect(cfg.oidc).toBeNull();
  });
});
