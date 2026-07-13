import { useTranslation } from 'react-i18next';
import { RenameTemplateDrawer as SharedRenameTemplateDrawer } from '@/shared/components/template-management/RenameTemplateDrawer';
import { ApiError } from '@/shared/api/apiError';
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
 * drawer (the picker hides the manage actions for them). Thin wrapper around
 * the shared entity-agnostic `RenameTemplateDrawer`.
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

  return (
    <SharedRenameTemplateDrawer
      title={t('gradeStructure.manage_templates.rename_title')}
      subtitle={t('gradeStructure.manage_templates.rename_subtitle')}
      code={template.code}
      codeImmutableHint={t('gradeStructure.metadata.code_immutable')}
      initialName={template.name_i18n ?? {}}
      initialDescription={template.description_i18n ?? {}}
      validateName={(name) => Object.values(name).some((v) => v?.trim())}
      nameRequiredError={t('gradeStructure.metadata.name_required')}
      notFoundError={t('gradeStructure.manage_templates.rename_not_found')}
      genericError={t('gradeStructure.manage_templates.rename_failed')}
      isNotFoundError={(e) => e instanceof ApiError && e.status === 404}
      onClose={onClose}
      onSubmit={onSubmit}
      testIdPrefix="rename-grade-template"
    />
  );
}
