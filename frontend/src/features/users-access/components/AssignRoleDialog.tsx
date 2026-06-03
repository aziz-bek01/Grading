/**
 * AssignRoleDialog — small drawer for adding a single role to an existing
 * membership row. Whitelist mirrors the invite dialog (super-admin vs.
 * client-admin scope).
 */
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { DrawerForm } from '@/shared/components/data-table/DrawerForm';
import { useAuthStore } from '@/features/auth/authStore';
import type { RoleCode } from '@/shared/auth/authTypes';
import {
  AddRoleSchema,
  CLIENT_GRANTABLE_ROLES,
  SUPER_ADMIN_GRANTABLE_ROLES,
  type AddRoleInput,
} from '../schemas/userSchemas';

interface AssignRoleDialogProps {
  open: boolean;
  /** Roles already on this membership — excluded from the picker. */
  alreadyAssigned: RoleCode[];
  tenantBrand: string;
  onClose: () => void;
  onSubmit: (input: AddRoleInput) => Promise<void> | void;
}

export function AssignRoleDialog({ open, alreadyAssigned, tenantBrand, onClose, onSubmit }: AssignRoleDialogProps) {
  const { t } = useTranslation();
  const user = useAuthStore((s) => s.user);
  const isSuperAdmin = user?.roles.includes('HRLAB_SUPER_ADMIN') ?? false;
  const grantable = (isSuperAdmin ? SUPER_ADMIN_GRANTABLE_ROLES : CLIENT_GRANTABLE_ROLES).filter(
    (r) => !alreadyAssigned.includes(r),
  );

  const { register, handleSubmit, reset, formState } = useForm<AddRoleInput>({
    resolver: zodResolver(AddRoleSchema),
    defaultValues: { role_code: grantable[0] ?? 'CLIENT_VIEWER' },
  });

  useEffect(() => {
    if (open) reset({ role_code: grantable[0] ?? 'CLIENT_VIEWER' });
  }, [open, grantable, reset]);

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
        <select
          id="assign-role-code"
          {...register('role_code')}
          className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
          data-testid="assign-role-code"
          disabled={grantable.length === 0}
        >
          {grantable.length === 0 ? (
            <option value="">{t('users.assignRole.noneLeft')}</option>
          ) : null}
          {grantable.map((rc) => (
            <option key={rc} value={rc}>
              {t(`users.role.${rc}`, { defaultValue: rc })}
            </option>
          ))}
        </select>
        {grantable.length === 0 ? (
          <p className="text-xs text-text-muted mt-1">{t('users.assignRole.noneLeftHint')}</p>
        ) : null}
      </div>
      {formState.isSubmitting ? <p className="text-xs text-text-muted">{t('app.loading')}</p> : null}
    </DrawerForm>
  );
}
