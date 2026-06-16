import { configureAxe, type JestAxeConfigureOptions } from 'jest-axe';
import { expect } from 'vitest';

/**
 * Accessibility (a11y) test harness — Batch 6.
 *
 * Wraps `jest-axe` so any component/screen test can assert that the rendered
 * DOM is free of axe-core violations:
 *
 *   import { expectNoA11yViolations } from '@/test/a11y';
 *   const { container } = render(renderWithProviders(<Thing />));
 *   await expectNoA11yViolations(container);
 *
 * Notes / scope decisions:
 *
 * 1. `color-contrast` is DISABLED here. Vitest runs with `css: false` (see
 *    vitest.config.ts) and jsdom does not apply our Tailwind stylesheet, so
 *    every element computes to the jsdom default (transparent bg / black
 *    text). axe would therefore report bogus contrast failures (or, worse,
 *    pass spuriously) — the rule is meaningless without real computed styles.
 *    Contrast is instead governed at the DESIGN-TOKEN level (tailwind.config.ts)
 *    and verified by `src/test/__tests__/contrastTokens.test.ts`, which checks
 *    the actual hex pairs against the WCAG AA ratio. This is the documented,
 *    narrowly-scoped exception called for in the batch brief.
 *
 * 2. `region` is DISABLED for component-level tests because primitives are
 *    mounted in isolation (no surrounding <main>/landmark). Screen-level tests
 *    that render a full page mount inside the app shell and DO assert landmark
 *    structure via `expectNoA11yViolations(container, { region: true })`.
 *
 * axe is a DEV/test-only dependency (jest-axe is in devDependencies) and is
 * never imported from production code, so it cannot land in the prod bundle.
 */

const baseRules: NonNullable<JestAxeConfigureOptions['rules']> = {
  // See note (1) above — contrast is a token-level concern in jsdom.
  'color-contrast': { enabled: false },
};

const axe = configureAxe({ rules: baseRules });

interface A11yOptions {
  /**
   * Enable the landmark `region` rule (off by default for isolated primitives).
   * Pass `true` from full-screen tests rendered inside the app shell.
   */
  region?: boolean;
  /**
   * Additional axe rule overrides, merged last. Use sparingly and with a
   * comment explaining why a rule is being relaxed for a specific case.
   */
  rules?: JestAxeConfigureOptions['rules'];
}

/**
 * Run axe against `container` and assert zero violations. On failure, the
 * jest-axe matcher prints each violation (rule id, impact, help URL, and the
 * offending node) so the fix target is obvious.
 */
export async function expectNoA11yViolations(
  container: Element,
  options: A11yOptions = {},
): Promise<void> {
  const rules: NonNullable<JestAxeConfigureOptions['rules']> = {
    ...baseRules,
    region: { enabled: options.region === true },
    ...(options.rules ?? {}),
  };
  const results = await axe(container, { rules });
  expect(results).toHaveNoViolations();
}
