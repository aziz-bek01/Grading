import { useQuery } from '@tanstack/react-query';
import { clientKeys, fetchClient } from '../api/clientApi';

export function useClient(clientId: string | undefined) {
  return useQuery({
    queryKey: clientId ? clientKeys.client(clientId) : ['clients', 'companies', 'detail', null],
    queryFn: () => fetchClient(clientId!),
    enabled: !!clientId,
  });
}
