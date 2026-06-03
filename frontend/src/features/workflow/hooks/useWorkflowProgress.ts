import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  fetchWorkflowProgress,
  advanceWorkflow,
  recomputeWorkflow,
  workflowKeys,
} from '../api/workflowApi';
import type { WorkflowStageKey } from '@/shared/components/workflow/workflowTypes';

/**
 * Workflow snapshot — polled every 30s while the page is visible so the
 * stepper auto-reflects activity done elsewhere (other tabs / approvals).
 */
export function useWorkflowProgress(projectId: string | undefined) {
  return useQuery({
    queryKey: projectId ? workflowKeys.progress(projectId) : ['workflow', 'progress', null],
    queryFn: () => fetchWorkflowProgress(projectId!),
    enabled: !!projectId,
    refetchInterval: 30_000,
    refetchOnWindowFocus: true,
  });
}

export function useAdvanceWorkflow(projectId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (targetStage: WorkflowStageKey) => advanceWorkflow(projectId!, targetStage),
    onSuccess: (data) => {
      if (projectId) qc.setQueryData(workflowKeys.progress(projectId), data);
    },
  });
}

export function useRecomputeWorkflow(projectId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => recomputeWorkflow(projectId!),
    onSuccess: (data) => {
      if (projectId) qc.setQueryData(workflowKeys.progress(projectId), data);
    },
  });
}
