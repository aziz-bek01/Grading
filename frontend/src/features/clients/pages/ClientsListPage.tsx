/**
 * /app/clients — HRLab portfolio Tenants list.
 *
 * Replaces the previous PlaceholderPage. Renders the TenantsTable with
 * server-side filters (status / search) on top of the F-3 contract.
 *
 * Permission model:
 *   - Mounted route requires TENANT_READ (see router.tsx).
 *   - The "Edit" and "Archive" actions are gated by TENANT_EDIT /
 *     TENANT_ARCHIVE respectively, evaluated per-row via PermissionGate.
 */
import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Plus } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { FilterBar, type FilterDefinition } from '@/shared/components/data-table/FilterBar';
import { TenantsTable } from '../components/TenantsTable';
import { CreateTenantDialog } from '../components/CreateTenantDialog';
import { useTenants } from '../hooks/useTenants';
import { useCreateTenant } from '../hooks/useTenantMutations';
import type { TenantListQuery, TenantStatus } from '../types/clientTypes';

export function ClientsListPage() {
  const { t } = useTranslation();
  const [status, setStatus] = useState<TenantStatus | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [createdName, setCreatedName] = useState<string | null>(null);
  const createMutation = useCreateTenant();

  const query: TenantListQuery = useMemo(
    () => ({ status: status ?? undefined }),
    [status],
  );

  const { data, isLoading, error, refetch } = useTenants(query);

  // Auto-dismiss the success banner so it behaves like a transient toast.
  useEffect(() => {
    if (!createdName) return;
    const id = window.setTimeout(() => setCreatedName(null), 5000);
    return () => window.clearTimeout(id);
  }, [createdName]);

  const filters: FilterDefinition[] = [
    {
      key: 'status',
      label: t('clients.filter.status'),
      value: status,
      onChange: (v) => setStatus((v as TenantStatus | null) ?? null),
      options: [
        { value: 'ACTIVE', label: t('clients.status.active') },
        { value: 'SUSPENDED', label: t('clients.status.suspended') },
        { value: 'ARCHIVED', label: t('clients.status.archived') },
      ],
    },
  ];

  return (
    <div className="space-y-6">
      <Breadcrumbs extra={[{ label: t('nav.clients') }]} />
      <header className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-2xl text-text-primary">{t('clients.list.title')}</h1>
          <p className="text-sm text-text-secondary mt-1">{t('clients.list.subtitle')}</p>
        </div>
        <div
          className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-primary-50 border border-primary-200 text-primary-700 text-xs font-medium"
          title={t('clients.list.super_admin_hint')}
        >
          {t('clients.list.super_admin_badge')}
        </div>
      </header>

      {createdName ? (
        <div
          role="status"
          className="rounded-md border border-success-500/30 bg-success-50 px-4 py-3 text-sm text-success-700"
          data-testid="create-tenant-success"
        >
          {t('clients.create.success', { name: createdName })}
        </div>
      ) : null}

      {error ? (
        <ErrorState onRetry={() => refetch()} />
      ) : (
        <TenantsTable
          rows={data?.items ?? []}
          loading={isLoading}
          filterBar={<FilterBar filters={filters} onReset={() => setStatus(null)} />}
          toolbarRight={
            <PermissionGate permission={PERMISSIONS.TENANT_CREATE}>
              <Button
                variant="primary"
                size="sm"
                leadingIcon={<Plus size={14} />}
                onClick={() => setCreateOpen(true)}
                data-testid="add-client-button"
              >
                {t('clients.create.button')}
              </Button>
            </PermissionGate>
          }
        />
      )}

      <CreateTenantDialog
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onSubmit={async (payload) => {
          const created = await createMutation.mutateAsync(payload);
          setCreatedName(created.display_name);
        }}
      />
    </div>
  );
}
