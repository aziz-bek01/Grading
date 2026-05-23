import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createProject, projectKeys } from '../api/projectApi';

export function useCreateProject() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: createProject,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: projectKeys.all });
    },
  });
}
