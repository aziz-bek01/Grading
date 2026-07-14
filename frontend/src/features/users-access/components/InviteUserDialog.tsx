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
 *   DATA-DRIVEN from the backend role catalog (`GET /roles?assignableOnly=true`)
 *   via the shared <RolePicker />. Every assignable role — including future
 *   custom roles — appears; roles the caller may not grant are shown disabled
 *   with a reason. The backend re-enforces grant authority on submit.
 */
import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { DrawerForm } from '@/shared/components/data-table/DrawerForm';
import { PasswordField } from '@/shared/components/form/PasswordField';
import { ApiError } from '@/shared/api/apiError';
import { fieldA11y, fieldErrorId } from '@/shared/lib/fieldA11y';
import { useAuthStore } from '@/features/auth/authStore';
import { RolePicker } from './RolePicker';
import {
  InviteUserSchema,
  generateStrongPassword,
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

  const [showPassword, setShowPassword] = useState(false);

  const defaultValues = useMemo<InviteUserInput>(
    () => ({
      email: '',
      full_name: '',
      locale: user?.locale ?? 'ru-RU',
      password: '',
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
    setValue,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<InviteUserInput>({
    resolver: zodResolver(InviteUserSchema),
    defaultValues,
  });

  useEffect(() => {
    if (open) reset(defaultValues);
  }, [open, defaultValues, reset]);

  // Always hide the (cleartext) password when the drawer is dismissed so a
  // freshly reopened invite never starts with a revealed value.
  const handleClose = () => {
    setShowPassword(false);
    onClose();
  };

  const submit = handleSubmit(async (data) => {
    const payload: InviteUserPayload = {
      email: data.email,
      full_name: data.full_name,
      locale: data.locale,
      password: data.password,
      role_codes: data.role_codes,
      // Only super-admins can override; for everyone else the backend
      // derives from JWT and we MUST NOT include tenant_id.
      ...(isSuperAdmin && data.tenant_id ? { tenant_id: data.tenant_id } : {}),
    };
    try {
      await onSubmit(payload);
      handleClose();
    } catch (err) {
      // Map known backend error codes to an inline field error and keep the
      // drawer open so the admin can correct the input. Unknown errors are
      // re-thrown for the surrounding mutation/toast handling.
      if (err instanceof ApiError) {
        const code = err.code;
        if (code === 'USER_INVITE_WEAK_PASSWORD') {
          setShowPassword(true);
          setError('password', { type: 'server', message: 'validation_password_complexity' });
          return;
        }
        if (code === 'USER_INVITE_UNKNOWN_ROLE' || code === 'UNKNOWN_ROLE_CODE') {
          setError('role_codes', { type: 'server', message: 'validation_roles_unknown' });
          return;
        }
      }
      throw err;
    }
  });

  const onGeneratePassword = () => {
    const generated = generateStrongPassword();
    setValue('password', generated, { shouldValidate: true, shouldDirty: true });
    setShowPassword(true);
  };

  const errKey = (k: string | undefined) =>
    k ? t(`users.invite.${k}`, { defaultValue: k }) : undefined;

  return (
    <DrawerForm
      open={open}
      title={t('users.invite.title')}
      subtitle={t('users.invite.subtitle')}
      onClose={handleClose}
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
          {...fieldA11y('invite-email', !!errors.email)}
          className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
          data-testid="invite-email"
        />
        {errors.email ? (
          <p id={fieldErrorId('invite-email')} className="text-xs text-danger-700 mt-1" role="alert">
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
          {...fieldA11y('invite-fullname', !!errors.full_name)}
          className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
          data-testid="invite-fullname"
        />
        {errors.full_name ? (
          <p id={fieldErrorId('invite-fullname')} className="text-xs text-danger-700 mt-1" role="alert">
            {errKey(errors.full_name.message)}
          </p>
        ) : null}
      </div>

      <PasswordField
        id="invite-password"
        label={t('users.invite.password')}
        required
        showPassword={showPassword}
        onToggleShow={() => setShowPassword((v) => !v)}
        onGenerate={onGeneratePassword}
        hint={t('users.invite.passwordHint')}
        error={errKey(errors.password?.message)}
        showLabel={t('users.invite.passwordShow')}
        hideLabel={t('users.invite.passwordHide')}
        generateLabel={t('users.invite.passwordGenerate')}
        testId="invite-password"
        toggleTestId="invite-password-toggle"
        generateTestId="invite-password-generate"
        registration={register('password')}
      />

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
          <fieldset
            className="border border-border rounded-md p-3"
            {...fieldA11y('invite-roles', !!fieldState.error)}
          >
            <legend className="text-sm font-medium text-text-primary px-1">
              {t('users.invite.roles')} <span className="text-danger-700">*</span>
            </legend>
            <p className="text-xs text-text-muted mb-2">{t('users.invite.rolesHint')}</p>
            <RolePicker
              mode="multi"
              value={field.value ?? []}
              onChange={field.onChange}
              testIdPrefix="invite-role"
            />
            {fieldState.error ? (
              <p id={fieldErrorId('invite-roles')} className="text-xs text-danger-700 mt-2" role="alert">
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
