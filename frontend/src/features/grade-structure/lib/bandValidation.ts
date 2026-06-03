/**
 * Client-side preview of band overlap / gap detection.
 *
 * Mirrors the backend algorithm (BandValidationService): inclusive [min,max]
 * ranges, with 0.0001 epsilon between adjacent grades being considered a
 * "no gap" boundary (since BigDecimal numeric(18,4) is the canonical
 * precision). Backend remains the source of truth — these checks only
 * drive UI hints (banner colour, row validation pip).
 */
import type { Grade, GradeGapPolicy } from '../types';

export interface OverlapIssue {
  type: 'overlap';
  /** Lower grade number. */
  aGradeNumber: number;
  bGradeNumber: number;
  /** The numeric interval that overlaps. */
  fromScore: number;
  toScore: number;
}

export interface GapIssue {
  type: 'gap';
  /** Grade number on the lower side of the gap. */
  belowGradeNumber: number;
  /** Grade number on the upper side. */
  aboveGradeNumber: number;
  fromScore: number;
  toScore: number;
}

export interface ValidationReport {
  overlaps: OverlapIssue[];
  gaps: GapIssue[];
  missingBands: number[]; // grade numbers without a band
  invalidBands: number[]; // grade numbers whose min > max
}

const EPSILON = 0.0001;

function round4(n: number): number {
  return Math.round(n * 10_000) / 10_000;
}

/**
 * Returns issue lists. Caller decides severity: gaps are
 *   - error when gap_policy === STRICT_NO_GAPS
 *   - warning when gap_policy === ALLOW_GAPS_WARN
 * Overlaps and invalid (min>max) are ALWAYS error.
 */
export function validateBands(grades: Grade[]): ValidationReport {
  const overlaps: OverlapIssue[] = [];
  const gaps: GapIssue[] = [];
  const missingBands: number[] = [];
  const invalidBands: number[] = [];

  // Sort by grade_number ascending — bands should monotonically increase.
  const sorted = [...grades].sort((a, b) => a.grade_number - b.grade_number);

  // Capture missing / invalid first; they cannot participate in overlap math.
  const withBand: Grade[] = [];
  for (const g of sorted) {
    if (!g.band) {
      missingBands.push(g.grade_number);
      continue;
    }
    if (g.band.min_score > g.band.max_score) {
      invalidBands.push(g.grade_number);
      continue;
    }
    withBand.push(g);
  }

  for (let i = 0; i < withBand.length; i++) {
    for (let j = i + 1; j < withBand.length; j++) {
      const a = withBand[i].band!;
      const b = withBand[j].band!;
      const overlapFrom = Math.max(a.min_score, b.min_score);
      const overlapTo = Math.min(a.max_score, b.max_score);
      if (overlapFrom <= overlapTo) {
        overlaps.push({
          type: 'overlap',
          aGradeNumber: withBand[i].grade_number,
          bGradeNumber: withBand[j].grade_number,
          fromScore: round4(overlapFrom),
          toScore: round4(overlapTo),
        });
      }
    }
  }

  // Gap detection on adjacent grades (after grade_number sort)
  for (let i = 0; i < withBand.length - 1; i++) {
    const lower = withBand[i].band!;
    const upper = withBand[i + 1].band!;
    // gap exists if there is a score X with lower.max < X < upper.min
    // accounting for 4-decimal precision: gap means upper.min - lower.max > EPSILON
    if (upper.min_score - lower.max_score > EPSILON + 1e-9) {
      gaps.push({
        type: 'gap',
        belowGradeNumber: withBand[i].grade_number,
        aboveGradeNumber: withBand[i + 1].grade_number,
        fromScore: round4(lower.max_score + EPSILON),
        toScore: round4(upper.min_score - EPSILON),
      });
    }
  }

  return { overlaps, gaps, missingBands, invalidBands };
}

export function hasErrors(report: ValidationReport, gapPolicy: GradeGapPolicy): boolean {
  if (report.overlaps.length > 0) return true;
  if (report.invalidBands.length > 0) return true;
  if (gapPolicy === 'STRICT_NO_GAPS' && report.gaps.length > 0) return true;
  return false;
}

/** Local preview of "what grade does this score map to?" — mirrors backend. */
export function lookupGradeForScore(
  grades: Grade[],
  score: number,
): { gradeNumber: number; gradeId: string; gradeBandId: string } | null {
  for (const g of grades) {
    if (!g.band) continue;
    if (score >= g.band.min_score && score <= g.band.max_score) {
      return { gradeNumber: g.grade_number, gradeId: g.id, gradeBandId: g.band.id };
    }
  }
  return null;
}
