import { useQuery } from '@tanstack/react-query';
import { fetchPositions, positionKeys } from '../api/positionApi';
import type { PositionListParams } from '../types/positionTypes';

export function usePositions(params: PositionListParams | null) {
  return useQuery({
    queryKey: params ? positionKeys.list(params) : ['positions', 'list', null],
    queryFn: () => fetchPositions(params!),
    enabled: !!params?.projectId,
  });
}
