import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { DrawerForm } from '@/shared/components/data-table/DrawerForm';
import { LocalizedNameTabs } from '@/features/projects/components/LocalizedNameTabs';
import { ApiError } from '@/shared/api/apiError';
import type { LocalizedString } from '@/shared/types/common';
import type { GradeStructureTemplate, UpdateGradeTemplatePayload } from '../types';

interface RenameGradeTemplateDrawerProps {
  open: boolean;
  /** The CUSTOM template being renamed. */
  template: GradeStructureTemplate | null;
  onClose: () => void;
  onSubmit: (payload: UpdateGradeTemplatePayload) => Promise<void>;
}

/**
 * Rename a CUSTOM grade template (BE-9): edits name/description only — the
 * template `code` is immutable and shown read-only. Wired to
 * PUT /grade-structure-templates/{id}. Built-in templates never reach this
 * drawer (the picker hides the manage actions for them).
 */
export function RenameGradeTemplateDrawer({
  open,
  template,
  ...rest
}: RenameGradeTemplateDrawerProps) {
  if (!open || !template) return null;
  return (
    <RenameGradeTemplateDrawerBody
      key={template.id ?? template.code}
      template={template}
      {...rest}
    />
  );
}

function RenameGradeTemplateDrawerBody({
  template,
  onClose,
  onSubmit,
}: Omit<RenameGradeTemplateDrawerProps, 'open'> & { template: GradeStructureTemplate }) {
  const { t } = useTranslation();
  const [name, setName] = useState<LocalizedString>(template.name_i18n ?? {});
  const [description, setDescription] = useState<LocalizedString>(
    template.description_i18n ?? {},
  );
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async () => {
    setError(null);
    if (!Object.values(name).some((v) => v?.trim())) {
      setError(t('gradeStructure.metadata.name_required'));
      return;
    }
    setSubmitting(true);
    try {
      // `code` is intentionally NOT part of the payload (immutable).
      await onSubmit({ name_i18n: name, description_i18n: description });
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) {
        setError(t('gradeStructure.manage_templates.rename_not_found'));
      } else {
        setError(t('gradeStructure.manage_templates.rename_failed'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <DrawerForm
      open
      title={t('gradeStructure.manage_templates.rename_title')}
      subtitle={t('gradeStructure.manage_templates.rename_subtitle')}
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
            value={template.code}
            readOnly
            disabled
            className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-divider text-text-secondary font-mono cursor-not-allowed"
            data-testid="rename-grade-template-code"
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

        {error ? (
          <p
            className="text-xs text-danger-700"
            role="alert"
            data-testid="rename-grade-template-error"
          >
            {error}
          </p>
        ) : null}
      </div>
    </DrawerForm>
  );
}
