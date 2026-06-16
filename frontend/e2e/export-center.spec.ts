import { test, expect } from './support/fixtures';
import { loginAs } from './support/auth';

/**
 * Core happy path — Export Center renders its primary content.
 *
 * A super-admin (holds EXPORT_READ) opens the project-scoped Export Center and
 * sees the page shell + the "new export" action. The list may be empty (mocked
 * mode returns no jobs) — we assert the always-present primary content, not
 * specific rows, so the spec stays resilient in both mocked and full-stack mode.
 */
test.describe('export center', () => {
  test('renders its primary content for an authorised role', async ({ page }) => {
    await loginAs(page, 'super-admin');

    // Project-scoped route; the page reads :projectId from the URL and renders
    // its shell immediately (list loads independently).
    await page.goto('/app/projects/e2e-project/exports');

    await expect(page.getByTestId('export-center-page')).toBeVisible();
    await expect(page.getByTestId('export-new-button')).toBeVisible();
    // Page-level heading is present (single primary heading of the view).
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  });
});
