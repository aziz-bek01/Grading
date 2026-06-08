import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AddMembershipDialog } from './AddMembershipDialog';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';

const TENANT_ACME = '11111111-1111-1111-1111-111111111111';
const TENANT_BETA = '22222222-2222-2222-2222-222222222222';

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

  it('refuses submission when no role is selected', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <AddMembershipDialog open existingTenantIds={[]} onClose={() => {}} onSubmit={onSubmit} />,
      ),
    );
    await user.click(
      screen.getByRole('button', {
        name: /Добавить членство|Add membership|A'zolik qo'shish|Аъзолик қўшиш/i,
      }),
    );
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
    await user.click(screen.getByTestId('add-membership-role-CLIENT_COMPANY_ADMIN'));
    await user.click(
      screen.getByRole('button', {
        name: /Добавить членство|Add membership|A'zolik qo'shish|Аъзолик қўшиш/i,
      }),
    );
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
    await user.click(screen.getByTestId('add-membership-role-VIEWER'));
    await user.click(
      screen.getByRole('button', {
        name: /Добавить членство|Add membership|A'zolik qo'shish|Аъзолик қўшиш/i,
      }),
    );
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});
