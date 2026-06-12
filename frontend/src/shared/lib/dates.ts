/**
 * Date formatting helpers shared across features.
 *
 * The real backend serialises timestamps as ISO-8601 strings, but with
 * `@JsonInclude(NON_NULL)` a missing timestamp is OMITTED entirely — so
 * consumers can receive `undefined`/`null`/`""`. `new Date(undefined)` yields
 * an "Invalid Date" whose `.toLocaleString()` renders the literal string
 * "Invalid Date". `formatDateSafe` guards that path and returns a caller-
 * supplied placeholder (a localized dash) instead.
 */

/**
 * Format an ISO timestamp for display, returning `fallback` for any
 * undefined / null / empty / unparseable value.
 */
export function formatDateSafe(
  value: string | null | undefined,
  locale: string,
  fallback = '—',
): string {
  if (!value) return fallback;
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return fallback;
  return d.toLocaleString(locale);
}
