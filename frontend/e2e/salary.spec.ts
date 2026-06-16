import { test, expect } from './support/fixtures';
import { loginAs, seedSalaryPermittedSession, waitForE2EHandle } from './support/auth';

/**
 * Salary masking — security-critical invariant.
 *
 * Asserts the REAL `SalaryValue` component's contract via its stable
 * `data-state` attribute on the E2E harness route (`/__e2e/salary`):
 *   - non-permitted session  → `data-state="permission-required"`, the numeric
 *     value is NEVER in the DOM.
 *   - salary-permitted session → `data-state="visible"`, the formatted figure
 *     renders.
 *
 * The harness mounts the actual production component (no re-implementation), so
 * a regression in masking would fail here. Sessions are seeded through the
 * dev/mock-only `window.__E2E__` handle (dead-code-eliminated in prod).
 */
test.describe('salary masking', () => {
  test('a non-permitted role sees salary values MASKED (no figure in the DOM)', async ({
    page,
  }) => {
    // Plain dev role — none carry SALARY_VIEW.
    await loginAs(page, 'consultant');
    await page.goto('/__e2e/salary');

    const value = page.getByTestId('e2e-salary-value').locator('[data-state]');
    await expect(value).toHaveAttribute('data-state', 'permission-required');
    // The raw figure must never leak into the rendered DOM.
    await expect(page.getByTestId('e2e-salary-harness')).not.toContainText('5');
    await expect(page.getByTestId('e2e-salary-harness')).not.toContainText('5 000 000');
  });

  test('a salary-permitted role SEES the salary value', async ({ page }) => {
    await seedSalaryPermittedSession(page, 'consultant');
    await page.goto('/__e2e/salary');
    await waitForE2EHandle(page);

    const value = page.getByTestId('e2e-salary-value').locator('[data-state]');
    await expect(value).toHaveAttribute('data-state', 'visible');
    await expect(value).toContainText('UZS');
  });
});
