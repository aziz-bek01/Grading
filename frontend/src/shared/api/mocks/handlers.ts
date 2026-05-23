/**
 * Lightweight in-process mock layer (no MSW dependency).
 *
 * Activated via VITE_USE_MSW=true. Hooked into the existing axios httpClient
 * through an `adapter` that short-circuits matching URLs.
 *
 * Tenant-context contract (mirrors the real backend — see security blueprint
 * API-13 and master plan §10 rule 13):
 *   - The active tenant is NEVER read from the request body or query string.
 *   - The real backend derives it from the JWT (`tenant_id` claim).
 *   - This mock derives it from an `X-Mock-Tenant-Id` header set by the
 *     auth bootstrap (see {@link setMockTenantHeaderInterceptor} in the
 *     httpClient). MOCK-ONLY — this header is invisible to production code.
 *   - If a body contains `tenant_id` / `tenantId`, the mock LOGS A WARNING
 *     and IGNORES the field (matching backend semantics).
 *
 * Endpoints covered (Phase 2):
 *   GET    /projects
 *   GET    /projects/:id
 *   POST   /projects
 *   PATCH  /projects/:id
 *   GET    /projects/:id/workflow-progress
 *   GET    /departments/tree?projectId=
 *   POST   /departments
 *   PATCH  /departments/:id
 *   POST   /departments/:id/archive
 *   GET    /positions?projectId=&departmentId=&status=&page=&size=
 *   GET    /positions/:id
 *   POST   /positions
 *   PATCH  /positions/:id
 *   POST   /positions/:id/archive
 */
import type { AxiosAdapter, AxiosResponse, AxiosRequestConfig } from 'axios';
import {
  mockDb,
  type MockDepartment,
  type MockJobProfile,
  type MockPosition,
  type MockProject,
  type MockQuestionnaire,
} from './fixtures';

const MOCK_TENANT_HEADER = 'x-mock-tenant-id';
const MOCK_DEFAULT_TENANT_ID = 'tenant-acme';

/** Read tenant from the mock auth header (simulates JWT-derived tenant on real backend). */
function resolveMockTenantId(config: AxiosRequestConfig): string {
  const raw = config.headers as Record<string, unknown> | undefined;
  if (!raw) return MOCK_DEFAULT_TENANT_ID;
  // axios headers can be either a plain object or an AxiosHeaders instance.
  const fromObj = (raw[MOCK_TENANT_HEADER] ?? raw['X-Mock-Tenant-Id']) as string | undefined;
  if (typeof fromObj === 'string' && fromObj.length > 0) return fromObj;
  const headers = raw as { get?: (k: string) => string | null | undefined };
  if (typeof headers.get === 'function') {
    const v = headers.get(MOCK_TENANT_HEADER) ?? headers.get('X-Mock-Tenant-Id');
    if (typeof v === 'string' && v.length > 0) return v;
  }
  return MOCK_DEFAULT_TENANT_ID;
}

/** Defensive: if a body carries tenant_id, warn and drop it before merging. */
function stripTenantFromBody<T extends Record<string, unknown>>(
  body: T,
  url: string,
  method: string,
): T {
  if (!body || typeof body !== 'object') return body;
  const has = 'tenant_id' in body || 'tenantId' in body;
  if (has) {
    // eslint-disable-next-line no-console
    console.warn(
      `[mock] ignoring tenant_id in request body for ${method} ${url}. ` +
        'Backend derives tenant from JWT; client must never send it.',
    );
    const clone: Record<string, unknown> = { ...body };
    delete clone.tenant_id;
    delete clone.tenantId;
    return clone as T;
  }
  return body;
}

interface MatchResult {
  status: number;
  body: unknown;
}

function uuid(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID();
  return `mock-${Math.random().toString(36).slice(2)}`;
}

function ok(body: unknown, status = 200): MatchResult {
  return { status, body };
}

function notFound(): MatchResult {
  return { status: 404, body: { code: 'NOT_FOUND', message: 'Resource not found' } };
}

function parseUrl(url: string): { path: string; query: URLSearchParams } {
  const idx = url.indexOf('?');
  const path = idx >= 0 ? url.slice(0, idx) : url;
  const query = new URLSearchParams(idx >= 0 ? url.slice(idx + 1) : '');
  return { path: path.replace(/^\/api\/v1/, '').replace(/\/$/, ''), query };
}

function readBody<T>(config: AxiosRequestConfig): T {
  if (!config.data) return {} as T;
  if (typeof config.data === 'string') return JSON.parse(config.data) as T;
  return config.data as T;
}

function paginate<T>(items: T[], page: number, size: number) {
  const start = page * size;
  const slice = items.slice(start, start + size);
  return {
    items: slice,
    page,
    size,
    total_elements: items.length,
    total_pages: Math.max(1, Math.ceil(items.length / size)),
  };
}

function handleProjects(method: string, path: string, _query: URLSearchParams, config: AxiosRequestConfig): MatchResult | null {
  if (path === '/projects' && method === 'GET') {
    // Real backend filters by JWT-derived tenant; mock reads it from header.
    const tenantId = resolveMockTenantId(config);
    const list = mockDb.projects.filter((p) => p.tenant_id === tenantId);
    return ok({ items: list, page: 0, size: list.length, total_elements: list.length, total_pages: 1 });
  }
  if (path === '/projects' && method === 'POST') {
    const raw = readBody<Partial<MockProject> & Record<string, unknown>>(config);
    const body = stripTenantFromBody(raw, '/projects', 'POST') as Partial<MockProject>;
    const tenantId = resolveMockTenantId(config);
    const next: MockProject = {
      id: uuid(),
      tenant_id: tenantId,
      code: body.code ?? 'NEW',
      name: body.name ?? {},
      description: body.description,
      status: 'DRAFT',
      start_date: body.start_date,
      end_date: body.end_date,
      updated_at: new Date().toISOString(),
    };
    mockDb.projects.unshift(next);
    return ok(next, 201);
  }
  const projectMatch = /^\/projects\/([^/]+)$/.exec(path);
  if (projectMatch) {
    const id = projectMatch[1];
    const project = mockDb.projects.find((p) => p.id === id);
    if (!project) return notFound();
    if (method === 'GET') return ok(project);
    if (method === 'PATCH') {
      const raw = readBody<Partial<MockProject> & Record<string, unknown>>(config);
      const body = stripTenantFromBody(raw, path, 'PATCH') as Partial<MockProject>;
      Object.assign(project, body, { updated_at: new Date().toISOString() });
      return ok(project);
    }
  }
  const wfMatch = /^\/projects\/([^/]+)\/workflow-progress$/.exec(path);
  if (wfMatch && method === 'GET') {
    const id = wfMatch[1];
    const stages = mockDb.workflowProgress[id] ?? mockDb.workflowProgress['proj-acme-2026'];
    return ok({ project_id: id, stages });
  }
  return null;
}

function handleDepartments(method: string, path: string, query: URLSearchParams, config: AxiosRequestConfig): MatchResult | null {
  if (path === '/departments/tree' && method === 'GET') {
    const projectId = query.get('projectId');
    const list = projectId ? mockDb.departments.filter((d) => d.project_id === projectId) : mockDb.departments;
    return ok({ items: list });
  }
  if (path === '/departments' && method === 'POST') {
    const raw = readBody<Partial<MockDepartment> & Record<string, unknown>>(config);
    const body = stripTenantFromBody(raw, '/departments', 'POST') as Partial<MockDepartment>;
    const next: MockDepartment = {
      id: uuid(),
      project_id: body.project_id ?? 'proj-acme-2026',
      parent_id: body.parent_id ?? null,
      code: body.code ?? 'NEW',
      name: body.name ?? {},
      type: body.type ?? 'DEPARTMENT',
      status: 'ACTIVE',
      updated_at: new Date().toISOString(),
    };
    mockDb.departments.push(next);
    return ok(next, 201);
  }
  const deptMatch = /^\/departments\/([^/]+)$/.exec(path);
  if (deptMatch && method === 'PATCH') {
    const d = mockDb.departments.find((x) => x.id === deptMatch[1]);
    if (!d) return notFound();
    const raw = readBody<Partial<MockDepartment> & Record<string, unknown>>(config);
    const body = stripTenantFromBody(raw, path, 'PATCH') as Partial<MockDepartment>;
    Object.assign(d, body, { updated_at: new Date().toISOString() });
    return ok(d);
  }
  const archMatch = /^\/departments\/([^/]+)\/archive$/.exec(path);
  if (archMatch && method === 'POST') {
    const d = mockDb.departments.find((x) => x.id === archMatch[1]);
    if (!d) return notFound();
    d.status = 'ARCHIVED';
    d.updated_at = new Date().toISOString();
    return ok(d);
  }
  return null;
}

function handlePositions(method: string, path: string, query: URLSearchParams, config: AxiosRequestConfig): MatchResult | null {
  if (path === '/positions' && method === 'GET') {
    const projectId = query.get('projectId');
    const departmentId = query.get('departmentId');
    const status = query.get('status');
    const page = parseInt(query.get('page') ?? '0', 10);
    const size = parseInt(query.get('size') ?? '20', 10);
    let list = projectId ? mockDb.positions.filter((p) => p.project_id === projectId) : mockDb.positions;
    if (departmentId) list = list.filter((p) => p.department_id === departmentId);
    if (status) list = list.filter((p) => p.status === status);
    return ok(paginate(list, page, size));
  }
  if (path === '/positions' && method === 'POST') {
    const raw = readBody<Partial<MockPosition> & Record<string, unknown>>(config);
    const body = stripTenantFromBody(raw, '/positions', 'POST') as Partial<MockPosition>;
    const next: MockPosition = {
      id: uuid(),
      project_id: body.project_id ?? 'proj-acme-2026',
      department_id: body.department_id ?? '',
      code: body.code ?? 'NEW',
      title: body.title ?? {},
      function: body.function,
      category: body.category,
      job_family: body.job_family,
      job_level: body.job_level,
      status: 'DRAFT',
      updated_at: new Date().toISOString(),
    };
    mockDb.positions.unshift(next);
    return ok(next, 201);
  }
  const posMatch = /^\/positions\/([^/]+)$/.exec(path);
  if (posMatch) {
    const p = mockDb.positions.find((x) => x.id === posMatch[1]);
    if (!p) return notFound();
    if (method === 'GET') return ok(p);
    if (method === 'PATCH') {
      const raw = readBody<Partial<MockPosition> & Record<string, unknown>>(config);
      const body = stripTenantFromBody(raw, path, 'PATCH') as Partial<MockPosition>;
      Object.assign(p, body, { updated_at: new Date().toISOString() });
      return ok(p);
    }
  }
  const archMatch = /^\/positions\/([^/]+)\/archive$/.exec(path);
  if (archMatch && method === 'POST') {
    const p = mockDb.positions.find((x) => x.id === archMatch[1]);
    if (!p) return notFound();
    p.status = 'ARCHIVED';
    p.updated_at = new Date().toISOString();
    return ok(p);
  }
  return null;
}

// ============================================================
// Phase 3 — Job Profile + Job Analysis handlers
// ============================================================

const LONG_TEXT_KEYS = [
  'purpose',
  'main_duties',
  'responsibility_area',
  'authority',
  'kpi_expected_results',
  'education_requirements',
  'experience_requirements',
  'knowledge_skills',
  'internal_interactions',
  'external_interactions',
  'working_conditions',
  'documents_regulations',
] as const;

function blankProfile(positionId: string, projectId: string): MockJobProfile {
  const now = new Date().toISOString();
  const fields: Record<string, Record<string, string>> = {};
  for (const k of LONG_TEXT_KEYS) {
    fields[k] = {};
  }
  return {
    ...(fields as unknown as MockJobProfile),
    id: uuid(),
    position_id: positionId,
    project_id: projectId,
    status: 'DRAFT',
    revision_number: 1,
    parent_revision_id: null,
    actualization_date: undefined,
    created_at: now,
    updated_at: now,
    approved_at: null,
    approved_by: null,
  } as MockJobProfile;
}

function handleJobProfiles(method: string, path: string, _query: URLSearchParams, config: AxiosRequestConfig): MatchResult | null {
  // POST /job-profiles  (create)
  if (path === '/job-profiles' && method === 'POST') {
    const raw = readBody<{ position_id: string }>(config);
    const body = stripTenantFromBody(raw as unknown as Record<string, unknown>, '/job-profiles', 'POST') as { position_id: string };
    const pos = mockDb.positions.find((p) => p.id === body.position_id);
    const projectId = pos?.project_id ?? 'proj-acme-2026';
    const existing = mockDb.jobProfiles.find((p) => p.position_id === body.position_id && p.status !== 'ARCHIVED');
    if (existing) {
      return { status: 409, body: { code: 'PROFILE_ALREADY_EXISTS', message: 'Active profile exists' } };
    }
    const created = blankProfile(body.position_id, projectId);
    mockDb.jobProfiles.unshift(created);
    return ok(created, 201);
  }

  // GET /job-profiles/by-position/:positionId
  const byPos = /^\/job-profiles\/by-position\/([^/]+)$/.exec(path);
  if (byPos && method === 'GET') {
    const positionId = byPos[1];
    const active = mockDb.jobProfiles.find((p) => p.position_id === positionId && p.status !== 'ARCHIVED');
    return ok(active ?? null);
  }

  // GET /job-profiles/by-position/:positionId/revisions
  const revs = /^\/job-profiles\/by-position\/([^/]+)\/revisions$/.exec(path);
  if (revs && method === 'GET') {
    const positionId = revs[1];
    const items = mockDb.jobProfiles
      .filter((p) => p.position_id === positionId)
      .map((p) => ({
        id: p.id,
        revision_number: p.revision_number,
        status: p.status,
        approved_at: p.approved_at,
        approved_by: p.approved_by,
        updated_at: p.updated_at,
      }))
      .sort((a, b) => b.revision_number - a.revision_number);
    return ok({ items });
  }

  // GET / PATCH / POST actions on /job-profiles/:id
  const detail = /^\/job-profiles\/([^/]+)$/.exec(path);
  if (detail) {
    const id = detail[1];
    const profile = mockDb.jobProfiles.find((p) => p.id === id);
    if (!profile) return notFound();
    if (method === 'GET') return ok(profile);
    if (method === 'PATCH') {
      if (profile.status !== 'DRAFT') {
        return { status: 409, body: { code: 'PROFILE_LOCKED', message: 'Only draft is editable' } };
      }
      const raw = readBody<Partial<MockJobProfile>>(config);
      const body = stripTenantFromBody(raw as unknown as Record<string, unknown>, path, 'PATCH') as Partial<MockJobProfile>;
      Object.assign(profile, body, { updated_at: new Date().toISOString() });
      return ok(profile);
    }
  }

  const submitMatch = /^\/job-profiles\/([^/]+)\/submit$/.exec(path);
  if (submitMatch && method === 'POST') {
    const p = mockDb.jobProfiles.find((x) => x.id === submitMatch[1]);
    if (!p) return notFound();
    if (p.status !== 'DRAFT') {
      return { status: 409, body: { code: 'INVALID_TRANSITION', message: 'Only DRAFT can be submitted' } };
    }
    p.status = 'UNDER_REVIEW';
    p.updated_at = new Date().toISOString();
    return ok(p);
  }

  const approveMatch = /^\/job-profiles\/([^/]+)\/approve$/.exec(path);
  if (approveMatch && method === 'POST') {
    const p = mockDb.jobProfiles.find((x) => x.id === approveMatch[1]);
    if (!p) return notFound();
    if (p.status !== 'UNDER_REVIEW') {
      return { status: 400, body: { code: 'INVALID_TRANSITION', message: 'Only UNDER_REVIEW can be approved' } };
    }
    p.status = 'APPROVED';
    const now = new Date().toISOString();
    p.approved_at = now;
    p.approved_by = 'Mock Approver';
    p.updated_at = now;
    return ok(p);
  }

  const reqChangesMatch = /^\/job-profiles\/([^/]+)\/request-changes$/.exec(path);
  if (reqChangesMatch && method === 'POST') {
    const p = mockDb.jobProfiles.find((x) => x.id === reqChangesMatch[1]);
    if (!p) return notFound();
    const body = readBody<{ reason?: string }>(config);
    if (!body.reason || body.reason.trim().length < 20) {
      return { status: 400, body: { code: 'REASON_REQUIRED', message: 'Reason min 20 chars' } };
    }
    if (p.status !== 'UNDER_REVIEW') {
      return { status: 400, body: { code: 'INVALID_TRANSITION', message: 'Only UNDER_REVIEW can be returned' } };
    }
    p.status = 'DRAFT';
    p.updated_at = new Date().toISOString();
    return ok(p);
  }

  const archiveMatch = /^\/job-profiles\/([^/]+)\/archive$/.exec(path);
  if (archiveMatch && method === 'POST') {
    const p = mockDb.jobProfiles.find((x) => x.id === archiveMatch[1]);
    if (!p) return notFound();
    const body = readBody<{ reason?: string }>(config);
    if (!body.reason || body.reason.trim().length < 20) {
      return { status: 400, body: { code: 'REASON_REQUIRED', message: 'Reason min 20 chars' } };
    }
    const now = new Date().toISOString();
    p.status = 'ARCHIVED';
    p.archived_at = now;
    p.archive_reason = body.reason;
    p.updated_at = now;
    return ok(p);
  }

  const revisionMatch = /^\/job-profiles\/([^/]+)\/create-revision$/.exec(path);
  if (revisionMatch && method === 'POST') {
    const parent = mockDb.jobProfiles.find((x) => x.id === revisionMatch[1]);
    if (!parent) return notFound();
    if (parent.status !== 'APPROVED') {
      return { status: 400, body: { code: 'INVALID_TRANSITION', message: 'Only APPROVED can spawn a new revision' } };
    }
    const maxRev = Math.max(
      ...mockDb.jobProfiles
        .filter((p) => p.position_id === parent.position_id)
        .map((p) => p.revision_number),
    );
    const now = new Date().toISOString();
    const next: MockJobProfile = {
      ...parent,
      id: uuid(),
      status: 'DRAFT',
      revision_number: maxRev + 1,
      parent_revision_id: parent.id,
      approved_at: null,
      approved_by: null,
      created_at: now,
      updated_at: now,
    };
    mockDb.jobProfiles.unshift(next);
    return ok(next, 201);
  }

  return null;
}

function handleJobAnalysis(method: string, path: string, _query: URLSearchParams, config: AxiosRequestConfig): MatchResult | null {
  if (path === '/job-analysis/templates' && method === 'GET') {
    const items = mockDb.questionnaireTemplates.map((tpl) => ({
      code: tpl.code,
      name: tpl.name,
      description: tpl.description,
      question_count: tpl.questions.length,
    }));
    return ok({ items });
  }

  const byPos = /^\/job-analysis\/by-position\/([^/]+)$/.exec(path);
  if (byPos && method === 'GET') {
    const positionId = byPos[1];
    const items = mockDb.questionnaires
      .filter((q) => q.position_id === positionId)
      .map((q) => ({
        id: q.id,
        name: q.name,
        status: q.status,
        template_code: q.template_code,
        updated_at: q.updated_at,
        answered_count: q.answers.length,
        required_count: q.questions.filter((qq) => qq.required).length,
      }));
    return ok({ items });
  }

  if (path === '/job-analysis' && method === 'POST') {
    const raw = readBody<{ position_id: string; template_code: string }>(config);
    const body = stripTenantFromBody(raw as unknown as Record<string, unknown>, '/job-analysis', 'POST') as { position_id: string; template_code: string };
    const tpl = mockDb.questionnaireTemplates.find((t) => t.code === body.template_code);
    if (!tpl) return { status: 400, body: { code: 'TEMPLATE_NOT_FOUND', message: 'Unknown template' } };
    const pos = mockDb.positions.find((p) => p.id === body.position_id);
    const now = new Date().toISOString();
    const created: MockQuestionnaire = {
      id: uuid(),
      position_id: body.position_id,
      project_id: pos?.project_id ?? 'proj-acme-2026',
      template_code: tpl.code,
      name: tpl.name,
      status: 'DRAFT',
      questions: tpl.questions,
      answers: [],
      created_at: now,
      updated_at: now,
    };
    mockDb.questionnaires.unshift(created);
    return ok(created, 201);
  }

  const detail = /^\/job-analysis\/([^/]+)$/.exec(path);
  if (detail && method === 'GET') {
    const id = detail[1];
    const q = mockDb.questionnaires.find((x) => x.id === id);
    if (!q) return notFound();
    return ok(q);
  }

  const answersMatch = /^\/job-analysis\/([^/]+)\/answers$/.exec(path);
  if (answersMatch && method === 'PATCH') {
    const q = mockDb.questionnaires.find((x) => x.id === answersMatch[1]);
    if (!q) return notFound();
    if (q.status === 'ARCHIVED') {
      return { status: 409, body: { code: 'ARCHIVED', message: 'Archived questionnaire is read-only' } };
    }
    const body = readBody<{ question_id: string; value: unknown }>(config);
    const existing = q.answers.find((a) => a.question_id === body.question_id);
    if (existing) existing.value = body.value;
    else q.answers.push({ question_id: body.question_id, value: body.value });
    if (q.status === 'DRAFT') q.status = 'IN_PROGRESS';
    q.updated_at = new Date().toISOString();
    return ok(q);
  }

  const submitMatch = /^\/job-analysis\/([^/]+)\/submit$/.exec(path);
  if (submitMatch && method === 'POST') {
    const q = mockDb.questionnaires.find((x) => x.id === submitMatch[1]);
    if (!q) return notFound();
    const required = q.questions.filter((qq) => qq.required);
    const answeredRequired = required.filter((qq) =>
      q.answers.some((a) => a.question_id === qq.id && a.value !== null && a.value !== undefined && a.value !== ''),
    );
    if (answeredRequired.length !== required.length) {
      return { status: 400, body: { code: 'QUESTIONNAIRE_INCOMPLETE', message: 'Required answers missing' } };
    }
    q.status = 'COMPLETED';
    q.updated_at = new Date().toISOString();
    return ok(q);
  }

  const archiveMatch = /^\/job-analysis\/([^/]+)\/archive$/.exec(path);
  if (archiveMatch && method === 'POST') {
    const q = mockDb.questionnaires.find((x) => x.id === archiveMatch[1]);
    if (!q) return notFound();
    const body = readBody<{ reason?: string }>(config);
    if (!body.reason || body.reason.trim().length < 20) {
      return { status: 400, body: { code: 'REASON_REQUIRED', message: 'Reason min 20 chars' } };
    }
    q.status = 'ARCHIVED';
    q.updated_at = new Date().toISOString();
    return ok(q);
  }

  return null;
}

export function tryHandle(config: AxiosRequestConfig): MatchResult | null {
  const method = (config.method ?? 'get').toUpperCase();
  const url = config.url ?? '';
  const { path, query } = parseUrl(url);
  return (
    handleProjects(method, path, query, config) ??
    handleDepartments(method, path, query, config) ??
    handlePositions(method, path, query, config) ??
    handleJobProfiles(method, path, query, config) ??
    handleJobAnalysis(method, path, query, config) ??
    null
  );
}

/** Axios adapter that uses our mock handlers; falls back to network for unmatched paths. */
export function createMockAdapter(realAdapter: AxiosAdapter | undefined): AxiosAdapter {
  return async (config) => {
    const matched = tryHandle(config);
    if (!matched) {
      if (realAdapter) return realAdapter(config);
      throw new Error(`[mock] no handler for ${config.method?.toUpperCase()} ${config.url}`);
    }
    const response: AxiosResponse = {
      data: matched.body,
      status: matched.status,
      statusText: matched.status === 201 ? 'Created' : 'OK',
      headers: { 'content-type': 'application/json' },
      config: config as never,
    } as AxiosResponse;
    if (matched.status >= 400) {
      const err: Error & { response?: AxiosResponse; config?: AxiosRequestConfig } = new Error(
        (matched.body as { message?: string })?.message ?? `HTTP ${matched.status}`,
      );
      err.response = response;
      err.config = config;
      throw err;
    }
    return response;
  };
}
