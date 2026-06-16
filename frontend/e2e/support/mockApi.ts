import type { Page, Route } from '@playwright/test';

/**
 * Playwright network safety-net for the MOCKED standalone mode.
 *
 * The harness build runs the app with its OWN in-process axios mock adapter
 * (`VITE_USE_MSW=true`), so the vast majority of `/api/v1/*` calls never leave
 * the page. These route handlers are a belt-and-braces guard: if ANY request
 * does escape to the network (a path the in-process adapter doesn't match, a
 * future endpoint, etc.) we answer it locally so a real backend is NEVER
 * contacted and the spec stays deterministic.
 *
 * GATING: only installed when `E2E_BASE_URL` is unset. Against a real
 * full-stack deployment we install NOTHING here, so specs hit the live API.
 */

const isFullStack = !!process.env.E2E_BASE_URL;

/** Minimal generic payloads keyed by URL suffix. Extend as smoke flows grow. */
function genericBodyFor(url: string): unknown {
  // `/users/me` — shape consumed by mapMeResponse. Kept permission-rich so a
  // safety-net response never accidentally locks the shell. (Only used if the
  // in-process mock ever misses; dev-auth seeds the store directly in specs.)
  if (url.includes('/users/me')) {
    return {
      id: 'e2e-user',
      email: 'e2e@hrlab.local',
      name: 'E2E User',
      locale: 'ru-RU',
      roles: [],
      permissions: [],
      salary_data_permission: false,
      tenants: [],
    };
  }
  // Portfolio summary counters for the dashboard.
  if (url.includes('/analytics/portfolio-summary')) {
    return {
      tenant_id: null,
      client_company_count: 0,
      project_count: 0,
      methodology_count: 0,
      last_activity_at: null,
    };
  }
  // Anything list-shaped → an empty Spring `Page` envelope is the safest default
  // (most list hooks read `.content` / `.items`); plain endpoints get `{}`.
  if (
    url.includes('/projects') ||
    url.includes('/positions') ||
    url.includes('/departments') ||
    url.includes('/exports') ||
    url.includes('/audit') ||
    url.includes('/users') ||
    url.includes('/approval-requests') ||
    url.includes('/comments')
  ) {
    return { content: [], items: [], totalElements: 0, totalPages: 0, number: 0, size: 0 };
  }
  return {};
}

async function fulfilJson(route: Route, body: unknown, status = 200): Promise<void> {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  });
}

/**
 * Install the API network safety-net. No-op against a real stack.
 * Call once per test (via the `mountApiMocks` fixture) before navigation.
 */
export async function installApiMocks(page: Page): Promise<void> {
  if (isFullStack) return;

  await page.route('**/api/v1/**', async (route) => {
    const url = route.request().url();
    const method = route.request().method();
    // Auth exchange / logout — accept and return a tiny ok.
    if (url.includes('/auth/exchange') || url.includes('/auth/logout') || url.includes('/dev-auth/login')) {
      await fulfilJson(route, { ok: true });
      return;
    }
    // Mutations we don't model → 200 with an echo-ish ok so nothing throws.
    if (method !== 'GET') {
      await fulfilJson(route, { ok: true }, 200);
      return;
    }
    await fulfilJson(route, genericBodyFor(url));
  });
}
