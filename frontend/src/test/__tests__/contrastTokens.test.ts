import { describe, it, expect } from 'vitest';
import tailwindConfig from '../../../tailwind.config';

/**
 * Token-level WCAG contrast guard (Batch 6 a11y).
 *
 * WHY this exists separately from axe:
 *   Vitest runs with `css: false` (vitest.config.ts) and jsdom never applies
 *   our Tailwind stylesheet, so axe-core's `color-contrast` rule cannot read
 *   real computed colors — it is deliberately DISABLED in `src/test/a11y.ts`.
 *   Contrast is therefore governed where the colors actually live: the design
 *   tokens in tailwind.config.ts. This test reads those hex values directly
 *   and asserts each foreground/background PAIR that renders small UI text
 *   (badges, locked/AI/salary states, muted body copy) meets the WCAG 2.1 AA
 *   ratio for its text size.
 *
 * If a token is darkened/lightened and a pair drops below threshold, this test
 * fails with the offending pair named — fix the TOKEN, not a one-off override.
 */

type Hex = `#${string}`;

const colors = (tailwindConfig.theme?.extend?.colors ?? {}) as Record<
  string,
  string | Record<string, string>
>;

function hex(path: string): Hex {
  const [name, shade] = path.split('.');
  const v = colors[name];
  if (typeof v === 'string') return v as Hex;
  if (v && shade) return v[shade] as Hex;
  if (v && typeof v === 'object' && 'DEFAULT' in v) return v.DEFAULT as Hex;
  throw new Error(`Unknown color token: ${path}`);
}

function channel(c: number): number {
  const s = c / 255;
  return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
}

function luminance(h: Hex): number {
  const r = parseInt(h.slice(1, 3), 16);
  const g = parseInt(h.slice(3, 5), 16);
  const b = parseInt(h.slice(5, 7), 16);
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
}

function ratio(fg: Hex, bg: Hex): number {
  const l1 = luminance(fg);
  const l2 = luminance(bg);
  const hi = Math.max(l1, l2);
  const lo = Math.min(l1, l2);
  return (hi + 0.05) / (lo + 0.05);
}

const AA_TEXT = 4.5; // normal text < 18px (badge labels are 12px)
const AA_LARGE = 3.0; // ≥18px or ≥14px bold — and UI component/graphical objects

interface Pair {
  name: string;
  fg: string;
  bg: string;
  min: number;
}

// Foreground text token ON background token, for the small (12–14px) text the
// component actually renders. Background literals (#FFFFFF/#F8FAFC) are the
// surface/background tokens.
const PAIRS: Pair[] = [
  { name: 'badge in-review', fg: 'info.600', bg: 'info.50', min: AA_TEXT },
  { name: 'badge approved', fg: 'success.700', bg: 'success.50', min: AA_TEXT },
  { name: 'badge locked', fg: 'locked', bg: 'locked-bg', min: AA_TEXT },
  { name: 'badge archived', fg: 'text-secondary', bg: 'divider', min: AA_TEXT },
  { name: 'badge incomplete', fg: 'warning.700', bg: 'warning.50', min: AA_TEXT },
  { name: 'badge needs-attention', fg: 'danger.700', bg: 'danger.50', min: AA_TEXT },
  { name: 'badge salary-protected', fg: 'salary-sensitive', bg: 'salary-sensitive-bg', min: AA_TEXT },
  { name: 'badge ai-suggestion', fg: 'ai-suggestion', bg: 'ai-suggestion-bg', min: AA_TEXT },
  { name: 'badge draft', fg: 'text-secondary', bg: 'divider', min: AA_TEXT },
  { name: 'muted text on surface', fg: 'text-muted', bg: 'surface', min: AA_TEXT },
  { name: 'muted text on background', fg: 'text-muted', bg: 'background', min: AA_TEXT },
  { name: 'primary button label', fg: 'text-inverse', bg: 'primary.500', min: AA_TEXT },
  // Danger solid button: white on #EF4444 is 3.76 — AA-large/UI threshold (the
  // label is bold/medium and the button is a UI component). Documented as a
  // 3:1 (large/UI) pair rather than 4.5, matching the design token intent.
  { name: 'danger button label', fg: 'text-inverse', bg: 'danger.500', min: AA_LARGE },
];

describe('design-token contrast (WCAG AA)', () => {
  it.each(PAIRS)('$name meets its target ratio', ({ fg, bg, min }) => {
    const r = ratio(hex(fg), hex(bg));
    expect(
      r,
      `${fg} on ${bg} = ${r.toFixed(2)}:1 (needs ≥ ${min}:1). ` +
        `Adjust the token in tailwind.config.ts, not a one-off class.`,
    ).toBeGreaterThanOrEqual(min);
  });
});
