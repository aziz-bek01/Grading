/**
 * CreateRoleDrawer — create a TENANT custom role (slice E3).
 *
 * Rendered inside the existing <DrawerForm /> shell (inherits Esc-to-close /
 * focus / submit conventions). Three sections:
 *   1. `code` — UPPER_SNAKE_CASE input with a localized helper ("system kodlari
 *      band" — system codes are reserved). Client validates the FORMAT only; the
 *      backend owns the reserved/uniqueness check (409 ROLE_CODE_RESERVED /
 *      ROLE_CODE_EXISTS), surfaced inline below the field.
 *   2. localized `name` inputs for all four locales (ru-RU required).
 *   3. the REUSED <PermissionMatrix /> for the initial granted set. Restricted
 *      rows are locked exactly as in the per-role editor; they are never part of
 *      the submitted `permission_codes` set.
 *
 * No `tenant_id` is collected or sent — the backend derives the tenant from the
 * JWT (slice convention). Backend re-enforces every permission on submit.
 */
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { DrawerForm } from '@/shared/components/data-table/DrawerForm';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { ApiError } from '@/shared/api/apiError';
import { SUPPORTED_LOCALES } from '@/shared/i18n';
import type { LocalizedString } from '@/shared/types/common';
import { useAuthStore } from '@/features/auth/authStore';
import { useCreateRole, usePermissionCatalogTemplate } from '../hooks/useRolePermissions';
import { PermissionMatrix } from './PermissionMatrix';
import { mapRolePermissionError } from '../lib/rolePermissionError';
import {
  CreateRoleSchema,
  emptyCreateRoleValues,
  type CreateRoleInput,
} from '../schemas/roleSchemas';

interface CreateRoleDrawerProps {
  open: boolean;
  onClose: () => void;
  /** Called after a successful create (parent refetches / toasts). */
  onCreated: () => void;
}

export function CreateRoleDrawer({ open, onClose, onCreated }: CreateRoleDrawerProps) {
  const { t } = useTranslation();
  const create = useCreateRole();
  const { data: catalog, isLoading, error, refetch } = usePermissionCatalogTemplate(open);

  // Raw permission codes the CURRENT caller holds on the active tenant.
  // We read user.permissions DIRECTLY (not via can() / usePermission()) because
  // can() short-circuits to true for HRLAB_SUPER_ADMIN even when the backend has
  // NOT actually granted a code (e.g. PAYROLL_IMPORT, CLIENT_VIEW, CLIENT_LIST,
  // CLIENT_UPDATE). The matrix uses this set to lock rows the caller cannot grant,
  // preventing a 422 PERMISSION_NOT_HELD_BY_CALLER on submit.
  const rawPermissions = useAuthStore((s) => s.user?.permissions);
  const callerPermissions = useMemo(
    () => new Set<string>(rawPermissions ?? []),
    [rawPermissions],
  );

  // Working set of CHECKED (granted) non-restricted codes for the new role.
  const [selected, setSelected] = useState<Set<string>>(() => new Set());

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<CreateRoleInput>({
    resolver: zodResolver(CreateRoleSchema),
    defaultValues: emptyCreateRoleValues(),
  });

  const restricted = useMemo(
    () => new Set((catalog ?? []).filter((i) => i.restricted).map((i) => i.code)),
    [catalog],
  );

  const handleClose = () => {
    create.reset();
    reset(emptyCreateRoleValues());
    setSelected(new Set());
    onClose();
  };

  const toggle = (code: string, next: boolean) => {
    setSelected((prev) => {
      const n = new Set(prev);
      if (next) n.add(code);
      else n.delete(code);
      return n;
    });
  };

  const toggleGroup = (codes: string[], next: boolean) => {
    setSelected((prev) => {
      const n = new Set(prev);
      for (const c of codes) {
        if (next) n.add(c);
        else n.delete(c);
      }
      return n;
    });
  };

  const submit = handleSubmit((data) => {
    // Only ru-RU is required; trim and drop blank locales so the payload is clean.
    const name_i18n: LocalizedString = {};
    for (const loc of SUPPORTED_LOCALES) {
      const v = (data.name_i18n as Record<string, string | undefined>)[loc]?.trim();
      if (v) name_i18n[loc] = v;
    }
    // Belt-and-braces: never send restricted codes or codes the caller doesn't
    // hold (the matrix already excludes them via locking; this is a final guard).
    const permission_codes = [...selected]
      .filter((c) => !restricted.has(c) && callerPermissions.has(c))
      .sort();

    create.mutate(
      { code: data.code.trim(), name_i18n, permission_codes },
      {
        onSuccess: () => {
          handleClose();
          onCreated();
        },
      },
    );
  });

  // Server-side code errors render inline below the code field; everything else
  // (permission errors, generic) renders in the form-level banner.
  const mapped = create.error ? mapRolePermissionError(create.error) : null;
  const codeServerError =
    create.error instanceof ApiError &&
    (create.error.code === 'ROLE_CODE_RESERVED' || create.error.code === 'ROLE_CODE_EXISTS')
      ? mapped
      : null;
  const bannerError = mapped && !codeServerError ? mapped : null;

  const errKey = (k: string | undefined) => (k ? t(k, { defaultValue: k }) : undefined);

  return (
    <DrawerForm
      open={open}
      title={t('roles.create.title')}
      subtitle={t('roles.create.subtitle')}
      onClose={handleClose}
      onSubmit={submit}
      submitLabel={create.isPending ? t('roles.create.submitting') : t('roles.create.submit')}
    >
      {bannerError ? (
        <div
          role="alert"
          className="rounded-md border border-danger-500/30 bg-danger-50 px-4 py-3 text-sm text-danger-700"
          data-testid="role-create-error"
        >
          <p>{t(bannerError.key)}</p>
          {bannerError.correlationId ? (
            <p className="text-xs text-text-muted mt-1 font-mono">
              {t('states.correlation_id')}: {bannerError.correlationId}
            </p>
          ) : null}
        </div>
      ) : null}

      <div>
        <label htmlFor="role-code" className="text-sm font-medium text-text-primary">
          {t('roles.create.code')} <span className="text-danger-700">*</span>
        </label>
        <input
          id="role-code"
          type="text"
          autoComplete="off"
          spellCheck={false}
          {...register('code')}
          className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface font-mono focus:outline-none focus:ring-2 focus:ring-primary-500"
          data-testid="role-create-code"
        />
        <p className="text-xs text-text-muted mt-1">{t('roles.create.codeHelper')}</p>
        {errors.code ? (
          <p className="text-xs text-danger-700 mt-1" role="alert" data-testid="role-create-code-error">
            {errKey(errors.code.message)}
          </p>
        ) : codeServerError ? (
          <p className="text-xs text-danger-700 mt-1" role="alert" data-testid="role-create-code-error">
            {t(codeServerError.key)}
          </p>
        ) : null}
      </div>

      <fieldset className="border border-border rounded-md p-3 space-y-3">
        <legend className="text-sm font-medium text-text-primary px-1">
          {t('roles.create.name')}
        </legend>
        {SUPPORTED_LOCALES.map((loc) => (
          <div key={loc}>
            <label htmlFor={`role-name-${loc}`} className="text-xs font-medium text-text-secondary">
              {t(`language.${loc}`)}
              {loc === 'ru-RU' ? <span className="text-danger-700"> *</span> : null}
            </label>
            <input
              id={`role-name-${loc}`}
              type="text"
              autoComplete="off"
              {...register(`name_i18n.${loc}` as const)}
              className="mt-1 w-full h-9 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
              data-testid={`role-create-name-${loc}`}
            />
            {loc === 'ru-RU' && errors.name_i18n?.['ru-RU'] ? (
              <p className="text-xs text-danger-700 mt-1" role="alert" data-testid="role-create-name-error">
                {errKey(errors.name_i18n['ru-RU']?.message)}
              </p>
            ) : null}
          </div>
        ))}
      </fieldset>

      <fieldset className="border border-border rounded-md p-3">
        <legend className="text-sm font-medium text-text-primary px-1">
          {t('roles.create.permissions')}
        </legend>
        <p className="text-xs text-text-muted mb-3">{t('roles.create.permissionsHint')}</p>
        {isLoading ? (
          <LoadingState />
        ) : error ? (
          <ErrorState onRetry={() => refetch()} />
        ) : (
          <PermissionMatrix
            items={catalog ?? []}
            selected={selected}
            readOnly={false}
            callerPermissions={callerPermissions}
            onToggle={toggle}
            onToggleGroup={toggleGroup}
          />
        )}
      </fieldset>
    </DrawerForm>
  );
}
