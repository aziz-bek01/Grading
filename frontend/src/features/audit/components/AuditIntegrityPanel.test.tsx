/**
 * AuditIntegrityPanel tests (MVP1-E10-1).
 *
 * Coverage:
 *   1. AUDIT_READ-gated visibility — hidden entirely without the permission.
 *   2. The four honest status renderings:
 *      - INTACT (bounded=false) → full "verified intact" pass.
 *      - INTACT (bounded=true)  → rendered as PARTIAL, never a plain "intact".
 *      - BROKEN                 → first_break with a VERSION_REGRESSION label.
 *      - EMPTY                  → neutral "nothing to verify yet".
 *   3. A failed request surfaces an inline error (not a silent no-op).
 *
 * Mocks `httpClient.get` directly (same pattern as
 * `ReportSignedDownloadButton.test.tsx`) rather than the in-process MSW
 * adapter, since each test needs a DIFFERENT `/audit/integrity` outcome.
 */
import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AuditIntegrityPanel } from './AuditIntegrityPanel';
import { renderWithProviders, signInWithPermissions, signOut } from '@/test/testUtils';
import { httpClient } from '@/shared/api/httpClient';
import { ApiError } from '@/shared/api/apiError';
import { PERMISSIONS } from '@/shared/types/permissions';
import ruRU from '@/shared/i18n/locales/ru-RU.json';

afterEach(() => {
  vi.restoreAllMocks();
  signOut();
});

describe('<AuditIntegrityPanel /> permission gating', () => {
  it('renders nothing for a user without AUDIT_READ', () => {
    signInWithPermissions([PERMISSIONS.PROJECT_READ]);
    render(renderWithProviders(<AuditIntegrityPanel />));
    expect(screen.queryByTestId('audit-verify-integrity-button')).not.toBeInTheDocument();
  });

  it('renders the action for an AUDIT_READ holder', () => {
    signInWithPermissions([PERMISSIONS.AUDIT_READ]);
    render(renderWithProviders(<AuditIntegrityPanel />));
    expect(screen.getByTestId('audit-verify-integrity-button')).toBeInTheDocument();
  });
});

describe('<AuditIntegrityPanel /> status rendering', () => {
  it('renders a full pass for INTACT + bounded=false', async () => {
    const user = userEvent.setup();
    signInWithPermissions([PERMISSIONS.AUDIT_READ]);
    vi.spyOn(httpClient, 'get').mockResolvedValue({
      data: {
        tenant_id: '11111111-1111-1111-1111-111111111111',
        status: 'INTACT',
        intact: true,
        checked_count: 1284,
        chain_length: 1284,
        independently_verified_count: 1200,
        legacy_unverifiable_count: 84,
        verifiable_from: '2026-07-15T00:00:00.000000Z',
        verified_through: '2026-07-15T09:41:12.512874Z',
        bounded: false,
        max_rows: 50000,
        first_break: null,
        verified_at: '2026-07-15T09:42:03.114Z',
      },
    } as Awaited<ReturnType<typeof httpClient.get>>);

    render(renderWithProviders(<AuditIntegrityPanel />));
    await user.click(screen.getByTestId('audit-verify-integrity-button'));

    const intact = await screen.findByTestId('audit-integrity-intact');
    expect(intact).toHaveTextContent('1200');
    expect(intact).toHaveTextContent('1284');
    expect(intact).toHaveTextContent('84');
    // Never the partial/broken/empty renderings at the same time.
    expect(screen.queryByTestId('audit-integrity-partial')).not.toBeInTheDocument();
    expect(screen.queryByTestId('audit-integrity-broken')).not.toBeInTheDocument();
  });

  it('renders INTACT + bounded=true as PARTIAL, never a plain "all intact"', async () => {
    const user = userEvent.setup();
    signInWithPermissions([PERMISSIONS.AUDIT_READ]);
    vi.spyOn(httpClient, 'get').mockResolvedValue({
      data: {
        tenant_id: '11111111-1111-1111-1111-111111111111',
        status: 'INTACT',
        intact: true,
        checked_count: 50000,
        chain_length: 120000,
        independently_verified_count: 49500,
        legacy_unverifiable_count: 500,
        bounded: true,
        max_rows: 50000,
        first_break: null,
        verified_at: '2026-07-15T09:42:03.114Z',
      },
    } as Awaited<ReturnType<typeof httpClient.get>>);

    render(renderWithProviders(<AuditIntegrityPanel />));
    await user.click(screen.getByTestId('audit-verify-integrity-button'));

    const partial = await screen.findByTestId('audit-integrity-partial');
    expect(partial).toHaveTextContent('50000');
    expect(partial).toHaveTextContent('120000');
    // The I1 partial semantics: bounded=true must NEVER render the plain
    // "fully intact" success block, even though status === 'INTACT'.
    expect(screen.queryByTestId('audit-integrity-intact')).not.toBeInTheDocument();
  });

  it('renders BROKEN with the first_break, including the VERSION_REGRESSION label', async () => {
    const user = userEvent.setup();
    signInWithPermissions([PERMISSIONS.AUDIT_READ]);
    vi.spyOn(httpClient, 'get').mockResolvedValue({
      data: {
        tenant_id: '11111111-1111-1111-1111-111111111111',
        status: 'BROKEN',
        intact: false,
        checked_count: 500,
        chain_length: 1284,
        independently_verified_count: 400,
        legacy_unverifiable_count: 99,
        bounded: false,
        max_rows: 50000,
        first_break: {
          row_id: '9f2c1111-2222-3333-4444-555566667777',
          created_at: '2026-07-14T18:03:00.000000Z',
          break_type: 'VERSION_REGRESSION',
          expected_hash: 'a1b2c3d4e5f60708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f',
          actual_hash: 'deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef',
        },
        verified_at: '2026-07-15T09:42:03.114Z',
      },
    } as Awaited<ReturnType<typeof httpClient.get>>);

    render(renderWithProviders(<AuditIntegrityPanel />));
    await user.click(screen.getByTestId('audit-verify-integrity-button'));

    await screen.findByTestId('audit-integrity-broken');
    const breakType = screen.getByTestId('audit-integrity-break-type');
    expect(breakType).toHaveTextContent(ruRU.audit.integrity.break_type.VERSION_REGRESSION);
    // The raw enum code must never leak to the user as the visible label.
    expect(breakType).not.toHaveTextContent('VERSION_REGRESSION');

    const firstBreak = screen.getByTestId('audit-integrity-first-break');
    expect(firstBreak).toHaveTextContent('9f2c1111');
  });

  it('renders a neutral message for EMPTY', async () => {
    const user = userEvent.setup();
    signInWithPermissions([PERMISSIONS.AUDIT_READ]);
    vi.spyOn(httpClient, 'get').mockResolvedValue({
      data: {
        tenant_id: '33333333-3333-3333-3333-333333333333',
        status: 'EMPTY',
        intact: true,
        checked_count: 0,
        chain_length: 0,
        independently_verified_count: 0,
        legacy_unverifiable_count: 0,
        bounded: false,
        max_rows: 50000,
        first_break: null,
        verified_at: '2026-07-15T09:42:03.114Z',
      },
    } as Awaited<ReturnType<typeof httpClient.get>>);

    render(renderWithProviders(<AuditIntegrityPanel />));
    await user.click(screen.getByTestId('audit-verify-integrity-button'));

    const empty = await screen.findByTestId('audit-integrity-empty');
    expect(empty).toHaveTextContent(ruRU.audit.integrity.status.EMPTY);
  });
});

describe('<AuditIntegrityPanel /> failure handling', () => {
  it('surfaces an inline error when the request fails (not a silent no-op)', async () => {
    const user = userEvent.setup();
    signInWithPermissions([PERMISSIONS.AUDIT_READ]);
    vi.spyOn(httpClient, 'get').mockRejectedValue(
      new ApiError(500, { code: 'INTERNAL_ERROR', message: 'boom' }),
    );

    render(renderWithProviders(<AuditIntegrityPanel />));
    await user.click(screen.getByTestId('audit-verify-integrity-button'));

    const error = await screen.findByTestId('audit-integrity-error');
    expect(error).toHaveTextContent(ruRU.audit.integrity.error);
    // No status block is rendered on failure.
    expect(screen.queryByTestId('audit-integrity-intact')).not.toBeInTheDocument();
    expect(screen.queryByTestId('audit-integrity-broken')).not.toBeInTheDocument();
  });

  it('surfaces the forbidden-specific message on a 403', async () => {
    const user = userEvent.setup();
    signInWithPermissions([PERMISSIONS.AUDIT_READ]);
    vi.spyOn(httpClient, 'get').mockRejectedValue(
      new ApiError(403, { code: 'FORBIDDEN', message: 'denied' }),
    );

    render(renderWithProviders(<AuditIntegrityPanel />));
    await user.click(screen.getByTestId('audit-verify-integrity-button'));

    const error = await screen.findByTestId('audit-integrity-error');
    expect(error).toHaveTextContent(ruRU.audit.integrity.error_forbidden);
  });
});
