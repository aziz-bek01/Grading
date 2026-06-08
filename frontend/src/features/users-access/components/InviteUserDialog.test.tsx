import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { InviteUserDialog } from './InviteUserDialog';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';

describe('<InviteUserDialog />', () => {
  beforeEach(() => {
    signOut();
    signIn('super-admin');
  });

  it('refuses submission when email is missing', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(renderWithProviders(<InviteUserDialog open onClose={() => {}} onSubmit={onSubmit} />));
    // Fill name + role only — email missing → Zod must block.
    await user.type(screen.getByTestId('invite-fullname'), 'Test User');
    await user.click(screen.getByTestId('invite-role-VIEWER'));
    await user.click(screen.getByRole('button', { name: /Отправить приглашение|Send invitation|Таклифни юбориш|Taklifni yuborish/i }));
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('refuses submission when no role is selected', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(renderWithProviders(<InviteUserDialog open onClose={() => {}} onSubmit={onSubmit} />));
    await user.type(screen.getByTestId('invite-email'), 'new@example.com');
    await user.type(screen.getByTestId('invite-fullname'), 'Test User');
    await user.click(screen.getByRole('button', { name: /Отправить приглашение|Send invitation|Таклифни юбориш|Taklifni yuborish/i }));
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('calls onSubmit with valid values (super-admin sees tenant selector)', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(renderWithProviders(<InviteUserDialog open onClose={() => {}} onSubmit={onSubmit} />));
    // Super-admin must see the tenant selector.
    expect(screen.getByTestId('invite-tenant')).toBeInTheDocument();
    await user.type(screen.getByTestId('invite-email'), 'new@example.com');
    await user.type(screen.getByTestId('invite-fullname'), 'Anna Test');
    await user.click(screen.getByTestId('invite-role-CLIENT_HR_DIRECTOR'));
    await user.click(screen.getByRole('button', { name: /Отправить приглашение|Send invitation|Таклифни юбориш|Taklifni yuborish/i }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    const payload = onSubmit.mock.calls[0][0];
    expect(payload.email).toBe('new@example.com');
    expect(payload.full_name).toBe('Anna Test');
    expect(payload.role_codes).toContain('CLIENT_HR_DIRECTOR');
    // Super-admin payload may include tenant_id (selected tenant).
    expect(typeof payload.tenant_id === 'string').toBe(true);
  });
});

describe('<InviteUserDialog /> as client admin (non-super)', () => {
  beforeEach(() => {
    signOut();
    // Consultant has neither USER_INVITE nor HRLAB_SUPER_ADMIN role; the
    // dialog itself does not gate visibility (the toolbar button does).
    // We use consultant here only to confirm the tenant selector is hidden.
    signIn('consultant');
  });

  it('omits tenant selector for non-super-admin and never sends tenant_id in payload', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(renderWithProviders(<InviteUserDialog open onClose={() => {}} onSubmit={onSubmit} />));
    expect(screen.queryByTestId('invite-tenant')).not.toBeInTheDocument();
    await user.type(screen.getByTestId('invite-email'), 'newuser@example.com');
    await user.type(screen.getByTestId('invite-fullname'), 'Some User');
    await user.click(screen.getByTestId('invite-role-VIEWER'));
    await user.click(screen.getByRole('button', { name: /Отправить приглашение|Send invitation|Таклифни юбориш|Taklifni yuborish/i }));
    await new Promise((r) => setTimeout(r, 0));
    expect(onSubmit).toHaveBeenCalledTimes(1);
    const payload = onSubmit.mock.calls[0][0];
    // FE-TI-004: client admins must never send tenant_id — backend derives from JWT.
    expect(payload.tenant_id).toBeUndefined();
  });
});
