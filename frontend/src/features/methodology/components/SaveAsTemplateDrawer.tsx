import { useTranslation } from 'react-i18next';
import { SaveAsTemplateDrawer as SharedSaveAsTemplateDrawer } from '@/shared/components/template-management/SaveAsTemplateDrawer';
import { ApiError } from '@/shared/api/apiError';
import type { Methodology, SaveAsTemplatePayload } from '../types';

interface SaveAsTemplateDrawerProps {
  open: boolean;
  /** The methodology being snapshotted (seeds the suggested name). */
  methodology: Methodology | null;
  onClose: () => void;
  onSubmit: (payload: SaveAsTemplatePayload) => Promise<void>;
}

/**
 * "Save as template" drawer (Epic E).
 *
 * Snapshots a methodology's latest version into a reusable tenant CUSTOM
 * template (POST /methodologies/{id}/save-as-template). The consultant picks a
 * unique template `code` plus a localized name (ru-RU required) and optional
 * description. The backend duplicate-code conflict
 * (409 METHODOLOGY_TEMPLATE_CODE_EXISTS) is surfaced inline on the code field.
 *
 * Thin wrapper around the shared entity-agnostic `SaveAsTemplateDrawer`.
 */
export function SaveAsTemplateDrawer({ open, methodology, ...rest }: SaveAsTemplateDrawerProps) {
  // Keyed remount per methodology so the body re-seeds from props each open.
  if (!open || !methodology) return null;
  return <SaveAsTemplateDrawerBody key={methodology.id} methodology={methodology} {...rest} />;
}

function SaveAsTemplateDrawerBody({
  methodology,
  onClose,
  onSubmit,
}: Omit<SaveAsTemplateDrawerProps, 'open'> & { methodology: Methodology }) {
  const { t } = useTranslation();

  return (
    <SharedSaveAsTemplateDrawer
      title={t('methodology.save_as_template.title')}
      subtitle={t('methodology.save_as_template.subtitle')}
      submitLabel={t('methodology.save_as_template.submit')}
      codeLabel={t('methodology.save_as_template.code_label')}
      codePlaceholder="TPL-CFO-2026"
      hint={t('methodology.save_as_template.hint')}
      initialName={methodology.name_i18n ?? {}}
      initialDescription={methodology.description_i18n ?? {}}
      // ru-RU is the required primary locale for methodology names (unlike
      // grade-structure, which accepts a name in any locale).
      validateName={(name) => !!(name['ru-RU'] ?? '').trim()}
      nameRequiredError={t('methodology.metadata.name_primary_required')}
      codeRequiredError={t('methodology.save_as_template.code_required')}
      duplicateCodeError={t('methodology.save_as_template.duplicate_code')}
      genericError={t('methodology.save_as_template.failed')}
      isDuplicateCodeError={(e) =>
        e instanceof ApiError &&
        (e.code === 'METHODOLOGY_TEMPLATE_CODE_EXISTS' || e.status === 409)
      }
      fieldCodeError={(e) => (e instanceof ApiError ? e.fieldErrors?.code : undefined)}
      onClose={onClose}
      onSubmit={onSubmit}
      testIdPrefix="save-template"
    />
  );
}
