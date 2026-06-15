import { Component, type ErrorInfo, type ReactNode } from 'react';
import i18n from '@/shared/i18n';

interface AppErrorBoundaryProps {
  children: ReactNode;
}

interface AppErrorBoundaryState {
  hasError: boolean;
}

/**
 * Top-level (app-wide) error boundary.
 *
 * The {@link RouteErrorBoundary} lives INSIDE the AppShell, so it only guards
 * routed subpages — a render error thrown by a provider, the login page or the
 * `/auth/callback` screen (all outside the shell) would white-screen the whole
 * app. This boundary wraps the entire router to provide a friendly, reload-able
 * fallback for those cases.
 *
 * It cannot use the `useTranslation` hook (class component, and the i18n React
 * context may itself have crashed), so it reads strings straight off the i18n
 * singleton, falling back to English literals if i18n is unavailable.
 */
export class AppErrorBoundary extends Component<
  AppErrorBoundaryProps,
  AppErrorBoundaryState
> {
  state: AppErrorBoundaryState = { hasError: false };

  static getDerivedStateFromError(): AppErrorBoundaryState {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    if (import.meta.env.DEV) {
      // Dev-only diagnostic. NEVER logs tokens or salary data — only the error
      // message + component stack, consistent with the httpClient logging rules.
      console.error('[app-error-boundary]', error.message, info.componentStack);
    }
  }

  private translate(key: string, fallback: string): string {
    try {
      const value = i18n.t(key);
      return value === key ? fallback : value;
    } catch {
      return fallback;
    }
  }

  render(): ReactNode {
    if (!this.state.hasError) return this.props.children;
    const title = this.translate('states.app_error_title', 'Something went wrong');
    const body = this.translate(
      'states.app_error_body',
      'The application ran into an unexpected error. Reloading usually fixes it.',
    );
    const reload = this.translate('states.app_error_reload', 'Reload application');
    return (
      <div
        role="alert"
        className="min-h-screen flex items-center justify-center bg-background p-6"
      >
        <div className="max-w-md w-full text-center rounded-lg border border-border bg-surface p-8 shadow-sm">
          <h1 className="text-lg font-semibold text-text-primary">{title}</h1>
          <p className="text-sm text-text-secondary mt-2">{body}</p>
          <button
            type="button"
            onClick={() => window.location.reload()}
            className="mt-6 inline-flex items-center justify-center h-10 px-4 rounded-md bg-primary-500 text-text-inverse text-sm hover:bg-primary-600"
          >
            {reload}
          </button>
        </div>
      </div>
    );
  }
}
