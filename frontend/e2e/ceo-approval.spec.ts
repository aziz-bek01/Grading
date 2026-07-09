import { test, expect } from './support/fixtures';
import { loginAs } from './support/auth';

/**
 * QA-055 — CEO approval journey (Playwright E2E).
 *
 * Highest-value CEO flow: sign in → open the CEO panel overview → decide on a
 * panel awaiting sign-off (approve / reject). Runs against the MOCKED
 * standalone harness (`VITE_USE_MSW=true`, the default mode — see
 * `playwright.config.ts`), using the SAME in-process mock adapter
 * (`src/shared/api/mocks/{fixtures,handlers}.ts`) the frontend unit tests
 * exercise (e.g. `CeoPanelsPage.test.tsx`), so fixtures stay consistent across
 * the unit + e2e layers — no forked mock data, no new harness.
 *
 * FIXTURE NOTE: the seeded mock dataset carries exactly ONE panel with an open
 * ("Awaiting sign-off") CEO approval — `panel-cfo-averaged` /
 * `appr-panel-cfo-1` (see `src/shared/api/mocks/fixtures.ts`). Each Playwright
 * `test()` below gets its own fresh page (a full load re-instantiates the
 * app's in-memory mock store from that same static fixture set), so "approve
 * one panel" and "reject another [decision]" are exercised as two
 * independent, isolated CEO journeys against that seeded row rather than two
 * simultaneously-visible rows. This intentionally avoids forking/duplicating
 * the shared mock fixtures — which are also consumed by the frontend unit
 * suite — just for this e2e spec; QA-053's regression fixtures were added
 * locally to `CeoPanelsPage.test.tsx` for the same reason (no shared-fixture
 * edit), and there is no `window.__E2E__` data-seeding hook (only session
 * seeding — see `e2e/support/auth.ts`) that this spec could otherwise use
 * without inventing a new harness.
 *
 * REUSE: `loginAs` (dev-auth login, `e2e/support/auth.ts`) + the
 * auto-installed API network safety-net (`e2e/support/fixtures.ts` →
 * `mockApi.ts`). Every selector is a stable `data-testid` / accessible role
 * already exposed by `CeoPanelsPage` / `CeoInlineSignOffCell` and already
 * asserted by `src/features/ceo/__tests__/CeoPanelsPage.test.tsx` — no new
 * test-only DOM hooks added.
 */
test.describe('CEO approval journey', () => {
  test('CEO signs in, opens the panel overview, and approves the panel awaiting sign-off', async ({
    page,
  }) => {
    await loginAs(page, 'super-admin'); // dev role carries EVALUATION_PANEL_APPROVE.

    // Open the CEO panel overview via the REAL sidebar nav link (proves the
    // gated nav entry is reachable for a permitted role) rather than a raw
    // page.goto — this is the actual journey a CEO user would take.
    await page.getByTestId('app-sidebar').locator('a[href="/app/ceo/panels"]').click();
    await page.waitForURL('**/app/ceo/panels');
    await expect(page.getByTestId('ceo-panels-page')).toBeVisible();

    // Default tab is "Awaiting sign-off" — the seeded AVERAGED panel with an
    // open CEO approval renders the actions dropdown immediately.
    const trigger = page.getByTestId('ceo-actions-menu-trigger');
    await expect(trigger).toBeVisible();
    await trigger.click();
    await expect(page.getByTestId('ceo-actions-menu')).toBeVisible();

    await page.getByTestId('ceo-inline-approve').click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    // ConfirmDialog button order: Cancel[0], Confirm[1] (same convention
    // asserted in CeoPanelsPage.test.tsx).
    await dialog.locator('button').nth(1).click();
    await expect(dialog).toBeHidden();

    // RESULTING STATE: the now-approved row no longer awaits sign-off — both
    // the dropdown trigger and the "Open approval" link are gone from THIS
    // (pending) tab.
    await expect(page.getByTestId('ceo-actions-menu-trigger')).toHaveCount(0);
    await expect(
      page.getByTestId('ceo-open-approval-panel-cfo-averaged'),
    ).toHaveCount(0);

    // The panel itself is still reachable — now via the plain "Open"
    // panel-detail link under "All" (its approval is resolved, so it is no
    // longer treated as awaiting the CEO). Uses the stable `ceo-filter-all`
    // testid (not a localized tab label) — the harness's UI locale is
    // browser-driven and not guaranteed to be ru-RU here.
    await page.getByTestId('ceo-filter-all').click();
    await expect(
      page.getByTestId('ceo-open-panel-panel-cfo-averaged'),
    ).toBeVisible();
  });

  test('CEO signs in, opens the panel overview, and rejects the panel awaiting sign-off', async ({
    page,
  }) => {
    await loginAs(page, 'super-admin');

    await page.getByTestId('app-sidebar').locator('a[href="/app/ceo/panels"]').click();
    await page.waitForURL('**/app/ceo/panels');
    await expect(page.getByTestId('ceo-panels-page')).toBeVisible();

    const trigger = page.getByTestId('ceo-actions-menu-trigger');
    await expect(trigger).toBeVisible();
    await trigger.click();
    await expect(page.getByTestId('ceo-actions-menu')).toBeVisible();

    await page.getByTestId('ceo-inline-reject').click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();

    // ReasonRequiredDialog: Confirm stays disabled below the 20-char minimum
    // reason length (the destructive panel-reject copy — QA-005).
    const confirmBtn = dialog.locator('button').nth(1);
    await expect(confirmBtn).toBeDisabled();
    await dialog
      .locator('textarea')
      .fill('Rejecting via the E2E CEO approval journey spec — reason is long enough.');
    await expect(confirmBtn).toBeEnabled();
    await confirmBtn.click();
    await expect(dialog).toBeHidden();

    // RESULTING STATE: rejected → no longer awaiting sign-off on this tab.
    await expect(page.getByTestId('ceo-actions-menu-trigger')).toHaveCount(0);
    await expect(
      page.getByTestId('ceo-open-approval-panel-cfo-averaged'),
    ).toHaveCount(0);

    // Still reachable via the plain "Open" link under "All" (stable testid —
    // see the locale note above).
    await page.getByTestId('ceo-filter-all').click();
    await expect(
      page.getByTestId('ceo-open-panel-panel-cfo-averaged'),
    ).toBeVisible();
  });
});
