import { useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { DrawerForm } from '@/shared/components/data-table/DrawerForm';
import { LocalizedNameTabs } from '@/features/projects/components/LocalizedNameTabs';
import type { LocalizedString } from '@/shared/types/common';

/** Result of {@link TemplateMetadataDrawerProps.buildPatch}. */
export type MetadataPatchResult<TPatch> =
  | { ok: true; patch: TPatch }
  | { ok: false; error: string };

export interface TemplateMetadataDrawerProps<TPatch> {
  title: string;
  subtitle: string;
  /** The entity's immutable container code, shown read-only. */
  code: string;
  codeImmutableHint: string;
  initialName: LocalizedString;
  initialDescription: LocalizedString;
  /** True when `name` satisfies the entity's "name required" rule. The two
   *  features genuinely disagree here (grade-structure accepts a name in ANY
   *  locale; methodology requires the ru-RU primary locale specifically). */
  validateName: (name: LocalizedString) => boolean;
  nameRequiredError: string;
  /**
   * Assemble the final wire patch from the (already-validated) name/description,
   * running any entity-specific extra-field validation. Return `{ ok: false }`
   * to block the submit and surface `error` inline instead of calling `onSubmit`.
   */
  buildPatch: (base: {
    name_i18n: LocalizedString;
    description_i18n: LocalizedString;
  }) => MetadataPatchResult<TPatch>;
  /** Map a thrown error (usually `ApiError`) to a translated inline message. */
  mapError: (error: unknown) => string;
  onClose: () => void;
  onSubmit: (patch: TPatch) => void | Promise<void>;
  /** Entity-specific extra fields (e.g. gap_policy, methodology type/scoring),
   *  rendered between the description tabs and the inline error. */
  children?: ReactNode;
  /** testid namespace — e.g. `grade-metadata` / `metadata`. Builds
   *  `${prefix}-code` and `${prefix}-error`. */
  testIdPrefix: string;
}

/**
 * Drawer for editing an entity's METADATA (name/description + a read-only
 * immutable code + any entity-specific extra fields) — the shared shell
 * behind `GradeStructureMetadataDrawer` and `MethodologyMetadataDrawer`.
 *
 * Owns name/description state, the name-required validation, and submit
 * orchestration (build → submit → catch/map error). Entity-specific fields
 * (and their own local state/validation) are supplied by the caller via
 * `children` + `buildPatch`, keeping this shell fully entity-agnostic.
 *
 * Reuses DrawerForm + LocalizedNameTabs — no new primitives.
 */
export function TemplateMetadataDrawer<TPatch>({
  title,
  subtitle,
  code,
  codeImmutableHint,
  initialName,
  initialDescription,
  validateName,
  nameRequiredError,
  buildPatch,
  mapError,
  onClose,
  onSubmit,
  children,
  testIdPrefix,
}: TemplateMetadataDrawerProps<TPatch>) {
  const { t } = useTranslation();
  const [name, setName] = useState<LocalizedString>(initialName ?? {});
  const [description, setDescription] = useState<LocalizedString>(initialDescription ?? {});
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async () => {
    if (!validateName(name)) {
      setError(nameRequiredError);
      return;
    }
    const result = buildPatch({ name_i18n: name, description_i18n: description });
    if (!result.ok) {
      setError(result.error);
      return;
    }

    setError(null);
    setSubmitting(true);
    try {
      await onSubmit(result.patch);
    } catch (e) {
      setError(mapError(e));
      return;
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <DrawerForm
      open
      title={title}
      subtitle={subtitle}
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
            value={code}
            readOnly
            disabled
            className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-divider text-text-secondary font-mono cursor-not-allowed"
            data-testid={`${testIdPrefix}-code`}
          />
          <span className="mt-1 block text-xs text-text-muted">{codeImmutableHint}</span>
        </label>

        <LocalizedNameTabs value={name} onChange={setName} label={t('common.name')} />

        <LocalizedNameTabs
          value={description}
          onChange={setDescription}
          label={t('common.description')}
        />

        {children}

        {error ? (
          <p className="text-xs text-danger-700" role="alert" data-testid={`${testIdPrefix}-error`}>
            {error}
          </p>
        ) : null}
      </div>
    </DrawerForm>
  );
}
