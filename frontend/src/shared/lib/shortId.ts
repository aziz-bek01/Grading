/**
 * Shorten an identifier for display fallback.
 *
 * Across the app, several enriched responses (approval entity labels,
 * initiator / approver / decided-by names, workflow responsible users …)
 * are OMITTED by the backend when the referenced entity or user is missing,
 * unlabellable, or in another tenant (`@JsonInclude(NON_NULL)`). The UI must
 * then degrade gracefully: it shows a SHORT identifier (the first UUID
 * segment, i.e. the first 8 chars) instead of leaking a raw 36-char UUID as a
 * card title or actor name.
 *
 * This is the single shared helper for that fallback so no component
 * duplicates the truncation rule.
 *
 * Examples:
 *   shortId('40000000-0000-0000-0000-0000000000a2') === '40000000'
 *   shortId('abc')   === 'abc'   (already short → unchanged)
 *   shortId('')      === ''
 *   shortId(null)    === ''
 */
export function shortId(value: string | null | undefined): string {
  if (!value) return '';
  const trimmed = value.trim();
  if (trimmed.length === 0) return '';
  // First UUID segment (before the first dash). For non-UUID strings, fall
  // back to the first 8 chars so a long opaque id is never shown in full.
  const firstSegment = trimmed.split('-')[0];
  if (firstSegment.length > 0 && firstSegment.length <= 8) return firstSegment;
  return trimmed.slice(0, 8);
}
