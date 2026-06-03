/**
 * Invite User dialog.
 *
 * Renders inside the existing <DrawerForm /> shell so we inherit the
 * Esc-to-close / focus-trap / submit button conventions. Form state is
 * managed by react-hook-form + Zod (see userSchemas.ts).
 *
 * Tenant scope:
 *   - For non-HRLAB_SUPER_ADMIN callers, `tenant_id` is dropped from the
 *     payload — the backend derives the tenant from the JWT.
 *   - For HRLAB_SUPER_ADMIN, a tenant selector is shown so the invite
 *     can be targeted across tenants.
 *
 * Role catalogue:
 *   - CLIENT_GRANTABLE_ROLES for client admins.
 *   - SUPER_ADMIN_GRANTABLE_ROLES (full list) for super-admins.
 */
import { useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { DrawerForm } from '@/shared/components/data-table/DrawerForm';
import { useAuthStore } from '@/features/auth/authStore';
import type { RoleCode } from '@/shared/auth/authTypes';
import {
  CLIENT_GRANTABLE_ROLES,
  InviteUserSchema,
  SUPER_ADMIN_GRANTABLE_ROLES,
  type InviteUserInput,
} from '../schemas/userSchemas';
import type { InviteUserPayload } from '../types/userTypes';

interface InviteUserDialogProps {
  open: boolean;
  onClose: () => void;
  onSubmit: (payload: InviteUserPayload) => Promise<void> | void;
}

export function InviteUserDialog({ open, onClose, onSubmit }: InviteUserDialogProps) {
  const { t } = useTranslation();
  const user = useAuthStore((s) => s.user);
  const activeTenant = useAuthStore((s) => s.activeTenant);
  const isSuperAdmin = user?.roles.includes('HRLAB_SUPER_ADMIN') ?? false;

  const grantableRoles: readonly RoleCode[] = isSuperAdmin
    ? SUPER_ADMIN_GRANTABLE_ROLES
    : CLIENT_GRANTABLE_ROLES;

  const defaultValues = useMemo<InviteUserInput>(
    () => ({
      email: '',
      full_name: '',
      locale: user?.locale ?? 'ru-RU',
      role_codes: [],
      tenant_id: isSuperAdmin ? activeTenant?.id : undefined,
    }),
    [user?.locale, isSuperAdmin, activeTenant?.id],
  );

  const {
    register,
    handleSubmit,
    control,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<InviteUserInput>({
    resolver: zodResolver(InviteUserSchema),
    defaultValues,
  });

  useEffect(() => {
    if (open) reset(defaultValues);
  }, [open, defaultValues, reset]);

  const submit = handleSubmit(async (data) => {
    const payload: InviteUserPayload = {
      email: data.email,
      full_name: data.full_name,
      locale: data.locale,
      role_codes: data.role_codes,
      // Only super-admins can override; for everyone else the backend
      // derives from JWT and we MUST NOT include tenant_id.
      ...(isSuperAdmin && data.tenant_id ? { tenant_id: data.tenant_id } : {}),
    };
    await onSubmit(payload);
    onClose();
  });

  const errKey = (k: string | undefined) =>
    k ? t(`users.invite.${k}`, { defaultValue: k }) : undefined;

  return (
    <DrawerForm
      open={open}
      title={t('users.invite.title')}
      subtitle={t('users.invite.subtitle')}
      onClose={onClose}
      onSubmit={submit}
      submitLabel={t('users.invite.submit')}
    >
      <div>
        <label htmlFor="invite-email" className="text-sm font-medium text-text-primary">
          {t('users.invite.email')} <span className="text-danger-700">*</span>
        </label>
        <input
          id="invite-email"
          type="email"
          autoComplete="off"
          {...register('email')}
          className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
          data-testid="invite-email"
        />
        {errors.email ? (
          <p className="text-xs text-danger-700 mt-1" role="alert">
            {errKey(errors.email.message)}
          </p>
        ) : null}
      </div>

      <div>
        <label htmlFor="invite-fullname" className="text-sm font-medium text-text-primary">
          {t('users.invite.fullName')} <span className="text-danger-700">*</span>
        </label>
        <input
          id="invite-fullname"
          type="text"
          autoComplete="off"
          {...register('full_name')}
          className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
          data-testid="invite-fullname"
        />
        {errors.full_name ? (
          <p className="text-xs text-danger-700 mt-1" role="alert">
            {errKey(errors.full_name.message)}
          </p>
        ) : null}
      </div>

      <div>
        <label htmlFor="invite-locale" className="text-sm font-medium text-text-primary">
          {t('users.invite.locale')} <span className="text-danger-700">*</span>
        </label>
        <select
          id="invite-locale"
          {...register('locale')}
          className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
          data-testid="invite-locale"
        >
          <option value="ru-RU">{t('language.ru-RU')}</option>
          <option value="uz-Cyrl-UZ">{t('language.uz-Cyrl-UZ')}</option>
          <option value="uz-Latn-UZ">{t('language.uz-Latn-UZ')}</option>
          <option value="en-US">{t('language.en-US')}</option>
        </select>
      </div>

      {isSuperAdmin ? (
        <div>
          <label htmlFor="invite-tenant" className="text-sm font-medium text-text-primary">
            {t('users.invite.tenant')}
          </label>
          <select
            id="invite-tenant"
            {...register('tenant_id')}
            className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
            data-testid="invite-tenant"
          >
            {user?.tenants.map((tn) => (
              <option key={tn.id} value={tn.id}>
                {tn.brand_name}
              </option>
            ))}
          </select>
          <p className="text-xs text-text-muted mt-1">{t('users.invite.tenantHint')}</p>
        </div>
      ) : (
        <p className="text-xs text-text-muted">
          {t('users.invite.tenantImpliedHint', { tenant: activeTenant?.brand_name ?? '' })}
        </p>
      )}

      <Controller
        control={control}
        name="role_codes"
        render={({ field, fieldState }) => (
          <fieldset className="border border-border rounded-md p-3">
            <legend className="text-sm font-medium text-text-primary px-1">
              {t('users.invite.roles')} <span className="text-danger-700">*</span>
            </legend>
            <p className="text-xs text-text-muted mb-2">{t('users.invite.rolesHint')}</p>
            <div className="grid grid-cols-1 gap-1.5 max-h-44 overflow-y-auto">
              {grantableRoles.map((rc) => {
                const checked = field.value?.includes(rc) ?? false;
                return (
                  <label
                    key={rc}
                    className="inline-flex items-center gap-2 text-sm text-text-primary"
                  >
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={(e) => {
                        const next = new Set(field.value ?? []);
                        if (e.target.checked) next.add(rc);
                        else next.delete(rc);
                        field.onChange([...next]);
                      }}
                      className="h-4 w-4 rounded border-border-strong text-primary-500 focus:ring-primary-500"
                      data-testid={`invite-role-${rc}`}
                    />
                    <span>{t(`users.role.${rc}`, { defaultValue: rc })}</span>
                  </label>
                );
              })}
            </div>
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
