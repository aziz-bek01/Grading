/**
 * RecentActivityList tests (D-2).
 *
 * Coverage:
 *   1. Renders event rows for a project the audit feed knows about.
 *   2. For users without AUDIT_READ, shows the explanatory no-access
 *      placeholder instead of trying to fetch.
 */
import { afterEach, describe, it, expect, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import type { AxiosAdapter } from 'axios';
import { RecentActivityList } from './RecentActivityList';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { httpClient } from '@/shared/api/httpClient';
import { createMockAdapter } from '@/shared/api/mocks/handlers';
import { useAuthStore } from '@/features/auth/authStore';

// Tests run with VITE_USE_MSW unset, so the in-process mock adapter is NOT
// auto-installed by httpClient. Install it explicitly (same pattern as every
// other data-driven page test) so the audit feed resolves from fixtures.
const ORIGINAL_ADAPTER = httpClient.defaults.adapter as AxiosAdapter | undefined;

function activateAcme() {
  const tenants = useAuthStore.getState().user?.tenants ?? [];
  const acme = tenants.find((t) => t.slug === 'acme');
  if (acme) useAuthStore.getState().setActiveTenant(acme);
}

afterEach(() => {
  httpClient.defaults.adapter = ORIGINAL_ADAPTER;
});

describe('<RecentActivityList />', () => {
  beforeEach(() => {
    httpClient.defaults.adapter = createMockAdapter(ORIGINAL_ADAPTER);
    signOut();
    signIn('super-admin');
    activateAcme();
  });

  it('renders project-related events from the audit feed', async () => {
    render(renderWithProviders(<RecentActivityList projectId="proj-acme-2026" />, ['/app']));
    await waitFor(
      () => {
        // Seed contains PROJECT_CREATED / proj-acme-2026 → list should appear.
        expect(screen.getByTestId('recent-activity-list')).toBeInTheDocument();
      },
      { timeout: 3000 },
    );
    expect(screen.getByTestId('recent-activity-open-audit')).toBeInTheDocument();
  });

  it('shows no-access placeholder for users without AUDIT_READ', async () => {
    signOut();
    signIn('viewer'); // viewer in devAuth has no AUDIT_READ
    activateAcme();
    render(renderWithProviders(<RecentActivityList projectId="proj-acme-2026" />, ['/app']));
    expect(screen.getByTestId('recent-activity-no-access')).toBeInTheDocument();
    // No fetch should occur — the loading placeholder must not appear.
    expect(screen.queryByTestId('recent-activity-loading')).not.toBeInTheDocument();
  });
});
