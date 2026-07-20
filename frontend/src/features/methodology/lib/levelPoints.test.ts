/**
 * Mode-aware level point display (regression: a WEIGHTED_SCALE methodology
 * showed "0" next to every level in the K-sheet LevelDropSelect because the
 * UI rendered the unused `level.points` field instead of
 * weight × scale_value).
 */
import { describe, it, expect } from 'vitest';
import type { Factor, FactorLevel } from '../types';
import { effectiveLevelPoints, formatLevelPoints } from './levelPoints';

function makeFactor(overrides: Partial<Factor> = {}): Factor {
  return {
    id: 'f-1',
    methodology_version_id: 'mv-1',
    code: '1',
    name_i18n: { 'ru-RU': 'Фактор' },
    weight: 10,
    max_points: 50,
    sort_order: 1,
    required: true,
    levels: [],
    ...overrides,
  } as Factor;
}

function makeLevel(overrides: Partial<FactorLevel> = {}): FactorLevel {
  return {
    id: 'l-1',
    factor_id: 'f-1',
    code: 'A',
    level_order: 1,
    points: 0,
    scale_value: 10,
    label_i18n: { 'ru-RU': 'A' },
    ...overrides,
  } as FactorLevel;
}

describe('effectiveLevelPoints', () => {
  it('WEIGHTED_SCALE: weight × scale_value (the Agrobank case — points is 0)', () => {
    const value = effectiveLevelPoints('WEIGHTED_SCALE', makeFactor(), makeLevel());
    expect(value).toBe(100); // 10 × 10, NOT the unused points field (0)
  });

  it('WEIGHTED_SCALE: null scale_value contributes 0 (engine parity)', () => {
    const value = effectiveLevelPoints(
      'WEIGHTED_SCALE',
      makeFactor(),
      makeLevel({ scale_value: undefined as unknown as number }),
    );
    expect(value).toBe(0);
  });

  it('DIRECT_POINTS / WEIGHTED_POINTS / undefined: authored points as-is', () => {
    const level = makeLevel({ points: 25 });
    expect(effectiveLevelPoints('DIRECT_POINTS', makeFactor(), level)).toBe(25);
    expect(effectiveLevelPoints('WEIGHTED_POINTS', makeFactor(), level)).toBe(25);
    expect(effectiveLevelPoints(undefined, makeFactor(), level)).toBe(25);
  });

  it('rounds the weighted-scale product to 2 decimals', () => {
    const value = effectiveLevelPoints(
      'WEIGHTED_SCALE',
      makeFactor({ weight: 7.5 }),
      makeLevel({ scale_value: 0.333 }),
    );
    expect(value).toBe(2.5); // 7.5 × 0.333 = 2.4975 → 2.5
  });
});

describe('formatLevelPoints', () => {
  it('trims trailing zeros and caps at 2 decimals', () => {
    expect(formatLevelPoints(100)).toBe('100');
    expect(formatLevelPoints(7.5)).toBe('7.5');
    expect(formatLevelPoints(2.4975)).toBe('2.5');
    expect(formatLevelPoints(0)).toBe('0');
  });
});
