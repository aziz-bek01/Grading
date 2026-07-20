import type { Factor, FactorLevel, ScoringMode } from '../types';

/**
 * Point value a level is worth on screen, per scoring mode:
 *
 *   DIRECT_POINTS / WEIGHTED_POINTS: the authored `level.points`
 *     (meaningful as-is; WEIGHTED_POINTS only normalises it by
 *     max_points × weight at total-computation time — backend
 *     EvaluationScoringEngine).
 *   WEIGHTED_SCALE: `weight × level.scale_value` — the engine's actual
 *     per-factor contribution. In this mode `level.points` is unused and
 *     stays 0, so rendering it showed "0" next to EVERY level.
 *
 * Every UI that surfaces a level's point worth (LevelDropSelect's
 * admin-only badge, EvaluationMatrix level cards) MUST go through this.
 * When `mode` is undefined (version not loaded yet) falls back to raw
 * points — the pre-existing behaviour.
 */
export function effectiveLevelPoints(
  mode: ScoringMode | undefined,
  factor: Factor,
  level: FactorLevel,
): number {
  if (mode === 'WEIGHTED_SCALE') {
    return round2((factor.weight ?? 0) * (level.scale_value ?? 0));
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
