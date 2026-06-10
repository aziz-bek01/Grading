/**
 * Epic E — MSW custom-template handler tests.
 *
 * Asserts the in-memory custom-template store wired into the mock layer:
 *   1. GET /methodology-templates returns built-ins ∪ custom with the new
 *      fields (id / source / is_builtin).
 *   2. POST /methodologies/{id}/save-as-template creates a CUSTOM template;
 *      duplicate code → 409 METHODOLOGY_TEMPLATE_CODE_EXISTS.
 *   3. PUT /methodology-templates/{id} renames a CUSTOM template; built-in
 *      (id=null, never matched) and unknown ids → 404.
 *   4. DELETE /methodology-templates/{id} archives a CUSTOM template — it then
 *      disappears from the GET list.
 */
import { describe, expect, it, beforeEach } from 'vitest';
import type { AxiosRequestConfig } from 'axios';
import { tryHandle, resetMockCustomTemplates } from '../mocks/handlers';

const TENANT = { 'X-Mock-Tenant-Id': '11111111-1111-1111-1111-111111111111' };

interface TemplateRow {
  id: string | null;
  code: string;
  source: 'BUILTIN' | 'CUSTOM';
  is_builtin: boolean;
  name_i18n: Record<string, string>;
}

function listTemplates(): TemplateRow[] {
  const res = tryHandle({
    method: 'GET',
    url: '/methodology-templates',
    headers: TENANT,
  } as AxiosRequestConfig);
  return (res?.body as { items: TemplateRow[] }).items;
}

function saveAsTemplate(code: string, name = 'Шаблон') {
  return tryHandle({
    method: 'POST',
    url: '/methodologies/meth-cfo-finance/save-as-template',
    data: JSON.stringify({ code, name_i18n: { 'ru-RU': name } }),
    headers: TENANT,
  } as AxiosRequestConfig);
}

describe('Epic E — MSW methodology-template handlers', () => {
  beforeEach(() => resetMockCustomTemplates());

  it('GET returns built-ins with id=null/is_builtin and only active custom rows', () => {
    const items = listTemplates();
    const builtins = items.filter((t) => t.is_builtin);
    expect(builtins.length).toBeGreaterThanOrEqual(3);
    builtins.forEach((b) => {
      expect(b.id).toBeNull();
      expect(b.source).toBe('BUILTIN');
    });
    // No custom rows yet.
    expect(items.filter((t) => t.source === 'CUSTOM')).toHaveLength(0);
  });

  it('save-as-template creates a CUSTOM template that appears in the list', () => {
    const res = saveAsTemplate('TPL-CFO', 'CFO Финансы');
    expect(res?.status).toBe(201);
    const created = res?.body as TemplateRow;
    expect(created.source).toBe('CUSTOM');
    expect(created.is_builtin).toBe(false);
    expect(created.id).toBeTruthy();

    const custom = listTemplates().filter((t) => t.source === 'CUSTOM');
    expect(custom).toHaveLength(1);
    expect(custom[0].code).toBe('TPL-CFO');
  });

  it('duplicate code → 409 METHODOLOGY_TEMPLATE_CODE_EXISTS', () => {
    expect(saveAsTemplate('TPL-DUP')?.status).toBe(201);
    const dup = saveAsTemplate('TPL-DUP');
    expect(dup?.status).toBe(409);
    expect((dup?.body as { code: string }).code).toBe('METHODOLOGY_TEMPLATE_CODE_EXISTS');
  });

  it('a built-in registry code is also reserved (case-insensitive) → 409', () => {
    const dup = saveAsTemplate('classic_8_factor');
    expect(dup?.status).toBe(409);
  });

  it('PUT renames a CUSTOM template; unknown id → 404', () => {
    const created = saveAsTemplate('TPL-REN')?.body as TemplateRow;
    const renamed = tryHandle({
      method: 'PUT',
      url: `/methodology-templates/${created.id}`,
      data: JSON.stringify({ name_i18n: { 'ru-RU': 'Новое имя' } }),
      headers: TENANT,
    } as AxiosRequestConfig);
    expect(renamed?.status).toBe(200);
    expect((renamed?.body as TemplateRow).name_i18n['ru-RU']).toBe('Новое имя');

    const missing = tryHandle({
      method: 'PUT',
      url: '/methodology-templates/does-not-exist',
      data: JSON.stringify({ name_i18n: { 'ru-RU': 'x' } }),
      headers: TENANT,
    } as AxiosRequestConfig);
    expect(missing?.status).toBe(404);
  });

  it('DELETE archives a CUSTOM template — it disappears from the list', () => {
    const created = saveAsTemplate('TPL-ARCH')?.body as TemplateRow;
    expect(listTemplates().some((t) => t.code === 'TPL-ARCH')).toBe(true);

    const del = tryHandle({
      method: 'DELETE',
      url: `/methodology-templates/${created.id}`,
      headers: TENANT,
    } as AxiosRequestConfig);
    expect(del?.status).toBe(204);

    expect(listTemplates().some((t) => t.code === 'TPL-ARCH')).toBe(false);
  });

  it('built-in templates cannot be archived (id=null never matches PUT/DELETE route)', () => {
    // Built-ins have id=null; their codes are never addressable as /{id}.
    // Probing with the registry code (not an id) returns 404 (no custom match).
    const del = tryHandle({
      method: 'DELETE',
      url: '/methodology-templates/CLASSIC_8_FACTOR',
      headers: TENANT,
    } as AxiosRequestConfig);
    expect(del?.status).toBe(404);
    // The built-in is still present.
    expect(listTemplates().some((t) => t.code === 'CLASSIC_8_FACTOR' && t.is_builtin)).toBe(true);
  });
});
