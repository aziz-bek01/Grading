/**
 * MSW fixtures for the Audit Reader (D-1 FE).
 *
 * 28 sample events across two tenants (ACME = 11111…, Beta = 22222…)
 * covering: auth, project lifecycle, position changes, methodology
 * version approval, evaluation calibration (with reason), grade
 * assignment, user salary permission grant, import/export, and a couple
 * of security-probe events. Hashes form a plausible chain — current
 * event's `hashPrevious` matches the previous event's `hashCurrent`.
 *
 * Tenant ids match `devAuth.ts` so the active-tenant filter works
 * against the same scope the auth store sees.
 */
import type { AuditEvent } from '../types/auditTypes';

const TENANT_ACME = '11111111-1111-1111-1111-111111111111';
const TENANT_BETA = '22222222-2222-2222-2222-222222222222';

const ACTOR_HRLAB = { id: 'u-hrlab-001', name: 'HRLab Admin' };
const ACTOR_ANNA = { id: 'u-acme-anna', name: 'Anna Karimova' };
const ACTOR_BEKZOD = { id: 'u-acme-bekzod', name: 'Bekzod Tursunov' };
const ACTOR_JOHN = { id: 'u-beta-john', name: 'John Smith' };

let hashCounter = 0;
function nextHash(): string {
  hashCounter += 1;
  // Deterministic pseudo-hash so the chain is repeatable in tests.
  return `h${hashCounter.toString(16).padStart(8, '0')}-mock`;
}

interface MockOpts {
  metadata?: Record<string, unknown>;
  before?: Record<string, unknown>;
  after?: Record<string, unknown>;
  reason?: string;
  correlationId?: string;
  ip?: string;
  ua?: string;
}

function event(
  id: string,
  action: string,
  tenantId: string | null,
  actor: { id: string; name: string } | null,
  entityType: string | null,
  entityId: string | null,
  createdAt: string,
  prevHash: string | null,
  opts: MockOpts = {},
): AuditEvent {
  return {
    id,
    action,
    tenantId,
    actorUserId: actor?.id ?? null,
    actorName: actor?.name ?? null,
    entityType,
    entityId,
    reason: opts.reason ?? null,
    ipAddress: opts.ip ?? '10.0.0.42',
    userAgent: opts.ua ?? 'Mozilla/5.0 (compatible; grading.hrlab.uz/0.x)',
    correlationId: opts.correlationId ?? `corr-${id}`,
    createdAt,
    hashPrevious: prevHash,
    hashCurrent: nextHash(),
    metadata: opts.metadata ?? null,
    before: opts.before ?? null,
    after: opts.after ?? null,
  };
}

const SEED: AuditEvent[] = [];

// Build the seed list in chronological order so the hash chain is plausible,
// then reverse to "newest first" at the end.
function push(...evts: AuditEvent[]): void {
  SEED.push(...evts);
}

let prev: string | null = null;
const link = (e: AuditEvent): AuditEvent => {
  e.hashPrevious = prev;
  prev = e.hashCurrent ?? null;
  return e;
};

push(
  link(event('a-001', 'LOGIN_SUCCESS', TENANT_ACME, ACTOR_HRLAB, 'USER', ACTOR_HRLAB.id, '2026-05-15T08:00:00Z', null)),
  link(event('a-002', 'PROJECT_CREATED', TENANT_ACME, ACTOR_HRLAB, 'PROJECT', 'proj-acme-2026', '2026-05-15T08:05:12Z', null, {
    after: { code: 'ACME-2026', name_ru: 'Грейдинг 2026' },
  })),
  link(event('a-003', 'TENANT_CONTEXT_SWITCH', TENANT_ACME, ACTOR_HRLAB, 'TENANT', TENANT_ACME, '2026-05-15T08:06:00Z', null)),
  link(event('a-004', 'DEPARTMENT_CREATED', TENANT_ACME, ACTOR_ANNA, 'DEPARTMENT', 'dept-001', '2026-05-15T09:14:21Z', null, {
    after: { code: 'CEO-OFFICE', type: 'OFFICE' },
  })),
  link(event('a-005', 'POSITION_CREATED', TENANT_ACME, ACTOR_ANNA, 'POSITION', 'pos-001', '2026-05-15T10:01:00Z', null, {
    after: { code: 'POS-CFO', title_ru: 'Финансовый директор' },
  })),
  link(event('a-006', 'POSITION_UPDATED', TENANT_ACME, ACTOR_ANNA, 'POSITION', 'pos-001', '2026-05-15T10:18:33Z', null, {
    before: { category: 'SPECIALIST' },
    after: { category: 'MANAGEMENT' },
  })),
  link(event('a-007', 'JOB_PROFILE_CREATED', TENANT_ACME, ACTOR_ANNA, 'JOB_PROFILE', 'jp-001', '2026-05-15T10:45:00Z', null)),
  link(event('a-008', 'JOB_PROFILE_SUBMITTED', TENANT_ACME, ACTOR_ANNA, 'JOB_PROFILE', 'jp-001', '2026-05-16T07:11:00Z', null)),
  link(event('a-009', 'JOB_PROFILE_APPROVED', TENANT_ACME, ACTOR_HRLAB, 'JOB_PROFILE', 'jp-001', '2026-05-16T11:42:00Z', null)),
  link(event('a-010', 'METHODOLOGY_CREATED', TENANT_ACME, ACTOR_HRLAB, 'METHODOLOGY', 'm-001', '2026-05-17T09:00:00Z', null)),
  link(event('a-011', 'METHODOLOGY_VERSION_APPROVED', TENANT_ACME, ACTOR_HRLAB, 'METHODOLOGY_VERSION', 'mv-001', '2026-05-17T14:25:00Z', null, {
    metadata: { factorCount: 8, scoringMode: 'WEIGHTED_POINTS' },
  })),
  link(event('a-012', 'METHODOLOGY_VERSION_LOCKED', TENANT_ACME, ACTOR_HRLAB, 'METHODOLOGY_VERSION', 'mv-001', '2026-05-17T14:30:11Z', null)),
  link(event('a-013', 'EVALUATION_CREATED', TENANT_ACME, ACTOR_ANNA, 'EVALUATION', 'ev-001', '2026-05-18T08:14:00Z', null)),
  link(event('a-014', 'EVALUATION_SCORE_UPSERTED', TENANT_ACME, ACTOR_ANNA, 'EVALUATION_SCORE', 'es-001', '2026-05-18T08:45:00Z', null, {
    after: { factorId: 'f-001', factorLevelId: 'l-003' },
  })),
  link(event('a-015', 'EVALUATION_SUBMITTED', TENANT_ACME, ACTOR_ANNA, 'EVALUATION', 'ev-001', '2026-05-18T09:30:00Z', null)),
  link(event('a-016', 'EVALUATION_APPROVED', TENANT_ACME, ACTOR_HRLAB, 'EVALUATION', 'ev-001', '2026-05-18T13:00:00Z', null)),
  link(event('a-017', 'EVALUATION_SCORE_CALIBRATED', TENANT_ACME, ACTOR_HRLAB, 'EVALUATION_SCORE', 'es-001', '2026-05-19T10:11:00Z', null, {
    reason: 'Калибровка по решению комитета: пересмотр уровня фактора RESPONSIBILITY',
    before: { rawScore: 12.5 },
    after: { rawScore: 15.625 },
  })),
  link(event('a-018', 'GRADE_ASSIGNED', TENANT_ACME, ACTOR_HRLAB, 'POSITION', 'pos-001', '2026-05-19T10:11:42Z', null, {
    metadata: { gradeNumber: 12, totalScore: 760.5 },
  })),
  link(event('a-019', 'USER_INVITED', TENANT_ACME, ACTOR_HRLAB, 'USER', ACTOR_BEKZOD.id, '2026-05-19T11:00:00Z', null)),
  link(event('a-020', 'USER_SALARY_PERMISSION_GRANTED', TENANT_ACME, ACTOR_HRLAB, 'USER_MEMBERSHIP', ACTOR_ANNA.id, '2026-05-20T07:42:00Z', null, {
    reason: 'HR Director needs full compensation visibility for grading project',
  })),
  link(event('a-021', 'IMPORT_UPLOADED', TENANT_ACME, ACTOR_ANNA, 'IMPORT', 'imp-001', '2026-05-20T09:15:00Z', null, {
    metadata: { template: 'POSITION_CATALOG_V1', filename: 'positions-2026.xlsx' },
  })),
  link(event('a-022', 'IMPORT_COMMITTED', TENANT_ACME, ACTOR_ANNA, 'IMPORT', 'imp-001', '2026-05-20T09:18:14Z', null, {
    metadata: { rowsCommitted: 15 },
  })),
  link(event('a-023', 'EXPORT_REQUESTED', TENANT_ACME, ACTOR_ANNA, 'EXPORT', 'exp-001', '2026-05-21T14:00:00Z', null, {
    metadata: { type: 'EVALUATION_MATRIX', format: 'XLSX' },
  })),
  link(event('a-024', 'EXPORT_DOWNLOADED', TENANT_ACME, ACTOR_ANNA, 'EXPORT', 'exp-001', '2026-05-21T14:02:33Z', null)),
  link(event('a-025', 'CROSS_TENANT_OR_PROJECT_ACCESS_ATTEMPT', TENANT_ACME, ACTOR_BEKZOD, 'POSITION', 'pos-from-beta', '2026-05-22T11:30:00Z', null, {
    metadata: { attemptedTenantId: TENANT_BETA, decision: 'DENIED' },
  })),
  link(event('a-026', 'LOGIN_FAILED', null, null, 'USER', null, '2026-05-23T05:11:00Z', null, {
    metadata: { attemptedEmail: 'unknown@example.test', reason: 'INVALID_CREDENTIALS' },
    ip: '203.0.113.42',
  })),
  link(event('a-027', 'PROJECT_CREATED', TENANT_BETA, ACTOR_JOHN, 'PROJECT', 'proj-beta-2026', '2026-05-23T08:00:00Z', null, {
    after: { code: 'BETA-2026', name_en: 'Beta Grading 2026' },
  })),
  link(event('a-028', 'REPORT_GENERATED', TENANT_ACME, ACTOR_HRLAB, 'REPORT', 'rep-001', '2026-05-24T10:00:00Z', null, {
    metadata: { type: 'GRADE_DISTRIBUTION', format: 'PDF' },
  })),
);

/** Newest first — list endpoint serves this order by default. */
export const auditDb = {
  events: [...SEED].sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1)),
};
