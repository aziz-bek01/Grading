/**
 * Phase 6 — MSW grade-structure handler tests.
 *
 * Asserts the handlers match the REAL backend wire contracts (FE-12):
 *  - GET /grade-structures returns a PageResponse with grade_count + updated_at.
 *  - GET /grade-structures/{id} returns the `{ structure, grades, bands }` envelope.
 *  - POST .../from-template seeds 14 / 16 grades + bands and pyramid counts.
 *  - approve → lock transitions populate metadata; non-DRAFT approval returns 409.
 *  - preview-grade-lookup returns matched + out_of_range.
 *  - delete (DRAFT-only), grades/reorder (ordered_ids), grades/{id}/band DELETE.
 *  - grade-structure-templates CRUD + save-as-template (409 on duplicate code).
 *  - body.tenant_id is dropped (consistent with the rest of MSW).
 */
import { describe, expect, it, beforeEach, vi } from 'vitest';
import type { AxiosRequestConfig } from 'axios';
import { tryHandle, resetMockCustomGradeTemplates } from '../mocks/handlers';
import { mockDb } from '../mocks/fixtures';
import type { MockGradeStructure } from '../mocks/fixtures';

const HEADERS = { 'X-Mock-Tenant-Id': '11111111-1111-1111-1111-111111111111' };

function call(method: string, url: string, data?: unknown) {
  return tryHandle({
    method,
    url,
    data: data === undefined ? undefined : JSON.stringify(data),
    headers: HEADERS,
  } as AxiosRequestConfig);
}

function seedDraft(code: string, template = 'GRADE_14'): MockGradeStructure {
  const res = call('POST', '/grade-structures/from-template', {
    template_code: template,
    project_id: 'proj-acme-2026',
    code,
    name_i18n: { 'ru-RU': code, 'en-US': code },
  });
  // from-template returns the envelope; the structure lives in mockDb.
  const envelope = res?.body as { structure: { id: string } };
  return mockDb.gradeStructures.find((s) => s.id === envelope.structure.id)!;
}

describe('Phase 6 MSW grade-structure handlers', () => {
  let originalStructures: MockGradeStructure[];

  beforeEach(() => {
    resetMockCustomGradeTemplates();
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

  it('GET /grade-structures returns a PageResponse with grade_count + updated_at', () => {
    const result = call('GET', '/grade-structures?projectId=proj-acme-2026');
    expect(result?.status).toBe(200);
    const body = result?.body as {
      items: { id: string; project_id: string; grade_count: number; updated_at: string }[];
      total_elements: number;
    };
    expect(body.items.length).toBeGreaterThanOrEqual(1);
    for (const item of body.items) {
      expect(item.project_id).toBe('proj-acme-2026');
      expect(typeof item.grade_count).toBe('number');
      expect(item.updated_at).toBeTruthy();
    }
    expect(typeof body.total_elements).toBe('number');
  });

  it('GET /grade-structures/{id} returns the {structure, grades, bands} envelope', () => {
    const result = call('GET', '/grade-structures/gs-acme-14');
    expect(result?.status).toBe(200);
    const body = result?.body as {
      structure: { id: string; name_i18n: Record<string, string> };
      grades: { id: string; name_i18n: Record<string, string> }[];
      bands: { grade_id: string; min_score: number; max_score: number }[];
    };
    expect(body.structure.id).toBe('gs-acme-14');
    expect(body.structure.name_i18n['ru-RU']).toBeTruthy();
    expect(body.grades.length).toBe(14);
    // bands is a SEPARATE array joined by grade_id.
    expect(body.bands.length).toBe(14);
    expect(body.bands[0].grade_id).toBe(body.grades[0].id);
    // grade carries i18n keys and NO nested band.
    expect(body.grades[0].name_i18n['ru-RU']).toBeTruthy();
    expect((body.grades[0] as Record<string, unknown>).band).toBeUndefined();
  });

  it('POST /grade-structures/from-template seeds a 14-grade envelope with bands', () => {
    const result = call('POST', '/grade-structures/from-template', {
      template_code: 'GRADE_14',
      project_id: 'proj-acme-2026',
      code: 'NEW-14',
      name_i18n: { 'ru-RU': 'Тест 14', 'en-US': 'Test 14' },
    });
    expect(result?.status).toBe(201);
    const body = result?.body as {
      structure: { id: string; structure_type: string };
      grades: unknown[];
      bands: unknown[];
    };
    expect(body.structure.structure_type).toBe('GRADE_14');
    expect(body.grades).toHaveLength(14);
    expect(body.bands).toHaveLength(14);
    expect(mockDb.gradePyramidCounts[body.structure.id]).toHaveLength(14);
  });

  it('POST /grade-structures/from-template with unknown code → 404', () => {
    const result = call('POST', '/grade-structures/from-template', {
      template_code: 'DOES_NOT_EXIST',
      project_id: 'proj-acme-2026',
      code: 'X',
      name_i18n: { 'ru-RU': 'X' },
    });
    expect(result?.status).toBe(404);
  });

  it('POST /grade-structures with body.tenant_id is dropped + warned', () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const result = call('POST', '/grade-structures', {
      tenant_id: 'tenant-evil',
      project_id: 'proj-acme-2026',
      code: 'CUST-1',
      name_i18n: { 'ru-RU': 'Custom' },
      structure_type: 'CUSTOM',
      gap_policy: 'STRICT_NO_GAPS',
    });
    expect(result?.status).toBe(201);
    const body = result?.body as { structure: Record<string, unknown> };
    expect(body.structure).not.toHaveProperty('tenant_id');
    expect(warnSpy).toHaveBeenCalled();
    warnSpy.mockRestore();
  });

  it('PATCH metadata sends name_i18n/gap_policy keys and reflects them', () => {
    const draft = seedDraft('META-1');
    const result = call('PATCH', `/grade-structures/${draft.id}`, {
      name_i18n: { 'ru-RU': 'Переименовано', 'en-US': 'Renamed' },
      description_i18n: { 'ru-RU': 'Описание' },
      gap_policy: 'ALLOW_GAPS_WARN',
    });
    expect(result?.status).toBe(200);
    const body = result?.body as { name_i18n: Record<string, string>; gap_policy: string };
    expect(body.name_i18n['en-US']).toBe('Renamed');
    expect(body.gap_policy).toBe('ALLOW_GAPS_WARN');
  });

  it('approve transitions DRAFT → APPROVED; cannot approve from non-DRAFT', () => {
    const draft = seedDraft('APP-1');
    const approved = call('POST', `/grade-structures/${draft.id}/approve`, {});
    expect(approved?.status).toBe(200);
    expect((approved?.body as { status: string }).status).toBe('APPROVED');

    const again = call('POST', `/grade-structures/${draft.id}/approve`, {});
    expect(again?.status).toBe(409);
  });

  it('lock transitions APPROVED → LOCKED; cannot lock DRAFT', () => {
    const draft = seedDraft('LCK-1', 'GRADE_16');
    const tooEarly = call('POST', `/grade-structures/${draft.id}/lock`, {});
    expect(tooEarly?.status).toBe(409);

    call('POST', `/grade-structures/${draft.id}/approve`, {});
    const locked = call('POST', `/grade-structures/${draft.id}/lock`, {});
    expect(locked?.status).toBe(200);
    expect((locked?.body as { status: string }).status).toBe('LOCKED');
  });

  it('DELETE removes a DRAFT structure; non-DRAFT → 400 transition rejected', () => {
    const draft = seedDraft('DEL-1');
    const del = call('DELETE', `/grade-structures/${draft.id}`);
    expect(del?.status).toBe(204);
    expect(mockDb.gradeStructures.find((s) => s.id === draft.id)).toBeUndefined();

    // gs-acme-14 is LOCKED → delete rejected.
    const rejected = call('DELETE', '/grade-structures/gs-acme-14');
    expect(rejected?.status).toBe(400);
    expect((rejected?.body as { code: string }).code).toBe(
      'GRADE_STRUCTURE_TRANSITION_REJECTED',
    );
  });

  it('POST /grades/reorder accepts ordered_ids and persists sort_order', () => {
    const draft = seedDraft('REO-1');
    const ids = draft.grades.map((g) => g.id);
    const reversed = [...ids].reverse();
    const result = call('POST', `/grade-structures/${draft.id}/grades/reorder`, {
      ordered_ids: reversed,
    });
    expect(result?.status).toBe(200);
    const items = (result?.body as { items: { id: string }[] }).items;
    expect(items.map((g) => g.id)).toEqual(reversed);
    // sort_order persisted to match the new order.
    const first = draft.grades.find((g) => g.id === reversed[0])!;
    expect(first.sort_order).toBe(0);
  });

  it('POST /grades/reorder with a mismatched id set → 400 GRADE_REORDER_SET_MISMATCH', () => {
    const draft = seedDraft('REO-2');
    const result = call('POST', `/grade-structures/${draft.id}/grades/reorder`, {
      ordered_ids: ['not-a-real-id'],
    });
    expect(result?.status).toBe(400);
    expect((result?.body as { code: string }).code).toBe('GRADE_REORDER_SET_MISMATCH');
  });

  it('DELETE /grades/{id}/band clears the band (idempotent 204)', () => {
    const draft = seedDraft('BND-1');
    const grade = draft.grades[0];
    expect(grade.band).toBeTruthy();
    const del = call('DELETE', `/grades/${grade.id}/band`);
    expect(del?.status).toBe(204);
    expect(grade.band).toBeNull();
    // idempotent — still 204 when no band.
    const again = call('DELETE', `/grades/${grade.id}/band`);
    expect(again?.status).toBe(204);
  });

  it('preview-grade-lookup returns matched + out_of_range', () => {
    const acme = mockDb.gradeStructures.find((s) => s.id === 'gs-acme-14')!;
    const hit = call('POST', `/grade-structures/${acme.id}/preview-grade-lookup`, {
      score: 75,
    });
    const r = hit?.body as { matched: boolean; out_of_range: boolean; grade_number?: number };
    expect(r.matched).toBe(true);
    expect(r.out_of_range).toBe(false);
    expect(r.grade_number).toBe(2);

    const miss = call('POST', `/grade-structures/${acme.id}/preview-grade-lookup`, {
      score: 5000,
    });
    const m = miss?.body as { matched: boolean; out_of_range: boolean };
    expect(m.matched).toBe(false);
    expect(m.out_of_range).toBe(true);
  });

  it('pyramid endpoint returns enriched rows ({ rows } only)', () => {
    const result = call('GET', '/grade-structures/gs-acme-14/pyramid');
    expect(result?.status).toBe(200);
    const body = result?.body as {
      rows: {
        grade_number: number;
        grade_name: Record<string, string>;
        position_count: number;
        min_score: number | null;
      }[];
    };
    expect(body.rows.length).toBe(14);
    expect(body.rows[0].grade_name['ru-RU']).toBeTruthy();
    expect(body.rows.every((r) => r.position_count >= 0)).toBe(true);
  });

  it('GET /grade-structure-templates returns built-ins ∪ custom', () => {
    const result = call('GET', '/grade-structure-templates');
    expect(result?.status).toBe(200);
    const items = (result?.body as { items: { code: string; is_builtin: boolean }[] }).items;
    expect(items.some((t) => t.code === 'GRADE_14' && t.is_builtin)).toBe(true);
    expect(items.some((t) => t.code === 'GRADE_16')).toBe(true);
  });

  it('save-as-template returns 201 { template_id } and surfaces 409 on duplicate code', () => {
    const draft = seedDraft('TPL-SRC-1');
    const created = call('POST', `/grade-structures/${draft.id}/save-as-template`, {
      code: 'GS-TPL-CUSTOM-1',
      name_i18n: { 'ru-RU': 'Кастом', 'en-US': 'Custom' },
    });
    expect(created?.status).toBe(201);
    expect((created?.body as { template_id: string }).template_id).toBeTruthy();

    // It now appears in the catalog tagged custom.
    const list = call('GET', '/grade-structure-templates');
    const items = (list?.body as { items: { code: string; source: string }[] }).items;
    expect(items.some((t) => t.code === 'GS-TPL-CUSTOM-1' && t.source === 'CUSTOM')).toBe(
      true,
    );

    // Duplicate code → 409.
    const dup = call('POST', `/grade-structures/${draft.id}/save-as-template`, {
      code: 'GS-TPL-CUSTOM-1',
      name_i18n: { 'ru-RU': 'Снова' },
    });
    expect(dup?.status).toBe(409);
    expect((dup?.body as { code: string }).code).toBe('GRADE_TEMPLATE_CODE_EXISTS');
  });

  it('rename + archive a custom template; built-in id → 404', () => {
    const draft = seedDraft('TPL-SRC-2');
    const created = call('POST', `/grade-structures/${draft.id}/save-as-template`, {
      code: 'GS-TPL-CUSTOM-2',
      name_i18n: { 'ru-RU': 'Кастом2' },
    });
    const templateId = (created?.body as { template_id: string }).template_id;

    const renamed = call('PUT', `/grade-structure-templates/${templateId}`, {
      name_i18n: { 'ru-RU': 'Переименовано', 'en-US': 'Renamed' },
    });
    expect(renamed?.status).toBe(200);

    // A non-existent (built-in / cross-tenant) id → 404.
    const notFound = call('PUT', '/grade-structure-templates/00000000-0000-0000-0000-000000000000', {
      name_i18n: { 'ru-RU': 'X' },
    });
    expect(notFound?.status).toBe(404);

    const archived = call('DELETE', `/grade-structure-templates/${templateId}`);
    expect(archived?.status).toBe(204);
    const list = call('GET', '/grade-structure-templates');
    const items = (list?.body as { items: { code: string }[] }).items;
    expect(items.some((t) => t.code === 'GS-TPL-CUSTOM-2')).toBe(false);
  });
});

// Tiny shim to keep snapshot logic close to the test, while still using the
// describe/it API. Reset is purely for hygiene — assertions don't depend on it.
function afterEachReset(fn: () => void) {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (globalThis as any).afterEach?.(fn);
}
