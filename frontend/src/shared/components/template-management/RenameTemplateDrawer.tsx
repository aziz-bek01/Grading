import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { DrawerForm } from '@/shared/components/data-table/DrawerForm';
import { LocalizedNameTabs } from '@/features/projects/components/LocalizedNameTabs';
import type { LocalizedString } from '@/shared/types/common';

/**
 * The wire shape both `UpdateGradeTemplatePayload` and `UpdateTemplatePayload`
 * already share 1:1 — kept as a plain (non-generic) structural type so either
 * feature-specific payload type is assignable without a cast.
 */
export interface TemplateRenamePayload {
  name_i18n: LocalizedString;
  description_i18n?: LocalizedString;
}

export interface RenameTemplateDrawerProps {
  title: string;
  subtitle: string;
  /** The CUSTOM template's immutable code, shown read-only. */
  code: string;
  codeImmutableHint: string;
  initialName: LocalizedString;
  initialDescription: LocalizedString;
  /** True when `name` satisfies the entity's "name required" rule (see
   *  {@link TemplateMetadataDrawerProps.validateName} for why this varies). */
  validateName: (name: LocalizedString) => boolean;
  nameRequiredError: string;
  notFoundError: string;
  genericError: string;
  /** True when the thrown error is a 404 (template no longer exists). */
  isNotFoundError: (error: unknown) => boolean;
  onClose: () => void;
  onSubmit: (payload: TemplateRenamePayload) => Promise<void>;
  testIdPrefix: string;
}

/**
 * Rename a CUSTOM template — the shared shell behind `RenameGradeTemplateDrawer`
 * and `RenameTemplateDrawer` (methodology). Edits name/description only; the
 * template `code` is immutable and shown read-only. Built-in templates never
 * reach this drawer (the picker hides the manage actions for them).
 *
 * Reuses DrawerForm + LocalizedNameTabs — no new primitives.
 */
export function RenameTemplateDrawer({
  title,
  subtitle,
  code,
  codeImmutableHint,
  initialName,
  initialDescription,
  validateName,
  nameRequiredError,
  notFoundError,
  genericError,
  isNotFoundError,
  onClose,
  onSubmit,
  testIdPrefix,
}: RenameTemplateDrawerProps) {
  const { t } = useTranslation();
  const [name, setName] = useState<LocalizedString>(initialName ?? {});
  const [description, setDescription] = useState<LocalizedString>(initialDescription ?? {});
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async () => {
    setError(null);
    if (!validateName(name)) {
      setError(nameRequiredError);
      return;
    }
    setSubmitting(true);
    try {
      // `code` is intentionally NOT part of the payload (immutable).
      await onSubmit({ name_i18n: name, description_i18n: description });
    } catch (e) {
      setError(isNotFoundError(e) ? notFoundError : genericError);
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

        {error ? (
          <p className="text-xs text-danger-700" role="alert" data-testid={`${testIdPrefix}-error`}>
            {error}
          </p>
        ) : null}
      </div>
    </DrawerForm>
  );
}
