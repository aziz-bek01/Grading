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
  if (/\/grade-structures\/[^/]+\/pyramid$/.test(url)) {
    return { grade_structure_id: 'x', rows: [] };
  }
  if (url === '/grade-structures' || /\/grade-structures\?/.test(url)) {
    return { items: [] };
  }
  if (/\/exports\/[^/]+\/download-url$/.test(url)) {
    return { url: 'https://example.test/stub.xlsx' };
  }
  if (url === '/exports' || url.startsWith('/exports?')) {
    return { items: [], page: 0, size: 0, totalElements: 0, totalPages: 0 };
  }
  if (/\/reports\/[^/]+\/download-url$/.test(url)) {
    return { url: 'https://example.test/stub.pdf' };
  }
  if (url === '/reports' || url.startsWith('/reports?')) {
    return { items: [], page: 0, size: 0, totalElements: 0, totalPages: 0 };
  }
  if (/\/reports\/[^/]+$/.test(url)) {
    return {
      id: 'r-1',
      projectId: 'p-1',
      reportType: 'GRADE_DISTRIBUTION',
      format: 'PDF',
      status: 'GENERATED',
      title: 'Stub',
      locale: 'ru-RU',
      requestedBy: null,
      requestedAt: '2026-05-23T08:15:00Z',
      generatedAt: null,
      expiresAt: null,
      downloadedAt: null,
      fileSize: null,
      containsSalaryData: false,
      containsPersonalData: false,
      attemptCount: 0,
      failureReason: null,
      traceId: null,
    };
  }
  if (url === '/imports' || url.startsWith('/imports?')) {
    return { items: [], page: 0, size: 0, totalElements: 0, totalPages: 0 };
  }
  if (/\/imports\/[^/]+\/errors$/.test(url)) {
    return { items: [], page: 0, size: 0, totalElements: 0, totalPages: 0 };
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
    await createProject({ code: 'X', name_i18n: { 'ru-RU': 'X' } });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('projectApi.updateProject() sends no tenant id', async () => {
    recorded.length = 0;
    const { updateProject } = await import('@/features/projects/api/projectApi');
    await updateProject('proj-1', { name_i18n: { 'ru-RU': 'X' } });
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
      name_i18n: { 'ru-RU': 'X' },
      type: 'DEPARTMENT',
    });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('organizationApi.updateDepartment() sends no tenant id', async () => {
    recorded.length = 0;
    const { updateDepartment } = await import('@/features/organization/api/organizationApi');
    await updateDepartment('dep-1', { name_i18n: { 'ru-RU': 'X' } });
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
      title_i18n: { 'ru-RU': 'X' },
    });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('positionApi.updatePosition() sends no tenant id', async () => {
    recorded.length = 0;
    const { updatePosition } = await import('@/features/positions/api/positionApi');
    await updatePosition('pos-1', { title_i18n: { 'ru-RU': 'X' } });
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

  // ---------------------------------------------------------------
  // Phase 4 — Methodology / Factor / FactorLevel fetchers (F-401)
  // ---------------------------------------------------------------

  it('methodologyApi.addFactor() sends no tenant id', async () => {
    recorded.length = 0;
    const { addFactor } = await import('@/features/methodology/api/methodologyApi');
    await addFactor('v-1', {
      code: 'F1',
      name_i18n: { 'ru-RU': 'F1' },
      weight: 50,
      max_points: 100,
      required: true,
    });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('methodologyApi.updateFactor() sends no tenant id', async () => {
    recorded.length = 0;
    const { updateFactor } = await import('@/features/methodology/api/methodologyApi');
    await updateFactor('f-1', { weight: 60 });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('methodologyApi.reorderFactors() sends no tenant id', async () => {
    recorded.length = 0;
    const { reorderFactors } = await import('@/features/methodology/api/methodologyApi');
    await reorderFactors('v-1', { ordered_ids: ['f-1'] });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('methodologyApi.addFactorLevel() sends no tenant id', async () => {
    recorded.length = 0;
    const { addFactorLevel } = await import('@/features/methodology/api/methodologyApi');
    await addFactorLevel('f-1', {
      code: 'L1',
      points: 10,
      scale_value: 0.5,
      label_i18n: { 'ru-RU': 'L1' },
    });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('methodologyApi.updateFactorLevel() sends no tenant id', async () => {
    recorded.length = 0;
    const { updateFactorLevel } = await import('@/features/methodology/api/methodologyApi');
    await updateFactorLevel('l-1', { points: 20 });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  // ---------------------------------------------------------------
  // Phase 5 — Evaluation fetchers
  // ---------------------------------------------------------------

  it('evaluationApi.createEvaluation() sends no tenant id', async () => {
    recorded.length = 0;
    const { createEvaluation } = await import(
      '@/features/evaluation/api/evaluationApi'
    );
    await createEvaluation({ position_id: 'p-1', methodology_version_id: 'v-1' });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('evaluationApi.fetchEvaluations() sends no tenant id (projectId allowed)', async () => {
    recorded.length = 0;
    const { fetchEvaluations } = await import(
      '@/features/evaluation/api/evaluationApi'
    );
    await fetchEvaluations({ projectId: 'p-1', status: 'DRAFT' });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
    expect(recorded[0].params).toHaveProperty('projectId', 'p-1');
  });

  it('evaluationApi.upsertScore() sends no tenant id', async () => {
    recorded.length = 0;
    const { upsertScore } = await import(
      '@/features/evaluation/api/evaluationApi'
    );
    await upsertScore('e-1', { factor_id: 'f-1', factor_level_id: 'l-1' });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('evaluationApi.calibrateScore() sends no tenant id', async () => {
    recorded.length = 0;
    const { calibrateScore } = await import(
      '@/features/evaluation/api/evaluationApi'
    );
    await calibrateScore('e-1', {
      factor_id: 'f-1',
      new_raw_factor_score: 50,
      reason: 'Calibration after committee review meeting',
    });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('evaluationApi.previewScore() sends no tenant id', async () => {
    recorded.length = 0;
    const { previewScore } = await import(
      '@/features/evaluation/api/evaluationApi'
    );
    await previewScore({
      methodology_version_id: 'v-1',
      selections: [{ factor_id: 'f-1', factor_level_id: 'l-1' }],
    });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('evaluationApi.archiveEvaluation() sends no tenant id', async () => {
    recorded.length = 0;
    const { archiveEvaluation } = await import(
      '@/features/evaluation/api/evaluationApi'
    );
    await archiveEvaluation('e-1', {
      reason: 'Project archived after final delivery',
    });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  // ---------------------------------------------------------------
  // Phase 6 — Grade Structure / Grade / Band fetchers
  // ---------------------------------------------------------------

  it('gradeStructureApi.fetchGradeStructures() sends no tenant id', async () => {
    recorded.length = 0;
    const { fetchGradeStructures } = await import(
      '@/features/grade-structure/api/gradeStructureApi'
    );
    await fetchGradeStructures({ projectId: 'p-1', status: 'DRAFT' });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
    expect(recorded[0].params).toHaveProperty('projectId', 'p-1');
  });

  it('gradeStructureApi.fetchGradeStructure() sends no tenant id', async () => {
    recorded.length = 0;
    const { fetchGradeStructure } = await import(
      '@/features/grade-structure/api/gradeStructureApi'
    );
    await fetchGradeStructure('gs-1');
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('gradeStructureApi.createFromTemplate() sends no tenant id', async () => {
    recorded.length = 0;
    const { createFromTemplate } = await import(
      '@/features/grade-structure/api/gradeStructureApi'
    );
    await createFromTemplate({
      template_code: 'GRADE_14',
      project_id: 'p-1',
      code: 'TEST-14',
      name: { 'ru-RU': 'Тест' },
    });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('gradeStructureApi.createFromScratch() sends no tenant id', async () => {
    recorded.length = 0;
    const { createFromScratch } = await import(
      '@/features/grade-structure/api/gradeStructureApi'
    );
    await createFromScratch({
      project_id: 'p-1',
      code: 'CUSTOM-1',
      name: { 'ru-RU': 'Custom' },
      structure_type: 'CUSTOM',
      gap_policy: 'STRICT_NO_GAPS',
    });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('gradeStructureApi.updateMetadata() sends no tenant id', async () => {
    recorded.length = 0;
    const { updateMetadata } = await import(
      '@/features/grade-structure/api/gradeStructureApi'
    );
    await updateMetadata('gs-1', { code: 'NEW-CODE' });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('gradeStructureApi.approveStructure() sends no tenant id', async () => {
    recorded.length = 0;
    const { approveStructure } = await import(
      '@/features/grade-structure/api/gradeStructureApi'
    );
    await approveStructure('gs-1');
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('gradeStructureApi.lockStructure() sends no tenant id', async () => {
    recorded.length = 0;
    const { lockStructure } = await import(
      '@/features/grade-structure/api/gradeStructureApi'
    );
    await lockStructure('gs-1');
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('gradeStructureApi.archiveStructure() sends no tenant id', async () => {
    recorded.length = 0;
    const { archiveStructure } = await import(
      '@/features/grade-structure/api/gradeStructureApi'
    );
    await archiveStructure('gs-1', {
      reason: 'Replaced by 2027 grade structure update',
    });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('gradeStructureApi.createNewVersion() sends no tenant id', async () => {
    recorded.length = 0;
    const { createNewVersion } = await import(
      '@/features/grade-structure/api/gradeStructureApi'
    );
    await createNewVersion('gs-1');
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('gradeStructureApi.addGrade() sends no tenant id', async () => {
    recorded.length = 0;
    const { addGrade } = await import(
      '@/features/grade-structure/api/gradeStructureApi'
    );
    await addGrade('gs-1', {
      grade_number: 1,
      name: { 'ru-RU': 'Грейд 1' },
    });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('gradeStructureApi.updateGrade() sends no tenant id', async () => {
    recorded.length = 0;
    const { updateGrade } = await import(
      '@/features/grade-structure/api/gradeStructureApi'
    );
    await updateGrade('g-1', { grade_number: 2 });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('gradeStructureApi.removeGrade() sends no tenant id', async () => {
    recorded.length = 0;
    const { removeGrade } = await import(
      '@/features/grade-structure/api/gradeStructureApi'
    );
    await removeGrade('g-1');
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('gradeStructureApi.upsertBand() sends no tenant id', async () => {
    recorded.length = 0;
    const { upsertBand } = await import(
      '@/features/grade-structure/api/gradeStructureApi'
    );
    await upsertBand('g-1', { min_score: 0, max_score: 100 });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('gradeStructureApi.fetchGradePyramid() sends no tenant id', async () => {
    recorded.length = 0;
    const { fetchGradePyramid } = await import(
      '@/features/grade-structure/api/gradeStructureApi'
    );
    await fetchGradePyramid('gs-1');
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('gradeStructureApi.previewGradeLookup() sends no tenant id', async () => {
    recorded.length = 0;
    const { previewGradeLookup } = await import(
      '@/features/grade-structure/api/gradeStructureApi'
    );
    await previewGradeLookup('gs-1', { score: 75 });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  // ---------------------------------------------------------------
  // MVP 2 Phase 1 — Workflow / Approval / Comment fetchers
  // ---------------------------------------------------------------

  it('workflowApi.fetchWorkflowProgress() sends no tenant id', async () => {
    recorded.length = 0;
    const { fetchWorkflowProgress } = await import('@/features/workflow/api/workflowApi');
    await fetchWorkflowProgress('proj-1');
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('workflowApi.advanceWorkflow() sends no tenant id', async () => {
    recorded.length = 0;
    const { advanceWorkflow } = await import('@/features/workflow/api/workflowApi');
    await advanceWorkflow('proj-1', 'POSITIONS');
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('approvalApi.fetchMyInbox() sends no tenant id', async () => {
    recorded.length = 0;
    const { fetchMyInbox } = await import('@/features/approval/api/approvalApi');
    await fetchMyInbox();
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('approvalApi.fetchApprovalRequests() sends no tenant id (projectId allowed)', async () => {
    recorded.length = 0;
    const { fetchApprovalRequests } = await import(
      '@/features/approval/api/approvalApi'
    );
    await fetchApprovalRequests({ projectId: 'p-1', status: 'PENDING' });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
    expect(recorded[0].params).toHaveProperty('projectId', 'p-1');
  });

  it('approvalApi.createApprovalRequest() sends no tenant id', async () => {
    recorded.length = 0;
    const { createApprovalRequest } = await import(
      '@/features/approval/api/approvalApi'
    );
    await createApprovalRequest({ entityType: 'JOB_PROFILE', entityId: 'jp-1' });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('approvalApi.approveStep() sends no tenant id', async () => {
    recorded.length = 0;
    const { approveStep } = await import('@/features/approval/api/approvalApi');
    await approveStep('a-1', 'step-1');
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('approvalApi.rejectStep() sends no tenant id', async () => {
    recorded.length = 0;
    const { rejectStep } = await import('@/features/approval/api/approvalApi');
    await rejectStep('a-1', 'step-1', 'rejecting because of insufficient KPI clarity right now');
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('commentApi.fetchComments() sends no tenant id', async () => {
    recorded.length = 0;
    const { fetchComments } = await import('@/features/comment/api/commentApi');
    await fetchComments('JOB_PROFILE', 'jp-1');
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('commentApi.createComment() sends no tenant id', async () => {
    recorded.length = 0;
    const { createComment } = await import('@/features/comment/api/commentApi');
    await createComment({ entityType: 'JOB_PROFILE', entityId: 'jp-1', body: 'Hello' });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('commentApi.updateComment() sends no tenant id', async () => {
    recorded.length = 0;
    const { updateComment } = await import('@/features/comment/api/commentApi');
    await updateComment('c-1', { body: 'Updated' });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('commentApi.deleteComment() sends no tenant id', async () => {
    recorded.length = 0;
    const { deleteComment } = await import('@/features/comment/api/commentApi');
    await deleteComment('c-1');
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  // ---------------------------------------------------------------
  // MVP 2 Phase 2 — Import / Export fetchers
  // ---------------------------------------------------------------

  it('importApi.fetchImports() sends no tenant id (projectId allowed)', async () => {
    recorded.length = 0;
    const { fetchImports } = await import('@/features/import/api/importApi');
    await fetchImports({ projectId: 'p-1', status: 'UPLOADED' });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
    expect(recorded[0].params).toHaveProperty('projectId', 'p-1');
  });

  it('importApi.fetchImport() sends no tenant id', async () => {
    recorded.length = 0;
    const { fetchImport } = await import('@/features/import/api/importApi');
    await fetchImport('imp-1');
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('importApi.fetchImportErrors() sends no tenant id', async () => {
    recorded.length = 0;
    const { fetchImportErrors } = await import('@/features/import/api/importApi');
    await fetchImportErrors('imp-1', { level: 'BLOCKER' });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('importApi.uploadImport() sends no tenant id (FormData body without tenant)', async () => {
    recorded.length = 0;
    const { uploadImport } = await import('@/features/import/api/importApi');
    const file = new File([new Uint8Array(16)], 'sample.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    });
    await uploadImport({ file, templateCode: 'ORG_STRUCTURE_V1', projectId: 'p-1' });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
    expect(recorded[0].params).toHaveProperty('templateCode', 'ORG_STRUCTURE_V1');
  });

  it('importApi.commitImport() sends no tenant id', async () => {
    recorded.length = 0;
    const { commitImport } = await import('@/features/import/api/importApi');
    await commitImport('imp-1');
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('importApi.cancelImport() sends no tenant id', async () => {
    recorded.length = 0;
    const { cancelImport } = await import('@/features/import/api/importApi');
    await cancelImport('imp-1');
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('exportApi.requestExport() sends no tenant id', async () => {
    recorded.length = 0;
    const { requestExport } = await import('@/features/export/api/exportApi');
    await requestExport({
      exportType: 'POSITION_CATALOG',
      format: 'XLSX',
      projectId: 'p-1',
    });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('exportApi.fetchExports() sends no tenant id (projectId allowed)', async () => {
    recorded.length = 0;
    const { fetchExports } = await import('@/features/export/api/exportApi');
    await fetchExports({ projectId: 'p-1', status: 'GENERATED' });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
    expect(recorded[0].params).toHaveProperty('projectId', 'p-1');
  });

  it('exportApi.fetchExport() sends no tenant id', async () => {
    recorded.length = 0;
    const { fetchExport } = await import('@/features/export/api/exportApi');
    await fetchExport('exp-1');
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('exportApi.fetchExportDownloadUrl() sends no tenant id', async () => {
    recorded.length = 0;
    const { fetchExportDownloadUrl } = await import('@/features/export/api/exportApi');
    await fetchExportDownloadUrl('exp-1');
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('exportApi.cancelExport() sends no tenant id', async () => {
    recorded.length = 0;
    const { cancelExport } = await import('@/features/export/api/exportApi');
    await cancelExport('exp-1');
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  // ---------------------------------------------------------------
  // MVP 2 Phase 3 — Report fetchers (5 endpoints)
  // ---------------------------------------------------------------

  it('reportApi.requestReport() sends no tenant id', async () => {
    recorded.length = 0;
    const { requestReport } = await import('@/features/report/api/reportApi');
    await requestReport({
      reportType: 'GRADE_DISTRIBUTION',
      format: 'PDF',
      projectId: 'p-1',
    });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('reportApi.fetchReports() sends no tenant id (projectId allowed)', async () => {
    recorded.length = 0;
    const { fetchReports } = await import('@/features/report/api/reportApi');
    await fetchReports({ projectId: 'p-1', status: 'GENERATED' });
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
    expect(recorded[0].params).toHaveProperty('projectId', 'p-1');
  });

  it('reportApi.fetchReport() sends no tenant id', async () => {
    recorded.length = 0;
    const { fetchReport } = await import('@/features/report/api/reportApi');
    await fetchReport('rep-1');
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('reportApi.fetchReportDownloadUrl() sends no tenant id', async () => {
    recorded.length = 0;
    const { fetchReportDownloadUrl } = await import('@/features/report/api/reportApi');
    await fetchReportDownloadUrl('rep-1');
    expect(recorded.length).toBe(1);
    assertNoTenantLeak(recorded[0]);
  });

  it('reportApi.cancelReport() sends no tenant id', async () => {
    recorded.length = 0;
    const { cancelReport } = await import('@/features/report/api/reportApi');
    await cancelReport('rep-1');
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
      headers: { 'X-Mock-Tenant-Id': '11111111-1111-1111-1111-111111111111' },
    } as AxiosRequestConfig);
    expect(result?.status).toBe(201);
    const created = result?.body as { tenant_id: string };
    // Tenant must come from the mock-auth header, NEVER from body.
    expect(created.tenant_id).toBe('11111111-1111-1111-1111-111111111111');
    expect(warnSpy).toHaveBeenCalled();
    warnSpy.mockRestore();
  });

  it('mock GET /projects derives tenant from header, not query', async () => {
    const { tryHandle } = await import('../mocks/handlers');
    const result = tryHandle({
      method: 'GET',
      url: '/projects?tenantId=tenant-evil',
      headers: { 'X-Mock-Tenant-Id': '11111111-1111-1111-1111-111111111111' },
    } as AxiosRequestConfig);
    expect(result?.status).toBe(200);
    const body = result?.body as { items: { tenant_id: string }[] };
    for (const p of body.items) {
      expect(p.tenant_id).toBe('11111111-1111-1111-1111-111111111111');
    }
  });

  // ---------------------------------------------------------------
  // F-401 — MSW factor / level write handlers strip body.tenant_id
  // ---------------------------------------------------------------

  it('F-401: POST /methodology-versions/:id/factors ignores body.tenant_id and warns', async () => {
    const { tryHandle } = await import('../mocks/handlers');
    const { mockDb } = await import('../mocks/fixtures');
    const draft = mockDb.methodologyVersions.find((v) => v.status === 'DRAFT');
    expect(draft).toBeDefined();
    const before = draft!.factors.length;
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const result = tryHandle({
      method: 'POST',
      url: `/methodology-versions/${draft!.id}/factors`,
      data: JSON.stringify({
        tenant_id: 'tenant-evil',
        tenantId: 'tenant-evil',
        code: 'F-new',
        name: { 'ru-RU': 'F-new' },
        weight: 10,
        max_points: 100,
        required: true,
      }),
      headers: { 'X-Mock-Tenant-Id': '11111111-1111-1111-1111-111111111111' },
    } as AxiosRequestConfig);
    expect(result?.status).toBe(201);
    const created = result?.body as Record<string, unknown>;
    expect(created).not.toHaveProperty('tenant_id');
    expect(created).not.toHaveProperty('tenantId');
    expect(warnSpy).toHaveBeenCalled();
    expect(draft!.factors.length).toBe(before + 1);
    warnSpy.mockRestore();
  });

  it('F-401: PATCH /factors/:id ignores body.tenant_id and warns', async () => {
    const { tryHandle } = await import('../mocks/handlers');
    const { mockDb } = await import('../mocks/fixtures');
    const draft = mockDb.methodologyVersions.find((v) => v.status === 'DRAFT' && v.factors.length > 0);
    expect(draft).toBeDefined();
    const factor = draft!.factors[0];
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const result = tryHandle({
      method: 'PATCH',
      url: `/factors/${factor.id}`,
      data: JSON.stringify({ tenant_id: 'tenant-evil', weight: 42 }),
      headers: { 'X-Mock-Tenant-Id': '11111111-1111-1111-1111-111111111111' },
    } as AxiosRequestConfig);
    expect(result?.status).toBe(200);
    expect(warnSpy).toHaveBeenCalled();
    expect(factor.weight).toBe(42);
    warnSpy.mockRestore();
  });

  it('F-401: POST /methodology-versions/:id/factors/reorder ignores body.tenant_id', async () => {
    const { tryHandle } = await import('../mocks/handlers');
    const { mockDb } = await import('../mocks/fixtures');
    const draft = mockDb.methodologyVersions.find((v) => v.status === 'DRAFT' && v.factors.length > 0);
    expect(draft).toBeDefined();
    const factor = draft!.factors[0];
    // Backend ReorderRequest contract: ids-only, position = array index.
    const ids = draft!.factors.map((f) => f.id).reverse();
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const result = tryHandle({
      method: 'POST',
      url: `/methodology-versions/${draft!.id}/factors/reorder`,
      data: JSON.stringify({
        tenant_id: 'tenant-evil',
        ordered_ids: ids,
      }),
      headers: { 'X-Mock-Tenant-Id': '11111111-1111-1111-1111-111111111111' },
    } as AxiosRequestConfig);
    expect(result?.status).toBe(200);
    expect(warnSpy).toHaveBeenCalled();
    expect(factor.sort_order).toBe(ids.indexOf(factor.id));
    warnSpy.mockRestore();
  });

  it('F-401: POST /factors/:id/levels ignores body.tenant_id and warns', async () => {
    const { tryHandle } = await import('../mocks/handlers');
    const { mockDb } = await import('../mocks/fixtures');
    const draft = mockDb.methodologyVersions.find((v) => v.status === 'DRAFT' && v.factors.length > 0);
    expect(draft).toBeDefined();
    const factor = draft!.factors[0];
    const before = factor.levels.length;
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const result = tryHandle({
      method: 'POST',
      url: `/factors/${factor.id}/levels`,
      data: JSON.stringify({
        tenant_id: 'tenant-evil',
        code: 'L-new',
        points: 5,
        scale_value: 0.5,
        label: { 'ru-RU': 'L-new' },
      }),
      headers: { 'X-Mock-Tenant-Id': '11111111-1111-1111-1111-111111111111' },
    } as AxiosRequestConfig);
    expect(result?.status).toBe(201);
    const created = result?.body as Record<string, unknown>;
    expect(created).not.toHaveProperty('tenant_id');
    expect(factor.levels.length).toBe(before + 1);
    expect(warnSpy).toHaveBeenCalled();
    warnSpy.mockRestore();
  });

  it('F-401: PATCH /factor-levels/:id ignores body.tenant_id and warns', async () => {
    const { tryHandle } = await import('../mocks/handlers');
    const { mockDb } = await import('../mocks/fixtures');
    const draft = mockDb.methodologyVersions.find(
      (v) => v.status === 'DRAFT' && v.factors.some((f) => f.levels.length > 0),
    );
    if (!draft) {
      // Seed a level for the test if no fixture has one.
      const factor = mockDb.methodologyVersions
        .find((v) => v.status === 'DRAFT' && v.factors.length > 0)!
        .factors[0];
      factor.levels.push({
        id: 'l-test-001',
        factor_id: factor.id,
        code: 'L-test',
        level_order: 0,
        points: 1,
        scale_value: 0.1,
        label_i18n: { 'ru-RU': 'L-test' },
      });
    }
    const v = mockDb.methodologyVersions.find(
      (vv) => vv.status === 'DRAFT' && vv.factors.some((f) => f.levels.length > 0),
    )!;
    const factor = v.factors.find((f) => f.levels.length > 0)!;
    const level = factor.levels[0];
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const result = tryHandle({
      method: 'PATCH',
      url: `/factor-levels/${level.id}`,
      data: JSON.stringify({ tenant_id: 'tenant-evil', points: 99 }),
      headers: { 'X-Mock-Tenant-Id': '11111111-1111-1111-1111-111111111111' },
    } as AxiosRequestConfig);
    expect(result?.status).toBe(200);
    expect(warnSpy).toHaveBeenCalled();
    expect(level.points).toBe(99);
    warnSpy.mockRestore();
  });
});
