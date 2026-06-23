/**
 * MSW handler extension test — org-wide status filter for the CEO panels pull.
 *
 * REUSE: tryHandle from existing handlers (not forked), mockDb fixtures.
 *
 * Asserts:
 * 1. GET /panels with status= (no project_id) filters by status.
 * 2. GET /panels with status= AND project_id ignores the status filter
 *    (backend contract: status filter only honoured for tenant-wide pulls).
 * 3. GET /panels with multiple status= values returns panels matching any.
 */
import { describe, it, expect } from 'vitest';
import type { AxiosRequestConfig } from 'axios';
import { tryHandle } from '@/shared/api/mocks/handlers';
import { mockDb } from '@/shared/api/mocks/fixtures';
import type { PanelPageResponse } from '@/features/evaluation/panelTypes';

function req(
  method: string,
  url: string,
  params?: Record<string, unknown>,
): AxiosRequestConfig {
  return {
    method,
    url,
    params,
    headers: { 'X-Mock-Tenant-Id': '11111111-1111-1111-1111-111111111111' },
  } as AxiosRequestConfig;
}

describe('MSW GET /panels — org-wide CEO status filter', () => {
  it('returns only panels matching the requested status (no project_id)', () => {
    // AVERAGED panel is seeded as 'panel-cfo-averaged'.
    const res = tryHandle(req('GET', '/panels', { status: ['AVERAGED'] }));
    expect(res?.status).toBe(200);
    const body = res?.body as PanelPageResponse;
    expect(body.items.every((p) => p.status === 'AVERAGED')).toBe(true);
    expect(body.items.length).toBeGreaterThanOrEqual(1);
  });

  it('returns multiple statuses with repeated status param', () => {
    const res = tryHandle(
      req('GET', '/panels', { status: ['AVERAGED', 'AWAITING_EVALUATIONS'] }),
    );
    expect(res?.status).toBe(200);
    const body = res?.body as PanelPageResponse;
    const statuses = new Set(body.items.map((p) => p.status));
    // Both seeded panels cover AWAITING_EVALUATIONS and AVERAGED.
    expect(statuses.has('AVERAGED') || statuses.has('AWAITING_EVALUATIONS')).toBe(true);
    expect(body.items.every((p) => ['AVERAGED', 'AWAITING_EVALUATIONS'].includes(p.status))).toBe(true);
  });

  it('ignores the status filter when project_id is also present (backend contract)', () => {
    // With project_id present, status filter should be ignored: we get all
    // panels for the project regardless of status.
    const projectId = 'proj-acme-2026';
    const allForProject = mockDb.panels.filter((p) => p.project_id === projectId);

    const res = tryHandle(
      req('GET', '/panels', { project_id: projectId, status: ['APPROVED'] }),
    );
    expect(res?.status).toBe(200);
    const body = res?.body as PanelPageResponse;
    // Status filter ignored — returns all panels for the project (not just APPROVED).
    expect(body.items.length).toBe(allForProject.length);
  });
});
