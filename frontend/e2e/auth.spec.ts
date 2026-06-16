import { test, expect } from './support/fixtures';
import { loginAs } from './support/auth';

/**
 * Auth critical path.
 *
 * The app uses dev-auth role buttons in the harness (no credential form), so
 * "invalid login" is expressed as the security-equivalent guard invariant:
 * an UNauthenticated visit to a protected route must NOT render the app shell
 * and must bounce to /login. The positive case asserts a valid login lands on
 * the dashboard with the shell mounted.
 */
test.describe('auth', () => {
  test('valid login lands on the dashboard', async ({ page }) => {
    await loginAs(page, 'consultant');

    await expect(page).toHaveURL(/\/app\/dashboard$/);
    // Dashboard heading is the app's single <h1> on this route.
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(page.getByTestId('app-sidebar')).toBeVisible();
  });

  test('unauthenticated access to a protected route is blocked and does NOT render the app shell', async ({
    page,
  }) => {
    // Directly hit a protected route with no session.
    await page.goto('/app/dashboard');

    // RequireAuth redirects to /login; the dev-auth login surface renders and
    // the app shell (sidebar) must NOT be present.
    await page.waitForURL('**/login');
    await expect(page.getByTestId('login-dev-auth')).toBeVisible();
    await expect(page.getByTestId('app-sidebar')).toHaveCount(0);
  });
});
