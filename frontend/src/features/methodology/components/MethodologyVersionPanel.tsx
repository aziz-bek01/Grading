import { useTranslation } from 'react-i18next';
import { Card } from '@/shared/components/ui/Card';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { MethodologyStatusBadge } from './MethodologyStatusBadge';
import type { MethodologyVersionSummary } from '../types';

interface MethodologyVersionPanelProps {
  versions: MethodologyVersionSummary[] | undefined;
  loading?: boolean;
  activeVersionId: string;
  onSelect: (versionId: string) => void;
}

/**
 * Collapsible side panel listing all versions of a methodology.
 * "Compare versions" is a placeholder per design-foundation §13 (reserved for MVP 2).
 */
export function MethodologyVersionPanel({
  versions,
  loading,
  activeVersionId,
  onSelect,
}: MethodologyVersionPanelProps) {
  const { t } = useTranslation();
  return (
    <Card compact title={t('methodology.version_panel_title')}>
      {loading ? (
        <LoadingState />
      ) : !versions || versions.length === 0 ? (
        <p className="text-sm text-text-secondary">{t('methodology.no_versions')}</p>
      ) : (
        <ul className="space-y-2" data-testid="version-list">
          {versions
            .slice()
            .sort((a, b) => b.version_number - a.version_number)
            .map((v) => {
              const active = v.id === activeVersionId;
              return (
                <li key={v.id}>
                  <button
                    type="button"
                    onClick={() => onSelect(v.id)}
                    aria-current={active ? 'true' : undefined}
                    data-testid={`version-row-${v.version_number}`}
                    className={
                      active
                        ? 'w-full text-left rounded-md border border-primary-500 bg-primary-50 p-2 flex items-center justify-between gap-2'
                        : 'w-full text-left rounded-md border border-border bg-surface p-2 hover:bg-divider flex items-center justify-between gap-2'
                    }
                  >
                    <span className="text-sm font-medium text-text-primary">
                      {t('methodology.version_label', { number: v.version_number })}
                    </span>
                    <MethodologyStatusBadge status={v.status} />
                  </button>
                </li>
              );
            })}
        </ul>
      )}
      <p className="text-xs text-text-muted mt-3" aria-disabled="true">
        {t('methodology.compare_versions_soon')}
      </p>
    </Card>
  );
}
