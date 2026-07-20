/**
 * bulkCreateEvaluations chunking tests.
 *
 * The BE caps `items` at 200 per call (BulkCreateEvaluationRequest @Size →
 * 400 VALIDATION_FAILED above it). The FE fetcher must therefore split larger
 * selections into sequential ≤200-item chunks and merge the per-chunk
 * `{ created, failed }` results — the regression here was 460 selected
 * positions sent in ONE call → "Request validation failed" in the
 * AddPositionsDialog.
 */
import { describe, it, expect, afterEach, vi } from 'vitest';
import { httpClient } from '@/shared/api/httpClient';
import {
  bulkCreateEvaluations,
  fetchAllEvaluations,
  MAX_BULK_CREATE_ITEMS,
} from './evaluationApi';
import type { BulkCreateEvaluationItem } from '../types';

function makeItems(count: number): BulkCreateEvaluationItem[] {
  return Array.from({ length: count }, (_, i) => ({
    position_id: `pos-${i}`,
    methodology_version_id: 'mv-1',
  }));
}

describe('bulkCreateEvaluations chunking (BE 200-item cap)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('sends a ≤200-item selection in a single call', async () => {
    const postSpy = vi
      .spyOn(httpClient, 'post')
      .mockResolvedValue({ data: { created: 200, failed: [] } } as never);

    const result = await bulkCreateEvaluations({ items: makeItems(200) });

    expect(postSpy).toHaveBeenCalledTimes(1);
    expect(result).toEqual({ created: 200, failed: [] });
  });

  it('splits 460 items into sequential 200/200/60 chunks and merges results', async () => {
    const chunkSizes: number[] = [];
    const postSpy = vi
      .spyOn(httpClient, 'post')
      .mockImplementation(async (_url, body) => {
        const { items } = body as { items: BulkCreateEvaluationItem[] };
        chunkSizes.push(items.length);
        // Fail one row per chunk so the merge of failed[] is exercised too.
        return {
          data: {
            created: items.length - 1,
            failed: [
              {
                position_id: items[0].position_id,
                error_code: 'ALREADY_EXISTS',
                message: 'duplicate',
              },
            ],
          },
        } as never;
      });

    const result = await bulkCreateEvaluations({ items: makeItems(460) });

    expect(chunkSizes).toEqual([200, 200, 60]);
    expect(
      postSpy.mock.calls.every(([url]) => url === '/evaluations/bulk-create'),
    ).toBe(true);
    expect(result.created).toBe(199 + 199 + 59);
    expect(result.failed.map((f) => f.position_id)).toEqual([
      'pos-0',
      'pos-200',
      'pos-400',
    ]);
  });

  it('keeps every chunk within the server cap', () => {
    expect(MAX_BULK_CREATE_ITEMS).toBe(200);
  });
});

describe('fetchAllEvaluations page aggregation (shared fetchAllPages)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  // Serves a fake 460-row evaluation table page-by-page, honouring whatever
  // page/size fetchAllPages asks for.
  function mockServerWith(total: number) {
    return vi
      .spyOn(httpClient, 'get')
      .mockImplementation(async (_url, config) => {
        const { page, size } = (
          config as { params: { page: number; size: number } }
        ).params;
        const start = page * size;
        const count = Math.max(0, Math.min(size, total - start));
        return {
          data: {
            items: Array.from({ length: count }, (_, i) => ({
              id: `ev-${start + i}`,
            })),
            page,
            size,
            total_elements: total,
            total_pages: Math.ceil(total / size),
          },
        } as never;
      });
  }

  it('walks every page and concatenates all 460 rows (the regression size)', async () => {
    const getSpy = mockServerWith(460);

    const result = await fetchAllEvaluations({ projectId: 'proj-1' });

    expect(result.items).toHaveLength(460);
    expect(result.truncated).toBe(false);
    expect(result.items[0].id).toBe('ev-0');
    expect(result.items[459].id).toBe('ev-459');
    expect(getSpy.mock.calls.length).toBeGreaterThan(1); // really paged
  });

  it('stops after one call when everything fits a single page', async () => {
    const getSpy = mockServerWith(1);

    const result = await fetchAllEvaluations({ projectId: 'proj-1' });

    expect(getSpy).toHaveBeenCalledTimes(1);
    expect(result.items).toHaveLength(1);
    expect(result.truncated).toBe(false);
  });

  it('resolves an empty result set without looping', async () => {
    const getSpy = mockServerWith(0);

    const result = await fetchAllEvaluations({ projectId: 'proj-1' });

    expect(getSpy).toHaveBeenCalledTimes(1);
    expect(result.items).toEqual([]);
  });
});
