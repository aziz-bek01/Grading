/**
 * Add Membership dialog ("Add to company").
 *
 * Cross-tenant action (POST /users/:id/memberships) used by super-admins to
 * attach an existing user to another tenant with a starting set of roles.
 * Mirrors the {@link InviteUserDialog} role multi-select; tenant is chosen
 * explicitly because this endpoint targets a NEW membership row (the path/body
 * `tenant_id` here is the membership target, NOT a tenant-scope marker, so —
 * unlike business forms — it is required and sent).
 *
 * Tenants already held by the user are excluded from the picker so the UI can't
 * trigger the backend's `MEMBERSHIP_EXISTS` (409). The role picker is
 * DATA-DRIVEN from the backend role catalog (`GET /roles?assignableOnly=true`)
 * via the shared <RolePicker /> — the same picker the invite/assign dialogs use.
 */
import { useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { DrawerForm } from '@/shared/components/data-table/DrawerForm';
import { ApiError } from '@/shared/api/apiError';
import { useAuthStore } from '@/features/auth/authStore';
import { RolePicker } from './RolePicker';
import { AddMembershipSchema, type AddMembershipInput } from '../schemas/userSchemas';
import type { AddMembershipPayload } from '../types/userTypes';

interface AddMembershipDialogProps {
  open: boolean;
  /** Tenant ids the user already belongs to — excluded from the picker. */
  existingTenantIds: string[];
  onClose: () => void;
  onSubmit: (payload: AddMembershipPayload) => Promise<void> | void;
}

export function AddMembershipDialog({
  open,
  existingTenantIds,
  onClose,
  onSubmit,
}: AddMembershipDialogProps) {
  const { t } = useTranslation();
  const user = useAuthStore((s) => s.user);

  const availableTenants = useMemo(
    () => (user?.tenants ?? []).filter((tn) => !existingTenantIds.includes(tn.id)),
    [user?.tenants, existingTenantIds],
  );

  const defaultValues = useMemo<AddMembershipInput>(
    () => ({
      tenant_id: availableTenants[0]?.id ?? '',
      role_codes: [],
    }),
    [availableTenants],
  );

  const {
    register,
    handleSubmit,
    control,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<AddMembershipInput>({
    resolver: zodResolver(AddMembershipSchema),
    defaultValues,
  });

  useEffect(() => {
    if (open) reset(defaultValues);
  }, [open, defaultValues, reset]);

  const submit = handleSubmit(async (data) => {
    const payload: AddMembershipPayload = {
      tenant_id: data.tenant_id,
      role_codes: data.role_codes,
    };
    try {
      await onSubmit(payload);
      onClose();
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.code === 'MEMBERSHIP_EXISTS') {
          setError('tenant_id', { type: 'server', message: 'validation_membership_exists' });
          return;
        }
        if (err.code === 'UNKNOWN_ROLE_CODE' || err.code === 'USER_INVITE_UNKNOWN_ROLE') {
          setError('role_codes', { type: 'server', message: 'validation_roles_unknown' });
          return;
        }
      }
      throw err;
    }
  });

  const errKey = (k: string | undefined) =>
    k ? t(`users.addMembership.${k}`, { defaultValue: k }) : undefined;

  return (
    <DrawerForm
      open={open}
      title={t('users.addMembership.title')}
      subtitle={t('users.addMembership.subtitle')}
      onClose={onClose}
      onSubmit={submit}
      submitLabel={t('users.addMembership.submit')}
    >
      <div>
        <label htmlFor="add-membership-tenant" className="text-sm font-medium text-text-primary">
          {t('users.addMembership.tenant')} <span className="text-danger-700">*</span>
        </label>
        <select
          id="add-membership-tenant"
          {...register('tenant_id')}
          className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
          data-testid="add-membership-tenant"
          disabled={availableTenants.length === 0}
        >
          {availableTenants.length === 0 ? (
            <option value="">{t('users.addMembership.noneLeft')}</option>
          ) : null}
          {availableTenants.map((tn) => (
            <option key={tn.id} value={tn.id}>
              {tn.brand_name}
            </option>
          ))}
        </select>
        {availableTenants.length === 0 ? (
          <p className="text-xs text-text-muted mt-1">{t('users.addMembership.noneLeftHint')}</p>
        ) : null}
        {errors.tenant_id ? (
          <p className="text-xs text-danger-700 mt-1" role="alert">
            {errKey(errors.tenant_id.message)}
          </p>
        ) : null}
      </div>

      <Controller
        control={control}
        name="role_codes"
        render={({ field, fieldState }) => (
          <fieldset className="border border-border rounded-md p-3">
            <legend className="text-sm font-medium text-text-primary px-1">
              {t('users.addMembership.roles')} <span className="text-danger-700">*</span>
            </legend>
            <p className="text-xs text-text-muted mb-2">{t('users.addMembership.rolesHint')}</p>
            <RolePicker
              mode="multi"
              value={field.value ?? []}
              onChange={field.onChange}
              testIdPrefix="add-membership-role"
            />
            {fieldState.error ? (
              <p className="text-xs text-danger-700 mt-2" role="alert">
                {errKey(fieldState.error.message)}
              </p>
            ) : null}
          </fieldset>
        )}
      />

      {isSubmitting ? <p className="text-xs text-text-muted">{t('app.loading')}</p> : null}
    </DrawerForm>
  );
}
