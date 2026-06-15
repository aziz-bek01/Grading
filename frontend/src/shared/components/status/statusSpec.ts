import type { StatusTone } from './StatusBadge';

/**
 * Resolves a backend status against a per-entity `{ tone, label }` map without
 * ever throwing on an unexpected value. The ~15 per-entity *StatusBadge
 * components used to do `map[status].tone` directly, so a status the FE didn't
 * know about (a new backend enum value, a stale cache) crashed the page —
 * caught only by the route boundary. Routing every map lookup through this
 * helper turns that into a neutral badge that shows the raw status verbatim, so
 * the UI degrades gracefully instead of erroring.
 *
 * `label` may be a translated string or, for unknown statuses, the raw status
 * code itself (which is what we want to surface for support/debugging).
 *
 * Lives in its own module (not StatusBadge.tsx) so the badge file only exports
 * a component — required by the `react-refresh/only-export-components` rule.
 */
export function resolveStatusSpec<S extends string>(
  map: Partial<Record<S, { tone: StatusTone; label: string }>>,
  status: S,
): { tone: StatusTone; label: string } {
  const spec = map[status];
  if (spec) return spec;
  return { tone: 'draft', label: String(status) };
}
