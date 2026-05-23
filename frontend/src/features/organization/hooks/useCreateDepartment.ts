import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createDepartment, orgKeys } from '../api/organizationApi';

export function useCreateDepartment(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: createDepartment,
    onSuccess: () => qc.invalidateQueries({ queryKey: orgKeys.tree(projectId) }),
  });
}
