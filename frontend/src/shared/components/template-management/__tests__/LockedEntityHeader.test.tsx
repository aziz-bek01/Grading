import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { renderWithProviders, signInWithPermissions, signOut } from '@/test/testUtils';
import { PERMISSIONS } from '@/shared/types/permissions';
import { LockedEntityHeader } from '../LockedEntityHeader';

describe('<LockedEntityHeader /> (shared)', () => {
  beforeEach(() => signInWithPermissions([PERMISSIONS.GRADE_EDIT]));
  afterEach(() => signOut());

  it('renders the status/title/body content passed in verbatim', () => {
    render(
      renderWithProviders(
        <LockedEntityHeader
          status="APPROVED"
          testId="locked-x-header"
          title="Approved"
          body="Approved on 2026-04-22T10:00:00Z"
          permission={PERMISSIONS.GRADE_EDIT}
          createNewVersionLabel="Create new version"
          createNewVersionTestId="x-create-new-version"
        />,
      ),
    );
    const root = screen.getByTestId('locked-x-header');
    expect(root.getAttribute('data-status')).toBe('APPROVED');
    expect(screen.getByText('Approved')).toBeInTheDocument();
    expect(screen.getByTestId('locked-actor-time').textContent).toBe(
      'Approved on 2026-04-22T10:00:00Z',
    );
  });

  it('shows the secondary "approved by" line only when supplied', () => {
    const { rerender } = render(
      renderWithProviders(
        <LockedEntityHeader
          status="LOCKED"
          testId="locked-x-header"
          title="Locked"
          body="Locked on Y"
          approvedByLine={null}
          permission={PERMISSIONS.GRADE_EDIT}
          createNewVersionLabel="Create new version"
          createNewVersionTestId="x-create-new-version"
        />,
      ),
    );
    expect(screen.queryByTestId('locked-approved-by')).not.toBeInTheDocument();

    rerender(
      renderWithProviders(
        <LockedEntityHeader
          status="LOCKED"
          testId="locked-x-header"
          title="Locked"
          body="Locked on Y"
          approvedByLine="Approved by Someone on Z"
          permission={PERMISSIONS.GRADE_EDIT}
          createNewVersionLabel="Create new version"
          createNewVersionTestId="x-create-new-version"
        />,
      ),
    );
    expect(screen.getByTestId('locked-approved-by').textContent).toBe(
      'Approved by Someone on Z',
    );
  });

  it('calls onCreateNewVersion when the CTA is clicked (permission granted)', () => {
    const onCreate = vi.fn();
    render(
      renderWithProviders(
        <LockedEntityHeader
          status="APPROVED"
          testId="locked-x-header"
          title="Approved"
          body="Approved on Y"
          permission={PERMISSIONS.GRADE_EDIT}
          onCreateNewVersion={onCreate}
          createNewVersionLabel="Create new version"
          createNewVersionTestId="x-create-new-version"
        />,
      ),
    );
    fireEvent.click(screen.getByTestId('x-create-new-version'));
    expect(onCreate).toHaveBeenCalledTimes(1);
  });

  it('hides the CTA entirely when the permission is missing (UX-only gate)', () => {
    signOut();
    signInWithPermissions([]);
    render(
      renderWithProviders(
        <LockedEntityHeader
          status="APPROVED"
          testId="locked-x-header"
          title="Approved"
          body="Approved on Y"
          permission={PERMISSIONS.GRADE_EDIT}
          onCreateNewVersion={vi.fn()}
          createNewVersionLabel="Create new version"
          createNewVersionTestId="x-create-new-version"
        />,
      ),
    );
    expect(screen.queryByTestId('x-create-new-version')).not.toBeInTheDocument();
  });
});
