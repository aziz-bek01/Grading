import { create } from 'zustand';
import type {
  AccessToken,
  CurrentUser,
  ProjectSummary,
  TenantSummary,
} from '@/shared/auth/authTypes';
import { tokenStorage } from '@/shared/auth/tokenStorage';
import {
  isOidcAvailable,
  startSignoutRedirect,
} from '@/shared/auth/oidcClient';
import { fetchCurrentUser } from './authApi';
import { mapMeResponse } from './mapMeResponse';

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
    set({
      user,
      isAuthenticated: true,
      // default active tenant is first one user belongs to
      activeTenant: user.tenants[0] ?? null,
      activeProject: null,
    });
  },
  refreshUser: async () => {
    const token = tokenStorage.get();
    if (!token) return;
    const me = await fetchCurrentUser(token.value);
    const user = mapMeResponse(me);
    set((state) => ({
      // Refresh the user (and therefore user.tenants) but preserve the current
      // active tenant/project selection — refreshing the catalog must not yank
      // the user out of their current workspace.
      user,
      isAuthenticated: state.isAuthenticated,
      activeTenant: state.activeTenant,
      activeProject: state.activeProject,
    }));
  },
  setActiveTenant: (tenant) => set({ activeTenant: tenant, activeProject: null }),
  setActiveProject: (project) => set({ activeProject: project }),
  setSidebarCollapsed: (v) => {
    persistSidebarCollapsed(v);
    set({ sidebarCollapsed: v });
  },
  clearSession: () => {
    tokenStorage.clear();
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
