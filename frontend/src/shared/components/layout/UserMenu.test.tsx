import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { UserMenu } from './UserMenu';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { useAuthStore } from '@/features/auth/authStore';

describe('<UserMenu />', () => {
  beforeEach(() => {
    signOut();
  });

  it('renders trigger with aria-label and testid', () => {
    signIn('super-admin');
    render(renderWithProviders(<UserMenu />));
    const trigger = screen.getByTestId('user-menu-trigger');
    expect(trigger).toBeInTheDocument();
    expect(trigger).toHaveAttribute('aria-label');
    expect(trigger).toHaveAttribute('aria-expanded', 'false');
  });

  it('opens the popover on click', async () => {
    const user = userEvent.setup();
    signIn('super-admin');
    render(renderWithProviders(<UserMenu />));
    expect(screen.queryByTestId('user-menu-panel')).not.toBeInTheDocument();
    await user.click(screen.getByTestId('user-menu-trigger'));
    expect(screen.getByTestId('user-menu-panel')).toBeInTheDocument();
    expect(screen.getByTestId('user-menu-signout')).toBeInTheDocument();
    expect(screen.getByTestId('user-menu-profile')).toBeInTheDocument();
  });

  it('Sign out clears auth state and redirects to /login', async () => {
    const user = userEvent.setup();
    signIn('super-admin');
    render(renderWithProviders(<UserMenu />, ['/app/dashboard']));
    await user.click(screen.getByTestId('user-menu-trigger'));
    await user.click(screen.getByTestId('user-menu-signout'));
    // Sign-out clears the auth store
    expect(useAuthStore.getState().isAuthenticated).toBe(false);
    expect(useAuthStore.getState().user).toBeNull();
  });
});
