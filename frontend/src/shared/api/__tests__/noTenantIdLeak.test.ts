/**
 * Guard against `tenant_id` / `tenantId` leaking onto the wire (D-202 / D-217 / F-208).
 *
 * Contract: the backend derives the active tenant from the JWT. The
 * frontend MUST NOT include any tenant identifier in:
 *   - URL path
 *   - query string
 *   - request body
 *   - any custom header other than the mock-only `X-Mock-Tenant-Id`
 *
 * This test patches the axios httpClient with a recording adapter, runs
 * every Phase 2 API fetcher, and asserts no outbound request carries a
 * tenant token.
 */
import { describe, expect, it, beforeAll, afterAll, vi } from 'vitest';
import type { AxiosAdapter, AxiosRequestConfig, AxiosResponse } from 'axios';
import { httpClient } from '../httpClient';

interface RecordedRequest {
  method: string;
  url: string;
  params: Record<string, unknown>;
  body: unknown;
  headers: Record<string, unknown>;
}

const recorded: RecordedRequest[] = [];
let originalAdapter: AxiosAdapter | undefined;

const recordingAdapter: AxiosAdapter = async (config: AxiosRequestConfig) => {
  // Extract a plain-object snapshot of headers (axios uses AxiosHeaders class).
  const hdrs = config.headers as unknown;
  let headersObj: Record<string, unknown> = {};
  if (hdrs && typeof hdrs === 'object') {
    if (typeof (hdrs as { toJSON?: () => Record<string, unknown> }).toJSON === 'function') {
      headersObj = (hdrs as { toJSON: () => Record<string, unknown> }).toJSON();
    } else {
      headersObj = { ...(hdrs as Record<string, unknown>) };
    }
  }
  recorded.push({
    method: (config.method ?? 'get').toUpperCase(),
    url: config.url ?? '',
    params: (config.params as Record<string, unknown>) ?? {},
    body: config.data,
    headers: headersObj,
  });
  // Minimal happy-path response so each fetcher resolves.
  const data = stubResponseFor(config);
  const res: AxiosResponse = {
    data,
    status: 200,
    statusText: 'OK',
    headers: {},
    config: config as never,
  } as AxiosResponse;
  return res;
};

function stubResponseFor(config: AxiosRequestConfig): unknown {
  const url = config.url ?? '';
  if (url.includes('/projects') && !url.includes('/workflow-progress') && !/\/projects\/[^/]+$/.test(url)) {
    return { items: [], page: 0, size: 0, total_elements: 0, total_pages: 0 };
  }
  if (url.includes('/departments/tree')) return { items: [] };
  if (url.includes('/positions') && !/\/positions\/[^/]+$/.test(url)) {
    return { items: [], page: 0, size: 0, total_elements: 0, total_pages: 0 };
  }
  if (/\/projects\/[^/]+\/workflow-progress$/.test(url)) {
    return { project_id: 'x', stages: [] };
  }
  return {};
}

beforeAll(() => {
  originalAdapter = httpClient.defaults.adapter as AxiosAdapter | undefined;
  httpClient.defaults.adapter = recordingAdapter;
});

afterAll(() => {
  httpClient.defaults.adapter = originalAdapter;
});

function assertNoTenantLeak(req: RecordedRequest) {
  const FORBIDDEN = /tenant[._-]?id/i;
  // Path & query
  expect(req.url, `Path includes tenant id: ${req.url}`).not.toMatch(FORBIDDEN);
  // Params
  for (const k of Object.keys(req.params)) {
    expect(k, `Query param leaks tenant id (${req.method} ${req.url})`).not.toMatch(FORBIDDEN);
  }
  // Body
  if (req.body !== undefined && req.body !== null && req.body !== '') {
    const bodyStr = typeof req.body === 'string' ? req.body : JSON.stringify(req.body);
    expect(bodyStr, `Body leaks tenant id (${req.method} ${req.url})`).not.toMatch(FORBIDDEN);
  }
  // Headers — `X-Mock-Tenant-Id` is mock-only and explicitly allowed.
  for (const [name, value] of Object.entries(req.headers)) {
    if (name.toLowerCase() === 'x-mock-tenant-id') continue;
    expect(name.toLowerCase(), `Header name leaks tenant id (${req.method} ${req.url})`).not.toMatch(FORBIDDEN);
    if (typeof value === 'string') {
      expect(value, `Header ${name} leaks tenant id`).not.toMatch(FORBIDDEN);
    }
  }
}

describe('no tenant_id leak in outbound API requests', () => {
  it('projectApi.fetchProjects() sends no tenant id', async () => {
    recorded.length = 0;
    const { fetchProjects } = await import('@/features/projects/api/projectApi');
    await fetchProjects();
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('projectApi.createProject() sends no tenant id', async () => {
    recorded.length = 0;
    const { createProject } = await import('@/features/projects/api/projectApi');
    await createProject({ code: 'X', name: { 'ru-RU': 'X' } });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('projectApi.updateProject() sends no tenant id', async () => {
    recorded.length = 0;
    const { updateProject } = await import('@/features/projects/api/projectApi');
    await updateProject('proj-1', { name: { 'ru-RU': 'X' } });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('projectApi.fetchProject() sends no tenant id', async () => {
    recorded.length = 0;
    const { fetchProject } = await import('@/features/projects/api/projectApi');
    await fetchProject('proj-1');
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('projectApi.fetchWorkflowProgress() sends no tenant id', async () => {
    recorded.length = 0;
    const { fetchWorkflowProgress } = await import('@/features/projects/api/projectApi');
    await fetchWorkflowProgress('proj-1');
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('organizationApi.fetchDepartmentTree() sends no tenant id', async () => {
    recorded.length = 0;
    const { fetchDepartmentTree } = await import('@/features/organization/api/organizationApi');
    await fetchDepartmentTree('proj-1');
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('organizationApi.createDepartment() sends no tenant id', async () => {
    recorded.length = 0;
    const { createDepartment } = await import('@/features/organization/api/organizationApi');
    await createDepartment({
      project_id: 'proj-1',
      parent_id: null,
      code: 'X',
      name: { 'ru-RU': 'X' },
      type: 'DEPARTMENT',
    });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('organizationApi.updateDepartment() sends no tenant id', async () => {
    recorded.length = 0;
    const { updateDepartment } = await import('@/features/organization/api/organizationApi');
    await updateDepartment('dep-1', { name: { 'ru-RU': 'X' } });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('organizationApi.archiveDepartment() sends no tenant id', async () => {
    recorded.length = 0;
    const { archiveDepartment } = await import('@/features/organization/api/organizationApi');
    await archiveDepartment('dep-1');
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('positionApi.fetchPositions() sends no tenant id (projectId is allowed)', async () => {
    recorded.length = 0;
    const { fetchPositions } = await import('@/features/positions/api/positionApi');
    await fetchPositions({ projectId: 'proj-1' });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
    // Positive: projectId IS allowed to leave the wire.
    expect(recorded[0].params).toHaveProperty('projectId', 'proj-1');
  });

  it('positionApi.createPosition() sends no tenant id', async () => {
    recorded.length = 0;
    const { createPosition } = await import('@/features/positions/api/positionApi');
    await createPosition({
      project_id: 'proj-1',
      department_id: 'dep-1',
      code: 'X',
      title: { 'ru-RU': 'X' },
    });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('positionApi.updatePosition() sends no tenant id', async () => {
    recorded.length = 0;
    const { updatePosition } = await import('@/features/positions/api/positionApi');
    await updatePosition('pos-1', { title: { 'ru-RU': 'X' } });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('positionApi.fetchPosition() sends no tenant id', async () => {
    recorded.length = 0;
    const { fetchPosition } = await import('@/features/positions/api/positionApi');
    await fetchPosition('pos-1');
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('positionApi.archivePosition() sends no tenant id', async () => {
    recorded.length = 0;
    const { archivePosition } = await import('@/features/positions/api/positionApi');
    await archivePosition('pos-1');
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });
});

describe('MSW mock handler drops tenant_id from body', () => {
  it('mock POST /projects ignores body.tenant_id and warns', async () => {
    const { tryHandle } = await import('../mocks/handlers');
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const result = tryHandle({
      method: 'POST',
      url: '/projects',
      data: JSON.stringify({ tenant_id: 'tenant-evil', code: 'X', name: { 'ru-RU': 'X' } }),
      headers: { 'X-Mock-Tenant-Id': 'tenant-acme' },
    } as AxiosRequestConfig);
    expect(result?.status).toBe(201);
    const created = result?.body as { tenant_id: string };
    // Tenant must come from the mock-auth header, NEVER from body.
    expect(created.tenant_id).toBe('tenant-acme');
    expect(warnSpy).toHaveBeenCalled();
    warnSpy.mockRestore();
  });

  it('mock GET /projects derives tenant from header, not query', async () => {
    const { tryHandle } = await import('../mocks/handlers');
    const result = tryHandle({
      method: 'GET',
      url: '/projects?tenantId=tenant-evil',
      headers: { 'X-Mock-Tenant-Id': 'tenant-acme' },
    } as AxiosRequestConfig);
    expect(result?.status).toBe(200);
    const body = result?.body as { items: { tenant_id: string }[] };
    for (const p of body.items) {
      expect(p.tenant_id).toBe('tenant-acme');
    }
  });
});
