import { Outlet } from 'react-router-dom';
import { TopBar } from './TopBar';
import { Sidebar } from './Sidebar';
import { RouteErrorBoundary } from '@/shared/components/feedback/RouteErrorBoundary';

export function AppShell() {
  return (
    <div className="min-h-screen bg-background flex flex-col">
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:absolute focus:top-2 focus:left-2 bg-primary-500 text-text-inverse px-3 py-1 rounded-md text-sm"
      >
        Skip to content
      </a>
      <TopBar />
      {/*
        Window-level scroll (no nested overflow) so that the `sticky` TopBar
        and the per-page sticky widgets (RubricPanel, Sidebar) survive page
        scrolling. A nested overflow:auto on <main> would create its own
        scroll context and break top-0 sticky positioning above it.
      */}
      <div className="flex flex-1">
        <Sidebar className="sticky top-16 h-[calc(100vh-4rem)] shrink-0" />
        <main id="main-content" className="flex-1 min-w-0">
          <div className="max-w-screen-2xl mx-auto p-6">
            {/*
              Route-level error boundary (the JSX router's stand-in for
              `errorElement`): a thrown render error in a routed subpage shows
              the shared ErrorState here while TopBar + Sidebar keep working.
            */}
            <RouteErrorBoundary>
              <Outlet />
            </RouteErrorBoundary>
          </div>
        </main>
      </div>
    </div>
  );
}
