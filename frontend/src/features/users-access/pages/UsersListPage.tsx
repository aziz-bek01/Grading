/**
 * /app/users — list view.
 *
 * Replaces the previous PlaceholderPage. Renders the full DataTable with
 * server-side filter parameters (status/role/search) and an InviteUser CTA
 * gated by USER_INVITE.
 *
 * Tenant scoping:
 *   - The list always reflects the active tenant (the API client derives it
 *     from the JWT). Tenant id is included in the React-Query cache key so
 *     the list refetches when the tenant switcher changes.
 *   - For HRLAB_SUPER_ADMIN, an extra tenant filter is shown so a single
 *     view can span all tenants.
 */
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Plus } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { FilterBar, type FilterDefinition } from '@/shared/components/data-table/FilterBar';
import { PERMISSIONS } from '@/shared/types/permissions';
import { useAuthStore } from '@/features/auth/authStore';
import type { RoleCode } from '@/shared/auth/authTypes';
import { UsersTable } from '../components/UsersTable';
import { InviteUserDialog } from '../components/InviteUserDialog';
import { useUsers } from '../hooks/useUsers';
import { useInviteUser } from '../hooks/useUserMutations';
import type { UserListQuery, UserStatus } from '../types/userTypes';

const FILTERABLE_ROLES: RoleCode[] = [
  'CLIENT_ADMIN',
  'CLIENT_HR_DIRECTOR',
  'CLIENT_HR_SPECIALIST',
  'CLIENT_COMMITTEE_MEMBER',
  'CLIENT_DEPARTMENT_MANAGER',
  'CLIENT_VIEWER',
];

export function UsersListPage() {
  const { t } = useTranslation();
  const user = useAuthStore((s) => s.user);
  const activeTenant = useAuthStore((s) => s.activeTenant);
  const isSuperAdmin = user?.roles.includes('HRLAB_SUPER_ADMIN') ?? false;

  const [status, setStatus] = useState<UserStatus | null>(null);
  const [role, setRole] = useState<RoleCode | null>(null);
  const [tenantOverride, setTenantOverride] = useState<string | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const query: UserListQuery = useMemo(
    () => ({
      status: status ?? undefined,
      role: role ?? undefined,
      tenant_id: isSuperAdmin ? tenantOverride ?? undefined : undefined,
    }),
    [status, role, isSuperAdmin, tenantOverride],
  );

  const { data, isLoading, error, refetch } = useUsers(query);
  const inviteMutation = useInviteUser();

  const filters: FilterDefinition[] = [
    {
      key: 'status',
      label: t('users.filter.status'),
      value: status,
      onChange: (v) => setStatus((v as UserStatus | null) ?? null),
      options: [
        { value: 'ACTIVE', label: t('users.status.active') },
        { value: 'INVITED', label: t('users.status.invited') },
        { value: 'REVOKED', label: t('users.status.revoked') },
        { value: 'SUSPENDED', label: t('users.status.suspended') },
      ],
    },
    {
      key: 'role',
      label: t('users.filter.role'),
      value: role,
      onChange: (v) => setRole((v as RoleCode | null) ?? null),
      options: FILTERABLE_ROLES.map((rc) => ({
        value: rc,
        label: t(`users.role.${rc}`, { defaultValue: rc }),
      })),
    },
  ];

  if (isSuperAdmin) {
    filters.push({
      key: 'tenant',
      label: t('users.filter.tenant'),
      value: tenantOverride,
      onChange: setTenantOverride,
      options: (user?.tenants ?? []).map((tn) => ({ value: tn.id, label: tn.brand_name })),
    });
  }

  const subtitle = activeTenant
    ? t('users.list.subtitleForTenant', { tenant: activeTenant.brand_name })
    : t('users.list.subtitle');

  return (
    <div className="space-y-6">
      <Breadcrumbs extra={[{ label: t('nav.users_access') }]} />
      <header className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-2xl text-text-primary">{t('users.list.title')}</h1>
          <p className="text-sm text-text-secondary mt-1">{subtitle}</p>
        </div>
      </header>

      {error ? (
        <ErrorState onRetry={() => refetch()} />
      ) : (
        <UsersTable
          rows={data?.items ?? []}
          loading={isLoading}
          filterBar={
            <FilterBar
              filters={filters}
              onReset={() => {
                setStatus(null);
                setRole(null);
                setTenantOverride(null);
              }}
            />
          }
          toolbarRight={
            <PermissionGate permission={PERMISSIONS.USER_INVITE}>
              <Button
                variant="primary"
                size="sm"
                leadingIcon={<Plus size={14} />}
                onClick={() => setDrawerOpen(true)}
                data-testid="invite-user-button"
              >
                {t('users.invite.button')}
              </Button>
            </PermissionGate>
          }
        />
      )}

      <InviteUserDialog
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        onSubmit={async (payload) => {
          await inviteMutation.mutateAsync(payload);
        }}
      />
    </div>
  );
}
