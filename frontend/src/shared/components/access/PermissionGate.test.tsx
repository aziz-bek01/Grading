import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PermissionGate } from './PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';

describe('<PermissionGate />', () => {
  beforeEach(() => {
    signOut();
  });

  it('hides children when permission is missing', () => {
    signIn('viewer');
    render(
      renderWithProviders(
        <PermissionGate permission={PERMISSIONS.PROJECT_CREATE}>
          <span data-testid="protected">secret</span>
        </PermissionGate>,
      ),
    );
    expect(screen.queryByTestId('protected')).not.toBeInTheDocument();
  });

  it('renders children when permission is present', () => {
    signIn('super-admin');
    render(
      renderWithProviders(
        <PermissionGate permission={PERMISSIONS.PROJECT_CREATE}>
          <span data-testid="protected">secret</span>
        </PermissionGate>,
      ),
    );
    expect(screen.getByTestId('protected')).toBeInTheDocument();
  });

  it('supports "any" mode for multiple permissions', () => {
    signIn('consultant');
    render(
      renderWithProviders(
        <PermissionGate
          permission={[PERMISSIONS.POSITION_CREATE, PERMISSIONS.PROJECT_CREATE]}
          mode="any"
        >
          <span data-testid="anymode">visible</span>
        </PermissionGate>,
      ),
    );
    expect(screen.getByTestId('anymode')).toBeInTheDocument();
  });

  it('"all" mode hides when one permission missing', () => {
    signIn('consultant');
    render(
      renderWithProviders(
        <PermissionGate
          permission={[PERMISSIONS.POSITION_CREATE, PERMISSIONS.PROJECT_CREATE]}
          mode="all"
        >
          <span data-testid="allmode">visible</span>
        </PermissionGate>,
      ),
    );
    expect(screen.queryByTestId('allmode')).not.toBeInTheDocument();
  });
});
