import { useQuery } from '@tanstack/react-query';
import { fetchProject, fetchWorkflowProgress, projectKeys } from '../api/projectApi';

export function useProject(id: string | undefined) {
  return useQuery({
    queryKey: id ? projectKeys.detail(id) : ['projects', 'detail', null],
    queryFn: () => fetchProject(id!),
    enabled: !!id,
  });
}

export function useWorkflowProgress(projectId: string | undefined) {
  return useQuery({
    queryKey: projectId ? projectKeys.workflowProgress(projectId) : ['projects', 'workflow-progress', null],
    queryFn: () => fetchWorkflowProgress(projectId!),
    enabled: !!projectId,
  });
}
