/**
 * Paged table of import-batch row errors with a level filter.
 * Uses lightweight rendering — we already have many DataTable usages but
 * the columns here are domain-specific (errorLevel chip + suggested fix
 * cell), so a flat table keeps the markup readable.
 */
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '@/shared/lib/cn';
import type { ImportError, ImportErrorLevel } from '../types';

const LEVEL_TONE: Record<ImportErrorLevel, string> = {
  BLOCKER: 'bg-danger-50 text-danger-700 border-danger-500/30',
  ERROR: 'bg-danger-50 text-danger-700 border-danger-500/30',
  WARNING: 'bg-warning-50 text-warning-700 border-warning-500/30',
  INFO: 'bg-info-50 text-info-700 border-info-500/30',
};

interface Props {
  errors: ImportError[];
  level?: ImportErrorLevel | undefined;
  onLevelChange?: (level: ImportErrorLevel | undefined) => void;
  loading?: boolean;
}

const ALL_LEVELS: ImportErrorLevel[] = ['BLOCKER', 'ERROR', 'WARNING', 'INFO'];

export function ImportErrorsTable({ errors, level, onLevelChange, loading }: Props) {
  const { t } = useTranslation();
  const [localLevel, setLocalLevel] = useState<ImportErrorLevel | undefined>(level);
  const effectiveLevel = level ?? localLevel;
  const visibleErrors = effectiveLevel
    ? errors.filter((e) => e.errorLevel === effectiveLevel)
    : errors;

  return (
    <div className="space-y-2" data-testid="import-errors-table">
      <div className="flex items-center gap-2 flex-wrap">
        <span className="text-xs text-text-muted">{t('import.errors.filter_label')}</span>
        <button
          type="button"
          onClick={() => {
            setLocalLevel(undefined);
            onLevelChange?.(undefined);
          }}
          className={cn(
            'text-xs px-2 py-0.5 rounded border',
            !effectiveLevel
              ? 'bg-primary-50 text-primary-700 border-primary-500/30'
              : 'bg-surface text-text-secondary border-border hover:bg-divider',
          )}
        >
          {t('common.all')}
        </button>
        {ALL_LEVELS.map((lvl) => (
          <button
            key={lvl}
            type="button"
            onClick={() => {
              setLocalLevel(lvl);
              onLevelChange?.(lvl);
            }}
            className={cn(
              'text-xs px-2 py-0.5 rounded border',
              effectiveLevel === lvl
                ? 'bg-primary-50 text-primary-700 border-primary-500/30'
                : 'bg-surface text-text-secondary border-border hover:bg-divider',
            )}
          >
            {t(`import.error_level.${lvl}`)}
          </button>
        ))}
      </div>

      {loading ? (
        <div className="text-sm text-text-muted py-4">{t('states.loading')}</div>
      ) : visibleErrors.length === 0 ? (
        <div className="text-sm text-text-muted py-4">{t('import.errors.empty')}</div>
      ) : (
        <div className="overflow-x-auto border border-border rounded-md">
          <table className="w-full text-sm">
            <thead className="bg-divider text-text-secondary text-xs uppercase">
              <tr>
                <th scope="col" className="text-left px-3 py-2">{t('import.errors.col_row')}</th>
                <th scope="col" className="text-left px-3 py-2">{t('import.errors.col_level')}</th>
                <th scope="col" className="text-left px-3 py-2">{t('import.errors.col_field')}</th>
                <th scope="col" className="text-left px-3 py-2">{t('import.errors.col_message')}</th>
                <th scope="col" className="text-left px-3 py-2">{t('import.errors.col_fix')}</th>
              </tr>
            </thead>
            <tbody>
              {visibleErrors.map((e) => (
                <tr key={e.id} className="border-t border-border">
                  <td className="px-3 py-2 text-text-muted">{e.rowNumber ?? '—'}</td>
                  <td className="px-3 py-2">
                    <span
                      className={cn(
                        'inline-flex items-center text-xs px-2 py-0.5 rounded border font-medium',
                        LEVEL_TONE[e.errorLevel],
                      )}
                    >
                      {t(`import.error_level.${e.errorLevel}`)}
                    </span>
                  </td>
                  <td className="px-3 py-2 text-text-primary">{e.fieldName ?? '—'}</td>
                  <td className="px-3 py-2 text-text-primary">{e.message}</td>
                  <td className="px-3 py-2 text-text-secondary">{e.suggestedFix ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
