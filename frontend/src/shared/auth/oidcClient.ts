/**
 * OIDC client wrapper around the maintained `oidc-client-ts` UserManager.
 *
 * Hard security rules enforced here:
 *  - Authorization Code flow with PKCE (response_type=code; PKCE is automatic).
 *  - The access/id/refresh tokens live in an IN-MEMORY user store ONLY — they
 *    are NEVER written to localStorage / sessionStorage. We back the UserManager
 *    with `InMemoryWebStorage` so oidc-client-ts cannot persist tokens to disk.
 *  - Configuration (issuer, public clientId, redirect URIs, scopes) comes from
 *    runtime `/config.json` (getOidcConfig). Nothing is hardcoded.
 *  - We never log tokens or PII.
 *
 * `oidc-client-ts` is loaded via a DYNAMIC import so that modules which only
 * need the cheap helpers (isOidcAvailable / startSignoutRedirect) — e.g. the
 * auth store and route guards — do not statically pull the library (and its
 * crypto deps) into their bundle/test graph. The library is fetched lazily the
 * first time a UserManager is actually required (login / callback / renew).
 *
 * If OIDC is not provisioned for the deployment (getOidcConfig() === null),
 * every operation here is a safe no-op and callers fall back to dev / login.
 */
import type { User, UserManager } from 'oidc-client-ts';
import { getOidcConfig } from '@/shared/config/runtimeConfig';

let userManager: UserManager | null = null;
let initializedFor: string | null = null;

/** Lazily build (and cache) the UserManager from runtime OIDC config. */
async function buildUserManager(): Promise<UserManager | null> {
  const oidc = getOidcConfig();
  if (!oidc) return null;

  const identity = `${oidc.issuer}|${oidc.clientId}|${oidc.redirectUri}`;
  if (userManager && initializedFor === identity) return userManager;

  // Dynamic import keeps oidc-client-ts out of the static graph (see header).
  const { InMemoryWebStorage, UserManager: UM, WebStorageStateStore } = await import(
    'oidc-client-ts'
  );

  userManager = new UM({
    authority: oidc.issuer,
    client_id: oidc.clientId,
    redirect_uri: oidc.redirectUri,
    post_logout_redirect_uri: oidc.postLogoutRedirectUri,
    response_type: 'code', // Authorization Code + PKCE (automatic).
    scope: oidc.scopes,
    // ACCESS/ID/REFRESH TOKENS live IN MEMORY only (userStore) — never on disk.
    userStore: new WebStorageStateStore({ store: new InMemoryWebStorage() }),
    // The transient sign-in STATE (PKCE code_verifier + state) MUST survive the
    // full-page redirect to the IdP and back, so it CANNOT be in-memory (a normal
    // navigation wipes it -> "sign-in could not be completed"). sessionStorage is
    // correct here: it persists across the redirect, is single-use, scoped to the
    // tab, and cleared after the callback. It never holds the access token.
    stateStore: new WebStorageStateStore({ store: window.sessionStorage }),
    // We resolve the current user from the backend (/users/me), not the IdP
    // userinfo endpoint — keep the manager lean.
    loadUserInfo: false,
    automaticSilentRenew: false,
    monitorSession: false,
  });
  initializedFor = identity;
  return userManager;
}

/**
 * Get the cached UserManager (creating it if needed).
 * Returns null when OIDC is disabled for this deployment.
 */
export async function getUserManager(): Promise<UserManager | null> {
  return buildUserManager();
}

/** True when OIDC is provisioned. Cheap — only reads runtime config. */
export function isOidcAvailable(): boolean {
  return getOidcConfig() !== null;
}

/**
 * Begin the login redirect to the IdP.
 * `returnTo` is preserved in the OIDC `state` so the callback can navigate back
 * to the originally requested route after the round-trip.
 */
export async function startSignin(returnTo?: string): Promise<void> {
  const mgr = await getUserManager();
  if (!mgr) return;
  await mgr.signinRedirect(returnTo ? { state: { returnTo } } : undefined);
}

/**
 * Complete the redirect callback, exchanging the auth code for tokens EXACTLY
 * ONCE. The OIDC callback is always reached via a full-page redirect from the
 * IdP, so this module is fresh per real callback; the cached promise therefore
 * only dedupes a double-invocation within the same page load (React
 * StrictMode / concurrent render / remount). Without this, the second exchange
 * of the single-use auth code returns invalid_grant (400) and the sign-in
 * "could not be completed" even though the first exchange succeeded.
 */
let signinCallbackPromise: Promise<User | null> | null = null;
export function completeSigninCallback(): Promise<User | null> {
  if (!signinCallbackPromise) {
    signinCallbackPromise = (async () => {
      const mgr = await getUserManager();
      if (!mgr) return null;
      return mgr.signinRedirectCallback();
    })();
  }
  return signinCallbackPromise;
}

/** Return the in-memory access token (never persisted), or null. */
export async function getOidcAccessToken(): Promise<string | null> {
  const mgr = await getUserManager();
  if (!mgr) return null;
  const user = await mgr.getUser();
  return user?.access_token ?? null;
}

/**
 * Attempt a single silent token renewal using the in-memory refresh token.
 * Returns the new access token, or null if renewal is not possible.
 */
export async function trySilentRenew(): Promise<string | null> {
  if (!isOidcAvailable()) return null;
  const mgr = await getUserManager();
  if (!mgr) return null;
  try {
    const user = await mgr.signinSilent();
    return user?.access_token ?? null;
  } catch {
    return null;
  }
}

/**
 * End the IdP session (RP-initiated logout) and redirect to the configured
 * post-logout URI. No-op when OIDC is disabled. Never throws.
 */
export async function startSignoutRedirect(): Promise<void> {
  if (!isOidcAvailable()) return;
  const mgr = await getUserManager();
  if (!mgr) return;
  try {
    await mgr.signoutRedirect();
  } catch {
    // If end-session fails, the caller has already cleared local state.
  }
}

/** Drop the in-memory OIDC user (tokens) without an IdP round-trip. */
export async function removeOidcUser(): Promise<void> {
  if (!isOidcAvailable()) return;
  const mgr = await getUserManager();
  if (!mgr) return;
  try {
    await mgr.removeUser();
  } catch {
    /* ignore */
  }
}

/** Test-only: reset the cached manager so a new config takes effect. */
export function __resetUserManagerForTests(): void {
  userManager = null;
  initializedFor = null;
}
