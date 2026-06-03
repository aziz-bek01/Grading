import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { DataTable, type DataTableColumn } from '@/shared/components/data-table/DataTable';
import { pickLocalized } from '@/shared/lib/localized';
import { routes } from '@/shared/config/routes';
import type { Project } from '../types/projectTypes';
import { ProjectStatusBadge } from './ProjectStatusBadge';

interface ProjectTableProps {
  rows: Project[];
  loading?: boolean;
  toolbarRight?: React.ReactNode;
}

export function ProjectTable({ rows, loading, toolbarRight }: ProjectTableProps) {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const locale = i18n.language;

  const columns: DataTableColumn<Project>[] = [
    {
      key: 'code',
      header: t('projects.column_code'),
      render: (p) => <span className="font-medium text-text-primary">{p.code}</span>,
      sortable: true,
      sortAccessor: (p) => p.code,
      width: '140px',
    },
    {
      key: 'name',
      header: t('projects.column_name'),
      render: (p) => <span>{pickLocalized(p.name_i18n, locale)}</span>,
      sortable: true,
      sortAccessor: (p) => pickLocalized(p.name_i18n, locale).toLowerCase(),
    },
    {
      key: 'status',
      header: t('projects.column_status'),
      render: (p) => <ProjectStatusBadge status={p.status} />,
      width: '140px',
    },
    {
      key: 'start',
      header: t('projects.column_start'),
      render: (p) => p.start_date ?? '—',
      sortable: true,
      sortAccessor: (p) => p.start_date ?? '',
      width: '120px',
    },
    {
      key: 'updated',
      header: t('projects.column_updated'),
      render: (p) => (p.updated_at ? new Date(p.updated_at).toLocaleDateString(locale) : '—'),
      sortable: true,
      sortAccessor: (p) => p.updated_at ?? '',
      width: '140px',
    },
  ];

  return (
    <DataTable<Project>
      rows={rows}
      columns={columns}
      rowKey={(p) => p.id}
      loading={loading}
      searchPredicate={(p, q) => {
        const name = pickLocalized(p.name_i18n, locale).toLowerCase();
        return p.code.toLowerCase().includes(q) || name.includes(q);
      }}
      onRowClick={(p) => navigate(routes.projectWorkspace(p.id))}
      emptyTitle={t('projects.empty_title')}
      emptyBody={t('projects.empty_body')}
      toolbarRight={toolbarRight}
      caption={t('projects.list_title')}
    />
  );
}
