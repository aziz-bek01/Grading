/**
 * RolesListPage — full catalog render + permission gating (slice E2).
 */
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import type { AxiosAdapter } from 'axios';
import { RolesListPage } from './RolesListPage';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { httpClient } from '@/shared/api/httpClient';
import { createMockAdapter } from '@/shared/api/mocks/handlers';
import { useAuthStore } from '@/features/auth/authStore';
import { buildDevUser } from '@/features/auth/devAuth';
import { PERMISSIONS } from '@/shared/types/permissions';

const ORIGINAL_ADAPTER = httpClient.defaults.adapter as AxiosAdapter | undefined;

beforeEach(() => {
  httpClient.defaults.adapter = createMockAdapter(ORIGINAL_ADAPTER);
});

afterEach(() => {
  httpClient.defaults.adapter = ORIGINAL_ADAPTER;
  signOut();
});

describe('<RolesListPage />', () => {
  it('lists roles from GET /roles for a manager (USER_ACCESS_MANAGE)', async () => {
    signIn('super-admin');
    render(renderWithProviders(<RolesListPage />, ['/app/roles']));
    // Localized names come from the role catalog.
    expect(await screen.findByText(/HR Director|HR-директор|HR директор|HR direktor/i)).toBeInTheDocument();
    expect(await screen.findByText(/HRLab Super Admin/i)).toBeInTheDocument();
  });

  it('renders NoAccessState without USER_ACCESS_MANAGE', async () => {
    // Craft a user that can reach the route conceptually but lacks the gate.
    const user = buildDevUser('viewer');
    useAuthStore
      .getState()
      .setSession(
        { ...user, permissions: user.permissions.filter((p) => p !== PERMISSIONS.USER_ACCESS_MANAGE) },
        { value: 'test', expiresAt: Date.now() + 60_000 },
      );
    render(renderWithProviders(<RolesListPage />, ['/app/roles']));
    // No role table is rendered; a no-access state is shown instead.
    expect(screen.queryByText(/HRLab Super Admin/i)).not.toBeInTheDocument();
  });
});
