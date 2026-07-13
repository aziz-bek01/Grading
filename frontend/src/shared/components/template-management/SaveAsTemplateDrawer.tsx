import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { DrawerForm } from '@/shared/components/data-table/DrawerForm';
import { LocalizedNameTabs } from '@/features/projects/components/LocalizedNameTabs';
import type { LocalizedString } from '@/shared/types/common';

/**
 * The wire shape both `SaveAsGradeTemplatePayload` and `SaveAsTemplatePayload`
 * already share 1:1 — kept as a plain (non-generic) structural type so either
 * feature-specific payload type is assignable without a cast.
 */
export interface TemplateSaveAsPayload {
  code: string;
  name_i18n: LocalizedString;
  description_i18n?: LocalizedString;
}

export interface SaveAsTemplateDrawerProps {
  title: string;
  subtitle: string;
  submitLabel: string;
  codeLabel: string;
  codePlaceholder: string;
  hint: string;
  /** Seeds the suggested name/description from the entity being snapshotted. */
  initialName: LocalizedString;
  initialDescription: LocalizedString;
  /** True when `name` satisfies the entity's "name required" rule (see
   *  {@link TemplateMetadataDrawerProps.validateName} for why this varies). */
  validateName: (name: LocalizedString) => boolean;
  nameRequiredError: string;
  codeRequiredError: string;
  duplicateCodeError: string;
  genericError: string;
  /** True when the thrown error is the backend's duplicate-code conflict for
   *  this entity (distinct error codes per feature, both fall back to a bare
   *  409 as a safety net — mirrors the original per-feature checks). */
  isDuplicateCodeError: (error: unknown) => boolean;
  /** A generic (non-duplicate-code) `fieldErrors.code` validation message from
   *  the backend, if any — surfaced inline on the code field like the
   *  duplicate-code conflict. */
  fieldCodeError: (error: unknown) => string | undefined;
  onClose: () => void;
  onSubmit: (payload: TemplateSaveAsPayload) => Promise<void>;
  testIdPrefix: string;
}

/**
 * "Save as template" drawer — the shared shell behind
 * `SaveAsGradeTemplateDrawer` and `SaveAsTemplateDrawer` (methodology).
 * Snapshots an entity into a reusable tenant CUSTOM template: the consultant
 * picks a unique template `code` plus a localized name and optional
 * description. Duplicate-code conflicts are surfaced inline on the code field
 * without losing form input.
 *
 * Reuses DrawerForm + LocalizedNameTabs — no new primitives.
 */
export function SaveAsTemplateDrawer({
  title,
  subtitle,
  submitLabel,
  codeLabel,
  codePlaceholder,
  hint,
  initialName,
  initialDescription,
  validateName,
  nameRequiredError,
  codeRequiredError,
  duplicateCodeError,
  genericError,
  isDuplicateCodeError,
  fieldCodeError,
  onClose,
  onSubmit,
  testIdPrefix,
}: SaveAsTemplateDrawerProps) {
  const { t } = useTranslation();
  const [code, setCode] = useState('');
  const [name, setName] = useState<LocalizedString>(initialName ?? {});
  const [description, setDescription] = useState<LocalizedString>(initialDescription ?? {});
  const [error, setError] = useState<string | null>(null);
  const [codeError, setCodeError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async () => {
    setError(null);
    setCodeError(null);
    if (!code.trim()) {
      setCodeError(codeRequiredError);
      return;
    }
    if (!validateName(name)) {
      setError(nameRequiredError);
      return;
    }
    const payload: TemplateSaveAsPayload = {
      code: code.trim(),
      name_i18n: name,
      description_i18n: description,
    };
    setSubmitting(true);
    try {
      await onSubmit(payload);
    } catch (e) {
      // Surface the duplicate-code conflict inline on the code field.
      const fieldError = fieldCodeError(e);
      if (isDuplicateCodeError(e)) {
        setCodeError(duplicateCodeError);
      } else if (fieldError) {
        setCodeError(fieldError);
      } else {
        setError(genericError);
      }
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
      submitLabel={submitLabel}
      onSubmit={() => {
        if (!submitting) void handleSubmit();
      }}
    >
      <div className="space-y-4">
        <label className="block text-sm font-medium text-text-primary">
          <span>
            {codeLabel} <span className="text-danger-700">*</span>
          </span>
          <input
            type="text"
            value={code}
            onChange={(e) => setCode(e.target.value.toUpperCase())}
            placeholder={codePlaceholder}
            className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500 font-mono"
            data-testid={`${testIdPrefix}-code`}
          />
          {codeError ? (
            <span
              className="mt-1 block text-xs text-danger-700"
              role="alert"
              data-testid={`${testIdPrefix}-code-error`}
            >
              {codeError}
            </span>
          ) : null}
        </label>

        <LocalizedNameTabs value={name} onChange={setName} label={t('common.name')} />

        <LocalizedNameTabs
          value={description}
          onChange={setDescription}
          label={t('common.description')}
        />

        <p className="text-xs text-text-muted">{hint}</p>

        {error ? (
          <p className="text-xs text-danger-700" role="alert" data-testid={`${testIdPrefix}-error`}>
            {error}
          </p>
        ) : null}
      </div>
    </DrawerForm>
  );
}
