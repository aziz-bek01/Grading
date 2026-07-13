import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { TemplateMetadataDrawer } from '@/shared/components/template-management/TemplateMetadataDrawer';
import { ApiError } from '@/shared/api/apiError';
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
 * The container `code` is immutable post-create — shown read-only, never
 * PATCHed. Thin wrapper around the shared entity-agnostic
 * `TemplateMetadataDrawer`; the type/scoring/target fields are this entity's
 * extra-fields slot.
 */
export function MethodologyMetadataDrawer({
  open,
  methodology,
  ...rest
}: MethodologyMetadataDrawerProps) {
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

  return (
    <TemplateMetadataDrawer<MethodologyMetadataPatch>
      title={t('methodology.metadata.edit_title')}
      subtitle={t('methodology.metadata.edit_subtitle')}
      code={methodology.code}
      codeImmutableHint={t('methodology.metadata.code_immutable')}
      initialName={methodology.name_i18n ?? {}}
      initialDescription={methodology.description_i18n ?? {}}
      // Accept a name in ANY supported locale — hard-requiring ru-RU trapped
      // users working in the uz/en tab (same locale-trap fixed in FactorEditor /
      // FactorLevelEditor). The backend only validates locale KEYS.
      validateName={(name) => Object.values(name).some((v) => v?.trim())}
      nameRequiredError={t('methodology.metadata.name_primary_required')}
      buildPatch={({ name_i18n, description_i18n }) => {
        // Container patch — `code` is intentionally excluded (immutable container).
        const methodologyPatch: MethodologyUpdatePayload = { name_i18n, description_i18n };
        let versionPatch: MethodologyVersionMetadataUpdatePayload | undefined;

        if (scoringEditable) {
          methodologyPatch.methodology_type = methodologyType;
          versionPatch = { scoring_mode: scoringMode };
          if (scoringMode === 'WEIGHTED_SCALE') {
            const tt = Number.parseFloat(targetTotalPoints);
            if (Number.isNaN(tt) || tt <= 0) {
              return { ok: false, error: t('methodology.create.target_total_invalid') };
            }
            versionPatch.target_total_points = tt;
          }
        }
        return { ok: true, patch: { methodology: methodologyPatch, version: versionPatch } };
      }}
      mapError={(e) => {
        // Surface known backend conflict codes inline; fall back to a generic msg.
        if (e instanceof ApiError) {
          if (e.code === 'METHODOLOGY_TYPE_LOCKED') {
            return t('methodology.metadata.type_locked');
          }
          if (e.code === 'SCORING_TARGET_REQUIRED') {
            return t('methodology.create.target_total_invalid');
          }
          if (e.code === 'METHODOLOGY_VERSION_TRANSITION_REJECTED') {
            return t('methodology.metadata.version_not_editable');
          }
        }
        return t('methodology.metadata.save_failed');
      }}
      onClose={onClose}
      onSubmit={onSubmit}
      testIdPrefix="metadata"
    >
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
    </TemplateMetadataDrawer>
  );
}
