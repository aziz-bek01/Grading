import { useAuthStore } from '@/features/auth/authStore';
import { buildDevUser } from '@/features/auth/devAuth';
import { PERMISSIONS, type PermissionCode } from '@/shared/types/permissions';
import type { CurrentUser } from '@/shared/auth/authTypes';

/**
 * E2E-ONLY test handle.
 *
 * Exposes a small, explicit surface on `window.__E2E__` so Playwright specs can
 * deterministically seed an authenticated session WITHOUT relying on a live
 * backend or the OIDC redirect flow. The dev-auth login buttons only offer the
 * three canned roles (super-admin / consultant / viewer) — none of which carry
 * salary visibility — so the salary-masking spec needs a way to mint a
 * salary-permitted session to assert the *visible* branch of `SalaryValue`.
 *
 * SECURITY / SHIPPING GUARANTEE:
 *   - Installed ONLY when `import.meta.env.DEV` (vite dev server) OR
 *     `__ENABLE_MSW__` (a compile-time `define`, true only for mock/demo
 *     builds). In a real production build both are `false`, so the entire body
 *     collapses to `if (false) { ... }` and esbuild/Rolldown dead-code-
 *     eliminates it — `window.__E2E__` never exists in the prod bundle.
 *   - It only ever sets a CLIENT-SIDE session object; the backend remains the
 *     source of truth. Granting a permission here changes only what the UI
 *     renders in the mocked harness, never what a real server authorizes.
 *   - It never logs the token or any salary value.
 */

interface E2ESeedOptions {
  role?: 'super-admin' | 'consultant' | 'viewer';
  /** Extra permission codes to merge onto the dev user (e.g. SALARY_VIEW). */
  extraPermissions?: PermissionCode[];
  /** Grant membership-level salary visibility (feeds canViewSalary()). */
  salaryDataPermission?: boolean;
}

export interface E2ETestHandle {
  /** Seed an authenticated dev session with optional permission overrides. */
  seedSession: (opts?: E2ESeedOptions) => void;
  /** Seed a session that can view salary values (SALARY_VIEW + membership flag). */
  seedSalaryPermittedSession: (role?: E2ESeedOptions['role']) => void;
  /** Clear the local session (no IdP round-trip). */
  clearSession: () => void;
}

declare global {
  interface Window {
    __E2E__?: E2ETestHandle;
  }
}

export function installE2ETestHandle(): void {
  // Build-flag guard: dead-code-eliminated in production (DEV=false,
  // __ENABLE_MSW__=false). `__ENABLE_MSW__` is a Vite `define` (vite.config.ts).
  if (!(import.meta.env.DEV || __ENABLE_MSW__)) return;
  if (typeof window === 'undefined') return;

  const buildUser = (opts?: E2ESeedOptions): CurrentUser => {
    const base = buildDevUser(opts?.role ?? 'consultant');
    const extra = opts?.extraPermissions ?? [];
    const merged = Array.from(new Set([...base.permissions, ...extra]));
    return {
      ...base,
      permissions: merged,
      salary_data_permission: opts?.salaryDataPermission ?? base.salary_data_permission,
    };
  };

  const seedSession: E2ETestHandle['seedSession'] = (opts) => {
    const user = buildUser(opts);
    useAuthStore.getState().setSession(user, {
      value: 'e2e-token',
      expiresAt: Date.now() + 60 * 60 * 1000,
    });
  };

  window.__E2E__ = {
    seedSession,
    seedSalaryPermittedSession: (role = 'consultant') =>
      seedSession({
        role,
        extraPermissions: [PERMISSIONS.SALARY_VIEW],
        salaryDataPermission: true,
      }),
    clearSession: () => useAuthStore.getState().clearSession(),
  };
}
