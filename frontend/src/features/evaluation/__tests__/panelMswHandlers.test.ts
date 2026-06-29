/**
 * Golden-file wire test for the EVALUATION_PANEL endpoints (MSW-from-BE).
 *
 * Asserts the MSW handlers serve EXACTLY the BE golden-file pinned shapes
 * (snake_case keys, PageResponse { items, page, size, total }, scores/grade
 * OMITTED until AVERAGED/APPROVED, result 404 while collecting, mass-assignment
 * fields dropped on create). The FE panel types/adapter are derived from these,
 * so this is the FE side of the contract pin.
 */
import { describe, expect, it, vi } from 'vitest';
import type { AxiosRequestConfig } from 'axios';
import { tryHandle } from '@/shared/api/mocks/handlers';
import { mockDb } from '@/shared/api/mocks/fixtures';
import type {
  Panel,
  PanelDetail,
  PanelPageResponse,
  PanelResult,
} from '../panelTypes';

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

describe('MSW GET /panels', () => {
  it('returns a PageResponse envelope with `total` (not total_elements)', () => {
    const res = tryHandle(req('GET', '/panels', undefined, { projectId: 'proj-acme-2026' }));
    expect(res?.status).toBe(200);
    const body = res?.body as PanelPageResponse;
    expect(body).toHaveProperty('items');
    expect(body).toHaveProperty('total');
    expect(Array.isArray(body.items)).toBe(true);
    expect(body.items.length).toBeGreaterThanOrEqual(2);
    const panel = body.items[0];
    expect(panel).toHaveProperty('position_title_i18n');
    expect(panel).toHaveProperty('min_evaluators');
    expect(panel).toHaveProperty('completed_count');
  });

  it('filters by positionId (camelCase query param)', () => {
    const res = tryHandle(
      req('GET', '/panels', undefined, { projectId: 'proj-acme-2026', positionId: 'pos-cfo' }),
    );
    const body = res?.body as PanelPageResponse;
    expect(body.items.every((p) => p.position_id === 'pos-cfo')).toBe(true);
  });
});

describe('MSW GET /panels/:id (blind-safe detail)', () => {
  it('returns panel + roster + completed_count, with NO scores while collecting', () => {
    const res = tryHandle(req('GET', '/panels/panel-cto-collecting'));
    expect(res?.status).toBe(200);
    const body = res?.body as PanelDetail;
    expect(body.panel.status).toBe('AWAITING_EVALUATIONS');
    // NON_NULL: scores omitted while collecting (blind-safe).
    expect(body.panel.raw_total_score == null).toBe(true);
    expect(body.panel.displayed_total_score == null).toBe(true);
    expect(body.roster.length).toBe(3);
    expect(body.completed_count).toBe(1);
    expect(body.total_count).toBe(3);
    // Each assignment carries role + status, never a score.
    const a = body.roster[0];
    expect(a).toHaveProperty('evaluator_role');
    expect(a).toHaveProperty('assignment_status');
    expect(a).not.toHaveProperty('raw_factor_score');
  });
});

describe('MSW GET /panels/:id/result (gated)', () => {
  it('404s while collecting (REQ-ISO-5 — no live average)', () => {
    const res = tryHandle(req('GET', '/panels/panel-cto-collecting/result'));
    expect(res?.status).toBe(404);
  });

  it('returns averaged total + per-factor averages with per-evaluator breakdown when AVERAGED', () => {
    const res = tryHandle(req('GET', '/panels/panel-cfo-averaged/result'));
    expect(res?.status).toBe(200);
    const body = res?.body as PanelResult;
    expect(body.evaluator_count).toBe(3);
    expect(typeof body.displayed_total_score).toBe('number');
    expect(body.factor_averages.length).toBeGreaterThan(0);
    const fa = body.factor_averages[0];
    expect(fa).toHaveProperty('avg_raw_factor_score');
    expect(fa.per_evaluator.length).toBe(3);
    // disagreement_range non-null when >= 2 contributors.
    expect(fa.disagreement_range).not.toBeNull();
  });
});

describe('MSW POST /panels (mass-assignment guard)', () => {
  it('ignores client-supplied raw_total_score / evaluator_count / status', () => {
    const before = mockDb.panels.length;
    const res = tryHandle(
      req('POST', '/panels', {
        position_id: 'pos-swe-senior',
        methodology_version_id: 'mv-cfo-v1',
        raw_total_score: 9999,
        evaluator_count: 99,
        status: 'APPROVED',
        assigned_grade_number: 16,
      }),
    );
    expect(res?.status).toBe(201);
    const panel = res?.body as Panel;
    // Server-computed: start at COLLECTING with zero counts; the injected
    // fields are dropped.
    expect(panel.status).toBe('COLLECTING');
    expect(panel.evaluator_count).toBe(0);
    expect(panel.raw_total_score == null).toBe(true);
    expect(panel.assigned_grade_number == null).toBe(true);
    expect(mockDb.panels.length).toBe(before + 1);
    // cleanup
    mockDb.panels = mockDb.panels.filter((p) => p.id !== panel.id);
  });

  it('drops tenant_id from the body (defence in depth)', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const res = tryHandle(
      req('POST', '/panels', {
        tenant_id: 'tenant-evil',
        position_id: 'pos-swe-senior',
        methodology_version_id: 'mv-cfo-v1',
      }),
    );
    expect(res?.status).toBe(201);
    expect(warn).toHaveBeenCalled();
    warn.mockRestore();
    const panel = res?.body as Panel;
    mockDb.panels = mockDb.panels.filter((p) => p.id !== panel.id);
  });
});

describe('MSW POST /panels/:id/lock-roster (min-3 + mandatory trio)', () => {
  it('rejects below floor / missing mandatory role; panel stays COLLECTING', () => {
    // Seed a COLLECTING panel with only 1 evaluator (HR director).
    const id = 'panel-test-lock';
    mockDb.panels.unshift({
      id,
      project_id: 'proj-acme-2026',
      position_id: 'pos-swe-senior',
      position_title_i18n: {},
      methodology_version_id: 'mv-cfo-v1',
      status: 'COLLECTING',
      min_evaluators: 3,
      evaluator_count: 1,
      completed_count: 0,
      created_at: new Date().toISOString(),
    });
    mockDb.panelAssignments.push({
      id: 'pa-test-1',
      panel_id: id,
      evaluator_user_id: 'user-hr-dir',
      evaluator_role: 'HR_DIRECTOR',
      assignment_status: 'ASSIGNED',
      updated_at: new Date().toISOString(),
    });
    const res = tryHandle(req('POST', `/panels/${id}/lock-roster`));
    expect(res?.status).toBe(400);
    expect((res?.body as { code?: string })?.code).toBe('MANDATORY_ROLES_MISSING');
    expect(panelStatus(id)).toBe('COLLECTING');
    // cleanup
    mockDb.panels = mockDb.panels.filter((p) => p.id !== id);
    mockDb.panelAssignments = mockDb.panelAssignments.filter((a) => a.panel_id !== id);
  });

  it('transitions COLLECTING -> AWAITING_EVALUATIONS when the trio is present', () => {
    const id = 'panel-test-lock-ok';
    mockDb.panels.unshift({
      id,
      project_id: 'proj-acme-2026',
      position_id: 'pos-swe-senior',
      position_title_i18n: {},
      methodology_version_id: 'mv-cfo-v1',
      status: 'COLLECTING',
      min_evaluators: 3,
      evaluator_count: 3,
      completed_count: 0,
      created_at: new Date().toISOString(),
    });
    for (const [i, role] of (['HR_DIRECTOR', 'DEPARTMENT_DIRECTOR', 'EXTERNAL_EXPERT'] as const).entries()) {
      mockDb.panelAssignments.push({
        id: `pa-ok-${i}`,
        panel_id: id,
        evaluator_user_id: `u-${i}`,
        evaluator_role: role,
        assignment_status: 'ASSIGNED',
        updated_at: new Date().toISOString(),
      });
    }
    const res = tryHandle(req('POST', `/panels/${id}/lock-roster`));
    expect(res?.status).toBe(200);
    expect((res?.body as Panel).status).toBe('AWAITING_EVALUATIONS');
    mockDb.panels = mockDb.panels.filter((p) => p.id !== id);
    mockDb.panelAssignments = mockDb.panelAssignments.filter((a) => a.panel_id !== id);
  });
});

describe('MSW POST /panels/:id/submit (CEO submit)', () => {
  it('rejects unless AVERAGED (PANEL_NOT_AVERAGED)', () => {
    const res = tryHandle(req('POST', '/panels/panel-cto-collecting/submit'));
    expect(res?.status).toBe(409);
    expect((res?.body as { code?: string })?.code).toBe('PANEL_NOT_AVERAGED');
  });
});

function panelStatus(id: string): string | undefined {
  return mockDb.panels.find((p) => p.id === id)?.status;
}
