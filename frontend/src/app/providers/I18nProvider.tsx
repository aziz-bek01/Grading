import { useEffect, type ReactNode } from 'react';
import { I18nextProvider, useTranslation } from 'react-i18next';
import i18n from '@/shared/i18n';
import { setHttpLocale } from '@/shared/api/httpClient';

function LocaleSync() {
  const { i18n: instance } = useTranslation();
  useEffect(() => {
    document.documentElement.lang = instance.language;
    setHttpLocale(instance.language);
    const onChange = (lng: string) => {
      document.documentElement.lang = lng;
      setHttpLocale(lng);
    };
    instance.on('languageChanged', onChange);
    return () => {
      instance.off('languageChanged', onChange);
    };
  }, [instance]);
  return null;
}

export function I18nProvider({ children }: { children: ReactNode }) {
  return (
    <I18nextProvider i18n={i18n}>
      <LocaleSync />
      {children}
    </I18nextProvider>
  );
}
