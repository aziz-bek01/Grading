/**
 * Create company-client (tenant) drawer.
 *
 * Mirrors the EditTenant / InviteUser drawer pattern: renders inside the shared
 * <DrawerForm /> shell, React Hook Form + Zod validation, inline field errors.
 *
 * Fields match the backend `CreateTenantRequest.java` contract exactly:
 *   slug · display_name · default_locale · isolation_mode ·
 *   company_legal_name · company_brand_name? · company_industry?
 *
 * Server-error mapping: a 409 TENANT_SLUG_TAKEN is mapped to a friendly,
 * field-level "slug already taken" message under the slug input rather than a
 * raw error. The backend remains the authoritative validator (slug regex +
 * uniqueness); the Zod schema is only a first-line UX guard.
 *
 * Tenant rule: tenant_id is NEVER part of this body — the new tenant id is
 * minted by the backend and returned in the response.
 */
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { DrawerForm } from '@/shared/components/data-table/DrawerForm';
import { ApiError } from '@/shared/api/apiError';
import { CreateTenantSchema, type CreateTenantInput } from '../schemas/clientSchemas';
import type { CreateTenantPayload } from '../types/clientTypes';

interface CreateTenantDialogProps {
  open: boolean;
  onClose: () => void;
  onSubmit: (payload: CreateTenantPayload) => Promise<void> | void;
}

const DEFAULTS: CreateTenantInput = {
  slug: '',
  display_name: '',
  default_locale: 'ru-RU',
  isolation_mode: 'SCHEMA',
  company_legal_name: '',
  company_brand_name: '',
  company_industry: '',
};

export function CreateTenantDialog({ open, onClose, onSubmit }: CreateTenantDialogProps) {
  const { t } = useTranslation();
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    setError,
    setFocus,
    formState: { errors, isSubmitting },
  } = useForm<CreateTenantInput>({
    resolver: zodResolver(CreateTenantSchema),
    defaultValues: DEFAULTS,
  });

  // Reset the form fields whenever the drawer (re)opens. Server-error state is
  // cleared in handleClose() instead, keeping setState out of this effect
  // (react-hooks/set-state-in-effect).
  useEffect(() => {
    if (open) {
      reset(DEFAULTS);
    }
  }, [open, reset]);

  const handleClose = () => {
    setServerError(null);
    onClose();
  };

  const submit = handleSubmit(async (data) => {
    setServerError(null);
    const payload: CreateTenantPayload = {
      slug: data.slug,
      display_name: data.display_name,
      default_locale: data.default_locale,
      isolation_mode: data.isolation_mode,
      company_legal_name: data.company_legal_name,
      // Drop empty optionals so we never POST empty strings for nullable fields.
      ...(data.company_brand_name ? { company_brand_name: data.company_brand_name } : {}),
      ...(data.company_industry ? { company_industry: data.company_industry } : {}),
    };
    try {
      await onSubmit(payload);
      handleClose();
    } catch (err) {
      // Map the backend slug-collision to a friendly, field-level message.
      if (err instanceof ApiError && err.code === 'TENANT_SLUG_TAKEN') {
        setError('slug', { type: 'server', message: 'validation_slug_taken' });
        setFocus('slug');
        return;
      }
      setServerError(t('clients.create.server_error'));
    }
  });

  const errKey = (k: string | undefined) =>
    k ? t(`clients.create.${k}`, { defaultValue: k }) : undefined;

  return (
    <DrawerForm
      open={open}
      title={t('clients.create.title')}
      subtitle={t('clients.create.subtitle')}
      onClose={handleClose}
      onSubmit={submit}
      submitLabel={t('clients.create.submit')}
    >
      <div>
        <label htmlFor="create-tenant-slug" className="text-sm font-medium text-text-primary">
          {t('clients.create.slug')} <span className="text-danger-700">*</span>
        </label>
        <input
          id="create-tenant-slug"
          type="text"
          autoComplete="off"
          spellCheck={false}
          {...register('slug')}
          aria-invalid={!!errors.slug}
          aria-describedby={errors.slug ? 'create-tenant-slug-error' : undefined}
          className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm font-mono bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
          data-testid="create-tenant-slug"
        />
        <p className="text-xs text-text-muted mt-1">{t('clients.create.slug_hint')}</p>
        {errors.slug ? (
          <p id="create-tenant-slug-error" className="text-xs text-danger-700 mt-1" role="alert">
            {errKey(errors.slug.message)}
          </p>
        ) : null}
      </div>

      <div>
        <label htmlFor="create-tenant-display-name" className="text-sm font-medium text-text-primary">
          {t('clients.create.display_name')} <span className="text-danger-700">*</span>
        </label>
        <input
          id="create-tenant-display-name"
          type="text"
          autoComplete="off"
          {...register('display_name')}
          aria-invalid={!!errors.display_name}
          aria-describedby={errors.display_name ? 'create-tenant-display-name-error' : undefined}
          className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
          data-testid="create-tenant-display-name"
        />
        {errors.display_name ? (
          <p
            id="create-tenant-display-name-error"
            className="text-xs text-danger-700 mt-1"
            role="alert"
          >
            {errKey(errors.display_name.message)}
          </p>
        ) : null}
      </div>

      <div>
        <label htmlFor="create-tenant-legal-name" className="text-sm font-medium text-text-primary">
          {t('clients.create.legal_name')} <span className="text-danger-700">*</span>
        </label>
        <input
          id="create-tenant-legal-name"
          type="text"
          autoComplete="off"
          {...register('company_legal_name')}
          aria-invalid={!!errors.company_legal_name}
          aria-describedby={errors.company_legal_name ? 'create-tenant-legal-name-error' : undefined}
          className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
          data-testid="create-tenant-legal-name"
        />
        {errors.company_legal_name ? (
          <p
            id="create-tenant-legal-name-error"
            className="text-xs text-danger-700 mt-1"
            role="alert"
          >
            {errKey(errors.company_legal_name.message)}
          </p>
        ) : null}
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div>
          <label htmlFor="create-tenant-brand-name" className="text-sm font-medium text-text-primary">
            {t('clients.create.brand_name')}
          </label>
          <input
            id="create-tenant-brand-name"
            type="text"
            autoComplete="off"
            {...register('company_brand_name')}
            aria-invalid={!!errors.company_brand_name}
            aria-describedby={errors.company_brand_name ? 'create-tenant-brand-name-error' : undefined}
            className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
            data-testid="create-tenant-brand-name"
          />
          {errors.company_brand_name ? (
            <p
              id="create-tenant-brand-name-error"
              className="text-xs text-danger-700 mt-1"
              role="alert"
            >
              {errKey(errors.company_brand_name.message)}
            </p>
          ) : null}
        </div>
        <div>
          <label htmlFor="create-tenant-industry" className="text-sm font-medium text-text-primary">
            {t('clients.create.industry')}
          </label>
          <input
            id="create-tenant-industry"
            type="text"
            autoComplete="off"
            {...register('company_industry')}
            aria-invalid={!!errors.company_industry}
            aria-describedby={errors.company_industry ? 'create-tenant-industry-error' : undefined}
            className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
            data-testid="create-tenant-industry"
          />
          {errors.company_industry ? (
            <p
              id="create-tenant-industry-error"
              className="text-xs text-danger-700 mt-1"
              role="alert"
            >
              {errKey(errors.company_industry.message)}
            </p>
          ) : null}
        </div>
      </div>

      <div>
        <label htmlFor="create-tenant-locale" className="text-sm font-medium text-text-primary">
          {t('clients.create.default_locale')} <span className="text-danger-700">*</span>
        </label>
        <select
          id="create-tenant-locale"
          {...register('default_locale')}
          className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
          data-testid="create-tenant-locale"
        >
          <option value="ru-RU">{t('language.ru-RU')}</option>
          <option value="uz-Cyrl-UZ">{t('language.uz-Cyrl-UZ')}</option>
          <option value="uz-Latn-UZ">{t('language.uz-Latn-UZ')}</option>
          <option value="en-US">{t('language.en-US')}</option>
        </select>
        <p className="text-xs text-text-muted mt-1">{t('clients.create.default_locale_hint')}</p>
      </div>

      <div>
        <label htmlFor="create-tenant-isolation" className="text-sm font-medium text-text-primary">
          {t('clients.create.isolation_mode')} <span className="text-danger-700">*</span>
        </label>
        <select
          id="create-tenant-isolation"
          {...register('isolation_mode')}
          className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
          data-testid="create-tenant-isolation"
        >
          <option value="SCHEMA">{t('clients.create.isolation_schema')}</option>
          <option value="DATABASE">{t('clients.create.isolation_database')}</option>
        </select>
        <p className="text-xs text-text-muted mt-1">{t('clients.create.isolation_mode_hint')}</p>
      </div>

      {serverError ? (
        <p className="text-sm text-danger-700" role="alert" data-testid="create-tenant-server-error">
          {serverError}
        </p>
      ) : null}

      {isSubmitting ? <p className="text-xs text-text-muted">{t('app.loading')}</p> : null}
    </DrawerForm>
  );
}
