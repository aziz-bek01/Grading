/**
 * AssignRoleDialog — small drawer for adding a single role to an existing
 * membership row.
 *
 * The role picker is DATA-DRIVEN: it renders from the backend role catalog
 * (`GET /roles?assignableOnly=true`) via the shared {@link RolePicker}, so every
 * assignable role — including future custom roles — appears, and roles the
 * caller may not grant are shown disabled with a reason. The backend remains the
 * source of truth and re-enforces grant authority on submit.
 */
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { DrawerForm } from '@/shared/components/data-table/DrawerForm';
import { fieldA11y, fieldErrorId } from '@/shared/lib/fieldA11y';
import type { RoleCode } from '@/shared/auth/authTypes';
import { RolePicker } from './RolePicker';
import { AddRoleSchema, type AddRoleInput } from '../schemas/userSchemas';

interface AssignRoleDialogProps {
  open: boolean;
  /** Roles already on this membership — excluded from the picker. */
  alreadyAssigned: RoleCode[];
  tenantBrand: string;
  onClose: () => void;
  onSubmit: (input: AddRoleInput) => Promise<void> | void;
}

export function AssignRoleDialog({
  open,
  alreadyAssigned,
  tenantBrand,
  onClose,
  onSubmit,
}: AssignRoleDialogProps) {
  const { t } = useTranslation();

  const {
    control,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<AddRoleInput>({
    resolver: zodResolver(AddRoleSchema),
    defaultValues: { role_code: '' },
  });

  useEffect(() => {
    if (open) reset({ role_code: '' });
  }, [open, reset]);

  const submit = handleSubmit(async (data) => {
    await onSubmit(data);
    onClose();
  });

  return (
    <DrawerForm
      open={open}
      title={t('users.assignRole.title')}
      subtitle={t('users.assignRole.subtitle', { tenant: tenantBrand })}
      onClose={onClose}
      onSubmit={submit}
      submitLabel={t('users.assignRole.submit')}
    >
      <div>
        <label htmlFor="assign-role-code" className="text-sm font-medium text-text-primary">
          {t('users.assignRole.role')} <span className="text-danger-700">*</span>
        </label>
        <Controller
          control={control}
          name="role_code"
          render={({ field }) => (
            <RolePicker
              mode="single"
              selectId="assign-role-code"
              value={field.value ?? ''}
              onChange={field.onChange}
              excludeCodes={alreadyAssigned}
              testIdPrefix="assign-role"
              {...fieldA11y('assign-role-code', !!errors.role_code)}
            />
          )}
        />
        {errors.role_code ? (
          <p id={fieldErrorId('assign-role-code')} className="text-xs text-danger-700 mt-1" role="alert">
            {t('users.assignRole.validation_roles_min')}
          </p>
        ) : null}
      </div>
      {isSubmitting ? <p className="text-xs text-text-muted">{t('app.loading')}</p> : null}
    </DrawerForm>
  );
}
