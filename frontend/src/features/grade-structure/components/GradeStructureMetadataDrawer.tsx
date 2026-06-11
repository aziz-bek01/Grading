import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { DrawerForm } from '@/shared/components/data-table/DrawerForm';
import { LocalizedNameTabs } from '@/features/projects/components/LocalizedNameTabs';
import { ApiError } from '@/shared/api/apiError';
import type { LocalizedString } from '@/shared/types/common';
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
 * `code` is immutable post-create — shown read-only, never PATCHed.
 * Reuses DrawerForm + LocalizedNameTabs (no new primitives).
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
  const [name, setName] = useState<LocalizedString>(structure.name_i18n ?? {});
  const [description, setDescription] = useState<LocalizedString>(
    structure.description_i18n ?? {},
  );
  const [gapPolicy, setGapPolicy] = useState<GradeGapPolicy>(
    structure.gap_policy ?? 'STRICT_NO_GAPS',
  );
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const gapEditable = !!editable && structure.status === 'DRAFT';

  const handleSubmit = async () => {
    // Accept a name in ANY supported locale (locale-trap fix, mirrors methodology).
    if (!Object.values(name).some((v) => v?.trim())) {
      setError(t('gradeStructure.metadata.name_required'));
      return;
    }
    // `code` is intentionally excluded — immutable container code.
    const patch: UpdateMetadataPayload = {
      name_i18n: name,
      description_i18n: description,
    };
    if (gapEditable) patch.gap_policy = gapPolicy;

    setError(null);
    setSubmitting(true);
    try {
      await onSubmit(patch);
    } catch (e) {
      if (
        e instanceof ApiError &&
        e.code === 'GRADE_STRUCTURE_TRANSITION_REJECTED'
      ) {
        setError(t('gradeStructure.metadata.not_editable'));
      } else {
        setError(t('gradeStructure.metadata.save_failed'));
      }
      return;
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <DrawerForm
      open
      title={t('gradeStructure.metadata.edit_title')}
      subtitle={t('gradeStructure.metadata.edit_subtitle')}
      onClose={onClose}
      submitLabel={t('common.save')}
      onSubmit={() => {
        if (!submitting) void handleSubmit();
      }}
    >
      <div className="space-y-4">
        <label className="block text-sm font-medium text-text-primary">
          <span>{t('common.code')}</span>
          <input
            type="text"
            value={structure.code}
            readOnly
            disabled
            className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-divider text-text-secondary font-mono cursor-not-allowed"
            data-testid="grade-metadata-code"
          />
          <span className="mt-1 block text-xs text-text-muted">
            {t('gradeStructure.metadata.code_immutable')}
          </span>
        </label>

        <LocalizedNameTabs value={name} onChange={setName} label={t('common.name')} />

        <LocalizedNameTabs
          value={description}
          onChange={setDescription}
          label={t('common.description')}
        />

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

        {error ? (
          <p
            className="text-xs text-danger-700"
            role="alert"
            data-testid="grade-metadata-error"
          >
            {error}
          </p>
        ) : null}
      </div>
    </DrawerForm>
  );
}
