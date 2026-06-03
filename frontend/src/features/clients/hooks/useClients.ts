import { useQuery } from '@tanstack/react-query';
import { clientKeys, fetchClients } from '../api/clientApi';
import type { ClientCompanyListQuery } from '../types/clientTypes';

export function useClients(query: ClientCompanyListQuery = {}) {
  return useQuery({
    queryKey: clientKeys.clientList(query),
    queryFn: () => fetchClients(query),
  });
}
