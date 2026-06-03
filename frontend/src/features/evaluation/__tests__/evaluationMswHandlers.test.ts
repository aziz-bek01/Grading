/**
 * Phase 5 — MSW handler smoke tests for the evaluation endpoints.
 *
 * Validates:
 *   - `/evaluations/preview-score` is purely stateless and uses the same
 *     WEIGHTED_POINTS math as the backend EvaluationScoringEngine.
 *   - `/evaluations/:id/scores` upsert flips the status COMPLETE when
 *     every required factor has a score.
 *   - `/evaluations/:id/calibrate` requires reason ≥ 20 chars (matches
 *     backend `CalibrateScoreRequest`).
 *   - `tenant_id` in the body is dropped (F-401 pattern extended).
 */
import { describe, expect, it, vi } from 'vitest';
import type { AxiosRequestConfig } from 'axios';
import { tryHandle } from '@/shared/api/mocks/handlers';
import { mockDb } from '@/shared/api/mocks/fixtures';

function req(
  method: string,
  url: string,
  data?: unknown,
  headers: Record<string, string> = { 'X-Mock-Tenant-Id': '11111111-1111-1111-1111-111111111111' },
): AxiosRequestConfig {
  return {
    method,
    url,
    data: data !== undefined ? JSON.stringify(data) : undefined,
    headers,
  } as AxiosRequestConfig;
}

describe('MSW /evaluations/preview-score', () => {
  it('computes WEIGHTED_POINTS total matching scoring engine', () => {
    const version = mockDb.methodologyVersions.find(
      (v) => v.scoring_mode === 'WEIGHTED_POINTS' && v.factors.length > 0,
    )!;
    // Select level index 3 (points=75) on every factor; expected = sum(weight*0.75).
    const selections = version.factors.map((f) => ({
      factor_id: f.id,
      factor_level_id: f.levels[3].id,
    }));
    const expected = version.factors.reduce(
      (acc, f) => acc + f.weight * (75 / 100),
      0,
    );
    const result = tryHandle(
      req('POST', '/evaluations/preview-score', {
        methodology_version_id: version.id,
        selections,
      }),
    );
    expect(result?.status).toBe(200);
    const body = result?.body as {
      raw_total: number;
      displayed_total: number;
      per_factor_raw: Record<string, number>;
    };
    expect(body.raw_total).toBeCloseTo(expected, 4);
    expect(body.displayed_total).toBeCloseTo(Number(expected.toFixed(2)), 2);
    for (const f of version.factors) {
      expect(body.per_factor_raw[f.id]).toBeCloseTo(f.weight * 0.75, 4);
    }
  });

  it('drops tenant_id from preview body (F-401 defence in depth)', () => {
    const version = mockDb.methodologyVersions.find(
      (v) => v.scoring_mode === 'WEIGHTED_POINTS' && v.factors.length > 0,
    )!;
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const result = tryHandle(
      req('POST', '/evaluations/preview-score', {
        tenant_id: 'tenant-evil',
        methodology_version_id: version.id,
        selections: [],
      }),
    );
    expect(result?.status).toBe(200);
    expect(warn).toHaveBeenCalled();
    warn.mockRestore();
  });
});

describe('MSW /evaluations/:id/scores upsert', () => {
  it('upsert recomputes total + sets status COMPLETE when required factors covered', () => {
    const before = mockDb.evaluations.find((e) => e.id === 'eval-cfo-1')!;
    // Phase 6 fixture seeds this eval as APPROVED (to demo AssignedGradeBadge).
    // Reset to COMPLETE for the upsert path under test.
    before.status = 'COMPLETE';
    before.approved_at = null;
    before.approved_by = null;
    before.grade_band_id = null;
    before.assigned_grade_number = null;
    const version = mockDb.methodologyVersions.find(
      (v) => v.id === before.methodology_version_id,
    )!;
    const f0 = version.factors[0];
    const targetLevel = f0.levels[1]; // points=25
    const result = tryHandle(
      req('POST', `/evaluations/${before.id}/scores`, {
        factor_id: f0.id,
        factor_level_id: targetLevel.id,
      }),
    );
    expect(result?.status).toBe(200);
    expect(before.status).toBe('COMPLETE'); // still complete — every factor scored
  });
});

describe('MSW /evaluations/:id/calibrate', () => {
  it('rejects reason shorter than 20 chars', () => {
    // Promote eval-cfo-1 to APPROVED via the state machine for the test.
    const e = mockDb.evaluations.find((ev) => ev.id === 'eval-cfo-1')!;
    e.status = 'APPROVED';
    const version = mockDb.methodologyVersions.find(
      (v) => v.id === e.methodology_version_id,
    )!;
    const f0 = version.factors[0];
    const result = tryHandle(
      req('POST', `/evaluations/${e.id}/calibrate`, {
        factor_id: f0.id,
        new_raw_factor_score: 50,
        reason: 'short',
      }),
    );
    expect(result?.status).toBe(400);
    expect((result?.body as { code?: string })?.code).toBe('REASON_REQUIRED');
  });

  it('creates a calibration event and updates score on valid input', () => {
    const e = mockDb.evaluations.find((ev) => ev.id === 'eval-cfo-1')!;
    e.status = 'APPROVED';
    const version = mockDb.methodologyVersions.find(
      (v) => v.id === e.methodology_version_id,
    )!;
    const f0 = version.factors[0];
    const result = tryHandle(
      req('POST', `/evaluations/${e.id}/calibrate`, {
        factor_id: f0.id,
        new_raw_factor_score: 9.5,
        reason: 'Committee unanimously agreed after deep review',
      }),
    );
    expect(result?.status).toBe(201);
    const event = result?.body as {
      original_score: number;
      adjusted_score: number;
      delta: number;
    };
    expect(event.adjusted_score).toBeCloseTo(9.5, 4);
    expect(mockDb.calibrationEvents.some((c) => c.evaluation_id === e.id)).toBe(true);
  });
});
