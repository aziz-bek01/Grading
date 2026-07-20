import type { Factor, FactorLevel, ScoringMode } from '../types';

/**
 * Point value a level is worth on screen, per scoring mode:
 *
 *   DIRECT_POINTS / WEIGHTED_POINTS: the authored `level.points`
 *     (meaningful as-is; WEIGHTED_POINTS only normalises it by
 *     max_points × weight at total-computation time — backend
 *     EvaluationScoringEngine).
 *   WEIGHTED_SCALE: the authored `level.scale_value` AS-IS — the same
 *     number the methodology editor's "Шкала қиймати" field holds. NOT
 *     multiplied by the factor weight (product-owner decision: the weight
 *     is a factor-wide property; the per-level badge must show the plain
 *     scale value; the engine applies weight × scale_value only when
 *     computing totals). In this mode `level.points` is unused and stays
 *     0, so rendering it showed "0" next to EVERY level.
 *
 * Every UI that surfaces a level's point worth (LevelDropSelect's
 * admin-only badge, EvaluationMatrix level cards) MUST go through this.
 * When `mode` is undefined (version not loaded yet) falls back to raw
 * points — the pre-existing behaviour.
 */
export function effectiveLevelPoints(
  mode: ScoringMode | undefined,
  _factor: Factor,
  level: FactorLevel,
): number {
  if (mode === 'WEIGHTED_SCALE') {
    return round2(level.scale_value ?? 0);
  }
  return level.points ?? 0;
}

/** Display form: max 2 decimals, trailing zeros trimmed ("100", "7.5"). */
export function formatLevelPoints(value: number): string {
  return String(Number(value.toFixed(2)));
}

function round2(value: number): number {
  return Math.round(value * 100) / 100;
}
