import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '@/shared/lib/cn';
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
          return (
            <button
              key={loc}
              type="button"
              role="tab"
              aria-selected={isActive}
              onClick={() => setActive(loc)}
              className={cn(
                'px-3 py-1 text-xs rounded-md',
                isActive ? 'bg-surface text-text-primary shadow-sm' : 'text-text-secondary hover:text-text-primary',
              )}
            >
              {t(`language.${loc}`)}
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
          className="w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
          data-testid={`locale-input-${loc}`}
        />
      ))}
      {hint ? <p className="text-xs text-text-secondary">{hint}</p> : null}
      {error ? <p className="text-xs text-danger-700" role="alert">{error}</p> : null}
    </div>
  );
}
