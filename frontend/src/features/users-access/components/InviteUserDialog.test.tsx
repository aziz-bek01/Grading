import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { AxiosAdapter } from 'axios';
import { InviteUserDialog } from './InviteUserDialog';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { httpClient } from '@/shared/api/httpClient';
import { createMockAdapter } from '@/shared/api/mocks/handlers';

const ORIGINAL_ADAPTER = httpClient.defaults.adapter as AxiosAdapter | undefined;

// The role picker is now DATA-DRIVEN (GET /roles). Install the mock adapter so
// the catalog resolves; without it the picker would stay in its loading/error
// state and no role options would render.
beforeEach(() => {
  httpClient.defaults.adapter = createMockAdapter(ORIGINAL_ADAPTER);
});

afterEach(() => {
  httpClient.defaults.adapter = ORIGINAL_ADAPTER;
});

const SUBMIT = /Отправить приглашение|Send invitation|Таклифни юбориш|Taklifni yuborish/i;

describe('<InviteUserDialog />', () => {
  beforeEach(() => {
    signOut();
    signIn('super-admin');
  });

  it('renders the role picker from the API catalog, not a hardcoded list', async () => {
    render(renderWithProviders(<InviteUserDialog open onClose={() => {}} onSubmit={() => {}} />));
    // These roles only appear because the GET /roles catalog returned them.
    expect(await screen.findByTestId('invite-role-CLIENT_HR_DIRECTOR')).toBeInTheDocument();
    expect(screen.getByTestId('invite-role-VIEWER')).toBeInTheDocument();
    // Super-admin can grant every role, including platform/HRLab ones.
    expect(screen.getByTestId('invite-role-HRLAB_CONSULTANT')).not.toBeDisabled();
  });

  it('refuses submission when email is missing', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(renderWithProviders(<InviteUserDialog open onClose={() => {}} onSubmit={onSubmit} />));
    // Fill name + role only — email missing → Zod must block.
    await user.type(screen.getByTestId('invite-fullname'), 'Test User');
    await user.click(await screen.findByTestId('invite-role-VIEWER'));
    await user.click(screen.getByRole('button', { name: SUBMIT }));
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('refuses submission when no role is selected', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(renderWithProviders(<InviteUserDialog open onClose={() => {}} onSubmit={onSubmit} />));
    await user.type(screen.getByTestId('invite-email'), 'new@example.com');
    await user.type(screen.getByTestId('invite-fullname'), 'Test User');
    await user.click(screen.getByRole('button', { name: SUBMIT }));
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('calls onSubmit with the selected role code (super-admin sees tenant selector)', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(renderWithProviders(<InviteUserDialog open onClose={() => {}} onSubmit={onSubmit} />));
    // Super-admin must see the tenant selector.
    expect(screen.getByTestId('invite-tenant')).toBeInTheDocument();
    await user.type(screen.getByTestId('invite-email'), 'new@example.com');
    await user.type(screen.getByTestId('invite-fullname'), 'Anna Test');
    await user.type(screen.getByTestId('invite-password'), 'Str0ng!Pass1');
    await user.click(await screen.findByTestId('invite-role-CLIENT_HR_DIRECTOR'));
    await user.click(screen.getByRole('button', { name: SUBMIT }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    const payload = onSubmit.mock.calls[0][0];
    expect(payload.email).toBe('new@example.com');
    expect(payload.full_name).toBe('Anna Test');
    expect(payload.password).toBe('Str0ng!Pass1');
    expect(payload.role_codes).toContain('CLIENT_HR_DIRECTOR');
    // Super-admin payload may include tenant_id (selected tenant).
    expect(typeof payload.tenant_id === 'string').toBe(true);
  });

  it('refuses submission and shows an inline error when the password is weak', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(renderWithProviders(<InviteUserDialog open onClose={() => {}} onSubmit={onSubmit} />));
    await user.type(screen.getByTestId('invite-email'), 'weak@example.com');
    await user.type(screen.getByTestId('invite-fullname'), 'Weak Pass');
    // Missing uppercase / symbol → fails client-side complexity rule.
    await user.type(screen.getByTestId('invite-password'), 'abc12345');
    await user.click(await screen.findByTestId('invite-role-CLIENT_HR_DIRECTOR'));
    await user.click(screen.getByRole('button', { name: SUBMIT }));
    // Zod blocks submit; an inline alert surfaces and onSubmit is never called.
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('generate button fills a strong, compliant password and reveals it', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(renderWithProviders(<InviteUserDialog open onClose={() => {}} onSubmit={onSubmit} />));
    await user.click(screen.getByTestId('invite-password-generate'));
    const input = screen.getByTestId('invite-password') as HTMLInputElement;
    // Revealed (type=text) so the admin can copy it.
    expect(input.type).toBe('text');
    expect(input.value).toMatch(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{12,}$/);
  });
});

describe('<InviteUserDialog /> as client admin (non-super)', () => {
  beforeEach(() => {
    signOut();
    // Consultant has neither USER_INVITE nor HRLAB_SUPER_ADMIN role; the
    // dialog itself does not gate visibility (the toolbar button does).
    // We use consultant here only to confirm the tenant selector is hidden
    // AND that platform roles render disabled with a reason.
    signIn('consultant');
  });

  it('omits tenant selector for non-super-admin and never sends tenant_id in payload', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(renderWithProviders(<InviteUserDialog open onClose={() => {}} onSubmit={onSubmit} />));
    expect(screen.queryByTestId('invite-tenant')).not.toBeInTheDocument();
    await user.type(screen.getByTestId('invite-email'), 'newuser@example.com');
    await user.type(screen.getByTestId('invite-fullname'), 'Some User');
    await user.type(screen.getByTestId('invite-password'), 'Str0ng!Pass1');
    await user.click(await screen.findByTestId('invite-role-VIEWER'));
    await user.click(screen.getByRole('button', { name: SUBMIT }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    const payload = onSubmit.mock.calls[0][0];
    // FE-TI-004: client admins must never send tenant_id — backend derives from JWT.
    expect(payload.tenant_id).toBeUndefined();
  });

  it('renders platform/HRLab roles as disabled with a reason for a non-super caller', async () => {
    render(renderWithProviders(<InviteUserDialog open onClose={() => {}} onSubmit={() => {}} />));
    // HRLAB_SUPER_ADMIN → disabled with the HRLab-only reason hint.
    const superAdmin = await screen.findByTestId('invite-role-HRLAB_SUPER_ADMIN');
    expect(superAdmin).toBeDisabled();
    expect(screen.getByTestId('invite-role-HRLAB_SUPER_ADMIN-reason')).toHaveTextContent(
      /Только HRLab Super Admin/i,
    );
    // Tenant-scope role stays selectable.
    expect(screen.getByTestId('invite-role-VIEWER')).not.toBeDisabled();
  });
});
