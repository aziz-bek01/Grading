import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { DataTable, type DataTableColumn } from '@/shared/components/data-table/DataTable';
import { formatDateSafe } from '@/shared/lib/dates';
import { routes } from '@/shared/config/routes';
import type { User } from '../types/userTypes';
import { UserStatusBadge } from './UserStatusBadge';

interface UsersTableProps {
  rows: User[];
  loading?: boolean;
  toolbarRight?: React.ReactNode;
  filterBar?: React.ReactNode;
}

export function UsersTable({ rows, loading, toolbarRight, filterBar }: UsersTableProps) {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const locale = i18n.language;

  const columns: DataTableColumn<User>[] = [
    {
      key: 'full_name',
      header: t('users.column.fullName'),
      render: (u) => (
        <div className="flex flex-col">
          <span className="font-medium text-text-primary">{u.full_name}</span>
          <span className="text-xs text-text-muted">{u.email}</span>
        </div>
      ),
      sortable: true,
      sortAccessor: (u) => u.full_name.toLowerCase(),
    },
    {
      key: 'tenant_count',
      header: t('users.column.tenants'),
      render: (u) => (
        <span className="inline-flex items-center justify-center min-w-[24px] h-6 px-2 rounded-full bg-divider text-text-primary text-xs">
          {u.tenant_count}
        </span>
      ),
      sortable: true,
      sortAccessor: (u) => u.tenant_count,
      width: '120px',
    },
    {
      key: 'role_count',
      header: t('users.column.roles'),
      render: (u) => (
        <span className="inline-flex items-center justify-center min-w-[24px] h-6 px-2 rounded-full bg-divider text-text-primary text-xs">
          {u.role_count}
        </span>
      ),
      sortable: true,
      sortAccessor: (u) => u.role_count,
      width: '100px',
    },
    {
      key: 'status',
      header: t('users.column.status'),
      render: (u) => <UserStatusBadge status={u.status} />,
      width: '140px',
    },
    {
      key: 'last_login',
      header: t('users.column.lastLogin'),
      render: (u) =>
        u.last_login_at ? (
          formatDateSafe(u.last_login_at, locale)
        ) : (
          <span className="text-text-muted">{t('users.never')}</span>
        ),
      sortable: true,
      sortAccessor: (u) => u.last_login_at ?? '',
      width: '180px',
    },
  ];

  return (
    <DataTable<User>
      rows={rows}
      columns={columns}
      rowKey={(u) => u.id}
      loading={loading}
      filterBar={filterBar}
      searchPredicate={(u, q) =>
        u.full_name.toLowerCase().includes(q) || u.email.toLowerCase().includes(q)
      }
      onRowClick={(u) => navigate(`${routes.usersAccess}/${u.id}`)}
      emptyTitle={t('users.empty.title')}
      emptyBody={t('users.empty.body')}
      toolbarRight={toolbarRight}
      caption={t('users.list.title')}
    />
  );
}
