import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ResetLoginDialog } from './ResetLoginDialog';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';

// Submit button label across the 4 locales (ru-RU is the test-default).
const SUBMIT = /Сбросить вход|Reset login|Loginni tiklash|Логинни тиклаш/i;

describe('<ResetLoginDialog />', () => {
  beforeEach(() => {
    signOut();
    signIn('super-admin');
  });

  it('starts with a blank, hidden password field', () => {
    render(
      renderWithProviders(
        <ResetLoginDialog open onClose={() => {}} onSubmit={() => {}} />,
      ),
    );
    const input = screen.getByTestId('reset-login-password-input') as HTMLInputElement;
    expect(input.value).toBe('');
    expect(input.type).toBe('password');
  });

  it('happy path: posts the password (only) and closes', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <ResetLoginDialog open onClose={onClose} onSubmit={onSubmit} />,
      ),
    );
    await user.type(screen.getByTestId('reset-login-password-input'), 'Str0ng!Passw0rd');
    await user.click(screen.getByRole('button', { name: SUBMIT }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    const payload = onSubmit.mock.calls[0][0];
    expect(payload).toEqual({ password: 'Str0ng!Passw0rd' });
    // Tenant scope rule: reset-login carries no tenant_id.
    expect('tenant_id' in payload).toBe(false);
    await waitFor(() => expect(onClose).toHaveBeenCalled());
  });

  it('generate-password fills a compliant password into the payload', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <ResetLoginDialog open onClose={() => {}} onSubmit={onSubmit} />,
      ),
    );
    await user.click(screen.getByTestId('reset-login-password-generate'));
    const generated = (screen.getByTestId('reset-login-password-input') as HTMLInputElement).value;
    expect(generated.length).toBeGreaterThanOrEqual(12);
    await user.click(screen.getByRole('button', { name: SUBMIT }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit.mock.calls[0][0].password).toBe(generated);
  });

  it('refuses submission when the password is too weak (client-side)', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <ResetLoginDialog open onClose={() => {}} onSubmit={onSubmit} />,
      ),
    );
    await user.type(screen.getByTestId('reset-login-password-input'), 'weak');
    await user.click(screen.getByRole('button', { name: SUBMIT }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('maps USER_INVITE_WEAK_PASSWORD from the backend to an inline password error', async () => {
    const { ApiError } = await import('@/shared/api/apiError');
    const onSubmit = vi
      .fn()
      .mockRejectedValue(new ApiError(400, { code: 'USER_INVITE_WEAK_PASSWORD', message: 'weak' }));
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <ResetLoginDialog open onClose={onClose} onSubmit={onSubmit} />,
      ),
    );
    // Client-valid so the request reaches onSubmit and the backend rejects.
    await user.type(screen.getByTestId('reset-login-password-input'), 'Str0ng!Passw0rd');
    await user.click(screen.getByRole('button', { name: SUBMIT }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    // Drawer stays open so the admin can correct and retry.
    expect(onClose).not.toHaveBeenCalled();
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
        <ResetLoginDialog open onClose={onClose} onSubmit={onSubmit} />,
      ),
    );
    await user.type(screen.getByTestId('reset-login-password-input'), 'Str0ng!Passw0rd');
    await user.click(screen.getByRole('button', { name: SUBMIT }));
    const alert = await screen.findByRole('alert');
    // Helpful, localized copy (ru-RU is the test-default locale, see test/setup.ts).
    expect(alert.textContent).toMatch(/тиклаш уни фаоллаштиради|активирует/i);
    expect(onClose).not.toHaveBeenCalled();
  });

  it('maps a 502 IDP_PROVISIONING_FAILED to a general try-again banner', async () => {
    const { ApiError } = await import('@/shared/api/apiError');
    const onClose = vi.fn();
    const onSubmit = vi
      .fn()
      .mockRejectedValue(
        new ApiError(502, { code: 'IDP_PROVISIONING_FAILED', message: 'idp down' }),
      );
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <ResetLoginDialog open onClose={onClose} onSubmit={onSubmit} />,
      ),
    );
    await user.type(screen.getByTestId('reset-login-password-input'), 'Str0ng!Passw0rd');
    await user.click(screen.getByRole('button', { name: SUBMIT }));
    expect(await screen.findByTestId('reset-login-error')).toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();
  });
});
