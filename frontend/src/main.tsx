import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './app/App';
import { loadRuntimeConfig } from '@/shared/config/runtimeConfig';
import './styles/globals.css';

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
