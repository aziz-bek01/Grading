/**
 * Methodology-VERSION multi-select for the EVALUATION_SUMMARY report filter
 * panel (PRD §4.1, decision D1 = version granularity).
 *
 * Reuse anchor (per the dispatch doc): the established pattern is a native
 * `<select>`/checkbox group bound directly to `useMethodologies(projectId)` +
 * `useMethodologyVersions(methodologyId)` — the SAME hooks `OpenPanelDialog`
 * uses for its single-select version `<select>` (lines 582-596). There is no
 * existing standalone `<MethodologySelector>` component to avoid duplicating;
 * the duplication risk called out by the PRD is re-fetching methodologies
 * with a hand-rolled query instead of these hooks, which this component
 * avoids by calling them directly.
 *
 * Each methodology is rendered as a group of its versions ("{name} v{n}"),
 * grouped by parent methodology name per the recommended UX. Selecting zero
 * versions = no narrowing (server-side "all versions" per AC-1.1).
 */
import { useTranslation } from 'react-i18next';
import { useMethodologies, useMethodologyVersions } from '@/features/methodology/hooks/useMethodology';
import { pickLocalized } from '@/shared/lib/localized';
import type { Methodology } from '@/features/methodology/types';

interface Props {
  projectId: string;
  value: string[];
  onChange: (next: string[]) => void;
  disabled?: boolean;
}

function MethodologyVersionGroup({
  methodology,
  selected,
  onToggle,
  disabled,
}: {
  methodology: Methodology;
  selected: Set<string>;
  onToggle: (versionId: string, checked: boolean) => void;
  disabled?: boolean;
}) {
  const { t, i18n } = useTranslation();
  const { data: versions, isLoading, isError } = useMethodologyVersions(methodology.id);
  const name = pickLocalized(methodology.name_i18n, i18n.language, methodology.code);

  if (isLoading) {
    return (
      <p className="text-xs text-text-muted" data-testid={`report-filter-methodology-${methodology.id}-loading`}>
        {name} — {t('common.loading')}
      </p>
    );
  }
  if (isError) {
    return (
      <p
        className="text-xs text-danger-700"
        role="alert"
        data-testid={`report-filter-methodology-${methodology.id}-error`}
      >
        {name} — {t('report.filter.methodology_load_error')}
      </p>
    );
  }
  if (!versions || versions.length === 0) return null;

  return (
    <fieldset className="border border-border rounded-md p-2">
      <legend className="text-xs font-medium text-text-secondary px-1">{name}</legend>
      <div className="grid grid-cols-1 gap-1">
        {versions.map((v) => {
          const checked = selected.has(v.id);
          return (
            <label key={v.id} className="inline-flex items-center gap-2 text-sm text-text-primary">
              <input
                type="checkbox"
                checked={checked}
                disabled={disabled}
                onChange={(e) => onToggle(v.id, e.target.checked)}
                className="h-4 w-4 rounded border-border-strong text-primary-500 focus:ring-primary-500 disabled:opacity-50"
                data-testid={`report-filter-methodology-version-${v.id}`}
              />
              <span>
                {name} v{v.version_number}
              </span>
            </label>
          );
        })}
      </div>
    </fieldset>
  );
}

export function MethodologyVersionMultiSelect({ projectId, value, onChange, disabled }: Props) {
  const { t } = useTranslation();
  const { data, isLoading, isError } = useMethodologies(projectId);
  const methodologies = data?.items ?? [];
  const selected = new Set(value);

  const toggle = (versionId: string, checked: boolean) => {
    const next = new Set(value);
    if (checked) next.add(versionId);
    else next.delete(versionId);
    onChange([...next]);
  };

  if (isLoading) {
    return (
      <p className="text-xs text-text-muted" data-testid="report-filter-methodology-status-loading">
        {t('common.loading')}
      </p>
    );
  }
  if (isError) {
    return (
      <p
        className="text-xs text-danger-700"
        role="alert"
        data-testid="report-filter-methodology-status-error"
      >
        {t('report.filter.methodology_load_error')}
      </p>
    );
  }
  if (methodologies.length === 0) {
    return (
      <p className="text-xs text-text-muted" data-testid="report-filter-methodology-status-empty">
        {t('report.filter.no_methodologies')}
      </p>
    );
  }

  return (
    <div className="space-y-2 max-h-48 overflow-y-auto" role="group">
      {methodologies.map((m) => (
        <MethodologyVersionGroup
          key={m.id}
          methodology={m}
          selected={selected}
          onToggle={toggle}
          disabled={disabled}
        />
      ))}
    </div>
  );
}
