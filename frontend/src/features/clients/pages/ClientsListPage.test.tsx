/**
 * /app/clients smoke + permission tests.
 *
 * We don't drive the full DataTable here (covered by DataTable.test.tsx);
 * the focus is: page renders against the mock backend, status filter
 * round-trips, and PermissionGate hides the archive action when missing
 * TENANT_ARCHIVE.
 */
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Routes, Route } from 'react-router-dom';
import type { AxiosAdapter } from 'axios';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { httpClient } from '@/shared/api/httpClient';
import { createMockAdapter } from '@/shared/api/mocks/handlers';
import { useAuthStore } from '@/features/auth/authStore';
import { PERMISSIONS } from '@/shared/types/permissions';
import { ClientsListPage } from './ClientsListPage';
import { ClientDetailsPage } from './ClientDetailsPage';

const ORIGINAL_ADAPTER = httpClient.defaults.adapter as AxiosAdapter | undefined;

beforeEach(() => {
  httpClient.defaults.adapter = createMockAdapter(ORIGINAL_ADAPTER);
});

afterEach(() => {
  httpClient.defaults.adapter = ORIGINAL_ADAPTER;
  signOut();
});

function withRoutes(initial = '/app/clients') {
  return renderWithProviders(
    <Routes>
      <Route path="/app/clients" element={<ClientsListPage />} />
      <Route path="/app/clients/:tenantId" element={<ClientDetailsPage />} />
    </Routes>,
    [initial],
  );
}

describe('<ClientsListPage />', () => {
  it('renders the tenants list from the mock backend', async () => {
    signIn('super-admin');
    render(withRoutes());
    // Each of the fixture tenants should eventually appear by display_name.
    expect(await screen.findByText('ACME Holdings')).toBeInTheDocument();
    expect(screen.getByText('Beta University')).toBeInTheDocument();
    expect(screen.getByText('OzBank')).toBeInTheDocument();
  });

  it('filters by status (ARCHIVED hides ACTIVE tenants)', async () => {
    signIn('super-admin');
    const user = userEvent.setup();
    render(withRoutes());
    expect(await screen.findByText('ACME Holdings')).toBeInTheDocument();
    // Status filter is rendered by FilterBar — a single <select> with
    // aria-label="Status / Статус / …".
    const selects = await screen.findAllByRole('combobox');
    // First combobox is the status filter (only filter rendered for tenants).
    await user.selectOptions(selects[0], 'ARCHIVED');
    await waitFor(() => {
      expect(screen.queryByText('ACME Holdings')).not.toBeInTheDocument();
    });
    expect(screen.getByText('UzTel')).toBeInTheDocument();
  });

  it('navigates to ClientDetailsPage on row click and hides archive button without TENANT_ARCHIVE', async () => {
    signIn('super-admin');
    // Manually drop TENANT_ARCHIVE from the active permission set to assert
    // the PermissionGate suppresses the destructive action.
    const state = useAuthStore.getState();
    useAuthStore.setState({
      ...state,
      user: {
        ...state.user!,
        permissions: state.user!.permissions.filter(
          (p) => p !== PERMISSIONS.TENANT_ARCHIVE,
        ),
      },
    });

    const user = userEvent.setup();
    render(withRoutes());
    const row = await screen.findByText('ACME Holdings');
    await user.click(row);
    // Detail page heading appears (also "ACME Holdings"); wait for the back
    // button which only exists on the detail page.
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Назад|Back|Орқага|Ortga/i })).toBeInTheDocument();
    });
    // Archive button must be absent.
    expect(screen.queryByTestId('archive-tenant-button')).not.toBeInTheDocument();
    // Edit tenant button must still be present (TENANT_EDIT still granted).
    expect(screen.getByTestId('edit-tenant-button')).toBeInTheDocument();
  });
});

