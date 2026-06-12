import { create } from 'zustand';
import type {
  AccessToken,
  CurrentUser,
  ProjectSummary,
  TenantSummary,
} from '@/shared/auth/authTypes';
import { tokenStorage } from '@/shared/auth/tokenStorage';
import {
  setActiveTenantHeader,
  setMockActiveTenantId,
} from '@/shared/api/httpClient';
import {
  isOidcAvailable,
  startSignoutRedirect,
} from '@/shared/auth/oidcClient';
import { fetchCurrentUser } from './authApi';
import { mapMeResponse } from './mapMeResponse';

/**
 * FE-1/FE-2 (2026-06-12) — mirror the active company-client into the http
 * client's module-scoped header holders SYNCHRONOUSLY, the moment the store
 * changes, rather than in a post-render `useEffect` (AuthProvider). This closes
 * the window where an approval query/poll could fire after `activeTenant` was
 * set but before the effect attached `X-Active-Tenant-Id`, which the backend
 * would have resolved to a defaulted tenant -> cross-tenant inbox leak.
 *
 * `setActiveTenantHeader` is the real-backend header; `setMockActiveTenantId`
 * feeds the in-process MSW adapter. Both are guarded internally so production
 * builds dead-code-eliminate the mock branch.
 */
function syncActiveTenantHeaders(tenantId: string | null): void {
  setActiveTenantHeader(tenantId);
  setMockActiveTenantId(tenantId);
}

const SIDEBAR_COLLAPSED_KEY = 'sidebar.collapsed';

/** SSR-safe localStorage read for the sidebar collapse preference. */
function readSidebarCollapsed(): boolean {
  if (typeof window === 'undefined') return false;
  try {
    return window.localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === '1';
  } catch {
    return false;
  }
}

/** SSR-safe localStorage write; failures are swallowed (private mode etc.). */
function persistSidebarCollapsed(v: boolean): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(SIDEBAR_COLLAPSED_KEY, v ? '1' : '0');
  } catch {
    /* ignore quota / disabled storage */
  }
}

interface AuthState {
  user: CurrentUser | null;
  activeTenant: TenantSummary | null;
  activeProject: ProjectSummary | null;
  isAuthenticated: boolean;
  /** Icon-only sidebar preference (persisted to localStorage). */
  sidebarCollapsed: boolean;
  setSession: (user: CurrentUser, token: AccessToken) => void;
  /**
   * Re-fetch `/users/me` with the in-memory token and refresh the user object
   * (notably `user.tenants`, which feeds the TenantSelector) WITHOUT disturbing
   * the active tenant / project. Used after creating a new company-client so it
   * appears in the switcher. No-op when there is no live session/token.
   */
  refreshUser: () => Promise<void>;
  setActiveTenant: (tenant: TenantSummary | null) => void;
  setActiveProject: (project: ProjectSummary | null) => void;
  setSidebarCollapsed: (v: boolean) => void;
  /** Local-only session clear (no IdP round-trip) — used by the 401 handler. */
  clearSession: () => void;
  signOut: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  activeTenant: null,
  activeProject: null,
  isAuthenticated: false,
  sidebarCollapsed: readSidebarCollapsed(),
  setSession: (user, token) => {
    tokenStorage.set(token);
    // default active tenant is first one user belongs to
    const activeTenant = user.tenants[0] ?? null;
    // Attach the header BEFORE React re-renders / any approval query fires.
    syncActiveTenantHeaders(activeTenant?.id ?? null);
    set({
      user,
      isAuthenticated: true,
      activeTenant,
      activeProject: null,
    });
  },
  refreshUser: async () => {
    const token = tokenStorage.get();
    if (!token) return;
    const me = await fetchCurrentUser(token.value);
    const user = mapMeResponse(me);
    set((state) => {
      // Preserve the current active tenant/project selection — refreshing the
      // catalog must not yank the user out of their current workspace. BUT if no
      // tenant is active yet (e.g. first /users/me after an OIDC redirect that
      // skipped setSession) and the user belongs to one, auto-select the first
      // so a valid X-Active-Tenant-Id is always sent and the backend can resolve
      // permissions for an actual company.
      const activeTenant = state.activeTenant ?? user.tenants[0] ?? null;
      // Keep the http header in lock-step (it may have auto-selected a tenant).
      syncActiveTenantHeaders(activeTenant?.id ?? null);
      return {
        user,
        isAuthenticated: state.isAuthenticated,
        activeTenant,
        // Clearing the project only when we just auto-selected a tenant would be
        // surprising; keep the existing project unless there is no tenant at all.
        activeProject: activeTenant ? state.activeProject : null,
      };
    });
  },
  setActiveTenant: (tenant) => {
    // Switch the http header SYNCHRONOUSLY so the very next approval query (and
    // the gated inbox poll) carries the NEW X-Active-Tenant-Id, not the old one.
    syncActiveTenantHeaders(tenant?.id ?? null);
    set({ activeTenant: tenant, activeProject: null });
  },
  setActiveProject: (project) => set({ activeProject: project }),
  setSidebarCollapsed: (v) => {
    persistSidebarCollapsed(v);
    set({ sidebarCollapsed: v });
  },
  clearSession: () => {
    tokenStorage.clear();
    // Drop the tenant header so no stale X-Active-Tenant-Id survives a logout.
    syncActiveTenantHeaders(null);
    set({
      user: null,
      activeTenant: null,
      activeProject: null,
      isAuthenticated: false,
    });
  },
  signOut: () => {
    // 1) Local clear first (synchronous contract — tests rely on immediate state).
    tokenStorage.clear();
    syncActiveTenantHeaders(null);
    set({
      user: null,
      activeTenant: null,
      activeProject: null,
      isAuthenticated: false,
    });
    // 2) OIDC mode: also end the IdP session (RP-initiated logout). Fire-and-
    //    forget — it triggers a full-page redirect to the post-logout URI.
    //    No-op when OIDC is disabled (dev / tests), so existing behavior holds.
    if (isOidcAvailable()) {
      void startSignoutRedirect();
    }
  },
}));
