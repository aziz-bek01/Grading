/**
 * Edit-client-company drawer (legal entity behind a tenant).
 *
 * Editable: legal_name, brand_name, industry, country_code, tax_id.
 * The client-company id and creation date are immutable.
 */
import { useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { DrawerForm } from '@/shared/components/data-table/DrawerForm';
import {
  UpdateClientCompanySchema,
  type UpdateClientCompanyInput,
} from '../schemas/clientSchemas';
import type { ClientCompany, UpdateClientCompanyPayload } from '../types/clientTypes';

interface EditClientDialogProps {
  open: boolean;
  client: ClientCompany | null;
  onClose: () => void;
  onSubmit: (payload: UpdateClientCompanyPayload) => Promise<void> | void;
}

export function EditClientDialog({ open, client, onClose, onSubmit }: EditClientDialogProps) {
  const { t } = useTranslation();

  const defaultValues = useMemo<UpdateClientCompanyInput>(
    () => ({
      legal_name: client?.legal_name ?? '',
      brand_name: client?.brand_name ?? '',
      industry: client?.industry ?? '',
      country_code: client?.country_code ?? '',
      tax_id: client?.tax_id ?? '',
    }),
    [client?.legal_name, client?.brand_name, client?.industry, client?.country_code, client?.tax_id],
  );

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<UpdateClientCompanyInput>({
    resolver: zodResolver(UpdateClientCompanySchema),
    defaultValues,
  });

  useEffect(() => {
    if (open) reset(defaultValues);
  }, [open, defaultValues, reset]);

  const submit = handleSubmit(async (data) => {
    const payload: UpdateClientCompanyPayload = {
      legal_name: data.legal_name?.trim() || undefined,
      brand_name: data.brand_name?.trim() || undefined,
      industry: data.industry === undefined ? undefined : (data.industry?.trim() || null),
      country_code:
        data.country_code === undefined
          ? undefined
          : data.country_code?.trim()
          ? data.country_code.toUpperCase()
          : null,
      tax_id: data.tax_id === undefined ? undefined : (data.tax_id?.trim() || null),
    };
    await onSubmit(payload);
    onClose();
  });

  const errKey = (k: string | undefined) =>
    k ? t(`clients.edit_client.${k}`, { defaultValue: k }) : undefined;

  return (
    <DrawerForm
      open={open}
      title={t('clients.edit_client.title')}
      subtitle={t('clients.edit_client.subtitle')}
      onClose={onClose}
      onSubmit={submit}
      submitLabel={t('common.save')}
    >
      <div>
        <label htmlFor="edit-client-legal" className="text-sm font-medium text-text-primary">
          {t('clients.edit_client.legal_name')} <span className="text-danger-700">*</span>
        </label>
        <input
          id="edit-client-legal"
          type="text"
          autoComplete="off"
          {...register('legal_name')}
          className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
          data-testid="edit-client-legal-name"
        />
        {errors.legal_name ? (
          <p className="text-xs text-danger-700 mt-1" role="alert">
            {errKey(errors.legal_name.message)}
          </p>
        ) : null}
      </div>

      <div>
        <label htmlFor="edit-client-brand" className="text-sm font-medium text-text-primary">
          {t('clients.edit_client.brand_name')} <span className="text-danger-700">*</span>
        </label>
        <input
          id="edit-client-brand"
          type="text"
          autoComplete="off"
          {...register('brand_name')}
          className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
          data-testid="edit-client-brand-name"
        />
        {errors.brand_name ? (
          <p className="text-xs text-danger-700 mt-1" role="alert">
            {errKey(errors.brand_name.message)}
          </p>
        ) : null}
      </div>

      <div>
        <label htmlFor="edit-client-industry" className="text-sm font-medium text-text-primary">
          {t('clients.edit_client.industry')}
        </label>
        <input
          id="edit-client-industry"
          type="text"
          autoComplete="off"
          {...register('industry')}
          className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
        />
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div>
          <label htmlFor="edit-client-country" className="text-sm font-medium text-text-primary">
            {t('clients.edit_client.country_code')}
          </label>
          <input
            id="edit-client-country"
            type="text"
            autoComplete="off"
            maxLength={2}
            placeholder="UZ"
            {...register('country_code')}
            className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm uppercase bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
          {errors.country_code ? (
            <p className="text-xs text-danger-700 mt-1" role="alert">
              {errKey(errors.country_code.message)}
            </p>
          ) : null}
        </div>
        <div>
          <label htmlFor="edit-client-tax" className="text-sm font-medium text-text-primary">
            {t('clients.edit_client.tax_id')}
          </label>
          <input
            id="edit-client-tax"
            type="text"
            autoComplete="off"
            {...register('tax_id')}
            className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
        </div>
      </div>

      {isSubmitting ? <p className="text-xs text-text-muted">{t('app.loading')}</p> : null}
    </DrawerForm>
  );
}
