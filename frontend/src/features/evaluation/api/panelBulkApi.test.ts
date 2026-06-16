/**
 * Bulk-create + roster-suggestions wire tests (FE-5 / FE-10).
 *
 * Drives the REAL fetchers through the MSW mock adapter (handlers written FROM
 * the BE golden records) and asserts the snake_case wire + the documented shape
 * rules: failed[] keys on position_id; seat_failures present ONLY for
 * ROSTER_PARTIAL; 404 on roster-suggestions resolves to an empty list; the
 * department cut is server-filtered; the 200 cap is enforced.
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import type { AxiosAdapter } from 'axios';
import { httpClient } from '@/shared/api/httpClient';
import { createMockAdapter } from '@/shared/api/mocks/handlers';
import { mockDb } from '@/shared/api/mocks/fixtures';
import { ApiError } from '@/shared/api/apiError';
import {
  bulkCreatePanels,
  fetchRosterSuggestions,
} from './panelApi';
import { fetchPositions } from '@/features/positions/api/positionApi';
import {
  BulkCreatePanelsRequestSchema,
  BulkCreatePanelsResultSchema,
} from '../schemas/panelSchemas';

describe('panel bulk-create / roster-suggestions wire (MSW from BE records)', () => {
  let originalAdapter: AxiosAdapter | undefined;
  let panelCountBefore = 0;

  beforeEach(() => {
    originalAdapter = httpClient.defaults.adapter as AxiosAdapter | undefined;
    httpClient.defaults.adapter = createMockAdapter(undefined);
    panelCountBefore = mockDb.panels.length;
  });

  afterEach(() => {
    httpClient.defaults.adapter = originalAdapter;
    // Roll back panels/assignments created by a test so the suite stays stable.
    mockDb.panels.length = panelCountBefore;
    mockDb.panelOutOfScopeDepartmentIds.length = 0;
    mockDb.panelWithdrawnUserIds.length = 0;
    vi.restoreAllMocks();
  });

  it('opens one panel per position with a shared roster in a SINGLE call', async () => {
    const result = await bulkCreatePanels({
      methodology_version_id: 'mv-cfo-v1',
      position_ids: ['pos-ks-ops-coo', 'pos-ks-ops-head', 'pos-ks-ops-spec'],
      roster: [
        { evaluator_user_id: 'user-hr-dir', evaluator_role: 'HR_DIRECTOR' },
        { evaluator_user_id: 'user-dept-dir', evaluator_role: 'DEPARTMENT_DIRECTOR' },
        { evaluator_user_id: 'user-ext-exp', evaluator_role: 'EXTERNAL_EXPERT' },
      ],
    });
    expect(result.created).toBe(3);
    expect(result.failed).toEqual([]);
    // Result conforms to the recorded BE shape.
    expect(BulkCreatePanelsResultSchema.safeParse(result).success).toBe(true);
  });

  it('sends start_evaluations:true on the wire when requested (create AND start)', async () => {
    // Stub the raw post so we assert the WHITELISTED body exactly WITHOUT
    // persisting a panel into the mock DB (the MSW handler uses unshift, which
    // the suite's length-truncate rollback would not undo). The flag must reach
    // the wire only when set, and no tenant_id is ever carried.
    const postSpy = vi
      .spyOn(httpClient, 'post')
      .mockResolvedValue({ data: { created: 1, failed: [] } } as never);
    await bulkCreatePanels({
      methodology_version_id: 'mv-cfo-v1',
      position_ids: ['pos-ks-ops-mgr'],
      roster: [
        { evaluator_user_id: 'user-hr-dir', evaluator_role: 'HR_DIRECTOR' },
        { evaluator_user_id: 'user-dept-dir', evaluator_role: 'DEPARTMENT_DIRECTOR' },
        { evaluator_user_id: 'user-ext-exp', evaluator_role: 'EXTERNAL_EXPERT' },
      ],
      start_evaluations: true,
    });
    const body = postSpy.mock.calls[0][1] as Record<string, unknown>;
    expect(body.start_evaluations).toBe(true);
    expect(body).not.toHaveProperty('tenant_id');
  });

  it('OMITS start_evaluations from the wire when unset / falsy', async () => {
    const postSpy = vi
      .spyOn(httpClient, 'post')
      .mockResolvedValue({ data: { created: 1, failed: [] } } as never);
    // Unset.
    await bulkCreatePanels({
      methodology_version_id: 'mv-cfo-v1',
      position_ids: ['pos-ks-ops-mgr'],
    });
    // Explicit false.
    await bulkCreatePanels({
      methodology_version_id: 'mv-cfo-v1',
      position_ids: ['pos-ks-ops-mgr'],
      start_evaluations: false,
    });
    const unsetBody = postSpy.mock.calls[0][1] as Record<string, unknown>;
    const falseBody = postSpy.mock.calls[1][1] as Record<string, unknown>;
    expect(unsetBody).not.toHaveProperty('start_evaluations');
    expect(falseBody).not.toHaveProperty('start_evaluations');
  });

  it('reports duplicate-active rows as ALREADY_EXISTS without seat_failures, and creates the rest', async () => {
    // First call creates one panel for pos-ks-ops-coo.
    await bulkCreatePanels({
      methodology_version_id: 'mv-cfo-v1',
      position_ids: ['pos-ks-ops-coo'],
    });
    // Second call mixes the duplicate with a fresh row.
    const result = await bulkCreatePanels({
      methodology_version_id: 'mv-cfo-v1',
      position_ids: ['pos-ks-ops-coo', 'pos-ks-ops-mgr'],
    });
    expect(result.created).toBe(1); // only the fresh one
    expect(result.failed).toHaveLength(1);
    const dup = result.failed[0];
    expect(dup.position_id).toBe('pos-ks-ops-coo');
    expect(dup.error_code).toBe('ALREADY_EXISTS');
    // KEY SHAPE RULE: seat_failures ABSENT for non-ROSTER_PARTIAL codes.
    expect(dup.seat_failures).toBeUndefined();
  });

  it('reports a withdrawn seat as ROSTER_PARTIAL with seat_failures while still opening the panel', async () => {
    mockDb.panelWithdrawnUserIds.push('user-ext-exp');
    const result = await bulkCreatePanels({
      methodology_version_id: 'mv-cfo-v1',
      position_ids: ['pos-ks-hr-chro'],
      roster: [
        { evaluator_user_id: 'user-hr-dir', evaluator_role: 'HR_DIRECTOR' },
        { evaluator_user_id: 'user-ext-exp', evaluator_role: 'EXTERNAL_EXPERT' },
      ],
    });
    // Panel WAS opened (counts toward created) AND reported as ROSTER_PARTIAL.
    expect(result.created).toBe(1);
    expect(result.failed).toHaveLength(1);
    const partial = result.failed[0];
    expect(partial.error_code).toBe('ROSTER_PARTIAL');
    expect(partial.seat_failures).toBeDefined();
    expect(partial.seat_failures?.[0].evaluator_role).toBe('EXTERNAL_EXPERT');
    expect(partial.seat_failures?.[0].evaluator_user_id).toBe('user-ext-exp');
  });

  it('reports an unknown / cross-tenant position as ACCESS_DENIED (no existence reveal)', async () => {
    const result = await bulkCreatePanels({
      methodology_version_id: 'mv-cfo-v1',
      position_ids: ['pos-does-not-exist'],
    });
    expect(result.created).toBe(0);
    expect(result.failed[0].error_code).toBe('ACCESS_DENIED');
  });

  it('rejects a request above the 200-position cap with a validation error', async () => {
    const tooMany = Array.from({ length: 201 }, (_, i) => `pos-${i}`);
    // FE schema mirror rejects pre-flight.
    expect(
      BulkCreatePanelsRequestSchema.safeParse({
        methodology_version_id: 'mv-cfo-v1',
        position_ids: tooMany,
      }).success,
    ).toBe(false);
    // And the server rejects it too.
    await expect(
      bulkCreatePanels({ methodology_version_id: 'mv-cfo-v1', position_ids: tooMany }),
    ).rejects.toBeInstanceOf(ApiError);
  });

  it('roster-suggestions returns advisory dept-director candidates (snake_case wire)', async () => {
    const res = await fetchRosterSuggestions('proj-acme-2026', 'dep-acme-ops');
    expect(res.department_id).toBe('dep-acme-ops');
    expect(res.dept_director_candidates.length).toBeGreaterThan(0);
    expect(res.dept_director_candidates[0]).toHaveProperty('user_id');
    expect(res.dept_director_candidates[0]).toHaveProperty('full_name');
  });

  it('roster-suggestions 404 (out-of-subtree) resolves to an EMPTY list, not an error', async () => {
    mockDb.panelOutOfScopeDepartmentIds.push('dep-acme-legal');
    const res = await fetchRosterSuggestions('proj-acme-2026', 'dep-acme-legal');
    expect(res.dept_director_candidates).toEqual([]);
  });

  it('positions are server-filtered by departmentId (department cut, no full scan)', async () => {
    const res = await fetchPositions({
      projectId: 'proj-acme-2026',
      departmentId: 'dep-acme-hr',
      status: 'ACTIVE',
      size: 200,
    });
    expect(res.items.length).toBeGreaterThan(0);
    expect(res.items.every((p) => p.department_id === 'dep-acme-hr')).toBe(true);
  });
});
