import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright E2E configuration for grading.hrlab.uz (frontend only).
 *
 * TWO MODES, switched by the `E2E_BASE_URL` env var:
 *
 *  1. MOCKED / STANDALONE (default — `E2E_BASE_URL` unset)
 *     - `webServer` below BUILDS the app with the in-process mock adapter
 *       (`VITE_USE_MSW=true`) + dev-auth (`VITE_DEV_AUTH=true`) enabled, then
 *       serves it with `vite preview`. The app self-serves all `/api/v1/*`
 *       data via its own axios mock adapter, so NO backend is required.
 *     - Specs additionally install Playwright route handlers (see
 *       e2e/support/mockApi.ts) as a network safety-net: any request to the
 *       API base path that escapes to the network is intercepted and answered
 *       locally, guaranteeing a real backend is never contacted.
 *     - `baseURL` defaults to the local preview origin.
 *
 *  2. FULL-STACK (`E2E_BASE_URL` set, e.g. an ephemeral CI stack)
 *     - `webServer` is NOT started (we target the provided URL).
 *     - Route mocks are NOT installed (the helper is gated on E2E_BASE_URL
 *       being unset), so specs exercise the real API end-to-end.
 *     - `baseURL` = `E2E_BASE_URL`.
 *
 * FIRST RUN / CI: install the browser first with
 *   npx playwright install --with-deps chromium
 */

const E2E_BASE_URL = process.env.E2E_BASE_URL;
const PREVIEW_PORT = Number(process.env.E2E_PREVIEW_PORT ?? 4173);
const PREVIEW_URL = `http://localhost:${PREVIEW_PORT}`;
const baseURL = E2E_BASE_URL ?? PREVIEW_URL;

export default defineConfig({
  testDir: './e2e',
  // Specs are independent and side-effect-free, so run them in parallel.
  fullyParallel: true,
  // Fail the CI build if a `test.only` is committed by accident.
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 1 : undefined,
  timeout: 30_000,
  expect: { timeout: 10_000 },
  // `list` for readable CI logs + `html` for an artifact to open on failure.
  reporter: process.env.CI
    ? [['list'], ['html', { open: 'never' }]]
    : [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL,
    headless: true,
    // Diagnostics only on the first retry to keep green runs fast/cheap.
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'off',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  // Only spin up a local preview server in MOCKED mode. When E2E_BASE_URL is
  // set we target that deployment directly and skip building/serving here.
  webServer: E2E_BASE_URL
    ? undefined
    : {
        // Build with the mock adapter + dev-auth, then serve the static bundle.
        // Using the production build (not `vite dev`) mirrors what ships and
        // keeps the run fast + deterministic.
        command: `npm run build && npm run preview -- --port ${PREVIEW_PORT} --strictPort`,
        url: PREVIEW_URL,
        timeout: 180_000,
        reuseExistingServer: !process.env.CI,
        env: {
          // Enable the in-process mock adapter + dev-auth for the harness build.
          VITE_USE_MSW: 'true',
          VITE_DEV_AUTH: 'true',
          VITE_DEFAULT_LOCALE: 'ru-RU',
        },
      },
});
