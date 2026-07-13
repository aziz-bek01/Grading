import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { TemplateMetadataDrawer } from '@/shared/components/template-management/TemplateMetadataDrawer';
import { ApiError } from '@/shared/api/apiError';
import type {
  GradeGapPolicy,
  GradeStructure,
  GradeStructureListItem,
  UpdateMetadataPayload,
} from '../types';

interface GradeStructureMetadataDrawerProps {
  open: boolean;
  structure: GradeStructure | GradeStructureListItem | null;
  /**
   * Builder DRAFT mode: when true (and the structure is DRAFT) the gap_policy
   * field is shown and included in the patch. The list page never sets this, so
   * it keeps a rename/description-only shape. The backend independently enforces
   * DRAFT-only.
   */
  editable?: boolean;
  onClose: () => void;
  onSubmit: (patch: UpdateMetadataPayload) => void | Promise<void>;
}

const GAP_POLICY_OPTIONS: { value: GradeGapPolicy; labelKey: string }[] = [
  { value: 'STRICT_NO_GAPS', labelKey: 'gradeStructure.gap_policy.strict_no_gaps' },
  { value: 'ALLOW_GAPS_WARN', labelKey: 'gradeStructure.gap_policy.allow_gaps_warn' },
];

/**
 * Drawer for editing a grade structure's METADATA.
 *
 * Two modes share ONE component (mirrors MethodologyMetadataDrawer):
 *  - List page: rename / description only (`editable` unset).
 *  - Builder (DRAFT only): additionally edit gap_policy (`editable` true).
 *
 * `code` is immutable post-create — shown read-only, never PATCHed. Thin
 * wrapper around the shared entity-agnostic `TemplateMetadataDrawer`; the
 * gap_policy field is this entity's only extra-fields slot.
 */
export function GradeStructureMetadataDrawer({
  open,
  structure,
  ...rest
}: GradeStructureMetadataDrawerProps) {
  // Keyed remount per structure so the body seeds straight from props.
  if (!open || !structure) return null;
  return (
    <GradeStructureMetadataDrawerBody key={structure.id} structure={structure} {...rest} />
  );
}

function GradeStructureMetadataDrawerBody({
  structure,
  editable,
  onClose,
  onSubmit,
}: Omit<GradeStructureMetadataDrawerProps, 'open'> & {
  structure: GradeStructure | GradeStructureListItem;
}) {
  const { t } = useTranslation();
  const gapEditable = !!editable && structure.status === 'DRAFT';
  const [gapPolicy, setGapPolicy] = useState<GradeGapPolicy>(
    structure.gap_policy ?? 'STRICT_NO_GAPS',
  );

  return (
    <TemplateMetadataDrawer<UpdateMetadataPayload>
      title={t('gradeStructure.metadata.edit_title')}
      subtitle={t('gradeStructure.metadata.edit_subtitle')}
      code={structure.code}
      codeImmutableHint={t('gradeStructure.metadata.code_immutable')}
      initialName={structure.name_i18n ?? {}}
      initialDescription={structure.description_i18n ?? {}}
      // Accept a name in ANY supported locale (locale-trap fix, mirrors methodology).
      validateName={(name) => Object.values(name).some((v) => v?.trim())}
      nameRequiredError={t('gradeStructure.metadata.name_required')}
      buildPatch={({ name_i18n, description_i18n }) => {
        // `code` is intentionally excluded — immutable container code.
        const patch: UpdateMetadataPayload = { name_i18n, description_i18n };
        if (gapEditable) patch.gap_policy = gapPolicy;
        return { ok: true, patch };
      }}
      mapError={(e) =>
        e instanceof ApiError && e.code === 'GRADE_STRUCTURE_TRANSITION_REJECTED'
          ? t('gradeStructure.metadata.not_editable')
          : t('gradeStructure.metadata.save_failed')
      }
      onClose={onClose}
      onSubmit={onSubmit}
      testIdPrefix="grade-metadata"
    >
      {gapEditable ? (
        <label className="block text-sm font-medium text-text-primary">
          <span>{t('gradeStructure.gap_policy_label')}</span>
          <select
            value={gapPolicy}
            onChange={(e) => setGapPolicy(e.target.value as GradeGapPolicy)}
            className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
            data-testid="grade-metadata-gap-policy"
          >
            {GAP_POLICY_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {t(opt.labelKey)}
              </option>
            ))}
          </select>
        </label>
      ) : null}
    </TemplateMetadataDrawer>
  );
}
