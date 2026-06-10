import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { DrawerForm } from '@/shared/components/data-table/DrawerForm';
import { LocalizedNameTabs } from '@/features/projects/components/LocalizedNameTabs';
import { ApiError } from '@/shared/api/apiError';
import type { LocalizedString } from '@/shared/types/common';
import type { MethodologyTemplate, UpdateTemplatePayload } from '../types';

interface RenameTemplateDrawerProps {
  open: boolean;
  /** The CUSTOM template being renamed. */
  template: MethodologyTemplate | null;
  onClose: () => void;
  onSubmit: (payload: UpdateTemplatePayload) => Promise<void>;
}

/**
 * Rename a CUSTOM template (Epic E): edits name/description only — the template
 * `code` is immutable and shown read-only. Wired to PUT /methodology-templates/{id}.
 * Built-in templates never reach this drawer (the manager hides the action).
 */
export function RenameTemplateDrawer({ open, template, ...rest }: RenameTemplateDrawerProps) {
  if (!open || !template) return null;
  return <RenameTemplateDrawerBody key={template.id ?? template.code} template={template} {...rest} />;
}

function RenameTemplateDrawerBody({
  template,
  onClose,
  onSubmit,
}: Omit<RenameTemplateDrawerProps, 'open'> & { template: MethodologyTemplate }) {
  const { t } = useTranslation();
  const [name, setName] = useState<LocalizedString>(template.name_i18n ?? {});
  const [description, setDescription] = useState<LocalizedString>(
    template.description_i18n ?? {},
  );
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async () => {
    setError(null);
    const ru = (name['ru-RU'] ?? '').trim();
    if (!ru) {
      setError(t('methodology.metadata.name_primary_required'));
      return;
    }
    setSubmitting(true);
    try {
      // `code` is intentionally NOT part of the payload (immutable).
      await onSubmit({ name_i18n: name, description_i18n: description });
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) {
        setError(t('methodology.manage_templates.rename_not_found'));
      } else {
        setError(t('methodology.manage_templates.rename_failed'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <DrawerForm
      open
      title={t('methodology.manage_templates.rename_title')}
      subtitle={t('methodology.manage_templates.rename_subtitle')}
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
            data-testid="rename-template-code"
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

        {error ? (
          <p className="text-xs text-danger-700" role="alert" data-testid="rename-template-error">
            {error}
          </p>
        ) : null}
      </div>
    </DrawerForm>
  );
}
