/**
 * FE-8 schema tests: the bulk-create request/result schemas + the wizard roster
 * cap (1 mandatory EXTERNAL_EXPERT + up to 3 ADDITIONAL externals = 4).
 */
import { describe, it, expect } from 'vitest';
import {
  BulkCreatePanelsRequestSchema,
  BulkCreatePanelsResultSchema,
  WizardRosterDraftSchema,
} from './panelSchemas';

describe('BulkCreatePanelsRequestSchema (FE-8)', () => {
  it('accepts a non-empty position list with an optional shared roster', () => {
    const r = BulkCreatePanelsRequestSchema.safeParse({
      methodology_version_id: 'v1',
      position_ids: ['p1', 'p2'],
      roster: [{ evaluator_user_id: 'u1', evaluator_role: 'HR_DIRECTOR' }],
    });
    expect(r.success).toBe(true);
  });

  it('rejects an empty position list', () => {
    const r = BulkCreatePanelsRequestSchema.safeParse({
      methodology_version_id: 'v1',
      position_ids: [],
    });
    expect(r.success).toBe(false);
  });

  it('rejects more than 200 positions (MAX_PAGE_SIZE mirror)', () => {
    const r = BulkCreatePanelsRequestSchema.safeParse({
      methodology_version_id: 'v1',
      position_ids: Array.from({ length: 201 }, (_, i) => `p${i}`),
    });
    expect(r.success).toBe(false);
  });
});

describe('BulkCreatePanelsResultSchema (FE-8) — seat_failures shape', () => {
  it('parses a clean success-all result', () => {
    expect(
      BulkCreatePanelsResultSchema.safeParse({ created: 5, failed: [] }).success,
    ).toBe(true);
  });

  it('parses an ALREADY_EXISTS row WITHOUT seat_failures', () => {
    expect(
      BulkCreatePanelsResultSchema.safeParse({
        created: 4,
        failed: [{ position_id: 'p1', error_code: 'ALREADY_EXISTS', message: 'dup' }],
      }).success,
    ).toBe(true);
  });

  it('parses a ROSTER_PARTIAL row WITH seat_failures', () => {
    expect(
      BulkCreatePanelsResultSchema.safeParse({
        created: 4,
        failed: [
          {
            position_id: 'p1',
            error_code: 'ROSTER_PARTIAL',
            message: 'seat',
            seat_failures: [
              { evaluator_role: 'EXTERNAL_EXPERT', evaluator_user_id: 'u3', reason: 'withdrawn' },
            ],
          },
        ],
      }).success,
    ).toBe(true);
  });

  it('rejects an unknown error_code', () => {
    expect(
      BulkCreatePanelsResultSchema.safeParse({
        created: 0,
        failed: [{ position_id: 'p1', error_code: 'NOPE', message: 'x' }],
      }).success,
    ).toBe(false);
  });
});

describe('WizardRosterDraftSchema (FE-8) — external cap + min-3', () => {
  const seat = (role: string, id: string | null) => ({ role, evaluator_user_id: id });

  it('accepts the mandatory trio (min-3 satisfied)', () => {
    const r = WizardRosterDraftSchema.safeParse([
      seat('HR_DIRECTOR', 'u1'),
      seat('DEPARTMENT_DIRECTOR', 'u2'),
      seat('EXTERNAL_EXPERT', 'u3'),
    ]);
    expect(r.success).toBe(true);
  });

  it('rejects a roster missing a mandatory role', () => {
    const r = WizardRosterDraftSchema.safeParse([
      seat('HR_DIRECTOR', 'u1'),
      seat('DEPARTMENT_DIRECTOR', 'u2'),
      seat('EXTERNAL_EXPERT', null),
    ]);
    expect(r.success).toBe(false);
  });

  it('accepts 4 external seats (1 EXTERNAL_EXPERT + 3 ADDITIONAL)', () => {
    const r = WizardRosterDraftSchema.safeParse([
      seat('HR_DIRECTOR', 'u1'),
      seat('DEPARTMENT_DIRECTOR', 'u2'),
      seat('EXTERNAL_EXPERT', 'u3'),
      seat('ADDITIONAL', 'u4'),
      seat('ADDITIONAL', 'u5'),
      seat('ADDITIONAL', 'u6'),
    ]);
    expect(r.success).toBe(true);
  });

  it('rejects a 5th external seat (over the cap of 4)', () => {
    const r = WizardRosterDraftSchema.safeParse([
      seat('HR_DIRECTOR', 'u1'),
      seat('DEPARTMENT_DIRECTOR', 'u2'),
      seat('EXTERNAL_EXPERT', 'u3'),
      seat('ADDITIONAL', 'u4'),
      seat('ADDITIONAL', 'u5'),
      seat('ADDITIONAL', 'u6'),
      seat('ADDITIONAL', 'u7'),
    ]);
    expect(r.success).toBe(false);
  });
});
