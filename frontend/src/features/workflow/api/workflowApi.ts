import { httpClient } from '@/shared/api/httpClient';
import { endpoints } from '@/shared/api/endpoints';
import type {
  WorkflowProgressResponse,
  WorkflowStageKey,
  WorkflowStageProgress,
  WorkflowStageStatus,
} from '@/shared/components/workflow/workflowTypes';

export const workflowKeys = {
  all: ['workflow'] as const,
  progress: (projectId: string) => ['workflow', 'progress', projectId] as const,
};

/* ------------------------------------------------------------------ *
 * Wire → domain adapter
 *
 * The real backend serialises the workflow snapshot in SNAKE_CASE (global
 * Jackson) with `@JsonInclude(NON_NULL)`. The envelope (`projectId`,
 * `startedAt`, `currentStage`) and each stage (`completionPercent`,
 * `responsibleUserId`, `sortOrder`) are read camelCase by the stepper /
 * dashboard. Without this map the envelope is broken and the stage
 * names render as UUIDs (scout sweep #3/#6). Once BE-10 sends
 * `responsible_user_name` / `last_updated_by_name`, this adapter reads them
 * automatically. Mirrors `normalizeApprovalRequest`: snake_case FIRST with a
 * camelCase fallback so the MSW mock and the real backend both deserialise.
 * ------------------------------------------------------------------ */

type Raw = Record<string, unknown>;

function pick<T = string>(raw: Raw, snake: string, camel: string): T | null {
  return ((raw[snake] ?? raw[camel]) as T | undefined) ?? null;
}

export function normalizeWorkflowStage(input: unknown): WorkflowStageProgress {
  const raw = (input ?? {}) as Raw;
  return {
    stage: (pick(raw, 'stage', 'stage') ?? 'SETUP') as WorkflowStageKey,
    status: (pick(raw, 'status', 'status') ?? 'NOT_STARTED') as WorkflowStageStatus,
    completionPercent: Number(pick<number>(raw, 'completion_percent', 'completionPercent') ?? 0),
    responsibleUserId: pick(raw, 'responsible_user_id', 'responsibleUserId'),
    // BE-10: server-resolved display name (OMITTED when unknown/non-member).
    responsibleUserName: pick(raw, 'responsible_user_name', 'responsibleUserName'),
    lastUpdatedAt: pick(raw, 'last_updated_at', 'lastUpdatedAt'),
    // BE-10: server-resolved display name of the last actor.
    lastUpdatedBy: pick(raw, 'last_updated_by_name', 'lastUpdatedByName') ??
      pick(raw, 'last_updated_by', 'lastUpdatedBy'),
    sortOrder: Number(pick<number>(raw, 'sort_order', 'sortOrder') ?? 0),
  };
}

export function normalizeWorkflowProgress(input: unknown): WorkflowProgressResponse {
  const raw = (input ?? {}) as Raw;
  const rawStages = Array.isArray(raw['stages']) ? (raw['stages'] as unknown[]) : [];
  return {
    id: String(raw.id ?? ''),
    projectId: pick(raw, 'project_id', 'projectId') ?? '',
    currentStage: (pick(raw, 'current_stage', 'currentStage') ?? 'SETUP') as WorkflowStageKey,
    startedAt: pick(raw, 'started_at', 'startedAt') ?? '',
    archivedAt: pick(raw, 'archived_at', 'archivedAt'),
    stages: rawStages.map((s) => normalizeWorkflowStage(s)),
  };
}

/** Fetch the 11-stage workflow snapshot for a project. */
export async function fetchWorkflowProgress(projectId: string): Promise<WorkflowProgressResponse> {
  const res = await httpClient.get<unknown>(endpoints.workflow.progress(projectId));
  return normalizeWorkflowProgress(res.data);
}

/** Advance project to a specific stage (requires WORKFLOW_EDIT). */
export async function advanceWorkflow(
  projectId: string,
  targetStage: WorkflowStageKey,
): Promise<WorkflowProgressResponse> {
  const res = await httpClient.post<unknown>(
    endpoints.workflow.advance(projectId),
    { targetStage },
  );
  return normalizeWorkflowProgress(res.data);
}

/** Force backend recomputation of completion percentages. */
export async function recomputeWorkflow(projectId: string): Promise<WorkflowProgressResponse> {
  const res = await httpClient.post<unknown>(
    endpoints.workflow.recompute(projectId),
    {},
  );
  return normalizeWorkflowProgress(res.data);
}
