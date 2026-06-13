import { useQuery } from '@tanstack/react-query';
import {
  fetchDepartmentPositionCounts,
  orgKeys,
  type DepartmentPositionCount,
} from '../api/organizationApi';

/**
 * T4 (Defect 1) — the ONE shared hook for server-authoritative per-department
 * position counts. Returns a `Map<departmentId, { directCount, subtreeCount }>`.
 *
 * BOTH count consumers read this single hook (the OpenPanelDialog coverage
 * badges and the DepartmentPanelProgress strip) so there is exactly one
 * count-computation path — no FE-side bucketing of a page-capped position list,
 * and no risk of one surface being FE-computed while the other is BE-fed.
 *
 * Re-exports the count row type so consumers do not reach into the api layer.
 */
export type { DepartmentPositionCount };

export function useDepartmentPositionCounts(projectId: string | undefined) {
  return useQuery({
    queryKey: projectId
      ? orgKeys.positionCounts(projectId)
      : ['organization', 'position-counts', null],
    queryFn: () => fetchDepartmentPositionCounts(projectId!),
    enabled: !!projectId,
  });
}
