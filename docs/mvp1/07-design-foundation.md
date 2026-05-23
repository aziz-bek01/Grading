# MVP 1 — Design Foundation

**Product:** grading.hrlab.uz
**Owner agent:** product-designer
**Audience:** frontend-engineer, backend-engineer, hr-product-owner, qa-engineer, security-engineer
**Status:** Draft v1.0 — implementation-ready specification for the React + TypeScript + Tailwind team
**Date:** 2026-05-23
**Source of truth:** `архитектура.md` §20 + §8; `docs/mvp1/01-product-prd.md`; `docs/mvp1/02-security-blueprint.md`

This document defines the **design system foundation** and the **AppShell-level UX contract** that every screen in MVP 1 must respect. It is the binding handoff between design and frontend implementation. Where the security blueprint and PRD contradict creative choices, security and PRD win.

---

## 1. Design Principles

grading.hrlab.uz must feel like **premium HR consulting software** delivered as **secure enterprise SaaS**. It is a tool that HR Laboratories operates on behalf of many different **company-clients** — banks, holdings, universities, telecoms, production companies, public organisations. The product must therefore feel universal, neutral, executive-grade, and trustworthy. It is not a banking-internal admin panel, not a playful HR app, not a generic admin template.

### 1.1 Ten product-design principles

1. **Company-client first, not "bank".** Every label, empty state, and navigation item uses the word "company-client" (and its 4-language equivalents). No banking-specific terminology in shipped strings. The product is universal SaaS.
2. **Context is sacred.** The user must always be able to answer four questions at a glance: *Where am I? Which company-client? Which project? Which workflow stage?* The TopBar is the answer; it is never hidden, never collapsed below a hamburger.
3. **Permission is server-truth.** The frontend hides actions that the user does not have, but it never decides whether something is allowed — the backend is the only source of truth. `PermissionGate` is UX-only; the backend will re-check every call.
4. **Salary is a separate sensitivity class.** Grade visibility does not imply salary visibility. Salary data is masked, encrypted, audited, and isolated from charts, tooltips, exports, and AI panels unless `SALARY_VIEW` is granted. In MVP 1 nobody has this permission — but the visual language must already exist.
5. **Approved is locked, and it looks locked.** Approved methodology and approved evaluation become read-only, are visually grey, carry a lock icon, and only offer "Create new version" as an edit affordance.
6. **AI is advisory, never decisive.** Every AI surface carries the label "AI suggestion — human approval required", uses a distinct visual treatment (subtle accent border, "AI" chip), and is never confused with system-approved output.
7. **No cross-tenant confusion ever.** Tenant switching is explicit, confirmation-gated, and visually marked. Two tenants are never visible at the same time. The user cannot type `tenant_id` into any form.
8. **States are a first-class deliverable.** Every screen has a documented loading, empty, error, no-access, locked and archived state. "Done" means all six are designed.
9. **Multilingual from day one with Uzbek length budget.** All copy is keyed and translated into 4 locales: `ru-RU`, `uz-Cyrl-UZ`, `uz-Latn-UZ`, `en-US`. Designs assume Uzbek strings are **~25–35% longer** than Russian and **~40% longer** than English; nothing breaks at +40%.
10. **No accidental disclosure.** Confirmation dialogs, reason-required dialogs, data-scope warnings before export, and "this report contains confidential compensation data" warnings exist by default — not as exceptions.

### 1.2 Visual direction (summary)

* White and light neutral surfaces. Deep navy primary, cyan accent, slate-gray text. Soft borders. Calm status colors. Red and orange reserved for risk and danger; green for healthy and approved states.
* Compact but readable executive density. Tables prioritised over decorative cards.
* No gradients, no playful illustrations, no decorative shadows, no childish iconography.
* Charts are simple and explanatory; numbers must always be reachable in a table view, not only a chart.

---

## 2. Information Architecture and Role-Aware Navigation

### 2.1 Top-level IA

```
grading.hrlab.uz
├── (Public)         /login                       — SSO / OIDC sign-in
│                    /access-denied               — 403 fallback
│                    /no-access                   — 404 / no-context fallback
│
├── (HRLab Portfolio scope — no active tenant)
│   ├── /                                         — HRLab Admin Dashboard (Portfolio)
│   ├── /companies                                — Company-client list
│   ├── /companies/:id                            — Company-client details + Tenant settings
│   ├── /access                                   — Users & Access (cross-tenant, HRLab Super Admin only)
│   ├── /audit                                    — Cross-tenant audit (HRLab Super Admin only)
│   └── /localization                             — Localization dictionary (HRLab Super Admin only)
│
├── (Tenant scope — active company-client selected)
│   ├── /t/:tenantSlug                            — Tenant overview
│   ├── /t/:tenantSlug/projects                   — Project list
│   ├── /t/:tenantSlug/access                     — Tenant users & access
│   └── /t/:tenantSlug/audit                      — Tenant audit log
│
└── (Project scope — active project selected)
    └── /t/:tenantSlug/p/:projectSlug
        ├── /                                     — Project Workspace (Workflow Stepper)
        ├── /organization                         — Organization structure tree
        ├── /positions                            — Position catalog
        ├── /positions/:id                        — Position details
        ├── /job-profiles/:id                     — Job profile editor / viewer
        ├── /methodology                          — Methodology list
        ├── /methodology/:id/v/:version           — Methodology builder
        ├── /evaluation                           — Evaluation matrix
        ├── /evaluation/:id                       — Evaluation detail
        ├── /grades                               — Grade structure / pyramid (basic)
        ├── /audit                                — Project-scoped audit log
        └── (reserved, disabled stubs)
            /reports, /compensation, /files, /ai
```

### 2.2 Three scope contexts

The platform has three navigation scopes, displayed differently in the TopBar and Sidebar:

1. **Portfolio scope** — no active tenant. HRLab-internal users only. TopBar shows "All company-clients" badge. Sidebar shows portfolio-level items (Dashboard, Companies, cross-tenant Access/Audit). Most client-facing modules are hidden.
2. **Tenant scope** — active company-client, no active project. TenantSelector shows selected client. Sidebar shows Projects list, Tenant-level users & access, Tenant audit.
3. **Project scope** — both tenant and project active. Full workflow sidebar appears (Workspace, Organization, Positions, Job Profiles, Methodology, Evaluation, Grades, etc.).

Switching scope is explicit. Clicking a company-client moves you into tenant scope (confirmation if you had unsaved work elsewhere). Clicking a project moves you into project scope. The TopBar always shows the active scope chain.

### 2.3 Role visibility matrix (MVP 1)

The matrix below drives which nav groups each role sees. Items not visible are **hidden**, not disabled, unless an item exists but is gated by per-resource ABAC — in which case it is shown but rejected on click (with a "No access" state) so the user understands the IA. (Hidden vs disabled rule — see §9.)

| Sidebar group / item       | HRLab SA | HRLab PM | HRLab Consult. | HRLab Analyst | Client Admin | Client HR Director | Client HR Spec. | Committee | Dept. Manager | Viewer | Auditor |
|----------------------------|:--------:|:--------:|:--------------:|:-------------:|:------------:|:------------------:|:---------------:|:---------:|:-------------:|:------:|:-------:|
| Portfolio Dashboard        | ✓        | ✓        |                |               |              |                    |                 |           |               |        |         |
| Company-clients            | ✓        | ✓ (own)  | ✓ (own)        | ✓ (own)       |              |                    |                 |           |               |        |         |
| Tenant Settings            | ✓        |          |                |               | ✓            |                    |                 |           |               |        |         |
| Projects (tenant)          | ✓        | ✓        | ✓              | ✓             | ✓            | ✓                  | ✓               | ✓         | ✓             | ✓      | ✓       |
| Project Workspace          | ✓        | ✓        | ✓              | ✓             | ✓            | ✓                  | ✓               | ✓         | ✓             | ✓      | ✓       |
| Organization               | ✓        | ✓        | ✓              | ✓             | ✓            | ✓                  |                 |           | ✓ (scope)     | ✓      | ✓       |
| Position Catalog           | ✓        | ✓        | ✓              | ✓             | ✓            | ✓                  | ✓               | ✓         | ✓ (scope)     | ✓      | ✓       |
| Job Profiles               | ✓        | ✓        | ✓              | ✓             | ✓            | ✓                  | ✓               | ✓         | ✓ (scope)     | ✓      | ✓       |
| Methodology                | ✓        | ✓        | ✓              | ✓ (read)      | ✓ (read)     | ✓ (read)           |                 |           |               | ✓      | ✓       |
| Evaluation                 | ✓        | ✓        | ✓              | ✓ (read)      | ✓ (read)     | ✓                  |                 | ✓         | ✓ (read)      | ✓      | ✓       |
| Grades                     | ✓        | ✓        | ✓              | ✓ (read)      | ✓ (read)     | ✓                  |                 | ✓         | ✓ (read)      | ✓      | ✓       |
| Reports (locked stub)      | locked   | locked   | locked         | locked        | locked       | locked             | locked          | locked    | locked        | locked | locked  |
| Compensation (locked stub) | locked   | locked   | locked         | locked        | locked       | locked             | locked          | locked    | locked        | locked | locked  |
| Files (locked stub)        | locked   | locked   | locked         | locked        | locked       | locked             | locked          | locked    | locked        | locked | locked  |
| AI Assist (locked stub)    | locked   | locked   | locked         | locked        | locked       | locked             | locked          | locked    | locked        | locked | locked  |
| Audit Log                  | ✓ (X-T)  | ✓ (proj) | ✓ (proj)       | ✓ (proj)      | ✓ (tenant)   | ✓ (tenant)         |                 |           |               |        | ✓       |
| Users & Access             | ✓ (X-T)  | ✓ (view) | ✓ (view)       |               | ✓ (tenant)   |                    |                 |           |               |        |         |
| Localization (admin)       | ✓        |          |                |               |              |                    |                 |           |               |        |         |

`X-T` = cross-tenant scope. `locked` = item is rendered but with a `LockedBadge` and "Available in next release" tooltip; clicking does not navigate.

---

## 3. Design Tokens (Tailwind theme)

All values below must ship as CSS custom properties (`--color-*`, `--space-*` …) and be mirrored in `tailwind.config.ts`. **No hardcoded colors or pixel values in components.** Use tokens.

### 3.1 Color tokens

```ts
// tailwind.config.ts — theme.extend.colors
colors: {
  // Brand
  primary: {
    50:  '#EFF6FF',
    100: '#DBE7F5',
    200: '#B3C7E4',
    300: '#7C9EC9',
    400: '#3F6EA8',
    500: '#1F4F86',  // primary-default (deep navy)
    600: '#163E6C',  // primary-hover
    700: '#0F2E54',  // primary-pressed
    800: '#0A2240',
    900: '#061833',
    DEFAULT: '#1F4F86',
  },
  accent: {
    50:  '#ECFEFF',
    100: '#CFFAFE',
    300: '#67E8F9',
    500: '#06B6D4',  // cyan accent
    600: '#0891B2',
    700: '#0E7490',
    DEFAULT: '#06B6D4',
  },

  // Neutrals (slate)
  surface:     '#FFFFFF',
  background:  '#F8FAFC',
  border:      '#E2E8F0',
  'border-strong': '#CBD5E1',
  divider:     '#EDF2F7',

  // Text
  'text-primary':   '#0F172A',
  'text-secondary': '#475569',
  'text-muted':     '#64748B',
  'text-inverse':   '#FFFFFF',
  'text-disabled':  '#94A3B8',

  // Semantic / status
  success: { 50: '#ECFDF5', 500: '#10B981', 600: '#059669', 700: '#047857', DEFAULT: '#10B981' },
  warning: { 50: '#FFFBEB', 500: '#F59E0B', 600: '#D97706', 700: '#B45309', DEFAULT: '#F59E0B' },
  danger:  { 50: '#FEF2F2', 500: '#EF4444', 600: '#DC2626', 700: '#B91C1C', DEFAULT: '#EF4444' },
  info:    { 50: '#EFF6FF', 500: '#3B82F6', 600: '#2563EB', DEFAULT: '#3B82F6' },

  // Specialised states
  locked:           '#64748B',  // slate-500: locked / read-only
  'locked-bg':      '#F1F5F9',  // slate-100
  'salary-sensitive':    '#7C3AED',  // violet-600: salary-protected accent
  'salary-sensitive-bg': '#F5F3FF',
  'ai-suggestion':       '#0EA5E9',  // sky-500: AI advisory accent
  'ai-suggestion-bg':    '#F0F9FF',
  'audit-alert':         '#DC2626',
  'audit-alert-bg':      '#FEF2F2',
}
```

Notes:

* Brand primary is "deep navy 500"; primary-hover is 600; pressed is 700. Use the 50/100 tints for backgrounds of badges and selected rows.
* `salary-sensitive` and `ai-suggestion` are deliberately not red/green — they need their own semantic slot so that "salary protected" never looks like "danger" and "AI suggestion" never looks like "success".
* `locked` reuses slate so that the read-only feel is visually heavier than disabled.

### 3.2 Typography

Font stack — system-first to guarantee Cyrillic and Latin coverage:

```ts
fontFamily: {
  sans: ['Inter', 'ui-sans-serif', '-apple-system', 'Segoe UI', 'Roboto',
         '"Helvetica Neue"', 'Arial', '"PT Sans"', 'sans-serif'],
  mono: ['JetBrains Mono', 'ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace'],
  // Numbers in tables / cards — tabular figures
  tabular: ['Inter', 'system-ui'],
}
```

Typography scale (`text-*` utilities, with mandatory `line-height` and `letter-spacing`):

| Token         | Size  | Line-height | Weight     | Usage                                          |
|---------------|-------|-------------|------------|------------------------------------------------|
| `text-xs`     | 12 px | 16 px       | 500        | Badges, table micro-meta, helper text          |
| `text-sm`     | 14 px | 20 px       | 400 / 500  | Body, form controls, table cells, sidebar item |
| `text-base`   | 15 px | 22 px       | 400        | Default body                                   |
| `text-md`     | 16 px | 24 px       | 500        | Section labels                                 |
| `text-lg`     | 18 px | 26 px       | 600        | Card titles                                    |
| `text-xl`     | 20 px | 28 px       | 600        | Subheadings                                    |
| `text-2xl`    | 24 px | 32 px       | 600        | Screen H1                                      |
| `text-3xl`    | 30 px | 38 px       | 600        | Dashboard hero numbers                         |
| `text-4xl`    | 36 px | 44 px       | 700        | Reserved (rare)                                |

Rules:

* Tabular figures (`font-variant-numeric: tabular-nums`) on every numeric column and every StatCard number.
* Letter-spacing for ALL-CAPS micro-labels: `+0.04em`. ALL-CAPS only allowed on `text-xs` micro-labels (`STATUS`, `ROLE`).
* Uzbek-length budget: any container holding translated text reserves **≥1.35× the Russian width** before clipping. Truncation uses ellipsis with tooltip showing full text.

### 3.3 Spacing

4-px base grid via Tailwind defaults:

```
space-0 = 0
space-1 = 4 px
space-2 = 8 px
space-3 = 12 px
space-4 = 16 px
space-5 = 20 px
space-6 = 24 px
space-8 = 32 px
space-10 = 40 px
space-12 = 48 px
space-16 = 64 px
```

Layout rules:

* Sidebar item vertical padding = `py-2.5` (10 px).
* Card padding = `p-6` (24 px) default; compact card `p-4`.
* Section vertical rhythm = `space-y-6` for primary blocks, `space-y-4` inside cards.
* Table cell padding = `px-4 py-3`; compact density `px-3 py-2`.

### 3.4 Radius

```
radius-none   = 0
radius-sm     = 4 px   // inputs, badges
radius-md     = 6 px   // buttons
radius-lg     = 8 px   // cards
radius-xl     = 12 px  // modals, drawer
radius-2xl    = 16 px  // hero cards (dashboards)
radius-full   = 9999px // pills, avatars
```

### 3.5 Shadows

Use shadows sparingly — enterprise feel:

```
shadow-sm   = 0 1px 2px rgba(15, 23, 42, 0.04)
shadow      = 0 1px 3px rgba(15, 23, 42, 0.06), 0 1px 2px rgba(15, 23, 42, 0.04)
shadow-md   = 0 4px 6px -1px rgba(15, 23, 42, 0.08), 0 2px 4px -1px rgba(15, 23, 42, 0.04)
shadow-lg   = 0 10px 15px -3px rgba(15, 23, 42, 0.08), 0 4px 6px -2px rgba(15, 23, 42, 0.04)
shadow-focus = 0 0 0 3px rgba(31, 79, 134, 0.25)   // primary-500 @ 25%
```

`shadow-focus` is the visible focus ring for keyboard navigation (a11y).

### 3.6 Status badge tokens

A `StatusBadge` consumes one of the named tones below. Each tone is `(bg, border, fg)`:

| Tone               | bg              | border        | fg            | Icon         |
|--------------------|-----------------|---------------|---------------|--------------|
| `draft`            | slate-100       | slate-200     | slate-700     | pencil       |
| `in-review`        | info-50         | info-500/30   | info-700      | clock        |
| `approved`         | success-50      | success-500/30| success-700   | check        |
| `locked`           | locked-bg       | locked/40     | locked        | lock         |
| `archived`         | slate-100       | slate-300     | slate-500     | archive      |
| `incomplete`       | warning-50      | warning-500/30| warning-700   | alert        |
| `needs-attention`  | danger-50       | danger-500/30 | danger-700    | alert-octagon|
| `salary-protected` | salary-sensitive-bg | salary-sensitive/30 | salary-sensitive | shield |
| `ai-suggestion`    | ai-suggestion-bg | ai-suggestion/30 | ai-suggestion | sparkles  |

All badges render icon + label and **never rely on color alone** (a11y rule).

### 3.7 Chart palette (executive, accessible)

Sequence used by data viz (Recharts/ECharts) — 8 colors in fixed order, optimised for both light/dark backgrounds and color-blind safety (Okabe-Ito-inspired with brand alignment):

```
chart-1 = #1F4F86  // primary navy
chart-2 = #06B6D4  // cyan accent
chart-3 = #10B981  // success
chart-4 = #F59E0B  // warning
chart-5 = #7C3AED  // violet (salary band)
chart-6 = #0EA5E9  // sky (AI)
chart-7 = #64748B  // slate
chart-8 = #DC2626  // danger (reserved last, used only when semantic)
```

Red/Green-Circle dashboard MUST additionally encode shape (filled / outlined / striped) so that color-blind users can distinguish red-circle from green-circle.

---

## 4. AppShell Specification

### 4.1 Structure

```
┌────────────────────────────────────────────────────────────────────────┐
│ TopBar  (h = 56 px, sticky, surface, border-bottom)                    │
├──────────────┬─────────────────────────────────────────────────────────┤
│              │ Breadcrumbs (h = 36 px, background, border-bottom)      │
│   Sidebar    ├─────────────────────────────────────────────────────────┤
│   (w = 240,  │                                                         │
│   collapsi-  │              Content Area                               │
│   ble to     │              (max-w-screen-2xl, mx-auto, p-6)           │
│   72 px)     │                                                         │
│              │                                                         │
└──────────────┴─────────────────────────────────────────────────────────┘
```

* Min viewport width: **1366 px** (laptop). Below 1280 px the sidebar collapses to 72 px (icons only) automatically.
* Sidebar collapse persists per user (local preference).
* `<main>` is a single scroll container; TopBar, Sidebar, Breadcrumbs are sticky.
* Background is `background` (`#F8FAFC`); cards and panels are `surface`.

### 4.2 AppShell responsibilities

* Hosts global providers: `<AuthProvider>`, `<I18nProvider>`, `<TenantContextProvider>`, `<ProjectContextProvider>`, `<QueryClientProvider>`, `<Toaster>`, `<DialogStack>`.
* Renders the route outlet inside the content area.
* Listens to 401/403 from the API client; on 401 redirects to `/login`; on 403 routes to `/access-denied`.
* Provides keyboard shortcut surface: `?` opens shortcut help; `g p` goes to project list; `g d` goes to dashboard; `Esc` closes drawers/dialogs.

### 4.3 Implementation hint

```
src/app/layout/
  AppShell.tsx
  TopBar.tsx
  Sidebar.tsx
  Breadcrumbs.tsx
  AppShellRouter.tsx
```

---

## 5. TopBar Specification

The TopBar is the **single most important UI surface** for tenant safety. It must always answer: "Which company-client and which project am I in?"

### 5.1 Layout (left → right)

```
┌──[Logo HRL]──[TenantSelector ▾]──[ProjectSelector ▾]─────────────────────[Search ⌘K]──[ 🔔 ]──[ LanguageSwitcher ▾ ]──[ UserMenu ▾ ]──┐
```

* Logo `HRL` (32×32) → clicking returns to Portfolio Dashboard (with unsaved-changes confirmation).
* **TenantSelector** — see §7. Always rendered; shows "All company-clients" in Portfolio scope; shows the active company-client name and a colored 8-px context dot in Tenant/Project scope.
* **ProjectSelector** — visible only when a tenant is selected. Shows "Select project" placeholder until a project is chosen.
* **Search ⌘K** — global within current scope; never crosses tenants. (MVP 1: optional; if not shipped, hide the input — do not stub.)
* **Bell icon** — notifications; MVP 1 shows audit-alert badge count for HRLab Super Admin only (security alerts: cross-tenant attempts). Other users see disabled bell.
* **LanguageSwitcher** — see §8.
* **UserMenu** — avatar (initials), name, role, "Profile", "Switch role" (only if user has multi-role membership — MVP 2), "Sign out".

### 5.2 Visual rules

* Background `surface`, border-bottom `border`.
* Active tenant context indicator: a 6-px-tall colored bar below the TopBar tinted to the tenant's brand accent (a deterministic hash of `tenant_id` → one of 8 palette tones). This is the **tenant visual fingerprint** — different company-clients always look distinct, even on the same browser tab. Two windows on two tenants are visually unmistakable.
* Active project indicator: project name appears in `text-text-primary text-sm font-semibold` inside ProjectSelector; a 2-px underline in `accent-500` confirms the active project.
* TopBar height is fixed; content does not collapse.

### 5.3 Accessibility

* All selectors are keyboard navigable (Tab cycles selectors; Enter opens; arrows navigate options; Esc closes).
* Active tenant + project must be announced by screen readers via `aria-label="Active company-client: ACME Holdings. Active project: Grading 2026."` on the TopBar root.

---

## 6. Sidebar Specification

### 6.1 Behavior

* Width: 240 px expanded, 72 px collapsed (icon-only).
* Auto-collapse < 1280 px; user override persists.
* Sidebar groups are **role-aware** (see §2.3 matrix).
* Active route highlighted with: `bg-primary-50`, left-border `border-l-2 border-primary-500`, text `text-primary-700`, icon `text-primary-600`.
* Hover: `bg-divider`.

### 6.2 Item states

| State                | Visual                                                     | Click behavior                                          |
|----------------------|------------------------------------------------------------|---------------------------------------------------------|
| **Visible & enabled**| Standard                                                    | Navigates                                               |
| **Active route**     | Highlight (see above)                                       | n/a                                                     |
| **Hidden**           | Not rendered                                                | n/a                                                     |
| **Disabled**         | `text-text-disabled`, no hover, cursor-not-allowed          | No-op; tooltip "Requires permission: X"                 |
| **Locked (reserved)**| Lock icon + slate; `LockedBadge` ("Soon")                   | No navigation; tooltip "Available in next release"      |
| **Has-notification** | Small red dot top-right of icon                             | Navigates; clears dot on read                           |

### 6.3 MVP 1 sidebar structure (Project scope)

```
PROJECT
  ▸ Workspace                  (workflow stepper home)
  ▸ Organization               (org tree)
  ▸ Positions                  (catalog)
  ▸ Job Profiles               (drafts & approved)
  ▸ Methodology                (builder + versions)
  ▸ Evaluation                 (matrix + approvals)
  ▸ Grades                     (structure + basic pyramid)

GOVERNANCE
  ▸ Audit log
  ▸ Users & Access             (where role allows)

LOCKED (visible but disabled with "Soon" badge)
  ▸ Compensation 🔒
  ▸ Reports 🔒
  ▸ Files 🔒
  ▸ AI Assist 🔒

FOOTER
  ▸ Help & shortcuts (?)
```

The locked group is shown so that users understand the product roadmap visually, but never as a clickable item. This satisfies "context is sacred" while preventing user disappointment when MVP 2/3/4 features are not there.

### 6.4 Tenant scope sidebar

```
TENANT
  ▸ Tenant overview
  ▸ Projects
  ▸ Users & Access (tenant)
  ▸ Tenant audit
  ▸ Tenant settings (admins only)
```

### 6.5 Portfolio scope sidebar

```
HRLAB
  ▸ Portfolio dashboard
  ▸ Company-clients
  ▸ Cross-tenant audit       (Super Admin)
  ▸ Localization             (Super Admin)
  ▸ Platform users           (Super Admin)
```

---

## 7. TenantSelector + ProjectSelector UX

### 7.1 Tenant switching contract

**Critical security UX:** the user never types a `tenant_id`. They only pick from a backend-provided list of tenants they belong to. The act of selecting issues a fresh tenant-context token from the backend (per security blueprint §5.1, §6).

```
TenantSelector (closed):
  ┌──────────────────────────────────────┐
  │ ● ACME Holdings ▾                    │   ← context dot (tenant fingerprint) + brand name
  └──────────────────────────────────────┘

TenantSelector (open):
  ┌──────────────────────────────────────┐
  │ ● ACME Holdings              ✓       │   ← active
  │ ● Beta University                    │
  │ ● Gamma Telecom                      │
  │ ─────────────────────────────────────│
  │ + Manage company-clients             │   ← only HRLab Super Admin / PM
  └──────────────────────────────────────┘
```

### 7.2 Tenant switch confirmation

When the user picks a **different** tenant, a `ConfirmDialog` appears:

> **Switch active company-client?**
> You are leaving **ACME Holdings** and switching to **Beta University**.
> Any unsaved changes will be discarded.
>
> [Cancel] [Switch company-client]

* The dialog uses the destination tenant's context dot color as the confirmation button accent so the user sees the new fingerprint before committing.
* On confirm: backend exchanges current token for new tenant-context token; frontend invalidates all React Query caches (`queryClient.clear()`); router navigates to `/t/:newTenantSlug/projects`.
* Toast confirmation: "Switched to Beta University".
* Audit event `TENANT_CONTEXT_SWITCH` fires server-side.

### 7.3 Project switching contract

Same pattern, scoped within the active tenant:

```
ProjectSelector (open):
  ┌──────────────────────────────────────┐
  │ Grading 2026                  ✓      │
  │ Pilot 2025                           │
  │ Methodology refresh 2024 (archived)  │
  │ ─────────────────────────────────────│
  │ + Create project                     │   ← PMs only
  └──────────────────────────────────────┘
```

* Archived projects shown italicised + dim; clickable for read-only access.
* Switching projects within the same tenant invalidates project-scoped queries but keeps tenant context.

### 7.4 Hard rules

* No business form (project create, position create, profile, methodology, evaluation, etc.) contains a `tenant_id` input. Backend ignores any `tenant_id` in body (per security blueprint TI-12). Designs must never include such a field.
* Tenant cannot be inferred from URL only — the URL `/t/:tenantSlug/...` is a UX convenience; the backend trusts only the JWT/context-token.
* On 401 `TENANT_CONTEXT_EXPIRED`, the AppShell auto-prompts re-selection of tenant before retry.

---

## 8. LanguageSwitcher UX

### 8.1 Languages

| Code         | Native label   | Latin label | Direction |
|--------------|----------------|-------------|-----------|
| `ru-RU`      | Русский        | Russkiy     | LTR       |
| `uz-Cyrl-UZ` | Ўзбек          | Oʻzbek (k)  | LTR       |
| `uz-Latn-UZ` | Oʻzbek         | Oʻzbek (l)  | LTR       |
| `en-US`      | English        | English     | LTR       |

All MVP 1 languages are LTR. RTL not in scope.

### 8.2 Behavior

* TopBar shows current locale as a 2-letter pill: `RU`, `УЗ`, `UZ`, `EN`. (Note distinct Cyrillic vs Latin pill for Uzbek.)
* Click opens dropdown with native label + Latin transliteration sublabel.
* Selecting persists per user (`PATCH /api/v1/users/me/preferences {locale}`); applies immediately (no reload).
* Locale switch also reformats numbers, dates, percentages.

### 8.3 Uzbek length budget

* Every label container — sidebar item, button, table column header, badge — is sized for **+40 %** of the Russian width before truncation. If a label still overflows, ellipsis is used with a tooltip showing full text.
* Numeric values use locale-specific separators (`1 000 000` in `ru-RU`/`uz-*`; `1,000,000` in `en-US`).
* No string concatenation in code — always full sentences via i18next, parameterised. Gender/plural rules supported.

### 8.4 Localization completeness indicator

* In dev/QA builds, a small "missing translation" warning indicator appears next to keys that fall back to `ru-RU`.
* In production, silent fallback to primary locale (`ru-RU` for tenant or `en-US` global default).

---

## 9. Permission-Based Navigation Rules

### 9.1 The three states: hidden vs disabled vs no-access

| Decision                                                          | UX state          |
|-------------------------------------------------------------------|-------------------|
| User's role catalog does not include the module (e.g., HR Specialist + Methodology) | **Hidden**       |
| User has the module but lacks one specific action (e.g., can read but not approve)  | Action is hidden inside the screen; row remains accessible |
| User has the module in IA but the specific object is out of scope (different department) | **No-access state** (404 generic) |
| Module is reserved for future MVP                                | **Locked stub** (visible, badge "Soon") |

### 9.2 `<PermissionGate>` contract (frontend)

```tsx
<PermissionGate permission="METHODOLOGY_APPROVE">
  <Button>Approve & lock</Button>
</PermissionGate>

<PermissionGate permission="SALARY_VIEW" fallback={<SalaryValue masked />}>
  <SalaryValue value={123_000} />
</PermissionGate>
```

* `permission` accepts a string or array (ALL-of by default; `mode="any"` for ANY-of).
* When the user lacks the permission, the child is **not mounted** (DOM-absent). No CSS hiding — that would still ship to inspector.
* `fallback` optional: render an alternative element (e.g., masked value).
* Hook variant: `usePermission("…")` for branching logic. Never use it to gate API calls — that's the backend's job.

### 9.3 Backend is source of truth

* Every API call returns a 403 / 404 / omitted-field if the backend disagrees with the frontend. The frontend treats these as authoritative.
* If a hidden button is somehow triggered (deep-link, script), the backend will reject. The frontend handles the rejection gracefully (toast: "Action not permitted").
* Salary fields are **omitted from the response body entirely** when no permission (per security blueprint §8). The frontend never receives masked stubs from backend — it just doesn't see the field. The `<SalaryValue masked />` state is rendered only when the surrounding UI needs to display a placeholder.

### 9.4 Route guards

```tsx
<Route element={<ProtectedRoute permissions={["METHODOLOGY_EDIT"]} />}>
  <Route path="methodology/:id/v/:version/edit" element={<MethodologyBuilder />} />
</Route>
```

* On lack of permission → render `<NoAccessState />` inside the layout (not redirect, so user keeps context).
* On 401 → redirect to `/login`.

---

## 10. Status Badge System

The `<StatusBadge>` component is the most-used atom in the product. Every entity that has a state shows it. All badges combine **icon + label + tone**.

### 10.1 Catalog

| Tone                | Label (en)            | Used for                                                                 |
|---------------------|-----------------------|--------------------------------------------------------------------------|
| `draft`             | Draft                 | Job profile, methodology, evaluation, project, grade structure           |
| `in-review`         | In review             | Submitted profile / evaluation awaiting approval                         |
| `approved`          | Approved              | Approved profile / evaluation / methodology / grade structure            |
| `locked`            | Locked                | Approved methodology version; approved evaluation                        |
| `archived`          | Archived              | Archived project (mostly MVP 2)                                          |
| `incomplete`        | Incomplete            | Missing required factor selection; missing required profile field        |
| `needs-attention`   | Needs attention       | Validation errors; blocked workflow stage; integrity warning             |
| `salary-protected`  | Salary protected      | Card/section that contains compensation data (mostly MVP 3)              |
| `ai-suggestion`     | AI suggestion         | Anything authored by AI awaiting human approval                          |

### 10.2 Variants

* **Solid** (default) — filled tone background.
* **Outline** — used when many badges appear together (tables).
* **With reason tooltip** — when `Needs attention`, the badge has a tooltip explaining what is wrong.

### 10.3 Required composition rules

* Always icon + label. Never icon-alone or label-alone.
* `text-xs`, `font-medium`, padding `px-2 py-0.5`, radius `radius-full`.
* For 4-language fit, reserve **9 chars** in EN as the design width; expect Uzbek labels up to **14 chars**. Truncation forbidden — use multi-line if absolutely needed.

---

## 11. Salary Masking UX (`<SalaryValue>`)

Even though MVP 1 has no salary screens shipped, the **component must exist** and be used wherever a future salary value would be displayed (per security blueprint R-06). It is the visual contract the team adopts on day one.

### 11.1 States

| State                       | Visual                                                              | When                                                       |
|-----------------------------|---------------------------------------------------------------------|------------------------------------------------------------|
| `visible`                   | Numeric value with locale formatting, tabular figures, e.g. `8 500 000 UZS` | Backend returned the field AND user has `SALARY_VIEW`     |
| `masked`                    | `••••••` (six dots) with `SalaryProtectedBadge`                     | Backend returned the field but UI policy hides it (rare)  |
| `permission-required`       | Lock icon + label "Salary access required" + `text-text-muted`      | Backend omitted the field (no `SALARY_VIEW`)              |
| `forbidden` (403)           | "Salary access required" — same as above; toast on attempt          | User tried to load a salary endpoint without permission   |

### 11.2 Hard rules

* `<SalaryValue>` **never** receives an unmasked number in MVP 1 because no user has `SALARY_VIEW`. It always renders `permission-required`.
* Charts and tooltips must use a `<SalaryValue>` placeholder, not a raw number. ChartTooltip's salary slot is conditionally rendered via `<PermissionGate permission="SALARY_VIEW">`.
* `<SalaryValue>` cannot be exported. Export buttons surface a warning dialog (see §14 ExportButton).
* Audit timeline displays salary-touched events as `<redacted>` (backend already redacts per security blueprint §8).

### 11.3 Export warning

Before any export that **could** contain salary data, a `ConfirmDialog` requires:

> **Confirm export**
> This report contains **confidential compensation data**.
> Data scope:
> - Company-client: ACME Holdings
> - Project: Grading 2026
> - Report type: Salary range summary
> - Contains salary: **Yes**
>
> By exporting, you accept responsibility for handling this file according to your company's confidentiality policy.
>
> [Cancel] [Export with salary data]

(MVP 1 never ships salary exports — but the dialog component must exist in the design system.)

---

## 12. AI Suggestion Visual Treatment

Even though AI ships in MVP 4, the **AIRecommendationPanel** component and the visual treatment must exist so that any preview surface, placeholder, or roadmap demo is unambiguous.

### 12.1 Rules

* AI surfaces use the `ai-suggestion` tone (sky blue / cyan family) — different from approved (green) and from primary brand.
* Every AI surface carries the label **"AI suggestion — human approval required"** (full sentence; never abbreviated to "AI" alone).
* AI-authored content is wrapped in a panel with:
  * Left border `border-l-4 border-ai-suggestion-500`.
  * Header chip `<StatusBadge tone="ai-suggestion">AI suggestion</StatusBadge>`.
  * Sub-label: confidence indicator (0–100 %), source fields, generation date, model identifier (when available).
  * Two terminal actions: **Accept** (stages the AI value into a draft state — does NOT approve), **Reject** (dismisses with optional comment).
  * Disclaimer footer: "Accepting an AI suggestion does not approve the entity. A human must still approve."
* AI suggestions never look like "Approved". `tone="approved"` is forbidden on AI-authored content.

### 12.2 Locked-stub treatment in MVP 1

The "AI Assist" sidebar item is locked (`LockedBadge "Soon"`); the `<AIRecommendationPanel>` is built but not surfaced in user flows. It exists for design demo and downstream integration only.

---

## 13. Locked Methodology + Locked Evaluation Visual Treatment

### 13.1 Visual contract

A locked entity is the strongest read-only signal in the product. The signal must be unmistakable.

* Header: lock icon (`lucide-lock`) + `<StatusBadge tone="locked">Locked</StatusBadge>` + the actor and timestamp ("Locked by Dilshod Karimov on 2026-05-18 at 14:23 UTC").
* All inputs in the body are rendered as `read-only` text — not as disabled form controls. Disabled form controls invite hover and click; read-only text does not.
* Background of the editor pane shifts to `locked-bg` (`#F1F5F9`).
* The primary CTA changes from "Save" to **"Create new version"** (for methodology) or **"Reject and return to draft"** (for evaluation, where allowed).
* Side panel displays version history: v1 (locked), v2 (draft), with a "Compare versions" affordance reserved for MVP 2.
* Editing attempts via deep-link return a `409 METHODOLOGY_LOCKED` / `EVALUATION_LOCKED` response → toast "This entity is locked. Create a new version to make changes." → CTA "Create new version".

### 13.2 No false "Save" buttons

If a user opens a locked entity, they must NEVER see a Save / Submit / Approve button. The only mutating affordance is "Create new version" (methodology) or none at all (locked evaluation, except by HR Director rejecting).

---

## 14. Component Inventory

Each component below ships in the MVP 1 design system. One-line UX spec; full UX docs live next to each component in `src/components/`.

| # | Component                  | UX spec (one-liner)                                                                                  |
|---|----------------------------|------------------------------------------------------------------------------------------------------|
| 1 | `AppShell`                 | Hosts TopBar + Sidebar + Breadcrumbs + Content; manages 401/403 redirects and global providers.       |
| 2 | `TopBar`                   | Always-visible header: logo, TenantSelector, ProjectSelector, search, notifications, language, user.  |
| 3 | `Sidebar`                  | Role-aware nav, collapsible 240→72; active state + locked stubs + notification dots.                  |
| 4 | `Breadcrumbs`              | Reflects scope chain (HRLab > Company > Project > Module > Entity); each crumb navigable.             |
| 5 | `TenantSelector`           | Dropdown of user's tenants; switch triggers confirmation + cache invalidation; never accepts free input. |
| 6 | `ProjectSelector`          | Dropdown scoped to active tenant; switch confirms unsaved work; create-project visible to PM.         |
| 7 | `LanguageSwitcher`         | RU / УЗ / UZ / EN pill dropdown; persists per user; reformats numbers/dates.                         |
| 8 | `UserMenu`                 | Avatar + name + role; profile, sign-out; future "switch role".                                       |
| 9 | `Card`                     | Base container with `surface`, `radius-lg`, `shadow-sm`, `p-6`; optional title, subtitle, action slot. |
| 10 | `StatCard`                | Hero number + label + delta + tone; tabular figures; supports `salary-protected` and `locked` overlays. |
| 11 | `ProgressCard`            | Completion % bar + counts (e.g., 32 / 60 positions); supports blocker indicator.                     |
| 12 | `WorkflowStepper`         | Horizontal multi-stage navigator: Setup → Organization → ... → Archive; per-stage badge + blocker.    |
| 13 | `StatusBadge`             | Icon + label + tone; 9 tones; never color-alone; tabular sizing for 4 languages.                     |
| 14 | `LockedBadge`             | Specialized status badge with lock icon + "Locked" or "Soon" label; used in sidebar and entities.    |
| 15 | `SalaryValue`             | Renders salary number when `SALARY_VIEW`; else "Salary access required" with lock icon.              |
| 16 | `SensitiveDataMask`       | Generic masker for any classified value (PII, salary); accepts state prop.                           |
| 17 | `DataTable`               | Search, filters, sort, pagination, column visibility, saved views (basic), bulk actions stub.        |
| 18 | `FilterBar`               | Composable filter chips with multi-select; respects scope; preserves URL state.                      |
| 19 | `SearchInput`             | Single-line with debounce 300 ms; scope-locked (never crosses tenant); shows "no matches" inline.    |
| 20 | `EmptyState`              | Illustration (line-art only), title, description, primary CTA (creation), secondary CTA (docs).      |
| 21 | `LoadingState`            | Skeleton sized to the next visual; never spinner-only for primary content (a11y + perceived speed).  |
| 22 | `ErrorState`              | Inline error block with retry; uses `danger` tone; never shows stack trace; surfaces correlation id. |
| 23 | `NoAccessState`           | Friendly generic ("This area requires additional access"); no enumeration; contact-admin CTA.        |
| 24 | `ConfirmDialog`           | Modal with title, body, [Cancel] + destructive CTA; focus-trapped; ESC closes; ENTER confirms only if non-destructive. |
| 25 | `ReasonRequiredDialog`    | ConfirmDialog variant with mandatory `<textarea>` (min 20 chars) — used for manual calibration, score override. |
| 26 | `DrawerForm`              | Right-side slide-in (560 px) for create/edit; embeds form sections; saves draft locally.              |
| 27 | `DetailPanel`             | Right-side panel (~640 px) for entity inspection; tabs (Overview, Audit, Comments).                  |
| 28 | `AuditTimeline`           | Vertical timeline of audit events; actor, action, timestamp, before/after diff (redacted as needed). |
| 29 | `CommentThread`           | Thread of comments with author, timestamp, mention; stub in MVP 1; lays out for MVP 2.               |
| 30 | `AIRecommendationPanel`   | "AI suggestion — human approval required" card with confidence, sources, Accept/Reject.             |
| 31 | `PermissionGate`          | Conditionally renders children based on permission; no DOM when missing; fallback supported.         |
| 32 | `ExportButton`            | Dropdown of formats; opens scope-warning dialog; gated by `*_EXPORT` permissions; tags salary scope. |

---

## 15. Wireframe — HRLab Admin Dashboard (Portfolio scope)

### 15.1 UX goal

Give HRLab management an at-a-glance picture of: how many company-clients are active, how many projects are in flight, which projects are healthy vs blocked, and what security alerts need attention. **No salary numbers visible.**

### 15.2 Primary users

HRLab Super Admin, HRLab Project Manager (limited cross-tenant scope).

### 15.3 Layout

```
┌── TopBar (Portfolio scope: tenant = "All company-clients") ───────────────────┐
├── Sidebar (Portfolio) ──┬────────────────────────────────────────────────────┤
│ Portfolio Dashboard ●   │ Breadcrumbs: HRLab › Portfolio                     │
│ Company-clients         │                                                    │
│ Cross-tenant audit      │  ┌──────────── Hero stats row ────────────────┐    │
│ Localization            │  │ [StatCard] [StatCard] [StatCard] [StatCard]│    │
│ Platform users          │  │ Active     Active      Approved   Security│    │
│                         │  │ company-   projects    methodologies alerts│    │
│                         │  │ clients    (MVP1: ___) (MVP1: ___) (24h)  │    │
│                         │  └────────────────────────────────────────────┘    │
│                         │                                                    │
│                         │  ┌────────── Projects by status (bar) ─────────┐    │
│                         │  │ Draft  ████░░░░░░░  3                       │    │
│                         │  │ Active ██████████░  12                      │    │
│                         │  │ At risk ███░░░░░░░  4 ← needs-attention      │    │
│                         │  └────────────────────────────────────────────┘    │
│                         │                                                    │
│                         │  ┌── Recent projects (DataTable) ───────────┐      │
│                         │  │ Client │ Project │ Stage │ Status │ Last │      │
│                         │  │  ...   │  ...    │  ...  │  ...   │ ...  │      │
│                         │  └──────────────────────────────────────────┘      │
│                         │                                                    │
│                         │  ┌── Security alerts (last 24h) ────────────┐      │
│                         │  │ CROSS_TENANT_ACCESS_ATTEMPT   2          │      │
│                         │  │ MFA_FAILED                    1          │      │
│                         │  │ AUDIT_CHAIN_VERIFY_FAIL       0          │      │
│                         │  │                                          │      │
│                         │  │ [View all in cross-tenant audit →]       │      │
│                         │  └──────────────────────────────────────────┘      │
└─────────────────────────┴────────────────────────────────────────────────────┘
```

### 15.4 Components used

`AppShell`, `TopBar`, `Sidebar`, `Breadcrumbs`, `StatCard` × 4, `Card`, `DataTable`, `StatusBadge`, `EmptyState` (for no projects).

### 15.5 Data displayed

Counts only. No salary, no compensation. Each StatCard fetches a count from `GET /api/v1/analytics/portfolio-summary` (HRLab scope).

### 15.6 States

* **Loading**: skeleton for each StatCard (rectangles of correct size) + skeleton rows in DataTable. Never blank.
* **Empty**: "No company-clients yet — create your first" with primary CTA (Super Admin) or "Awaiting tenant assignment" for PM.
* **Error**: `ErrorState` block in place of failing card; other cards still render. Retry button per card.
* **No access**: hidden entirely (this is a Portfolio-scope screen; client users do not reach it).
* **Locked / archived**: n/a for dashboard.

### 15.7 Permissions

* Visible to roles with `PROJECT_READ` cross-tenant (HRLab Super Admin) or limited Portfolio (HRLab PM with assigned tenants).
* Salary cards are never rendered in MVP 1.
* Security-alerts card visible only with `AUDIT_READ` cross-tenant.

### 15.8 Salary masking

The dashboard never queries or displays salary. Components that could show salary (StatCard, charts) do not include salary slots in MVP 1.

### 15.9 Responsive

* ≥ 1366 px: layout as drawn.
* 1280–1366 px: sidebar collapses to icons; stat cards stay 4-up.
* < 1280 px: stat cards wrap 2×2; DataTable becomes horizontally scrollable; alerts card moves below.
* Mobile: out of scope (MVP 1 desktop-first).

### 15.10 i18n notes

* Card titles ≤ 18 chars EN, ≤ 26 chars RU/UZ; layout reserves 28-char width.
* All numbers locale-formatted; "24 h" uses locale-aware time unit.

### 15.11 Accessibility

* Each StatCard has `aria-label="{count} {label}"`.
* Charts include data-table alternative (hidden visually, available to screen readers).
* Keyboard reachable: Tab order is TopBar → Sidebar → Hero row → Projects table → Alerts card.

---

## 16. Wireframe — Project Workspace (Project scope)

### 16.1 UX goal

Make the workflow tangible: the user always knows which stage they are at, what is blocked, who is responsible, and what to do next.

### 16.2 Primary users

HRLab PM, HRLab Consultant, HR Director, HR Specialist, Committee Member (everyone with project access).

### 16.3 Layout

```
┌── TopBar (Tenant: ACME, Project: Grading 2026, active project underlined) ──┐
├── Sidebar (Project) ──┬─────────────────────────────────────────────────────┤
│ Workspace ●            │ Breadcrumbs: ACME › Grading 2026 › Workspace       │
│ Organization           │                                                    │
│ Positions              │  ┌──────────── Project header card ───────────┐    │
│ Job profiles           │  │ Grading 2026 · [StatusBadge: Active]        │   │
│ Methodology            │  │ Owner: D. Karimov · Target: 30 Jun 2026     │   │
│ Evaluation             │  │ Languages: RU (primary) · UZ-Cyrl · EN      │   │
│ Grades                 │  └─────────────────────────────────────────────┘   │
│ ────                   │                                                    │
│ Audit log              │  ┌──────────── WorkflowStepper (11 stages) ───┐    │
│ Users & Access         │  │ 1 Setup ✓ 2 Org ✓ 3 Positions ● 4 Profiles ⌗ │  │
│ ────                   │  │ 5 Method 🔒 6 Eval ⌛ 7 Calib ⌗ 8 Grades ⌗  │   │
│ 🔒 Compensation        │  │ 9 Comp 🔒 10 Reports 🔒 11 Archive ⌗         │  │
│ 🔒 Reports             │  └─────────────────────────────────────────────┘   │
│ 🔒 Files               │                                                    │
│ 🔒 AI Assist           │  ┌── Current stage detail: "Positions" ─────────┐  │
│                        │  │ Completion: ████████░░  32 / 60 (53 %)        │ │
│                        │  │ Status: in-review · Responsible: HRLab Analyst│ │
│                        │  │ Last update: 2 hours ago (D. Karimov)         │ │
│                        │  │ Blocker: 4 positions missing department       │ │
│                        │  │ Next action: [Review 4 incomplete positions]  │ │
│                        │  └───────────────────────────────────────────────┘ │
│                        │                                                    │
│                        │  ┌── Other stages (collapsed cards, scroll) ────┐  │
│                        │  │ 4 Job profiles  ⌗ Incomplete · 12 of 60 …    │ │
│                        │  │ 5 Methodology   🔒 Locked (v1) · 2 days ago  │ │
│                        │  │ 6 Evaluation    ⌛ In progress · 8 of 60     │ │
│                        │  │ …                                            │ │
│                        │  └──────────────────────────────────────────────┘  │
│                        │                                                    │
│                        │  ┌── Recent activity (AuditTimeline preview) ───┐  │
│                        │  │ ▸ EVALUATION_APPROVED · Pos #42 · …          │ │
│                        │  │ ▸ JOB_PROFILE_SUBMITTED · Pos #41 · …        │ │
│                        │  │ [View full audit log →]                       │ │
│                        │  └──────────────────────────────────────────────┘  │
└────────────────────────┴────────────────────────────────────────────────────┘
```

### 16.4 WorkflowStepper specification

Stages, in order, with the icon legend used on each step:

| # | Stage          | Icon       | Status semantics                                                       |
|---|----------------|------------|------------------------------------------------------------------------|
| 1 | Setup          | settings   | `approved` (done) or `draft`                                            |
| 2 | Organization   | sitemap    | `approved` / `incomplete`                                              |
| 3 | Positions      | briefcase  | `in-review` / `approved` / `incomplete`                                 |
| 4 | Job Profiles   | clipboard  | `in-review` / `approved` / `incomplete`                                |
| 5 | Methodology    | scale      | `draft` / `approved` → `locked`                                        |
| 6 | Evaluation     | check-square | `in-review` / `approved` / `locked` (after all approved)              |
| 7 | Calibration    | sliders    | MVP 2 — visible in stepper but locked stub                             |
| 8 | Grades         | layers     | `draft` / `approved` / `locked`                                        |
| 9 | Compensation   | dollar     | 🔒 Reserved — MVP 3                                                    |
| 10 | Reports       | document   | 🔒 Reserved — MVP 2                                                    |
| 11 | Archive       | archive    | MVP 2 — visible but disabled                                           |

### 16.5 Per-stage card data

Each stage card shows:

* Stage number + name (4-language)
* `StatusBadge` (tone matches stage status)
* `ProgressCard`-style completion bar
* Responsible role label
* Last update (relative time + actor)
* Blockers list (if any) — clickable to filtered view
* Primary "Next action" CTA — context-aware
* Audit indicator (small icon → opens audit filtered to this stage)
* Lock indicator (if applicable)

### 16.6 States

* **Loading**: skeleton stepper (11 grey blocks) + stage detail skeleton + activity skeleton.
* **Empty**: project freshly created → stepper shows stage 1 active, all others `draft`; "Get started" CTA in stage 1 detail.
* **Error**: stepper renders without data; stage detail shows `ErrorState` block; retry preserves scroll.
* **No access**: project listed but specific stage requires permission → stage card greyed with "Access required" overlay; stepper still navigable.
* **Locked stage** (methodology after approval): card shows `<LockedBadge>` and read-only summary; "Create new version" CTA replaces "Edit".
* **Archived project**: read-only banner at top of workspace; all CTAs hidden.

### 16.7 Permissions

* Stepper visible to anyone with `PROJECT_READ`. Per-stage edits gated:
  * Methodology stage edits → `METHODOLOGY_EDIT` (HRLab Consultant / PM).
  * Evaluation stage actions → `EVALUATION_*`.
  * Compensation/Reports/Files/AI — always locked stubs in MVP 1.

### 16.8 Salary masking

None in MVP 1 (no salary on this screen).

### 16.9 Responsive

* ≥ 1366 px: stepper full horizontal.
* 1280–1366 px: stepper wraps to 2 rows (steps 1–6, 7–11).
* < 1280 px: stepper becomes vertical list; stage detail stacks beneath.

### 16.10 i18n notes

Stage names are 4-language. EN max 14 chars; UZ-Cyrl up to 22 chars — stepper steps are sized for 24 char fallback.

### 16.11 Accessibility

* Stepper is a `<nav aria-label="Project workflow">` containing an `<ol>`.
* Each step has `aria-current="step"` when active and `aria-disabled="true"` when locked.
* Color is doubled by status icon — never relies on color alone.

---

## 17. Accessibility Checklist (WCAG 2.1 AA target)

| Area                    | Requirement                                                                                  |
|-------------------------|----------------------------------------------------------------------------------------------|
| Keyboard                | Every interactive element reachable via Tab; logical order; no keyboard traps; `Esc` exits dialogs and drawers. |
| Focus                   | `shadow-focus` ring on every focused element; never `outline: none` without replacement.    |
| Skip-link               | `<a href="#main">Skip to content</a>` first focusable element in AppShell.                  |
| Contrast                | Body text ≥ 4.5 : 1; UI text ≥ 4.5 : 1; large text ≥ 3 : 1; status badges meet 4.5 : 1 against their tinted background. |
| Color independence      | All status, all charts, all red/green-circle visualisations carry icon and/or shape.        |
| ARIA                    | Landmark roles for `<header>`, `<nav>`, `<main>`, `<aside>`; live regions for toasts (`aria-live="polite"`). |
| Form labels             | Every input has a visible `<label>` (no placeholder-as-label); error text linked via `aria-describedby`. |
| Tables                  | `<th scope="col">` headers; `caption` for context; sortable columns expose `aria-sort`.    |
| Modals                  | Focus-trap; `aria-modal="true"`; focus returns to invoker on close.                         |
| Motion                  | Honour `prefers-reduced-motion`; avoid auto-playing animations.                              |
| Language attribute      | `<html lang>` updates on locale switch; date/number formatting follows locale.              |
| Screen reader copy      | Active tenant + project announced; "AI suggestion — human approval required" read in full. |

---

## 18. Responsive Strategy

* **Desktop-first.** Primary target: 1366 × 768 to 1920 × 1080.
* **Min usable width: 1366 px.** Below that, sidebar auto-collapses and some grids reflow but the product remains operable down to 1280 px.
* **< 1280 px:** considered "narrow desktop"; cards wrap; tables horizontally scroll; some dashboards stack.
* **< 1024 px (tablet) and below:** not supported in MVP 1. Login and a "best viewed on desktop" page are the only mobile-friendly surfaces. Mobile dashboards reserved for a later phase.
* All breakpoints use Tailwind defaults (`sm 640`, `md 768`, `lg 1024`, `xl 1280`, `2xl 1536`); MVP 1 uses primarily `xl` and `2xl`.

---

## 19. React + Tailwind Implementation Hints

Feature-based structure aligned with backend modules and PRD epics:

```
src/
├── app/
│   ├── layout/
│   │   ├── AppShell.tsx
│   │   ├── TopBar.tsx
│   │   ├── Sidebar.tsx
│   │   └── Breadcrumbs.tsx
│   ├── routing/
│   │   ├── AppRouter.tsx
│   │   ├── ProtectedRoute.tsx
│   │   └── NoAccess.tsx
│   ├── providers/
│   │   ├── AuthProvider.tsx
│   │   ├── TenantContextProvider.tsx
│   │   ├── ProjectContextProvider.tsx
│   │   ├── I18nProvider.tsx
│   │   └── QueryProvider.tsx
│   └── i18n/
│       ├── i18n.ts
│       └── locales/{ru-RU,uz-Cyrl-UZ,uz-Latn-UZ,en-US}/common.json
├── components/                 ← design system primitives (shadcn/ui based)
│   ├── ui/                     ← shadcn primitives (button, input, dropdown, dialog, drawer, tabs, table)
│   ├── status/
│   │   ├── StatusBadge.tsx
│   │   └── LockedBadge.tsx
│   ├── salary/
│   │   ├── SalaryValue.tsx
│   │   └── SensitiveDataMask.tsx
│   ├── ai/
│   │   └── AIRecommendationPanel.tsx
│   ├── data/
│   │   ├── DataTable.tsx
│   │   ├── FilterBar.tsx
│   │   └── SearchInput.tsx
│   ├── feedback/
│   │   ├── EmptyState.tsx
│   │   ├── LoadingState.tsx
│   │   ├── ErrorState.tsx
│   │   └── NoAccessState.tsx
│   ├── dialogs/
│   │   ├── ConfirmDialog.tsx
│   │   ├── ReasonRequiredDialog.tsx
│   │   ├── DrawerForm.tsx
│   │   └── DetailPanel.tsx
│   ├── workflow/
│   │   ├── WorkflowStepper.tsx
│   │   └── StageStatusCard.tsx
│   ├── timeline/
│   │   ├── AuditTimeline.tsx
│   │   └── CommentThread.tsx
│   ├── selectors/
│   │   ├── TenantSelector.tsx
│   │   ├── ProjectSelector.tsx
│   │   ├── LanguageSwitcher.tsx
│   │   └── UserMenu.tsx
│   ├── cards/
│   │   ├── Card.tsx
│   │   ├── StatCard.tsx
│   │   └── ProgressCard.tsx
│   ├── export/
│   │   └── ExportButton.tsx
│   └── access/
│       ├── PermissionGate.tsx
│       └── usePermission.ts
├── features/                   ← feature slices, one per PRD epic
│   ├── tenants/
│   ├── projects/
│   ├── organization/
│   ├── positions/
│   ├── job-profiles/
│   ├── methodology/
│   ├── evaluation/
│   ├── grades/
│   ├── audit/
│   ├── access/
│   └── dashboard-portfolio/
├── lib/
│   ├── api/                    ← single api client; injects Bearer + X-Correlation-Id; never logs body
│   ├── auth/
│   ├── permissions/
│   ├── tenant-context/
│   ├── formatting/             ← locale-aware number, date, percent formatters
│   └── tokens.ts               ← runtime access to design tokens
├── styles/
│   ├── globals.css
│   └── tokens.css              ← CSS custom properties mirroring tailwind theme
└── main.tsx
```

Implementation rules:

* **Primitives via shadcn/ui** (Radix-based). Customise via Tailwind, never inline CSS.
* **No hardcoded colors** — every color reference resolves to a Tailwind class backed by tokens.
* **No `style={{}}` for color/spacing.** Layout `style` allowed only for dynamic sizes (e.g., progress bar widths from props).
* **All copy via i18next** — `t("module.key", { interpolation })`. No string literals in JSX outside dev-only debug.
* **All numeric values** formatted via `lib/formatting/number.format.ts` which dispatches to `Intl.NumberFormat` with the active locale.
* **Date/time** via `lib/formatting/date.format.ts` — `Intl.DateTimeFormat`; never `toLocaleString` direct.
* **API client** at `lib/api/client.ts`:
  ```ts
  const apiClient = axios.create({ baseURL: import.meta.env.VITE_API_BASE });
  apiClient.interceptors.request.use(req => {
    req.headers.Authorization = `Bearer ${getAccessToken()}`;          // in-memory
    req.headers["X-Correlation-Id"] = req.headers["X-Correlation-Id"] ?? crypto.randomUUID();
    req.headers["Accept-Language"] = i18n.language;
    return req;
  });
  // never log req/res body in prod
  ```
* **State**: TanStack Query for server state; Zustand for cross-cutting UI state (active tenant id, sidebar collapsed, language); React Hook Form + Zod for forms.
* **Routing**: react-router-dom v6; route definitions co-located in `app/routing/AppRouter.tsx`; protected routes wrap segments.
* **CSS variables in `tokens.css`** so non-Tailwind contexts (charts) still consume the same palette:
  ```css
  :root {
    --color-primary-500: #1F4F86;
    --color-chart-1: #1F4F86;
    /* ... */
  }
  ```

---

## 20. Handoff to Frontend-Engineer (Build Order)

Build in this order. Each item is a discrete, reviewable PR. Estimated relative effort is `S` (small, < 1 day), `M` (1–2 days), `L` (3–5 days).

### 20.1 Priority 0 — Foundation (blocking everything else)

1. **Design tokens (`tailwind.config.ts` + `tokens.css`)** — [S] · ship colors, typography, spacing, radius, shadows, chart palette per §3. *Acceptance: token file matches §3 verbatim; build passes; storybook page renders palette.*
2. **AppShell skeleton (`AppShell`, `TopBar`, `Sidebar`, `Breadcrumbs`)** — [L] · sticky layout, collapsible sidebar, scope-aware structure per §4–§6. *AC: empty AppShell with stub providers renders at 1366 × 768; sidebar collapses < 1280; focus ring visible.*
3. **i18n provider + 4 locale stubs** — [M] · `i18next` setup, `ru-RU` complete, others stubbed with English keys; LanguageSwitcher pill. *AC: switching locale reflows AppShell; date / number formatters work.*
4. **API client + AuthProvider (in-memory token)** — [M] · Axios client with interceptors per §19; never persists token to storage. *AC: 401 → redirect to `/login`; 403 → no-access route; correlation id propagated.*
5. **PermissionGate + usePermission + ProtectedRoute** — [M] · per §9. *AC: child not mounted when permission missing; route guard renders NoAccessState.*

### 20.2 Priority 1 — Core context + selectors

6. **TenantContextProvider + TenantSelector** — [L] · backend-issued tenant context token; switch confirmation; cache invalidation; tenant fingerprint color bar. *AC: switching invalidates QueryClient; confirmation dialog shows destination tenant name.*
7. **ProjectContextProvider + ProjectSelector** — [M] · scoped to active tenant; switch confirmation; create-project visible to PM.
8. **UserMenu + Sign-out flow** — [S] · clears in-memory token AND calls IdP `end_session_endpoint`.

### 20.3 Priority 2 — Status & feedback primitives

9. **StatusBadge + LockedBadge** — [S] · 9 tones; icon + label; tabular sizing for 4 languages.
10. **Card, StatCard, ProgressCard** — [M] · per §14.
11. **LoadingState, EmptyState, ErrorState, NoAccessState** — [M] · per §14.
12. **ConfirmDialog + ReasonRequiredDialog** — [M] · ESC handling, focus trap, mandatory reason ≥ 20 chars.

### 20.4 Priority 3 — Salary + AI primitives (shipped early per security §11.6)

13. **SalaryValue + SensitiveDataMask** — [S] · defaults to `permission-required` state; never receives raw values in MVP 1.
14. **AIRecommendationPanel** — [S] · stub component; not wired into flows; "AI suggestion — human approval required" label.
15. **ExportButton with scope-warning dialog** — [S] · stub: triggers warning dialog; actual export disabled in MVP 1.

### 20.5 Priority 4 — Data & forms

16. **DataTable + FilterBar + SearchInput** — [L] · pagination ≤ 200, debounced search, sort, column visibility, saved-view stub.
17. **DrawerForm + DetailPanel** — [M] · right-side panels with tabs.
18. **AuditTimeline** — [M] · vertical timeline; redaction of sensitive fields.

### 20.6 Priority 5 — Workflow + dashboards

19. **WorkflowStepper + StageStatusCard** — [L] · 11 stages with locked stubs per §16.
20. **HRLab Admin Dashboard (portfolio)** — [L] · per §15.
21. **Project Workspace** — [L] · per §16.

### 20.7 Priority 6 — Feature screens (one feature slice per epic)

22. Organization tree (E4)
23. Position catalog + details (E5)
24. Job profile editor + viewer (E6)
25. Methodology builder (E7) — must visually lock on approval (§13)
26. Evaluation matrix (E8) — preview-only score with disclaimer
27. Grade structure + basic pyramid (E9)
28. Audit log table (E10)
29. Users & Access management screens (E2)
30. Localization dictionary screen (E11, Super Admin only)

Each feature slice ships its own `feature.acceptance.md` documenting the six required states (loading / empty / error / no-access / locked / archived).

---

## 21. Handoff to Backend-Engineer (API Response Shape Requirements)

The design assumes the backend provides the following conventions. Anything that does not appear here is a frontend assumption that backend must confirm.

### 21.1 Auth, identity, permissions

* `POST /api/v1/auth/exchange` — accepts OIDC code, returns:
  ```json
  {
    "access_token": "...",
    "expires_in": 900,
    "user": {
      "id": "uuid",
      "email": "...",
      "name": "...",
      "locale": "ru-RU",
      "roles": ["HRLAB_PROJECT_MANAGER"],
      "permissions": ["PROJECT_CREATE", "AUDIT_READ", ...],
      "salary_data_permission": false,
      "tenants": [
        { "id": "uuid", "slug": "acme", "brand_name": "ACME Holdings", "fingerprint_hue": 215 },
        ...
      ]
    }
  }
  ```
* `GET /api/v1/users/me` — returns the same user object (no token).
* `POST /api/v1/access/tenant-context` — body `{ "tenant_id": "uuid" }`; returns:
  ```json
  {
    "tenant_context_token": "...",
    "expires_in": 1800,
    "active_tenant_id": "uuid",
    "active_project_ids": ["uuid", ...],
    "department_scope": ["uuid", ...]
  }
  ```
* All subsequent business calls send `Authorization: Bearer <tenant_context_token>`.
* Frontend never sends `tenant_id`/`project_id` in body for business endpoints.

### 21.2 List endpoints — must support

* Pagination: `?page=0&size=50` (max 200; default 20 per security blueprint API-5).
* Sorting: `?sort=createdAt,desc`.
* Filtering: documented per endpoint; uses query params, not body.
* Response envelope:
  ```json
  {
    "items": [...],
    "page": 0,
    "size": 50,
    "total_elements": 137,
    "total_pages": 3
  }
  ```

### 21.3 Status fields

* Every entity that has a state returns `"status": "DRAFT" | "SUBMITTED" | "IN_REVIEW" | "APPROVED" | "LOCKED" | "ARCHIVED" | "INCOMPLETE"`.
* Frontend maps backend status → StatusBadge tone (1:1 table maintained in `lib/status/status.map.ts`).
* Approved methodology version returns:
  ```json
  {
    "id": "...", "version": 1, "status": "LOCKED",
    "locked_by": { "id": "...", "name": "..." },
    "locked_at": "2026-05-18T14:23:00Z",
    "approved_by": { "id": "...", "name": "..." },
    "approved_at": "2026-05-18T14:22:00Z"
  }
  ```

### 21.4 Multilingual fields

* Multilingual entity fields ship as objects keyed by locale:
  ```json
  "title": {
    "ru-RU": "Главный аналитик",
    "uz-Cyrl-UZ": "Бош таҳлилчи",
    "uz-Latn-UZ": "Bosh tahlilchi",
    "en-US": "Lead Analyst"
  }
  ```
* Primary locale guaranteed; other locales may be absent. Frontend falls back to primary locale and shows "translation missing" indicator in dev.

### 21.5 Sensitive field omission

* Salary fields are **omitted entirely** when the caller lacks `SALARY_VIEW` (not `null`, not masked string). Frontend uses presence/absence to drive `<SalaryValue>` state.
* Audit `before` / `after` already-redacted server-side for sensitive entities.

### 21.6 Error envelope

```json
{
  "code": "PERMISSION_DENIED",
  "message": "Action not permitted",
  "correlation_id": "uuid"
}
```

Frontend renders error using `code` (mapped to a translation key) and `message` as fallback. `correlation_id` is shown in `ErrorState` for support reference.

### 21.7 Workflow / stage progress endpoint

For the Project Workspace stepper:

```
GET /api/v1/projects/:projectId/workflow-progress
```

returns:

```json
{
  "project_id": "uuid",
  "stages": [
    {
      "key": "ORGANIZATION",
      "status": "APPROVED",
      "completion_percent": 100,
      "total_items": 42, "completed_items": 42,
      "responsible_role": "HRLAB_ANALYST",
      "last_update": { "at": "...", "actor": { "id": "...", "name": "..." } },
      "blockers": [],
      "next_action": null
    },
    {
      "key": "POSITIONS",
      "status": "IN_REVIEW",
      "completion_percent": 53,
      "total_items": 60, "completed_items": 32,
      "responsible_role": "HRLAB_ANALYST",
      "last_update": { "at": "...", "actor": { "id": "...", "name": "..." } },
      "blockers": [
        { "code": "POSITION_MISSING_DEPARTMENT", "count": 4 }
      ],
      "next_action": {
        "label_key": "workspace.positions.next_action.review_incomplete",
        "route": "/positions?status=INCOMPLETE"
      }
    },
    ...
  ]
}
```

This single call drives the whole WorkflowStepper, every StageStatusCard, and the "Next action" CTAs.

### 21.8 Portfolio dashboard endpoint

```
GET /api/v1/analytics/portfolio-summary
```

returns:

```json
{
  "active_clients_count": 7,
  "active_projects_count": 19,
  "approved_methodologies_count": 4,
  "security_alerts_last_24h": 3,
  "projects_by_status": {
    "DRAFT": 3, "ACTIVE": 12, "AT_RISK": 4
  }
}
```

### 21.9 Audit endpoint shape

```
GET /api/v1/audit-logs?from=...&to=...&action=...&actor=...&entity_type=...&page=...&size=...
```

returns paginated audit records with redacted `before_json` / `after_json` per security blueprint.

### 21.10 Hard contracts (non-negotiable)

| Contract                                                                              | Why                                |
|---------------------------------------------------------------------------------------|------------------------------------|
| No business endpoint accepts `tenant_id` in body / query / path                       | Tenant safety (security blueprint API-13) |
| Sensitive fields omitted, not nulled, not masked-from-server                          | Frontend salary state logic depends on absence |
| Errors return `code` + `message` + `correlation_id`; never stack trace                | Security blueprint API-8           |
| `Accept-Language` header drives any server-side localised string                      | Multilingual contract              |
| Pagination defaults `size=20`, max 200; over-limit → 400                              | DoS protection                     |
| Cross-tenant attempt → 404 with audit event, never 403 leaking existence              | Tenant safety (security blueprint API-9) |
| Workflow progress endpoint returns *all* MVP 1 stages including reserved ones with `status: "LOCKED_FUTURE"` | Stepper consistency across MVP versions |

---

## 22. Acceptance Criteria for Design Foundation

The Design Foundation is "done" when:

* All design tokens listed in §3 ship as Tailwind config + CSS variables, used by every component.
* `AppShell` renders with TopBar, Sidebar, Breadcrumbs at 1366 × 768 with no horizontal scroll.
* Active company-client and active project are always visible (or "All company-clients" in portfolio scope).
* TenantSelector and ProjectSelector exist and only accept choices from a backend list; no free-text tenant entry anywhere.
* LanguageSwitcher cycles RU / УЗ / UZ / EN; all AppShell strings re-render; Uzbek longest string fits within layout reservations.
* `<PermissionGate>` removes child from DOM (not just CSS-hidden) when permission absent.
* `<SalaryValue>` renders `permission-required` state by default in MVP 1; never displays a number.
* `<StatusBadge>` exists with all 9 tones and is used wherever an entity has a state.
* `WorkflowStepper` renders 11 stages in correct order with locked stubs for MVP 2/3/4 features.
* Approved entities (methodology + evaluation) visually render as locked: lock icon, `locked` tone, read-only text, "Create new version" CTA only.
* AI surfaces (where stubbed) carry "AI suggestion — human approval required" label and `ai-suggestion` tone.
* Every screen ships loading / empty / error / no-access / locked / archived states.
* All confirmation dialogs that change scope (tenant switch, project switch) name the destination explicitly.
* All export buttons (stubbed in MVP 1) show data-scope warning dialog including "contains salary: yes/no".
* Manual calibration / score override flow uses `ReasonRequiredDialog` with ≥ 20-char reason.
* All copy is keyed; no hardcoded strings; `ru-RU` complete; other locales graceful fallback with indicator.
* Accessibility checklist §17 passes for AppShell, TopBar, Sidebar, StatusBadge, ConfirmDialog, DataTable, WorkflowStepper at minimum.

— end of design foundation —
