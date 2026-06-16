import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './app/App';
import { loadRuntimeConfig } from '@/shared/config/runtimeConfig';
import { installE2ETestHandle } from '@/shared/lib/e2eTestHandle';
import './styles/globals.css';

// E2E ONLY: expose a narrow auth-store handle on `window` so Playwright specs
// can seed/inspect the session (e.g. mint a salary-permitted user to assert
// SalaryValue's permitted vs masked invariant). No-op + dead-code-eliminated in
// production builds — see e2eTestHandle.ts for the build-flag guard.
installE2ETestHandle();

const container = document.getElementById('root');
if (!container) throw new Error('Root container not found');

// Load runtime config (/config.json) BEFORE rendering so OIDC settings are
// available to the auth flow on first paint. Never throws — on failure OIDC is
// simply disabled and the app falls back to dev/login.
void loadRuntimeConfig().then(() => {
  createRoot(container).render(
    <StrictMode>
      <App />
    </StrictMode>,
  );
});
