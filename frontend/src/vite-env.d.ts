/// <reference types="vite/client" />

/**
 * Compile-time flag injected by Vite via `define` (see vite.config.ts).
 *
 * - `true`  when the build was invoked with `VITE_USE_MSW=true`
 * - `false` otherwise (default for production)
 *
 * Use `if (__ENABLE_MSW__) { ... }` to guard the in-process mock adapter so
 * the entire `mocks/handlers.ts` chunk is tree-shaken out of production bundles.
 */
declare const __ENABLE_MSW__: boolean;
