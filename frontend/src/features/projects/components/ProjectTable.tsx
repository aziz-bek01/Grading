import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Pencil, Trash2 } from 'lucide-react';
import { DataTable, type DataTableColumn } from '@/shared/components/data-table/DataTable';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import { pickLocalized } from '@/shared/lib/localized';
import { cn } from '@/shared/lib/cn';
import { routes } from '@/shared/config/routes';
import type { Project } from '../types/projectTypes';
import { ProjectStatusBadge } from './ProjectStatusBadge';

interface ProjectTableProps {
  rows: Project[];
  loading?: boolean;
  toolbarRight?: React.ReactNode;
  /** Opens the edit drawer for the row. Gated by PROJECT_EDIT. */
  onEdit?: (project: Project) => void;
  /** Opens the archive confirmation for the row. Gated by PROJECT_EDIT. */
  onArchive?: (project: Project) => void;
}

/**
 * A project whose status is ARCHIVED or LOCKED cannot be edited or archived —
 * the backend rejects those mutations. The actions are suppressed up front so
 * we never offer an action the server will refuse (no wasted 4xx round-trip).
 */
function isMutable(status: Project['status']): boolean {
  return status !== 'ARCHIVED' && status !== 'LOCKED';
}

export function ProjectTable({ rows, loading, toolbarRight, onEdit, onArchive }: ProjectTableProps) {
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

  // Trailing actions column — only mounted when at least one handler is wired.
  if (onEdit || onArchive) {
    columns.push({
      key: 'actions',
      header: t('projects.column_actions'),
      width: '108px',
      className: 'text-right',
      render: (p) => {
        const mutable = isMutable(p.status);
        return (
          <PermissionGate permission={PERMISSIONS.PROJECT_EDIT}>
            {/* When the project is locked/archived there is nothing to do — render a
                dash rather than disabled icons so the row reads as read-only. */}
            {mutable ? (
              <div className="flex items-center justify-end gap-1" data-testid={`project-actions-${p.id}`}>
                {onEdit ? (
                  <IconButton
                    label={t('common.edit')}
                    testId={`project-edit-${p.id}`}
                    onClick={(e) => {
                      // Never let the action bubble to the row → workspace navigation.
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
                    testId={`project-archive-${p.id}`}
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
              <span className="text-text-muted" data-testid={`project-actions-locked-${p.id}`}>
                —
              </span>
            )}
          </PermissionGate>
        );
      },
    });
  }

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
