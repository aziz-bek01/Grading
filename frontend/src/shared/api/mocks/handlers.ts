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
import { handleUsers } from '@/features/users-access/mocks/userHandlers';
import { handleAudit } from '@/features/audit/mocks/auditHandlers';
import { handleClients } from '@/features/clients/mocks/clientHandlers';
import {
  mockDb,
  type MockCalibrationEvent,
  type MockDepartment,
  type MockEvaluation,
  type MockEvaluationScore,
  type MockEvaluationStatus,
  type MockFactor,
  type MockFactorLevel,
  type MockGrade,
  type MockGradeBand,
  type MockGradeStructure,
  type MockJobProfile,
  type MockMethodology,
  type MockMethodologyVersion,
  type MockPosition,
  type MockProject,
  type MockQuestionnaire,
  type MockScoringMode,
} from './fixtures';

const MOCK_TENANT_HEADER = 'x-mock-tenant-id';
const MOCK_DEFAULT_TENANT_ID = '11111111-1111-1111-1111-111111111111';

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
  /** Optional content-type override (default: application/json). Used by blob/template responses. */
  contentType?: string;
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

function parseUrl(url: string, params?: Record<string, unknown>): { path: string; query: URLSearchParams } {
  const idx = url.indexOf('?');
  const path = idx >= 0 ? url.slice(0, idx) : url;
  const query = new URLSearchParams(idx >= 0 ? url.slice(idx + 1) : '');
  // Axios passes `params` separately from `url`; merge them in so handlers can
  // read query parameters regardless of how the caller built the request.
  if (params && typeof params === 'object') {
    for (const [k, v] of Object.entries(params)) {
      if (v === undefined || v === null) continue;
      if (!query.has(k)) query.set(k, String(v));
    }
  }
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
      name_i18n: body.name_i18n ?? {},
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
    const found = mockDb.workflowProgress[id] ?? mockDb.workflowProgress['proj-acme-2026'];
    // MVP 2 Phase 1 shape: top-level workflow snapshot + stages[].
    return ok({ ...found, projectId: id });
  }

  const wfAdvance = /^\/projects\/([^/]+)\/workflow\/advance$/.exec(path);
  if (wfAdvance && method === 'POST') {
    const id = wfAdvance[1];
    const wf = mockDb.workflowProgress[id] ?? mockDb.workflowProgress['proj-acme-2026'];
    const body = readBody<{ targetStage?: string }>(config);
    if (body.targetStage) wf.currentStage = body.targetStage;
    return ok({ ...wf, projectId: id });
  }

  const wfRecompute = /^\/projects\/([^/]+)\/workflow\/recompute$/.exec(path);
  if (wfRecompute && method === 'POST') {
    const id = wfRecompute[1];
    const wf = mockDb.workflowProgress[id] ?? mockDb.workflowProgress['proj-acme-2026'];
    return ok({ ...wf, projectId: id });
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
      name_i18n: body.name_i18n ?? {},
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
      title_i18n: body.title_i18n ?? {},
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
    previous_revision_id: null,
    actualization_date: undefined,
    created_at: now,
    updated_at: now,
    approved_at: null,
    approved_by: null,
  } as MockJobProfile;
}

function handleJobProfiles(method: string, path: string, _query: URLSearchParams, config: AxiosRequestConfig): MatchResult | null {
  // POST /positions/:positionId/job-profile  (create — positionId from path,
  // empty/minimal body; mirrors PositionJobProfileController#create → 201).
  const posCreate = /^\/positions\/([^/]+)\/job-profile$/.exec(path);
  if (posCreate && method === 'POST') {
    const positionId = posCreate[1];
    const pos = mockDb.positions.find((p) => p.id === positionId);
    const projectId = pos?.project_id ?? 'proj-acme-2026';
    const existing = mockDb.jobProfiles.find((p) => p.position_id === positionId && p.status !== 'ARCHIVED');
    if (existing) {
      return { status: 409, body: { code: 'PROFILE_ALREADY_EXISTS', message: 'Active profile exists' } };
    }
    const created = blankProfile(positionId, projectId);
    mockDb.jobProfiles.unshift(created);
    return ok(created, 201);
  }

  // GET /positions/:positionId/job-profile  (active profile or 204 No Content;
  // mirrors PositionJobProfileController#getActive).
  const posActive = /^\/positions\/([^/]+)\/job-profile$/.exec(path);
  if (posActive && method === 'GET') {
    const positionId = posActive[1];
    const active = mockDb.jobProfiles.find((p) => p.position_id === positionId && p.status !== 'ARCHIVED');
    if (!active) return { status: 204, body: null };
    return ok(active);
  }

  // GET /positions/:positionId/job-profile/revisions  (BARE array; mirrors
  // PositionJobProfileController#listRevisions).
  const posRevs = /^\/positions\/([^/]+)\/job-profile\/revisions$/.exec(path);
  if (posRevs && method === 'GET') {
    const positionId = posRevs[1];
    const items = mockDb.jobProfiles
      .filter((p) => p.position_id === positionId)
      .slice()
      .sort((a, b) => b.revision_number - a.revision_number);
    return ok(items);
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
      previous_revision_id: parent.id,
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

/**
 * Serialize an internal mock question into the REAL backend wire shape
 * (`JobAnalysisQuestionResponse`, global SNAKE_CASE). The backend exposes only
 * `options: string[]` — choice questions carry their choice codes; RATING_SCALE
 * carries numeric scale points. No per-locale choice labels, no scale bounds.
 */
function serializeQuestionToBackend(q: MockQuestionnaire['questions'][number]) {
  let options: string[] | null = null;
  if (q.question_type === 'SINGLE_CHOICE' || q.question_type === 'MULTI_CHOICE') {
    options = (q.choices ?? []).map((c) => c.code);
  } else if (q.question_type === 'RATING_SCALE') {
    const min = q.scale_min ?? 1;
    const max = q.scale_max ?? 5;
    options = Array.from({ length: max - min + 1 }, (_, i) => String(min + i));
  }
  return {
    id: q.id,
    code: q.code,
    prompt_i18n: q.prompt,
    help_text_i18n: q.help_text ?? {},
    question_type: q.question_type,
    options,
    required: q.required,
    sort_order: q.sort_order,
  };
}

/** Serialize a mock questionnaire into the backend `QuestionnaireResponse` shape. */
function serializeQuestionnaireToBackend(q: MockQuestionnaire) {
  return {
    id: q.id,
    project_id: q.project_id,
    position_id: q.position_id,
    template_code: q.template_code,
    name_i18n: q.name,
    status: q.status,
    version: 1,
    questions: q.questions.map(serializeQuestionToBackend),
  };
}

/**
 * Serialize a mock answer (`{ question_id, value }`) into the backend
 * `AnswerResponse` shape, splitting `value` across answer_text/choices/number
 * based on the question's type.
 */
function serializeAnswerToBackend(q: MockQuestionnaire, ans: { question_id: string; value: unknown }) {
  const question = q.questions.find((qq) => qq.id === ans.question_id);
  const type = question?.question_type;
  let answer_text: string | null = null;
  let answer_choices: string[] | null = null;
  let answer_number: number | null = null;
  if (type === 'SINGLE_CHOICE') {
    answer_choices = typeof ans.value === 'string' ? [ans.value] : null;
  } else if (type === 'MULTI_CHOICE') {
    answer_choices = Array.isArray(ans.value) ? (ans.value as string[]) : null;
  } else if (type === 'NUMBER' || type === 'RATING_SCALE') {
    answer_number = typeof ans.value === 'number' ? ans.value : null;
  } else {
    answer_text = typeof ans.value === 'string' ? ans.value : null;
  }
  return {
    id: `${q.id}-ans-${ans.question_id}`,
    questionnaire_id: q.id,
    question_id: ans.question_id,
    answer_text,
    answer_choices,
    answer_number,
    respondent_user_id: null,
    submitted_at: q.updated_at,
  };
}

function handleJobAnalysis(method: string, path: string, _query: URLSearchParams, config: AxiosRequestConfig): MatchResult | null {
  // GET /questionnaire-templates  → { items: [{ code, name_i18n, question_count }] }
  if (path === '/questionnaire-templates' && method === 'GET') {
    const items = mockDb.questionnaireTemplates.map((tpl) => ({
      code: tpl.code,
      name_i18n: tpl.name,
      question_count: tpl.questions.length,
    }));
    return ok({ items });
  }

  // GET /positions/:positionId/questionnaires  → BARE QuestionnaireResponse[]
  const byPos = /^\/positions\/([^/]+)\/questionnaires$/.exec(path);
  if (byPos && method === 'GET') {
    const positionId = byPos[1];
    const items = mockDb.questionnaires
      .filter((q) => q.position_id === positionId)
      .map(serializeQuestionnaireToBackend);
    return ok(items);
  }

  // POST /positions/:positionId/questionnaires  body { template_code } → 201
  const create = /^\/positions\/([^/]+)\/questionnaires$/.exec(path);
  if (create && method === 'POST') {
    const positionId = create[1];
    const body = readBody<{ template_code: string }>(config);
    const tpl = mockDb.questionnaireTemplates.find((t) => t.code === body.template_code);
    if (!tpl) return { status: 400, body: { code: 'TEMPLATE_NOT_FOUND', message: 'Unknown template' } };
    const pos = mockDb.positions.find((p) => p.id === positionId);
    const now = new Date().toISOString();
    const created: MockQuestionnaire = {
      id: uuid(),
      position_id: positionId,
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
    return ok(serializeQuestionnaireToBackend(created), 201);
  }

  // GET /questionnaires/:id  → QuestionnaireResponse
  const detail = /^\/questionnaires\/([^/]+)$/.exec(path);
  if (detail && method === 'GET') {
    const q = mockDb.questionnaires.find((x) => x.id === detail[1]);
    if (!q) return notFound();
    return ok(serializeQuestionnaireToBackend(q));
  }

  // GET /questionnaires/:id/answers  → BARE AnswerResponse[]
  const answersList = /^\/questionnaires\/([^/]+)\/answers$/.exec(path);
  if (answersList && method === 'GET') {
    const q = mockDb.questionnaires.find((x) => x.id === answersList[1]);
    if (!q) return notFound();
    return ok(q.answers.map((a) => serializeAnswerToBackend(q, a)));
  }

  // PATCH /questionnaires/:id/answers/:questionId  body answer_text|answer_choices|answer_number
  const answerUpsert = /^\/questionnaires\/([^/]+)\/answers\/([^/]+)$/.exec(path);
  if (answerUpsert && method === 'PATCH') {
    const q = mockDb.questionnaires.find((x) => x.id === answerUpsert[1]);
    if (!q) return notFound();
    if (q.status === 'ARCHIVED') {
      return { status: 409, body: { code: 'ARCHIVED', message: 'Archived questionnaire is read-only' } };
    }
    const questionId = answerUpsert[2];
    const question = q.questions.find((qq) => qq.id === questionId);
    const body = readBody<{ answer_text?: string | null; answer_choices?: string[] | null; answer_number?: number | null }>(config);
    // Reduce the typed backend body back to the internal union `value`.
    let value: unknown;
    if (question?.question_type === 'SINGLE_CHOICE') value = body.answer_choices?.[0] ?? null;
    else if (question?.question_type === 'MULTI_CHOICE') value = body.answer_choices ?? [];
    else if (question?.question_type === 'NUMBER' || question?.question_type === 'RATING_SCALE') value = body.answer_number ?? null;
    else value = body.answer_text ?? null;
    const existing = q.answers.find((a) => a.question_id === questionId);
    if (existing) existing.value = value;
    else q.answers.push({ question_id: questionId, value });
    if (q.status === 'DRAFT') q.status = 'IN_PROGRESS';
    q.updated_at = new Date().toISOString();
    const upserted = q.answers.find((a) => a.question_id === questionId)!;
    return ok(serializeAnswerToBackend(q, upserted));
  }

  // POST /questionnaires/:id/submit  → QuestionnaireResponse
  const submitMatch = /^\/questionnaires\/([^/]+)\/submit$/.exec(path);
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
    return ok(serializeQuestionnaireToBackend(q));
  }

  // POST /questionnaires/:id/archive  body { reason } → QuestionnaireResponse
  const archiveMatch = /^\/questionnaires\/([^/]+)\/archive$/.exec(path);
  if (archiveMatch && method === 'POST') {
    const q = mockDb.questionnaires.find((x) => x.id === archiveMatch[1]);
    if (!q) return notFound();
    const body = readBody<{ reason?: string }>(config);
    if (!body.reason || body.reason.trim().length < 10) {
      return { status: 400, body: { code: 'REASON_REQUIRED', message: 'Reason min 10 chars' } };
    }
    q.status = 'ARCHIVED';
    q.updated_at = new Date().toISOString();
    return ok(serializeQuestionnaireToBackend(q));
  }

  return null;
}

// ============================================================
// Phase 4 — Methodology handlers
// ============================================================

const TOLERANCE = 1e-4;

function cloneFactorWithNewIds(f: MockFactor, newVersionId: string): MockFactor {
  const newFactorId = uuid();
  return {
    ...f,
    id: newFactorId,
    methodology_version_id: newVersionId,
    levels: (f.levels ?? []).map((lvl) => ({
      ...lvl,
      id: uuid(),
      factor_id: newFactorId,
    })),
  };
}

function methodologyById(id: string): MockMethodology | undefined {
  return mockDb.methodologies.find((m) => m.id === id);
}

function versionById(id: string): MockMethodologyVersion | undefined {
  return mockDb.methodologyVersions.find((v) => v.id === id);
}

function lockedConflict(): MatchResult {
  return {
    status: 409,
    body: { code: 'METHODOLOGY_LOCKED', message: 'Version is locked or approved; edits not allowed' },
  };
}

function validateWeights(version: MockMethodologyVersion): { ok: boolean; sum: number; target: number } {
  const target = version.scoring_mode === 'WEIGHTED_POINTS' ? 100 : version.target_total_points ?? 100;
  if (version.scoring_mode === 'DIRECT_POINTS') return { ok: true, sum: 0, target };
  const sum = version.factors.reduce((acc, f) => acc + (f.weight ?? 0), 0);
  return { ok: Math.abs(sum - target) <= TOLERANCE, sum, target };
}

function validatePrimaryLocaleNames(version: MockMethodologyVersion): boolean {
  return version.factors.every((f) => {
    const ru = f.name_i18n?.['ru-RU'];
    return typeof ru === 'string' && ru.trim().length > 0;
  });
}

function summariseVersion(v: MockMethodologyVersion) {
  return {
    id: v.id,
    version_number: v.version_number,
    status: v.status,
    scoring_mode: v.scoring_mode,
    approved_at: v.approved_at ?? null,
    locked_at: v.locked_at ?? null,
    updated_at: v.updated_at,
  };
}

/**
 * Mock template row shape mirroring backend `MethodologyTemplateResponse`
 * (Epic E): built-ins (id=null) ∪ tenant CUSTOM templates (id=uuid).
 */
interface MockTemplate {
  id: string | null;
  code: string;
  name_i18n: Record<string, string>;
  description_i18n?: Record<string, string>;
  factor_count: number;
  default_scoring_mode: MockScoringMode;
  source: 'BUILTIN' | 'CUSTOM';
  is_builtin: boolean;
}

const BUILTIN_TEMPLATES: MockTemplate[] = [
  {
    id: null,
    code: 'CLASSIC_8_FACTOR',
    name_i18n: { 'ru-RU': 'Классическая 8-факторная', 'en-US': 'Classic 8-factor' },
    description_i18n: {
      'ru-RU': 'Стандартные 8 факторов: знания, опыт, сложность, ответственность и др.',
      'en-US': 'The standard 8 factors: knowledge, experience, complexity, responsibility, etc.',
    },
    factor_count: 8,
    default_scoring_mode: 'WEIGHTED_POINTS',
    source: 'BUILTIN',
    is_builtin: true,
  },
  {
    id: null,
    code: 'EXTENDED_11_CRITERIA',
    name_i18n: { 'ru-RU': 'Расширенная 11-критериальная', 'en-US': 'Extended 11-criteria' },
    description_i18n: {
      'ru-RU': '11 критериев для крупных холдингов и расширенных проектов.',
      'en-US': '11 criteria for large holdings and extended projects.',
    },
    factor_count: 11,
    default_scoring_mode: 'WEIGHTED_POINTS',
    source: 'BUILTIN',
    is_builtin: true,
  },
  {
    id: null,
    code: 'CUSTOM',
    name_i18n: { 'ru-RU': 'Пользовательская', 'en-US': 'Custom' },
    description_i18n: {
      'ru-RU': 'Пустая методология — настроите факторы и уровни вручную.',
      'en-US': 'An empty methodology — configure factors and levels manually.',
    },
    factor_count: 0,
    default_scoring_mode: 'DIRECT_POINTS',
    source: 'BUILTIN',
    is_builtin: true,
  },
];

/**
 * In-memory tenant CUSTOM-template store (Epic E). Module-level so it survives
 * across requests within a session (mirrors a tenant-scoped backend table).
 * Tests that need a clean slate can splice it via {@link resetMockCustomTemplates}.
 */
const customTemplates: MockTemplate[] = [];

/** Reset hook for tests — clears the custom-template store. */
export function resetMockCustomTemplates(): void {
  customTemplates.length = 0;
}

function templateCodeTaken(code: string): boolean {
  const upper = code.toUpperCase();
  return (
    BUILTIN_TEMPLATES.some((tpl) => tpl.code.toUpperCase() === upper) ||
    customTemplates.some((tpl) => tpl.code.toUpperCase() === upper)
  );
}

function handleMethodologyTemplates(
  method: string,
  path: string,
  config: AxiosRequestConfig,
): MatchResult | null {
  if (path === '/methodology-templates' && method === 'GET') {
    // Built-ins ∪ tenant CUSTOM (active only — archived disappear from the list).
    return ok({ items: [...BUILTIN_TEMPLATES, ...customTemplates] });
  }

  // POST /methodology-templates — create a CUSTOM template (alt save-as route).
  if (path === '/methodology-templates' && method === 'POST') {
    const raw = readBody<{ code?: string; name_i18n?: Record<string, string>; description_i18n?: Record<string, string> }>(config);
    const body = stripTenantFromBody(raw as Record<string, unknown>, path, 'POST') as typeof raw;
    return createCustomTemplate(body);
  }

  // PUT /methodology-templates/{id} — rename a CUSTOM template. Built-in /
  // cross-tenant ids → 404 (built-ins have id=null, never matched here).
  const detail = /^\/methodology-templates\/([^/]+)$/.exec(path);
  if (detail) {
    const id = detail[1];
    const tpl = customTemplates.find((c) => c.id === id);
    if (method === 'PUT') {
      if (!tpl) return notFound();
      const raw = readBody<{ name_i18n?: Record<string, string>; description_i18n?: Record<string, string> }>(config);
      const body = stripTenantFromBody(raw as Record<string, unknown>, path, 'PUT') as typeof raw;
      if (!body.name_i18n?.['ru-RU']?.trim()) {
        return { status: 400, body: { code: 'VALIDATION_FAILED', message: 'name_i18n.ru-RU required' } };
      }
      tpl.name_i18n = body.name_i18n;
      tpl.description_i18n = body.description_i18n;
      return ok(tpl);
    }
    if (method === 'DELETE') {
      if (!tpl) return notFound();
      // Archive = remove from the active list (the picker stops showing it).
      const idx = customTemplates.indexOf(tpl);
      customTemplates.splice(idx, 1);
      return { status: 204, body: null };
    }
  }
  return null;
}

/**
 * Shared CUSTOM-template creation used by both `POST /methodology-templates`
 * and `POST /methodologies/{id}/save-as-template`. Enforces unique code
 * (409 METHODOLOGY_TEMPLATE_CODE_EXISTS) and ru-RU name (400).
 */
function createCustomTemplate(body: {
  code?: string;
  name_i18n?: Record<string, string>;
  description_i18n?: Record<string, string>;
  factor_count?: number;
  default_scoring_mode?: MockScoringMode;
}): MatchResult {
  const code = (body.code ?? '').trim();
  if (!code || !body.name_i18n?.['ru-RU']?.trim()) {
    return { status: 400, body: { code: 'VALIDATION_FAILED', message: 'code + name_i18n.ru-RU required' } };
  }
  if (templateCodeTaken(code)) {
    return {
      status: 409,
      body: {
        code: 'METHODOLOGY_TEMPLATE_CODE_EXISTS',
        message: `A methodology template with code ${code} already exists`,
      },
    };
  }
  const tpl: MockTemplate = {
    id: uuid(),
    code,
    name_i18n: body.name_i18n,
    description_i18n: body.description_i18n,
    factor_count: body.factor_count ?? 0,
    default_scoring_mode: body.default_scoring_mode ?? 'WEIGHTED_POINTS',
    source: 'CUSTOM',
    is_builtin: false,
  };
  customTemplates.unshift(tpl);
  return ok(tpl, 201);
}

function handleMethodologies(method: string, path: string, query: URLSearchParams, config: AxiosRequestConfig): MatchResult | null {
  if (path === '/methodologies' && method === 'GET') {
    const projectId = query.get('projectId');
    const items = projectId
      ? mockDb.methodologies.filter((m) => m.project_id === projectId)
      : mockDb.methodologies;
    return ok({ items });
  }

  if ((path === '/methodologies' || path === '/methodologies/from-template') && method === 'POST') {
    const raw = readBody<Partial<MockMethodology> & { template_code?: 'CLASSIC_8_FACTOR' | 'EXTENDED_11_CRITERIA' | 'CUSTOM'; source_template_code?: 'CLASSIC_8_FACTOR' | 'EXTENDED_11_CRITERIA' | null; scoring_mode?: MockScoringMode; target_total_points?: number }>(config);
    const body = stripTenantFromBody(raw as unknown as Record<string, unknown>, path, 'POST') as typeof raw;
    if (!body.project_id || !body.code || !body.name_i18n?.['ru-RU']) {
      return { status: 400, body: { code: 'VALIDATION_FAILED', message: 'project_id, code, name_i18n.ru-RU required' } };
    }
    const methodologyId = uuid();
    const versionId = uuid();
    const now = new Date().toISOString();
    // The fixed FE payload sends `template_code` (the backend's
    // CreateMethodologyFromTemplateRequest contract); legacy fields are kept as
    // a fallback for older callers/tests. CUSTOM seeds zero factors.
    const templateCode = body.template_code ?? body.source_template_code ?? body.methodology_type ?? null;
    const seedTplCode = templateCode && templateCode !== 'CUSTOM' ? templateCode : null;
    const factors: MockFactor[] =
      seedTplCode
        ? mockDb.methodologyTemplates.find((tpl) => tpl.code === seedTplCode)?.factors(versionId) ?? []
        : [];
    const version: MockMethodologyVersion = {
      id: versionId,
      methodology_id: methodologyId,
      project_id: body.project_id,
      version_number: 1,
      status: 'DRAFT',
      scoring_mode: body.scoring_mode ?? 'WEIGHTED_POINTS',
      target_total_points: body.target_total_points ?? (body.scoring_mode === 'WEIGHTED_POINTS' ? 100 : 1000),
      factors,
      parent_version_id: null,
      created_at: now,
      updated_at: now,
    };
    const meth: MockMethodology = {
      id: methodologyId,
      project_id: body.project_id,
      code: body.code,
      name_i18n: body.name_i18n ?? {},
      description_i18n: body.description_i18n,
      // Derive the type from the resolved template_code so the from-template
      // path no longer depends on the (now-removed) `methodology_type` field.
      methodology_type: templateCode ?? 'CUSTOM',
      status: 'ACTIVE',
      // Echoed back so the UI's post-create deep-link navigation is exercised
      // under MSW exactly as it is against the real backend.
      latest_version_id: versionId,
      active_version_id: null,
      active_version_number: null,
      active_version_status: null,
      created_at: now,
      updated_at: now,
    };
    mockDb.methodologies.unshift(meth);
    mockDb.methodologyVersions.unshift(version);
    return ok(meth, 201);
  }

  const detail = /^\/methodologies\/([^/]+)$/.exec(path);
  if (detail) {
    const m = methodologyById(detail[1]);
    if (!m) return notFound();
    if (method === 'GET') return ok(m);
    if (method === 'PATCH') {
      const raw = readBody<Partial<MockMethodology>>(config);
      const body = stripTenantFromBody(raw as unknown as Record<string, unknown>, path, 'PATCH') as Partial<MockMethodology>;
      Object.assign(m, body, { updated_at: new Date().toISOString() });
      return ok(m);
    }
  }

  const archive = /^\/methodologies\/([^/]+)\/archive$/.exec(path);
  if (archive && method === 'POST') {
    const m = methodologyById(archive[1]);
    if (!m) return notFound();
    const body = readBody<{ reason?: string }>(config);
    if (!body.reason || body.reason.trim().length < 20) {
      return { status: 400, body: { code: 'REASON_REQUIRED', message: 'Reason min 20 chars' } };
    }
    m.status = 'ARCHIVED';
    m.archived_at = new Date().toISOString();
    m.archive_reason = body.reason;
    return ok(m);
  }

  // POST /methodologies/{id}/save-as-template (Epic E) — snapshot the
  // methodology's latest version into a tenant CUSTOM template. Enforces unique
  // code (409 METHODOLOGY_TEMPLATE_CODE_EXISTS) just like the backend.
  const saveAsTemplate = /^\/methodologies\/([^/]+)\/save-as-template$/.exec(path);
  if (saveAsTemplate && method === 'POST') {
    const m = methodologyById(saveAsTemplate[1]);
    if (!m) return notFound();
    const raw = readBody<{ code?: string; name_i18n?: Record<string, string>; description_i18n?: Record<string, string> }>(config);
    const body = stripTenantFromBody(raw as Record<string, unknown>, path, 'POST') as typeof raw;
    // Snapshot the latest version's factor count so the picker shows it.
    const latest = mockDb.methodologyVersions
      .filter((v) => v.methodology_id === m.id)
      .sort((a, b) => b.version_number - a.version_number)[0];
    return createCustomTemplate({
      code: body.code,
      name_i18n: body.name_i18n,
      description_i18n: body.description_i18n,
      factor_count: latest?.factors.length ?? 0,
      default_scoring_mode: latest?.scoring_mode ?? 'WEIGHTED_POINTS',
    });
  }

  // Versions list under a methodology
  const versionsList = /^\/methodologies\/([^/]+)\/versions$/.exec(path);
  if (versionsList) {
    const methodologyId = versionsList[1];
    if (method === 'GET') {
      // Real backend returns a BARE ARRAY here (not a `{items}` envelope).
      const items = mockDb.methodologyVersions
        .filter((v) => v.methodology_id === methodologyId)
        .map(summariseVersion);
      return ok(items);
    }
  }

  return null;
}

/**
 * Clone the source version into a fresh DRAFT. Shared by the real-backend
 * `POST /methodology-versions/{sourceVersionId}/create-new-version` handler so
 * the clone logic lives in ONE place.
 */
function createNewVersionFromSource(
  parent: MockMethodologyVersion,
): MockMethodologyVersion | null {
  const m = methodologyById(parent.methodology_id);
  if (!m) return null;
  const newVersionId = uuid();
  const now = new Date().toISOString();
  const next: MockMethodologyVersion = {
    id: newVersionId,
    methodology_id: parent.methodology_id,
    project_id: parent.project_id,
    version_number:
      Math.max(
        ...mockDb.methodologyVersions
          .filter((v) => v.methodology_id === parent.methodology_id)
          .map((v) => v.version_number),
      ) + 1,
    status: 'DRAFT',
    scoring_mode: parent.scoring_mode,
    target_total_points: parent.target_total_points,
    factors: parent.factors.map((f) => cloneFactorWithNewIds(f, newVersionId)),
    parent_version_id: parent.id,
    created_at: now,
    updated_at: now,
  };
  mockDb.methodologyVersions.unshift(next);
  m.latest_version_id = newVersionId;
  m.updated_at = now;
  return next;
}

function handleMethodologyVersions(method: string, path: string, _query: URLSearchParams, config: AxiosRequestConfig): MatchResult | null {
  // GET /methodology-versions/:id/factors — BARE ARRAY of FactorResponse,
  // WITHOUT embedded levels (matches the real backend; the FE aggregator GETs
  // each factor's levels separately). Must precede the bare-detail regex below.
  const factorsList = /^\/methodology-versions\/([^/]+)\/factors$/.exec(path);
  if (factorsList && method === 'GET') {
    const v = versionById(factorsList[1]);
    if (!v) return notFound();
    // Strip the internally-stored nested `levels` from the serialized response.
    const items = v.factors.map(({ levels: _levels, ...rest }) => rest);
    return ok(items);
  }

  // POST /methodology-versions/:sourceVersionId/create-new-version — NO body;
  // clones the source version into a fresh DRAFT (real-backend contract).
  const createNew = /^\/methodology-versions\/([^/]+)\/create-new-version$/.exec(path);
  if (createNew && method === 'POST') {
    const parent = versionById(createNew[1]);
    if (!parent) return notFound();
    const next = createNewVersionFromSource(parent);
    if (!next) return notFound();
    return ok(next, 201);
  }

  // PATCH /methodology-versions/:id — version scoring metadata (scoring_mode +
  // target_total_points), DRAFT-only like the real backend's immutability guard.
  const metadataPatch = /^\/methodology-versions\/([^/]+)$/.exec(path);
  if (metadataPatch && method === 'PATCH') {
    const v = versionById(metadataPatch[1]);
    if (!v) return notFound();
    if (v.status !== 'DRAFT') return lockedConflict();
    const raw = readBody<Record<string, unknown>>(config);
    const body = stripTenantFromBody(raw, path, 'PATCH') as {
      scoring_mode?: MockMethodologyVersion['scoring_mode'];
      target_total_points?: number;
    };
    if (body.scoring_mode != null) v.scoring_mode = body.scoring_mode;
    if (body.target_total_points != null) v.target_total_points = body.target_total_points;
    v.updated_at = new Date().toISOString();
    const { factors: _factors, ...rest } = v;
    return ok(rest);
  }

  const detail = /^\/methodology-versions\/([^/]+)$/.exec(path);
  if (detail && method === 'GET') {
    const v = versionById(detail[1]);
    if (!v) return notFound();
    // Real backend's version-detail object does NOT carry `factors` — strip it
    // so the MSW shape matches and exercises the FE aggregator.
    const { factors: _factors, ...versionObject } = v;
    return ok(versionObject);
  }

  // D-406 / PC4-4 — approve lands in APPROVED, NOT LOCKED.
  // The master plan §14 state machine requires a two-step APPROVE → LOCK.
  // The previous mock collapsed both into LOCKED which masked the intermediate
  // APPROVED state from frontend tests. Real backend enforces the split via
  // MethodologyVersionStatusTransitionPolicy + DB trigger.
  const approve = /^\/methodology-versions\/([^/]+)\/approve$/.exec(path);
  if (approve && method === 'POST') {
    const v = versionById(approve[1]);
    if (!v) return notFound();
    if (v.status !== 'DRAFT') {
      return {
        status: 409,
        body: {
          code: 'INVALID_TRANSITION',
          message: `Cannot APPROVE a methodology version in state ${v.status}`,
        },
      };
    }
    if (!validatePrimaryLocaleNames(v)) {
      return { status: 400, body: { code: 'PRIMARY_LOCALE_INCOMPLETE', message: 'Primary locale (ru-RU) missing on some factors' } };
    }
    const wv = validateWeights(v);
    if (!wv.ok) {
      return { status: 400, body: { code: 'WEIGHT_SUM_INVALID', message: `Sum ${wv.sum} != target ${wv.target}` } };
    }
    const now = new Date().toISOString();
    v.status = 'APPROVED';
    v.approved_at = now;
    v.approved_by = 'mock-user-approver';
    v.approved_by_name = 'Mock Approver';
    v.updated_at = now;
    const m = methodologyById(v.methodology_id);
    if (m) {
      m.active_version_id = v.id;
      m.active_version_number = v.version_number;
      m.active_version_status = v.status;
      m.updated_at = now;
    }
    return ok(v);
  }

  const lock = /^\/methodology-versions\/([^/]+)\/lock$/.exec(path);
  if (lock && method === 'POST') {
    const v = versionById(lock[1]);
    if (!v) return notFound();
    if (v.status !== 'APPROVED') {
      return {
        status: 409,
        body: {
          code: 'INVALID_TRANSITION',
          message: `Cannot LOCK a methodology version in state ${v.status} (must be APPROVED first)`,
        },
      };
    }
    const now = new Date().toISOString();
    v.status = 'LOCKED';
    v.locked_at = now;
    v.locked_by = 'mock-user-locker';
    v.locked_by_name = 'Mock Locker';
    v.updated_at = now;
    const m = methodologyById(v.methodology_id);
    if (m) {
      m.active_version_status = v.status;
      m.updated_at = now;
    }
    return ok(v);
  }

  const archive = /^\/methodology-versions\/([^/]+)\/archive$/.exec(path);
  if (archive && method === 'POST') {
    const v = versionById(archive[1]);
    if (!v) return notFound();
    const body = readBody<{ reason?: string }>(config);
    if (!body.reason || body.reason.trim().length < 20) {
      return { status: 400, body: { code: 'REASON_REQUIRED', message: 'Reason min 20 chars' } };
    }
    v.status = 'ARCHIVED';
    v.archived_at = new Date().toISOString();
    v.archive_reason = body.reason;
    return ok(v);
  }

  // POST /methodology-versions/:id/factors  — add factor
  const factorsAdd = /^\/methodology-versions\/([^/]+)\/factors$/.exec(path);
  if (factorsAdd && method === 'POST') {
    const v = versionById(factorsAdd[1]);
    if (!v) return notFound();
    if (v.status !== 'DRAFT') return lockedConflict();
    // F-401: ensure no tenant_id leaks via the body (defence-in-depth in the mock).
    const raw = readBody<Partial<MockFactor> & Record<string, unknown>>(config);
    const body = stripTenantFromBody(raw, path, 'POST') as Partial<MockFactor>;
    const factor: MockFactor = {
      id: uuid(),
      methodology_version_id: v.id,
      code: body.code ?? `F-${Date.now()}`,
      name_i18n: body.name_i18n ?? {},
      description_i18n: body.description_i18n,
      weight: body.weight ?? 0,
      max_points: body.max_points ?? 0,
      sort_order: body.sort_order ?? v.factors.length,
      required: body.required ?? true,
      levels: [],
    };
    v.factors.push(factor);
    v.updated_at = new Date().toISOString();
    return ok(factor, 201);
  }

  const reorder = /^\/methodology-versions\/([^/]+)\/factors\/reorder$/.exec(path);
  if (reorder && method === 'POST') {
    const v = versionById(reorder[1]);
    if (!v) return notFound();
    if (v.status !== 'DRAFT') return lockedConflict();
    // F-401
    const raw = readBody<Record<string, unknown>>(config);
    const body = stripTenantFromBody(raw, path, 'POST') as { ordered_ids: string[] };
    body.ordered_ids.forEach((id, i) => {
      const f = v.factors.find((x) => x.id === id);
      if (f) f.sort_order = i;
    });
    v.updated_at = new Date().toISOString();
    return ok({ items: v.factors });
  }

  return null;
}

function handleFactors(method: string, path: string, _query: URLSearchParams, config: AxiosRequestConfig): MatchResult | null {
  const detail = /^\/factors\/([^/]+)$/.exec(path);
  if (detail) {
    const id = detail[1];
    let foundFactor: MockFactor | undefined;
    let owner: MockMethodologyVersion | undefined;
    for (const v of mockDb.methodologyVersions) {
      const f = v.factors.find((x) => x.id === id);
      if (f) {
        foundFactor = f;
        owner = v;
        break;
      }
    }
    if (!foundFactor || !owner) return notFound();
    if (method === 'PATCH') {
      if (owner.status !== 'DRAFT') return lockedConflict();
      // F-401
      const raw = readBody<Partial<MockFactor> & Record<string, unknown>>(config);
      const body = stripTenantFromBody(raw, path, 'PATCH') as Partial<MockFactor>;
      Object.assign(foundFactor, body);
      owner.updated_at = new Date().toISOString();
      return ok(foundFactor);
    }
    if (method === 'DELETE') {
      if (owner.status !== 'DRAFT') return lockedConflict();
      owner.factors = owner.factors.filter((x) => x.id !== id);
      owner.updated_at = new Date().toISOString();
      return ok({ removed: id }, 200);
    }
  }

  // GET /factors/:id/levels — BARE ARRAY of FactorLevelResponse (real-backend
  // contract; the FE aggregator fans out to this per factor).
  const levelsList = /^\/factors\/([^/]+)\/levels$/.exec(path);
  if (levelsList && method === 'GET') {
    const factorId = levelsList[1];
    const owner = mockDb.methodologyVersions.find((v) => v.factors.some((f) => f.id === factorId));
    if (!owner) return notFound();
    const factor = owner.factors.find((f) => f.id === factorId)!;
    return ok(factor.levels);
  }

  // POST /factors/:id/levels
  const levelsAdd = /^\/factors\/([^/]+)\/levels$/.exec(path);
  if (levelsAdd && method === 'POST') {
    const factorId = levelsAdd[1];
    const owner = mockDb.methodologyVersions.find((v) => v.factors.some((f) => f.id === factorId));
    if (!owner) return notFound();
    if (owner.status !== 'DRAFT') return lockedConflict();
    const factor = owner.factors.find((f) => f.id === factorId)!;
    // F-401
    const raw = readBody<Partial<MockFactorLevel> & Record<string, unknown>>(config);
    const body = stripTenantFromBody(raw, path, 'POST') as Partial<MockFactorLevel>;
    const lvl: MockFactorLevel = {
      id: uuid(),
      factor_id: factorId,
      code: body.code ?? `L-${Date.now()}`,
      level_order: body.level_order ?? factor.levels.length,
      points: body.points ?? 0,
      scale_value: body.scale_value ?? 0,
      label_i18n: body.label_i18n ?? {},
      description_i18n: body.description_i18n,
    };
    factor.levels.push(lvl);
    owner.updated_at = new Date().toISOString();
    return ok(lvl, 201);
  }

  // POST /factors/:id/levels/reorder
  const levelsReorder = /^\/factors\/([^/]+)\/levels\/reorder$/.exec(path);
  if (levelsReorder && method === 'POST') {
    const factorId = levelsReorder[1];
    const owner = mockDb.methodologyVersions.find((v) => v.factors.some((f) => f.id === factorId));
    if (!owner) return notFound();
    if (owner.status !== 'DRAFT') return lockedConflict();
    const factor = owner.factors.find((f) => f.id === factorId)!;
    // F-401
    const raw = readBody<Record<string, unknown>>(config);
    const body = stripTenantFromBody(raw, path, 'POST') as { ordered_ids: string[] };
    body.ordered_ids.forEach((id, i) => {
      const l = factor.levels.find((x) => x.id === id);
      if (l) l.level_order = i;
    });
    owner.updated_at = new Date().toISOString();
    return ok({ items: factor.levels });
  }

  return null;
}

function handleFactorLevels(method: string, path: string, _query: URLSearchParams, config: AxiosRequestConfig): MatchResult | null {
  const detail = /^\/factor-levels\/([^/]+)$/.exec(path);
  if (detail) {
    const id = detail[1];
    let found: { lvl: MockFactorLevel; factor: MockFactor; version: MockMethodologyVersion } | null = null;
    for (const v of mockDb.methodologyVersions) {
      for (const f of v.factors) {
        const lvl = f.levels.find((x) => x.id === id);
        if (lvl) {
          found = { lvl, factor: f, version: v };
          break;
        }
      }
      if (found) break;
    }
    if (!found) return notFound();
    if (method === 'PATCH') {
      if (found.version.status !== 'DRAFT') return lockedConflict();
      // F-401
      const raw = readBody<Partial<MockFactorLevel> & Record<string, unknown>>(config);
      const body = stripTenantFromBody(raw, path, 'PATCH') as Partial<MockFactorLevel>;
      Object.assign(found.lvl, body);
      found.version.updated_at = new Date().toISOString();
      return ok(found.lvl);
    }
    if (method === 'DELETE') {
      if (found.version.status !== 'DRAFT') return lockedConflict();
      found.factor.levels = found.factor.levels.filter((x) => x.id !== id);
      found.version.updated_at = new Date().toISOString();
      return ok({ removed: id }, 200);
    }
  }
  return null;
}

// ============================================================
// Phase 5 — Evaluation handlers (mirrors backend EvaluationController)
// ============================================================

/**
 * Pure scoring engine — mirrors backend `EvaluationScoringEngine` so the
 * mock and real backend agree on the preview / per-factor numbers. See
 * architecture §15.2.
 */
function computeMockScoring(
  version: MockMethodologyVersion,
  selections: { factor_id: string; factor_level_id: string }[],
): { raw_total: number; displayed_total: number; per_factor_raw: Record<string, number> } {
  const selMap = new Map(selections.map((s) => [s.factor_id, s.factor_level_id]));
  const perFactor: Record<string, number> = {};
  let raw = 0;
  for (const f of version.factors) {
    const lvlId = selMap.get(f.id);
    if (!lvlId) {
      perFactor[f.id] = 0;
      continue;
    }
    const lvl = f.levels.find((l) => l.id === lvlId);
    if (!lvl) {
      perFactor[f.id] = 0;
      continue;
    }
    let score = 0;
    switch (version.scoring_mode) {
      case 'DIRECT_POINTS':
        score = lvl.points ?? 0;
        break;
      case 'WEIGHTED_POINTS': {
        if (f.max_points === 0) {
          score = 0;
        } else {
          const normalised = (lvl.points ?? 0) / f.max_points;
          score = f.weight * normalised;
        }
        break;
      }
      case 'WEIGHTED_SCALE':
        score = f.weight * (lvl.scale_value ?? 0);
        break;
    }
    perFactor[f.id] = Number(score.toFixed(4));
    raw += perFactor[f.id];
  }
  return {
    raw_total: Number(raw.toFixed(4)),
    displayed_total: Number(raw.toFixed(2)),
    per_factor_raw: perFactor,
  };
}

function recomputeEvaluationTotals(e: MockEvaluation): void {
  const version = mockDb.methodologyVersions.find(
    (v) => v.id === e.methodology_version_id,
  );
  if (!version) return;
  const scores = mockDb.evaluationScores.filter((s) => s.evaluation_id === e.id);
  const result = computeMockScoring(
    version,
    scores.map((s) => ({
      factor_id: s.factor_id,
      factor_level_id: s.factor_level_id,
    })),
  );
  e.raw_total_score = result.raw_total;
  e.displayed_total_score = result.displayed_total;
}

function isComplete(e: MockEvaluation): boolean {
  const version = mockDb.methodologyVersions.find(
    (v) => v.id === e.methodology_version_id,
  );
  if (!version) return false;
  const required = version.factors.filter((f) => f.required);
  const scored = new Set(
    mockDb.evaluationScores
      .filter((s) => s.evaluation_id === e.id)
      .map((s) => s.factor_id),
  );
  return required.every((f) => scored.has(f.id));
}

function evaluationById(id: string): MockEvaluation | undefined {
  return mockDb.evaluations.find((e) => e.id === id);
}

function evaluationLockedConflict(state: MockEvaluationStatus): MatchResult {
  return {
    status: 409,
    body: {
      code: 'INVALID_TRANSITION',
      message: `Cannot mutate evaluation in state ${state}`,
    },
  };
}

const EVAL_EDITABLE: ReadonlySet<MockEvaluationStatus> = new Set([
  'DRAFT',
  'INCOMPLETE',
  'COMPLETE',
]);

function handleEvaluations(
  method: string,
  path: string,
  query: URLSearchParams,
  config: AxiosRequestConfig,
): MatchResult | null {
  // POST /evaluations/preview-score — stateless preview
  if (path === '/evaluations/preview-score' && method === 'POST') {
    const raw = readBody<Record<string, unknown>>(config);
    const body = stripTenantFromBody(raw, path, 'POST') as {
      methodology_version_id?: string;
      methodologyVersionId?: string;
      selections?: { factor_id?: string; factor_level_id?: string; factorId?: string; factorLevelId?: string }[];
    };
    const versionId = body.methodology_version_id ?? body.methodologyVersionId ?? '';
    const version = mockDb.methodologyVersions.find((v) => v.id === versionId);
    if (!version) return notFound();
    const sel = (body.selections ?? []).map((s) => ({
      factor_id: s.factor_id ?? s.factorId ?? '',
      factor_level_id: s.factor_level_id ?? s.factorLevelId ?? '',
    }));
    return ok(computeMockScoring(version, sel));
  }

  // GET /evaluations?groupBy=factor — Bulk Evaluation by Factor view
  if (
    path === '/evaluations' &&
    method === 'GET' &&
    query.get('groupBy') === 'factor'
  ) {
    const projectId = query.get('projectId') ?? '';
    const factorId = query.get('factorId') ?? '';
    const status = query.get('status');
    const departmentId = query.get('departmentId');
    const onlyUnfilled = query.get('onlyUnfilled') === 'true';
    const search = (query.get('search') ?? '').trim().toLowerCase();
    const page = parseInt(query.get('page') ?? '0', 10);
    const size = Math.min(parseInt(query.get('size') ?? '25', 10), 200);

    // Build row set: one EvaluationByFactorRow per evaluation in the project.
    let evals = mockDb.evaluations.filter((e) => e.project_id === projectId);
    if (status) evals = evals.filter((e) => e.status === status);

    const rows = evals
      .map((ev) => {
        const pos = mockDb.positions.find((p) => p.id === ev.position_id);
        if (!pos) return null;
        if (departmentId && pos.department_id !== departmentId) return null;
        const dept = mockDb.departments.find((d) => d.id === pos.department_id);
        // Resolve unit (= "DIVISION/UNIT" child) — best-effort: walk parents.
        const unit =
          dept && (dept.type === 'UNIT' || dept.type === 'DIVISION')
            ? dept
            : null;
        const deptName = dept
          ? dept.name_i18n['ru-RU'] ?? dept.name_i18n['en-US'] ?? ''
          : '';
        const unitName = unit
          ? unit.name_i18n['ru-RU'] ?? unit.name_i18n['en-US'] ?? ''
          : null;
        const title =
          pos.title_i18n['ru-RU'] ?? pos.title_i18n['en-US'] ?? pos.code;

        if (search && !title.toLowerCase().includes(search) && !pos.code.toLowerCase().includes(search)) {
          return null;
        }

        const version = mockDb.methodologyVersions.find(
          (v) => v.id === ev.methodology_version_id,
        );
        const totalFactors = version?.factors.length ?? 0;
        const evScores = mockDb.evaluationScores.filter(
          (s) => s.evaluation_id === ev.id,
        );
        const filledFactors = evScores.length;
        const score = evScores.find((s) => s.factor_id === factorId);
        // Map factor_level_id back to its level_order (display value).
        let rawValue: number | null = null;
        if (score && version) {
          const factor = version.factors.find((f) => f.id === factorId);
          const lvl = factor?.levels.find(
            (l) => l.id === score.factor_level_id,
          );
          if (lvl) rawValue = lvl.level_order + 1;
        }

        if (onlyUnfilled && score) return null;

        // snake_case to match EvaluationByFactorRow (FE type) + the real
        // SNAKE_CASE backend; the K-sheet view reads row.evaluation_id etc.
        return {
          evaluation_id: ev.id,
          position_id: pos.id,
          position_code: pos.code,
          position_title: title,
          department_name: deptName,
          unit_name: unitName,
          status: ev.status,
          filled_factors_count: filledFactors,
          total_factors_count: totalFactors,
          current_score_factor_level_id: score?.factor_level_id ?? null,
          current_score_raw_value: rawValue,
          current_comment: score?.comment_text ?? null,
        };
      })
      .filter((r): r is NonNullable<typeof r> => r !== null);

    return ok(paginate(rows, page, size));
  }

  // GET /evaluations  (list with filters — by-position view)
  if (path === '/evaluations' && method === 'GET') {
    const projectId = query.get('projectId');
    const positionId = query.get('positionId');
    const status = query.get('status');
    const page = parseInt(query.get('page') ?? '0', 10);
    const size = Math.min(parseInt(query.get('size') ?? '20', 10), 200);
    let list = mockDb.evaluations;
    if (projectId) list = list.filter((e) => e.project_id === projectId);
    if (positionId) list = list.filter((e) => e.position_id === positionId);
    if (status) list = list.filter((e) => e.status === status);
    return ok(paginate(list, page, size));
  }

  // POST /evaluations  (create)
  if (path === '/evaluations' && method === 'POST') {
    const raw = readBody<Record<string, unknown>>(config);
    const body = stripTenantFromBody(raw, path, 'POST') as {
      position_id?: string;
      positionId?: string;
      methodology_version_id?: string;
      methodologyVersionId?: string;
      evaluator_user_id?: string;
    };
    const positionId = body.position_id ?? body.positionId;
    const versionId = body.methodology_version_id ?? body.methodologyVersionId;
    if (!positionId || !versionId) {
      return {
        status: 400,
        body: {
          code: 'VALIDATION_FAILED',
          message: 'positionId + methodologyVersionId required',
        },
      };
    }
    const pos = mockDb.positions.find((p) => p.id === positionId);
    if (!pos) return notFound();
    const next: MockEvaluation = {
      id: uuid(),
      project_id: pos.project_id,
      position_id: positionId,
      methodology_version_id: versionId,
      evaluator_user_id: body.evaluator_user_id ?? null,
      status: 'DRAFT',
      raw_total_score: 0,
      displayed_total_score: 0,
    };
    mockDb.evaluations.unshift(next);
    return ok(next, 201);
  }

  const detail = /^\/evaluations\/([^/]+)$/.exec(path);
  if (detail && method === 'GET') {
    const e = evaluationById(detail[1]);
    if (!e) return notFound();
    return ok(e);
  }

  const scoresList = /^\/evaluations\/([^/]+)\/scores$/.exec(path);
  if (scoresList && method === 'GET') {
    const evalId = scoresList[1];
    const e = evaluationById(evalId);
    if (!e) return notFound();
    const items = mockDb.evaluationScores.filter(
      (s) => s.evaluation_id === evalId,
    );
    return ok(items);
  }

  // POST /evaluations/:id/scores — upsert
  if (scoresList && method === 'POST') {
    const evalId = scoresList[1];
    const e = evaluationById(evalId);
    if (!e) return notFound();
    if (!EVAL_EDITABLE.has(e.status)) return evaluationLockedConflict(e.status);
    const raw = readBody<Record<string, unknown>>(config);
    const body = stripTenantFromBody(raw, path, 'POST') as {
      factor_id?: string;
      factorId?: string;
      factor_level_id?: string;
      factorLevelId?: string;
      comment?: string | null;
    };
    const factorId = body.factor_id ?? body.factorId ?? '';
    const lvlId = body.factor_level_id ?? body.factorLevelId ?? '';
    const version = mockDb.methodologyVersions.find(
      (v) => v.id === e.methodology_version_id,
    );
    if (!version) return notFound();
    const factor = version.factors.find((f) => f.id === factorId);
    const lvl = factor?.levels.find((l) => l.id === lvlId);
    if (!factor || !lvl) {
      return {
        status: 400,
        body: { code: 'INVALID_REFERENCE', message: 'Factor or level not found in version' },
      };
    }
    const existing = mockDb.evaluationScores.find(
      (s) => s.evaluation_id === evalId && s.factor_id === factorId,
    );
    // Compute factor score the same way as the engine.
    const partial = computeMockScoring(version, [
      { factor_id: factorId, factor_level_id: lvlId },
    ]);
    const rawScore = partial.per_factor_raw[factorId] ?? 0;
    let score: MockEvaluationScore;
    if (existing) {
      existing.factor_level_id = lvlId;
      existing.raw_factor_score = rawScore;
      if (body.comment !== undefined) existing.comment_text = body.comment;
      score = existing;
    } else {
      score = {
        id: uuid(),
        evaluation_id: evalId,
        factor_id: factorId,
        factor_level_id: lvlId,
        raw_factor_score: rawScore,
        comment_text: body.comment ?? null,
        manually_adjusted: false,
      };
      mockDb.evaluationScores.push(score);
    }
    recomputeEvaluationTotals(e);
    // Auto-toggle COMPLETE/INCOMPLETE based on required-factor coverage.
    if (e.status === 'DRAFT' || e.status === 'INCOMPLETE' || e.status === 'COMPLETE') {
      e.status = isComplete(e) ? 'COMPLETE' : 'INCOMPLETE';
    }
    return ok(score);
  }

  const calHistory = /^\/evaluations\/([^/]+)\/calibration-history$/.exec(path);
  if (calHistory && method === 'GET') {
    const evalId = calHistory[1];
    if (!evaluationById(evalId)) return notFound();
    const items = mockDb.calibrationEvents.filter(
      (c) => c.evaluation_id === evalId,
    );
    return ok(items);
  }

  const submit = /^\/evaluations\/([^/]+)\/submit$/.exec(path);
  if (submit && method === 'POST') {
    const e = evaluationById(submit[1]);
    if (!e) return notFound();
    if (e.status !== 'COMPLETE' && e.status !== 'INCOMPLETE' && e.status !== 'DRAFT') {
      return evaluationLockedConflict(e.status);
    }
    if (!isComplete(e)) {
      return {
        status: 400,
        body: {
          code: 'EVALUATION_INCOMPLETE',
          message: 'Required factors are missing',
        },
      };
    }
    e.status = 'SUBMITTED';
    e.submitted_at = new Date().toISOString();
    e.submitted_by = 'mock-evaluator-1';
    return ok(e);
  }

  const approve = /^\/evaluations\/([^/]+)\/approve$/.exec(path);
  if (approve && method === 'POST') {
    const e = evaluationById(approve[1]);
    if (!e) return notFound();
    if (e.status !== 'SUBMITTED') return evaluationLockedConflict(e.status);
    e.status = 'APPROVED';
    e.approved_at = new Date().toISOString();
    e.approved_by = 'mock-approver-1';
    return ok(e);
  }

  const reqChanges = /^\/evaluations\/([^/]+)\/request-changes$/.exec(path);
  if (reqChanges && method === 'POST') {
    const e = evaluationById(reqChanges[1]);
    if (!e) return notFound();
    if (e.status !== 'SUBMITTED') return evaluationLockedConflict(e.status);
    const body = readBody<{ reason?: string }>(config);
    if (!body.reason || body.reason.trim().length < 20) {
      return { status: 400, body: { code: 'REASON_REQUIRED', message: 'Reason min 20 chars' } };
    }
    e.status = isComplete(e) ? 'COMPLETE' : 'INCOMPLETE';
    e.submitted_at = null;
    return ok(e);
  }

  const lock = /^\/evaluations\/([^/]+)\/lock$/.exec(path);
  if (lock && method === 'POST') {
    const e = evaluationById(lock[1]);
    if (!e) return notFound();
    if (e.status !== 'APPROVED') return evaluationLockedConflict(e.status);
    e.status = 'LOCKED';
    e.locked_at = new Date().toISOString();
    e.locked_by = 'mock-locker-1';
    return ok(e);
  }

  const archive = /^\/evaluations\/([^/]+)\/archive$/.exec(path);
  if (archive && method === 'POST') {
    const e = evaluationById(archive[1]);
    if (!e) return notFound();
    const body = readBody<{ reason?: string }>(config);
    if (!body.reason || body.reason.trim().length < 20) {
      return { status: 400, body: { code: 'REASON_REQUIRED', message: 'Reason min 20 chars' } };
    }
    e.status = 'ARCHIVED';
    e.archived_at = new Date().toISOString();
    return ok(e);
  }

  // POST /evaluations/factor/:factorId/bulk-score — Bulk Evaluation by Factor
  const bulkScore = /^\/evaluations\/factor\/([^/]+)\/bulk-score$/.exec(path);
  if (bulkScore && method === 'POST') {
    const factorId = bulkScore[1];
    const raw = readBody<Record<string, unknown>>(config);
    const body = stripTenantFromBody(raw, path, 'POST') as {
      evaluation_ids?: string[];
      factor_level_id?: string;
      // camelCase fallbacks kept so a stale caller never silently 400s.
      evaluationIds?: string[];
      factorLevelId?: string;
      reason?: string;
    };
    const ids = body.evaluation_ids ?? body.evaluationIds ?? [];
    const lvlId = body.factor_level_id ?? body.factorLevelId ?? '';
    if (!body.reason || body.reason.trim().length < 50) {
      return {
        status: 400,
        body: { code: 'REASON_REQUIRED', message: 'Reason min 50 chars' },
      };
    }
    if (ids.length === 0 || !lvlId) {
      return {
        status: 400,
        body: {
          code: 'VALIDATION_FAILED',
          message: 'evaluation_ids + factor_level_id required',
        },
      };
    }
    const failed: { evaluation_id: string; reason: string }[] = [];
    let updated = 0;
    for (const evId of ids) {
      const e = evaluationById(evId);
      if (!e) {
        failed.push({ evaluation_id: evId, reason: 'NOT_FOUND' });
        continue;
      }
      if (!EVAL_EDITABLE.has(e.status)) {
        failed.push({ evaluation_id: evId, reason: `LOCKED:${e.status}` });
        continue;
      }
      const version = mockDb.methodologyVersions.find(
        (v) => v.id === e.methodology_version_id,
      );
      const factor = version?.factors.find((f) => f.id === factorId);
      const lvl = factor?.levels.find((l) => l.id === lvlId);
      if (!factor || !lvl) {
        failed.push({ evaluation_id: evId, reason: 'INVALID_REFERENCE' });
        continue;
      }
      const partial = computeMockScoring(version!, [
        { factor_id: factorId, factor_level_id: lvlId },
      ]);
      const rawScore = partial.per_factor_raw[factorId] ?? 0;
      const existing = mockDb.evaluationScores.find(
        (s) => s.evaluation_id === evId && s.factor_id === factorId,
      );
      if (existing) {
        existing.factor_level_id = lvlId;
        existing.raw_factor_score = rawScore;
      } else {
        mockDb.evaluationScores.push({
          id: uuid(),
          evaluation_id: evId,
          factor_id: factorId,
          factor_level_id: lvlId,
          raw_factor_score: rawScore,
          comment_text: null,
          manually_adjusted: false,
        });
      }
      recomputeEvaluationTotals(e);
      if (e.status === 'DRAFT' || e.status === 'INCOMPLETE' || e.status === 'COMPLETE') {
        e.status = isComplete(e) ? 'COMPLETE' : 'INCOMPLETE';
      }
      updated += 1;
    }
    return ok({ updated, failed });
  }

  // POST /evaluations/factor/:factorId/bulk-submit
  const bulkSub = /^\/evaluations\/factor\/([^/]+)\/bulk-submit$/.exec(path);
  if (bulkSub && method === 'POST') {
    const raw = readBody<Record<string, unknown>>(config);
    const body = stripTenantFromBody(raw, path, 'POST') as {
      evaluation_ids?: string[];
      // camelCase fallback kept so a stale caller never silently 400s.
      evaluationIds?: string[];
      reason?: string;
    };
    const ids = body.evaluation_ids ?? body.evaluationIds ?? [];
    if (!body.reason || body.reason.trim().length < 20) {
      return {
        status: 400,
        body: { code: 'REASON_REQUIRED', message: 'Reason min 20 chars' },
      };
    }
    const failed: { evaluation_id: string; reason: string }[] = [];
    let updated = 0;
    for (const evId of ids) {
      const e = evaluationById(evId);
      if (!e) {
        failed.push({ evaluation_id: evId, reason: 'NOT_FOUND' });
        continue;
      }
      if (e.status === 'LOCKED' || e.status === 'ARCHIVED') {
        failed.push({ evaluation_id: evId, reason: `LOCKED:${e.status}` });
        continue;
      }
      if (!isComplete(e)) {
        failed.push({ evaluation_id: evId, reason: 'INCOMPLETE' });
        continue;
      }
      if (e.status === 'SUBMITTED' || e.status === 'APPROVED') {
        // Already past the SUBMITTED gate — count as a no-op success so
        // the UI doesn't error on a repeat bulk submit.
        updated += 1;
        continue;
      }
      e.status = 'SUBMITTED';
      e.submitted_at = new Date().toISOString();
      e.submitted_by = 'mock-evaluator-1';
      updated += 1;
    }
    return ok({ updated, failed });
  }

  const calibrate = /^\/evaluations\/([^/]+)\/calibrate$/.exec(path);
  if (calibrate && method === 'POST') {
    const e = evaluationById(calibrate[1]);
    if (!e) return notFound();
    if (e.status !== 'APPROVED' && e.status !== 'LOCKED') {
      return evaluationLockedConflict(e.status);
    }
    const raw = readBody<Record<string, unknown>>(config);
    const body = stripTenantFromBody(raw, path, 'POST') as {
      factor_id?: string;
      factorId?: string;
      new_raw_factor_score?: number;
      newRawFactorScore?: number;
      reason?: string;
    };
    const factorId = body.factor_id ?? body.factorId ?? '';
    const newScore = body.new_raw_factor_score ?? body.newRawFactorScore ?? 0;
    if (!body.reason || body.reason.trim().length < 20) {
      return { status: 400, body: { code: 'REASON_REQUIRED', message: 'Reason min 20 chars' } };
    }
    if (newScore < 0) {
      return { status: 400, body: { code: 'VALIDATION_FAILED', message: 'newRawFactorScore must be >= 0' } };
    }
    const existing = mockDb.evaluationScores.find(
      (s) => s.evaluation_id === e.id && s.factor_id === factorId,
    );
    if (!existing) {
      return {
        status: 400,
        body: { code: 'SCORE_NOT_FOUND', message: 'No existing score for factor' },
      };
    }
    const original = existing.raw_factor_score;
    existing.original_factor_score = original;
    existing.raw_factor_score = Number(newScore);
    existing.manually_adjusted = true;
    existing.adjusted_at = new Date().toISOString();
    existing.adjusted_by_user_id = 'mock-calibrator-1';
    existing.adjustment_reason = body.reason;
    const event: MockCalibrationEvent = {
      id: uuid(),
      evaluation_id: e.id,
      factor_id: factorId,
      original_score: Number(original.toFixed(4)),
      adjusted_score: Number(Number(newScore).toFixed(4)),
      delta: Number((Number(newScore) - original).toFixed(4)),
      reason: body.reason,
      decided_by: 'Mock Calibrator',
      decided_at: new Date().toISOString(),
    };
    mockDb.calibrationEvents.unshift(event);
    recomputeEvaluationTotals(e);
    return ok(event, 201);
  }

  return null;
}

// ============================================================
// Phase 6 — Grade Structure / Grade / GradeBand / Pyramid handlers
// ============================================================

function structureById(id: string): MockGradeStructure | undefined {
  return mockDb.gradeStructures.find((s) => s.id === id);
}

function gradeBelongs(
  structure: MockGradeStructure,
  gradeId: string,
): MockGrade | undefined {
  return structure.grades.find((g) => g.id === gradeId);
}

function gradeStructureLockedConflict(status: string): MatchResult {
  return {
    status: 409,
    body: {
      code: 'GRADE_STRUCTURE_LOCKED',
      message: `Grade structure is in status ${status} — only DRAFT allows edits`,
    },
  };
}

function ensureDraft(s: MockGradeStructure): MatchResult | null {
  if (s.status !== 'DRAFT') return gradeStructureLockedConflict(s.status);
  return null;
}

/** Inclusive-band lookup mirroring the backend grade-band resolver. */
function lookupGradeForScore(
  s: MockGradeStructure,
  score: number,
): { grade_id: string; grade_number: number; grade_band_id: string } | null {
  for (const g of s.grades) {
    if (!g.band) continue;
    if (score >= g.band.min_score && score <= g.band.max_score) {
      return {
        grade_id: g.id,
        grade_number: g.grade_number,
        grade_band_id: g.band.id,
      };
    }
  }
  return null;
}

function buildPyramid(s: MockGradeStructure) {
  const counts = mockDb.gradePyramidCounts[s.id] ?? [];
  const rows = s.grades
    .slice()
    .sort((a, b) => a.grade_number - b.grade_number)
    .map((g, idx) => ({
      grade_id: g.id,
      grade_number: g.grade_number,
      grade_name: g.name_i18n,
      position_count: counts[idx] ?? 0,
      evaluation_count: counts[idx] ?? 0,
      min_score: g.band?.min_score ?? null,
      max_score: g.band?.max_score ?? null,
    }));
  return { grade_structure_id: s.id, rows };
}

function build14Template(): MockGrade[] {
  // Same shape as the ACME 14-grade fixture but with placeholder ids.
  const ranges: [number, number][] = [
    [0, 50],
    [50.0001, 100],
    [100.0001, 150],
    [150.0001, 200],
    [200.0001, 250],
    [250.0001, 300],
    [300.0001, 350],
    [350.0001, 450],
    [450.0001, 550],
    [550.0001, 650],
    [650.0001, 750],
    [750.0001, 850],
    [850.0001, 950],
    [950.0001, 1000],
  ];
  return ranges.map((r, i) => {
    const n = i + 1;
    const id = uuid();
    return {
      id,
      grade_structure_id: '',
      grade_number: n,
      name_i18n: {
        'ru-RU': `Грейд ${n}`,
        'uz-Cyrl-UZ': `Грейд ${n}`,
        'uz-Latn-UZ': `Greyd ${n}`,
        'en-US': `Grade ${n}`,
      },
      sort_order: i,
      band: {
        id: `${id}-band`,
        grade_id: id,
        min_score: r[0],
        max_score: r[1],
      },
    };
  });
}

function build16Template(): MockGrade[] {
  return Array.from({ length: 16 }, (_, i) => {
    const n = i + 1;
    const id = uuid();
    const step = 1000 / 16;
    const min = i === 0 ? 0 : Number((i * step + 0.0001).toFixed(4));
    const max = i === 15 ? 1000 : Number(((i + 1) * step).toFixed(4));
    return {
      id,
      grade_structure_id: '',
      grade_number: n,
      name_i18n: {
        'ru-RU': `Грейд ${n}`,
        'uz-Cyrl-UZ': `Грейд ${n}`,
        'uz-Latn-UZ': `Greyd ${n}`,
        'en-US': `Grade ${n}`,
      },
      sort_order: i,
      band: { id: `${id}-band`, grade_id: id, min_score: min, max_score: max },
    };
  });
}

function handleGradeStructures(
  method: string,
  path: string,
  query: URLSearchParams,
  config: AxiosRequestConfig,
): MatchResult | null {
  // List / create
  if (path === '/grade-structures' && method === 'GET') {
    const projectId = query.get('projectId');
    const status = query.get('status');
    let list = mockDb.gradeStructures;
    if (projectId) list = list.filter((s) => s.project_id === projectId);
    if (status) list = list.filter((s) => s.status === status);
    return ok({ items: list });
  }
  if (path === '/grade-structures' && method === 'POST') {
    const raw = readBody<Record<string, unknown>>(config);
    const body = stripTenantFromBody(raw, path, 'POST') as Partial<MockGradeStructure>;
    const next: MockGradeStructure = {
      id: uuid(),
      project_id: (body.project_id as string | null | undefined) ?? null,
      code: body.code ?? 'NEW',
      name_i18n: body.name_i18n ?? {},
      description_i18n: body.description_i18n,
      structure_type: (body.structure_type as MockGradeStructure['structure_type']) ?? 'CUSTOM',
      status: 'DRAFT',
      gap_policy: (body.gap_policy as MockGradeStructure['gap_policy']) ?? 'STRICT_NO_GAPS',
      version_number: 1,
      parent_structure_id: null,
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
      grades: [],
    };
    mockDb.gradeStructures.unshift(next);
    return ok(next, 201);
  }
  if (path === '/grade-structures/from-template' && method === 'POST') {
    const raw = readBody<Record<string, unknown>>(config);
    const body = stripTenantFromBody(raw, path, 'POST') as {
      template_code?: string;
      project_id?: string | null;
      code?: string;
      name_i18n?: Record<string, string>;
    };
    const id = uuid();
    let grades: MockGrade[] = [];
    let structureType: MockGradeStructure['structure_type'] = 'CUSTOM';
    if (body.template_code === 'GRADE_14') {
      grades = build14Template();
      structureType = 'GRADE_14';
    } else if (body.template_code === 'GRADE_16') {
      grades = build16Template();
      structureType = 'GRADE_16';
    }
    grades.forEach((g) => {
      g.grade_structure_id = id;
      if (g.band) g.band.grade_id = g.id;
    });
    const next: MockGradeStructure = {
      id,
      project_id: body.project_id ?? null,
      code: body.code ?? 'NEW',
      name_i18n: body.name_i18n ?? {},
      structure_type: structureType,
      status: 'DRAFT',
      gap_policy: 'STRICT_NO_GAPS',
      version_number: 1,
      parent_structure_id: null,
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
      grades,
    };
    mockDb.gradeStructures.unshift(next);
    // Seed a pyramid distribution so the demo page isn't empty.
    if (structureType === 'GRADE_14') {
      mockDb.gradePyramidCounts[next.id] = [3, 5, 8, 10, 12, 14, 12, 9, 7, 5, 3, 2, 1, 1];
    } else if (structureType === 'GRADE_16') {
      mockDb.gradePyramidCounts[next.id] = [
        2, 4, 6, 8, 10, 12, 14, 13, 11, 9, 7, 5, 3, 2, 1, 1,
      ];
    }
    return ok(next, 201);
  }

  const idDetail = /^\/grade-structures\/([^/]+)$/.exec(path);
  if (idDetail) {
    const s = structureById(idDetail[1]);
    if (!s) return notFound();
    if (method === 'GET') return ok(s);
    if (method === 'PATCH') {
      const conflict = ensureDraft(s);
      if (conflict) return conflict;
      const raw = readBody<Record<string, unknown>>(config);
      const body = stripTenantFromBody(raw, path, 'PATCH') as Partial<MockGradeStructure>;
      Object.assign(s, body, { updated_at: new Date().toISOString() });
      return ok(s);
    }
  }

  const approveMatch = /^\/grade-structures\/([^/]+)\/approve$/.exec(path);
  if (approveMatch && method === 'POST') {
    const s = structureById(approveMatch[1]);
    if (!s) return notFound();
    if (s.status !== 'DRAFT') return gradeStructureLockedConflict(s.status);
    s.status = 'APPROVED';
    s.approved_at = new Date().toISOString();
    s.approved_by = 'mock-approver-1';
    s.approved_by_name = 'Mock Approver';
    s.updated_at = s.approved_at;
    return ok(s);
  }
  const lockMatch = /^\/grade-structures\/([^/]+)\/lock$/.exec(path);
  if (lockMatch && method === 'POST') {
    const s = structureById(lockMatch[1]);
    if (!s) return notFound();
    if (s.status !== 'APPROVED') return gradeStructureLockedConflict(s.status);
    s.status = 'LOCKED';
    s.locked_at = new Date().toISOString();
    s.locked_by = 'mock-locker-1';
    s.locked_by_name = 'Mock Locker';
    s.updated_at = s.locked_at;
    return ok(s);
  }
  const archMatch = /^\/grade-structures\/([^/]+)\/archive$/.exec(path);
  if (archMatch && method === 'POST') {
    const s = structureById(archMatch[1]);
    if (!s) return notFound();
    const raw = readBody<{ reason?: string }>(config);
    const body = stripTenantFromBody(raw as Record<string, unknown>, path, 'POST') as {
      reason?: string;
    };
    if (!body.reason || body.reason.trim().length < 20) {
      return {
        status: 400,
        body: { code: 'REASON_REQUIRED', message: 'Reason min 20 chars' },
      };
    }
    s.status = 'ARCHIVED';
    s.archived_at = new Date().toISOString();
    s.archive_reason = body.reason;
    s.updated_at = s.archived_at;
    return ok(s);
  }

  const newVerMatch = /^\/grade-structures\/([^/]+)\/create-new-version$/.exec(path);
  if (newVerMatch && method === 'POST') {
    const src = structureById(newVerMatch[1]);
    if (!src) return notFound();
    const newId = uuid();
    const cloneGrades = src.grades.map((g) => {
      const ng: MockGrade = {
        ...g,
        id: uuid(),
        grade_structure_id: newId,
        band: g.band
          ? {
              id: uuid(),
              grade_id: '',
              min_score: g.band.min_score,
              max_score: g.band.max_score,
            }
          : null,
      };
      if (ng.band) ng.band.grade_id = ng.id;
      return ng;
    });
    const next: MockGradeStructure = {
      ...src,
      id: newId,
      status: 'DRAFT',
      version_number: src.version_number + 1,
      parent_structure_id: src.id,
      approved_at: null,
      approved_by: null,
      approved_by_name: null,
      locked_at: null,
      locked_by: null,
      locked_by_name: null,
      archived_at: null,
      archive_reason: null,
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
      grades: cloneGrades,
    };
    mockDb.gradeStructures.unshift(next);
    return ok(next, 201);
  }

  // Grades sub-collection
  const gradesMatch = /^\/grade-structures\/([^/]+)\/grades$/.exec(path);
  if (gradesMatch && method === 'POST') {
    const s = structureById(gradesMatch[1]);
    if (!s) return notFound();
    const conflict = ensureDraft(s);
    if (conflict) return conflict;
    const raw = readBody<Record<string, unknown>>(config);
    const body = stripTenantFromBody(raw, path, 'POST') as Partial<MockGrade>;
    const id = uuid();
    const next: MockGrade = {
      id,
      grade_structure_id: s.id,
      grade_number: Number(body.grade_number ?? s.grades.length + 1),
      name_i18n: body.name_i18n ?? {},
      description_i18n: body.description_i18n,
      sort_order: Number(body.sort_order ?? s.grades.length),
      band: null,
    };
    s.grades.push(next);
    s.updated_at = new Date().toISOString();
    return ok(next, 201);
  }

  // Pyramid
  const pyrMatch = /^\/grade-structures\/([^/]+)\/pyramid$/.exec(path);
  if (pyrMatch && method === 'GET') {
    const s = structureById(pyrMatch[1]);
    if (!s) return notFound();
    return ok(buildPyramid(s));
  }

  // Preview grade lookup
  const lookupMatch = /^\/grade-structures\/([^/]+)\/preview-grade-lookup$/.exec(path);
  if (lookupMatch && method === 'POST') {
    const s = structureById(lookupMatch[1]);
    if (!s) return notFound();
    const raw = readBody<Record<string, unknown>>(config);
    const body = stripTenantFromBody(raw, path, 'POST') as { score?: number };
    const score = Number(body.score ?? 0);
    const result = lookupGradeForScore(s, score);
    return ok(
      result
        ? {
            grade_id: result.grade_id,
            grade_number: result.grade_number,
            grade_band_id: result.grade_band_id,
            out_of_range: false,
          }
        : {
            grade_id: null,
            grade_number: null,
            grade_band_id: null,
            out_of_range: true,
          },
    );
  }

  return null;
}

function handleGradesAndBands(
  method: string,
  path: string,
  _query: URLSearchParams,
  config: AxiosRequestConfig,
): MatchResult | null {
  // PATCH /grades/:id
  const updateGrade = /^\/grades\/([^/]+)$/.exec(path);
  if (updateGrade) {
    const gradeId = updateGrade[1];
    let target: MockGrade | undefined;
    let owner: MockGradeStructure | undefined;
    for (const s of mockDb.gradeStructures) {
      const g = gradeBelongs(s, gradeId);
      if (g) {
        target = g;
        owner = s;
        break;
      }
    }
    if (!target || !owner) return notFound();
    if (method === 'PATCH') {
      const conflict = ensureDraft(owner);
      if (conflict) return conflict;
      const raw = readBody<Record<string, unknown>>(config);
      const body = stripTenantFromBody(raw, path, 'PATCH') as Partial<MockGrade>;
      Object.assign(target, body);
      owner.updated_at = new Date().toISOString();
      return ok(target);
    }
    if (method === 'DELETE') {
      const conflict = ensureDraft(owner);
      if (conflict) return conflict;
      owner.grades = owner.grades.filter((g) => g.id !== gradeId);
      owner.updated_at = new Date().toISOString();
      return { status: 204, body: null };
    }
  }

  // POST /grades/:id/band  (upsert)
  const bandUpsert = /^\/grades\/([^/]+)\/band$/.exec(path);
  if (bandUpsert && method === 'POST') {
    const gradeId = bandUpsert[1];
    let target: MockGrade | undefined;
    let owner: MockGradeStructure | undefined;
    for (const s of mockDb.gradeStructures) {
      const g = gradeBelongs(s, gradeId);
      if (g) {
        target = g;
        owner = s;
        break;
      }
    }
    if (!target || !owner) return notFound();
    const conflict = ensureDraft(owner);
    if (conflict) return conflict;
    const raw = readBody<Record<string, unknown>>(config);
    const body = stripTenantFromBody(raw, path, 'POST') as {
      min_score?: number;
      max_score?: number;
    };
    const min = Number(body.min_score ?? 0);
    const max = Number(body.max_score ?? 0);
    if (min > max) {
      return {
        status: 400,
        body: { code: 'BAND_INVALID', message: 'min_score must be <= max_score' },
      };
    }
    const band: MockGradeBand = {
      id: target.band?.id ?? uuid(),
      grade_id: target.id,
      min_score: min,
      max_score: max,
    };
    target.band = band;
    owner.updated_at = new Date().toISOString();
    return ok(band);
  }

  return null;
}

// ============================================================
// MVP 2 Phase 1 — Approval handlers
// ============================================================

const MOCK_CURRENT_USER_ID = '00000000-0000-0000-0000-000000000001';
const MOCK_CURRENT_USER_NAME = 'Dev User';
/**
 * Mirror of dev super-admin permissions for the inbox computation —
 * the real backend resolves this from the JWT.
 */
const MOCK_CURRENT_USER_PERMISSIONS = new Set<string>([
  'JOB_PROFILE_APPROVE',
  'METHODOLOGY_APPROVE',
  'EVALUATION_APPROVE',
  'EVALUATION_LOCK',
  'GRADE_STRUCTURE_APPROVE',
  'GRADE_STRUCTURE_LOCK',
  'APPROVAL_STEP_APPROVE',
  'APPROVAL_STEP_REJECT',
  'APPROVAL_REQUEST_CREATE',
  'APPROVAL_REQUEST_CANCEL',
]);

function approvalById(id: string) {
  return mockDb.approvalRequests.find((a) => a.id === id);
}

function reasonRequired(reason: string | undefined): MatchResult | null {
  if (!reason || reason.trim().length < 20) {
    return { status: 400, body: { code: 'REASON_REQUIRED', message: 'Reason min 20 chars' } };
  }
  return null;
}

function isStepActionableByMe(step: { approverUserId: string | null; requiredPermission: string | null }) {
  if (step.approverUserId && step.approverUserId === MOCK_CURRENT_USER_ID) return true;
  if (step.requiredPermission && MOCK_CURRENT_USER_PERMISSIONS.has(step.requiredPermission)) return true;
  return false;
}

function approvalSummary(a: (typeof mockDb.approvalRequests)[number]) {
  // omit nested steps/decisions for list responses
  const { steps: _steps, decisions: _decisions, ...rest } = a;
  void _steps;
  void _decisions;
  return rest;
}

function handleApprovals(
  method: string,
  path: string,
  query: URLSearchParams,
  config: AxiosRequestConfig,
): MatchResult | null {
  // GET /approval-requests/my-inbox
  if (path === '/approval-requests/my-inbox' && method === 'GET') {
    const items = mockDb.approvalRequests
      .filter((a) => a.status === 'PENDING')
      .filter((a) => {
        const current = a.steps.find((s) => s.status === 'PENDING' && s.stepOrder === a.currentStepOrder);
        return current && isStepActionableByMe(current);
      })
      .map(approvalSummary);
    return ok({ items });
  }

  // GET /approval-requests
  if (path === '/approval-requests' && method === 'GET') {
    let list = mockDb.approvalRequests;
    const projectId = query.get('projectId');
    const entityType = query.get('entityType');
    const entityId = query.get('entityId');
    const status = query.get('status');
    const forCurrentUser = query.get('forCurrentUser');
    if (projectId) list = list.filter((a) => a.projectId === projectId);
    if (entityType) list = list.filter((a) => a.entityType === entityType);
    if (entityId) list = list.filter((a) => a.entityId === entityId);
    if (status) list = list.filter((a) => a.status === status);
    if (forCurrentUser === 'true') {
      list = list.filter((a) => a.steps.some((s) => isStepActionableByMe(s)));
    }
    return ok({ items: list.map(approvalSummary) });
  }

  // POST /approval-requests
  if (path === '/approval-requests' && method === 'POST') {
    const raw = readBody<Record<string, unknown>>(config);
    const body = stripTenantFromBody(raw, path, 'POST') as {
      entityType?: string;
      entityId?: string;
      notesI18n?: Partial<Record<string, string>>;
    };
    if (!body.entityType || !body.entityId) {
      return { status: 400, body: { code: 'VALIDATION_FAILED', message: 'entityType + entityId required' } };
    }
    const id = uuid();
    const step: (typeof mockDb.approvalRequests)[number]['steps'][number] = {
      id: uuid(),
      approvalRequestId: id,
      stepOrder: 1,
      approverUserId: null,
      approverName: null,
      requiredPermission: `${body.entityType}_APPROVE`,
      status: 'PENDING',
      decidedAt: null,
      decidedByUserId: null,
      decidedByName: null,
      notes: null,
      reason: null,
    };
    const next: (typeof mockDb.approvalRequests)[number] = {
      id,
      projectId: 'proj-acme-2026',
      entityType: body.entityType as never,
      entityId: body.entityId,
      entityLabel: {},
      status: 'PENDING',
      initiatedByUserId: MOCK_CURRENT_USER_ID,
      initiatedByName: MOCK_CURRENT_USER_NAME,
      initiatedAt: new Date().toISOString(),
      currentStepOrder: 1,
      totalSteps: 1,
      notesI18n: (body.notesI18n ?? {}) as never,
      steps: [step],
      decisions: [],
      completedAt: null,
    };
    mockDb.approvalRequests.unshift(next);
    return ok(next, 201);
  }

  // GET /approval-requests/:id
  const detail = /^\/approval-requests\/([^/]+)$/.exec(path);
  if (detail && method === 'GET') {
    const a = approvalById(detail[1]);
    if (!a) return notFound();
    return ok(a);
  }

  // POST /approval-requests/:id/cancel
  const cancel = /^\/approval-requests\/([^/]+)\/cancel$/.exec(path);
  if (cancel && method === 'POST') {
    const a = approvalById(cancel[1]);
    if (!a) return notFound();
    if (a.status !== 'PENDING') {
      return { status: 409, body: { code: 'INVALID_TRANSITION', message: `Cannot cancel ${a.status}` } };
    }
    a.status = 'CANCELLED';
    a.completedAt = new Date().toISOString();
    a.currentStepOrder = null;
    return ok(a);
  }

  // POST /approval-requests/:id/steps/:stepId/(approve|reject|request-changes)
  const stepAct = /^\/approval-requests\/([^/]+)\/steps\/([^/]+)\/(approve|reject|request-changes)$/.exec(path);
  if (stepAct && method === 'POST') {
    const a = approvalById(stepAct[1]);
    if (!a) return notFound();
    const step = a.steps.find((s) => s.id === stepAct[2]);
    if (!step) return notFound();
    if (a.status !== 'PENDING') {
      return { status: 409, body: { code: 'INVALID_TRANSITION', message: `Cannot act on ${a.status}` } };
    }
    if (step.status !== 'PENDING') {
      return { status: 409, body: { code: 'INVALID_TRANSITION', message: `Step already ${step.status}` } };
    }
    const action = stepAct[3] as 'approve' | 'reject' | 'request-changes';
    const raw = readBody<Record<string, unknown>>(config);
    const body = stripTenantFromBody(raw, path, 'POST') as { notes?: string; reason?: string };
    const now = new Date().toISOString();
    if (action === 'approve') {
      step.status = 'APPROVED';
      step.decidedAt = now;
      step.decidedByUserId = MOCK_CURRENT_USER_ID;
      step.decidedByName = MOCK_CURRENT_USER_NAME;
      step.notes = body.notes ?? null;
      a.decisions.push({
        id: uuid(),
        approvalRequestId: a.id,
        approvalStepId: step.id,
        decision: 'APPROVED',
        decidedByUserId: MOCK_CURRENT_USER_ID,
        decidedByName: MOCK_CURRENT_USER_NAME,
        decidedAt: now,
        notes: body.notes ?? null,
        reason: null,
      });
      // Advance to next step or complete
      const nextStep = a.steps.find((s) => s.stepOrder === step.stepOrder + 1);
      if (nextStep) {
        a.currentStepOrder = nextStep.stepOrder;
      } else {
        a.status = 'APPROVED';
        a.completedAt = now;
        a.currentStepOrder = null;
      }
      return ok(a);
    }
    const reasonErr = reasonRequired(body.reason);
    if (reasonErr) return reasonErr;
    if (action === 'reject') {
      step.status = 'REJECTED';
      step.decidedAt = now;
      step.decidedByUserId = MOCK_CURRENT_USER_ID;
      step.decidedByName = MOCK_CURRENT_USER_NAME;
      step.reason = body.reason ?? null;
      a.status = 'REJECTED';
      a.completedAt = now;
      a.currentStepOrder = null;
      a.decisions.push({
        id: uuid(),
        approvalRequestId: a.id,
        approvalStepId: step.id,
        decision: 'REJECTED',
        decidedByUserId: MOCK_CURRENT_USER_ID,
        decidedByName: MOCK_CURRENT_USER_NAME,
        decidedAt: now,
        notes: null,
        reason: body.reason ?? null,
      });
      return ok(a);
    }
    // request-changes
    step.status = 'PENDING';
    step.reason = body.reason ?? null;
    a.status = 'CHANGES_REQUESTED';
    a.completedAt = now;
    a.decisions.push({
      id: uuid(),
      approvalRequestId: a.id,
      approvalStepId: step.id,
      decision: 'CHANGES_REQUESTED',
      decidedByUserId: MOCK_CURRENT_USER_ID,
      decidedByName: MOCK_CURRENT_USER_NAME,
      decidedAt: now,
      notes: null,
      reason: body.reason ?? null,
    });
    return ok(a);
  }

  return null;
}

// ============================================================
// MVP 2 Phase 1 — Comment handlers
// ============================================================

function commentById(id: string) {
  return mockDb.comments.find((c) => c.id === id);
}

const MENTION_RE = /@\[([0-9a-f-]{6,})\|([^\]]+)\]/gi;

function extractMentions(body: string) {
  const out: { userId: string; userName: string | null }[] = [];
  for (const m of body.matchAll(MENTION_RE)) {
    out.push({ userId: m[1], userName: m[2] });
  }
  return out;
}

function handleComments(
  method: string,
  path: string,
  query: URLSearchParams,
  config: AxiosRequestConfig,
): MatchResult | null {
  if (path === '/comments/my-mentions' && method === 'GET') {
    const items = mockDb.comments.filter(
      (c) => !c.deletedAt && c.mentions.some((m) => m.userId === MOCK_CURRENT_USER_ID),
    );
    return ok({ items });
  }

  if (path === '/comments' && method === 'GET') {
    const entityType = query.get('entityType');
    const entityId = query.get('entityId');
    if (!entityType || !entityId) {
      return { status: 400, body: { code: 'VALIDATION_FAILED', message: 'entityType + entityId required' } };
    }
    const items = mockDb.comments
      .filter((c) => c.entityType === entityType && c.entityId === entityId)
      .sort((a, b) => a.createdAt.localeCompare(b.createdAt));
    return ok({ items });
  }

  if (path === '/comments' && method === 'POST') {
    const raw = readBody<Record<string, unknown>>(config);
    const body = stripTenantFromBody(raw, path, 'POST') as {
      entityType?: string;
      entityId?: string;
      body?: string;
      parentCommentId?: string | null;
    };
    if (!body.entityType || !body.entityId || !body.body || body.body.trim().length === 0) {
      return { status: 400, body: { code: 'VALIDATION_FAILED', message: 'entityType, entityId, body required' } };
    }
    if (body.body.length > 5000) {
      return { status: 400, body: { code: 'BODY_TOO_LONG', message: 'body max 5000 chars' } };
    }
    const now = new Date().toISOString();
    const next: (typeof mockDb.comments)[number] = {
      id: uuid(),
      entityType: body.entityType as never,
      entityId: body.entityId,
      parentCommentId: body.parentCommentId ?? null,
      authorUserId: MOCK_CURRENT_USER_ID,
      authorName: MOCK_CURRENT_USER_NAME,
      body: body.body,
      createdAt: now,
      updatedAt: null,
      deletedAt: null,
      mentions: extractMentions(body.body),
    };
    mockDb.comments.push(next);
    return ok(next, 201);
  }

  const detail = /^\/comments\/([^/]+)$/.exec(path);
  if (detail) {
    const c = commentById(detail[1]);
    if (!c) return notFound();
    if (method === 'PATCH') {
      if (c.authorUserId !== MOCK_CURRENT_USER_ID) {
        return { status: 403, body: { code: 'FORBIDDEN', message: 'Only the author can edit' } };
      }
      const raw = readBody<Record<string, unknown>>(config);
      const body = stripTenantFromBody(raw, path, 'PATCH') as { body?: string };
      if (!body.body || body.body.trim().length === 0) {
        return { status: 400, body: { code: 'VALIDATION_FAILED', message: 'body required' } };
      }
      if (body.body.length > 5000) {
        return { status: 400, body: { code: 'BODY_TOO_LONG', message: 'body max 5000 chars' } };
      }
      c.body = body.body;
      c.updatedAt = new Date().toISOString();
      c.mentions = extractMentions(body.body);
      return ok(c);
    }
    if (method === 'DELETE') {
      if (c.authorUserId !== MOCK_CURRENT_USER_ID) {
        return { status: 403, body: { code: 'FORBIDDEN', message: 'Only the author can delete' } };
      }
      c.deletedAt = new Date().toISOString();
      return { status: 204, body: null };
    }
  }

  return null;
}

// ============================================================
// MVP 2 Phase 2 — Import / Export handlers
// ============================================================

/**
 * The mock worker simulates the backend ImportProcessingJob by progressing the
 * batch status through SCANNING → PARSING → VALIDATING → READY_FOR_REVIEW
 * with 1.5s delays per stage. Idempotent — calling twice is a no-op.
 */
function progressImportBatch(id: string) {
  const find = () => mockDb.importBatches.find((b) => b.id === id);
  const advance = (from: string, to: string) => {
    setTimeout(() => {
      const b = find();
      if (b && b.status === from) {
        b.status = to as typeof b.status;
      }
    }, 0);
  };
  // Schedule the staged transitions.
  setTimeout(() => {
    const b = find();
    if (b && b.status === 'UPLOADED') b.status = 'SCANNING';
  }, 250);
  setTimeout(() => {
    const b = find();
    if (b && b.status === 'SCANNING') b.status = 'PARSING';
  }, 750);
  setTimeout(() => {
    const b = find();
    if (b && b.status === 'PARSING') b.status = 'VALIDATING';
  }, 1500);
  setTimeout(() => {
    const b = find();
    if (b && b.status === 'VALIDATING') {
      b.status = 'READY_FOR_REVIEW';
      b.totalRowCount = b.totalRowCount ?? 12;
      b.errorRowCount = b.errorRowCount ?? 0;
      b.warningRowCount = b.warningRowCount ?? 0;
    }
  }, 2500);
  void advance;
}

function handleImports(
  method: string,
  path: string,
  query: URLSearchParams,
  config: AxiosRequestConfig,
): MatchResult | null {
  // POST /imports/upload
  if (path === '/imports/upload' && method === 'POST') {
    // params come from the query string (axios put them under config.params)
    const templateCode = query.get('templateCode') ?? '';
    const projectId = query.get('projectId') ?? null;
    if (!templateCode) {
      return { status: 400, body: { code: 'VALIDATION_FAILED', message: 'templateCode required' } };
    }
    const id = uuid();
    const now = new Date().toISOString();
    // axios with FormData has data as FormData, fall back to filename guess.
    let filename = 'upload.xlsx';
    let size = 0;
    const data = config.data as FormData | undefined;
    if (data && typeof (data as FormData).get === 'function') {
      const f = (data as FormData).get('file');
      if (f && typeof f === 'object' && 'name' in f) {
        filename = (f as File).name ?? filename;
        size = (f as File).size ?? 0;
      }
    }
    const batch = {
      id,
      projectId,
      templateCode,
      status: 'UPLOADED' as const,
      originalFilename: filename,
      fileSize: size,
      fileChecksum: 'sha256:mock',
      totalRowCount: null,
      errorRowCount: null,
      warningRowCount: null,
      committedRowCount: null,
      containsSalaryData: false,
      uploadedBy: '00000000-0000-0000-0000-000000000001',
      uploadedAt: now,
      traceId: `trace-${id}`,
    };
    mockDb.importBatches.unshift(batch);
    progressImportBatch(id);
    return ok(batch, 201);
  }

  // GET /imports
  if (path === '/imports' && method === 'GET') {
    const projectId = query.get('projectId');
    const status = query.get('status');
    const templateCode = query.get('templateCode');
    let list = mockDb.importBatches;
    if (projectId) list = list.filter((b) => b.projectId === projectId);
    if (status) list = list.filter((b) => b.status === status);
    if (templateCode) list = list.filter((b) => b.templateCode === templateCode);
    const page = parseInt(query.get('page') ?? '0', 10);
    const size = parseInt(query.get('size') ?? '20', 10);
    return ok(paginate(list, page, size));
  }

  // GET /imports/:id
  const detail = /^\/imports\/([^/]+)$/.exec(path);
  if (detail && method === 'GET') {
    const b = mockDb.importBatches.find((x) => x.id === detail[1]);
    if (!b) return notFound();
    return ok(b);
  }

  // GET /imports/:id/errors
  const errs = /^\/imports\/([^/]+)\/errors$/.exec(path);
  if (errs && method === 'GET') {
    const id = errs[1];
    const level = query.get('level');
    let list = mockDb.importErrors.filter((e) => e.importBatchId === id);
    if (level) list = list.filter((e) => e.errorLevel === level);
    const page = parseInt(query.get('page') ?? '0', 10);
    const size = parseInt(query.get('size') ?? '50', 10);
    return ok(paginate(list, page, size));
  }

  // POST /imports/:id/commit
  const commit = /^\/imports\/([^/]+)\/commit$/.exec(path);
  if (commit && method === 'POST') {
    const b = mockDb.importBatches.find((x) => x.id === commit[1]);
    if (!b) return notFound();
    if (b.status !== 'READY_FOR_REVIEW' && b.status !== 'READY_TO_COMMIT') {
      return {
        status: 409,
        body: { code: 'INVALID_TRANSITION', message: `Cannot commit ${b.status}` },
      };
    }
    b.status = 'COMMITTED';
    b.committedAt = new Date().toISOString();
    b.committedBy = '00000000-0000-0000-0000-000000000001';
    b.committedRowCount = (b.totalRowCount ?? 0) - (b.errorRowCount ?? 0);
    return ok(b);
  }

  // POST /imports/:id/cancel
  const cancel = /^\/imports\/([^/]+)\/cancel$/.exec(path);
  if (cancel && method === 'POST') {
    const b = mockDb.importBatches.find((x) => x.id === cancel[1]);
    if (!b) return notFound();
    if (['COMMITTED', 'CANCELLED', 'ARCHIVED'].includes(b.status)) {
      return {
        status: 409,
        body: { code: 'INVALID_TRANSITION', message: `Cannot cancel ${b.status}` },
      };
    }
    b.status = 'CANCELLED';
    return ok(b);
  }

  return null;
}

function handleExports(
  method: string,
  path: string,
  query: URLSearchParams,
  config: AxiosRequestConfig,
): MatchResult | null {
  // POST /exports/request
  if (path === '/exports/request' && method === 'POST') {
    const raw = readBody<Record<string, unknown>>(config);
    const body = stripTenantFromBody(raw, path, 'POST') as {
      exportType?: string;
      format?: 'XLSX' | 'CSV' | 'PDF' | 'DOCX';
      projectId?: string;
      filterParams?: string | null;
    };
    if (!body.exportType || !body.format || !body.projectId) {
      return {
        status: 400,
        body: { code: 'VALIDATION_FAILED', message: 'exportType, format, projectId required' },
      };
    }
    const id = uuid();
    const now = new Date().toISOString();
    const job = {
      id,
      projectId: body.projectId,
      exportType: body.exportType,
      format: body.format,
      status: 'REQUESTED' as const,
      requestedBy: '00000000-0000-0000-0000-000000000001',
      requestedAt: now,
      generatedAt: null,
      expiresAt: null,
      downloadedAt: null,
      rowCount: null,
      fileSize: null,
      containsSalaryData: ['SALARY_RANGES', 'RED_GREEN_CIRCLE', 'COMPENSATION_SCENARIOS'].includes(
        body.exportType,
      ),
      containsPersonalData: false,
      signedUrl: null,
    };
    mockDb.exportJobs.unshift(job);
    // Simulate worker pipeline.
    setTimeout(() => {
      const j = mockDb.exportJobs.find((x) => x.id === id);
      if (j && j.status === 'REQUESTED') j.status = 'QUEUED';
    }, 250);
    setTimeout(() => {
      const j = mockDb.exportJobs.find((x) => x.id === id);
      if (j && j.status === 'QUEUED') j.status = 'GENERATING';
    }, 750);
    setTimeout(() => {
      const j = mockDb.exportJobs.find((x) => x.id === id);
      if (j && j.status === 'GENERATING') {
        const t = new Date().toISOString();
        j.status = 'GENERATED';
        j.generatedAt = t;
        j.expiresAt = new Date(Date.now() + 7 * 24 * 3600 * 1000).toISOString();
        j.rowCount = 42;
        j.fileSize = 56234;
        j.signedUrl = `https://mock-storage.local/exports/${id}/result.${j.format.toLowerCase()}?sig=stub`;
      }
    }, 1500);
    return ok(job, 201);
  }

  // GET /exports
  if (path === '/exports' && method === 'GET') {
    const projectId = query.get('projectId');
    const status = query.get('status');
    const type = query.get('type');
    let list = mockDb.exportJobs;
    if (projectId) list = list.filter((j) => j.projectId === projectId);
    if (status) list = list.filter((j) => j.status === status);
    if (type) list = list.filter((j) => j.exportType === type);
    const page = parseInt(query.get('page') ?? '0', 10);
    const size = parseInt(query.get('size') ?? '20', 10);
    return ok(paginate(list, page, size));
  }

  // GET /exports/:id
  const detail = /^\/exports\/([^/]+)$/.exec(path);
  if (detail && method === 'GET') {
    const j = mockDb.exportJobs.find((x) => x.id === detail[1]);
    if (!j) return notFound();
    return ok(j);
  }

  // GET /exports/:id/download-url
  const dl = /^\/exports\/([^/]+)\/download-url$/.exec(path);
  if (dl && method === 'GET') {
    const j = mockDb.exportJobs.find((x) => x.id === dl[1]);
    if (!j) return notFound();
    if (j.status !== 'GENERATED' && j.status !== 'DOWNLOADED') {
      return {
        status: 409,
        body: { code: 'NOT_DOWNLOADABLE', message: `Export not in downloadable state (${j.status})` },
      };
    }
    j.downloadedAt = new Date().toISOString();
    if (j.status === 'GENERATED') j.status = 'DOWNLOADED';
    // Generate a real file (CSV with DEMO banner) and serve via blob URL so
    // the browser performs a real download instead of navigating to a stub
    // hostname. See PO spec §6.2.
    if (typeof URL !== 'undefined' && typeof URL.createObjectURL === 'function') {
      const blob = exportBlobFor(j.exportType);
      const url = URL.createObjectURL(blob);
      j.signedUrl = url;
      return ok({ url });
    }
    // Fallback (non-browser env): keep prior stub.
    return ok({ url: j.signedUrl ?? `https://mock-storage.local/exports/${j.id}/result.xlsx?sig=stub` });
  }

  // POST /exports/:id/cancel
  const cancel = /^\/exports\/([^/]+)\/cancel$/.exec(path);
  if (cancel && method === 'POST') {
    const j = mockDb.exportJobs.find((x) => x.id === cancel[1]);
    if (!j) return notFound();
    if (['GENERATED', 'DOWNLOADED', 'CANCELLED', 'EXPIRED'].includes(j.status)) {
      return {
        status: 409,
        body: { code: 'INVALID_TRANSITION', message: `Cannot cancel ${j.status}` },
      };
    }
    j.status = 'CANCELLED';
    return ok(j);
  }
  return null;
}

// ============================================================
// MVP 2 Phase 3 — Report handlers (Reports Center)
// Mirrors backend ReportController — see architecture §17.
// ============================================================

const REPORT_FORMAT_AVAILABILITY_MOCK: Record<string, string[]> = {
  GRADE_DISTRIBUTION: ['PDF', 'DOCX', 'XLSX'],
  POSITION_CATALOG: ['PDF', 'DOCX', 'XLSX'],
  EVALUATION_SUMMARY: ['PDF', 'DOCX', 'XLSX'],
  METHODOLOGY_SPEC: ['PDF', 'DOCX'],
  AUDIT_SUMMARY: ['PDF', 'XLSX'],
  EXECUTIVE_SUMMARY: ['PDF'],
};

function defaultReportTitle(type: string): string {
  switch (type) {
    case 'GRADE_DISTRIBUTION':
      return 'Grade distribution report';
    case 'POSITION_CATALOG':
      return 'Position catalog report';
    case 'EVALUATION_SUMMARY':
      return 'Evaluation summary report';
    case 'METHODOLOGY_SPEC':
      return 'Methodology specification report';
    case 'AUDIT_SUMMARY':
      return 'Audit summary report';
    case 'EXECUTIVE_SUMMARY':
      return 'Executive summary report';
    default:
      return 'Report';
  }
}

function handleReports(
  method: string,
  path: string,
  query: URLSearchParams,
  config: AxiosRequestConfig,
): MatchResult | null {
  // POST /reports/request
  if (path === '/reports/request' && method === 'POST') {
    const raw = readBody<Record<string, unknown>>(config);
    const body = stripTenantFromBody(raw, path, 'POST') as {
      reportType?: string;
      format?: 'PDF' | 'DOCX' | 'XLSX';
      projectId?: string;
      filterParams?: string | null;
    };
    if (!body.reportType || !body.format || !body.projectId) {
      return {
        status: 400,
        body: { code: 'VALIDATION_FAILED', message: 'reportType, format, projectId required' },
      };
    }
    const supported = REPORT_FORMAT_AVAILABILITY_MOCK[body.reportType];
    if (!supported || !supported.includes(body.format)) {
      return {
        status: 400,
        body: {
          code: 'FORMAT_NOT_SUPPORTED',
          message: `Format ${body.format} is not supported for type ${body.reportType}`,
        },
      };
    }
    const id = uuid();
    const now = new Date().toISOString();
    const report: import('./fixtures').MockReport = {
      id,
      projectId: body.projectId,
      reportType: body.reportType as import('./fixtures').MockReportType,
      format: body.format,
      status: 'REQUESTED',
      title: defaultReportTitle(body.reportType),
      locale: 'ru-RU',
      requestedBy: '00000000-0000-0000-0000-000000000001',
      requestedAt: now,
      generatedAt: null,
      expiresAt: null,
      downloadedAt: null,
      fileSize: null,
      containsSalaryData: false,
      containsPersonalData: false,
      attemptCount: 0,
      failureReason: null,
      traceId: `trace-${id}`,
      signedUrl: null,
    };
    mockDb.reports.unshift(report);
    // Simulate the async worker pipeline over ~2 seconds.
    setTimeout(() => {
      const r = mockDb.reports.find((x) => x.id === id);
      if (r && r.status === 'REQUESTED') r.status = 'QUEUED';
    }, 250);
    setTimeout(() => {
      const r = mockDb.reports.find((x) => x.id === id);
      if (r && r.status === 'QUEUED') r.status = 'GENERATING';
    }, 750);
    setTimeout(() => {
      const r = mockDb.reports.find((x) => x.id === id);
      if (r && r.status === 'GENERATING') {
        const t = new Date().toISOString();
        r.status = 'GENERATED';
        r.generatedAt = t;
        r.expiresAt = new Date(Date.now() + 14 * 24 * 3600 * 1000).toISOString();
        r.fileSize = 134267;
        r.attemptCount = 1;
        r.signedUrl = `https://mock-storage.local/reports/${id}/result.${r.format.toLowerCase()}?sig=stub`;
      }
    }, 2000);
    return ok(report, 201);
  }

  // GET /reports
  if (path === '/reports' && method === 'GET') {
    const projectId = query.get('projectId');
    const status = query.get('status');
    const type = query.get('type');
    const format = query.get('format');
    let list = mockDb.reports;
    if (projectId) list = list.filter((r) => r.projectId === projectId);
    if (status) list = list.filter((r) => r.status === status);
    if (type) list = list.filter((r) => r.reportType === type);
    if (format) list = list.filter((r) => r.format === format);
    const page = parseInt(query.get('page') ?? '0', 10);
    const size = Math.min(parseInt(query.get('size') ?? '20', 10), 200);
    return ok(paginate(list, page, size));
  }

  // GET /reports/:id/download-url  (matched BEFORE /reports/:id)
  const dl = /^\/reports\/([^/]+)\/download-url$/.exec(path);
  if (dl && method === 'GET') {
    const r = mockDb.reports.find((x) => x.id === dl[1]);
    if (!r) return notFound();
    if (r.status !== 'GENERATED' && r.status !== 'DOWNLOADED') {
      return {
        status: 409,
        body: { code: 'NOT_DOWNLOADABLE', message: `Report not in downloadable state (${r.status})` },
      };
    }
    r.downloadedAt = new Date().toISOString();
    if (r.status === 'GENERATED') r.status = 'DOWNLOADED';
    if (typeof URL !== 'undefined' && typeof URL.createObjectURL === 'function') {
      const blob = exportBlobFor('REPORT_EXECUTIVE');
      const url = URL.createObjectURL(blob);
      r.signedUrl = url;
      return ok({ url });
    }
    return ok({
      url:
        r.signedUrl ??
        `https://mock-storage.local/reports/${r.id}/result.${r.format.toLowerCase()}?sig=stub`,
    });
  }

  // POST /reports/:id/cancel
  const cancel = /^\/reports\/([^/]+)\/cancel$/.exec(path);
  if (cancel && method === 'POST') {
    const r = mockDb.reports.find((x) => x.id === cancel[1]);
    if (!r) return notFound();
    if (['GENERATED', 'DOWNLOADED', 'CANCELLED', 'EXPIRED', 'FAILED'].includes(r.status)) {
      return {
        status: 409,
        body: { code: 'INVALID_TRANSITION', message: `Cannot cancel ${r.status}` },
      };
    }
    r.status = 'CANCELLED';
    return ok(r);
  }

  // GET /reports/:id  (kept last to avoid colliding with /download-url + /cancel)
  const detail = /^\/reports\/([^/]+)$/.exec(path);
  if (detail && method === 'GET') {
    const r = mockDb.reports.find((x) => x.id === detail[1]);
    if (!r) return notFound();
    return ok(r);
  }

  return null;
}

// ============================================================
// Template downloads + export blob (DEMO mode only)
// Produces CSV bytes with a DEMO header so a real file always downloads,
// even without a running backend. Filenames keep the .csv extension so
// Excel opens them natively.
// ============================================================

const DEMO_BANNER = 'Generated by grading.hrlab.uz (DEMO) - ACME Holdings';

function csvEscape(value: string): string {
  if (value == null) return '';
  // Formula injection guard — mirrors backend SafeCellWriter (=, +, -, @, tab, CR, LF).
  let v = value;
  if (v.length > 0 && /[=+\-@\t\r\n]/.test(v.charAt(0))) {
    v = "'" + v;
  }
  if (/[",\n]/.test(v)) {
    return `"${v.replace(/"/g, '""')}"`;
  }
  return v;
}

function csvBlob(headers: string[], rows: string[][]): Blob {
  const lines: string[] = [];
  // DEMO banner row first.
  lines.push(headers.map((_h, i) => (i === 0 ? csvEscape(DEMO_BANNER) : '')).join(','));
  lines.push(headers.map(csvEscape).join(','));
  for (const r of rows) lines.push(r.map(csvEscape).join(','));
  return new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8' });
}

const TEMPLATE_HEADERS: Record<string, string[]> = {
  ORG_STRUCTURE_V1: [
    'external_id', 'name', 'parent_external_id', 'level', 'type', 'location_code', 'cost_center_code', 'description',
  ],
  POSITION_CATALOG_V1: [
    'external_id', 'title', 'department_external_id', 'status', 'function', 'category', 'job_family', 'job_level', 'headcount',
  ],
  JOB_PROFILE_V1: [
    'position_external_id', 'purpose', 'main_duties', 'responsibility_area', 'authority', 'kpi_expected_results',
    'education_requirements', 'experience_requirements', 'knowledge_skills', 'working_conditions', 'actualization_date',
  ],
  METHODOLOGY_FACTORS_V1: [
    'factor_code', 'factor_name', 'factor_weight', 'level_code', 'level_name', 'level_points', 'level_order', 'level_description',
  ],
  GRADE_BANDS_V1: ['grade_code', 'min_score', 'max_score', 'label', 'description'],
};

const TEMPLATE_SAMPLE_ROWS: Record<string, string[][]> = {
  ORG_STRUCTURE_V1: [
    ['CEO-OFFICE',     'CEO Office',              '',            '1', 'OFFICE',     'HQ', 'CC-001', 'Holding executive office'],
    ['CFO-OFFICE',     'CFO Office',              'CEO-OFFICE',  '2', 'OFFICE',     'HQ', 'CC-100', 'Finance'],
    ['CTO-OFFICE',     'CTO Office',              'CEO-OFFICE',  '2', 'OFFICE',     'HQ', 'CC-200', 'Technology'],
    ['HR-OFFICE',      'HR Office',               'CEO-OFFICE',  '2', 'OFFICE',     'HQ', 'CC-300', 'People'],
    ['OPS-OFFICE',     'Operations Office',       'CEO-OFFICE',  '2', 'OFFICE',     'HQ', 'CC-400', 'Operations'],
    ['FIN-DEPT',       'Finance Department',      'CFO-OFFICE',  '3', 'DEPARTMENT', 'HQ', 'CC-110', 'FP&A'],
    ['TREASURY-DEPT',  'Treasury Department',     'CFO-OFFICE',  '3', 'DEPARTMENT', 'HQ', 'CC-120', 'Cash & banking'],
    ['IT-INFRA-DEPT',  'IT Infrastructure',       'CTO-OFFICE',  '3', 'DEPARTMENT', 'HQ', 'CC-210', 'Infra'],
    ['SW-ENG-DEPT',    'Software Engineering',    'CTO-OFFICE',  '3', 'DEPARTMENT', 'HQ', 'CC-220', 'Engineering'],
    ['HR-OPS-DEPT',    'HR Operations',           'HR-OFFICE',   '3', 'DEPARTMENT', 'HQ', 'CC-310', 'Payroll & admin'],
    ['TALENT-DEPT',    'Talent Acquisition',      'HR-OFFICE',   '3', 'DEPARTMENT', 'HQ', 'CC-320', 'Recruiting'],
    ['OPS-DEPT',       'Operations',              'OPS-OFFICE',  '3', 'DEPARTMENT', 'HQ', 'CC-410', 'Ops'],
  ],
  POSITION_CATALOG_V1: [
    ['POS-CFO',     'CFO',                      'CFO-OFFICE',     'ACTIVE', 'MANAGEMENT', 'MANAGER',    'Finance',     'C-level', '1'],
    ['POS-SR-FIN',  'Senior Financial Analyst', 'FIN-DEPT',       'ACTIVE', 'SPECIALIST', 'SPECIALIST', 'Finance',     'Senior',  '2'],
    ['POS-FIN',     'Financial Analyst',        'FIN-DEPT',       'ACTIVE', 'SPECIALIST', 'SPECIALIST', 'Finance',     'Mid',     '3'],
    ['POS-HEAD-TR', 'Head of Treasury',         'TREASURY-DEPT',  'ACTIVE', 'MANAGEMENT', 'MANAGER',    'Treasury',    'Lead',    '1'],
    ['POS-TR',      'Treasury Specialist',      'TREASURY-DEPT',  'ACTIVE', 'SPECIALIST', 'SPECIALIST', 'Treasury',    'Mid',     '2'],
    ['POS-CTO',     'CTO',                      'CTO-OFFICE',     'ACTIVE', 'MANAGEMENT', 'MANAGER',    'Engineering', 'C-level', '1'],
    ['POS-VP-ENG',  'VP Engineering',           'CTO-OFFICE',     'ACTIVE', 'MANAGEMENT', 'MANAGER',    'Engineering', 'VP',      '1'],
    ['POS-SR-SWE',  'Senior Software Engineer', 'SW-ENG-DEPT',    'ACTIVE', 'SPECIALIST', 'SPECIALIST', 'Engineering', 'Senior',  '4'],
    ['POS-SWE',     'Software Engineer',        'SW-ENG-DEPT',    'ACTIVE', 'SPECIALIST', 'SPECIALIST', 'Engineering', 'Mid',     '6'],
    ['POS-JR-SWE',  'Junior Engineer',          'SW-ENG-DEPT',    'ACTIVE', 'SPECIALIST', 'SPECIALIST', 'Engineering', 'Junior',  '3'],
    ['POS-HRD',     'HR Director',              'HR-OFFICE',      'ACTIVE', 'MANAGEMENT', 'MANAGER',    'HR',          'Director','1'],
    ['POS-HRBP',    'HR Business Partner',      'HR-OPS-DEPT',    'ACTIVE', 'SPECIALIST', 'SPECIALIST', 'HR',          'Senior',  '2'],
    ['POS-REC',     'Recruiter',                'TALENT-DEPT',    'ACTIVE', 'SPECIALIST', 'SPECIALIST', 'HR',          'Mid',     '3'],
    ['POS-OPS-MGR', 'Operations Manager',       'OPS-DEPT',       'ACTIVE', 'MANAGEMENT', 'MANAGER',    'Operations',  'Manager', '1'],
    ['POS-OPS',    'Operations Specialist',     'OPS-DEPT',       'ACTIVE', 'OPERATIONAL','SPECIALIST', 'Operations',  'Mid',     '5'],
  ],
  JOB_PROFILE_V1: (() => {
    const r: string[][] = [];
    const acts = '2026-01-15';
    r.push(['POS-CFO', 'Lead financial strategy', 'Own budgeting; lead audits', 'Group financial health', 'Approve budgets up to USD 5M', 'EBITDA growth; on-time close', 'MBA Finance', '10+ years finance leadership', 'Corporate finance; IFRS; SAP', 'HQ; ~40h/wk', acts]);
    r.push(['POS-CTO', 'Define tech strategy', 'Lead engineering, infra, security', 'Platform reliability', 'Approve roadmaps', 'Uptime 99.9%; incidents = 0', 'MSc CS', '10+ years tech leadership', 'Cloud arch; security', 'HQ; on-call', acts]);
    r.push(['POS-SR-SWE', 'Build platform services', 'Design; review; mentor; ship', 'Service quality', 'Approve PRs; RFCs', 'P95 latency; mentee growth', 'BSc CS', '5+ years backend', 'Java; PostgreSQL; AWS', 'Hybrid; 40h/wk', acts]);
    r.push(['POS-HRD', 'Lead HR strategy', 'Own talent, performance, comp', 'People function', 'Approve org/comp', 'Turnover; engagement; TTF', 'BSc/MSc HR', '8+ years HR', 'Comp&Ben; TA; OD', 'HQ; 40h/wk', acts]);
    r.push(['POS-OPS-MGR', 'Run Operations Office', 'Plan capacity; monitor KPIs', 'Throughput and quality', 'Approve schedules', 'Throughput; defect rate', 'BSc Eng/Bus', '7+ years ops mgmt', 'Lean; Six Sigma; ERP', 'On-site; 40h/wk', acts]);
    return r;
  })(),
  METHODOLOGY_FACTORS_V1: (() => {
    const factors = [
      ['KNOWLEDGE', 'Knowledge'],
      ['EXPERIENCE', 'Experience'],
      ['COMPLEXITY', 'Complexity'],
      ['RESPONSIBILITY', 'Responsibility'],
      ['AUTONOMY', 'Autonomy'],
      ['INFLUENCE', 'Influence'],
      ['COMMUNICATION', 'Communication'],
      ['WORKING_CONDITIONS', 'Working conditions'],
    ];
    const levels = ['L1 Basic', 'L2 Beginning', 'L3 Intermediate', 'L4 Advanced', 'L5 Expert'];
    const points = [40, 80, 120, 160, 200];
    const out: string[][] = [];
    for (const [code, name] of factors) {
      for (let i = 0; i < levels.length; i++) {
        out.push([code, name, '12.5', `L${i + 1}`, levels[i], String(points[i]), String(i + 1), `${name} - ${levels[i]}`]);
      }
    }
    return out;
  })(),
  GRADE_BANDS_V1: (() => {
    const labels = [
      'Junior', 'Junior+', 'Specialist', 'Specialist+',
      'Senior Specialist', 'Lead Specialist', 'Team Lead',
      'Senior Lead', 'Manager', 'Senior Manager',
      'Department Head', 'Director', 'VP', 'C-level',
    ];
    const rows: string[][] = [];
    for (let i = 0; i < 14; i++) {
      const min = i === 0 ? '0' : `${i * 50}.0001`;
      const max = String((i + 1) * 50);
      rows.push([`G${i + 1}`, min, max, labels[i], `Grade ${i + 1} - ${labels[i]}`]);
    }
    return rows;
  })(),
};

function handleImportTemplates(method: string, path: string): MatchResult | null {
  const match = /^\/imports\/templates\/([^/]+)\/(empty|sample)\.xlsx$/.exec(path);
  if (!match || method !== 'GET') return null;
  const [, code, variant] = match;
  const headers = TEMPLATE_HEADERS[code];
  if (!headers) return notFound();
  const rows = variant === 'sample' ? TEMPLATE_SAMPLE_ROWS[code] ?? [] : [];
  const blob = csvBlob(headers, rows);
  return { status: 200, body: blob, contentType: 'text/csv;charset=utf-8' };
}

function exportBlobFor(exportType: string): Blob {
  switch (exportType) {
    case 'METHODOLOGY':
      return csvBlob(TEMPLATE_HEADERS.METHODOLOGY_FACTORS_V1, TEMPLATE_SAMPLE_ROWS.METHODOLOGY_FACTORS_V1);
    case 'GRADE_STRUCTURE':
    case 'GRADE_PYRAMID':
      return csvBlob(TEMPLATE_HEADERS.GRADE_BANDS_V1, TEMPLATE_SAMPLE_ROWS.GRADE_BANDS_V1);
    case 'POSITION_CATALOG':
      return csvBlob(TEMPLATE_HEADERS.POSITION_CATALOG_V1, TEMPLATE_SAMPLE_ROWS.POSITION_CATALOG_V1);
    case 'JOB_PROFILES':
      return csvBlob(TEMPLATE_HEADERS.JOB_PROFILE_V1, TEMPLATE_SAMPLE_ROWS.JOB_PROFILE_V1);
    case 'EVALUATION_MATRIX': {
      const headers = ['position_external_id', 'position_title', 'KNOWLEDGE', 'EXPERIENCE', 'COMPLEXITY', 'RESPONSIBILITY', 'AUTONOMY', 'INFLUENCE', 'COMMUNICATION', 'WORKING_CONDITIONS', 'total_score', 'grade_code'];
      const rows = TEMPLATE_SAMPLE_ROWS.POSITION_CATALOG_V1.slice(0, 15).map((p, idx) => {
        const score = 30 + idx * 60;
        const grade = `G${Math.min(14, Math.ceil(score / 50))}`;
        return [p[0], p[1], '120', '120', '160', '120', '80', '80', '80', '40', String(score), grade];
      });
      return csvBlob(headers, rows);
    }
    case 'SALARY_RANGES':
    case 'RED_GREEN_CIRCLE':
    case 'COMPENSATION_SCENARIOS': {
      const headers = ['grade_code', 'label', 'min', 'mid', 'max', 'currency', 'note'];
      const rows = TEMPLATE_SAMPLE_ROWS.GRADE_BANDS_V1.map((g) => [
        g[0], g[3], '[SALARY MASKED IN DEMO]', '[SALARY MASKED IN DEMO]', '[SALARY MASKED IN DEMO]', 'UZS', 'demo only',
      ]);
      return csvBlob(headers, rows);
    }
    case 'REPORT_EXECUTIVE':
    default: {
      const headers = ['section', 'metric', 'value'];
      const rows = [
        ['Summary', 'Project', 'ACME Holdings 2026'],
        ['Summary', 'Status', 'Demo'],
        ['Counts', 'Departments', '12'],
        ['Counts', 'Positions', '15'],
        ['Counts', 'Grades', '14'],
      ];
      return csvBlob(headers, rows);
    }
  }
}

export function tryHandle(config: AxiosRequestConfig): MatchResult | null {
  const method = (config.method ?? 'get').toUpperCase();
  const url = config.url ?? '';
  const { path, query } = parseUrl(url, config.params as Record<string, unknown> | undefined);
  return (
    handleProjects(method, path, query, config) ??
    handleDepartments(method, path, query, config) ??
    handlePositions(method, path, query, config) ??
    handleJobProfiles(method, path, query, config) ??
    handleJobAnalysis(method, path, query, config) ??
    handleMethodologyTemplates(method, path, config) ??
    handleMethodologies(method, path, query, config) ??
    handleMethodologyVersions(method, path, query, config) ??
    handleFactors(method, path, query, config) ??
    handleFactorLevels(method, path, query, config) ??
    handleEvaluations(method, path, query, config) ??
    handleGradeStructures(method, path, query, config) ??
    handleGradesAndBands(method, path, query, config) ??
    handleApprovals(method, path, query, config) ??
    handleComments(method, path, query, config) ??
    handleImportTemplates(method, path) ??
    handleImports(method, path, query, config) ??
    handleExports(method, path, query, config) ??
    handleReports(method, path, query, config) ??
    handleUsers(config) ??
    handleAudit(config) ??
    handleClients(config) ??
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
    const contentType = matched.contentType ?? 'application/json';
    const response: AxiosResponse = {
      data: matched.body,
      status: matched.status,
      statusText: matched.status === 201 ? 'Created' : 'OK',
      headers: { 'content-type': contentType },
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
