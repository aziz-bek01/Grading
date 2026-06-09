# MVP 1 — HR LABORATORIES (HRL) Brand Token Remap

**Product:** grading.hrlab.uz
**Owner agent:** product-designer
**Audience:** frontend-engineer (implements this), hr-product-owner, qa-engineer
**Status:** Design spec v1.0 — implementation-ready. NO component code in this doc.
**Date:** 2026-06-08
**Companion to:** `docs/mvp1/07-design-foundation.md` §3 (this doc *replaces* the §3 color table; everything else in §3 stays).

This spec re-skins the app from the generic muted-navy/cyan palette to the **HR LABORATORIES** brand (blue→violet→magenta gradient). It is a **token remap**, not a redesign: the meaning of every token slot is preserved. Components are not edited — they already consume `primary`, `accent`, `salary-sensitive`, etc. The only component-level edits are the two hardcoded focus rings and the TopBar logo (which today is a CSS `HRL` span, not the brand asset).

---

## 0. Source-of-truth colors (extracted from the supplied logo SVGs)

| Role | Hex | Provenance |
|------|-----|------------|
| Brand blue (gradient start) | `#0739B9` | `hrl-mark-gradient.svg` `.fil0/.fil1` (leftmost stripe) |
| Brand violet (gradient mid) | `#6E2EE4` | `hrl-mark-gradient.svg` `.fil112` (true geometric mid stripe) |
| Brand violet (kit alt mid) | `#9529F4` | brief / `.fil154` |
| Brand magenta (gradient end) | `#AB27FD` | `hrl-mark-gradient.svg` `.fil181/.fil182` (rightmost stripe) |
| Headline near-navy | `#11045E` | `hrl-horizontal-lockup.svg` `.fil183` (wordmark ink) |

The existing app text ink `#0F172A` is retained (the brief allows it; `#11045E` is reserved as an optional deepen-on-brand alternative — not changed in v1 to keep contrast math stable).

---

## 1. New `primary` scale — brand blue `#0739B9`

Tonal ramp anchored so **500 = #0739B9** (matches the gradient start, the logo's dominant ink) and **600 = #062F9C** is the hover/CTA-pressed darken. Tints (50–200) lean toward the kit's light-lavender page wash; shades (700–900) deepen toward the headline navy.

```ts
primary: {
  50:  '#EEF2FD',  // page-tint / selected-row bg            (text on: primary-700+)
  100: '#D6E0FA',  // badge bg, hover surfaces                (text on: primary-700)
  200: '#AEC0F4',  // disabled button bg, subtle borders      (text on: primary-800)
  300: '#7C95EC',  // muted icons, focus-secondary
  400: '#3F63DC',  // chart/secondary brand blue
  500: '#0739B9',  // DEFAULT — brand blue, buttons, active   (text on it: #FFFFFF)
  600: '#062F9C',  // hover                                   (text on it: #FFFFFF)
  700: '#06277F',  // pressed / active-route text             (text on it: #FFFFFF)
  800: '#051E63',  // deep surfaces
  900: '#041444',  // near-headline navy
  DEFAULT: '#0739B9',
}
```

**AA contrast pairings (verified intent):**
- `text-inverse` (`#FFFFFF`) on `primary-500 #0739B9` → ratio ≈ **8.5:1** (AAA for normal text). Buttons are safe.
- `primary-700 #06277F` on `primary-50 #EEF2FD` → ≈ **12:1** — the sidebar active-route pairing (`bg-primary-50` + `text-primary-700`) stays well above AA.
- `primary-700` text on `surface #FFFFFF` → ≈ **13:1** — links/active text safe.
- Do **not** put white text on `primary-200/300` (fails); disabled buttons keep `text-inverse` on `primary-200` only as a deliberately low-emphasis state — acceptable because disabled controls are exempt from WCAG contrast minimums, and this preserves the existing Button disabled style verbatim.

**Why 500=#0739B9 (not a lighter "accessible" blue):** the brand mark's dominant color *is* this blue, and white-on-it already clears AAA. Choosing a lighter 500 would weaken the brand match for no contrast benefit.

---

## 2. New `accent` scale — brand violet/magenta `#9529F4`→`#AB27FD`

The accent is now the gradient's far end (violet→magenta), used for: secondary highlights, the active-project underline in the TopBar, focus-secondary, link accents, and chart series. **500 = #9529F4** (kit violet), **600 = #AB27FD** is the magenta tip used for the gradient terminus and accent-strong.

```ts
accent: {
  50:  '#FAF0FE',  // accent tint bg
  100: '#F1DCFD',  // accent badge bg
  200: '#E1B8FB',  // soft accent border
  300: '#C983F8',  // muted accent
  400: '#B14FF6',  // accent mid
  500: '#9529F4',  // DEFAULT — brand violet                  (text on it: #FFFFFF ≈ 4.9:1, AA)
  600: '#AB27FD',  // magenta tip / accent-strong             (text on it: #FFFFFF ≈ 4.6:1, AA)
  700: '#7A1FC9',  // accent pressed                          (text on it: #FFFFFF ≈ 7.0:1)
  DEFAULT: '#9529F4',
}
```

**AA notes:**
- White on `accent-500 #9529F4` ≈ **4.9:1** — passes AA for normal text; safe for accent buttons/badges if ever used with white labels.
- White on `accent-600 #AB27FD` ≈ **4.6:1** — passes AA (borderline); prefer `accent-700` for small white text on solid accent.
- For accent text *on light* (links), use `accent-700 #7A1FC9` on `surface` ≈ **6.5:1**.

> The previous accent (cyan `#06B6D4`) is fully retired as a brand color. It survives only as a *chart* hue (see §6) so existing data-viz keeps 8 distinguishable series.

---

## 3. Brand gradient token + utility

The signature HRL gradient. Geometric stops taken from the mark (blue start → true-mid violet → magenta end).

### 3.1 Primary brand gradient (CTAs, hero, rings)
```
--gradient-brand: linear-gradient(135deg, #0739B9 0%, #6E2EE4 55%, #AB27FD 100%);
```
- Stop at 55% (not 50%) because the mark's blue band is visually wider than the magenta band — this keeps the blue brand-dominant and avoids a magenta-heavy CTA.
- Expose as a Tailwind utility via `backgroundImage` token `brand` → `bg-brand`. Also expose a CSS var `--gradient-brand` in `globals.css`/`tokens.css` so non-Tailwind surfaces (login hero, SVG rings, charts) reuse the exact same stops.

### 3.2 Subtle background wash (large surfaces, page bg behind cards)
```
--gradient-brand-wash: linear-gradient(160deg, #F4F1FB 0%, #FAF0FE 100%);
```
- This is the light-lavender page background from the kit. **Optional**: the global page `background` token stays `#F8FAFC` for table-heavy app screens (keeps data legibility); the wash is applied only to *marketing-like* surfaces (LoginPage, empty-state heroes, dashboard hero cards). Use Tailwind utility `bg-brand-wash`.

### 3.3 Where the gradient is used (and where it is NOT)
| Use gradient | Use solid `primary-500` |
|--------------|--------------------------|
| LoginPage hero panel + corner rings | All in-app primary buttons (Button `variant="primary"`) |
| Dashboard hero card backgrounds (StatCard hero / portfolio header) | Sidebar active-route indicator |
| Section accent bars / decorative concentric rings | Form controls, table chrome, badges |
| The single "hero CTA" on Login ("Sign in") | Any dense/table screen |

**Decision — default primary button = SOLID `primary-500`, not gradient.** Rationale: the app is table-dense and buttons repeat heavily; gradient buttons at high frequency look noisy and hurt the enterprise-trust feel mandated by §1.2. Gradient is reserved for *hero* moments (Login, dashboard hero). This requires **no change** to the shared `Button` (it stays solid via the remapped `primary`); the FE only adds a `bg-brand` class to the specific hero CTA on LoginPage if desired.

### 3.4 Corner rings (signature decoration)
- Large concentric rings/circles bleeding off corners, drawn as inline SVG or a pseudo-element with `--gradient-brand` at low opacity (~12–18%) over the wash. Spec: 2–3 concentric stroked circles, stroke width ~2–3% of circle diameter, positioned so ~40% bleeds off-canvas (top-right and bottom-left on Login). Decorative only → `aria-hidden`, must sit behind content (`z-0`), never reduce text contrast (keep over the wash, not under text).

---

## 4. RESOLVE: salary-sensitive ↔ brand-purple clash (security-critical)

**Problem:** brand `accent` is now purple (`#9529F4`). The existing `salary-sensitive` signal is `#7C3AED` — a near-identical purple. After the remap they become visually confusable. Salary masking is a **security signal** (§1.4, security blueprint R-06); it must never read as "just the brand accent."

**Decision: move `salary-sensitive` to a reserved DEEP TEAL.** Teal is maximally distant from both the blue→magenta brand axis *and* from the semantic red/amber/green, so "salary-protected" reads as its own dedicated class. (Amber was considered and rejected: it collides with `warning`.)

```ts
'salary-sensitive':    '#0F766E',  // deep teal — reserved, NOT a brand color
'salary-sensitive-bg': '#ECFDF8',  // teal-50 tint for masked-cell / badge bg
```
- White on `#0F766E` ≈ **5.0:1** (AA). Teal text `#0F766E` on its bg `#ECFDF8` ≈ **5.2:1** (AA). The `salary-protected` StatusBadge (`bg`, `border`, `fg`) stays legible.
- The salary signal still never relies on color alone — it always pairs with the **shield icon** + "Salary access required" label (unchanged from §10/§11). Color shift is defense-in-depth, not the sole cue.
- `<SalaryValue>` masked / permission-required states keep their lock+shield iconography; only the accent hue changes from violet to teal.

### 4.1 Confirm the other reserved tokens stay distinct from the new brand axis
| Token | Keep value | Distinct from brand? | Note |
|-------|-----------|----------------------|------|
| `locked` / `locked-bg` | `#64748B` / `#F1F5F9` | Yes (neutral slate) | Read-only/immutable methodology — unchanged. Slate is deliberately non-brand. |
| `ai-suggestion` / `-bg` | `#0EA5E9` / `#F0F9FF` | Yes (sky/cyan) | **Keep sky-blue.** It is now the only blue-cyan accent in the system (brand blue is darker `#0739B9`; sky `#0EA5E9` is lighter and clearly different). Retaining it preserves the "AI = sky" convention from §12 and keeps AI ≠ approved-green ≠ brand-blue. |
| `audit-alert` / `-bg` | `#DC2626` / `#FEF2F2` | Yes (red) | Security alert — unchanged; red is reserved for danger/audit only. |
| `success` | `#10B981` | Yes | green only for approved/healthy — unchanged. |
| `warning` | `#F59E0B` | Yes | amber for risk — unchanged (and now NOT reused for salary, which freed amber for warning's sole use). |
| `danger` | `#EF4444` | Yes | unchanged. |
| `info` | `#3B82F6` | Borderline vs brand blue | `info-500 #3B82F6` is a lighter, brighter blue than `primary-500 #0739B9`; they coexist but to avoid "two blues" confusion, **prefer `primary` for brand chrome and reserve `info` strictly for neutral informational badges/toasts.** No hex change needed. |

**Result:** every meaning-bearing token (`locked`, `salary-sensitive`, `ai-suggestion`, `audit-alert`, semantic statuses) is now visually separable from the blue→violet→magenta brand axis. Only `salary-sensitive` required a hue move.

---

## 5. Chart palette — brand-harmonious, still distinguishable (8 series)

Reordered so brand colors lead, semantic colors keep their meaning, and adjacent series stay distinguishable for color-blind users (shape/legend still required per §3.7). Salary band hue follows the new teal so charts and the salary token agree.

```ts
'chart-1': '#0739B9',  // brand blue (primary)
'chart-2': '#9529F4',  // brand violet (accent)
'chart-3': '#10B981',  // success green
'chart-4': '#F59E0B',  // warning amber
'chart-5': '#0F766E',  // deep teal (salary band — matches salary-sensitive)
'chart-6': '#0EA5E9',  // sky (AI / info series)
'chart-7': '#64748B',  // slate (neutral)
'chart-8': '#DC2626',  // danger (reserved last, semantic only)
```
- `chart-1` and `chart-6` are both blue-family but well separated in luminance (`#0739B9` very dark vs `#0EA5E9` bright) — acceptable, and shape encoding (§3.7) remains mandatory.
- `chart-2 #9529F4` and `chart-5 #0F766E` are far apart in hue (violet vs teal) — safe neighbors.
- Red/Green-Circle dashboard still MUST add shape (filled/outlined/striped) — color is never the sole channel.

---

## 6. Logo usage per surface

Assets live in `frontend/src/assets/brand/`. Two monogram marks matter most: `hrl-mark-gradient.svg` (blue→magenta HRL monogram, for light backgrounds) and `hrl-mark-white.svg` (solid white HRL, for dark/gradient backgrounds). Horizontal lockups (`hrl-horizontal-lockup.svg`, `hrl-horizontal-1/2.svg`) include the "HR LABORATORIES" wordmark + tagline; stacked variants for square-ish areas.

| Surface | Asset | Size / spec | Notes |
|---------|-------|-------------|-------|
| **TopBar** (light `surface` bg) | `hrl-mark-gradient.svg` | 28–32 px tall, mark only | **Replaces the current CSS `HRL` span.** Mark only (no wordmark) — TopBar is space-constrained next to the tenant/project selectors. Clickable → Portfolio Dashboard (keep existing link + aria-label). |
| **Sidebar collapsed (72/64px)** | `hrl-mark-gradient.svg` | 24–28 px, centered | Mark only. (Currently the sidebar has no logo at top; optional to add the mark in the collapse-toggle row. Low priority.) |
| **Sidebar expanded** (optional) | `hrl-horizontal-1.svg` or `-lockup` | ≤ 140 px wide, ≤ 28 px tall | Optional: full lockup at top of expanded sidebar. If added, swap to mark-only when `collapsed`. |
| **LoginPage hero** (dark gradient bg) | `hrl-mark-white.svg` + tagline text | mark 48–64 px tall | White mark on the `--gradient-brand` hero panel. Pair with "HR LABORATORIES" + tagline "People. Systems. Results." rendered as text (i18n-keyed; tagline may stay English as a brand constant — confirm with PO). |
| **Favicon** | new `favicon.svg` from `hrl-mark-gradient.svg` | 48×48 viewBox | Replace the current generic purple-arrow `public/favicon.svg` with the HRL mark (cropped to the monogram, padded for clearspace). Also update `<meta name="theme-color">` (see §7). |
| **Loading / empty / splash** | `hrl-mark-gradient.svg` | 32–40 px, muted (opacity ~60%) or in skeleton tint | Use the mark as the brand anchor in `LoadingState`/`EmptyState` heroes; keep line-art illustrations per §14 EmptyState rule — mark is the brand stamp, not a decorative illustration. |

**Min size & clearspace:**
- Monogram mark min render size: **20 px tall** (below this the gradient stripes muddy). Prefer ≥ 24 px in chrome.
- Clearspace = **0.5× mark height** on all sides (no other element intrudes).
- Never recolor the gradient mark; on dark/gradient/photographic backgrounds use `hrl-mark-white.svg` instead of recoloring.
- Never stretch; preserve aspect ratio from each SVG's `viewBox`.

---

## 7. Per-surface application plan (referencing real files)

All file paths absolute. Only **focus rings, TopBar logo, favicon, theme-color, and (optionally) the Login hero** are component/asset edits; everything else rebrands automatically through the token remap.

### 7.1 Token files (the load-bearing edits)
- `D:\2026\Лойиҳалар\Грейдинг\grading.hrlab.uz\frontend\tailwind.config.ts`
  - Replace `colors.primary` with §1 scale.
  - Replace `colors.accent` with §2 scale (note: add `200`/`400`/`700` keys not present today).
  - Replace `colors['salary-sensitive']` `#7C3AED`→`#0F766E` and `salary-sensitive-bg` `#F5F3FF`→`#ECFDF8` (§4).
  - Replace `colors['chart-1..8']` with §5 values.
  - Leave `locked`, `ai-suggestion`, `audit-alert`, `success/warning/danger/info`, neutrals, surface/bg/border, text-* **unchanged**.
  - `boxShadow.focus`: change `rgba(31, 79, 134, 0.25)` → **`rgba(7, 57, 185, 0.30)`** (brand blue `#0739B9` @ 30%). This is the keyboard focus ring (a11y, §3.5/§17).
  - Add `theme.extend.backgroundImage`: `brand: 'linear-gradient(135deg, #0739B9 0%, #6E2EE4 55%, #AB27FD 100%)'` and `'brand-wash': 'linear-gradient(160deg, #F4F1FB 0%, #FAF0FE 100%)'` → enables `bg-brand` / `bg-brand-wash` utilities (§3).

- `D:\2026\Лойиҳалар\Грейдинг\grading.hrlab.uz\frontend\src\styles\globals.css`
  - `--color-primary-500: #1f4f86` → `#0739b9`.
  - `--color-accent-500: #06b6d4` → `#9529f4`.
  - `--color-salary-sensitive: #7c3aed` → `#0f766e`.
  - `--color-chart-1..8`: update to §5 values (chart-1 `#0739b9`, chart-2 `#9529f4`, chart-3 `#10b981`, chart-4 `#f59e0b`, chart-5 `#0f766e`, chart-6 `#0ea5e9`, chart-7 `#64748b`, chart-8 `#dc2626`).
  - Add `--gradient-brand` and `--gradient-brand-wash` vars (§3) for non-Tailwind/SVG/chart consumers.
  - `*:focus-visible { box-shadow: 0 0 0 3px rgba(31,79,134,0.25) }` → `rgba(7,57,185,0.30)` (must match `boxShadow.focus`).
  - `body { background-color: #f8fafc }` stays (table-screen legibility); do not switch the global bg to lavender.

- `D:\2026\Лойиҳалар\Грейдинг\grading.hrlab.uz\frontend\index.html`
  - `<meta name="theme-color" content="#1F4F86">` → `#0739B9`.

### 7.2 `LoginPage.tsx`
`D:\2026\Лойиҳалар\Грейдинг\grading.hrlab.uz\frontend\src\pages\LoginPage.tsx`
- Today: plain centered `Card` on `bg-background`. Rebrand to the signature look:
  - Wrap page in `bg-brand-wash` (or a split layout: gradient hero left/top, white card right/bottom on ≥1024px).
  - Add a hero panel using `bg-brand` with `hrl-mark-white.svg` + "HR LABORATORIES" + tagline (white text).
  - Add 2–3 concentric gradient corner rings (`aria-hidden`, behind content) per §3.4.
  - The "Sign in" (`auth.sign_in`) button is the one **hero CTA** that MAY use `bg-brand` gradient (or stay solid `primary` — both on-brand; pick gradient for the hero moment).
  - Keep dev-auth buttons (`as_super_admin`/`consultant`/`viewer`) as the existing solid/secondary/ghost variants — they auto-rebrand via `primary`.
  - `LanguageSwitcher` stays top-right; ensure contrast if it sits over the gradient (use white/`accent-100` text variant there).

### 7.3 `TopBar.tsx`
`D:\2026\Лойиҳалар\Грейдинг\grading.hrlab.uz\frontend\src\shared\components\layout\TopBar.tsx`
- Replace the `<span class="...bg-primary-500...">HRL</span>` placeholder (lines ~27–32) with an `<img>`/inline SVG of `hrl-mark-gradient.svg` at 28–32 px (keep the wrapping `<Link>` + `aria-label`).
- Keep the **tenant fingerprint bar** (`h-1.5` colored bar) as-is — it is a *tenant* signal, intentionally independent of brand color, and must remain a per-tenant hue (do not replace with brand gradient; that would defeat cross-tenant differentiation per §5.2/§7).
- No other change; selectors, language, user menu rebrand via tokens.

### 7.4 `Sidebar.tsx`
`D:\2026\Лойиҳалар\Грейдинг\grading.hrlab.uz\frontend\src\shared\components\layout\Sidebar.tsx`
- **No code change required for color** — the active-route classes already use `bg-primary-50 text-primary-700 border-l-2 border-primary-500` (line ~262), which now resolve to brand blue automatically. Verify visually that `primary-50 #EEF2FD` + `primary-700 #06277F` reads well (it does, ≈12:1).
- Optional: add `hrl-mark-gradient.svg` in the collapse-toggle header row (collapsed) / `hrl-horizontal-1.svg` (expanded) per §6.
- Locked-item slate (`text-text-muted`, `Lock` icon, `LockedBadge`) stays — `locked` token unchanged.

### 7.5 Primary buttons
`D:\2026\Лойиҳалар\Грейдинг\grading.hrlab.uz\frontend\src\shared\components\ui\Button.tsx`
- **No change.** `variant="primary"` uses `bg-primary-500 hover:bg-primary-600 active:bg-primary-700` → now brand blue/hover/pressed automatically. This is the chosen default (solid brand-blue, §3.3). Gradient is opt-in via `className="bg-brand"` only on hero CTAs (Login).
- `disabled:bg-primary-200` now `#AEC0F4` — verify it reads as clearly disabled (it does; low-chroma light blue).

### 7.6 Focus ring / links / active states
- Focus ring: the two hardcoded rgba values in `tailwind.config.ts` `boxShadow.focus` and `globals.css` `:focus-visible` must both move to brand blue `rgba(7,57,185,0.30)` (§7.1). They must stay in sync.
- Links: any `text-primary-*`/`text-accent-*` link utilities now resolve to brand blue/violet. For accent links on light, prefer `text-accent-700` (`#7A1FC9`) for AA.
- Active project underline in TopBar (§5.2, `accent-500`) now brand violet `#9529F4` — on-brand and distinct from the blue primary chrome.

### 7.7 Salary-masked cells, locked badges, AI panels, audit alerts
- All keep their iconography and labels. Only `salary-sensitive` hue changes (violet→teal, §4). FE makes **no component edits** here — the `salary-sensitive`/`salary-sensitive-bg` token remap flows into `SalaryValue`, `SensitiveDataMask`, and the `salary-protected` StatusBadge automatically. Verify the teal badge passes AA (it does, §4).
- `LockedMethodologyHeader`, `LockedGradeStructureHeader`, `AIRecommendationPanel`, audit alert rows: unchanged (their tokens are unchanged).

---

## 8. Edits to `docs/mvp1/07-design-foundation.md §3` (keep token-driven, no hardcoded colors)

So the design doc stays the single source of truth, the FE updates §3.1 (color tokens), §3.6 (badge tones reference salary-sensitive — value changes, mapping unchanged), and §3.7 (chart palette) to match this file. Concretely:

- **§3.1** — replace the `primary` block (§1 here), the `accent` block (§2 here), `salary-sensitive`/`salary-sensitive-bg` (`#7C3AED`/`#F5F3FF` → `#0F766E`/`#ECFDF8`). Add a one-line note: "Brand = HR LABORATORIES; gradient `--gradient-brand`; see `07b-hrl-brand-tokens.md`." Leave `locked`/`ai-suggestion`/`audit-alert`/semantic/neutral/text unchanged. Update the §3.1 "Notes" bullet that says salary-sensitive is violet → "salary-sensitive is reserved deep teal, deliberately off the blue→magenta brand axis so it never reads as brand accent."
- **§3.5** — update `shadow-focus` to `0 0 0 3px rgba(7,57,185,0.25)` (note: doc currently says 0.25; runtime uses 0.30 — align both to one value; recommend 0.30 for visibility, update the doc to 0.30).
- **§3.6** — `salary-protected` tone row still references `salary-sensitive*` tokens (no text change needed; the value behind them changed in §3.1).
- **§3.7** — replace the 8 `chart-*` values with §5 here, and update the inline comments (chart-1 brand blue, chart-2 brand violet, chart-5 teal salary band).
- Add a short pointer at the top of §3: "Color values are overridden by the HRL brand remap — see `docs/mvp1/07b-hrl-brand-tokens.md` (authoritative for brand color)."

No component file in the repo should contain a hardcoded brand hex after this — all brand color flows through `primary`/`accent`/`salary-sensitive`/`chart-*`/`--gradient-brand`.

---

## 9. Acceptance criteria (for the FE PR + QA)

1. `primary-500` renders `#0739B9` everywhere (buttons, sidebar active, links); no `#1F4F86` remains in built CSS.
2. `accent-500` renders `#9529F4`; old cyan `#06B6D4` appears only as `chart-6`'s sky `#0EA5E9`? No — cyan is fully retired; verify no `#06B6D4` outside chart palette (and chart uses `#0EA5E9`, not the old cyan).
3. Keyboard focus ring is brand blue `rgba(7,57,185,0.30)` in both Tailwind and `globals.css`; values match.
4. `salary-sensitive` is teal `#0F766E`; the `salary-protected` badge and `SalaryValue` masked state are visibly **non-purple** and pass AA; shield/lock icons intact.
5. `locked`, `ai-suggestion`, `audit-alert`, `success/warning/danger` are unchanged hex.
6. TopBar shows the `hrl-mark-gradient` SVG (not the CSS "HRL" span); favicon and theme-color are the HRL mark/blue.
7. LoginPage shows the gradient hero + white mark + tagline + corner rings; Sign-in is the only gradient CTA.
8. Charts render 8 distinguishable series; Red/Green-Circle still encodes shape, not color alone.
9. `bg-brand` / `bg-brand-wash` utilities exist and resolve to the §3 gradients; `--gradient-brand` CSS var available to charts/SVG.
10. WCAG AA: white-on-primary-500 ≥ 4.5:1 (≈8.5), sidebar active text ≥ 4.5:1 (≈12), salary teal badge ≥ 4.5:1. No new contrast regressions.
11. No component `.tsx` contains a hardcoded brand hex; all brand color is token-driven.
