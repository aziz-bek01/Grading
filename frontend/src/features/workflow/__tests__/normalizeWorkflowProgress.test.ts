import { describe, it, expect } from 'vitest';
import {
  normalizeWorkflowProgress,
  normalizeWorkflowStage,
} from '../api/workflowApi';

/**
 * Captured REAL-WIRE response from `WorkflowProgressResponse` (global
 * SNAKE_CASE, `@JsonInclude(NON_NULL)`). Before FE-9 the FE cast `res.data`
 * directly → broken envelope (projectId/startedAt undefined) and stage names
 * shown as UUIDs (scout sweep #3/#6 / AC12). Once BE-10 sends
 * responsible_user_name + last_updated_by_name the SAME adapter reads them.
 */
const REAL_WIRE = {
  id: 'wf-acme-2026',
  project_id: 'proj-acme-2026',
  current_stage: 'EVALUATION',
  started_at: '2026-01-15T08:00:00Z',
  archived_at: null,
  stages: [
    {
      stage: 'SETUP',
      status: 'COMPLETE',
      completion_percent: 100,
      sort_order: 0,
      responsible_user_id: '00000000-0000-0000-0000-000000000001',
      responsible_user_name: 'HRLab Analyst',
      last_updated_at: '2026-02-01T08:00:00Z',
      last_updated_by_name: 'HRLab Analyst',
    },
    {
      stage: 'EVALUATION',
      status: 'IN_PROGRESS',
      completion_percent: 50,
      sort_order: 5,
    },
  ],
} as const;

describe('normalizeWorkflowProgress (wire → domain adapter)', () => {
  it('maps the snake_case envelope to camelCase', () => {
    const w = normalizeWorkflowProgress(REAL_WIRE);
    expect(w.id).toBe('wf-acme-2026');
    expect(w.projectId).toBe('proj-acme-2026');
    expect(w.currentStage).toBe('EVALUATION');
    expect(w.startedAt).toBe('2026-01-15T08:00:00Z');
    expect(w.stages).toHaveLength(2);
  });

  it('reads BE-10 resolved names (responsible + last-updated)', () => {
    const w = normalizeWorkflowProgress(REAL_WIRE);
    const setup = w.stages[0];
    expect(setup.completionPercent).toBe(100);
    expect(setup.responsibleUserId).toBe('00000000-0000-0000-0000-000000000001');
    expect(setup.responsibleUserName).toBe('HRLab Analyst');
    expect(setup.lastUpdatedBy).toBe('HRLab Analyst');
    expect(setup.sortOrder).toBe(0);
  });

  it('leaves resolved names null when the BE omits them (NON_NULL)', () => {
    const w = normalizeWorkflowProgress(REAL_WIRE);
    const evalStage = w.stages[1];
    expect(evalStage.responsibleUserName).toBeNull();
    expect(evalStage.lastUpdatedBy).toBeNull();
  });

  it('normalizeWorkflowStage maps an isolated stage and tolerates camelCase', () => {
    const s = normalizeWorkflowStage({
      stage: 'POSITIONS',
      status: 'IN_PROGRESS',
      completionPercent: 80,
      sortOrder: 2,
      responsibleUserName: 'X',
    });
    expect(s.stage).toBe('POSITIONS');
    expect(s.completionPercent).toBe(80);
    expect(s.responsibleUserName).toBe('X');
  });

  it('defaults an empty payload without throwing', () => {
    const w = normalizeWorkflowProgress({});
    expect(w.projectId).toBe('');
    expect(w.stages).toEqual([]);
  });
});
