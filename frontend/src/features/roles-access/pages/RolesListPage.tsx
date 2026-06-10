/**
 * /app/roles — Roles admin LIST (slice E2).
 *
 * Lists every role from `GET /roles` (full catalog, NOT trimmed to assignable).
 * Each row links to the per-role permission matrix. The whole area is gated by
 * USER_ACCESS_MANAGE (route guard + nav entry); this page additionally renders
 * a NoAccessState if a user somehow reaches it without the permission, so the
 * gate is defence-in-depth rather than a single point.
 */
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ShieldCheck, Plus, Pencil, Trash2 } from 'lucide-react';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { NoAccessState } from '@/shared/components/feedback/NoAccessState';
import { DataTable, type DataTableColumn } from '@/shared/components/data-table/DataTable';
import { Button } from '@/shared/components/ui/Button';
import { PERMISSIONS } from '@/shared/types/permissions';
import { usePermission } from '@/features/auth/usePermission';
import { routes } from '@/shared/config/routes';
import type { AssignableRole } from '@/features/users-access/api/rolesApi';
import { useRoleCatalog } from '../hooks/useRolePermissions';
import { RoleKindBadge, RoleScopeBadge } from '../components/RoleScopeBadge';
import { CreateRoleDrawer } from '../components/CreateRoleDrawer';
import { DeleteRoleConfirm } from '../components/DeleteRoleConfirm';

export function RolesListPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { can } = usePermission();

  const { data, isLoading, error, refetch } = useRoleCatalog();
  const [createOpen, setCreateOpen] = useState(false);
  const [toDelete, setToDelete] = useState<AssignableRole | null>(null);

  if (!can(PERMISSIONS.USER_ACCESS_MANAGE)) {
    return (
      <div className="space-y-6">
        <Breadcrumbs extra={[{ label: t('roles.list.title') }]} />
        <NoAccessState />
      </div>
    );
  }

  const columns: DataTableColumn<AssignableRole>[] = [
    {
      key: 'name',
      header: t('roles.list.col.name'),
      render: (r) => (
        <div className="flex items-center gap-2">
          <ShieldCheck size={16} className="text-text-secondary" aria-hidden />
          <span className="font-medium text-text-primary">{r.name}</span>
        </div>
      ),
      sortable: true,
      sortAccessor: (r) => r.name,
    },
    {
      key: 'code',
      header: t('roles.list.col.code'),
      render: (r) => <code className="text-xs text-text-muted font-mono">{r.code}</code>,
      sortable: true,
      sortAccessor: (r) => r.code,
    },
    {
      key: 'scope',
      header: t('roles.list.col.scope'),
      render: (r) => <RoleScopeBadge scope={r.scope} />,
    },
    {
      key: 'kind',
      header: t('roles.list.col.kind'),
      render: (r) => <RoleKindBadge isSystem={r.isSystem} />,
    },
    {
      key: 'actions',
      header: <span className="sr-only">{t('roles.list.col.actions')}</span>,
      // Custom rows get Edit + Delete; system rows have NO Delete (and Edit just
      // opens the read-only-aware permission matrix detail). Clicks must NOT
      // bubble to the row's navigate handler.
      render: (r) => (
        <div className="flex items-center justify-end gap-1" onClick={(e) => e.stopPropagation()}>
          <Button
            variant="ghost"
            size="sm"
            leadingIcon={<Pencil size={14} />}
            onClick={() => navigate(routes.roleDetails(r.code))}
            data-testid={`role-edit-${r.code}`}
          >
            {t('roles.list.action.edit')}
          </Button>
          {r.isCustom ? (
            <Button
              variant="ghost"
              size="sm"
              leadingIcon={<Trash2 size={14} />}
              onClick={() => setToDelete(r)}
              data-testid={`role-delete-${r.code}`}
            >
              {t('roles.list.action.delete')}
            </Button>
          ) : null}
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <Breadcrumbs extra={[{ label: t('roles.list.title') }]} />
      <header>
        <h1 className="text-2xl text-text-primary">{t('roles.list.title')}</h1>
        <p className="text-sm text-text-secondary mt-1">{t('roles.list.subtitle')}</p>
      </header>

      {error ? (
        <ErrorState onRetry={() => refetch()} />
      ) : (
        <DataTable<AssignableRole>
          columns={columns}
          rows={data ?? []}
          rowKey={(r) => r.code}
          loading={isLoading}
          caption={t('roles.list.title')}
          searchPredicate={(r, q) =>
            r.name.toLowerCase().includes(q.toLowerCase()) ||
            r.code.toLowerCase().includes(q.toLowerCase())
          }
          toolbarRight={
            <Button
              variant="primary"
              size="sm"
              leadingIcon={<Plus size={16} />}
              onClick={() => setCreateOpen(true)}
              data-testid="role-create-button"
            >
              {t('roles.list.createRole')}
            </Button>
          }
          emptyTitle={t('roles.list.emptyTitle')}
          emptyBody={t('roles.list.emptyBody')}
          onRowClick={(r) => navigate(routes.roleDetails(r.code))}
        />
      )}

      <CreateRoleDrawer
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={() => void refetch()}
      />
      <DeleteRoleConfirm
        open={Boolean(toDelete)}
        role={toDelete}
        onClose={() => setToDelete(null)}
        onDeleted={() => void refetch()}
      />
    </div>
  );
}
