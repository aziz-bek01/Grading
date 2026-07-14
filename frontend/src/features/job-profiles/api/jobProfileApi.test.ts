/**
 * Bulk job-profile STATUS lookup wire test (replaces the per-position
 * `useQueries` client fan-out in `useJobProfileStatusesByPositions` with a
 * SINGLE call to `GET /job-profiles/statuses`).
 *
 * Drives the REAL fetcher through the MSW-style mock adapter so the test
 * exercises the actual wire shape: a BARE array with one element per
 * position that has an active (non-ARCHIVED) profile; positions with none
 * are simply absent (never an error).
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import type { AxiosAdapter } from 'axios';
import { httpClient } from '@/shared/api/httpClient';
import { createMockAdapter } from '@/shared/api/mocks/handlers';
import { ApiError } from '@/shared/api/apiError';
import { fetchJobProfileStatusesByPositions, jobProfileKeys } from './jobProfileApi';

describe('fetchJobProfileStatusesByPositions (bulk status endpoint)', () => {
  let originalAdapter: AxiosAdapter | undefined;

  beforeEach(() => {
    originalAdapter = httpClient.defaults.adapter as AxiosAdapter | undefined;
    httpClient.defaults.adapter = createMockAdapter(undefined);
  });

  afterEach(() => {
    httpClient.defaults.adapter = originalAdapter;
    vi.restoreAllMocks();
  });

  it('issues a SINGLE request carrying every requested id, and returns only the ones with an active profile', async () => {
    const getSpy = vi.spyOn(httpClient, 'get');
    // pos-cfo → APPROVED profile; pos-swe-senior → DRAFT profile; pos-cto → no profile at all.
    const result = await fetchJobProfileStatusesByPositions(['pos-cfo', 'pos-swe-senior', 'pos-cto']);

    expect(getSpy).toHaveBeenCalledTimes(1);
    const [url, config] = getSpy.mock.calls[0];
    expect(url).toBe('/job-profiles/statuses');
    expect((config as { params?: Record<string, unknown> })?.params).toEqual({
      positionIds: 'pos-cfo,pos-swe-senior,pos-cto',
    });

    const byId = new Map(result.map((r) => [r.position_id, r] as const));
    expect(byId.get('pos-cfo')?.status).toBe('APPROVED');
    expect(byId.get('pos-swe-senior')?.status).toBe('DRAFT');
    // No active profile → simply ABSENT from the array, not a null/error entry.
    expect(byId.has('pos-cto')).toBe(false);
    expect(result).toHaveLength(2);
  });

  it('returns job_profile_id and revision_number alongside status (lean wire shape)', async () => {
    const [cfoEntry] = await fetchJobProfileStatusesByPositions(['pos-cfo']);
    expect(cfoEntry).toEqual({
      position_id: 'pos-cfo',
      status: 'APPROVED',
      job_profile_id: 'jp-cfo-v1',
      revision_number: 1,
    });
  });

  it('rejects an empty id list with a 400 (never sends an unbounded/empty query)', async () => {
    await expect(fetchJobProfileStatusesByPositions([])).rejects.toBeInstanceOf(ApiError);
  });
});

describe('jobProfileKeys.statusesByPositions', () => {
  it('is stable for the same SET of ids regardless of input order (no refetch on re-render churn)', () => {
    const a = jobProfileKeys.statusesByPositions(['pos-2', 'pos-1']);
    const b = jobProfileKeys.statusesByPositions(['pos-1', 'pos-2']);
    expect(a).toEqual(b);
  });

  it('differs for a different set of ids', () => {
    const a = jobProfileKeys.statusesByPositions(['pos-1']);
    const b = jobProfileKeys.statusesByPositions(['pos-1', 'pos-2']);
    expect(a).not.toEqual(b);
  });
});
