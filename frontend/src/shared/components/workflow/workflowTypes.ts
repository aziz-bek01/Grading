/**
 * Workflow stepper contracts — mirror backend MVP 2 Phase 1 endpoint payload
 * (`GET /api/v1/projects/{id}/workflow-progress` returns WorkflowProgressResponse).
 *
 * Stages: 11 canonical stages per architecture §14 / design-foundation §16.4.
 * Statuses: 5 values per the backend WorkflowStageStatus enum.
 */
export type WorkflowStageKey =
  | 'SETUP'
  | 'ORGANIZATION'
  | 'POSITIONS'
  | 'JOB_PROFILES'
  | 'METHODOLOGY'
  | 'EVALUATION'
  | 'CALIBRATION'
  | 'GRADES'
  | 'COMPENSATION'
  | 'REPORTS'
  | 'ARCHIVE';

export const WORKFLOW_STAGES: WorkflowStageKey[] = [
  'SETUP',
  'ORGANIZATION',
  'POSITIONS',
  'JOB_PROFILES',
  'METHODOLOGY',
  'EVALUATION',
  'CALIBRATION',
  'GRADES',
  'COMPENSATION',
  'REPORTS',
  'ARCHIVE',
];

/**
 * 5 canonical statuses (MVP 2 backend `WorkflowStageStatus`).
 * NOT_STARTED replaces the Phase 2 `PENDING` value.
 */
export type WorkflowStageStatus =
  | 'NOT_STARTED'
  | 'IN_PROGRESS'
  | 'COMPLETE'
  | 'BLOCKED'
  | 'LOCKED_FUTURE';

export interface WorkflowStageProgress {
  stage: WorkflowStageKey;
  status: WorkflowStageStatus;
  /** 0..100 — backend rounds to nearest integer. */
  completionPercent: number;
  /** UUID of the user currently responsible for advancing the stage. May be null. */
  responsibleUserId?: string | null;
  /** Display name resolved by backend (when available). */
  responsibleUserName?: string | null;
  /** ISO-8601 timestamp of last status/completion change. May be null when stage untouched. */
  lastUpdatedAt?: string | null;
  /** Display name of the actor who triggered the last update. */
  lastUpdatedBy?: string | null;
  sortOrder: number;
}

export interface WorkflowProgressResponse {
  id: string;
  projectId: string;
  currentStage: WorkflowStageKey;
  startedAt: string;
  archivedAt?: string | null;
  stages: WorkflowStageProgress[];
}

export const WORKFLOW_STAGE_ROUTE_HINT: Record<WorkflowStageKey, 'workspace' | 'organization' | 'positions' | 'job-profiles' | 'methodology' | 'evaluation' | 'calibration' | 'grades' | 'compensation' | 'reports' | 'archive'> = {
  SETUP: 'workspace',
  ORGANIZATION: 'organization',
  POSITIONS: 'positions',
  JOB_PROFILES: 'job-profiles',
  METHODOLOGY: 'methodology',
  EVALUATION: 'evaluation',
  CALIBRATION: 'calibration',
  GRADES: 'grades',
  COMPENSATION: 'compensation',
  REPORTS: 'reports',
  ARCHIVE: 'archive',
};
