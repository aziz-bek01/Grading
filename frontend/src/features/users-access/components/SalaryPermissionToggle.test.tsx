import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SalaryPermissionToggle } from './SalaryPermissionToggle';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';

describe('<SalaryPermissionToggle />', () => {
  beforeEach(() => {
    signOut();
    signIn('super-admin'); // has USER_SALARY_PERMISSION_TOGGLE
  });

  it('opens 2-step confirmation dialog and refuses submit without a 10+ char reason', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(renderWithProviders(<SalaryPermissionToggle enabled={false} onSubmit={onSubmit} />));
    await user.click(screen.getByTestId('salary-permission-toggle'));
    const confirm = screen.getByTestId('salary-permission-confirm');
    expect(confirm).toBeDisabled();
    await user.type(screen.getByTestId('salary-permission-reason'), 'short');
    expect(confirm).toBeDisabled();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('submits with a valid reason (≥ 10 chars)', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(renderWithProviders(<SalaryPermissionToggle enabled={false} onSubmit={onSubmit} />));
    await user.click(screen.getByTestId('salary-permission-toggle'));
    await user.type(
      screen.getByTestId('salary-permission-reason'),
      'HR Director needs salary visibility for grading project',
    );
    await user.click(screen.getByTestId('salary-permission-confirm'));
    await new Promise((r) => setTimeout(r, 0));
    expect(onSubmit).toHaveBeenCalledWith(
      true,
      'HR Director needs salary visibility for grading project',
    );
  });
});

describe('<SalaryPermissionToggle /> permission gating', () => {
  it('does not render the toggle button for a user without USER_SALARY_PERMISSION_TOGGLE', () => {
    signOut();
    signIn('viewer'); // viewer has no USER_SALARY_PERMISSION_TOGGLE
    render(renderWithProviders(<SalaryPermissionToggle enabled onSubmit={vi.fn()} />));
    expect(screen.queryByTestId('salary-permission-toggle')).not.toBeInTheDocument();
  });
});
