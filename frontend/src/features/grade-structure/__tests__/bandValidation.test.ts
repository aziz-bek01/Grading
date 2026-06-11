import { describe, expect, it } from 'vitest';
import { hasErrors, lookupGradeForScore, validateBands } from '../lib/bandValidation';
import type { Grade } from '../types';

function band(min: number, max: number) {
  return { id: `b-${min}-${max}`, grade_id: '', min_score: min, max_score: max };
}

function grade(n: number, b?: { min: number; max: number } | null): Grade {
  return {
    id: `g-${n}`,
    grade_structure_id: 's',
    grade_number: n,
    name_i18n: { 'ru-RU': `Грейд ${n}` },
    sort_order: n - 1,
    band: b ? band(b.min, b.max) : null,
  };
}

describe('validateBands', () => {
  it('returns no overlaps and no gaps for a clean ascending range', () => {
    const grades: Grade[] = [
      grade(1, { min: 0, max: 100 }),
      grade(2, { min: 100.0001, max: 200 }),
      grade(3, { min: 200.0001, max: 300 }),
    ];
    const r = validateBands(grades);
    expect(r.overlaps).toEqual([]);
    expect(r.gaps).toEqual([]);
    expect(r.missingBands).toEqual([]);
    expect(r.invalidBands).toEqual([]);
    expect(hasErrors(r, 'STRICT_NO_GAPS')).toBe(false);
  });

  it('detects an overlap when G2 starts inside G1', () => {
    const r = validateBands([
      grade(1, { min: 0, max: 105 }),
      grade(2, { min: 100, max: 200 }),
    ]);
    expect(r.overlaps).toHaveLength(1);
    const o = r.overlaps[0];
    expect(o.aGradeNumber).toBe(1);
    expect(o.bGradeNumber).toBe(2);
    expect(o.fromScore).toBe(100);
    expect(o.toScore).toBe(105);
    expect(hasErrors(r, 'ALLOW_GAPS_WARN')).toBe(true);
  });

  it('detects a gap between G5 and G6', () => {
    const r = validateBands([
      grade(5, { min: 400, max: 499 }),
      grade(6, { min: 500, max: 600 }),
    ]);
    expect(r.gaps).toHaveLength(1);
    expect(r.gaps[0].belowGradeNumber).toBe(5);
    expect(r.gaps[0].aboveGradeNumber).toBe(6);
    // gap-only is an ERROR under STRICT and a WARNING under ALLOW
    expect(hasErrors(r, 'STRICT_NO_GAPS')).toBe(true);
    expect(hasErrors(r, 'ALLOW_GAPS_WARN')).toBe(false);
  });

  it('reports missing bands without crashing', () => {
    const r = validateBands([grade(1, null), grade(2, { min: 100, max: 200 })]);
    expect(r.missingBands).toEqual([1]);
  });

  it('flags invalid bands where min > max', () => {
    const r = validateBands([grade(1, { min: 200, max: 100 })]);
    expect(r.invalidBands).toEqual([1]);
    expect(hasErrors(r, 'ALLOW_GAPS_WARN')).toBe(true);
  });

  it('considers boundaries inclusive: G1.max === G2.min is an overlap', () => {
    const r = validateBands([
      grade(1, { min: 0, max: 100 }),
      grade(2, { min: 100, max: 200 }),
    ]);
    expect(r.overlaps).toHaveLength(1);
  });
});

describe('lookupGradeForScore', () => {
  const grades: Grade[] = [
    grade(1, { min: 0, max: 50 }),
    grade(2, { min: 50.0001, max: 100 }),
    grade(3, { min: 100.0001, max: 200 }),
  ];

  it('finds the correct grade when the score is in range', () => {
    expect(lookupGradeForScore(grades, 75)?.gradeNumber).toBe(2);
    expect(lookupGradeForScore(grades, 0)?.gradeNumber).toBe(1);
    expect(lookupGradeForScore(grades, 200)?.gradeNumber).toBe(3);
  });

  it('returns null when the score is out of every range', () => {
    expect(lookupGradeForScore(grades, 1000)).toBeNull();
    expect(lookupGradeForScore(grades, -5)).toBeNull();
  });
});
