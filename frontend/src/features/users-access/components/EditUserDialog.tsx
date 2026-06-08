/**
 * Edit User dialog.
 *
 * Mirrors {@link InviteUserDialog}: renders inside the shared <DrawerForm />
 * shell with react-hook-form + Zod ({@link UpdateUserSchema}). Edits the three
 * user-level fields the backend PATCH /users/:id accepts: `full_name`,
 * `locale`, and `status`.
 *
 * Status select intentionally offers ONLY ACTIVE / DISABLED — the only values
 * the backend honours on this endpoint (others → 400 `USER_PATCH_BAD_STATUS`,
 * which we also map to a friendly inline error). INVITED / LOCKED are
 * system-managed and never user-settable here. The dedicated Disable /
 * Reactivate button on the details page covers the common status flip; this
 * dialog is the full-profile editor.
 *
 * Email & password (extends the base profile editor):
 *   - `email` is pre-filled and editable; it is sent ONLY when it differs from
 *     the user's current address. A clash → 400 `USER_EMAIL_TAKEN`, mapped to
 *     an inline error on the email field.
 *   - "Set new password" is OPTIONAL. Left blank → password unchanged (omitted
 *     from the payload). When filled it is validated against the shared invite
 *     complexity rule and re-checked by the backend (weak → 400
 *     `USER_INVITE_WEAK_PASSWORD`; user has no IdP account →
 *     `USER_NO_IDP_ACCOUNT`). The generator + show/hide toggle are the same
 *     helpers the invite flow uses (no duplication).
 *
 * Tenant rule (D-202 / F-208): no `tenant_id` is sent — this endpoint is
 * user-scoped, not tenant-scoped.
 */
import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Eye, EyeOff, Wand2 } from 'lucide-react';
import { DrawerForm } from '@/shared/components/data-table/DrawerForm';
import { ApiError } from '@/shared/api/apiError';
import {
  EDITABLE_USER_STATUSES,
  UpdateUserSchema,
  generateStrongPassword,
  type UpdateUserInput,
} from '../schemas/userSchemas';
import type { UpdateUserPayload, UserDetails } from '../types/userTypes';

interface EditUserDialogProps {
  open: boolean;
  user: Pick<UserDetails, 'email' | 'full_name' | 'locale' | 'status'>;
  onClose: () => void;
  onSubmit: (payload: UpdateUserPayload) => Promise<void> | void;
}

export function EditUserDialog({ open, user, onClose, onSubmit }: EditUserDialogProps) {
  const { t } = useTranslation();

  const [showPassword, setShowPassword] = useState(false);

  // INVITED / LOCKED users cannot be edited to those same statuses via this
  // endpoint, so the select falls back to the nearest editable value while the
  // name/locale fields remain editable.
  const defaultStatus = useMemo<UpdateUserInput['status']>(
    () => (user.status === 'DISABLED' ? 'DISABLED' : 'ACTIVE'),
    [user.status],
  );

  const defaultValues = useMemo<UpdateUserInput>(
    () => ({
      full_name: user.full_name,
      email: user.email,
      // Always start blank: empty = leave password unchanged.
      password: '',
      locale: user.locale,
      status: defaultStatus,
    }),
    [user.full_name, user.email, user.locale, defaultStatus],
  );

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<UpdateUserInput>({
    resolver: zodResolver(UpdateUserSchema),
    defaultValues,
  });

  useEffect(() => {
    if (open) reset(defaultValues);
  }, [open, defaultValues, reset]);

  // Never leave a cleartext password revealed across reopens.
  const handleClose = () => {
    setShowPassword(false);
    onClose();
  };

  const submit = handleSubmit(async (data) => {
    const payload: UpdateUserPayload = {
      full_name: data.full_name,
      locale: data.locale,
      status: data.status,
    };
    // Send email only when it actually changed (trimmed compare).
    const nextEmail = data.email?.trim();
    if (nextEmail && nextEmail !== user.email) {
      payload.email = nextEmail;
    }
    // Send password only when the admin entered one (blank = unchanged).
    if (data.password && data.password.length > 0) {
      payload.password = data.password;
    }
    try {
      await onSubmit(payload);
      handleClose();
    } catch (err) {
      if (err instanceof ApiError) {
        const code = err.code;
        if (code === 'USER_PATCH_BAD_STATUS') {
          setError('status', { type: 'server', message: 'validation_status_invalid' });
          return;
        }
        if (code === 'USER_EMAIL_TAKEN') {
          setError('email', { type: 'server', message: 'validation_email_taken' });
          return;
        }
        if (code === 'USER_INVITE_WEAK_PASSWORD') {
          setShowPassword(true);
          setError('password', { type: 'server', message: 'validation_password_complexity' });
          return;
        }
        if (code === 'USER_NO_IDP_ACCOUNT') {
          setError('password', { type: 'server', message: 'validation_no_idp_account' });
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
    k ? t(`users.edit.${k}`, { defaultValue: k }) : undefined;

  return (
    <DrawerForm
      open={open}
      title={t('users.edit.title')}
      subtitle={t('users.edit.subtitle')}
      onClose={handleClose}
      onSubmit={submit}
      submitLabel={t('users.edit.submit')}
    >
      <div>
        <label htmlFor="edit-fullname" className="text-sm font-medium text-text-primary">
          {t('users.edit.fullName')} <span className="text-danger-700">*</span>
        </label>
        <input
          id="edit-fullname"
          type="text"
          autoComplete="off"
          {...register('full_name')}
          className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
          data-testid="edit-fullname"
        />
        {errors.full_name ? (
          <p className="text-xs text-danger-700 mt-1" role="alert">
            {errKey(errors.full_name.message)}
          </p>
        ) : null}
      </div>

      <div>
        <label htmlFor="edit-email" className="text-sm font-medium text-text-primary">
          {t('users.edit.email')} <span className="text-danger-700">*</span>
        </label>
        <input
          id="edit-email"
          type="email"
          autoComplete="off"
          {...register('email')}
          className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
          data-testid="edit-email"
        />
        {errors.email ? (
          <p className="text-xs text-danger-700 mt-1" role="alert">
            {errKey(errors.email.message)}
          </p>
        ) : null}
      </div>

      <div>
        <label htmlFor="edit-password" className="text-sm font-medium text-text-primary">
          {t('users.edit.password')}
        </label>
        <div className="mt-1 flex items-stretch gap-2">
          <div className="relative flex-1">
            <input
              id="edit-password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="new-password"
              {...register('password')}
              className="w-full h-10 pl-3 pr-10 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
              data-testid="edit-password"
            />
            <button
              type="button"
              onClick={() => setShowPassword((v) => !v)}
              aria-label={showPassword ? t('users.edit.passwordHide') : t('users.edit.passwordShow')}
              aria-pressed={showPassword}
              className="absolute inset-y-0 right-0 flex items-center px-3 text-text-secondary hover:text-text-primary"
              data-testid="edit-password-toggle"
            >
              {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
            </button>
          </div>
          <button
            type="button"
            onClick={onGeneratePassword}
            className="inline-flex items-center gap-1.5 h-10 px-3 border border-border-strong rounded-md text-sm text-text-primary bg-surface hover:bg-divider whitespace-nowrap"
            data-testid="edit-password-generate"
          >
            <Wand2 size={14} />
            {t('users.edit.passwordGenerate')}
          </button>
        </div>
        <p className="text-xs text-text-muted mt-1">{t('users.edit.passwordHint')}</p>
        {errors.password ? (
          <p className="text-xs text-danger-700 mt-1" role="alert">
            {errKey(errors.password.message)}
          </p>
        ) : null}
      </div>

      <div>
        <label htmlFor="edit-locale" className="text-sm font-medium text-text-primary">
          {t('users.edit.locale')} <span className="text-danger-700">*</span>
        </label>
        <select
          id="edit-locale"
          {...register('locale')}
          className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
          data-testid="edit-locale"
        >
          <option value="ru-RU">{t('language.ru-RU')}</option>
          <option value="uz-Cyrl-UZ">{t('language.uz-Cyrl-UZ')}</option>
          <option value="uz-Latn-UZ">{t('language.uz-Latn-UZ')}</option>
          <option value="en-US">{t('language.en-US')}</option>
        </select>
      </div>

      <div>
        <label htmlFor="edit-status" className="text-sm font-medium text-text-primary">
          {t('users.edit.status')} <span className="text-danger-700">*</span>
        </label>
        <select
          id="edit-status"
          {...register('status')}
          className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
          data-testid="edit-status"
        >
          {EDITABLE_USER_STATUSES.map((s) => (
            <option key={s} value={s}>
              {t(`users.status.${s.toLowerCase()}`)}
            </option>
          ))}
        </select>
        <p className="text-xs text-text-muted mt-1">{t('users.edit.statusHint')}</p>
        {errors.status ? (
          <p className="text-xs text-danger-700 mt-1" role="alert">
            {errKey(errors.status.message)}
          </p>
        ) : null}
      </div>

      {isSubmitting ? <p className="text-xs text-text-muted">{t('app.loading')}</p> : null}
    </DrawerForm>
  );
}
