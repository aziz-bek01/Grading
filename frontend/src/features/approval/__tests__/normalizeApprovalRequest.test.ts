import { describe, it, expect } from 'vitest';
import {
  normalizeApprovalRequest,
  normalizeApprovalStep,
} from '../api/approvalApi';

/**
 * Golden-file regression pin for the wire → domain adapter.
 *
 * This sample is a CAPTURED REAL-WIRE response from `ApprovalRequestResponse`
 * (SNAKE_CASE per application.yml:38, `@JsonInclude(NON_NULL)` — null fields
 * are OMITTED). If the BE later renames or drops a field, this test fails in CI
 * (not the prod page), and the BE-side `ApprovalControllerSecurityTest`
 * serialization assertions fail too. Mock ≡ prod wire ≡ this fixture.
 */
const REAL_WIRE_DETAIL = {
  id: 'appr-1',
  project_id: 'proj-acme-2026',
  entity_type: 'EVALUATION',
  entity_id: 'eval-1',
  requested_by: 'user-initiator',
  requested_at: '2026-05-15T08:30:00Z',
  current_status: 'PENDING',
  notes_i18n: { 'en-US': 'Please review.', 'ru-RU': 'Прошу проверить.' },
  steps: [
    {
      // already-decided step — carries decided_by/decided_at/reason_i18n
      id: 'step-1',
      step_order: 1,
      approver_user_id: 'user-approver',
      required_permission: 'EVALUATION_APPROVE',
      status: 'APPROVED',
      decided_by: 'user-approver',
      decided_at: '2026-05-16T09:00:00Z',
      reason_i18n: { 'en-US': 'Looks good.' },
    },
    {
      // PENDING step — NON_NULL omits decided_by/decided_at/reason_i18n
      id: 'step-2',
      step_order: 2,
      required_permission: 'EVALUATION_LOCK',
      status: 'PENDING',
    },
  ],
} as const;

describe('normalizeApprovalRequest (wire → domain adapter)', () => {
  it('maps snake_case request fields to camelCase domain fields', () => {
    const r = normalizeApprovalRequest(REAL_WIRE_DETAIL);
    expect(r.id).toBe('appr-1');
    expect(r.projectId).toBe('proj-acme-2026');
    expect(r.entityType).toBe('EVALUATION');
    expect(r.entityId).toBe('eval-1');
    expect(r.initiatedByUserId).toBe('user-initiator');
    expect(r.initiatedAt).toBe('2026-05-15T08:30:00Z');
    expect(r.status).toBe('PENDING');
    expect(r.notesI18n).toEqual({ 'en-US': 'Please review.', 'ru-RU': 'Прошу проверить.' });
  });

  it('derives display-only fields the BE does not send', () => {
    const r = normalizeApprovalRequest(REAL_WIRE_DETAIL);
    expect(r.totalSteps).toBe(2);
    // currentStepOrder = min(step_order where status === PENDING)
    expect(r.currentStepOrder).toBe(2);
    // BE does not send a name → null fallback (card falls back to UUID)
    expect(r.initiatedByName).toBeNull();
  });

  it('always returns arrays for steps and decisions', () => {
    const r = normalizeApprovalRequest(REAL_WIRE_DETAIL);
    expect(Array.isArray(r.steps)).toBe(true);
    expect(Array.isArray(r.decisions)).toBe(true);
    // empty / malformed payloads must not throw and must default to []
    const empty = normalizeApprovalRequest({});
    expect(empty.steps).toEqual([]);
    expect(empty.decisions).toEqual([]);
    expect(empty.status).toBe('PENDING');
  });

  it('maps step snake_case fields and localizes reason_i18n into reason', () => {
    const r = normalizeApprovalRequest(REAL_WIRE_DETAIL);
    const [decided, pending] = r.steps;
    expect(decided.stepOrder).toBe(1);
    expect(decided.approverUserId).toBe('user-approver');
    expect(decided.requiredPermission).toBe('EVALUATION_APPROVE');
    expect(decided.status).toBe('APPROVED');
    expect(decided.decidedByUserId).toBe('user-approver');
    expect(decided.decidedAt).toBe('2026-05-16T09:00:00Z');
    // reason_i18n localized into the flat `reason` string the StepCard reads
    expect(decided.reason).toBe('Looks good.');
    // PENDING step omits decision fields (NON_NULL) → null/undefined, no crash
    expect(pending.status).toBe('PENDING');
    expect(pending.decidedByUserId).toBeNull();
    expect(pending.reason).toBeNull();
  });

  it('synthesizes decisions[] from decided steps (not a BE field)', () => {
    const r = normalizeApprovalRequest(REAL_WIRE_DETAIL);
    expect(r.decisions).toHaveLength(1);
    const d = r.decisions[0];
    expect(d.approvalStepId).toBe('step-1');
    expect(d.decision).toBe('APPROVED');
    expect(d.decidedByUserId).toBe('user-approver');
    expect(d.decidedAt).toBe('2026-05-16T09:00:00Z');
    expect(d.reason).toBe('Looks good.');
  });

  it('maps REJECTED step status to a REJECTED decision', () => {
    const rejected = normalizeApprovalRequest({
      ...REAL_WIRE_DETAIL,
      current_status: 'REJECTED',
      steps: [
        {
          id: 's',
          step_order: 1,
          required_permission: 'EVALUATION_APPROVE',
          status: 'REJECTED',
          decided_by: 'u',
          decided_at: '2026-05-16T09:00:00Z',
          reason_i18n: { 'en-US': 'Insufficient detail provided.' },
        },
      ],
    });
    expect(rejected.decisions).toHaveLength(1);
    expect(rejected.decisions[0].decision).toBe('REJECTED');
    expect(rejected.decisions[0].reason).toBe('Insufficient detail provided.');
  });

  it('tolerates camelCase fallback (in-process MSW / legacy payloads)', () => {
    const camel = normalizeApprovalRequest({
      id: 'c-1',
      projectId: 'p',
      entityType: 'JOB_PROFILE',
      entityId: 'e',
      requestedBy: 'u',
      requestedAt: '2026-01-01T00:00:00Z',
      status: 'APPROVED',
      steps: [{ id: 's', stepOrder: 1, status: 'PENDING' }],
    });
    expect(camel.projectId).toBe('p');
    expect(camel.initiatedByUserId).toBe('u');
    expect(camel.initiatedAt).toBe('2026-01-01T00:00:00Z');
    expect(camel.status).toBe('APPROVED');
    expect(camel.steps[0].stepOrder).toBe(1);
  });

  it('normalizeApprovalStep maps an isolated step', () => {
    const s = normalizeApprovalStep(
      { id: 'x', step_order: 3, status: 'SKIPPED' },
      'req-1',
    );
    expect(s.approvalRequestId).toBe('req-1');
    expect(s.stepOrder).toBe(3);
    expect(s.status).toBe('SKIPPED');
    expect(s.reason).toBeNull();
  });

  // FE-1 / BE-3..BE-6: once the backend enriches the response, the SAME adapter
  // consumes entity_label_i18n + initiated_by_name + steps[].approver_name +
  // steps[].decided_by_name with no FE adapter change.
  it('consumes BE enrichment fields (entity label + actor names)', () => {
    const enriched = normalizeApprovalRequest({
      ...REAL_WIRE_DETAIL,
      entity_label_i18n: {
        'ru-RU': 'Старший разработчик · CFO Finance v1',
        'en-US': 'Senior SWE · CFO Finance v1',
      },
      initiated_by_name: 'HRLab Consultant',
      steps: [
        {
          id: 'step-1',
          step_order: 1,
          approver_user_id: 'user-approver',
          approver_name: 'Dev User',
          required_permission: 'EVALUATION_APPROVE',
          status: 'APPROVED',
          decided_by: 'user-approver',
          decided_by_name: 'Dev User',
          decided_at: '2026-05-16T09:00:00Z',
          reason_i18n: { 'en-US': 'Looks good.' },
        },
      ],
    });
    expect(enriched.entityLabel).toEqual({
      'ru-RU': 'Старший разработчик · CFO Finance v1',
      'en-US': 'Senior SWE · CFO Finance v1',
    });
    expect(enriched.initiatedByName).toBe('HRLab Consultant');
    expect(enriched.steps[0].approverName).toBe('Dev User');
    expect(enriched.steps[0].decidedByName).toBe('Dev User');
    // synthesized decision carries the decided-by name too
    expect(enriched.decisions[0].decidedByName).toBe('Dev User');
  });

  // Cross-project inbox fix: project_label_i18n mirrors entity_label_i18n —
  // mapped through the SAME asI18n helper, same NON_NULL omission semantics.
  it('maps project_label_i18n to projectLabel (mirrors entity_label_i18n)', () => {
    const withProject = normalizeApprovalRequest({
      ...REAL_WIRE_DETAIL,
      project_label_i18n: {
        'ru-RU': 'Acme HRTech 2026',
        'en-US': 'Acme HRTech 2026',
      },
    });
    expect(withProject.projectLabel).toEqual({
      'ru-RU': 'Acme HRTech 2026',
      'en-US': 'Acme HRTech 2026',
    });

    // BE omits project_label_i18n (NON_NULL) when the project cannot be
    // resolved — the adapter must yield undefined, never throw.
    const withoutProject = normalizeApprovalRequest(REAL_WIRE_DETAIL);
    expect(withoutProject.projectLabel).toBeUndefined();
  });

  // FE-1: when a label/name is OMITTED (NON_NULL), the adapter yields null and
  // the consumer falls back to a SHORT id (verified in the shortId + card tests),
  // never a raw full UUID. The adapter itself must keep the raw entityId intact.
  it('leaves entityLabel/names null when the BE omits them (NON_NULL)', () => {
    const bare = normalizeApprovalRequest({
      ...REAL_WIRE_DETAIL,
      entity_id: '40000000-0000-0000-0000-0000000000a2',
      requested_by: '50000000-0000-0000-0000-0000000000b3',
    });
    expect(bare.entityLabel).toBeUndefined();
    expect(bare.initiatedByName).toBeNull();
    // raw UUID preserved so the consumer can shorten it for display
    expect(bare.entityId).toBe('40000000-0000-0000-0000-0000000000a2');
    expect(bare.initiatedByUserId).toBe('50000000-0000-0000-0000-0000000000b3');
  });
});
