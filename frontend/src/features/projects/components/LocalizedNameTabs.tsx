import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { CheckCircle2, Circle } from 'lucide-react';
import { cn } from '@/shared/lib/cn';
import { fieldA11y, fieldErrorId } from '@/shared/lib/fieldA11y';
import type { Locale, LocalizedString } from '@/shared/types/common';

const LOCALES: Locale[] = ['ru-RU', 'uz-Cyrl-UZ', 'uz-Latn-UZ', 'en-US'];

interface LocalizedNameTabsProps {
  value: LocalizedString;
  onChange: (next: LocalizedString) => void;
  /** Primary locale — required indicator on its tab. */
  primary?: Locale;
  label?: string;
  hint?: string;
  error?: string;
  inputId?: string;
}

/** Multilingual single-line input with one tab per locale. */
export function LocalizedNameTabs({
  value,
  onChange,
  primary = 'ru-RU',
  label,
  hint,
  error,
  inputId,
}: LocalizedNameTabsProps) {
  const { t } = useTranslation();
  const [active, setActive] = useState<Locale>(primary);

  return (
    <div className="space-y-2">
      {label ? (
        <label htmlFor={inputId} className="text-sm font-medium text-text-primary">
          {label} <span className="text-text-muted text-xs">({t('common.required')})</span>
        </label>
      ) : null}
      <div role="tablist" className="inline-flex gap-1 rounded-md bg-divider p-1">
        {LOCALES.map((loc) => {
          const isActive = active === loc;
          const isPrimary = loc === primary;
          // Per-tab fill indicator (FE i18n hardening): lets the editor see at
          // a glance which locales still need translating without switching
          // tabs. Purely a UX affordance — never touches validation.
          const isFilled = (value?.[loc] ?? '').trim().length > 0;
          const localeName = t(`language.${loc}`);
          return (
            <button
              key={loc}
              type="button"
              role="tab"
              aria-selected={isActive}
              onClick={() => setActive(loc)}
              className={cn(
                'inline-flex items-center gap-1 px-3 py-1 text-xs rounded-md',
                isActive ? 'bg-surface text-text-primary shadow-sm' : 'text-text-secondary hover:text-text-primary',
              )}
            >
              <span
                className={isFilled ? 'text-success-600' : 'text-text-muted'}
                data-testid={`locale-tab-indicator-${loc}`}
                data-filled={isFilled || undefined}
              >
                {isFilled ? (
                  <CheckCircle2 size={11} aria-hidden />
                ) : (
                  <Circle size={11} aria-hidden />
                )}
                <span className="sr-only">
                  {isFilled
                    ? t('common.locale_tab_filled', { locale: localeName })
                    : t('common.locale_tab_empty', { locale: localeName })}
                </span>
              </span>
              {localeName}
              {isPrimary ? <span className="ml-1 text-danger-700">*</span> : null}
            </button>
          );
        })}
      </div>
      {LOCALES.map((loc) => (
        <input
          key={loc}
          id={loc === active ? inputId : undefined}
          type="text"
          hidden={loc !== active}
          value={value?.[loc] ?? ''}
          onChange={(e) => onChange({ ...value, [loc]: e.target.value })}
          placeholder={t('common.name')}
          {...(inputId ? fieldA11y(inputId, !!error) : {})}
          className="w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
          data-testid={`locale-input-${loc}`}
        />
      ))}
      {hint ? <p className="text-xs text-text-secondary">{hint}</p> : null}
      {error ? (
        <p
          id={inputId ? fieldErrorId(inputId) : undefined}
          className="text-xs text-danger-700"
          role="alert"
        >
          {error}
        </p>
      ) : null}
    </div>
  );
}
