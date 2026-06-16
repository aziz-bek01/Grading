import { useMutation, useQueryClient } from '@tanstack/react-query';
import { updateProject, projectKeys } from '../api/projectApi';
import type { ProjectUpdatePayload } from '../types/projectTypes';

interface UpdateProjectVars {
  id: string;
  payload: ProjectUpdatePayload;
}

/**
 * Update (rename / re-describe / re-schedule) a project.
 *
 * The variables carry the target `id` so a single hook instance on the list
 * page can edit any row. The payload is the backend update command
 * `{ name_i18n, description, start_date, end_date }` ONLY — the project `code`
 * is IMMUTABLE and is never sent (the server ignores it anyway). See the
 * PATCH /projects/{id} contract.
 */
export function useUpdateProject() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: UpdateProjectVars) => updateProject(id, payload),
    onSuccess: (_data, { id }) => {
      qc.invalidateQueries({ queryKey: projectKeys.all });
      qc.invalidateQueries({ queryKey: projectKeys.detail(id) });
    },
  });
}
