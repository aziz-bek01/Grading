import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Pencil, Trash2 } from 'lucide-react';
import { DataTable, type DataTableColumn } from '@/shared/components/data-table/DataTable';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import { pickLocalized } from '@/shared/lib/localized';
import { formatDateSafe } from '@/shared/lib/dates';
import { cn } from '@/shared/lib/cn';
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
  /** Opens the edit drawer for the row. Gated by POSITION_EDIT. */
  onEdit?: (position: Position) => void;
  /** Opens the archive confirmation for the row. Gated by POSITION_EDIT. */
  onArchive?: (position: Position) => void;
  /**
   * Real server-driven pagination — `rows` is already exactly the current
   * server page (see `PositionListPage`). Forwarded straight to `DataTable`.
   */
  serverPagination?: {
    page: number;
    totalPages: number;
    total: number;
    pageSize: number;
    onPageChange: (page: number) => void;
  };
}

/**
 * A position whose status is ARCHIVED cannot be edited or archived again —
 * the backend rejects those mutations. The actions are suppressed up front.
 */
function isMutable(status: Position['status']): boolean {
  return status !== 'ARCHIVED';
}

export function PositionTable({
  projectId,
  rows,
  departments,
  loading,
  filterBar,
  toolbarRight,
  onEdit,
  onArchive,
  serverPagination,
}: PositionTableProps) {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const locale = i18n.language;
  const depById = new Map(departments.map((d) => [d.id, d] as const));

  const columns: DataTableColumn<Position>[] = [
    {
      key: 'title',
      header: t('positions.column_title'),
      render: (p) => <span className="font-medium text-text-primary">{pickLocalized(p.title_i18n, locale)}</span>,
      sortable: true,
      sortAccessor: (p) => pickLocalized(p.title_i18n, locale).toLowerCase(),
    },
    {
      key: 'department',
      header: t('positions.column_department'),
      render: (p) => {
        const d = depById.get(p.department_id);
        return d ? `${d.code} · ${pickLocalized(d.name_i18n, locale)}` : '—';
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
      render: (p) => formatDateSafe(p.updated_at, locale),
      sortable: true,
      sortAccessor: (p) => p.updated_at ?? '',
      width: '140px',
    },
  ];

  // Trailing actions column — only mounted when at least one handler is wired.
  if (onEdit || onArchive) {
    columns.push({
      key: 'actions',
      header: t('positions.column_actions'),
      width: '108px',
      className: 'text-right',
      render: (p) => {
        const mutable = isMutable(p.status);
        return (
          <PermissionGate permission={PERMISSIONS.POSITION_EDIT}>
            {/* When the position is archived there is nothing to do — render a
                dash rather than disabled icons so the row reads as read-only. */}
            {mutable ? (
              <div className="flex items-center justify-end gap-1" data-testid={`position-actions-${p.id}`}>
                {onEdit ? (
                  <IconButton
                    label={t('common.edit')}
                    testId={`position-edit-${p.id}`}
                    onClick={(e) => {
                      // Never let the action bubble to the row → detail navigation.
                      e.stopPropagation();
                      onEdit(p);
                    }}
                  >
                    <Pencil size={15} aria-hidden />
                  </IconButton>
                ) : null}
                {onArchive ? (
                  <IconButton
                    label={t('common.archive')}
                    testId={`position-archive-${p.id}`}
                    danger
                    onClick={(e) => {
                      e.stopPropagation();
                      onArchive(p);
                    }}
                  >
                    <Trash2 size={15} aria-hidden />
                  </IconButton>
                ) : null}
              </div>
            ) : (
              <span className="text-text-muted" data-testid={`position-actions-locked-${p.id}`}>
                —
              </span>
            )}
          </PermissionGate>
        );
      },
    });
  }

  return (
    <DataTable<Position>
      rows={rows}
      columns={columns}
      rowKey={(p) => p.id}
      loading={loading}
      searchPredicate={(p, q) => {
        const title = pickLocalized(p.title_i18n, locale).toLowerCase();
        return p.code.toLowerCase().includes(q) || title.includes(q);
      }}
      filterBar={filterBar}
      toolbarRight={toolbarRight}
      emptyTitle={t('positions.empty_title')}
      emptyBody={t('positions.empty_body')}
      onRowClick={(p) => navigate(routes.projectPositionDetail(projectId, p.id))}
      serverPagination={serverPagination}
    />
  );
}

interface IconButtonProps {
  label: string;
  testId: string;
  danger?: boolean;
  onClick: (e: React.MouseEvent<HTMLButtonElement>) => void;
  children: React.ReactNode;
}

function IconButton({ label, testId, danger, onClick, children }: IconButtonProps) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      data-testid={testId}
      onClick={onClick}
      className={cn(
        'inline-flex items-center justify-center h-8 w-8 rounded-md transition-colors',
        'focus:outline-none focus:ring-2 focus:ring-primary-500',
        danger
          ? 'text-danger-700 hover:bg-danger-50'
          : 'text-text-secondary hover:bg-divider hover:text-text-primary',
      )}
    >
      {children}
    </button>
  );
}
