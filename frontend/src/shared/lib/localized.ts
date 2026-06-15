import type { Locale, LocalizedString } from '@/shared/types/common';

/**
 * Pick a label from a LocalizedString. Falls back to:
 *  1. requested locale,
 *  2. ru-RU (default product locale),
 *  3. en-US,
 *  4. first non-empty value,
 *  5. the optional `fallback` (e.g. an entity code or a localized "Untitled"
 *     placeholder) so a heading/cell is never rendered empty,
 *  6. empty string (when no `fallback` is supplied — preserves the original
 *     backward-compatible behaviour for existing callers).
 *
 * The `fallback` arg is optional and trailing, so every existing two-arg call
 * keeps its current semantics.
 */
export function pickLocalized(
  value: LocalizedString | undefined,
  locale: string,
  fallback?: string,
): string {
  if (value) {
    const candidates: (Locale | string)[] = [locale, 'ru-RU', 'en-US'];
    for (const key of candidates) {
      const v = value[key as Locale];
      if (v && v.trim().length > 0) return v;
    }
    for (const v of Object.values(value)) {
      if (v && v.trim().length > 0) return v;
    }
  }
  return fallback ?? '';
}
