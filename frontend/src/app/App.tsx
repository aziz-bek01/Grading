import { BrowserRouter } from 'react-router-dom';
import { QueryProvider } from './providers/QueryProvider';
import { I18nProvider } from './providers/I18nProvider';
import { AuthProvider } from './providers/AuthProvider';
import { ThemeProvider } from './providers/ThemeProvider';
import { AppErrorBoundary } from '@/shared/components/feedback/AppErrorBoundary';
import { AppRouter } from './router';

export function App() {
  return (
    <BrowserRouter>
      {/*
        Top-level error boundary. The route-level boundary lives inside the
        AppShell, so it cannot catch errors thrown by the providers, the login
        page or /auth/callback (all outside the shell). This boundary wraps the
        whole tree and renders a friendly, reload-able localized fallback.
      */}
      <AppErrorBoundary>
        <I18nProvider>
          <QueryProvider>
            <AuthProvider>
              <ThemeProvider>
                <AppRouter />
              </ThemeProvider>
            </AuthProvider>
          </QueryProvider>
        </I18nProvider>
      </AppErrorBoundary>
    </BrowserRouter>
  );
}
