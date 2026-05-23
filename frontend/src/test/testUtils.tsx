import { type ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { I18nProvider } from '@/app/providers/I18nProvider';
import { useAuthStore } from '@/features/auth/authStore';
import { buildDevUser } from '@/features/auth/devAuth';

export function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0, staleTime: 0, refetchOnWindowFocus: false },
      mutations: { retry: false },
    },
  });
}

export function renderWithProviders(
  children: ReactNode,
  initialEntries: string[] = ['/'],
  client: QueryClient = createTestQueryClient(),
) {
  return (
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={initialEntries}>
        <I18nProvider>{children}</I18nProvider>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

export function signIn(role: 'super-admin' | 'consultant' | 'viewer' = 'super-admin') {
  const user = buildDevUser(role);
  useAuthStore.getState().setSession(user, { value: 'test', expiresAt: Date.now() + 60_000 });
}

export function signOut() {
  useAuthStore.getState().signOut();
}
