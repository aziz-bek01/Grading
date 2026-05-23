import { useQuery } from '@tanstack/react-query';
import { fetchPosition, positionKeys } from '../api/positionApi';

export function usePosition(id: string | undefined) {
  return useQuery({
    queryKey: id ? positionKeys.detail(id) : ['positions', 'detail', null],
    queryFn: () => fetchPosition(id!),
    enabled: !!id,
  });
}
