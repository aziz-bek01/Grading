import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { SalaryValue } from './SalaryValue';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { useAuthStore } from '@/features/auth/authStore';
import { PERMISSIONS } from '@/shared/types/permissions';
import { buildDevUser } from '@/features/auth/devAuth';

describe('<SalaryValue />', () => {
  beforeEach(() => {
    signOut();
  });

  it('shows "Salary access required" by default (no SALARY_VIEW in MVP 1)', () => {
    signIn('super-admin');
    render(renderWithProviders(<SalaryValue value={5_000_000} />));
    const node = screen.getByText(/доступ к данным о компенсации/i);
    expect(node).toBeInTheDocument();
  });

  it('does not show the numeric value when no SALARY_VIEW permission', () => {
    signIn('viewer');
    render(renderWithProviders(<SalaryValue value={5_000_000} />));
    expect(screen.queryByText(/5\s?000\s?000/)).not.toBeInTheDocument();
  });

  it('renders numeric value when SALARY_VIEW + salary_data_permission both true', () => {
    const user = buildDevUser('consultant');
    useAuthStore.getState().setSession(
      {
        ...user,
        permissions: [...user.permissions, PERMISSIONS.SALARY_VIEW],
        salary_data_permission: true,
      },
      { value: 'test', expiresAt: Date.now() + 60_000 },
    );

    render(renderWithProviders(<SalaryValue value={5_000_000} currency="UZS" />));
    const node = screen.getByText(/UZS/);
    expect(node).toBeInTheDocument();
    expect(node.getAttribute('data-state')).toBe('visible');
  });
});
