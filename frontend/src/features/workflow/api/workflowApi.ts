import { httpClient } from '@/shared/api/httpClient';
import { endpoints } from '@/shared/api/endpoints';
import type {
  WorkflowProgressResponse,
  WorkflowStageKey,
} from '@/shared/components/workflow/workflowTypes';

export const workflowKeys = {
  all: ['workflow'] as const,
  progress: (projectId: string) => ['workflow', 'progress', projectId] as const,
};

/** Fetch the 11-stage workflow snapshot for a project. */
export async function fetchWorkflowProgress(projectId: string): Promise<WorkflowProgressResponse> {
  const res = await httpClient.get<WorkflowProgressResponse>(endpoints.workflow.progress(projectId));
  return res.data;
}

/** Advance project to a specific stage (requires WORKFLOW_EDIT). */
export async function advanceWorkflow(
  projectId: string,
  targetStage: WorkflowStageKey,
): Promise<WorkflowProgressResponse> {
  const res = await httpClient.post<WorkflowProgressResponse>(
    endpoints.workflow.advance(projectId),
    { targetStage },
  );
  return res.data;
}

/** Force backend recomputation of completion percentages. */
export async function recomputeWorkflow(projectId: string): Promise<WorkflowProgressResponse> {
  const res = await httpClient.post<WorkflowProgressResponse>(
    endpoints.workflow.recompute(projectId),
    {},
  );
  return res.data;
}
