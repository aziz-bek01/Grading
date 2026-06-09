/**
 * Reset login / Re-send invitation dialog.
 *
 * Renders inside the shared <DrawerForm /> shell (Esc-to-close / focus-trap /
 * submit conventions) and reuses the SHARED password helpers from
 * userSchemas.ts ({@link passwordField} via {@link ResetLoginSchema} +
 * {@link generateStrongPassword}) plus the same show/hide toggle markup as
 * {@link InviteUserDialog} / {@link EditUserDialog} — no duplication.
 *
 * Submit posts the admin-chosen password to POST /users/:id/reset-login. The
 * backend re-provisions the user's IdP (login) account by their grading email
 * (creating a fresh ACTIVE account if missing / mis-linked) and sets the
 * password. This is the recovery path for users whose login was never activated
 * (e.g. the edit-password `USER_IDP_NOT_INITIALIZED` case).
 *
 * Error mapping (inline, drawer stays open so the admin can correct & retry):
 *   - USER_INVITE_WEAK_PASSWORD   → complexity message on the password field
 *   - USER_IDP_NOT_INITIALIZED    → not-initialized message
 *   - USER_IDP_PASSWORD_REJECTED  → provider-rejected message
 *   - USER_NO_IDP_ACCOUNT         → no-account message
 *   - 502 IDP_PROVISIONING_FAILED → general "try again later" message
 */
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Eye, EyeOff, Wand2 } from 'lucide-react';
import { DrawerForm } from '@/shared/components/data-table/DrawerForm';
import { ApiError } from '@/shared/api/apiError';
import {
  ResetLoginSchema,
  generateStrongPassword,
  type ResetLoginInput,
} from '../schemas/userSchemas';
import type { ResetLoginPayload } from '../types/userTypes';

interface ResetLoginDialogProps {
  open: boolean;
  onClose: () => void;
  onSubmit: (payload: ResetLoginPayload) => Promise<void> | void;
}

export function ResetLoginDialog({ open, onClose, onSubmit }: ResetLoginDialogProps) {
  const { t } = useTranslation();
  const [showPassword, setShowPassword] = useState(false);
  // General (non-field) error banner — used for 502 transient failures.
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ResetLoginInput>({
    resolver: zodResolver(ResetLoginSchema),
    defaultValues: { password: '' },
  });

  useEffect(() => {
    if (open) reset({ password: '' });
  }, [open, reset]);

  // Never leave a cleartext password revealed across reopens.
  const handleClose = () => {
    setShowPassword(false);
    setFormError(null);
    onClose();
  };

  const submit = handleSubmit(async (data) => {
    setFormError(null);
    const payload: ResetLoginPayload = { password: data.password };
    try {
      await onSubmit(payload);
      handleClose();
    } catch (err) {
      if (err instanceof ApiError) {
        const code = err.code;
        if (code === 'USER_INVITE_WEAK_PASSWORD') {
          setShowPassword(true);
          setError('password', { type: 'server', message: 'validation_password_complexity' });
          return;
        }
        if (code === 'USER_IDP_NOT_INITIALIZED') {
          setShowPassword(true);
          setError('password', { type: 'server', message: 'validation_idp_not_initialized' });
          return;
        }
        if (code === 'USER_IDP_PASSWORD_REJECTED') {
          setShowPassword(true);
          setError('password', { type: 'server', message: 'validation_idp_password_rejected' });
          return;
        }
        if (code === 'USER_NO_IDP_ACCOUNT') {
          setError('password', { type: 'server', message: 'validation_no_idp_account' });
          return;
        }
        // 502 IDP_PROVISIONING_FAILED (or any other transient) → general banner.
        if (err.status === 502 || code === 'IDP_PROVISIONING_FAILED') {
          setFormError(t('users.resetLogin.errorTryAgain'));
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
    k ? t(`users.resetLogin.${k}`, { defaultValue: k }) : undefined;

  return (
    <DrawerForm
      open={open}
      title={t('users.resetLogin.title')}
      subtitle={t('users.resetLogin.subtitle')}
      onClose={handleClose}
      onSubmit={submit}
      submitLabel={t('users.resetLogin.submit')}
    >
      {formError ? (
        <div
          role="alert"
          className="rounded-md border border-danger-500/30 bg-danger-50 px-3 py-2 text-sm text-danger-700"
          data-testid="reset-login-error"
        >
          {formError}
        </div>
      ) : null}

      <div>
        <label htmlFor="reset-login-password" className="text-sm font-medium text-text-primary">
          {t('users.resetLogin.password')} <span className="text-danger-700">*</span>
        </label>
        <div className="mt-1 flex items-stretch gap-2">
          <div className="relative flex-1">
            <input
              id="reset-login-password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="new-password"
              {...register('password')}
              className="w-full h-10 pl-3 pr-10 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
              data-testid="reset-login-password-input"
            />
            <button
              type="button"
              onClick={() => setShowPassword((v) => !v)}
              aria-label={
                showPassword
                  ? t('users.resetLogin.passwordHide')
                  : t('users.resetLogin.passwordShow')
              }
              aria-pressed={showPassword}
              className="absolute inset-y-0 right-0 flex items-center px-3 text-text-secondary hover:text-text-primary"
              data-testid="reset-login-password-toggle"
            >
              {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
            </button>
          </div>
          <button
            type="button"
            onClick={onGeneratePassword}
            className="inline-flex items-center gap-1.5 h-10 px-3 border border-border-strong rounded-md text-sm text-text-primary bg-surface hover:bg-divider whitespace-nowrap"
            data-testid="reset-login-password-generate"
          >
            <Wand2 size={14} />
            {t('users.resetLogin.passwordGenerate')}
          </button>
        </div>
        <p className="text-xs text-text-muted mt-1">{t('users.resetLogin.passwordHint')}</p>
        {errors.password ? (
          <p className="text-xs text-danger-700 mt-1" role="alert">
            {errKey(errors.password.message)}
          </p>
        ) : null}
      </div>

      {isSubmitting ? <p className="text-xs text-text-muted">{t('app.loading')}</p> : null}
    </DrawerForm>
  );
}
