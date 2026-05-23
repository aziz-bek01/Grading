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
  onClose: () => void;
  onSubmit: (input: PositionCreateInput) => Promise<void> | void;
}

export function PositionFormDrawer({
  open,
  projectId,
  departments,
  initial,
  readOnly,
  onClose,
  onSubmit,
}: PositionFormDrawerProps) {
  const { t, i18n } = useTranslation();
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
      title: {},
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
        title: initial?.title ?? {},
        function: initial?.function ?? '',
        category: initial?.category ?? '',
        job_family: initial?.job_family ?? '',
        job_level: initial?.job_level ?? '',
      });
    }
  }, [open, initial, projectId, reset]);

  const submit = handleSubmit(async (data) => {
    await onSubmit(data);
    onClose();
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
    >
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
                      {d.code} · {pickLocalized(d.name, i18n.language)}
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
          disabled={readOnly}
          {...register('code')}
          className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface"
          data-testid="pos-code"
        />
        {errors.code ? <p className="text-xs text-danger-700 mt-1" role="alert">{errors.code.message}</p> : null}
      </div>

      <Controller
        control={control}
        name="title"
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
        {/* eslint-disable-next-line i18next/no-literal-string */}
        No salary fields.
      </p>
    </DrawerForm>
  );
}
