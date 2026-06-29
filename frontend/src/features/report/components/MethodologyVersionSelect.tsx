/**
 * Methodology-VERSION SINGLE-select for the evaluation report filter panel
 * (EVALUATION_SUMMARY / EXECUTIVE_SUMMARY).
 *
 * WHY single, mandatory (was a multi-select checkbox group): each methodology
 * version defines its OWN factor set, so a report must be scoped to exactly
 * ONE methodology version — otherwise the factor columns would be the UNION
 * across versions (mixing factors that belong to different methodologies),
 * which is meaningless on the page. The caller enforces "exactly one" via the
 * Zod schema; this control simply guarantees at most one version can be picked
 * at a time (native radio group across every methodology's versions).
 *
 * Reuse anchor: same `useMethodologies(projectId)` + `useMethodologyVersions(
 * methodologyId)` hooks the previous multi-select and `OpenPanelDialog` use —
 * no hand-rolled methodology query.
 *
 * Each methodology is rendered as a group of its versions ("{name} v{n}"),
 * grouped by parent methodology name. The radio inputs share ONE `name` so
 * selecting a version in any group deselects every other — true single-select
 * across the whole panel.
 */
import { useTranslation } from 'react-i18next';
import { useMethodologies, useMethodologyVersions } from '@/features/methodology/hooks/useMethodology';
import { pickLocalized } from '@/shared/lib/localized';
import type { Methodology } from '@/features/methodology/types';

const RADIO_GROUP_NAME = 'report-filter-methodology-version';

interface Props {
  projectId: string;
  /** The selected methodology version id, or null when none is chosen yet. */
  value: string | null;
  onChange: (next: string | null) => void;
  disabled?: boolean;
}

function MethodologyVersionGroup({
  methodology,
  selectedId,
  onSelect,
  disabled,
}: {
  methodology: Methodology;
  selectedId: string | null;
  onSelect: (versionId: string) => void;
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
          const checked = selectedId === v.id;
          return (
            <label key={v.id} className="inline-flex items-center gap-2 text-sm text-text-primary">
              <input
                type="radio"
                name={RADIO_GROUP_NAME}
                checked={checked}
                disabled={disabled}
                onChange={() => onSelect(v.id)}
                className="h-4 w-4 border-border-strong text-primary-500 focus:ring-primary-500 disabled:opacity-50"
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

export function MethodologyVersionSelect({ projectId, value, onChange, disabled }: Props) {
  const { t } = useTranslation();
  const { data, isLoading, isError } = useMethodologies(projectId);
  const methodologies = data?.items ?? [];

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
    <div className="space-y-2 max-h-48 overflow-y-auto" role="radiogroup">
      {methodologies.map((m) => (
        <MethodologyVersionGroup
          key={m.id}
          methodology={m}
          selectedId={value}
          onSelect={(id) => onChange(id)}
          disabled={disabled}
        />
      ))}
    </div>
  );
}
