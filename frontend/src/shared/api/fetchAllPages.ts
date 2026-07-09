/**
 * Shared "load the FULL list" helper — the ONE replacement for the
 * "guess a bigger page size" band-aid that had been independently
 * re-invented at every picker / lookup call site (`size: 200`, `size: 500`,
 * or simply omitting `page`/`size` and relying on the backend's default page
 * of 20). Every one of those copies silently truncated past its guessed
 * ceiling with NO error and NO indication to the user — e.g. the 21st
 * evaluator could never be assigned to a panel roster because `useUsers()`
 * only ever saw the backend's first 20 rows (EPIC-013).
 *
 * `fetchAllPages` pages through an EXISTING paginated envelope (page / size /
 * total_elements / total_pages — the wire contract is untouched) until the
 * server reports it is exhausted OR a documented safety cap is hit, whichever
 * comes first. The safety cap is a HARD, finite bound — this never turns into
 * an unbounded "fetch everything" loop against a hostile/misbehaving backend
 * — and when it is reached the result is flagged `truncated: true` so the
 * caller can render a VISIBLE "results truncated — refine your filter"
 * notice instead of quietly presenting a partial list as complete.
 *
 * Each call site supplies ONLY its own existing per-feature fetcher
 * (`fetchUsers`, `fetchProjects`, `fetchPositions`, …) partially applied over
 * `page`/`size` — the pagination loop, the safety cap, and the truncation
 * detection live HERE ONCE, not copy-pasted per feature.
 */
import type { PageEnvelope } from '@/shared/types/common';

/** Page size requested per network call while paging through a full list. */
export const DEFAULT_ALL_PAGES_SIZE = 100;

/**
 * Hard ceiling on the number of items `fetchAllPages` will ever collect in
 * one call. Comfortably above any real MVP1 tenant's expected catalog size
 * (users / projects / positions) while still bounding worst-case request
 * count and memory. When reached, `truncated: true` is returned — callers
 * MUST surface this; it is never a silent partial result.
 */
export const DEFAULT_ALL_PAGES_SAFETY_CAP = 2000;

export interface FetchAllPagesOptions {
  /** Page size requested per network call. Defaults to {@link DEFAULT_ALL_PAGES_SIZE}. */
  pageSize?: number;
  /** Hard ceiling on total items collected. Defaults to {@link DEFAULT_ALL_PAGES_SAFETY_CAP}. */
  safetyCap?: number;
}

export interface AllPagesResult<T> {
  items: T[];
  /** Server-reported total element count (informational — may exceed `items.length` when `truncated`). */
  totalElements: number;
  /**
   * True when `fetchAllPages` stopped before the server confirmed the set
   * was exhausted (the safety cap was hit, or the envelope was internally
   * inconsistent). Callers MUST show a visible notice instead of treating a
   * partial list as complete — this is the entire point of the helper.
   */
  truncated: boolean;
}

/**
 * Pages `fetchPage(page, size)` from `page = 0` until the server's own
 * `total_pages` is exhausted (or an empty page is returned), bounded by
 * `safetyCap` items collected. Never loops more than
 * `ceil(safetyCap / pageSize) + 1` times, regardless of what the server
 * claims — this bounds the worst case even against an inconsistent or
 * adversarial envelope (e.g. `total_pages` that never advances).
 */
export async function fetchAllPages<T>(
  fetchPage: (page: number, size: number) => Promise<PageEnvelope<T>>,
  options: FetchAllPagesOptions = {},
): Promise<AllPagesResult<T>> {
  const pageSize = options.pageSize ?? DEFAULT_ALL_PAGES_SIZE;
  const safetyCap = options.safetyCap ?? DEFAULT_ALL_PAGES_SAFETY_CAP;
  if (!Number.isFinite(pageSize) || pageSize <= 0) {
    throw new Error('fetchAllPages: pageSize must be a positive number');
  }
  if (!Number.isFinite(safetyCap) || safetyCap <= 0) {
    throw new Error('fetchAllPages: safetyCap must be a positive number');
  }

  const items: T[] = [];
  let totalElements = 0;
  let page = 0;
  // Only set true when we actually reach the server's last page (or an empty
  // page) via the normal exhaustion path below — every OTHER exit (safety cap
  // hit mid-page, safety cap hit on a page boundary, or the iteration guard
  // tripping) leaves this false, which is exactly what should mark the result
  // `truncated`.
  let exhausted = false;

  // Independent hard stop — bounded strictly by the safety cap, NOT by the
  // server-reported total_pages, so a malformed/inconsistent envelope can
  // never spin an unbounded request loop.
  const maxRequests = Math.ceil(safetyCap / pageSize) + 1;

  for (let requestCount = 0; requestCount < maxRequests; requestCount++) {
    const envelope = await fetchPage(page, pageSize);
    totalElements = envelope.total_elements;
    const rawTotalPages = envelope.total_pages;
    const totalPages =
      Number.isFinite(rawTotalPages) && rawTotalPages > 0 ? rawTotalPages : 1;

    const room = safetyCap - items.length;
    if (envelope.items.length > room) {
      items.push(...envelope.items.slice(0, room));
      break; // safety cap hit mid-page — NOT exhausted.
    }
    items.push(...envelope.items);

    if (page + 1 >= totalPages || envelope.items.length === 0) {
      exhausted = true;
      break;
    }
    if (items.length >= safetyCap) break; // cap hit exactly on a page boundary.
    page += 1;
  }

  // Defensive: even a "normal" exhaustion is only trustworthy when we
  // actually collected as many rows as the server claims exist — an
  // inconsistent envelope (fewer total_pages than total_elements implies)
  // must still surface as truncated rather than silently under-reporting.
  const truncated = !exhausted || items.length < totalElements;

  return { items, totalElements, truncated };
}
