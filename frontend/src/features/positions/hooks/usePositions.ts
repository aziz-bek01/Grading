import { useQuery } from '@tanstack/react-query';
import { fetchAllPages } from '@/shared/api/fetchAllPages';
import { fetchPositions, positionKeys } from '../api/positionApi';
import type { PositionListParams } from '../types/positionTypes';

/**
 * ONE server page (`params.page` / `params.size`) — correct for a browsable,
 * paginated position catalog (see `PositionListPage`, which wires this to
 * `PaginationBar`). Do NOT use this for a picker/lookup that needs every
 * position; use {@link useAllPositions} instead.
 */
export function usePositions(params: PositionListParams | null) {
  return useQuery({
    queryKey: params ? positionKeys.list(params) : ['positions', 'list', null],
    queryFn: () => fetchPositions(params!),
    enabled: !!params?.projectId,
  });
}

/**
 * Loads the FULL position set for a project via the shared `fetchAllPages`
 * helper — for lookups/pickers (the evaluation list's position-id → name/
 * department map, the "Add positions" candidate picker, the completion bar,
 * a single evaluation's position-name header lookup) that must see every
 * position, not just whichever ones happen to fit a guessed page size (the
 * former `size: 200` / `size: 500` band-aids — see EPIC-013).
 *
 * Bounded by `fetchAllPages`'s safety cap; `data.truncated` is set when that
 * cap is hit — callers MUST surface it (never silently drop rows).
 */
export function useAllPositions(
  params: Omit<PositionListParams, 'page' | 'size'> | null,
) {
  return useQuery({
    queryKey: params ? positionKeys.listAll(params) : ['positions', 'list-all', null],
    queryFn: () =>
      fetchAllPages((page, size) => fetchPositions({ ...params!, page, size })),
    enabled: !!params?.projectId,
  });
}
