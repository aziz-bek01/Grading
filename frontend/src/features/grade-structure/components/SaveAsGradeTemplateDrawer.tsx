import { useTranslation } from 'react-i18next';
import { SaveAsTemplateDrawer as SharedSaveAsTemplateDrawer } from '@/shared/components/template-management/SaveAsTemplateDrawer';
import { ApiError } from '@/shared/api/apiError';
import type { GradeStructure, SaveAsGradeTemplatePayload } from '../types';

interface SaveAsGradeTemplateDrawerProps {
  open: boolean;
  /** The structure being snapshotted (seeds the suggested name). */
  structure: GradeStructure | null;
  onClose: () => void;
  onSubmit: (payload: SaveAsGradeTemplatePayload) => Promise<void>;
}

/**
 * "Save as template" drawer (BE-9 / FE-10).
 *
 * Snapshots a structure's grades + bands into a reusable tenant CUSTOM grade
 * template (POST /grade-structures/{id}/save-as-template). The consultant picks
 * a unique template `code` plus a localized name and optional description. The
 * backend duplicate-code conflict (409 GRADE_TEMPLATE_CODE_EXISTS) is surfaced
 * inline on the code field without losing form input.
 *
 * Thin wrapper around the shared entity-agnostic `SaveAsTemplateDrawer`.
 */
export function SaveAsGradeTemplateDrawer({
  open,
  structure,
  ...rest
}: SaveAsGradeTemplateDrawerProps) {
  if (!open || !structure) return null;
  return <SaveAsGradeTemplateDrawerBody key={structure.id} structure={structure} {...rest} />;
}

function SaveAsGradeTemplateDrawerBody({
  structure,
  onClose,
  onSubmit,
}: Omit<SaveAsGradeTemplateDrawerProps, 'open'> & { structure: GradeStructure }) {
  const { t } = useTranslation();

  return (
    <SharedSaveAsTemplateDrawer
      title={t('gradeStructure.save_as_template.title')}
      subtitle={t('gradeStructure.save_as_template.subtitle')}
      submitLabel={t('gradeStructure.save_as_template.submit')}
      codeLabel={t('gradeStructure.save_as_template.code_label')}
      codePlaceholder="GS-TPL-2026"
      hint={t('gradeStructure.save_as_template.hint')}
      initialName={structure.name_i18n ?? {}}
      initialDescription={structure.description_i18n ?? {}}
      // Accept a name in ANY supported locale (locale-trap fix, mirrors methodology).
      validateName={(name) => Object.values(name).some((v) => v?.trim())}
      nameRequiredError={t('gradeStructure.metadata.name_required')}
      codeRequiredError={t('gradeStructure.save_as_template.code_required')}
      duplicateCodeError={t('gradeStructure.save_as_template.duplicate_code')}
      genericError={t('gradeStructure.save_as_template.failed')}
      isDuplicateCodeError={(e) =>
        e instanceof ApiError && (e.code === 'GRADE_TEMPLATE_CODE_EXISTS' || e.status === 409)
      }
      fieldCodeError={(e) => (e instanceof ApiError ? e.fieldErrors?.code : undefined)}
      onClose={onClose}
      onSubmit={onSubmit}
      testIdPrefix="save-grade-template"
    />
  );
}
