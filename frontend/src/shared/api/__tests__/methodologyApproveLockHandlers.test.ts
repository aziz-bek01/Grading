/**
 * Phase 4 remediation — D-406 / PC4-4 MSW approve/lock handler tests.
 *
 * Previously, the MSW approve handler collapsed APPROVED + LOCKED into a
 * single transition (set status = LOCKED immediately). The master plan §14
 * status machine requires a two-step APPROVE → LOCK, with APPROVED as a
 * distinct intermediate state.
 *
 * This suite asserts:
 *   1. POST /methodology-versions/:id/approve from DRAFT lands in APPROVED
 *      (not LOCKED), populates approved_at + approved_by + approved_by_name.
 *   2. POST .../lock from APPROVED lands in LOCKED, populates locked_*.
 *   3. POST .../approve from non-DRAFT returns 409 INVALID_TRANSITION.
 *   4. POST .../lock from non-APPROVED returns 409 INVALID_TRANSITION.
 */
import { describe, expect, it, beforeEach } from 'vitest';
import type { AxiosRequestConfig } from 'axios';
import { tryHandle } from '../mocks/handlers';
import { mockDb } from '../mocks/fixtures';
import type { MockMethodologyVersion } from '../mocks/fixtures';

describe('D-406 / PC4-4 — MSW approve/lock handlers', () => {
  let draftVersionId: string;
  let approvedVersionId: string;

  beforeEach(() => {
    // The seeded mockDb has 2 versions for the CFO methodology — v1 (APPROVED)
    // and v2 (DRAFT). Tests mutate these in place, so we reset to a known
    // baseline at every beforeEach by index, not by current status.
    expect(mockDb.methodologyVersions.length).toBeGreaterThanOrEqual(2);
    // Approve-baseline target = v2 (the existing DRAFT slot).
    const draftSlot = mockDb.methodologyVersions[1] as MockMethodologyVersion;
    draftSlot.status = 'DRAFT';
    draftSlot.approved_at = null;
    draftSlot.approved_by = null;
    draftSlot.approved_by_name = null;
    draftSlot.locked_at = null;
    draftSlot.locked_by = null;
    draftSlot.locked_by_name = null;
    draftVersionId = draftSlot.id;

    // Lock-baseline target = v1.
    const approvedSlot = mockDb.methodologyVersions[0] as MockMethodologyVersion;
    approvedSlot.status = 'APPROVED';
    approvedSlot.approved_at = '2026-04-12T10:00:00Z';
    approvedSlot.approved_by = '7e9c1234-5678-90ab-cdef-1234567890ab';
    approvedSlot.approved_by_name = 'Dilshod Karimov';
    approvedSlot.locked_at = null;
    approvedSlot.locked_by = null;
    approvedSlot.locked_by_name = null;
    approvedVersionId = approvedSlot.id;
  });

  it('approve from DRAFT → APPROVED (NOT LOCKED) + approved_at + approved_by_name', () => {
    const config: AxiosRequestConfig = {
      method: 'POST',
      url: `/methodology-versions/${draftVersionId}/approve`,
      data: '{}',
      headers: { 'X-Mock-Tenant-Id': '11111111-1111-1111-1111-111111111111' },
    };
    const result = tryHandle(config);
    expect(result).not.toBeNull();
    expect(result?.status).toBe(200);
    const body = result?.body as MockMethodologyVersion;
    expect(body.status).toBe('APPROVED');
    expect(body.status).not.toBe('LOCKED');
    expect(body.approved_at).toBeTruthy();
    expect(body.approved_by).toBeTruthy();
    expect(body.approved_by_name).toBe('Mock Approver');
    // Lock fields must NOT have been set by the approve handler.
    expect(body.locked_at ?? null).toBeNull();
    expect(body.locked_by ?? null).toBeNull();
  });

  it('approve from non-DRAFT returns 409 INVALID_TRANSITION', () => {
    const config: AxiosRequestConfig = {
      method: 'POST',
      url: `/methodology-versions/${approvedVersionId}/approve`,
      data: '{}',
      headers: { 'X-Mock-Tenant-Id': '11111111-1111-1111-1111-111111111111' },
    };
    const result = tryHandle(config);
    expect(result?.status).toBe(409);
    const body = result?.body as { code: string };
    expect(body.code).toBe('INVALID_TRANSITION');
  });

  it('lock from APPROVED → LOCKED + locked_at + locked_by_name', () => {
    const config: AxiosRequestConfig = {
      method: 'POST',
      url: `/methodology-versions/${approvedVersionId}/lock`,
      data: '{}',
      headers: { 'X-Mock-Tenant-Id': '11111111-1111-1111-1111-111111111111' },
    };
    const result = tryHandle(config);
    expect(result?.status).toBe(200);
    const body = result?.body as MockMethodologyVersion;
    expect(body.status).toBe('LOCKED');
    expect(body.locked_at).toBeTruthy();
    expect(body.locked_by).toBeTruthy();
    expect(body.locked_by_name).toBe('Mock Locker');
    // Approved metadata must be preserved.
    expect(body.approved_by_name).toBe('Dilshod Karimov');
  });

  it('lock from non-APPROVED returns 409 INVALID_TRANSITION', () => {
    // draftVersionId is currently in DRAFT — direct LOCK must be rejected.
    const config: AxiosRequestConfig = {
      method: 'POST',
      url: `/methodology-versions/${draftVersionId}/lock`,
      data: '{}',
      headers: { 'X-Mock-Tenant-Id': '11111111-1111-1111-1111-111111111111' },
    };
    const result = tryHandle(config);
    expect(result?.status).toBe(409);
    const body = result?.body as { code: string };
    expect(body.code).toBe('INVALID_TRANSITION');
  });

  it('full lifecycle DRAFT → APPROVED → LOCKED via two requests', () => {
    // Step 1: APPROVE
    const approveResult = tryHandle({
      method: 'POST',
      url: `/methodology-versions/${draftVersionId}/approve`,
      data: '{}',
      headers: { 'X-Mock-Tenant-Id': '11111111-1111-1111-1111-111111111111' },
    } as AxiosRequestConfig);
    expect((approveResult?.body as MockMethodologyVersion).status).toBe('APPROVED');

    // Step 2: LOCK
    const lockResult = tryHandle({
      method: 'POST',
      url: `/methodology-versions/${draftVersionId}/lock`,
      data: '{}',
      headers: { 'X-Mock-Tenant-Id': '11111111-1111-1111-1111-111111111111' },
    } as AxiosRequestConfig);
    expect(lockResult?.status).toBe(200);
    const locked = lockResult?.body as MockMethodologyVersion;
    expect(locked.status).toBe('LOCKED');
    expect(locked.approved_by_name).toBe('Mock Approver');
    expect(locked.locked_by_name).toBe('Mock Locker');
  });
});
