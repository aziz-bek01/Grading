/**
 * Shared Zod builders for the localized-string (`{ 'ru-RU': ..., ... }`)
 * shape repeated across feature schemas (methodology / factor / factor-level
 * / grade-structure / job-profile / project / position / department).
 *
 * Two DISTINCT validation semantics existed in the codebase and both are
 * preserved as separate builders (never merged — the rules are genuinely
 * different):
 *
 *  - {@link localizedStringRequiredPrimary} — the PRIMARY locale (`ru-RU`) is
 *    specifically required (`min(1)`); the other three locales are optional.
 *    Used by methodology / factor / factor-level / grade-structure /
 *    job-profile forms ("PRIMARY_LOCALE_INCOMPLETE" backend rule).
 *  - {@link localizedStringAnyRequired} — every locale is individually
 *    optional, but at least ONE of the four must be a non-empty string
 *    (`.refine`). Used by project / position / department forms.
 *
 * `maxLength` and the required/refine error message are the only
 * per-caller variance (mirrors what differed between the original
 * hand-rolled copies) and are always passed explicitly by the caller.
 */
import { z } from 'zod';
import { SUPPORTED_LOCALES } from '@/shared/i18n';

export const LOCALE_KEYS = SUPPORTED_LOCALES;

/** Reusable four-locale enum — mirrors the repeated `z.enum([...])` locale pickers. */
export const localeEnum = z.enum(LOCALE_KEYS);

const localizedShape = (maxLength: number) =>
  Object.fromEntries(LOCALE_KEYS.map((k) => [k, z.string().trim().max(maxLength).optional()])) as Record<
    (typeof LOCALE_KEYS)[number],
    z.ZodOptional<z.ZodString>
  >;

export interface LocalizedStringOptions {
  maxLength: number;
  /**
   * Adds `.passthrough()` so unknown keys on the parsed object survive
   * (matches the methodology/factor/factor-level/grade-structure/job-profile
   * schemas, all of which opted into this). Defaults to `false` — matches the
   * project/position/department schemas, which never passed `.passthrough()`.
   */
  passthrough?: boolean;
}

/** Every locale optional, each capped at `maxLength`. No cross-field rule. */
export function localizedStringOptional({ maxLength, passthrough }: LocalizedStringOptions) {
  const obj = z.object(localizedShape(maxLength));
  return passthrough ? obj.passthrough() : obj;
}

/**
 * `ru-RU` required (`min(1, requiredMessage)`), the other three locales
 * optional. Each locale capped at `maxLength`.
 */
export function localizedStringRequiredPrimary({
  maxLength,
  passthrough,
  requiredMessage = 'validation_primary_required',
}: LocalizedStringOptions & { requiredMessage?: string }) {
  const shape = {
    ...localizedShape(maxLength),
    'ru-RU': z.string().trim().min(1, requiredMessage).max(maxLength),
  };
  const obj = z.object(shape);
  return passthrough ? obj.passthrough() : obj;
}

/**
 * Every locale optional, but at least one of the four must be a non-empty
 * string (checked via `.refine`).
 */
export function localizedStringAnyRequired({
  maxLength,
  message = 'validation_primary_name_required',
}: {
  maxLength: number;
  message?: string;
}) {
  return z.object(localizedShape(maxLength)).refine(
    (v) => LOCALE_KEYS.some((k) => v[k] && v[k]!.trim().length > 0),
    { message },
  );
}
