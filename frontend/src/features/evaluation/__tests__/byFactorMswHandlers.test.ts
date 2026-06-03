/**
 * MSW smoke tests for the by-factor (Bulk Evaluation) endpoints.
 *
 * Validates:
 *   - `GET /evaluations?groupBy=factor` returns rows with the expected
 *     shape and respects departmentId + onlyUnfilled filters.
 *   - `POST /evaluations/factor/:factorId/bulk-score` rejects short
 *     reasons (< 50 chars) and records failures for locked rows.
 *   - `POST /evaluations/factor/:factorId/bulk-submit` rejects short
 *     reasons (< 20 chars) and surfaces partial failures for
 *     incomplete rows.
 *   - `tenant_id` in the body is dropped (F-401 defence in depth).
 */
import { describe, expect, it, vi } from 'vitest';
import type { AxiosRequestConfig } from 'axios';
import { tryHandle } from '@/shared/api/mocks/handlers';
import { mockDb } from '@/shared/api/mocks/fixtures';
import type { PageEnvelope } from '@/shared/types/common';
import type { EvaluationByFactorRow } from '../types';

function req(
  method: string,
  url: string,
  data?: unknown,
  params?: Record<string, unknown>,
): AxiosRequestConfig {
  return {
    method,
    url,
    data: data !== undefined ? JSON.stringify(data) : undefined,
    params,
    headers: { 'X-Mock-Tenant-Id': '11111111-1111-1111-1111-111111111111' },
  } as AxiosRequestConfig;
}

describe('MSW GET /evaluations?groupBy=factor', () => {
  it('returns rows for every evaluation in the project for the selected factor', () => {
    const version = mockDb.methodologyVersions[0];
    const factorId = version.factors[0].id;
    const result = tryHandle(
      req('GET', '/evaluations', undefined, {
        projectId: 'proj-acme-2026',
        groupBy: 'factor',
        factorId,
        page: 0,
        size: 100,
      }),
    );
    expect(result?.status).toBe(200);
    const body = result?.body as PageEnvelope<EvaluationByFactorRow>;
    expect(body.items.length).toBeGreaterThan(10);
    const row = body.items[0];
    expect(row).toHaveProperty('evaluation_id');
    expect(row).toHaveProperty('position_title');
    expect(row).toHaveProperty('department_name');
    expect(row).toHaveProperty('filled_factors_count');
    expect(row).toHaveProperty('total_factors_count');
    // `total_factors_count` always equals the version factor count.
    expect(row.total_factors_count).toBe(version.factors.length);
  });

  it('filters by departmentId', () => {
    const version = mockDb.methodologyVersions[0];
    const factorId = version.factors[0].id;
    const result = tryHandle(
      req('GET', '/evaluations', undefined, {
        projectId: 'proj-acme-2026',
        groupBy: 'factor',
        factorId,
        departmentId: 'dep-acme-hr',
        page: 0,
        size: 100,
      }),
    );
    const body = result?.body as PageEnvelope<EvaluationByFactorRow>;
    // Every HR row carries the HR position prefix in the title or code.
    expect(body.items.every((r) => r.position_code.startsWith('HR-'))).toBe(
      true,
    );
  });

  it('respects onlyUnfilled=true (drops rows already scored on the active factor)', () => {
    const version = mockDb.methodologyVersions[0];
    const factorId = version.factors[0].id;
    const all = tryHandle(
      req('GET', '/evaluations', undefined, {
        projectId: 'proj-acme-2026',
        groupBy: 'factor',
        factorId,
        page: 0,
        size: 200,
      }),
    );
    const unfilled = tryHandle(
      req('GET', '/evaluations', undefined, {
        projectId: 'proj-acme-2026',
        groupBy: 'factor',
        factorId,
        onlyUnfilled: true,
        page: 0,
        size: 200,
      }),
    );
    const allItems = (all?.body as PageEnvelope<EvaluationByFactorRow>).items;
    const unfilledItems = (unfilled?.body as PageEnvelope<EvaluationByFactorRow>)
      .items;
    expect(unfilledItems.length).toBeLessThanOrEqual(allItems.length);
    expect(
      unfilledItems.every((r) => r.current_score_factor_level_id == null),
    ).toBe(true);
  });
});

describe('MSW POST /evaluations/factor/:factorId/bulk-score', () => {
  it('rejects reason shorter than 50 chars', () => {
    const version = mockDb.methodologyVersions[0];
    const f0 = version.factors[0];
    const result = tryHandle(
      req('POST', `/evaluations/factor/${f0.id}/bulk-score`, {
        evaluation_ids: ['eval-ks-ops-coo'],
        factor_level_id: f0.levels[1].id,
        reason: 'too short',
      }),
    );
    expect(result?.status).toBe(400);
    expect((result?.body as { code?: string })?.code).toBe('REASON_REQUIRED');
  });

  it('drops tenant_id from the body (F-401)', () => {
    const version = mockDb.methodologyVersions[0];
    const f0 = version.factors[0];
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const result = tryHandle(
      req('POST', `/evaluations/factor/${f0.id}/bulk-score`, {
        tenant_id: 'tenant-evil',
        evaluation_ids: ['eval-ks-ops-coo'],
        factor_level_id: f0.levels[1].id,
        reason: 'A long enough reason explaining a justified bulk write op',
      }),
    );
    expect(result?.status).toBe(200);
    expect(warn).toHaveBeenCalled();
    warn.mockRestore();
  });

  it('records LOCKED rows in failed[] and still applies updates to editable rows', () => {
    const version = mockDb.methodologyVersions[0];
    const f0 = version.factors[0];
    // Pick one editable + one locked target.
    const editable = mockDb.evaluations.find((e) =>
      e.id.startsWith('eval-ks-') && e.status === 'DRAFT',
    );
    const locked = mockDb.evaluations.find(
      (e) => e.id.startsWith('eval-ks-') && e.status === 'LOCKED',
    );
    expect(editable).toBeDefined();
    expect(locked).toBeDefined();
    const result = tryHandle(
      req('POST', `/evaluations/factor/${f0.id}/bulk-score`, {
        evaluation_ids: [editable!.id, locked!.id],
        factor_level_id: f0.levels[2].id,
        reason: 'Committee unanimously agreed on level 3 after K-sheet review',
      }),
    );
    expect(result?.status).toBe(200);
    const body = result?.body as {
      updated: number;
      failed: { evaluation_id: string; reason: string }[];
    };
    expect(body.updated).toBeGreaterThanOrEqual(1);
    expect(body.failed.some((f) => f.evaluation_id === locked!.id)).toBe(true);
  });
});

describe('MSW POST /evaluations/factor/:factorId/bulk-submit', () => {
  it('rejects reason shorter than 20 chars', () => {
    const version = mockDb.methodologyVersions[0];
    const f0 = version.factors[0];
    const result = tryHandle(
      req('POST', `/evaluations/factor/${f0.id}/bulk-submit`, {
        evaluation_ids: ['eval-ks-ops-coo'],
        reason: 'short',
      }),
    );
    expect(result?.status).toBe(400);
    expect((result?.body as { code?: string })?.code).toBe('REASON_REQUIRED');
  });

  it('returns INCOMPLETE in failed[] for an evaluation missing required scores', () => {
    const version = mockDb.methodologyVersions[0];
    const f0 = version.factors[0];
    // Find an unfilled KS evaluation — fixture seeds only 1/3 with scores.
    const unfilled = mockDb.evaluations.find(
      (e) =>
        e.id.startsWith('eval-ks-') &&
        e.status === 'DRAFT' &&
        !mockDb.evaluationScores.some((s) => s.evaluation_id === e.id),
    );
    expect(unfilled).toBeDefined();
    const result = tryHandle(
      req('POST', `/evaluations/factor/${f0.id}/bulk-submit`, {
        evaluation_ids: [unfilled!.id],
        reason: 'Submitting to surface incomplete flag',
      }),
    );
    expect(result?.status).toBe(200);
    const body = result?.body as {
      updated: number;
      failed: { evaluation_id: string; reason: string }[];
    };
    expect(body.failed.some((f) => f.reason === 'INCOMPLETE')).toBe(true);
  });
});
