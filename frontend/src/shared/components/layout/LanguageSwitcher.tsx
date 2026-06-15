import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Globe, ChevronDown, Check } from 'lucide-react';
import { SUPPORTED_LOCALES, type SupportedLocale } from '@/shared/i18n';
import { setHttpLocale } from '@/shared/api/httpClient';
import { cn } from '@/shared/lib/cn';

const pillLabel: Record<SupportedLocale, string> = {
  'ru-RU': 'RU',
  'uz-Cyrl-UZ': 'УЗ',
  'uz-Latn-UZ': 'UZ',
  'en-US': 'EN',
};

export function LanguageSwitcher() {
  const { t, i18n } = useTranslation();
  const [open, setOpen] = useState(false);
  const current = (i18n.language as SupportedLocale) ?? 'ru-RU';

  const change = (locale: SupportedLocale) => {
    // Non-default locale bundles are code-split and fetched on demand (P1-T1):
    // `changeLanguage` resolves only after i18next's lazy backend has loaded the
    // target locale chunk, so the side effects run inside `.then` once strings
    // are actually available.
    void i18n.changeLanguage(locale).then(() => {
      setHttpLocale(locale);
      document.documentElement.lang = locale;
    });
    setOpen(false);
  };

  return (
    <div className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-label={t('language.title')}
        className={cn(
          'inline-flex items-center gap-1.5 h-9 px-2.5 rounded-md text-sm font-medium',
          'border border-border-strong bg-surface text-text-primary hover:bg-divider',
        )}
      >
        <Globe size={14} aria-hidden />
        <span data-testid="lang-pill">{pillLabel[current] ?? 'RU'}</span>
        <ChevronDown size={14} aria-hidden />
      </button>
      {open ? (
        <ul
          role="listbox"
          className="absolute right-0 mt-2 w-52 bg-surface border border-border rounded-lg shadow-md py-1 z-30"
        >
          {SUPPORTED_LOCALES.map((loc) => (
            <li key={loc}>
              <button
                role="option"
                aria-selected={current === loc}
                onClick={() => change(loc)}
                className={cn(
                  'w-full flex items-center justify-between px-3 py-2 text-sm hover:bg-divider',
                  current === loc ? 'text-primary-600' : 'text-text-primary',
                )}
              >
                <span>{t(`language.${loc}`)}</span>
                {current === loc ? <Check size={14} aria-hidden /> : null}
              </button>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}
