import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { DrawerForm } from '@/shared/components/data-table/DrawerForm';
import { LocalizedNameTabs } from '@/features/projects/components/LocalizedNameTabs';
import { PositionCreateSchema, type PositionCreateInput } from '../schemas/positionSchemas';
import type { Position } from '../types/positionTypes';
import type { Department } from '@/features/organization/types/organizationTypes';
import { pickLocalized } from '@/shared/lib/localized';

interface PositionFormDrawerProps {
  open: boolean;
  projectId: string;
  departments: Department[];
  initial?: Position;
  readOnly?: boolean;
  /**
   * Localized mutation error (create/update) surfaced as a banner near the top.
   * The page maps POSITION_CODE_TAKEN / POSITION_DEPARTMENT_INVALID etc. here so
   * a failed create/update is no longer silent.
   */
  error?: string;
  onClose: () => void;
  onSubmit: (input: PositionCreateInput) => Promise<void> | void;
}

export function PositionFormDrawer({
  open,
  projectId,
  departments,
  initial,
  readOnly,
  error,
  onClose,
  onSubmit,
}: PositionFormDrawerProps) {
  const { t, i18n } = useTranslation();
  // In edit mode the code is the immutable identifier — disable it so the
  // "codes are never rewritten" guarantee is visible in the UI.
  const isEdit = Boolean(initial);
  const codeReadOnly = readOnly || isEdit;

  const {
    register,
    control,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<PositionCreateInput>({
    resolver: zodResolver(PositionCreateSchema),
    defaultValues: {
      project_id: projectId,
      department_id: '',
      code: '',
      title_i18n: {},
      function: '',
      category: '',
      job_family: '',
      job_level: '',
    },
  });

  useEffect(() => {
    if (open) {
      reset({
        project_id: projectId,
        department_id: initial?.department_id ?? '',
        code: initial?.code ?? '',
        title_i18n: initial?.title_i18n ?? {},
        function: initial?.function ?? '',
        category: initial?.category ?? '',
        job_family: initial?.job_family ?? '',
        job_level: initial?.job_level ?? '',
      });
    }
  }, [open, initial, projectId, reset]);

  const submit = handleSubmit(async (data) => {
    // Close ONLY on success. When the mutation rejects (e.g. POSITION_CODE_TAKEN)
    // the page surfaces the message via the `error` prop and the drawer stays
    // open so the user can correct the input. We swallow the rejection here so
    // it does not bubble as an unhandled promise rejection.
    try {
      await onSubmit(data);
      onClose();
    } catch {
      /* error already surfaced by the page through the `error` prop */
    }
  });

  const tr = (msg?: string) =>
    msg ? t(`positions.${msg}`, { defaultValue: msg }) : undefined;

  return (
    <DrawerForm
      open={open}
      title={initial ? t('positions.edit_position') : t('positions.new_position')}
      onClose={onClose}
      onSubmit={submit}
      readOnly={readOnly}
      submitLabel={t('common.save')}
    >
      {error ? (
        <div
          role="alert"
          data-testid="position-form-error"
          className="rounded-md border border-danger-500/30 bg-danger-50 text-danger-700 text-sm p-3"
        >
          {error}
        </div>
      ) : null}

      <div>
        <label htmlFor="pos-department" className="text-sm font-medium text-text-primary">
          {t('positions.field_department')} <span className="text-danger-700">*</span>
        </label>
        <Controller
          control={control}
          name="department_id"
          render={({ field, fieldState }) => (
            <>
              <select
                id="pos-department"
                value={field.value ?? ''}
                onChange={(e) => field.onChange(e.target.value)}
                disabled={readOnly}
                className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface"
                data-testid="pos-department-select"
              >
                <option value="">—</option>
                {departments
                  .filter((d) => d.status !== 'ARCHIVED')
                  .map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.code} · {pickLocalized(d.name_i18n, i18n.language)}
                    </option>
                  ))}
              </select>
              {fieldState.error ? (
                <p className="text-xs text-danger-700 mt-1" role="alert">
                  {tr(fieldState.error.message)}
                </p>
              ) : null}
            </>
          )}
        />
      </div>

      <div>
        <label htmlFor="pos-code" className="text-sm font-medium text-text-primary">
          {t('common.code')} <span className="text-danger-700">*</span>
        </label>
        <input
          id="pos-code"
          type="text"
          disabled={codeReadOnly}
          aria-readonly={codeReadOnly || undefined}
          {...register('code')}
          className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500 disabled:bg-divider disabled:text-text-secondary"
          data-testid="pos-code"
        />
        <p className="text-xs text-text-secondary mt-1">
          {isEdit ? t('positions.code_immutable_hint') : t('positions.field_code_hint')}
        </p>
        {errors.code ? <p className="text-xs text-danger-700 mt-1" role="alert">{errors.code.message}</p> : null}
      </div>

      <Controller
        control={control}
        name="title_i18n"
        render={({ field, fieldState }) => (
          <LocalizedNameTabs
            value={field.value ?? {}}
            onChange={field.onChange}
            label={t('positions.field_title')}
            error={tr(fieldState.error?.message)}
            inputId="pos-title"
          />
        )}
      />

      <div className="grid grid-cols-2 gap-3">
        <div>
          <label htmlFor="pos-function" className="text-sm font-medium text-text-primary">
            {t('positions.field_function')}
          </label>
          <input
            id="pos-function"
            type="text"
            disabled={readOnly}
            {...register('function')}
            className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface"
          />
        </div>
        <div>
          <label htmlFor="pos-category" className="text-sm font-medium text-text-primary">
            {t('positions.field_category')}
          </label>
          <input
            id="pos-category"
            type="text"
            disabled={readOnly}
            {...register('category')}
            className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface"
          />
        </div>
        <div>
          <label htmlFor="pos-family" className="text-sm font-medium text-text-primary">
            {t('positions.field_job_family')}
          </label>
          <input
            id="pos-family"
            type="text"
            disabled={readOnly}
            {...register('job_family')}
            className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface"
          />
        </div>
        <div>
          <label htmlFor="pos-level" className="text-sm font-medium text-text-primary">
            {t('positions.field_job_level')}
          </label>
          <input
            id="pos-level"
            type="text"
            disabled={readOnly}
            {...register('job_level')}
            className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface"
          />
        </div>
      </div>

      <p className="text-xs text-text-muted">
        {/* Hard rule: NO salary fields on Position in Phase 2. */}
        {t('positions.no_salary_fields')}
      </p>
    </DrawerForm>
  );
}
