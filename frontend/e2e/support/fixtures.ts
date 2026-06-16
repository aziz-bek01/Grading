import { test as base, expect } from '@playwright/test';
import { installApiMocks } from './mockApi';

/**
 * Extended Playwright `test` that auto-installs the API network safety-net
 * BEFORE each test navigates. In full-stack mode (`E2E_BASE_URL` set) the
 * installer is a no-op, so the same specs run unchanged against a real stack.
 */
export const test = base.extend({
  page: async ({ page }, use) => {
    await installApiMocks(page);
    await use(page);
  },
});

export { expect };
