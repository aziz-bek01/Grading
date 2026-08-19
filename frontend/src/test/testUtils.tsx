import { type ReactNode } from 'react';
import { MemoryRouter, type InitialEntry } from 'react-router-dom';
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
  // Reuses react-router's own `InitialEntry` (`string | Partial<Location>`)
  // instead of a plain `string[]` so a test can seed router `state` (e.g. a
  // post-navigation flash message) — every existing `string[]` call site
  // stays valid since `string` is one of the union members.
  initialEntries: InitialEntry[] = ['/'],
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

/**
 * Sign in with an EXPLICIT permission set — used by tests that assert
 * permission-gated rendering (e.g. CAMPAIGN_RESULTS_VIEW lifting the blind).
 * Starts from the viewer seed (minimal) and replaces the permission array.
 */
export function signInWithPermissions(permissions: string[]) {
  const user = { ...buildDevUser('viewer'), permissions: permissions as never };
  useAuthStore.getState().setSession(user, { value: 'test', expiresAt: Date.now() + 60_000 });
}

export function signOut() {
  useAuthStore.getState().signOut();
}
