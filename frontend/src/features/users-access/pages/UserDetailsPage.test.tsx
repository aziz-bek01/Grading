/**
 * /app/users/:userId — user-level management actions.
 *
 * Focus: the NEW user-level actions (Edit, Disable/Reactivate, Add to company)
 * render against the mock backend, are permission-gated, and the disable flow
 * confirms before flipping status. Membership-level actions are covered by
 * MembershipCard / SalaryPermissionToggle / AssignRoleDialog tests.
 */
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Routes, Route } from 'react-router-dom';
import type { AxiosAdapter } from 'axios';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { httpClient } from '@/shared/api/httpClient';
import { createMockAdapter } from '@/shared/api/mocks/handlers';
import { useAuthStore } from '@/features/auth/authStore';
import { PERMISSIONS, type PermissionCode } from '@/shared/types/permissions';
import { UserDetailsPage } from './UserDetailsPage';

const ORIGINAL_ADAPTER = httpClient.defaults.adapter as AxiosAdapter | undefined;

beforeEach(() => {
  httpClient.defaults.adapter = createMockAdapter(ORIGINAL_ADAPTER);
});

afterEach(() => {
  httpClient.defaults.adapter = ORIGINAL_ADAPTER;
  signOut();
});

function withRoutes(userId: string) {
  return renderWithProviders(
    <Routes>
      <Route path="/app/users/:userId" element={<UserDetailsPage />} />
    </Routes>,
    [`/app/users/${userId}`],
  );
}

/** Strip specific permissions from the signed-in dev user. */
function dropPermissions(...codes: PermissionCode[]) {
  const state = useAuthStore.getState();
  useAuthStore.setState({
    ...state,
    user: {
      ...state.user!,
      // Strip HRLAB_SUPER_ADMIN too: that role short-circuits every permission
      // gate by design, so a super admin can never have these actions hidden.
      // To assert permission-LEVEL gating, simulate a regular user.
      roles: state.user!.roles.filter((r) => r !== 'HRLAB_SUPER_ADMIN'),
      permissions: state.user!.permissions.filter((p) => !codes.includes(p)),
    },
  });
}

describe('<UserDetailsPage /> user-level actions', () => {
  it('shows Edit, Disable and Add-to-company for a super-admin on an ACTIVE user', async () => {
    signIn('super-admin');
    render(withRoutes('u-acme-anna'));
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Anna Karimova' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('edit-user-button')).toBeInTheDocument();
    expect(screen.getByTestId('disable-user-button')).toBeInTheDocument();
    expect(screen.getByTestId('add-membership-button')).toBeInTheDocument();
    // ACTIVE user → no Reactivate button.
    expect(screen.queryByTestId('reactivate-user-button')).not.toBeInTheDocument();
  });

  it('shows Reactivate (not Disable) for a DISABLED user', async () => {
    signIn('super-admin');
    render(withRoutes('u-acme-yulia')); // fixture status = DISABLED
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Yulia Petrova' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('reactivate-user-button')).toBeInTheDocument();
    expect(screen.queryByTestId('disable-user-button')).not.toBeInTheDocument();
  });

  it('hides Edit / Disable when the user lacks USER_UPDATE and USER_ACCESS_MANAGE', async () => {
    signIn('super-admin');
    dropPermissions(PERMISSIONS.USER_UPDATE, PERMISSIONS.USER_ACCESS_MANAGE);
    render(withRoutes('u-acme-anna'));
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Anna Karimova' }),
    ).toBeInTheDocument();
    expect(screen.queryByTestId('edit-user-button')).not.toBeInTheDocument();
    expect(screen.queryByTestId('disable-user-button')).not.toBeInTheDocument();
  });

  it('hides Add-to-company when the user lacks USER_MEMBERSHIP_MANAGE and USER_ACCESS_MANAGE', async () => {
    signIn('super-admin');
    dropPermissions(PERMISSIONS.USER_MEMBERSHIP_MANAGE, PERMISSIONS.USER_ACCESS_MANAGE);
    render(withRoutes('u-acme-anna'));
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Anna Karimova' }),
    ).toBeInTheDocument();
    expect(screen.queryByTestId('add-membership-button')).not.toBeInTheDocument();
  });

  it('shows the Reset-login action for an editor and hides it without edit permission', async () => {
    signIn('super-admin');
    render(withRoutes('u-acme-dilshod')); // fixture status = INVITED
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Dilshod Rashidov' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('reset-login-button')).toBeInTheDocument();
  });

  it('hides Reset-login when the user lacks USER_UPDATE and USER_ACCESS_MANAGE', async () => {
    signIn('super-admin');
    dropPermissions(PERMISSIONS.USER_UPDATE, PERMISSIONS.USER_ACCESS_MANAGE);
    render(withRoutes('u-acme-dilshod'));
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Dilshod Rashidov' }),
    ).toBeInTheDocument();
    expect(screen.queryByTestId('reset-login-button')).not.toBeInTheDocument();
  });

  it('reset-login flow posts the password and shows the success banner', async () => {
    signIn('super-admin');
    const user = userEvent.setup();
    render(withRoutes('u-acme-dilshod')); // INVITED
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Dilshod Rashidov' }),
    ).toBeInTheDocument();

    await user.click(screen.getByTestId('reset-login-button'));
    // Scope to the drawer so we don't match the page's own (identically
    // labelled) Reset-login trigger button.
    const dialog = await screen.findByRole('dialog');
    await user.type(screen.getByTestId('reset-login-password-input'), 'Str0ng!Passw0rd');
    await user.click(
      within(dialog).getByRole('button', {
        name: /Сбросить вход|Reset login|Loginni tiklash|Логинни тиклаш/i,
      }),
    );

    // Success banner appears after the POST resolves (status flips to ACTIVE).
    await waitFor(() => {
      expect(screen.getByTestId('reset-login-success')).toBeInTheDocument();
    });
  });

  it('disable flow confirms then flips the user status to DISABLED', async () => {
    signIn('super-admin');
    const user = userEvent.setup();
    render(withRoutes('u-acme-anna'));
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Anna Karimova' }),
    ).toBeInTheDocument();

    await user.click(screen.getByTestId('disable-user-button'));
    // A confirm dialog appears; scope to it so we don't match the page's own
    // (identically-labelled) disable button.
    const dialog = await screen.findByRole('dialog');
    const confirm = within(dialog).getByRole('button', {
      name: /Отключить пользователя|Disable user|O'chirish|Ўчириш/i,
    });
    await user.click(confirm);

    // After the PATCH succeeds the header re-renders with a Reactivate action.
    await waitFor(() => {
      expect(screen.getByTestId('reactivate-user-button')).toBeInTheDocument();
    });
  });
});
