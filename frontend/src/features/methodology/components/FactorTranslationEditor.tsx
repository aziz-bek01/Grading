import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Card } from '@/shared/components/ui/Card';
import { SUPPORTED_LOCALES } from '@/shared/types/i18n';
import { cn } from '@/shared/lib/cn';
import type { Locale, LocalizedString } from '@/shared/types/common';
import type { Factor } from '../types';

interface FactorTranslationEditorProps {
  factors: Factor[];
  readOnly?: boolean;
  /** Called when a name cell is edited — page wires this to updateFactor. */
  onChangeName?: (factorId: string, locale: Locale, value: string) => void;
}

/**
 * Full-screen matrix: rows = factors, cols = 4 locales.
 * Shows completeness % per locale at the top, and per-locale per-factor
 * fill state per cell. Used for bulk translation work (design-foundation §13 spec).
 */
export function FactorTranslationEditor({
  factors,
  readOnly,
  onChangeName,
}: FactorTranslationEditorProps) {
  const { t } = useTranslation();

  const completeness = useMemo(() => {
    const map: Record<Locale, number> = { 'ru-RU': 0, 'uz-Cyrl-UZ': 0, 'uz-Latn-UZ': 0, 'en-US': 0 };
    if (factors.length === 0) return map;
    for (const loc of SUPPORTED_LOCALES) {
      const filled = factors.filter((f) => {
        const v = (f.name_i18n as LocalizedString)[loc];
        return typeof v === 'string' && v.trim().length > 0;
      }).length;
      map[loc] = Math.round((filled / factors.length) * 100);
    }
    return map;
  }, [factors]);

  return (
    <Card title={t('methodology.translation_matrix_title')} subtitle={t('methodology.translation_matrix_subtitle')}>
      <div className="grid grid-cols-4 gap-3 mb-4" data-testid="translation-completeness-row">
        {SUPPORTED_LOCALES.map((loc) => {
          const pct = completeness[loc];
          const at100 = pct === 100;
          return (
            <div
              key={loc}
              data-testid={`completeness-${loc}`}
              className={cn(
                'rounded-md border p-3',
                at100 ? 'bg-success-50 border-success-500/30' : 'bg-warning-50 border-warning-500/30',
              )}
            >
              <p className="text-xs text-text-secondary uppercase tracking-wide">
                {t(`language.${loc}`)}
              </p>
              <p className="text-lg font-semibold tabular-nums">
                {pct}%
              </p>
            </div>
          );
        })}
      </div>

      <div className="overflow-x-auto border border-border rounded-lg">
        <table className="w-full text-sm" data-testid="translation-matrix">
          <caption className="sr-only">{t('methodology.translation_matrix_title')}</caption>
          <thead className="bg-divider text-text-secondary text-xs uppercase tracking-wide">
            <tr>
              <th scope="col" className="text-left px-3 py-2 font-medium w-32">
                {t('factor.column.code')}
              </th>
              {SUPPORTED_LOCALES.map((loc) => (
                <th key={loc} scope="col" className="text-left px-3 py-2 font-medium">
                  {t(`language.${loc}`)}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {factors.length === 0 ? (
              <tr>
                <td colSpan={5} className="p-6 text-center text-text-secondary">
                  {t('methodology.no_factors')}
                </td>
              </tr>
            ) : (
              factors
                .slice()
                .sort((a, b) => a.sort_order - b.sort_order)
                .map((f) => (
                  <tr key={f.id} className="border-t border-border" data-testid={`translation-row-${f.code}`}>
                    <td className="px-3 py-2 font-mono text-xs text-text-secondary">{f.code}</td>
                    {SUPPORTED_LOCALES.map((loc) => {
                      const value = f.name_i18n?.[loc] ?? '';
                      return (
                        <td key={loc} className="px-2 py-1.5">
                          {readOnly ? (
                            <span
                              className="block min-h-[36px] px-2 py-2 rounded-md bg-locked-bg border border-border text-sm"
                              data-testid={`translation-readonly-${f.code}-${loc}`}
                            >
                              {value || <span className="text-text-muted">{t('common.none')}</span>}
                            </span>
                          ) : (
                            <input
                              type="text"
                              value={value}
                              onChange={(e) => onChangeName?.(f.id, loc, e.target.value)}
                              className="block w-full h-9 px-2 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
                              data-testid={`translation-cell-${f.code}-${loc}`}
                            />
                          )}
                        </td>
                      );
                    })}
                  </tr>
                ))
            )}
          </tbody>
        </table>
      </div>
    </Card>
  );
}
