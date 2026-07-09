import { describe, expect, it, vi } from 'vitest';
import type { PageEnvelope } from '@/shared/types/common';
import { fetchAllPages } from '../fetchAllPages';

/** Builds a `fetchPage(page, size)` fn backed by an in-memory array — mirrors
 * how a real per-feature fetcher (fetchUsers / fetchProjects / fetchPositions)
 * slices a full dataset server-side. */
function pagedFetcher<T>(all: T[]): (page: number, size: number) => Promise<PageEnvelope<T>> {
  return async (page, size) => {
    const start = page * size;
    const items = all.slice(start, start + size);
    return {
      items,
      page,
      size,
      total_elements: all.length,
      total_pages: Math.max(1, Math.ceil(all.length / size)),
    };
  };
}

describe('fetchAllPages', () => {
  it('returns every item across multiple pages and reports truncated:false', async () => {
    const all = Array.from({ length: 45 }, (_, i) => ({ id: `u${i}` }));
    const fetchPage = vi.fn(pagedFetcher(all));

    const result = await fetchAllPages(fetchPage, { pageSize: 20 });

    expect(result.items).toHaveLength(45);
    expect(result.items.map((x) => x.id)).toEqual(all.map((x) => x.id));
    expect(result.totalElements).toBe(45);
    expect(result.truncated).toBe(false);
    // 20 + 20 + 5 == 3 requests (page 0, 1, 2) — never over-fetches once
    // exhausted.
    expect(fetchPage).toHaveBeenCalledTimes(3);
    expect(fetchPage.mock.calls).toEqual([
      [0, 20],
      [1, 20],
      [2, 20],
    ]);
  });

  it('a single-page result (the common case) makes exactly one request', async () => {
    const all = Array.from({ length: 5 }, (_, i) => ({ id: `p${i}` }));
    const fetchPage = vi.fn(pagedFetcher(all));

    const result = await fetchAllPages(fetchPage, { pageSize: 100 });

    expect(fetchPage).toHaveBeenCalledTimes(1);
    expect(result.items).toHaveLength(5);
    expect(result.truncated).toBe(false);
  });

  it('an empty result set returns items:[] and truncated:false', async () => {
    const fetchPage = vi.fn(pagedFetcher<{ id: string }>([]));

    const result = await fetchAllPages(fetchPage, { pageSize: 20 });

    expect(result.items).toEqual([]);
    expect(result.totalElements).toBe(0);
    expect(result.truncated).toBe(false);
    expect(fetchPage).toHaveBeenCalledTimes(1);
  });

  it('THE core scenario: the 21st+ row is reachable — no silent cap at the backend default of 20', async () => {
    // Regression guard for the actual bug: a naive single unpaginated GET
    // (or a hand-guessed `size: 20`) would have stopped at exactly 20 and
    // never surfaced evaluator #21.
    const all = Array.from({ length: 27 }, (_, i) => ({ id: `evaluator-${i + 1}` }));
    const fetchPage = vi.fn(pagedFetcher(all));

    const result = await fetchAllPages(fetchPage, { pageSize: 20 });

    expect(result.items.some((x) => x.id === 'evaluator-21')).toBe(true);
    expect(result.items.some((x) => x.id === 'evaluator-27')).toBe(true);
    expect(result.truncated).toBe(false);
  });

  it('stops at the safety cap mid-page and reports truncated:true', async () => {
    const all = Array.from({ length: 500 }, (_, i) => ({ id: i }));
    const fetchPage = vi.fn(pagedFetcher(all));

    const result = await fetchAllPages(fetchPage, { pageSize: 100, safetyCap: 250 });

    expect(result.items).toHaveLength(250);
    expect(result.truncated).toBe(true);
    // page0 (100) + page1 (100) + page2 (capped to 50) == 3 requests, not 5.
    expect(fetchPage).toHaveBeenCalledTimes(3);
  });

  it('stops at the safety cap exactly on a page boundary and reports truncated:true', async () => {
    const all = Array.from({ length: 300 }, (_, i) => ({ id: i }));
    const fetchPage = vi.fn(pagedFetcher(all));

    const result = await fetchAllPages(fetchPage, { pageSize: 100, safetyCap: 200 });

    expect(result.items).toHaveLength(200);
    expect(result.truncated).toBe(true);
    expect(fetchPage).toHaveBeenCalledTimes(2);
  });

  it('never spins unbounded against a malformed/adversarial envelope that never reports exhaustion', async () => {
    // total_pages always claims "one more page" and every page returns a
    // FRESH page-size worth of rows — a real backend would never do this,
    // but the helper must still terminate rather than loop forever.
    let calls = 0;
    const fetchPage = vi.fn(async (page: number, size: number): Promise<PageEnvelope<{ id: number }>> => {
      calls += 1;
      const items = Array.from({ length: size }, (_, i) => ({ id: page * size + i }));
      return { items, page, size, total_elements: Number.MAX_SAFE_INTEGER, total_pages: 999_999 };
    });

    const result = await fetchAllPages(fetchPage, { pageSize: 50, safetyCap: 120 });

    expect(result.truncated).toBe(true);
    expect(result.items.length).toBeLessThanOrEqual(120);
    // Bounded by ceil(120/50)+1 == 4 requests, not 999,999.
    expect(calls).toBeLessThanOrEqual(4);
  });

  it('flags truncated when the envelope under-reports total_pages relative to total_elements', async () => {
    // Inconsistent server data: claims exhausted after page 0 (total_pages:1)
    // but total_elements says there should be more than we collected.
    const fetchPage = vi.fn(async (): Promise<PageEnvelope<{ id: number }>> => ({
      items: [{ id: 1 }, { id: 2 }],
      page: 0,
      size: 20,
      total_elements: 50,
      total_pages: 1,
    }));

    const result = await fetchAllPages(fetchPage, { pageSize: 20 });

    expect(result.items).toHaveLength(2);
    expect(result.truncated).toBe(true);
  });

  it('guards a total_pages of 0/NaN as "at least one page" instead of looping forever', async () => {
    const fetchPage = vi.fn(async (): Promise<PageEnvelope<{ id: number }>> => ({
      items: [{ id: 1 }],
      page: 0,
      size: 20,
      total_elements: 1,
      total_pages: 0,
    }));

    const result = await fetchAllPages(fetchPage, { pageSize: 20 });

    expect(fetchPage).toHaveBeenCalledTimes(1);
    expect(result.items).toHaveLength(1);
    expect(result.truncated).toBe(false);
  });

  it('rejects a non-positive pageSize', async () => {
    await expect(fetchAllPages(pagedFetcher([]), { pageSize: 0 })).rejects.toThrow(
      /pageSize/,
    );
  });

  it('rejects a non-positive safetyCap', async () => {
    await expect(fetchAllPages(pagedFetcher([]), { safetyCap: -1 })).rejects.toThrow(
      /safetyCap/,
    );
  });
});
