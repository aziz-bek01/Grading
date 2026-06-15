import { type ReactNode, useState } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

export function QueryProvider({ children }: { children: ReactNode }) {
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            // Catalog / read-mostly data (positions, departments, methodology,
            // grades …) changes rarely within a session. A 5-minute default
            // staleTime stops the workspace's many co-mounted queries from
            // re-fetching on every remount/navigation (P1-T2). Volatile,
            // polled queries override this per-hook.
            staleTime: 5 * 60_000,
            retry: 1,
            refetchOnWindowFocus: false,
            // Background polls (`refetchInterval`) must NOT fire while the tab
            // is hidden — pause them until the user returns, cutting idle
            // network churn (P1-T2).
            refetchIntervalInBackground: false,
          },
        },
      }),
  );
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}
