import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { DrawerForm } from '@/shared/components/data-table/DrawerForm';
import { LocalizedNameTabs } from '@/features/projects/components/LocalizedNameTabs';
import { ApiError } from '@/shared/api/apiError';
import type { LocalizedString } from '@/shared/types/common';
import type {
  Methodology,
  MethodologyType,
  MethodologyUpdatePayload,
  MethodologyVersion,
  MethodologyVersionMetadataUpdatePayload,
  ScoringMode,
} from '../types';

/**
 * Combined patch the drawer emits. The container fields (name/description/type)
 * map to `PATCH /methodologies/{id}`; the version fields (scoring_mode/target)
 * map to `PATCH /methodology-versions/{id}`. The caller sequences both PATCHes.
 */
export interface MethodologyMetadataPatch {
  methodology: MethodologyUpdatePayload;
  version?: MethodologyVersionMetadataUpdatePayload;
}

interface MethodologyMetadataDrawerProps {
  open: boolean;
  methodology: Methodology | null;
  /**
   * Builder mode only: the DRAFT version whose scoring metadata can be edited.
   * Omitted (list-page mode) → name/description-only behavior, unchanged.
   */
  version?: MethodologyVersion | null;
  /**
   * Builder mode only: when true (and `version` is DRAFT) the type/scoring/target
   * fields are shown. The list page never sets this, so it keeps its old shape.
   */
  editable?: boolean;
  onClose: () => void;
  onSubmit: (patch: MethodologyMetadataPatch) => void | Promise<void>;
}

const TYPE_OPTIONS: { value: MethodologyType; labelKey: string }[] = [
  { value: 'CLASSIC_8_FACTOR', labelKey: 'methodology.type.classic_8_factor' },
  { value: 'EXTENDED_11_CRITERIA', labelKey: 'methodology.type.extended_11_criteria' },
  { value: 'CUSTOM', labelKey: 'methodology.type.custom' },
];

const SCORING_OPTIONS: { value: ScoringMode; labelKey: string }[] = [
  { value: 'WEIGHTED_POINTS', labelKey: 'methodology.scoring_mode_label.weighted_points' },
  { value: 'DIRECT_POINTS', labelKey: 'methodology.scoring_mode_label.direct_points' },
  { value: 'WEIGHTED_SCALE', labelKey: 'methodology.scoring_mode_label.weighted_scale' },
];

/**
 * Drawer for editing a methodology's METADATA.
 *
 * Two modes share ONE component (no duplication):
 *  - List page: rename / description only (`version`/`editable` unset).
 *  - Builder (DRAFT only): additionally edit methodology_type (container) +
 *    scoring_mode / target_total_points (version). APPROVED/LOCKED versions never
 *    pass `editable` so the scoring fields stay hidden; the backend independently
 *    enforces DRAFT-only.
 *
 * The container `code` is immutable post-create — shown read-only, never PATCHed.
 * Reuses DrawerForm + LocalizedNameTabs (no new primitives).
 */
export function MethodologyMetadataDrawer({ open, methodology, ...rest }: MethodologyMetadataDrawerProps) {
  // Keyed remount per (methodology, version) so the body seeds straight from props.
  if (!open || !methodology) return null;
  return (
    <MethodologyMetadataDrawerBody
      key={`${methodology.id}:${rest.version?.id ?? 'none'}`}
      methodology={methodology}
      {...rest}
    />
  );
}

function MethodologyMetadataDrawerBody({
  methodology,
  version,
  editable,
  onClose,
  onSubmit,
}: Omit<MethodologyMetadataDrawerProps, 'open'> & { methodology: Methodology }) {
  const { t } = useTranslation();
  const [name, setName] = useState<LocalizedString>(methodology.name_i18n ?? {});
  const [description, setDescription] = useState<LocalizedString>(methodology.description_i18n ?? {});
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Scoring fields only ever editable in builder mode against a DRAFT version.
  const scoringEditable = !!editable && version?.status === 'DRAFT';
  const [methodologyType, setMethodologyType] = useState<MethodologyType>(
    methodology.methodology_type,
  );
  const originalScoringMode = version?.scoring_mode ?? 'WEIGHTED_POINTS';
  const [scoringMode, setScoringMode] = useState<ScoringMode>(originalScoringMode);
  const [targetTotalPoints, setTargetTotalPoints] = useState(
    version?.target_total_points != null ? String(version.target_total_points) : '1000',
  );

  const scoringModeChanged = scoringEditable && scoringMode !== originalScoringMode;

  const handleSubmit = async () => {
    // Accept a name in ANY supported locale — hard-requiring ru-RU trapped
    // users working in the uz/en tab (same locale-trap fixed in FactorEditor /
    // FactorLevelEditor). The backend only validates locale KEYS.
    if (!Object.values(name).some((v) => v?.trim())) {
      setError(t('methodology.metadata.name_primary_required'));
      return;
    }

    // Container patch — `code` is intentionally excluded (immutable container).
    const methodologyPatch: MethodologyUpdatePayload = {
      name_i18n: name,
      description_i18n: description,
    };
    let versionPatch: MethodologyVersionMetadataUpdatePayload | undefined;

    if (scoringEditable) {
      methodologyPatch.methodology_type = methodologyType;
      versionPatch = { scoring_mode: scoringMode };
      if (scoringMode === 'WEIGHTED_SCALE') {
        const tt = Number.parseFloat(targetTotalPoints);
        if (Number.isNaN(tt) || tt <= 0) {
          setError(t('methodology.create.target_total_invalid'));
          return;
        }
        versionPatch.target_total_points = tt;
      }
    }

    setError(null);
    setSubmitting(true);
    try {
      await onSubmit({ methodology: methodologyPatch, version: versionPatch });
    } catch (e) {
      // Surface known backend conflict codes inline; fall back to a generic msg.
      if (e instanceof ApiError) {
        if (e.code === 'METHODOLOGY_TYPE_LOCKED') {
          setError(t('methodology.metadata.type_locked'));
        } else if (e.code === 'SCORING_TARGET_REQUIRED') {
          setError(t('methodology.create.target_total_invalid'));
        } else if (e.code === 'METHODOLOGY_VERSION_TRANSITION_REJECTED') {
          setError(t('methodology.metadata.version_not_editable'));
        } else {
          setError(t('methodology.metadata.save_failed'));
        }
      } else {
        setError(t('methodology.metadata.save_failed'));
      }
      return;
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <DrawerForm
      open
      title={t('methodology.metadata.edit_title')}
      subtitle={t('methodology.metadata.edit_subtitle')}
      onClose={onClose}
      onSubmit={() => {
        if (!submitting) void handleSubmit();
      }}
    >
      <div className="space-y-4">
        <label className="block text-sm font-medium text-text-primary">
          <span>{t('common.code')}</span>
          <input
            type="text"
            value={methodology.code}
            readOnly
            disabled
            className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-divider text-text-secondary font-mono cursor-not-allowed"
            data-testid="metadata-code"
          />
          <span className="mt-1 block text-xs text-text-muted">
            {t('methodology.metadata.code_immutable')}
          </span>
        </label>

        <LocalizedNameTabs value={name} onChange={setName} label={t('common.name')} />

        <LocalizedNameTabs
          value={description}
          onChange={setDescription}
          label={t('common.description')}
        />

        {scoringEditable ? (
          <>
            <label className="block text-sm font-medium text-text-primary">
              <span>{t('common.type')}</span>
              <select
                value={methodologyType}
                onChange={(e) => setMethodologyType(e.target.value as MethodologyType)}
                className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
                data-testid="metadata-type"
              >
                {TYPE_OPTIONS.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {t(opt.labelKey)}
                  </option>
                ))}
              </select>
            </label>

            <label className="block text-sm font-medium text-text-primary">
              <span>{t('methodology.scoring_mode')}</span>
              <select
                value={scoringMode}
                onChange={(e) => setScoringMode(e.target.value as ScoringMode)}
                className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
                data-testid="metadata-scoring-mode"
              >
                {SCORING_OPTIONS.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {t(opt.labelKey)}
                  </option>
                ))}
              </select>
            </label>

            {scoringModeChanged ? (
              <p
                className="rounded-md border border-warning-500/30 bg-warning-50 px-3 py-2 text-xs text-warning-700"
                role="status"
                data-testid="metadata-scoring-change-warning"
              >
                {t('methodology.metadata.scoring_mode_change_warning')}
              </p>
            ) : null}

            {scoringMode === 'WEIGHTED_SCALE' ? (
              <label className="block text-sm font-medium text-text-primary">
                <span>{t('methodology.create.target_total_points')}</span>
                <input
                  type="number"
                  step="any"
                  min="1"
                  value={targetTotalPoints}
                  onChange={(e) => setTargetTotalPoints(e.target.value)}
                  className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500 tabular-nums"
                  data-testid="metadata-target-total"
                />
              </label>
            ) : null}
          </>
        ) : null}

        {error ? (
          <p className="text-xs text-danger-700" role="alert" data-testid="metadata-error">
            {error}
          </p>
        ) : null}
      </div>
    </DrawerForm>
  );
}
