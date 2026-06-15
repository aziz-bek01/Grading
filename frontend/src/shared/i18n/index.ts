import i18n, { type BackendModule, type ReadCallback } from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
// Only the default product locale (ru-RU) is bundled into the entry chunk.
// The three other locales are split into their own async chunks and fetched on
// demand via the lazy backend below (P1-T1) so ~300 KB of translation JSON no
// longer ships in the initial download.
import ruRU from './locales/ru-RU.json';
import { env } from '@/shared/config/env';

export const SUPPORTED_LOCALES = ['ru-RU', 'uz-Cyrl-UZ', 'uz-Latn-UZ', 'en-US'] as const;
export type SupportedLocale = (typeof SUPPORTED_LOCALES)[number];

/**
 * Per-locale dynamic importers. ru-RU is intentionally absent — it is bundled
 * eagerly (see `resources` below) so the default UI never blocks on a fetch.
 * Each entry is a separate `import()` so Vite/Rolldown emits one async chunk
 * per non-default locale; only the active locale's JSON is downloaded.
 */
const lazyLocaleLoaders: Record<
  Exclude<SupportedLocale, 'ru-RU'>,
  () => Promise<{ default: Record<string, unknown> }>
> = {
  'uz-Cyrl-UZ': () => import('./locales/uz-Cyrl-UZ.json'),
  'uz-Latn-UZ': () => import('./locales/uz-Latn-UZ.json'),
  'en-US': () => import('./locales/en-US.json'),
};

/**
 * Minimal i18next backend that resolves a `translation` namespace by
 * dynamically importing the matching locale chunk. i18next awaits this `read`
 * inside `changeLanguage`, so by the time the promise resolves the bundle is
 * registered and the UI re-renders with the new strings — no flash of missing
 * keys. ru-RU is served from the eagerly-bundled object (no network round-trip).
 */
const lazyResourceBackend: BackendModule = {
  type: 'backend',
  init: () => {
    /* no options needed */
  },
  read: (language: string, _namespace: string, callback: ReadCallback) => {
    if (language === 'ru-RU') {
      callback(null, ruRU as object);
      return;
    }
    const loader = lazyLocaleLoaders[language as Exclude<SupportedLocale, 'ru-RU'>];
    if (!loader) {
      // Unknown language — let i18next fall back to ru-RU.
      callback(null, false);
      return;
    }
    loader()
      .then((mod) => callback(null, mod.default as object))
      .catch((err) => callback(err as Error, false));
  },
};

void i18n
  .use(lazyResourceBackend)
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    // Only the default locale is preloaded; the rest arrive via the backend.
    resources: {
      'ru-RU': { translation: ruRU },
    },
    fallbackLng: env.defaultLocale,
    supportedLngs: SUPPORTED_LOCALES as unknown as string[],
    nonExplicitSupportedLngs: false,
    // Don't eagerly request fallback resources — the backend handles loading
    // the active locale, and ru-RU is already in memory.
    partialBundledLanguages: true,
    interpolation: { escapeValue: false },
    detection: {
      order: ['localStorage', 'navigator'],
      lookupLocalStorage: 'grading.locale',
      caches: ['localStorage'],
    },
    react: { useSuspense: false },
  });

export default i18n;
