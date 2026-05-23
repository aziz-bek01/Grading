import { create } from 'zustand';
import type {
  AccessToken,
  CurrentUser,
  ProjectSummary,
  TenantSummary,
} from '@/shared/auth/authTypes';
import { tokenStorage } from '@/shared/auth/tokenStorage';

interface AuthState {
  user: CurrentUser | null;
  activeTenant: TenantSummary | null;
  activeProject: ProjectSummary | null;
  isAuthenticated: boolean;
  setSession: (user: CurrentUser, token: AccessToken) => void;
  setActiveTenant: (tenant: TenantSummary | null) => void;
  setActiveProject: (project: ProjectSummary | null) => void;
  signOut: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  activeTenant: null,
  activeProject: null,
  isAuthenticated: false,
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
  signOut: () => {
    tokenStorage.clear();
    set({
      user: null,
      activeTenant: null,
      activeProject: null,
      isAuthenticated: false,
    });
  },
}));
