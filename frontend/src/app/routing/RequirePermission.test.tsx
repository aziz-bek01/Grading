import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { RequirePermission } from './RequirePermission';
import { PERMISSIONS } from '@/shared/types/permissions';
import { signIn, signOut } from '@/test/testUtils';
import { I18nProvider } from '@/app/providers/I18nProvider';

import type { PermissionCode } from '@/shared/types/permissions';

function renderUnderPermission(perm: PermissionCode = PERMISSIONS.SALARY_VIEW) {
  return render(
    <I18nProvider>
      <MemoryRouter initialEntries={['/secret']}>
        <Routes>
          <Route element={<RequirePermission permissions={perm} />}>
            <Route path="/secret" element={<div>Secret content</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </I18nProvider>,
  );
}

describe('<RequirePermission />', () => {
  beforeEach(() => {
    signOut();
  });

  it('renders NoAccessState when user lacks the permission', () => {
    signIn('viewer'); // viewer has no SALARY_VIEW
    renderUnderPermission(PERMISSIONS.SALARY_VIEW);
    expect(screen.queryByText('Secret content')).not.toBeInTheDocument();
    expect(screen.getByText(/Нет доступа/i)).toBeInTheDocument();
  });

  it('renders children when user has the permission', () => {
    signIn('super-admin'); // has PROJECT_READ
    renderUnderPermission(PERMISSIONS.PROJECT_READ);
    expect(screen.getByText('Secret content')).toBeInTheDocument();
  });
});
