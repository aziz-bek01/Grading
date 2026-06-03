import { useQuery } from '@tanstack/react-query';
import { fetchUserAudit, userKeys } from '../api/userApi';

export function useUserAudit(id: string | undefined, range: { from?: string; to?: string } = {}) {
  return useQuery({
    queryKey: id ? userKeys.audit(id, range) : ['users', 'audit', null],
    queryFn: () => fetchUserAudit(id!, range),
    enabled: !!id,
  });
}
