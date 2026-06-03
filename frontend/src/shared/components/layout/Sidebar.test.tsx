import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Sidebar } from './Sidebar';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { useAuthStore } from '@/features/auth/authStore';

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
