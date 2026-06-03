/**
 * Edit-tenant drawer.
 *
 * Editable fields per the F-3 contract: display_name + default_locale only.
 * Slug / status / client-company linkage are immutable here (slug renames
 * have multi-side effects on subdomain routing — out of scope for MVP 2).
 */
import { useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { DrawerForm } from '@/shared/components/data-table/DrawerForm';
import { UpdateTenantSchema, type UpdateTenantInput } from '../schemas/clientSchemas';
import type { TenantDetail, UpdateTenantPayload } from '../types/clientTypes';

interface EditTenantDialogProps {
  open: boolean;
  tenant: TenantDetail | null;
  onClose: () => void;
  onSubmit: (payload: UpdateTenantPayload) => Promise<void> | void;
}

export function EditTenantDialog({ open, tenant, onClose, onSubmit }: EditTenantDialogProps) {
  const { t } = useTranslation();

  const defaultValues = useMemo<UpdateTenantInput>(
    () => ({
      display_name: tenant?.display_name ?? '',
      default_locale: tenant?.default_locale ?? 'ru-RU',
    }),
    [tenant?.display_name, tenant?.default_locale],
  );

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<UpdateTenantInput>({
    resolver: zodResolver(UpdateTenantSchema),
    defaultValues,
  });

  useEffect(() => {
    if (open) reset(defaultValues);
  }, [open, defaultValues, reset]);

  const submit = handleSubmit(async (data) => {
    const payload: UpdateTenantPayload = {};
    if (data.display_name !== undefined) payload.display_name = data.display_name;
    if (data.default_locale !== undefined) payload.default_locale = data.default_locale;
    await onSubmit(payload);
    onClose();
  });

  const errKey = (k: string | undefined) =>
    k ? t(`clients.edit_tenant.${k}`, { defaultValue: k }) : undefined;

  return (
    <DrawerForm
      open={open}
      title={t('clients.edit_tenant.title')}
      subtitle={t('clients.edit_tenant.subtitle')}
      onClose={onClose}
      onSubmit={submit}
      submitLabel={t('common.save')}
    >
      <div>
        <label htmlFor="edit-tenant-name" className="text-sm font-medium text-text-primary">
          {t('clients.edit_tenant.display_name')} <span className="text-danger-700">*</span>
        </label>
        <input
          id="edit-tenant-name"
          type="text"
          autoComplete="off"
          {...register('display_name')}
          className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
          data-testid="edit-tenant-display-name"
        />
        {errors.display_name ? (
          <p className="text-xs text-danger-700 mt-1" role="alert">
            {errKey(errors.display_name.message)}
          </p>
        ) : null}
      </div>

      <div>
        <label htmlFor="edit-tenant-locale" className="text-sm font-medium text-text-primary">
          {t('clients.edit_tenant.default_locale')}
        </label>
        <select
          id="edit-tenant-locale"
          {...register('default_locale')}
          className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
          data-testid="edit-tenant-locale"
        >
          <option value="ru-RU">{t('language.ru-RU')}</option>
          <option value="uz-Cyrl-UZ">{t('language.uz-Cyrl-UZ')}</option>
          <option value="uz-Latn-UZ">{t('language.uz-Latn-UZ')}</option>
          <option value="en-US">{t('language.en-US')}</option>
        </select>
        <p className="text-xs text-text-muted mt-1">{t('clients.edit_tenant.default_locale_hint')}</p>
      </div>

      <p className="text-xs text-text-muted">{t('clients.edit_tenant.immutable_hint')}</p>

      {isSubmitting ? <p className="text-xs text-text-muted">{t('app.loading')}</p> : null}
    </DrawerForm>
  );
}
