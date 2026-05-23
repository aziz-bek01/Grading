import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { RequireAuth } from './RequireAuth';
import { signIn, signOut } from '@/test/testUtils';
import { I18nProvider } from '@/app/providers/I18nProvider';

function renderRoute(initial: string) {
  return render(
    <I18nProvider>
      <MemoryRouter initialEntries={[initial]}>
        <Routes>
          <Route path="/login" element={<div>Login page</div>} />
          <Route element={<RequireAuth />}>
            <Route path="/protected" element={<div>Protected content</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </I18nProvider>,
  );
}

describe('<RequireAuth />', () => {
  beforeEach(() => {
    signOut();
  });

  it('redirects unauthenticated users to /login', () => {
    renderRoute('/protected');
    expect(screen.getByText('Login page')).toBeInTheDocument();
    expect(screen.queryByText('Protected content')).not.toBeInTheDocument();
  });

  it('renders protected content when authenticated', () => {
    signIn();
    renderRoute('/protected');
    expect(screen.getByText('Protected content')).toBeInTheDocument();
  });
});
