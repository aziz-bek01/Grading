import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Sidebar } from './Sidebar';
import { renderWithProviders, signIn, signInWithPermissions, signOut } from '@/test/testUtils';
import { useAuthStore } from '@/features/auth/authStore';
import { PERMISSIONS } from '@/shared/types/permissions';

describe('<Sidebar /> collapse mode', () => {
  beforeEach(() => {
    signOut();
    // Reset the persisted preference so each test starts expanded.
    useAuthStore.getState().setSidebarCollapsed(false);
  });

  it('renders expanded by default with text labels visible', () => {
    signIn('super-admin');
    render(renderWithProviders(<Sidebar />));

    const aside = screen.getByTestId('app-sidebar');
    expect(aside).not.toHaveAttribute('data-collapsed');
    // Toggle button advertises the "collapse" affordance when expanded.
    expect(
      screen.getByRole('button', { name: /Переключить боковое меню/i }),
    ).toHaveAttribute('aria-expanded', 'true');
  });

  it('collapses to icon-only mode when the toggle is clicked', async () => {
    const user = userEvent.setup();
    signIn('super-admin');
    render(renderWithProviders(<Sidebar />));

    await user.click(screen.getByTestId('sidebar-toggle'));

    const aside = screen.getByTestId('app-sidebar');
    expect(aside).toHaveAttribute('data-collapsed');
    expect(useAuthStore.getState().sidebarCollapsed).toBe(true);
    // Width class flips to the icon-only rail.
    expect(aside.className).toContain('w-16');
  });

  it('reflects the collapsed store state on mount (persistence)', () => {
    signIn('super-admin');
    useAuthStore.getState().setSidebarCollapsed(true);
    render(renderWithProviders(<Sidebar />));

    expect(screen.getByTestId('app-sidebar')).toHaveAttribute('data-collapsed');
    expect(
      screen.getByRole('button', { name: /Переключить боковое меню/i }),
    ).toHaveAttribute('aria-expanded', 'false');
  });
});

/**
 * Project-scoped "approvals" nav entry (Finding 1 — wire-in).
 *
 * `ApprovalsListPage` (`/app/projects/:id/approvals`) was already registered
 * in router.tsx behind the correct permission gate but had no Sidebar link,
 * making it reachable only by typing the URL. Mirrors the sibling
 * `reports`/`grades` workspace items: permission-gated + disabled (no
 * active project) stub when no project is selected.
 */
describe('<Sidebar /> project approvals nav item', () => {
  beforeEach(() => {
    signOut();
    useAuthStore.getState().setSidebarCollapsed(false);
  });

  it('links to the project-scoped approvals route when a project is active and the user can decide/create approvals', () => {
    signInWithPermissions([PERMISSIONS.APPROVAL_REQUEST_DECIDE]);
    useAuthStore.getState().setActiveProject({
      id: 'proj-1',
      slug: 'proj-1',
      name: 'Demo project',
      tenant_id: 'tenant-1',
    });
    render(renderWithProviders(<Sidebar />));

    const link = screen.getByRole('link', { name: /Согласования проекта/i });
    expect(link).toHaveAttribute('href', '/app/projects/proj-1/approvals');
  });

  it('renders a disabled stub (no navigable link) when no project is active', () => {
    signInWithPermissions([PERMISSIONS.APPROVAL_REQUEST_DECIDE]);
    render(renderWithProviders(<Sidebar />));

    expect(screen.getByText('Согласования проекта')).toBeInTheDocument();
    expect(
      screen.queryByRole('link', { name: /Согласования проекта/i }),
    ).not.toBeInTheDocument();
  });

  it('is hidden for a user without any approval permission', () => {
    signInWithPermissions([PERMISSIONS.PROJECT_READ]);
    useAuthStore.getState().setActiveProject({
      id: 'proj-1',
      slug: 'proj-1',
      name: 'Demo project',
      tenant_id: 'tenant-1',
    });
    render(renderWithProviders(<Sidebar />));

    expect(screen.queryByText('Согласования проекта')).not.toBeInTheDocument();
  });
});
