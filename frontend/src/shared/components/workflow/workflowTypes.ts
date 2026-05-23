/**
 * Workflow stepper contracts — mirror backend §21.7 endpoint payload.
 * The 11 stages match design-foundation §16.4 in canonical order.
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

export type WorkflowStageStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETE' | 'BLOCKED' | 'LOCKED_FUTURE';

export interface WorkflowStageActor {
  id: string;
  name: string;
}

export interface WorkflowStageBlocker {
  code: string;
  count: number;
}

export interface WorkflowStageProgress {
  key: WorkflowStageKey;
  status: WorkflowStageStatus;
  completion_percent: number;
  total_items?: number;
  completed_items?: number;
  responsible_role?: string;
  last_update?: { at: string; actor: WorkflowStageActor };
  blockers: WorkflowStageBlocker[];
  next_action?: { label_key: string; route: string } | null;
}

export interface WorkflowProgressResponse {
  project_id: string;
  stages: WorkflowStageProgress[];
}
