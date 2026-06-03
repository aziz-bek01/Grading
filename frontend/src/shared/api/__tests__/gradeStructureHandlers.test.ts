/**
 * Phase 6 — MSW grade-structure handler tests.
 *
 * Asserts:
 *  - GET /grade-structures filters by projectId.
 *  - POST .../from-template seeds 14 / 16 grades and pyramid counts.
 *  - approve → lock transitions populate metadata; non-DRAFT approval returns 409.
 *  - preview-grade-lookup returns the matching grade and out-of-range result.
 *  - body.tenant_id is dropped (consistent with the rest of MSW).
 */
import { describe, expect, it, beforeEach, vi } from 'vitest';
import type { AxiosRequestConfig } from 'axios';
import { tryHandle } from '../mocks/handlers';
import { mockDb } from '../mocks/fixtures';
import type {
  MockGradeStructure,
} from '../mocks/fixtures';

const HEADERS = { 'X-Mock-Tenant-Id': '11111111-1111-1111-1111-111111111111' };

describe('Phase 6 MSW grade-structure handlers', () => {
  let originalStructures: MockGradeStructure[];

  beforeEach(() => {
    // snapshot+restore so mutations across tests are isolated
    originalStructures = mockDb.gradeStructures.map((s) => ({
      ...s,
      grades: s.grades.map((g) => ({
        ...g,
        band: g.band ? { ...g.band } : null,
      })),
    }));
  });

  afterEachReset(() => {
    mockDb.gradeStructures.length = 0;
    for (const s of originalStructures) mockDb.gradeStructures.push(s);
  });

  it('GET /grade-structures filters by projectId', () => {
    const result = tryHandle({
      method: 'GET',
      url: '/grade-structures?projectId=proj-acme-2026',
      headers: HEADERS,
    } as AxiosRequestConfig);
    expect(result?.status).toBe(200);
    const body = result?.body as { items: { id: string; project_id: string }[] };
    for (const item of body.items) {
      expect(item.project_id).toBe('proj-acme-2026');
    }
    expect(body.items.length).toBeGreaterThanOrEqual(1);
  });

  it('POST /grade-structures/from-template seeds a 14-grade structure', () => {
    const result = tryHandle({
      method: 'POST',
      url: '/grade-structures/from-template',
      data: JSON.stringify({
        template_code: 'GRADE_14',
        project_id: 'proj-acme-2026',
        code: 'NEW-14',
        name: { 'ru-RU': 'Тест 14' },
      }),
      headers: HEADERS,
    } as AxiosRequestConfig);
    expect(result?.status).toBe(201);
    const created = result?.body as MockGradeStructure;
    expect(created.structure_type).toBe('GRADE_14');
    expect(created.grades).toHaveLength(14);
    expect(created.grades[0].band).toBeDefined();
    expect(mockDb.gradePyramidCounts[created.id]).toHaveLength(14);
  });

  it('POST /grade-structures with body.tenant_id is dropped + warned', () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const result = tryHandle({
      method: 'POST',
      url: '/grade-structures',
      data: JSON.stringify({
        tenant_id: 'tenant-evil',
        project_id: 'proj-acme-2026',
        code: 'CUST-1',
        name: { 'ru-RU': 'Custom' },
        structure_type: 'CUSTOM',
        gap_policy: 'STRICT_NO_GAPS',
      }),
      headers: HEADERS,
    } as AxiosRequestConfig);
    expect(result?.status).toBe(201);
    const created = result?.body as Record<string, unknown>;
    expect(created).not.toHaveProperty('tenant_id');
    expect(warnSpy).toHaveBeenCalled();
    warnSpy.mockRestore();
  });

  it('approve transitions DRAFT → APPROVED; cannot approve from non-DRAFT', () => {
    // create a fresh draft
    const seed = tryHandle({
      method: 'POST',
      url: '/grade-structures/from-template',
      data: JSON.stringify({
        template_code: 'GRADE_14',
        project_id: 'proj-acme-2026',
        code: 'APP-1',
        name: { 'ru-RU': 'app1' },
      }),
      headers: HEADERS,
    } as AxiosRequestConfig);
    const draft = seed?.body as MockGradeStructure;

    const approveResult = tryHandle({
      method: 'POST',
      url: `/grade-structures/${draft.id}/approve`,
      data: '{}',
      headers: HEADERS,
    } as AxiosRequestConfig);
    expect(approveResult?.status).toBe(200);
    const approved = approveResult?.body as MockGradeStructure;
    expect(approved.status).toBe('APPROVED');
    expect(approved.approved_at).toBeTruthy();
    expect(approved.approved_by_name).toBeTruthy();

    // second approve should 409
    const again = tryHandle({
      method: 'POST',
      url: `/grade-structures/${draft.id}/approve`,
      data: '{}',
      headers: HEADERS,
    } as AxiosRequestConfig);
    expect(again?.status).toBe(409);
  });

  it('lock transitions APPROVED → LOCKED; cannot lock DRAFT', () => {
    const draftSeed = tryHandle({
      method: 'POST',
      url: '/grade-structures/from-template',
      data: JSON.stringify({
        template_code: 'GRADE_16',
        project_id: 'proj-acme-2026',
        code: 'LCK-1',
        name: { 'ru-RU': 'lock1' },
      }),
      headers: HEADERS,
    } as AxiosRequestConfig);
    const draft = draftSeed?.body as MockGradeStructure;

    // Lock from DRAFT → 409
    const tooEarly = tryHandle({
      method: 'POST',
      url: `/grade-structures/${draft.id}/lock`,
      data: '{}',
      headers: HEADERS,
    } as AxiosRequestConfig);
    expect(tooEarly?.status).toBe(409);

    // Approve first
    tryHandle({
      method: 'POST',
      url: `/grade-structures/${draft.id}/approve`,
      data: '{}',
      headers: HEADERS,
    } as AxiosRequestConfig);

    // Now lock
    const locked = tryHandle({
      method: 'POST',
      url: `/grade-structures/${draft.id}/lock`,
      data: '{}',
      headers: HEADERS,
    } as AxiosRequestConfig);
    expect(locked?.status).toBe(200);
    expect((locked?.body as MockGradeStructure).status).toBe('LOCKED');
  });

  it('preview-grade-lookup returns the matching grade and out-of-range', () => {
    const acme = mockDb.gradeStructures.find((s) => s.id === 'gs-acme-14');
    expect(acme).toBeTruthy();

    // 75 maps to G2 (50.0001..100)
    const hit = tryHandle({
      method: 'POST',
      url: `/grade-structures/${acme!.id}/preview-grade-lookup`,
      data: JSON.stringify({ score: 75 }),
      headers: HEADERS,
    } as AxiosRequestConfig);
    const r = hit?.body as { grade_number?: number | null; out_of_range: boolean };
    expect(r.grade_number).toBe(2);
    expect(r.out_of_range).toBe(false);

    // 5000 is out of range
    const miss = tryHandle({
      method: 'POST',
      url: `/grade-structures/${acme!.id}/preview-grade-lookup`,
      data: JSON.stringify({ score: 5000 }),
      headers: HEADERS,
    } as AxiosRequestConfig);
    const m = miss?.body as { grade_number?: number | null; out_of_range: boolean };
    expect(m.grade_number).toBeNull();
    expect(m.out_of_range).toBe(true);
  });

  it('pyramid endpoint returns row-per-grade with position counts', () => {
    const result = tryHandle({
      method: 'GET',
      url: '/grade-structures/gs-acme-14/pyramid',
      headers: HEADERS,
    } as AxiosRequestConfig);
    expect(result?.status).toBe(200);
    const body = result?.body as {
      rows: { grade_number: number; position_count: number }[];
    };
    expect(body.rows.length).toBe(14);
    expect(body.rows.every((r) => r.position_count >= 0)).toBe(true);
  });
});

// Tiny shim to keep snapshot logic close to the test, while still using the
// describe/it API. Reset is purely for hygiene — assertions don't depend on it.
function afterEachReset(fn: () => void) {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (globalThis as any).afterEach?.(fn);
}
