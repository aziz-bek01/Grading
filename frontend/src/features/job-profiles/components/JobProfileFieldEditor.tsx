import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '@/shared/lib/cn';
import type { Locale, LocalizedString } from '@/shared/types/common';

const LOCALES: Locale[] = ['ru-RU', 'uz-Cyrl-UZ', 'uz-Latn-UZ', 'en-US'];

interface JobProfileFieldEditorProps {
  fieldKey: string;
  label: string;
  hint?: string;
  value: LocalizedString;
  onChange: (next: LocalizedString) => void;
  primary?: Locale;
  required?: boolean;
  readOnly?: boolean;
  error?: string;
  rows?: number;
}

/**
 * Long-text multilingual field with 4 locale tabs.
 * Reuses the LocalizedNameTabs pattern from features/projects but renders a
 * textarea per locale (design-foundation §11 / §16 — Uzbek length budget,
 * read-only when approved/archived).
 */
export function JobProfileFieldEditor({
  fieldKey,
  label,
  hint,
  value,
  onChange,
  primary = 'ru-RU',
  required = true,
  readOnly = false,
  error,
  rows = 4,
}: JobProfileFieldEditorProps) {
  const { t } = useTranslation();
  const [active, setActive] = useState<Locale>(primary);

  return (
    <div className="space-y-2" data-testid={`field-${fieldKey}`}>
      <label className="text-sm font-medium text-text-primary">
        {label}
        {required ? <span className="text-danger-700 ml-1">*</span> : null}
      </label>
      <div role="tablist" className="inline-flex gap-1 rounded-md bg-divider p-1">
        {LOCALES.map((loc) => {
          const isActive = active === loc;
          const isPrimary = loc === primary;
          const isFilled = value?.[loc] && value[loc]!.trim().length > 0;
          return (
            <button
              key={loc}
              type="button"
              role="tab"
              aria-selected={isActive}
              onClick={() => setActive(loc)}
              className={cn(
                'px-3 py-1 text-xs rounded-md inline-flex items-center gap-1',
                isActive
                  ? 'bg-surface text-text-primary shadow-sm'
                  : 'text-text-secondary hover:text-text-primary',
              )}
              data-testid={`tab-${fieldKey}-${loc}`}
            >
              {t(`language.${loc}`)}
              {isPrimary ? <span className="text-danger-700">*</span> : null}
              {isFilled ? (
                <span className="w-1.5 h-1.5 rounded-full bg-success-500" aria-hidden />
              ) : null}
            </button>
          );
        })}
      </div>

      {LOCALES.map((loc) =>
        readOnly ? (
          loc === active ? (
            <div
              key={loc}
              className="w-full min-h-[88px] px-3 py-2 border border-border rounded-md text-sm bg-locked-bg text-text-primary whitespace-pre-wrap"
              data-testid={`readonly-${fieldKey}-${loc}`}
            >
              {value?.[loc] ?? <span className="text-text-muted">{t('common.none')}</span>}
            </div>
          ) : null
        ) : (
          <textarea
            key={loc}
            hidden={loc !== active}
            value={value?.[loc] ?? ''}
            onChange={(e) => onChange({ ...value, [loc]: e.target.value })}
            placeholder={t('jobProfile.placeholder_textarea')}
            rows={rows}
            className="w-full px-3 py-2 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
            data-testid={`textarea-${fieldKey}-${loc}`}
          />
        ),
      )}

      {hint ? <p className="text-xs text-text-secondary">{hint}</p> : null}
      {error ? (
        <p className="text-xs text-danger-700" role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
}
