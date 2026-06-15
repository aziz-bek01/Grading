/**
 * MSW handler tests for the two evaluation-panel features:
 *   - Feature 1: GET /evaluations/my  (evaluator self-inbox — JSON array)
 *   - Feature 2: POST /panels/:id/reopen-for-expert  (reopen APPROVED panel)
 *
 * Asserts the handlers serve the EXACT BE contract shapes (snake_case keys,
 * array-not-envelope for /my, PANEL_NOT_APPROVED_FOR_REOPEN when not APPROVED)
 * so the FE adapter + dialog are pinned to the same contract.
 */
import { describe, expect, it } from 'vitest';
import type { AxiosRequestConfig } from 'axios';
import { tryHandle } from '@/shared/api/mocks/handlers';
import { mockDb } from '@/shared/api/mocks/fixtures';
import type { Panel } from '../panelTypes';

function req(
  method: string,
  url: string,
  data?: unknown,
  params?: Record<string, unknown>,
): AxiosRequestConfig {
  return {
    method,
    url,
    data: data !== undefined ? JSON.stringify(data) : undefined,
    params,
    headers: { 'X-Mock-Tenant-Id': '11111111-1111-1111-1111-111111111111' },
  } as AxiosRequestConfig;
}

interface MyRow {
  evaluation_id: string;
  project_id: string;
  panel_id: string | null;
  position_id: string;
  position_code: string;
  position_title: Record<string, string>;
  status: string;
  filled_factors_count: number;
  total_factors_count: number;
}

describe('MSW GET /evaluations/my (Feature 1)', () => {
  it('returns a JSON ARRAY (not a PageResponse envelope)', () => {
    const res = tryHandle(req('GET', '/evaluations/my'));
    expect(res?.status).toBe(200);
    expect(Array.isArray(res?.body)).toBe(true);
  });

  it('each row carries the snake_case MyEvaluationRow shape', () => {
    const res = tryHandle(req('GET', '/evaluations/my'));
    const rows = res?.body as MyRow[];
    expect(rows.length).toBeGreaterThan(0);
    const row = rows[0];
    expect(row).toHaveProperty('evaluation_id');
    // Each row carries its OWN owning project so the inbox can deep-link to the
    // project-scoped scoring sheet without relying on the active project.
    expect(row).toHaveProperty('project_id');
    expect(row).toHaveProperty('position_code');
    expect(row).toHaveProperty('position_title');
    expect(row).toHaveProperty('status');
    expect(row).toHaveProperty('filled_factors_count');
    expect(row).toHaveProperty('total_factors_count');
    // The localized title map is present (full 4-locale map source).
    expect(typeof row.position_title).toBe('object');
    expect(typeof row.filled_factors_count).toBe('number');
    expect(typeof row.total_factors_count).toBe('number');
    // project_id is a non-empty string for every assigned sheet.
    expect(rows.every((r) => typeof r.project_id === 'string' && r.project_id.length > 0)).toBe(true);
  });

  it('only returns sheets that have an evaluator assigned (caller-owned proxy)', () => {
    const res = tryHandle(req('GET', '/evaluations/my'));
    const rows = res?.body as MyRow[];
    const assignedIds = new Set(
      mockDb.evaluations
        .filter((e) => e.evaluator_user_id != null)
        .map((e) => e.id),
    );
    expect(rows.every((r) => assignedIds.has(r.evaluation_id))).toBe(true);
  });
});

describe('MSW POST /panels/:id/reopen-for-expert (Feature 2)', () => {
  function seedPanel(id: string, status: Panel['status']) {
    mockDb.panels.unshift({
      id,
      project_id: 'proj-acme-2026',
      position_id: 'pos-cfo',
      position_title_i18n: {},
      methodology_version_id: 'mv-cfo-v1',
      status,
      min_evaluators: 3,
      evaluator_count: 3,
      completed_count: 3,
      created_at: new Date().toISOString(),
    });
  }
  function cleanup(id: string) {
    mockDb.panels = mockDb.panels.filter((p) => p.id !== id);
    mockDb.panelAssignments = mockDb.panelAssignments.filter(
      (a) => a.panel_id !== id,
    );
  }

  it('APPROVED → 200 AWAITING_EVALUATIONS and adds the expert seat', () => {
    const id = 'panel-reopen-expert-ok';
    seedPanel(id, 'APPROVED');
    const before = mockDb.panelAssignments.length;
    const res = tryHandle(
      req('POST', `/panels/${id}/reopen-for-expert`, {
        additional_evaluator_user_id: 'user-expert-x',
        reason: 'second opinion needed',
      }),
    );
    expect(res?.status).toBe(200);
    expect((res?.body as Panel).status).toBe('AWAITING_EVALUATIONS');
    expect(mockDb.panelAssignments.length).toBe(before + 1);
    cleanup(id);
  });

  it('non-APPROVED → 400 PANEL_NOT_APPROVED_FOR_REOPEN', () => {
    const id = 'panel-reopen-expert-bad';
    seedPanel(id, 'AVERAGED');
    const res = tryHandle(
      req('POST', `/panels/${id}/reopen-for-expert`, {
        additional_evaluator_user_id: 'user-expert-x',
        reason: 'second opinion needed',
      }),
    );
    expect(res?.status).toBe(400);
    expect((res?.body as { code?: string })?.code).toBe(
      'PANEL_NOT_APPROVED_FOR_REOPEN',
    );
    cleanup(id);
  });

  it('blank reason → 400 validation', () => {
    const id = 'panel-reopen-expert-novalidation';
    seedPanel(id, 'APPROVED');
    const res = tryHandle(
      req('POST', `/panels/${id}/reopen-for-expert`, {
        additional_evaluator_user_id: 'user-expert-x',
        reason: 'no',
      }),
    );
    expect(res?.status).toBe(400);
    expect((res?.body as { code?: string })?.code).toBe('VALIDATION_FAILED');
    cleanup(id);
  });

  it('missing evaluator → 400 validation', () => {
    const id = 'panel-reopen-expert-nouser';
    seedPanel(id, 'APPROVED');
    const res = tryHandle(
      req('POST', `/panels/${id}/reopen-for-expert`, {
        reason: 'second opinion needed',
      }),
    );
    expect(res?.status).toBe(400);
    expect((res?.body as { code?: string })?.code).toBe('VALIDATION_FAILED');
    cleanup(id);
  });

  it('unknown panel → 404', () => {
    const res = tryHandle(
      req('POST', '/panels/does-not-exist/reopen-for-expert', {
        additional_evaluator_user_id: 'user-expert-x',
        reason: 'second opinion needed',
      }),
    );
    expect(res?.status).toBe(404);
  });
});
