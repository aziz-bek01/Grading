import { useQuery } from '@tanstack/react-query';
import { fetchDepartmentTree, orgKeys } from '../api/organizationApi';

export function useDepartmentTree(projectId: string | undefined) {
  return useQuery({
    queryKey: projectId ? orgKeys.tree(projectId) : ['organization', 'tree', null],
    queryFn: () => fetchDepartmentTree(projectId!),
    enabled: !!projectId,
  });
}
