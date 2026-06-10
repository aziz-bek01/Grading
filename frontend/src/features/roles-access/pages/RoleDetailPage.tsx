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
import type { RolePermissions } from '@/features/users-access/api/rolesApi';
import { useRolePermissions, useSetRolePermissions } from '../hooks/useRolePermissions';
import { PermissionMatrix } from '../components/PermissionMatrix';
import { RoleKindBadge, RoleScopeBadge } from '../components/RoleScopeBadge';
import { mapRolePermissionError } from '../lib/rolePermissionError';

export function RoleDetailPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { roleCode = '' } = useParams<{ roleCode: string }>();
  const { can } = usePermission();

  const { data, isLoading, error, refetch } = useRolePermissions(roleCode);
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
