import { useQuery } from '@tanstack/react-query';
import { fetchUser, userKeys } from '../api/userApi';

export function useUser(id: string | undefined) {
  return useQuery({
    queryKey: id ? userKeys.detail(id) : ['users', 'detail', null],
    queryFn: () => fetchUser(id!),
    enabled: !!id,
  });
}
