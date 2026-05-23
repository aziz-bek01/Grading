import { Outlet } from 'react-router-dom';
import { TopBar } from './TopBar';
import { Sidebar } from './Sidebar';

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
      <div className="flex flex-1 min-h-0">
        <Sidebar />
        <main id="main-content" className="flex-1 min-w-0 overflow-y-auto">
          <div className="max-w-screen-2xl mx-auto p-6">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
