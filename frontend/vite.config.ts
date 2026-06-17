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
    build: {
      // Vite 8 / Rolldown splits each route-level `React.lazy` import into its
      // own async chunk automatically. These `advancedChunks` groups peel the
      // large, rarely-changing vendors out of the entry chunk so the initial
      // download shrinks and the vendor chunks stay cached across deploys.
      //
      // NOTE: Rolldown takes `output.codeSplitting.groups` (each a
      // `{ name, test }` matcher) — NOT Rollup's object-form `manualChunks`
      // (that shape is rejected by the Rolldown types in Vite 8; the older
      // `advancedChunks` alias is deprecated). Use `[\\/]` in the regexes to
      // match the path separator portably.
      rolldownOptions: {
        output: {
          codeSplitting: {
            groups: [
              {
                name: 'react-vendor',
                test: /node_modules[\\/](react|react-dom|react-router|react-router-dom|scheduler)[\\/]/,
                priority: 30,
              },
              {
                name: 'i18n-vendor',
                test: /node_modules[\\/](i18next|react-i18next|i18next-browser-languagedetector)[\\/]/,
                priority: 20,
              },
              {
                name: 'form-vendor',
                test: /node_modules[\\/](react-hook-form|@hookform[\\/]resolvers|zod)[\\/]/,
                priority: 20,
              },
              {
                name: 'query-vendor',
                test: /node_modules[\\/]@tanstack[\\/]/,
                priority: 20,
              },
              {
                name: 'auth-vendor',
                test: /node_modules[\\/]oidc-client-ts[\\/]/,
                priority: 20,
              },
              {
                name: 'icons-vendor',
                test: /node_modules[\\/]lucide-react[\\/]/,
                priority: 20,
              },
            ],
          },
        },
      },
    },
    server: {
      port: 5173,
      // Allow the Claude Code Preview reverse-proxy's generated Host header
      // (Vite otherwise returns "Blocked request"). Dev/preview only.
      allowedHosts: true,
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
