import type { AccessToken } from './authTypes';

/**
 * In-memory token holder.
 *
 * Architectural choice (per security blueprint §6 + design foundation §19):
 *  - access token kept in memory only,
 *  - never persisted to localStorage / sessionStorage,
 *  - refresh handled via IdP-managed httpOnly cookie (out of scope for MVP 1 dev mode),
 *  - on page refresh we re-issue the dev token via the dev-auth endpoint or send to /login.
 */
let memoryToken: AccessToken | null = null;

export const tokenStorage = {
  get(): AccessToken | null {
    return memoryToken;
  },
  set(token: AccessToken | null): void {
    memoryToken = token;
  },
  clear(): void {
    memoryToken = null;
  },
  isValid(): boolean {
    return memoryToken !== null && memoryToken.expiresAt > Date.now();
  },
};
