/**
 * /app/roles/:roleCode — per-role permission matrix editor (slice E2).
 *
 * Loads `GET /roles/{code}/permissions`, renders the grouped checkbox matrix,
 * tracks a dirty working copy, and saves via `PUT` with REPLACE-set semantics:
 * the body is the full set of CHECKED, NON-restricted codes.
 *
 * States handled:
 *   - loading / error (with retry)
 *   - read-only banner when `editable_by_caller=false` (system role / no rights)
 *   - dirty tracking → Save / Cancel; both disabled when clean
 *   - success toast (inline banner) on save
 *   - mapped error banner: PERMISSION_RESTRICTED / PERMISSION_NOT_HELD_BY_CALLER
 *     / 403 system role → localized message
 *
 * Working copy: the editable state lives in {@link RolePermissionEditor}, which
 * is keyed by a snapshot signature so a fresh server snapshot (initial load or a
 * post-save refetch) cleanly RE-MOUNTS the editor with new initial state — no
 * setState-in-effect. The server snapshot is always the diff baseline.
 *
 * Security: gated by USER_ACCESS_MANAGE at the route AND re-checked here. The
 * matrix is UX only — the backend re-enforces restricted + caller-held codes.
 */
import { useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { CheckCircle2, Lock, ArrowLeft } from 'lucide-react';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { Card } from '@/shared/components/ui/Card';
import { Button } from '@/shared/components/ui/Button';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { NoAccessState } from '@/shared/components/feedback/NoAccessState';
import { PERMISSIONS } from '@/shared/types/permissions';
import { usePermission } from '@/features/auth/usePermission';
import { ApiError } from '@/shared/api/apiError';
import { routes } from '@/shared/config/routes';
import { SUPPORTED_LOCALES } from '@/shared/i18n';
import type { LocalizedString } from '@/shared/types/common';
import type { AssignableRole, RolePermissions } from '@/features/users-access/api/rolesApi';
import {
  useRoleCatalog,
  useRolePermissions,
  useSetRolePermissions,
  useUpdateRole,
} from '../hooks/useRolePermissions';
import { PermissionMatrix } from '../components/PermissionMatrix';
import { RoleKindBadge, RoleScopeBadge } from '../components/RoleScopeBadge';
import { mapRolePermissionError } from '../lib/rolePermissionError';

export function RoleDetailPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { roleCode = '' } = useParams<{ roleCode: string }>();
  const { can } = usePermission();

  const { data, isLoading, error, refetch } = useRolePermissions(roleCode);
  // Catalog row gives us the role id (E3 PUT key) + custom flag + localized name.
  const { data: catalog } = useRoleCatalog();
  const role = catalog?.find((r) => r.code === roleCode) ?? null;
  // Success banner lives at the PAGE level (which never re-mounts) so it
  // survives the post-save refetch that re-mounts the keyed editor below.
  const [saved, setSaved] = useState(false);

  if (!can(PERMISSIONS.USER_ACCESS_MANAGE)) {
    return (
      <div className="space-y-6">
        <Breadcrumbs extra={[{ label: t('roles.list.title') }]} />
        <NoAccessState />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <Breadcrumbs
        extra={[
          { label: t('roles.list.title'), to: routes.roles },
          { label: data?.roleCode ?? roleCode },
        ]}
      />

      <header className="flex items-start justify-between gap-4 flex-wrap">
        <div className="space-y-2">
          <div className="flex items-center gap-3 flex-wrap">
            <h1 className="text-2xl text-text-primary">
              {t(`users.role.${roleCode}`, { defaultValue: roleCode })}
            </h1>
            {data ? <RoleScopeBadge scope={data.scope} /> : null}
            {data ? <RoleKindBadge isSystem={data.isSystem} /> : null}
          </div>
          <p className="text-sm text-text-secondary">{t('roles.detail.subtitle')}</p>
        </div>
        <Button
          variant="ghost"
          size="sm"
          leadingIcon={<ArrowLeft size={14} />}
          onClick={() => navigate(routes.roles)}
        >
          {t('roles.detail.back')}
        </Button>
      </header>

      {saved ? (
        <div
          role="status"
          className="flex items-start gap-3 rounded-md border border-success-500/30 bg-success-50 px-4 py-3 text-sm text-success-700"
          data-testid="role-save-success"
        >
          <CheckCircle2 size={16} className="mt-0.5" aria-hidden />
          <p>{t('roles.detail.saveSuccess')}</p>
        </div>
      ) : null}

      {/* Custom roles: localized NAME is editable here (system roles: read-only).
          The name save routes through the E3 PUT /roles/{id}; permissions still
          save through the E2 PUT /roles/{code}/permissions below. */}
      {role?.isCustom && role.id ? (
        <RoleNameEditor key={`name-${role.id}`} role={role} roleCode={roleCode} />
      ) : null}

      {isLoading ? (
        <LoadingState />
      ) : error ? (
        <ErrorState
          onRetry={() => refetch()}
          correlationId={error instanceof ApiError ? error.correlationId : undefined}
        />
      ) : data ? (
        <RolePermissionEditor
          key={snapshotSignature(data)}
          roleCode={roleCode}
          data={data}
          onSaved={() => setSaved(true)}
          onEdit={() => setSaved(false)}
        />
      ) : null}
    </div>
  );
}

/**
 * Localized-name editor for a CUSTOM role (slice E3). Seeds from the catalog
 * row's `nameI18n`; saves only the name via PUT /roles/{id} (the permission
 * matrix below owns permissions). ru-RU is required (the primary label).
 */
function RoleNameEditor({ role, roleCode }: { role: AssignableRole; roleCode: string }) {
  const { t } = useTranslation();
  const update = useUpdateRole(roleCode);
  const [names, setNames] = useState<Record<string, string>>(() => {
    const seed: Record<string, string> = {};
    for (const loc of SUPPORTED_LOCALES) seed[loc] = role.nameI18n[loc] ?? '';
    return seed;
  });
  const [saved, setSaved] = useState(false);

  const primaryMissing = (names['ru-RU'] ?? '').trim().length === 0;
  const dirty = SUPPORTED_LOCALES.some((loc) => (names[loc] ?? '') !== (role.nameI18n[loc] ?? ''));

  const set = (loc: string, value: string) => {
    setSaved(false);
    setNames((prev) => ({ ...prev, [loc]: value }));
  };

  const save = () => {
    if (!role.id || primaryMissing) return;
    const name_i18n: LocalizedString = {};
    for (const loc of SUPPORTED_LOCALES) {
      const v = names[loc]?.trim();
      if (v) name_i18n[loc] = v;
    }
    update.mutate(
      { roleId: role.id, payload: { name_i18n } },
      { onSuccess: () => setSaved(true) },
    );
  };

  const mapped = update.error ? mapRolePermissionError(update.error) : null;

  return (
    <Card className="space-y-4" data-testid="role-name-editor">
      <header>
        <h2 className="text-base font-semibold text-text-primary">{t('roles.edit.nameTitle')}</h2>
        <p className="text-sm text-text-secondary mt-1">{t('roles.edit.nameSubtitle')}</p>
      </header>

      {saved ? (
        <div
          role="status"
          className="rounded-md border border-success-500/30 bg-success-50 px-4 py-2.5 text-sm text-success-700"
          data-testid="role-name-success"
        >
          {t('roles.edit.nameSaved')}
        </div>
      ) : null}

      {mapped ? (
        <div
          role="alert"
          className="rounded-md border border-danger-500/30 bg-danger-50 px-4 py-2.5 text-sm text-danger-700"
          data-testid="role-name-error"
        >
          {t(mapped.key)}
        </div>
      ) : null}

      <div className="grid gap-3 sm:grid-cols-2">
        {SUPPORTED_LOCALES.map((loc) => (
          <div key={loc}>
            <label
              htmlFor={`role-edit-name-${loc}`}
              className="text-xs font-medium text-text-secondary"
            >
              {t(`language.${loc}`)}
              {loc === 'ru-RU' ? <span className="text-danger-700"> *</span> : null}
            </label>
            <input
              id={`role-edit-name-${loc}`}
              type="text"
              autoComplete="off"
              value={names[loc] ?? ''}
              onChange={(e) => set(loc, e.target.value)}
              className="mt-1 w-full h-9 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
              data-testid={`role-edit-name-${loc}`}
            />
          </div>
        ))}
      </div>
      {primaryMissing ? (
        <p className="text-xs text-danger-700" role="alert">
          {t('roles.create.error.nameRequired')}
        </p>
      ) : null}

      <footer className="flex items-center justify-end gap-3 border-t border-divider pt-4">
        <Button
          variant="primary"
          size="sm"
          onClick={save}
          disabled={!dirty || primaryMissing || update.isPending}
          data-testid="role-name-save"
        >
          {update.isPending ? t('roles.edit.nameSaving') : t('roles.edit.nameSave')}
        </Button>
      </footer>
    </Card>
  );
}

/** Stable signature of the granted/restricted state — changes only when the
 *  server snapshot does, so the editor re-mounts (re-seeds) on each new load. */
function snapshotSignature(data: RolePermissions): string {
  const granted = data.items
    .filter((i) => i.granted)
    .map((i) => i.code)
    .sort()
    .join(',');
  return `${data.roleCode}|${data.editableByCaller ? '1' : '0'}|${granted}`;
}

/**
 * The dirty-tracking editor. Mounted with the server snapshot as its initial
 * state; the parent re-mounts it (via `key`) on every fresh snapshot, so this
 * never needs an effect to sync server → local state.
 */
function RolePermissionEditor({
  roleCode,
  data,
  onSaved,
  onEdit,
}: {
  roleCode: string;
  data: RolePermissions;
  /** Called after a successful save (parent shows the persistent success banner). */
  onSaved: () => void;
  /** Called on any local edit so the parent can clear a stale success banner. */
  onEdit: () => void;
}) {
  const { t } = useTranslation();
  const mutation = useSetRolePermissions(roleCode);

  // Baseline = granted, non-restricted codes from the server snapshot.
  const baseline = useMemo(() => {
    const s = new Set<string>();
    for (const item of data.items) {
      if (item.granted && !item.restricted) s.add(item.code);
    }
    return s;
  }, [data.items]);

  const [selected, setSelected] = useState<Set<string>>(() => new Set(baseline));

  const readOnly = !data.editableByCaller;

  const dirty = useMemo(() => {
    if (selected.size !== baseline.size) return true;
    for (const c of selected) if (!baseline.has(c)) return true;
    return false;
  }, [selected, baseline]);

  const toggle = (code: string, next: boolean) => {
    onEdit();
    setSelected((prev) => {
      const n = new Set(prev);
      if (next) n.add(code);
      else n.delete(code);
      return n;
    });
  };

  const toggleGroup = (codes: string[], next: boolean) => {
    onEdit();
    setSelected((prev) => {
      const n = new Set(prev);
      for (const c of codes) {
        if (next) n.add(c);
        else n.delete(c);
      }
      return n;
    });
  };

  const cancel = () => {
    onEdit();
    mutation.reset();
    setSelected(new Set(baseline));
  };

  const save = () => {
    // REPLACE-set: send exactly the checked non-restricted codes. (`selected`
    // can only ever hold non-restricted codes; we still belt-and-braces filter.)
    const restricted = new Set(data.items.filter((i) => i.restricted).map((i) => i.code));
    const payload = [...selected].filter((c) => !restricted.has(c)).sort();
    mutation.mutate(payload, { onSuccess: () => onSaved() });
  };

  const mappedError = mutation.error ? mapRolePermissionError(mutation.error) : null;

  return (
    <Card className="space-y-5">
      {readOnly ? (
        <div
          role="status"
          className="flex items-start gap-3 rounded-md border border-locked/40 bg-locked-bg px-4 py-3 text-sm text-locked"
          data-testid="role-readonly-banner"
        >
          <Lock size={16} className="mt-0.5" aria-hidden />
          <p>{t('roles.detail.readOnlyBanner')}</p>
        </div>
      ) : null}

      {mappedError ? (
        <div
          role="alert"
          className="rounded-md border border-danger-500/30 bg-danger-50 px-4 py-3 text-sm text-danger-700"
          data-testid="role-save-error"
        >
          <p>{t(mappedError.key)}</p>
          {mappedError.correlationId ? (
            <p className="text-xs text-text-muted mt-1 font-mono">
              {t('states.correlation_id')}: {mappedError.correlationId}
            </p>
          ) : null}
        </div>
      ) : null}

      <PermissionMatrix
        items={data.items}
        selected={selected}
        readOnly={readOnly}
        onToggle={toggle}
        onToggleGroup={toggleGroup}
      />

      {!readOnly ? (
        <footer className="flex items-center justify-end gap-3 border-t border-divider pt-4">
          <Button
            variant="secondary"
            size="sm"
            onClick={cancel}
            disabled={!dirty || mutation.isPending}
            data-testid="role-cancel-button"
          >
            {t('roles.detail.cancel')}
          </Button>
          <Button
            variant="primary"
            size="sm"
            onClick={save}
            disabled={!dirty || mutation.isPending}
            data-testid="role-save-button"
          >
            {mutation.isPending ? t('roles.detail.saving') : t('roles.detail.save')}
          </Button>
        </footer>
      ) : null}
    </Card>
  );
}
