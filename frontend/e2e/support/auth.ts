import { expect, type Page } from '@playwright/test';

export type DevRole = 'super-admin' | 'consultant' | 'viewer';

/**
 * Log in via the in-app dev-auth buttons (default UI mode for the harness build
 * and for dev-auth-backed deployments). Lands on the dashboard.
 *
 * Selects role buttons by stable `data-testid` (added to LoginPage) rather than
 * localized labels, since the default UI locale is ru-RU.
 */
export async function loginAs(page: Page, role: DevRole = 'consultant'): Promise<void> {
  await page.goto('/login');
  const button = page.getByTestId(`login-as-${role}`);
  await expect(button).toBeVisible();
  await button.click();
  // RequireAuth → dashboard. Web-first wait (no fixed sleep).
  await page.waitForURL('**/app/dashboard');
  await expect(page.getByTestId('app-sidebar')).toBeVisible();
}

/**
 * Seed a salary-PERMITTED session directly through the E2E store handle, then
 * navigate to the app. Used by the salary-masking spec to assert the *visible*
 * branch of SalaryValue, which no canned dev role can reach (none carry
 * SALARY_VIEW). The handle is dev/mock-only and dead-code-eliminated in prod.
 */
export async function seedSalaryPermittedSession(
  page: Page,
  role: DevRole = 'consultant',
): Promise<void> {
  // The page must be loaded once so `window.__E2E__` is installed.
  await page.goto('/login');
  await waitForE2EHandle(page);
  await page.evaluate((r) => {
    window.__E2E__?.seedSalaryPermittedSession(r as DevRole);
  }, role);
  await page.goto('/app/dashboard');
  await expect(page.getByTestId('app-sidebar')).toBeVisible();
}

/** Resolve once the dev/mock-only `window.__E2E__` handle is installed. */
export async function waitForE2EHandle(page: Page): Promise<void> {
  await page.waitForFunction(() => Boolean(window.__E2E__), undefined, { timeout: 10_000 });
}

declare global {
  interface Window {
    __E2E__?: {
      seedSession: (opts?: unknown) => void;
      seedSalaryPermittedSession: (role?: string) => void;
      clearSession: () => void;
    };
  }
}
