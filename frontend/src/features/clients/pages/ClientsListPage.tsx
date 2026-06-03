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
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { FilterBar, type FilterDefinition } from '@/shared/components/data-table/FilterBar';
import { TenantsTable } from '../components/TenantsTable';
import { useTenants } from '../hooks/useTenants';
import type { TenantListQuery, TenantStatus } from '../types/clientTypes';

export function ClientsListPage() {
  const { t } = useTranslation();
  const [status, setStatus] = useState<TenantStatus | null>(null);

  const query: TenantListQuery = useMemo(
    () => ({ status: status ?? undefined }),
    [status],
  );

  const { data, isLoading, error, refetch } = useTenants(query);

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
          className="inline-flex items-center gap-2 px-3 py-2 rounded-md bg-primary-50 border border-primary-200 text-primary-700 text-xs"
          title={t('clients.list.super_admin_hint')}
        >
          {t('clients.list.super_admin_badge')}
        </div>
      </header>

      {error ? (
        <ErrorState onRetry={() => refetch()} />
      ) : (
        <TenantsTable
          rows={data?.items ?? []}
          loading={isLoading}
          filterBar={<FilterBar filters={filters} onReset={() => setStatus(null)} />}
        />
      )}
    </div>
  );
}
