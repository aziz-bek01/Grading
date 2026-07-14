import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { DrawerForm } from '@/shared/components/data-table/DrawerForm';
import { LocalizedNameTabs } from './LocalizedNameTabs';
import { fieldA11y, fieldErrorId } from '@/shared/lib/fieldA11y';
import { ProjectCreateSchema, type ProjectCreateInput } from '../schemas/projectSchemas';
import type { Project } from '../types/projectTypes';

interface ProjectFormDrawerProps {
  open: boolean;
  initial?: Project;
  readOnly?: boolean;
  /**
   * Localized mutation error (create/update) surfaced as a banner near the top.
   * The page maps PROJECT_CODE_TAKEN here so a failed create is no longer silent.
   */
  error?: string;
  onClose: () => void;
  onSubmit: (data: ProjectCreateInput) => Promise<void> | void;
}

export function ProjectFormDrawer({ open, initial, readOnly, error, onClose, onSubmit }: ProjectFormDrawerProps) {
  const { t } = useTranslation();
  // In edit mode the project code is the immutable identifier (PATCH ignores it,
  // and there is a DB unique index). Render it read-only so the "codes are never
  // rewritten" guarantee is visible in the UI.
  const isEdit = Boolean(initial);
  const codeReadOnly = readOnly || isEdit;
  const {
    register,
    handleSubmit,
    control,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<ProjectCreateInput>({
    resolver: zodResolver(ProjectCreateSchema),
    defaultValues: {
      code: '',
      name_i18n: {},
      description: '',
      start_date: '',
      end_date: '',
    },
  });

  useEffect(() => {
    if (open) {
      reset({
        code: initial?.code ?? '',
        name_i18n: initial?.name_i18n ?? {},
        description: initial?.description ?? '',
        start_date: initial?.start_date ?? '',
        end_date: initial?.end_date ?? '',
      });
    }
  }, [open, initial, reset]);

  const submit = handleSubmit(async (data) => {
    // Close ONLY on success. When the mutation rejects (e.g. PROJECT_CODE_TAKEN)
    // the page surfaces the message via the `error` prop and the drawer stays
    // open so the user can correct the input. We swallow the rejection here so it
    // does not bubble as an unhandled promise rejection.
    try {
      await onSubmit(data);
      onClose();
    } catch {
      /* error already surfaced by the page through the `error` prop */
    }
  });

  const errMessage = (key: string | undefined) => (key ? t(`projects.${key}`, { defaultValue: key }) : undefined);

  return (
    <DrawerForm
      open={open}
      title={initial ? t('projects.edit_project') : t('projects.new_project')}
      onClose={onClose}
      onSubmit={submit}
      readOnly={readOnly}
      submitLabel={t('common.save')}
    >
      {error ? (
        <div
          role="alert"
          data-testid="project-form-error"
          className="rounded-md border border-danger-500/30 bg-danger-50 text-danger-700 text-sm p-3"
        >
          {error}
        </div>
      ) : null}

      <div>
        <label htmlFor="project-code" className="text-sm font-medium text-text-primary">
          {t('projects.field_code')} <span className="text-danger-700">*</span>
        </label>
        <input
          id="project-code"
          type="text"
          disabled={codeReadOnly}
          aria-readonly={codeReadOnly || undefined}
          {...register('code')}
          {...fieldA11y('project-code', !!errors.code)}
          className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500 disabled:bg-divider disabled:text-text-secondary"
          data-testid="project-code"
        />
        <p className="text-xs text-text-secondary mt-1">
          {/* In edit mode the code is locked — explain WHY (immutable identifier);
              in create mode keep the original formatting hint. */}
          {isEdit ? t('projects.code_immutable_hint') : t('projects.field_code_hint')}
        </p>
        {errors.code ? (
          <p id={fieldErrorId('project-code')} className="text-xs text-danger-700 mt-1" role="alert">
            {errors.code.message}
          </p>
        ) : null}
      </div>

      <Controller
        control={control}
        name="name_i18n"
        render={({ field, fieldState }) => (
          <LocalizedNameTabs
            value={field.value ?? {}}
            onChange={field.onChange}
            label={t('projects.field_name')}
            hint={t('projects.field_name_hint')}
            error={errMessage(fieldState.error?.message)}
            inputId="project-name"
          />
        )}
      />

      <div>
        <label htmlFor="project-description" className="text-sm font-medium text-text-primary">
          {t('projects.field_description')}
        </label>
        <textarea
          id="project-description"
          rows={3}
          disabled={readOnly}
          {...register('description')}
          className="mt-1 w-full p-2 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500 disabled:bg-divider"
        />
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div>
          <label htmlFor="project-start" className="text-sm font-medium text-text-primary">
            {t('projects.field_start_date')}
          </label>
          <input
            id="project-start"
            type="date"
            disabled={readOnly}
            {...register('start_date')}
            className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface"
          />
        </div>
        <div>
          <label htmlFor="project-end" className="text-sm font-medium text-text-primary">
            {t('projects.field_end_date')}
          </label>
          <input
            id="project-end"
            type="date"
            disabled={readOnly}
            {...register('end_date')}
            {...fieldA11y('project-end', !!errors.end_date)}
            className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface"
          />
          {errors.end_date ? (
            <p id={fieldErrorId('project-end')} className="text-xs text-danger-700 mt-1" role="alert">
              {errMessage(errors.end_date.message)}
            </p>
          ) : null}
        </div>
      </div>
      {isSubmitting ? <p className="text-xs text-text-muted">{t('app.loading')}</p> : null}
    </DrawerForm>
  );
}
