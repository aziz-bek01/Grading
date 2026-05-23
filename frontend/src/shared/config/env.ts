/**
 * Centralised access to Vite environment variables.
 * Never reach for import.meta.env outside this file.
 */
export const env = {
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
  isDev: import.meta.env.DEV,
  isProd: import.meta.env.PROD,
  devAuthEnabled: (import.meta.env.VITE_DEV_AUTH ?? 'true') === 'true',
  useMockApi: (import.meta.env.VITE_USE_MSW ?? 'false') === 'true',
  defaultLocale: (import.meta.env.VITE_DEFAULT_LOCALE as string | undefined) ?? 'ru-RU',
} as const;
