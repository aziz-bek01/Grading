import { useTranslation } from 'react-i18next';
import { RenameTemplateDrawer as SharedRenameTemplateDrawer } from '@/shared/components/template-management/RenameTemplateDrawer';
import { ApiError } from '@/shared/api/apiError';
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
 * Thin wrapper around the shared entity-agnostic `RenameTemplateDrawer`.
 */
export function RenameTemplateDrawer({ open, template, ...rest }: RenameTemplateDrawerProps) {
  if (!open || !template) return null;
  return (
    <RenameTemplateDrawerBody key={template.id ?? template.code} template={template} {...rest} />
  );
}

function RenameTemplateDrawerBody({
  template,
  onClose,
  onSubmit,
}: Omit<RenameTemplateDrawerProps, 'open'> & { template: MethodologyTemplate }) {
  const { t } = useTranslation();

  return (
    <SharedRenameTemplateDrawer
      title={t('methodology.manage_templates.rename_title')}
      subtitle={t('methodology.manage_templates.rename_subtitle')}
      code={template.code}
      codeImmutableHint={t('methodology.metadata.code_immutable')}
      initialName={template.name_i18n ?? {}}
      initialDescription={template.description_i18n ?? {}}
      // ru-RU is the required primary locale for methodology names (unlike
      // grade-structure, which accepts a name in any locale).
      validateName={(name) => !!(name['ru-RU'] ?? '').trim()}
      nameRequiredError={t('methodology.metadata.name_primary_required')}
      notFoundError={t('methodology.manage_templates.rename_not_found')}
      genericError={t('methodology.manage_templates.rename_failed')}
      isNotFoundError={(e) => e instanceof ApiError && e.status === 404}
      onClose={onClose}
      onSubmit={onSubmit}
      testIdPrefix="rename-template"
    />
  );
}
