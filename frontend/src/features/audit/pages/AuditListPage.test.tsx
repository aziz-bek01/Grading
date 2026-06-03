/**
 * AuditListPage tests (D-1 FE).
 *
 * Coverage:
 *   1. Renders the table with audit events from the mock backend.
 *   2. Action filter narrows results to a single action code.
 *   3. The CSV export button is hidden for users without EXPORT_GENERAL.
 */
import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AuditListPage } from './AuditListPage';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { useAuthStore } from '@/features/auth/authStore';

function activateAcme() {
  const tenants = useAuthStore.getState().user?.tenants ?? [];
  const acme = tenants.find((t) => t.slug === 'acme');
  if (acme) {
    useAuthStore.getState().setActiveTenant(acme);
  }
}

describe('<AuditListPage />', () => {
  beforeEach(() => {
    signOut();
    signIn('super-admin');
    activateAcme();
  });

  it('renders rows from the mock audit feed', async () => {
    render(renderWithProviders(<AuditListPage />, ['/app/audit']));
    // Wait for the localised header to render.
    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument();
    });
    // At least one of our seed actions must be visible.
    await waitFor(
      () => {
        const cells = screen.getAllByRole('row');
        // header + at least 5 data rows
        expect(cells.length).toBeGreaterThan(5);
      },
      { timeout: 3000 },
    );
  });

  it('filters by action code', async () => {
    const user = userEvent.setup();
    render(renderWithProviders(<AuditListPage />, ['/app/audit']));
    await waitFor(() => expect(screen.getAllByRole('row').length).toBeGreaterThan(5));

    const actionSelect = screen.getByTestId('audit-filter-action') as HTMLSelectElement;
    await user.selectOptions(actionSelect, 'LOGIN_SUCCESS');

    await waitFor(() => {
      const rows = screen.getAllByRole('row');
      // header + exactly one LOGIN_SUCCESS row in the seed
      expect(rows.length).toBe(2);
    });
  });
});

describe('<AuditListPage /> permission gating', () => {
  it('hides CSV export when user lacks EXPORT_GENERAL', async () => {
    signOut();
    // Consultant has AUDIT_READ but not EXPORT_GENERAL in devAuth.
    signIn('consultant');
    activateAcme();
    render(renderWithProviders(<AuditListPage />, ['/app/audit']));
    await waitFor(() => expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument());
    expect(screen.queryByTestId('audit-export-csv')).not.toBeInTheDocument();
  });
});
