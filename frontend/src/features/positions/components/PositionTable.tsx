import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { DataTable, type DataTableColumn } from '@/shared/components/data-table/DataTable';
import { pickLocalized } from '@/shared/lib/localized';
import { routes } from '@/shared/config/routes';
import type { Position } from '../types/positionTypes';
import type { Department } from '@/features/organization/types/organizationTypes';
import { PositionStatusBadge } from './PositionStatusBadge';

interface PositionTableProps {
  projectId: string;
  rows: Position[];
  departments: Department[];
  loading?: boolean;
  filterBar?: React.ReactNode;
  toolbarRight?: React.ReactNode;
}

export function PositionTable({
  projectId,
  rows,
  departments,
  loading,
  filterBar,
  toolbarRight,
}: PositionTableProps) {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const locale = i18n.language;
  const depById = new Map(departments.map((d) => [d.id, d] as const));

  const columns: DataTableColumn<Position>[] = [
    {
      key: 'title',
      header: t('positions.column_title'),
      render: (p) => <span className="font-medium text-text-primary">{pickLocalized(p.title, locale)}</span>,
      sortable: true,
      sortAccessor: (p) => pickLocalized(p.title, locale).toLowerCase(),
    },
    {
      key: 'department',
      header: t('positions.column_department'),
      render: (p) => {
        const d = depById.get(p.department_id);
        return d ? `${d.code} · ${pickLocalized(d.name, locale)}` : '—';
      },
      sortable: true,
      sortAccessor: (p) => depById.get(p.department_id)?.code ?? '',
    },
    {
      key: 'function',
      header: t('positions.column_function'),
      render: (p) => p.function ?? '—',
      defaultVisible: true,
    },
    {
      key: 'category',
      header: t('positions.column_category'),
      render: (p) => p.category ?? '—',
    },
    {
      key: 'job_family',
      header: t('positions.column_job_family'),
      render: (p) => p.job_family ?? '—',
    },
    {
      key: 'job_level',
      header: t('positions.column_job_level'),
      render: (p) => p.job_level ?? '—',
    },
    {
      key: 'status',
      header: t('positions.column_status'),
      render: (p) => <PositionStatusBadge status={p.status} />,
      width: '120px',
    },
    {
      key: 'updated',
      header: t('positions.column_updated'),
      render: (p) => new Date(p.updated_at).toLocaleDateString(locale),
      sortable: true,
      sortAccessor: (p) => p.updated_at,
      width: '140px',
    },
  ];

  return (
    <DataTable<Position>
      rows={rows}
      columns={columns}
      rowKey={(p) => p.id}
      loading={loading}
      searchPredicate={(p, q) => {
        const title = pickLocalized(p.title, locale).toLowerCase();
        return p.code.toLowerCase().includes(q) || title.includes(q);
      }}
      filterBar={filterBar}
      toolbarRight={toolbarRight}
      emptyTitle={t('positions.empty_title')}
      emptyBody={t('positions.empty_body')}
      onRowClick={(p) => navigate(routes.projectPositionDetail(projectId, p.id))}
    />
  );
}
