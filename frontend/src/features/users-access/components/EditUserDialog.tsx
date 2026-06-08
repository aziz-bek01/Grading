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
 * Tenant rule (D-202 / F-208): no `tenant_id` is sent — this endpoint is
 * user-scoped, not tenant-scoped.
 */
import { useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { DrawerForm } from '@/shared/components/data-table/DrawerForm';
import { ApiError } from '@/shared/api/apiError';
import { EDITABLE_USER_STATUSES, UpdateUserSchema, type UpdateUserInput } from '../schemas/userSchemas';
import type { UpdateUserPayload, UserDetails } from '../types/userTypes';

interface EditUserDialogProps {
  open: boolean;
  user: Pick<UserDetails, 'full_name' | 'locale' | 'status'>;
  onClose: () => void;
  onSubmit: (payload: UpdateUserPayload) => Promise<void> | void;
}

export function EditUserDialog({ open, user, onClose, onSubmit }: EditUserDialogProps) {
  const { t } = useTranslation();

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
      locale: user.locale,
      status: defaultStatus,
    }),
    [user.full_name, user.locale, defaultStatus],
  );

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<UpdateUserInput>({
    resolver: zodResolver(UpdateUserSchema),
    defaultValues,
  });

  useEffect(() => {
    if (open) reset(defaultValues);
  }, [open, defaultValues, reset]);

  const submit = handleSubmit(async (data) => {
    const payload: UpdateUserPayload = {
      full_name: data.full_name,
      locale: data.locale,
      status: data.status,
    };
    try {
      await onSubmit(payload);
      onClose();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'USER_PATCH_BAD_STATUS') {
        setError('status', { type: 'server', message: 'validation_status_invalid' });
        return;
      }
      throw err;
    }
  });

  const errKey = (k: string | undefined) =>
    k ? t(`users.edit.${k}`, { defaultValue: k }) : undefined;

  return (
    <DrawerForm
      open={open}
      title={t('users.edit.title')}
      subtitle={t('users.edit.subtitle')}
      onClose={onClose}
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
