# grading.hrlab.uz — Frontend

React 19 + TypeScript + Vite SPA for the HR Laboratories grading SaaS platform.

This document covers the rules and procedures that apply to **every** PR. For
domain background see `docs/mvp1/`; for agent role see
`.claude/agents/frontend-engineer.md`.

---

## 1. Quick start

```bash
npm install
npm run dev          # http://localhost:5173 — uses MSW mocks when VITE_USE_MSW=true
npm run build        # type-check + production build
npm test             # vitest run
npm run lint
npm run e2e          # Playwright E2E (see §1a)
```

### 1a. E2E tests (Playwright)

```bash
# First run / CI: download the browser once.
npx playwright install --with-deps chromium

npm run e2e          # headless, mocked mode (builds + serves the app, no backend)
npm run e2e:ui       # interactive UI mode
```

Two modes, switched by `E2E_BASE_URL`:

| `E2E_BASE_URL` | Mode | Behaviour |
|----------------|------|-----------|
| **unset** (default) | Mocked / standalone | `playwright.config.ts` `webServer` builds the app with `VITE_USE_MSW=true` + `VITE_DEV_AUTH=true` and serves it via `vite preview`. The app self-serves all `/api/v1/*` data via its in-process mock adapter; specs also install a Playwright route safety-net so no real backend is ever contacted. `baseURL` = local preview. |
| **set** (e.g. ephemeral CI stack) | Full-stack | `webServer` is skipped and route mocks are NOT installed — specs run against `E2E_BASE_URL` end-to-end. |

Specs live in `e2e/` and select by stable `data-testid` / route hrefs (never localized text). Playwright artifacts (`test-results/`, `playwright-report/`, `.playwright/`) are gitignored and never in the bundle.

Environment variables (`.env.local`):

| Variable | Purpose |
|----------|---------|
| `VITE_API_BASE_URL` | Backend base URL (defaults to `/api/v1`) |
| `VITE_USE_MSW` | If `true`, the in-process mock adapter takes over matching routes |
| `VITE_DEFAULT_LOCALE` | Default UI locale (defaults to `ru-RU`) |

---

## 2. Tenant model — backend derives, frontend never sends

The backend is multi-tenant and derives the **active tenant id** from the
authenticated JWT (`tenant_id` claim). The frontend MUST NOT include
`tenant_id` / `tenantId` in any outbound request — not in URLs, query
strings, request bodies, or custom headers.

This is enforced by `src/shared/api/__tests__/noTenantIdLeak.test.ts`:
every Phase 2 API fetcher is exercised against a recording axios adapter
and asserted to produce zero tenant identifiers on the wire.

Allowed on the wire:
- `projectId` — explicit business identifier, scoped under the user's tenant
  on the backend.

The MSW mock layer mirrors backend semantics:
- Active tenant is read from a mock-only `X-Mock-Tenant-Id` header, set by
  `AuthProvider` from the active tenant in `authStore`.
- If a developer accidentally sends `body.tenant_id`, the mock logs a
  warning and IGNORES the field — same behaviour as the real backend.
- The real backend never reads this header (it always uses the JWT), so
  leaving the interceptor on in production is safe.

If you need to bust React-Query caches when the user switches tenants,
include the tenant id in the **cache key only** (see
`projectKeys.list(tenantScope)` in `features/projects/api/projectApi.ts`).

References: master plan §10 rule 13, §27 task 1, security blueprint API-13,
defects D-202 / D-217 / F-208.

---

## 3. i18n — four supported locales

We ship four locales:

- `ru-RU` (default)
- `uz-Cyrl-UZ`
- `uz-Latn-UZ`
- `en-US`

All translation keys live in `src/shared/i18n/locales/<locale>.json`.

### Adding a new key

1. Add the key to **all four** files.
2. Translate. For `uz-Cyrl-UZ` and `uz-Latn-UZ` use the same translation
   in different scripts. For `en-US` use crisp business English.
3. Run the parity test (see below).

### Parity guard

`src/shared/i18n/__tests__/i18nParity.test.ts` flattens every locale into a
key set and asserts:

1. Every locale carries the full union of keys.
2. No orphan keys (a key in one locale must be in all four).
3. Key counts agree across all four locales.

Run only this test:

```bash
npm test -- i18nParity
```

When it fails, the assertion message lists exactly which keys are missing
from which locale. Add them and re-run.

References: defects D-007 / C-5 / D-205.

---

## 4. Project layout

```
src/
  app/            # App-level providers + router
  features/       # Feature modules (auth, projects, organization, positions, …)
  pages/          # Route-level pages (composed of features)
  shared/         # Cross-cutting code (api client, i18n, auth, components)
  test/           # Vitest setup + helpers
```

Each feature follows the pattern:

```
features/<name>/
  api/        # httpClient calls + React-Query keys
  components/
  hooks/
  pages/
  schemas/
  types/
```

---

## 5. Testing

```bash
npm test                    # all tests
npm test -- i18nParity      # locale parity guard
npm test -- noTenantIdLeak  # tenancy leak guard
```

When adding new API fetchers, extend `noTenantIdLeak.test.ts` with a case
for each one — the test is the canonical guard against repeating D-202.

---

## 6. Security UI hard rules

- Never display salary values without the salary permission (use
  `<SalaryValue />` and `PermissionGate`).
- Never log JWTs, tokens, or salary values to the console.
- Never trust the frontend permission checks as real security — backend is
  source of truth. UI gating is for UX only.
- Approved methodology / evaluation must render as read-only.

See `src/shared/components/access/PermissionGate.tsx` and
`src/shared/components/salary/SalaryValue.tsx` for primitives.
