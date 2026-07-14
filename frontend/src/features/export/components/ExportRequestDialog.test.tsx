import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { renderWithProviders, signIn, signInWithPermissions, signOut } from '@/test/testUtils';
import { httpClient } from '@/shared/api/httpClient';
import { ApiError } from '@/shared/api/apiError';
import { PERMISSIONS } from '@/shared/types/permissions';
import { ExportRequestDialog } from './ExportRequestDialog';

const VALID_PROJECT_UUID = '11111111-1111-4111-8111-111111111111';

describe('ExportRequestDialog', () => {
  afterEach(() => {
    signOut();
    vi.restoreAllMocks();
  });

  it('renders nothing when closed', () => {
    const { container } = render(
      renderWithProviders(
        <ExportRequestDialog
          open={false}
          projectId={VALID_PROJECT_UUID}
          onClose={() => {}}
        />,
      ),
    );
    expect(container.querySelector('[data-testid="export-request-dialog"]')).toBeNull();
  });

  it('shows the salary warning banner only for salary-bearing types', () => {
    render(
      renderWithProviders(
        <ExportRequestDialog
          open
          projectId={VALID_PROJECT_UUID}
          onClose={() => {}}
        />,
      ),
    );
    // Default type is POSITION_CATALOG (non-salary).
    expect(screen.queryByTestId('export-salary-warning')).toBeNull();
    fireEvent.change(screen.getByTestId('export-request-type'), {
      target: { value: 'SALARY_RANGES' },
    });
    expect(screen.getByTestId('export-salary-warning')).toBeInTheDocument();
  });

  // -----------------------------------------------------------------------
  // Salary-bearing type gating (SALARY_EXPORT)
  // -----------------------------------------------------------------------

  it('disables salary-bearing export types for a caller without SALARY_EXPORT', () => {
    // The dev super-admin fixture deliberately excludes SALARY_EXPORT.
    signIn('super-admin');
    render(
      renderWithProviders(
        <ExportRequestDialog open projectId={VALID_PROJECT_UUID} onClose={() => {}} />,
      ),
    );
    const select = screen.getByTestId('export-request-type') as HTMLSelectElement;
    const opts = Object.fromEntries(
      Array.from(select.options).map((o) => [o.value, o.disabled]),
    );
    expect(opts.SALARY_RANGES).toBe(true);
    expect(opts.RED_GREEN_CIRCLE).toBe(true);
    expect(opts.COMPENSATION_SCENARIOS).toBe(true);
    // Non-salary types stay enabled.
    expect(opts.POSITION_CATALOG).toBe(false);
    expect(opts.REPORT_EXECUTIVE).toBe(false);
  });

  it('enables salary-bearing export types for a caller who holds SALARY_EXPORT', () => {
    signInWithPermissions([PERMISSIONS.PROJECT_READ, PERMISSIONS.SALARY_EXPORT]);
    render(
      renderWithProviders(
        <ExportRequestDialog open projectId={VALID_PROJECT_UUID} onClose={() => {}} />,
      ),
    );
    const select = screen.getByTestId('export-request-type') as HTMLSelectElement;
    const opts = Object.fromEntries(
      Array.from(select.options).map((o) => [o.value, o.disabled]),
    );
    expect(opts.SALARY_RANGES).toBe(false);
    expect(opts.RED_GREEN_CIRCLE).toBe(false);
    expect(opts.COMPENSATION_SCENARIOS).toBe(false);
  });

  // -----------------------------------------------------------------------
  // Error surfacing — a failed request must be VISIBLE, not swallowed
  // -----------------------------------------------------------------------

  it('surfaces a failed request inline and keeps the dialog open (mapped 403)', async () => {
    signIn('super-admin');
    const postSpy = vi
      .spyOn(httpClient, 'post')
      .mockRejectedValue(new ApiError(403, { code: 'FORBIDDEN' }));
    const onClose = vi.fn();
    render(
      renderWithProviders(
        <ExportRequestDialog open projectId={VALID_PROJECT_UUID} onClose={onClose} />,
      ),
    );

    fireEvent.click(screen.getByTestId('export-request-submit'));

    await waitFor(() => expect(postSpy).toHaveBeenCalled());
    const banner = await screen.findByTestId('export-request-error');
    expect(banner.textContent ?? '').not.toHaveLength(0);
    // The dialog stays open so the user can correct and retry.
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getByTestId('export-request-dialog')).toBeInTheDocument();
  });

  it('shows the backend error code + reference for an unmapped failure (500)', async () => {
    signIn('super-admin');
    vi.spyOn(httpClient, 'post').mockRejectedValue(
      new ApiError(500, { code: 'INTERNAL_ERROR', correlation_id: 'corr-xyz' }),
    );
    render(
      renderWithProviders(
        <ExportRequestDialog open projectId={VALID_PROJECT_UUID} onClose={() => {}} />,
      ),
    );

    fireEvent.click(screen.getByTestId('export-request-submit'));

    const banner = await screen.findByTestId('export-request-error');
    // The generic fallback carries the code + correlation id so the failure is reportable.
    expect(banner.textContent).toContain('INTERNAL_ERROR');
    expect(banner.textContent).toContain('corr-xyz');
  });

  it('surfaces a validation-blocked submit (no network call) instead of a silent no-op', async () => {
    signIn('super-admin');
    const postSpy = vi.spyOn(httpClient, 'post');
    // An invalid projectId fails the Zod `projectId.uuid()` rule, which has NO
    // inline field message in the dialog — previously the click did nothing.
    render(
      renderWithProviders(
        <ExportRequestDialog open projectId="not-a-uuid" onClose={() => {}} />,
      ),
    );

    fireEvent.click(screen.getByTestId('export-request-submit'));

    const banner = await screen.findByTestId('export-request-error');
    expect(banner.textContent ?? '').not.toHaveLength(0);
    // Validation blocked it BEFORE any network call.
    expect(postSpy).not.toHaveBeenCalled();
  });
});
