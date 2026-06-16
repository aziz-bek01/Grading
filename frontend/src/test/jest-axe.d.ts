/**
 * Vitest type augmentation for the jest-axe matcher.
 *
 * jest-axe ships its `toHaveNoViolations` ambient type against `jest`'s
 * `expect`, not vitest's. We register the matcher at runtime in
 * `src/test/setup.ts` and declare its signature here so TypeScript recognises
 * `expect(results).toHaveNoViolations()` under vitest's `Assertion` interface.
 *
 * Test-only: this declaration is scoped to the test runtime and is never part
 * of the production type-check surface for app code.
 */
import 'vitest';

interface CustomMatchers<R = unknown> {
  toHaveNoViolations(): R;
}

declare module 'vitest' {
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type
  interface Assertion<T = unknown> extends CustomMatchers<T> {}
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type
  interface AsymmetricMatchersContaining extends CustomMatchers {}
}
