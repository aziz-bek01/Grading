import { useMutation, useQueryClient } from '@tanstack/react-query';
import { updateDepartment, orgKeys } from '../api/organizationApi';
import type { DepartmentUpdatePayload } from '../types/organizationTypes';

export function useUpdateDepartment(projectId: string, id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: DepartmentUpdatePayload) => updateDepartment(id, payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: orgKeys.tree(projectId) }),
  });
}
