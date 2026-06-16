import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'node:path';

/**
 * Vitest config.
 *
 * Mirrors `vite.config.ts` so the test runtime can resolve the same
 * `__ENABLE_MSW__` compile-time global. Tests ALWAYS run with the mock
 * adapter enabled (otherwise the suite would hit a non-existent backend),
 * so we hard-code `true` here regardless of the env var.
 */
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  define: {
    __ENABLE_MSW__: JSON.stringify(true),
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    css: false,
    pool: 'threads',
    testTimeout: 15000,
    // Vitest owns the unit/component suite under `src` ONLY. The Playwright E2E
    // specs live in `e2e/` and use `@playwright/test` (a different runner), so
    // exclude that dir to stop Vitest from mis-collecting `e2e/*.spec.ts`.
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    exclude: ['e2e/**', 'node_modules/**', 'dist/**'],
  },
});
