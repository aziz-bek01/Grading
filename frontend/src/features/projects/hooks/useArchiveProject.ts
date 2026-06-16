import { useMutation, useQueryClient } from '@tanstack/react-query';
import { archiveProject, projectKeys } from '../api/projectApi';

/**
 * Archive (soft-delete) a project. Mirrors useUpdateProject: invalidates the
 * projects list query plus the single-project cache so the row's new ARCHIVED
 * status is reflected everywhere after the mutation settles.
 */
export function useArchiveProject() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => archiveProject(id),
    onSuccess: (_data, id) => {
      qc.invalidateQueries({ queryKey: projectKeys.all });
      qc.invalidateQueries({ queryKey: projectKeys.detail(id) });
    },
  });
}
