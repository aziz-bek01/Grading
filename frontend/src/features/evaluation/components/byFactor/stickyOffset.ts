/**
 * Shared sticky-offset contract for the by-factor K-sheet view.
 *
 * The global TopBar is `h-14` (56px) + a `h-1.5` (6px) tenant-fingerprint
 * accent bar = 62px tall. Any element that should stick *below* the TopBar
 * must clear that height. We round up to `top-20` (80px) so there is a small
 * breathing gap and the stuck element never tucks under the header.
 *
 * The FactorTabs strip imports this single constant for its sticky offset so
 * it can never silently diverge from the rest of the K-sheet chrome (the
 * original bug was FactorTabs using `top-14` / 56px while a now-retired
 * sibling used `top-20` / 80px, which let the tabs slide ~6px under the
 * TopBar when stuck).
 *
 * The TopBar owns `z-20`; sticky content inside the page must stay *below*
 * the header so it never paints over the global controls. Hence `z-10`.
 */
export const BY_FACTOR_STICKY_TOP = 'top-20';

/** z-index for in-page sticky chrome (factor tabs) — below the TopBar (z-20). */
export const BY_FACTOR_STICKY_Z = 'z-10';

/**
 * Sticky offset for the filter bar, which sits BELOW the methodology-header +
 * factor-tabs strip (FE-9). The strip starts at {@link BY_FACTOR_STICKY_TOP}
 * (80px) and is ~56px tall (methodology line ~30px + tab row ~37px, rounded
 * for breathing room), so the filter bar sticks at ~136px = `top-[8.5rem]`.
 * Centralised here (single source) so a strip-height change is updated once.
 */
export const BY_FACTOR_FILTER_STICKY_TOP = 'top-[8.5rem]';
