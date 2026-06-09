import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { AxiosAdapter } from 'axios';
import { AddMembershipDialog } from './AddMembershipDialog';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { httpClient } from '@/shared/api/httpClient';
import { createMockAdapter } from '@/shared/api/mocks/handlers';

const TENANT_ACME = '11111111-1111-1111-1111-111111111111';
const TENANT_BETA = '22222222-2222-2222-2222-222222222222';

const ORIGINAL_ADAPTER = httpClient.defaults.adapter as AxiosAdapter | undefined;

// The role picker is DATA-DRIVEN (GET /roles) — install the mock adapter so the
// catalog resolves and role checkboxes render.
beforeEach(() => {
  httpClient.defaults.adapter = createMockAdapter(ORIGINAL_ADAPTER);
});

afterEach(() => {
  httpClient.defaults.adapter = ORIGINAL_ADAPTER;
});

const SUBMIT = /Добавить членство|Add membership|A'zolik qo'shish|Аъзолик қўшиш/i;

describe('<AddMembershipDialog />', () => {
  beforeEach(() => {
    signOut();
    signIn('super-admin'); // has two tenants in the dev catalog
  });

  it('excludes tenants the user already belongs to from the picker', () => {
    render(
      renderWithProviders(
        <AddMembershipDialog
          open
          existingTenantIds={[TENANT_ACME]}
          onClose={() => {}}
          onSubmit={() => {}}
        />,
      ),
    );
    const select = screen.getByTestId('add-membership-tenant') as HTMLSelectElement;
    const values = Array.from(select.options).map((o) => o.value);
    expect(values).toContain(TENANT_BETA);
    expect(values).not.toContain(TENANT_ACME);
  });

  it('renders the role picker from the API catalog, not a hardcoded list', async () => {
    render(
      renderWithProviders(
        <AddMembershipDialog open existingTenantIds={[]} onClose={() => {}} onSubmit={() => {}} />,
      ),
    );
    expect(await screen.findByTestId('add-membership-role-CLIENT_COMPANY_ADMIN')).toBeInTheDocument();
    expect(screen.getByTestId('add-membership-role-VIEWER')).toBeInTheDocument();
  });

  it('refuses submission when no role is selected', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <AddMembershipDialog open existingTenantIds={[]} onClose={() => {}} onSubmit={onSubmit} />,
      ),
    );
    // Wait for the picker so the form is fully rendered before submitting.
    await screen.findByTestId('add-membership-role-VIEWER');
    await user.click(screen.getByRole('button', { name: SUBMIT }));
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('submits tenant_id + role_codes for the chosen company', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <AddMembershipDialog
          open
          existingTenantIds={[TENANT_ACME]}
          onClose={() => {}}
          onSubmit={onSubmit}
        />,
      ),
    );
    await user.click(await screen.findByTestId('add-membership-role-CLIENT_COMPANY_ADMIN'));
    await user.click(screen.getByRole('button', { name: SUBMIT }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    const payload = onSubmit.mock.calls[0][0];
    expect(payload.tenant_id).toBe(TENANT_BETA);
    expect(payload.role_codes).toEqual(['CLIENT_COMPANY_ADMIN']);
  });

  it('maps MEMBERSHIP_EXISTS to a friendly inline error', async () => {
    const { ApiError } = await import('@/shared/api/apiError');
    const onSubmit = vi
      .fn()
      .mockRejectedValue(new ApiError(409, { code: 'MEMBERSHIP_EXISTS', message: 'exists' }));
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <AddMembershipDialog open existingTenantIds={[]} onClose={() => {}} onSubmit={onSubmit} />,
      ),
    );
    await user.click(await screen.findByTestId('add-membership-role-VIEWER'));
    await user.click(screen.getByRole('button', { name: SUBMIT }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});
