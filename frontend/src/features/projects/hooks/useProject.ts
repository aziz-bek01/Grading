import { useQuery } from '@tanstack/react-query';
import { fetchProject, projectKeys } from '../api/projectApi';

export function useProject(id: string | undefined) {
  return useQuery({
    queryKey: id ? projectKeys.detail(id) : ['projects', 'detail', null],
    queryFn: () => fetchProject(id!),
    enabled: !!id,
  });
}

// Backwards-compat re-export so existing callers don't break — new code should
// import from `features/workflow/hooks/useWorkflowProgress` directly.
export { useWorkflowProgress } from '@/features/workflow/hooks/useWorkflowProgress';
