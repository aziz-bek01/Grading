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
