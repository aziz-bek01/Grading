import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { RequirePermission } from '@/app/routing/RequirePermission';
import { PERMISSIONS } from '@/shared/types/permissions';
import { signIn, signInWithPermissions, signOut } from '@/test/testUtils';
import { I18nProvider } from '@/app/providers/I18nProvider';

/**
 * P2-T1 regression: the GLOBAL approvals inbox/detail routes (/app/approvals,
 * /app/approvals/:id) must be permission-gated like the project-scoped variant.
 * BE GET /my-inbox + GET /approval-requests/{id} require APPROVAL_REQUEST_DECIDE;
 * a non-decider must see the clean NoAccessState, not a raw error card.
 *
 * This mirrors the exact guard wired in router.tsx around the inbox routes.
 */
const APPROVAL_PERMS = [
  PERMISSIONS.APPROVAL_REQUEST_CREATE,
  PERMISSIONS.APPROVAL_REQUEST_DECIDE,
];

function renderInbox() {
  return render(
    <I18nProvider>
      <MemoryRouter initialEntries={['/app/approvals']}>
        <Routes>
          <Route element={<RequirePermission permissions={APPROVAL_PERMS} mode="any" />}>
            <Route path="/app/approvals" element={<div>Inbox content</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </I18nProvider>,
  );
}

describe('Global approvals inbox route guard', () => {
  beforeEach(() => {
    signOut();
  });

  it('blocks a user without any approval permission (NoAccessState, not raw error)', () => {
    // viewer has none of the approval permissions.
    signInWithPermissions([PERMISSIONS.PROJECT_READ]);
    renderInbox();
    expect(screen.queryByText('Inbox content')).not.toBeInTheDocument();
    expect(screen.getByText(/Нет доступа/i)).toBeInTheDocument();
  });

  it('allows a decider that only holds APPROVAL_REQUEST_DECIDE', () => {
    signInWithPermissions([PERMISSIONS.APPROVAL_REQUEST_DECIDE]);
    renderInbox();
    expect(screen.getByText('Inbox content')).toBeInTheDocument();
  });

  it('allows a full consultant (has the create/decide set)', () => {
    signIn('consultant');
    renderInbox();
    expect(screen.getByText('Inbox content')).toBeInTheDocument();
  });
});
