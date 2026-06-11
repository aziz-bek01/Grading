import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { DrawerForm } from '@/shared/components/data-table/DrawerForm';
import { LocalizedNameTabs } from '@/features/projects/components/LocalizedNameTabs';
import { ApiError } from '@/shared/api/apiError';
import type { LocalizedString } from '@/shared/types/common';
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
 * Reuses DrawerForm + LocalizedNameTabs — no new primitives.
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
  const [code, setCode] = useState('');
  const [name, setName] = useState<LocalizedString>(structure.name_i18n ?? {});
  const [description, setDescription] = useState<LocalizedString>(
    structure.description_i18n ?? {},
  );
  const [error, setError] = useState<string | null>(null);
  const [codeError, setCodeError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async () => {
    setError(null);
    setCodeError(null);
    if (!code.trim()) {
      setCodeError(t('gradeStructure.save_as_template.code_required'));
      return;
    }
    if (!Object.values(name).some((v) => v?.trim())) {
      setError(t('gradeStructure.metadata.name_required'));
      return;
    }
    const payload: SaveAsGradeTemplatePayload = {
      code: code.trim(),
      name_i18n: name,
      description_i18n: description,
    };
    setSubmitting(true);
    try {
      await onSubmit(payload);
    } catch (e) {
      // Surface the duplicate-code conflict inline on the code field.
      if (
        e instanceof ApiError &&
        (e.code === 'GRADE_TEMPLATE_CODE_EXISTS' || e.status === 409)
      ) {
        setCodeError(t('gradeStructure.save_as_template.duplicate_code'));
      } else if (e instanceof ApiError && e.fieldErrors?.code) {
        setCodeError(e.fieldErrors.code);
      } else {
        setError(t('gradeStructure.save_as_template.failed'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <DrawerForm
      open
      title={t('gradeStructure.save_as_template.title')}
      subtitle={t('gradeStructure.save_as_template.subtitle')}
      onClose={onClose}
      submitLabel={t('gradeStructure.save_as_template.submit')}
      onSubmit={() => {
        if (!submitting) void handleSubmit();
      }}
    >
      <div className="space-y-4">
        <label className="block text-sm font-medium text-text-primary">
          <span>
            {t('gradeStructure.save_as_template.code_label')}{' '}
            <span className="text-danger-700">*</span>
          </span>
          <input
            type="text"
            value={code}
            onChange={(e) => setCode(e.target.value.toUpperCase())}
            placeholder="GS-TPL-2026"
            className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500 font-mono"
            data-testid="save-grade-template-code"
          />
          {codeError ? (
            <span
              className="mt-1 block text-xs text-danger-700"
              role="alert"
              data-testid="save-grade-template-code-error"
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

        <p className="text-xs text-text-muted">
          {t('gradeStructure.save_as_template.hint')}
        </p>

        {error ? (
          <p
            className="text-xs text-danger-700"
            role="alert"
            data-testid="save-grade-template-error"
          >
            {error}
          </p>
        ) : null}
      </div>
    </DrawerForm>
  );
}
