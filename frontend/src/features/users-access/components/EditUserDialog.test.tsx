import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { EditUserDialog } from './EditUserDialog';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import type { UserDetails } from '../types/userTypes';

const baseUser: Pick<UserDetails, 'email' | 'full_name' | 'locale' | 'status'> = {
  email: 'anna.karimova@example.com',
  full_name: 'Anna Karimova',
  locale: 'ru-RU',
  status: 'ACTIVE',
};

describe('<EditUserDialog />', () => {
  beforeEach(() => {
    signOut();
    signIn('super-admin');
  });

  it('prefills the form with the current user values', () => {
    render(
      renderWithProviders(
        <EditUserDialog open user={baseUser} onClose={() => {}} onSubmit={() => {}} />,
      ),
    );
    expect((screen.getByTestId('edit-fullname') as HTMLInputElement).value).toBe('Anna Karimova');
    expect((screen.getByTestId('edit-email') as HTMLInputElement).value).toBe(
      'anna.karimova@example.com',
    );
    // Password always starts blank (empty = unchanged).
    expect((screen.getByTestId('edit-password') as HTMLInputElement).value).toBe('');
    expect((screen.getByTestId('edit-locale') as HTMLSelectElement).value).toBe('ru-RU');
    expect((screen.getByTestId('edit-status') as HTMLSelectElement).value).toBe('ACTIVE');
  });

  it('only offers ACTIVE and DISABLED in the status select (never INVITED/LOCKED)', () => {
    render(
      renderWithProviders(
        <EditUserDialog open user={baseUser} onClose={() => {}} onSubmit={() => {}} />,
      ),
    );
    const select = screen.getByTestId('edit-status') as HTMLSelectElement;
    const values = Array.from(select.options).map((o) => o.value);
    expect(values).toEqual(['ACTIVE', 'DISABLED']);
    expect(values).not.toContain('INVITED');
    expect(values).not.toContain('LOCKED');
  });

  it('maps an INVITED user to the ACTIVE status option (not user-settable here)', () => {
    render(
      renderWithProviders(
        <EditUserDialog
          open
          user={{ ...baseUser, status: 'INVITED' }}
          onClose={() => {}}
          onSubmit={() => {}}
        />,
      ),
    );
    expect((screen.getByTestId('edit-status') as HTMLSelectElement).value).toBe('ACTIVE');
  });

  it('refuses submission when the name is too short', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <EditUserDialog open user={baseUser} onClose={() => {}} onSubmit={onSubmit} />,
      ),
    );
    await user.clear(screen.getByTestId('edit-fullname'));
    await user.type(screen.getByTestId('edit-fullname'), 'A');
    await user.click(screen.getByRole('button', { name: /Сохранить|Save|Saqlash|Сақлаш/i }));
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('submits the patched profile and never sends tenant_id', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <EditUserDialog open user={baseUser} onClose={() => {}} onSubmit={onSubmit} />,
      ),
    );
    await user.clear(screen.getByTestId('edit-fullname'));
    await user.type(screen.getByTestId('edit-fullname'), 'Anna Karimova-Petrova');
    await user.selectOptions(screen.getByTestId('edit-locale'), 'en-US');
    await user.selectOptions(screen.getByTestId('edit-status'), 'DISABLED');
    await user.click(screen.getByRole('button', { name: /Сохранить|Save|Saqlash|Сақлаш/i }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    const payload = onSubmit.mock.calls[0][0];
    expect(payload).toEqual({
      full_name: 'Anna Karimova-Petrova',
      locale: 'en-US',
      status: 'DISABLED',
    });
    expect('tenant_id' in payload).toBe(false);
    // Email unchanged and password left blank → neither is sent.
    expect('email' in payload).toBe(false);
    expect('password' in payload).toBe(false);
  });

  it('sends a changed email and a new password in the payload', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <EditUserDialog open user={baseUser} onClose={() => {}} onSubmit={onSubmit} />,
      ),
    );
    await user.clear(screen.getByTestId('edit-email'));
    await user.type(screen.getByTestId('edit-email'), 'anna.new@example.com');
    await user.type(screen.getByTestId('edit-password'), 'Str0ng!Passw0rd');
    await user.click(screen.getByRole('button', { name: /Сохранить|Save|Saqlash|Сақлаш/i }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    const payload = onSubmit.mock.calls[0][0];
    expect(payload.email).toBe('anna.new@example.com');
    expect(payload.password).toBe('Str0ng!Passw0rd');
    expect('tenant_id' in payload).toBe(false);
  });

  it('the generate-password button fills a compliant password into the payload', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <EditUserDialog open user={baseUser} onClose={() => {}} onSubmit={onSubmit} />,
      ),
    );
    await user.click(screen.getByTestId('edit-password-generate'));
    const generated = (screen.getByTestId('edit-password') as HTMLInputElement).value;
    expect(generated.length).toBeGreaterThanOrEqual(12);
    await user.click(screen.getByRole('button', { name: /Сохранить|Save|Saqlash|Сақлаш/i }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit.mock.calls[0][0].password).toBe(generated);
  });

  it('omits the password when the field is left blank (password unchanged)', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <EditUserDialog open user={baseUser} onClose={() => {}} onSubmit={onSubmit} />,
      ),
    );
    await user.clear(screen.getByTestId('edit-fullname'));
    await user.type(screen.getByTestId('edit-fullname'), 'Anna K.');
    await user.click(screen.getByRole('button', { name: /Сохранить|Save|Saqlash|Сақлаш/i }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect('password' in onSubmit.mock.calls[0][0]).toBe(false);
  });

  it('refuses submission when a non-empty password is too weak', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <EditUserDialog open user={baseUser} onClose={() => {}} onSubmit={onSubmit} />,
      ),
    );
    await user.type(screen.getByTestId('edit-password'), 'weak');
    await user.click(screen.getByRole('button', { name: /Сохранить|Save|Saqlash|Сақлаш/i }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('maps USER_EMAIL_TAKEN to an inline error on the email field', async () => {
    const { ApiError } = await import('@/shared/api/apiError');
    const onSubmit = vi
      .fn()
      .mockRejectedValue(new ApiError(400, { code: 'USER_EMAIL_TAKEN', message: 'taken' }));
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <EditUserDialog open user={baseUser} onClose={() => {}} onSubmit={onSubmit} />,
      ),
    );
    await user.clear(screen.getByTestId('edit-email'));
    await user.type(screen.getByTestId('edit-email'), 'taken@example.com');
    await user.click(screen.getByRole('button', { name: /Сохранить|Save|Saqlash|Сақлаш/i }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });

  it('maps USER_INVITE_WEAK_PASSWORD from the backend to an inline password error', async () => {
    const { ApiError } = await import('@/shared/api/apiError');
    const onSubmit = vi
      .fn()
      .mockRejectedValue(
        new ApiError(400, { code: 'USER_INVITE_WEAK_PASSWORD', message: 'weak' }),
      );
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <EditUserDialog open user={baseUser} onClose={() => {}} onSubmit={onSubmit} />,
      ),
    );
    // A client-valid password so the request reaches onSubmit and the backend rejects.
    await user.type(screen.getByTestId('edit-password'), 'Str0ng!Passw0rd');
    await user.click(screen.getByRole('button', { name: /Сохранить|Save|Saqlash|Сақлаш/i }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });

  it('maps USER_NO_IDP_ACCOUNT to a friendly inline error', async () => {
    const { ApiError } = await import('@/shared/api/apiError');
    const onSubmit = vi
      .fn()
      .mockRejectedValue(new ApiError(400, { code: 'USER_NO_IDP_ACCOUNT', message: 'no idp' }));
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <EditUserDialog open user={baseUser} onClose={() => {}} onSubmit={onSubmit} />,
      ),
    );
    await user.type(screen.getByTestId('edit-password'), 'Str0ng!Passw0rd');
    await user.click(screen.getByRole('button', { name: /Сохранить|Save|Saqlash|Сақлаш/i }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });

  it('maps USER_PATCH_BAD_STATUS to a friendly inline error', async () => {
    const { ApiError } = await import('@/shared/api/apiError');
    const onSubmit = vi
      .fn()
      .mockRejectedValue(new ApiError(400, { code: 'USER_PATCH_BAD_STATUS', message: 'bad' }));
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <EditUserDialog open user={baseUser} onClose={() => {}} onSubmit={onSubmit} />,
      ),
    );
    await user.click(screen.getByRole('button', { name: /Сохранить|Save|Saqlash|Сақлаш/i }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });

  it('maps USER_IDP_NOT_INITIALIZED to a helpful inline password error and keeps the drawer open', async () => {
    const { ApiError } = await import('@/shared/api/apiError');
    const onClose = vi.fn();
    const onSubmit = vi
      .fn()
      .mockRejectedValue(
        new ApiError(400, { code: 'USER_IDP_NOT_INITIALIZED', message: 'not initialized' }),
      );
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <EditUserDialog open user={baseUser} onClose={onClose} onSubmit={onSubmit} />,
      ),
    );
    await user.type(screen.getByTestId('edit-password'), 'Str0ng!Passw0rd');
    await user.click(screen.getByRole('button', { name: /Сохранить|Save|Saqlash|Сақлаш/i }));
    const alert = await screen.findByRole('alert');
    expect(alert).toBeInTheDocument();
    // Helpful, localized copy (ru-RU is the test-default locale, see test/setup.ts).
    expect(alert.textContent).toMatch(/ещё не активирована/i);
    // Drawer stays open so the admin can fix/clear the field and retry.
    expect(onClose).not.toHaveBeenCalled();
  });

  it('maps USER_IDP_PASSWORD_REJECTED to an inline password error', async () => {
    const { ApiError } = await import('@/shared/api/apiError');
    const onSubmit = vi
      .fn()
      .mockRejectedValue(
        new ApiError(400, { code: 'USER_IDP_PASSWORD_REJECTED', message: 'rejected' }),
      );
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <EditUserDialog open user={baseUser} onClose={() => {}} onSubmit={onSubmit} />,
      ),
    );
    await user.type(screen.getByTestId('edit-password'), 'Str0ng!Passw0rd');
    await user.click(screen.getByRole('button', { name: /Сохранить|Save|Saqlash|Сақлаш/i }));
    const alert = await screen.findByRole('alert');
    expect(alert.textContent).toMatch(/отклонил этот пароль/i);
  });

  it('maps USER_IDP_EMAIL_REJECTED to an inline email error', async () => {
    const { ApiError } = await import('@/shared/api/apiError');
    const onSubmit = vi
      .fn()
      .mockRejectedValue(
        new ApiError(400, { code: 'USER_IDP_EMAIL_REJECTED', message: 'rejected' }),
      );
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <EditUserDialog open user={baseUser} onClose={() => {}} onSubmit={onSubmit} />,
      ),
    );
    await user.clear(screen.getByTestId('edit-email'));
    await user.type(screen.getByTestId('edit-email'), 'rejected@idp-reject.test');
    await user.click(screen.getByRole('button', { name: /Сохранить|Save|Saqlash|Сақлаш/i }));
    const alert = await screen.findByRole('alert');
    expect(alert.textContent).toMatch(/отклонил этот email/i);
  });

  it('lets the admin clear a rejected password and save profile-only changes', async () => {
    const { ApiError } = await import('@/shared/api/apiError');
    // First attempt (with a password) is rejected by the IdP; second attempt
    // (password cleared) succeeds and sends no password.
    const onClose = vi.fn();
    const onSubmit = vi
      .fn()
      .mockRejectedValueOnce(
        new ApiError(400, { code: 'USER_IDP_NOT_INITIALIZED', message: 'not initialized' }),
      )
      .mockResolvedValueOnce(undefined);
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <EditUserDialog open user={baseUser} onClose={onClose} onSubmit={onSubmit} />,
      ),
    );
    await user.clear(screen.getByTestId('edit-fullname'));
    await user.type(screen.getByTestId('edit-fullname'), 'Anna Karimova-Petrova');
    await user.type(screen.getByTestId('edit-password'), 'Str0ng!Passw0rd');
    await user.click(screen.getByRole('button', { name: /Сохранить|Save|Saqlash|Сақлаш/i }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();

    // Clear the offending password and retry — profile-only save must succeed.
    await user.clear(screen.getByTestId('edit-password'));
    await user.click(screen.getByRole('button', { name: /Сохранить|Save|Saqlash|Сақлаш/i }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(2));
    const secondPayload = onSubmit.mock.calls[1][0];
    expect('password' in secondPayload).toBe(false);
    expect(secondPayload.full_name).toBe('Anna Karimova-Petrova');
    await waitFor(() => expect(onClose).toHaveBeenCalled());
  });
});
