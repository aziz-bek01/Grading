import { useMutation, useQueryClient } from '@tanstack/react-query';
import { updateProject, projectKeys } from '../api/projectApi';
import type { ProjectUpdatePayload } from '../types/projectTypes';

export function useUpdateProject(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: ProjectUpdatePayload) => updateProject(id, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: projectKeys.all });
      qc.invalidateQueries({ queryKey: projectKeys.detail(id) });
    },
  });
}
