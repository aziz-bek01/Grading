/**
 * Multilingual field contract — mirrors backend `JobProfileLocales.ALL_LOCALES`
 * and Liquibase JSONB column shape (see security review F-308).
 *
 * Frontend MUST NOT accept arbitrary locale keys: a loose `Record<string, string>`
 * silently swallows typos and unbounded keys. Use `MultilingualField` (a
 * `Partial<Record<Locale, string>>`) everywhere multilingual content is typed.
 *
 * Re-exports `Locale` and `LocalizedString` from `common.ts` to give every
 * feature module a single canonical import path for i18n types.
 */
export type { Locale, LocalizedString } from './common';
export type { LocalizedString as MultilingualField } from './common';

import type { Locale } from './common';

/** All supported locales, in design-foundation order. Use for runtime iteration. */
export const SUPPORTED_LOCALES: readonly Locale[] = [
  'ru-RU',
  'uz-Cyrl-UZ',
  'uz-Latn-UZ',
  'en-US',
] as const;

/** Primary locale: required at submit time for backend `requirePrimaryLocaleFields`. */
export const PRIMARY_LOCALE: Locale = 'ru-RU';

/** Type guard for runtime validation against the 4 supported locale keys. */
export function isSupportedLocale(value: unknown): value is Locale {
  return typeof value === 'string' && (SUPPORTED_LOCALES as readonly string[]).includes(value);
}
