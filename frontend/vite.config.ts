import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

/**
 * Vite config.
 *
 * Compile-time flag `__ENABLE_MSW__` lets the production build tree-shake the
 * entire mock adapter chunk (2977+ lines of fixtures + handlers). When MSW is
 * NOT enabled, `if (__ENABLE_MSW__) { ... }` collapses to `if (false) { ... }`
 * which esbuild's minifier strips, and the dynamic `import('./mocks/handlers')`
 * call inside that branch is never emitted as a chunk.
 *
 * Set `VITE_USE_MSW=true` (env var) at build time to opt in for a mocked
 * production-style bundle (used only by Storybook/demo deployments).
 */
export default defineConfig(({ mode }) => {
  // Vite does NOT auto-load `.env*` files into `process.env` — use loadEnv to
  // read them, then check VITE_USE_MSW (set in .env.local for demo dev).
  const env = loadEnv(mode, process.cwd(), '');
  const enableMsw = env.VITE_USE_MSW === 'true';

  // Surface the flag in dev console so developers know which mode they built in.
  // eslint-disable-next-line no-console
  console.info(`[vite] mode=${mode} __ENABLE_MSW__=${enableMsw}`);

  return {
    plugins: [react()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
      },
    },
    define: {
      __ENABLE_MSW__: JSON.stringify(enableMsw),
    },
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: process.env.VITE_API_PROXY ?? 'http://localhost:8080',
          changeOrigin: true,
          secure: false,
        },
      },
    },
  };
});
