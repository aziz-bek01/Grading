import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { DataTable, type DataTableColumn } from '@/shared/components/data-table/DataTable';
import { formatDateSafe } from '@/shared/lib/dates';
import { routes } from '@/shared/config/routes';
import type { TenantListRow } from '../types/clientTypes';
import { TenantStatusBadge } from './TenantStatusBadge';

interface TenantsTableProps {
  rows: TenantListRow[];
  loading?: boolean;
  toolbarRight?: React.ReactNode;
  filterBar?: React.ReactNode;
}

export function TenantsTable({ rows, loading, toolbarRight, filterBar }: TenantsTableProps) {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const locale = i18n.language;

  const columns: DataTableColumn<TenantListRow>[] = [
    {
      key: 'display_name',
      header: t('clients.column.tenant'),
      render: (r) => (
        <div className="flex items-center gap-3">
          <span
            className="inline-flex h-8 w-8 items-center justify-center rounded-md text-xs font-semibold text-text-inverse"
            style={{ backgroundColor: `hsl(${r.fingerprint_hue} 60% 45%)` }}
            aria-hidden
          >
            {r.display_name.slice(0, 2).toUpperCase()}
          </span>
          <div className="flex flex-col">
            <span className="font-medium text-text-primary">{r.display_name}</span>
            <span className="text-xs text-text-muted">{r.client_legal_name}</span>
          </div>
        </div>
      ),
      sortable: true,
      sortAccessor: (r) => r.display_name.toLowerCase(),
    },
    {
      key: 'slug',
      header: t('clients.column.slug'),
      render: (r) => <span className="font-mono text-xs text-text-secondary">{r.slug}</span>,
      width: '140px',
    },
    {
      key: 'project_count',
      header: t('clients.column.projects'),
      render: (r) => (
        <span className="inline-flex items-center justify-center min-w-[24px] h-6 px-2 rounded-full bg-divider text-text-primary text-xs">
          {r.project_count}
        </span>
      ),
      sortable: true,
      sortAccessor: (r) => r.project_count,
      width: '120px',
    },
    {
      key: 'user_count',
      header: t('clients.column.users'),
      render: (r) => (
        <span className="inline-flex items-center justify-center min-w-[24px] h-6 px-2 rounded-full bg-divider text-text-primary text-xs">
          {r.user_count}
        </span>
      ),
      sortable: true,
      sortAccessor: (r) => r.user_count,
      width: '110px',
    },
    {
      key: 'status',
      header: t('clients.column.status'),
      render: (r) => <TenantStatusBadge status={r.status} />,
      width: '140px',
    },
    {
      key: 'updated_at',
      header: t('clients.column.updated'),
      render: (r) => formatDateSafe(r.updated_at, locale),
      sortable: true,
      sortAccessor: (r) => r.updated_at,
      width: '180px',
    },
  ];

  return (
    <DataTable<TenantListRow>
      rows={rows}
      columns={columns}
      rowKey={(r) => r.id}
      loading={loading}
      filterBar={filterBar}
      searchPredicate={(r, q) =>
        r.display_name.toLowerCase().includes(q) ||
        r.client_legal_name.toLowerCase().includes(q) ||
        r.slug.toLowerCase().includes(q)
      }
      onRowClick={(r) => navigate(routes.clientDetails(r.id))}
      emptyTitle={t('clients.empty.title')}
      emptyBody={t('clients.empty.body')}
      toolbarRight={toolbarRight}
      caption={t('clients.list.title')}
    />
  );
}
