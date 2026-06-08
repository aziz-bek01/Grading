import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { EditUserDialog } from './EditUserDialog';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import type { UserDetails } from '../types/userTypes';

const baseUser: Pick<UserDetails, 'full_name' | 'locale' | 'status'> = {
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
});
