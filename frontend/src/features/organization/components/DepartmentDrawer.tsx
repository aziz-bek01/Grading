import { useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { DrawerForm } from '@/shared/components/data-table/DrawerForm';
import { LocalizedNameTabs } from '@/features/projects/components/LocalizedNameTabs';
import { collectDescendantIds } from '../lib/tree';
import { DepartmentCreateSchema, type DepartmentCreateInput } from '../schemas/departmentSchemas';
import type { Department, DepartmentType } from '../types/organizationTypes';
import { pickLocalized } from '@/shared/lib/localized';

interface DepartmentDrawerProps {
  open: boolean;
  projectId: string;
  items: Department[];
  initial?: Department;
  readOnly?: boolean;
  onClose: () => void;
  onSubmit: (input: DepartmentCreateInput) => Promise<void> | void;
}

const TYPES: DepartmentType[] = ['BRANCH', 'DEPARTMENT', 'DIVISION', 'UNIT'];

export function DepartmentDrawer({
  open,
  projectId,
  items,
  initial,
  readOnly,
  onClose,
  onSubmit,
}: DepartmentDrawerProps) {
  const { t, i18n } = useTranslation();
  const {
    register,
    control,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<DepartmentCreateInput>({
    resolver: zodResolver(DepartmentCreateSchema),
    defaultValues: {
      project_id: projectId,
      parent_id: null,
      code: '',
      name: {},
      type: 'DEPARTMENT',
    },
  });

  useEffect(() => {
    if (open) {
      reset({
        project_id: projectId,
        parent_id: initial?.parent_id ?? null,
        code: initial?.code ?? '',
        name: initial?.name ?? {},
        type: initial?.type ?? 'DEPARTMENT',
      });
    }
  }, [open, initial, projectId, reset]);

  /** Parent options exclude self + descendants (no cycles). */
  const parentOptions = useMemo(() => {
    const forbidden = initial ? collectDescendantIds(items, initial.id) : new Set<string>();
    return items.filter((d) => !forbidden.has(d.id));
  }, [items, initial]);

  const submit = handleSubmit(async (data) => {
    await onSubmit(data);
    onClose();
  });

  return (
    <DrawerForm
      open={open}
      title={initial ? t('organization.edit_department') : t('organization.new_department')}
      onClose={onClose}
      onSubmit={submit}
      readOnly={readOnly}
    >
      <div>
        <label htmlFor="dep-parent" className="text-sm font-medium text-text-primary">
          {t('organization.field_parent')}
        </label>
        <Controller
          control={control}
          name="parent_id"
          render={({ field }) => (
            <select
              id="dep-parent"
              value={field.value ?? ''}
              onChange={(e) => field.onChange(e.target.value === '' ? null : e.target.value)}
              disabled={readOnly}
              className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface"
              data-testid="dep-parent-select"
            >
              <option value="">{t('organization.field_parent_none')}</option>
              {parentOptions.map((o) => (
                <option key={o.id} value={o.id}>
                  {o.code} · {pickLocalized(o.name, i18n.language)}
                </option>
              ))}
            </select>
          )}
        />
      </div>

      <div>
        <label htmlFor="dep-type" className="text-sm font-medium text-text-primary">
          {t('organization.field_type')}
        </label>
        <select
          id="dep-type"
          {...register('type')}
          disabled={readOnly}
          className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface"
        >
          {TYPES.map((tp) => (
            <option key={tp} value={tp}>
              {t(`organization.type_${tp.toLowerCase()}`)}
            </option>
          ))}
        </select>
      </div>

      <div>
        <label htmlFor="dep-code" className="text-sm font-medium text-text-primary">
          {t('organization.field_code')} <span className="text-danger-700">*</span>
        </label>
        <input
          id="dep-code"
          type="text"
          disabled={readOnly}
          {...register('code')}
          className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface"
          data-testid="dep-code"
        />
        {errors.code ? <p className="text-xs text-danger-700 mt-1" role="alert">{errors.code.message}</p> : null}
      </div>

      <Controller
        control={control}
        name="name"
        render={({ field, fieldState }) => (
          <LocalizedNameTabs
            value={field.value ?? {}}
            onChange={field.onChange}
            label={t('organization.field_name')}
            error={fieldState.error?.message}
            inputId="dep-name"
          />
        )}
      />
    </DrawerForm>
  );
}
