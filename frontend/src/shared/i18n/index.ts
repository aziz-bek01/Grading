import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import ruRU from './locales/ru-RU.json';
import enUS from './locales/en-US.json';
import uzCyrl from './locales/uz-Cyrl-UZ.json';
import uzLatn from './locales/uz-Latn-UZ.json';
import { env } from '@/shared/config/env';

export const SUPPORTED_LOCALES = ['ru-RU', 'uz-Cyrl-UZ', 'uz-Latn-UZ', 'en-US'] as const;
export type SupportedLocale = (typeof SUPPORTED_LOCALES)[number];

void i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      'ru-RU': { translation: ruRU },
      'uz-Cyrl-UZ': { translation: uzCyrl },
      'uz-Latn-UZ': { translation: uzLatn },
      'en-US': { translation: enUS },
    },
    fallbackLng: env.defaultLocale,
    supportedLngs: SUPPORTED_LOCALES as unknown as string[],
    nonExplicitSupportedLngs: false,
    interpolation: { escapeValue: false },
    detection: {
      order: ['localStorage', 'navigator'],
      lookupLocalStorage: 'grading.locale',
      caches: ['localStorage'],
    },
    react: { useSuspense: false },
  });

export default i18n;
