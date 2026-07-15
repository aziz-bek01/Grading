/**
 * AuditDetailsDrawer hash-chain badge tests (MVP1-E10-1).
 *
 * Coverage:
 *   - Default (no verification run this session) → the original honest
 *     "Not independently verified" disclosure, unchanged.
 *   - After a verification result is available (written to the SAME cache
 *     slot `AuditIntegrityPanel` writes on success) the badge upgrades to
 *     the earned evidence — "Independently verified: X of Y rows, …" — and
 *     never claims more than the backend actually returned.
 */
import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { AuditDetailsDrawer } from './AuditDetailsDrawer';
import { auditKeys } from '../api/auditApi';
import type { AuditEvent } from '../types/auditTypes';
import { renderWithProviders, signInWithPermissions, signOut, createTestQueryClient } from '@/test/testUtils';
import { PERMISSIONS } from '@/shared/types/permissions';

const ACME_TENANT_ID = '11111111-1111-1111-1111-111111111111';

const EVENT: AuditEvent = {
  id: 'evt-1',
  action: 'PROJECT_CREATED',
  tenantId: ACME_TENANT_ID,
  actorUserId: 'u-1',
  actorName: 'Anna Karimova',
  entityType: 'PROJECT',
  entityId: 'proj-1',
  reason: null,
  ipAddress: '10.0.0.1',
  userAgent: 'test-agent',
  correlationId: 'corr-1',
  createdAt: '2026-07-10T08:00:00Z',
  hashCurrent: 'hcur',
  hashPrevious: 'hprev',
  metadata: null,
  before: null,
  after: null,
};

beforeEach(() => {
  signInWithPermissions([PERMISSIONS.AUDIT_READ]);
});

afterEach(() => {
  signOut();
});

describe('<AuditDetailsDrawer /> hash-chain badge honesty', () => {
  it('shows "Not independently verified" before any verification has run', () => {
    render(renderWithProviders(<AuditDetailsDrawer event={EVENT} open onClose={() => {}} />));
    const badge = screen.getByTestId('audit-hash-chain-badge');
    expect(badge).toHaveTextContent(/не проверено независимо/i);
  });

  it('upgrades to earned evidence after an INTACT verification run', () => {
    const qc = createTestQueryClient();
    qc.setQueryData(auditKeys.integrity(ACME_TENANT_ID), {
      tenantId: ACME_TENANT_ID,
      status: 'INTACT',
      intact: true,
      checkedCount: 1284,
      chainLength: 1284,
      independentlyVerifiedCount: 1200,
      legacyUnverifiableCount: 84,
      verifiableFrom: '2026-07-15T00:00:00Z',
      verifiedThrough: '2026-07-15T09:41:12Z',
      bounded: false,
      maxRows: 50000,
      firstBreak: null,
      verifiedAt: '2026-07-15T09:42:03Z',
    });

    render(
      renderWithProviders(<AuditDetailsDrawer event={EVENT} open onClose={() => {}} />, ['/'], qc),
    );
    const badge = screen.getByTestId('audit-hash-chain-badge');
    expect(badge).toHaveTextContent('1200');
    expect(badge).toHaveTextContent('1284');
    expect(badge).not.toHaveTextContent(/не проверено независимо/i);
  });

  it('reflects a BROKEN verification as "hash chain broken", not a false pass', () => {
    const qc = createTestQueryClient();
    qc.setQueryData(auditKeys.integrity(ACME_TENANT_ID), {
      tenantId: ACME_TENANT_ID,
      status: 'BROKEN',
      intact: false,
      checkedCount: 500,
      chainLength: 1284,
      independentlyVerifiedCount: 400,
      legacyUnverifiableCount: 99,
      verifiableFrom: null,
      verifiedThrough: null,
      bounded: false,
      maxRows: 50000,
      firstBreak: {
        rowId: 'row-1',
        createdAt: '2026-07-14T18:03:00Z',
        breakType: 'HASH_MISMATCH',
        expectedHash: 'aaa',
        actualHash: 'bbb',
      },
      verifiedAt: '2026-07-15T09:42:03Z',
    });

    render(
      renderWithProviders(<AuditDetailsDrawer event={EVENT} open onClose={() => {}} />, ['/'], qc),
    );
    const badge = screen.getByTestId('audit-hash-chain-badge');
    expect(badge).toHaveTextContent(/цепочка хешей нарушена/i);
  });
});
