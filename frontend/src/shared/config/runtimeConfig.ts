/**
 * Runtime configuration loader.
 *
 * The SPA is built once and deployed everywhere; environment-specific values
 * (API base URL, OIDC issuer/clientId, etc.) are served at runtime from
 * `/config.json` (populated by a ConfigMap-mounted file in K8s / nginx — see
 * infra/frontend/config.json). NEVER bake the OIDC client id / issuer into the
 * bundle: read them here at bootstrap.
 *
 * Security note: this file only holds *public* OIDC client metadata
 * (issuer, public client id, redirect URIs, scopes). No secrets. The access
 * token NEVER lives here — see tokenStorage (in-memory only).
 *
 * Contract:
 *   - `loadRuntimeConfig()` is called once before the app renders (main.tsx).
 *   - If `/config.json` is missing/unfetchable, we fall back to a minimal
 *     config with OIDC disabled (oidc === null) so local/dev still boots.
 *   - If the `oidc` block is absent, OIDC is simply disabled.
 */

export interface OidcRuntimeConfig {
  issuer: string;
  clientId: string;
  redirectUri: string;
  postLogoutRedirectUri: string;
  /** Space-separated OIDC scopes (e.g. "openid profile email offline_access ..."). */
  scopes: string;
}

export interface RuntimeConfig {
  apiBaseUrl?: string;
  environment?: string;
  /** Present only when OIDC is provisioned for this deployment; null = disabled. */
  oidc: OidcRuntimeConfig | null;
}

const DEFAULT_CONFIG: RuntimeConfig = {
  oidc: null,
};

let runtimeConfig: RuntimeConfig = DEFAULT_CONFIG;
let loaded = false;

function isNonEmptyString(v: unknown): v is string {
  return typeof v === 'string' && v.length > 0;
}

/** Validate the raw `oidc` block; returns a typed config or null when invalid/absent. */
function parseOidc(raw: unknown): OidcRuntimeConfig | null {
  if (raw === null || typeof raw !== 'object') return null;
  const o = raw as Record<string, unknown>;
  if (
    isNonEmptyString(o.issuer) &&
    isNonEmptyString(o.clientId) &&
    isNonEmptyString(o.redirectUri) &&
    isNonEmptyString(o.postLogoutRedirectUri) &&
    isNonEmptyString(o.scopes)
  ) {
    return {
      issuer: o.issuer,
      clientId: o.clientId,
      redirectUri: o.redirectUri,
      postLogoutRedirectUri: o.postLogoutRedirectUri,
      scopes: o.scopes,
    };
  }
  return null;
}

function parseRuntimeConfig(raw: unknown): RuntimeConfig {
  if (raw === null || typeof raw !== 'object') return DEFAULT_CONFIG;
  const r = raw as Record<string, unknown>;
  return {
    apiBaseUrl: isNonEmptyString(r.apiBaseUrl) ? r.apiBaseUrl : undefined,
    environment: isNonEmptyString(r.environment) ? r.environment : undefined,
    oidc: parseOidc(r.oidc),
  };
}

/**
 * Fetch & cache `/config.json`. Safe to call multiple times — only the first
 * call performs the network read. Never throws: on any failure it logs a
 * non-sensitive warning and keeps OIDC disabled so the app still renders.
 */
export async function loadRuntimeConfig(): Promise<RuntimeConfig> {
  if (loaded) return runtimeConfig;
  try {
    const res = await fetch('/config.json', { cache: 'no-store' });
    if (res.ok) {
      const json: unknown = await res.json();
      runtimeConfig = parseRuntimeConfig(json);
    } else {
      runtimeConfig = DEFAULT_CONFIG;
    }
  } catch {
    // Network / parse failure — keep OIDC disabled, never log response body.
    runtimeConfig = DEFAULT_CONFIG;
  }
  loaded = true;
  return runtimeConfig;
}

/** Synchronously read the loaded config. Call `loadRuntimeConfig()` first. */
export function getRuntimeConfig(): RuntimeConfig {
  return runtimeConfig;
}

/** OIDC block, or null when OIDC is disabled for this deployment. */
export function getOidcConfig(): OidcRuntimeConfig | null {
  return runtimeConfig.oidc;
}

/** True when the deployment is provisioned with a valid OIDC block. */
export function isOidcEnabled(): boolean {
  return runtimeConfig.oidc !== null;
}

/** Test-only: inject a config without touching the network. */
export function __setRuntimeConfigForTests(config: RuntimeConfig): void {
  runtimeConfig = config;
  loaded = true;
}

/** Test-only: reset to the default (OIDC disabled) and force a re-load. */
export function __resetRuntimeConfigForTests(): void {
  runtimeConfig = DEFAULT_CONFIG;
  loaded = false;
}
