import { Component, type ErrorInfo, type ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { ErrorState } from './ErrorState';

interface RouteErrorBoundaryProps {
  /** Route content to guard. */
  children: ReactNode;
  /**
   * Changing this value remounts the boundary and clears a caught error, so
   * client-side navigation recovers a crashed subpage without a full reload.
   */
  resetKey?: string;
}

interface RouteErrorBoundaryState {
  hasError: boolean;
}

/**
 * Route-level error boundary.
 *
 * The app uses React Router's JSX `<Routes>` API (not the data router), so the
 * data-router-only `errorElement` is unavailable. This class boundary fills that
 * gap: a render error thrown by any routed subpage (e.g. the Methodology
 * Translations page reading an un-hydrated `version.factors`) is caught here and
 * rendered IN-PLACE via the shared {@link ErrorState} component, so the app
 * shell (TopBar + Sidebar + navigation) keeps working instead of white-screening.
 *
 * It deliberately reuses {@link ErrorState} rather than building a bespoke error
 * UI, and resets on navigation via {@link RouteErrorBoundaryReset}'s `resetKey`.
 */
class RouteErrorBoundaryInner extends Component<
  RouteErrorBoundaryProps,
  RouteErrorBoundaryState
> {
  state: RouteErrorBoundaryState = { hasError: false };

  static getDerivedStateFromError(): RouteErrorBoundaryState {
    return { hasError: true };
  }

  componentDidUpdate(prev: RouteErrorBoundaryProps): void {
    // Clear the error when the route (resetKey) changes so navigating away from
    // a crashed page restores normal rendering.
    if (this.state.hasError && prev.resetKey !== this.props.resetKey) {
      this.setState({ hasError: false });
    }
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    if (import.meta.env.DEV) {
      // Dev-only diagnostic. NEVER logs tokens or salary data — only the error
      // message + component stack, consistent with the httpClient logging rules.
      // eslint-disable-next-line no-console
      console.error('[route-error-boundary]', error.message, info.componentStack);
    }
  }

  render(): ReactNode {
    if (this.state.hasError) {
      return (
        <ErrorState
          onRetry={() => this.setState({ hasError: false })}
          className="m-2"
        />
      );
    }
    return this.props.children;
  }
}

/**
 * Hook-aware wrapper that feeds the current route key into the class boundary so
 * it auto-resets on navigation. Use this as a route element/wrapper.
 */
export function RouteErrorBoundary({ children }: { children: ReactNode }) {
  const location = useLocation();
  return (
    <RouteErrorBoundaryInner resetKey={location.pathname}>
      {children}
    </RouteErrorBoundaryInner>
  );
}
