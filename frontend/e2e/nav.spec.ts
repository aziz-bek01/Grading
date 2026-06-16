import { test, expect } from './support/fixtures';
import { loginAs } from './support/auth';

/**
 * Role-aware navigation.
 *
 * The sidebar renders nav items gated by the user's permission set. We assert
 * via stable hrefs (route paths) inside the `app-sidebar` landmark — not
 * localized labels — so the spec is resilient to translation changes and works
 * in the default ru-RU UI.
 *
 *   - super-admin holds TENANT_READ + USER_ACCESS_MANAGE → sees Clients + Roles.
 *   - viewer holds neither → those entries are absent.
 */
test.describe('role-aware nav', () => {
  test('an authorised role (super-admin) sees its privileged nav landmarks', async ({ page }) => {
    await loginAs(page, 'super-admin');

    const sidebar = page.getByTestId('app-sidebar');
    await expect(sidebar).toBeVisible();

    // Always-present landmark for any authenticated user.
    await expect(sidebar.locator('a[href="/app/dashboard"]')).toBeVisible();
    // Privileged entries gated by super-admin-only permissions.
    await expect(sidebar.locator('a[href="/app/clients"]')).toBeVisible();
    await expect(sidebar.locator('a[href="/app/roles"]')).toBeVisible();
    await expect(sidebar.locator('a[href="/app/audit"]')).toBeVisible();
  });

  test('a viewer role does NOT see privileged admin nav landmarks', async ({ page }) => {
    await loginAs(page, 'viewer');

    const sidebar = page.getByTestId('app-sidebar');
    await expect(sidebar).toBeVisible();

    // Dashboard is always available...
    await expect(sidebar.locator('a[href="/app/dashboard"]')).toBeVisible();
    // ...but the admin-only entries must be hidden for a viewer.
    await expect(sidebar.locator('a[href="/app/clients"]')).toHaveCount(0);
    await expect(sidebar.locator('a[href="/app/roles"]')).toHaveCount(0);
  });
});
