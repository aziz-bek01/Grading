import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Trash2 } from 'lucide-react';
import type { DataTableColumn } from '@/shared/components/data-table/DataTable';
import { PERMISSIONS } from '@/shared/types/permissions';
import { usePermission } from '@/features/auth/usePermission';
import { EvaluationStatusBadge } from '../EvaluationStatusBadge';
import { isEvaluationDeletable, type Evaluation } from '../../types';

interface UseEvaluationListColumnsArgs {
  projectId: string;
  positionMap: Map<string, string>;
  departmentNameOfPosition: (positionId: string) => string;
  versionToMeth: Map<string, { id: string; name: string }>;
  onDeleteRequest: (row: Evaluation) => void;
}

/**
 * Builds the by-position DataTable column set. Extracted from
 * `EvaluationListPage` (FE-041) — pure column config + the row-level actions
 * cell (edit link / delete button), unchanged behaviour and testids.
 */
export function useEvaluationListColumns({
  projectId,
  positionMap,
  departmentNameOfPosition,
  versionToMeth,
  onDeleteRequest,
}: UseEvaluationListColumnsArgs): DataTableColumn<Evaluation>[] {
  const { t } = useTranslation();
  const { can } = usePermission();
  const canEdit = can(PERMISSIONS.EVALUATION_EDIT);

  return [
    {
      key: 'position',
      header: t('evaluation.column.position'),
      render: (row) => positionMap.get(row.position_id) ?? row.position_id,
      sortable: true,
      sortAccessor: (row) => positionMap.get(row.position_id) ?? '',
    },
    {
      // FE-1: department column derived FE-side from already-available data.
      key: 'department',
      header: t('evaluation.column.department'),
      render: (row) => departmentNameOfPosition(row.position_id) || '—',
      sortable: true,
      sortAccessor: (row) => departmentNameOfPosition(row.position_id),
    },
    {
      key: 'methodology',
      header: t('evaluation.column.methodology'),
      // FE-027: prefer the backend-resolved `methodologyVersionLabel`
      // ("Name (vN)") — every row already carries it, no per-methodology
      // version fetch needed. Falls back to the FE-derived methodology name
      // (versionToMeth, active/latest only — see its comment) for older API
      // responses that predate the field, then a dash.
      render: (row) =>
        row.methodologyVersionLabel ??
        versionToMeth.get(row.methodology_version_id)?.name ??
        '—',
    },
    {
      key: 'status',
      header: t('common.status'),
      render: (row) => <EvaluationStatusBadge status={row.status} />,
      sortable: true,
      sortAccessor: (row) => row.status,
    },
    {
      key: 'total',
      header: t('evaluation.column.total'),
      render: (row) =>
        row.displayed_total_score != null
          ? Number(row.displayed_total_score).toFixed(2)
          : '—',
      sortable: true,
      sortAccessor: (row) => row.displayed_total_score ?? 0,
    },
    {
      key: 'updated',
      header: t('evaluation.column.updated'),
      render: (row) =>
        (row.submitted_at ?? row.approved_at ?? row.locked_at ?? '').slice(0, 10) || '—',
    },
    {
      key: 'actions',
      header: t('common.actions'),
      render: (row) => (
        <div className="flex items-center gap-3">
          <Link
            to={`/app/projects/${projectId}/evaluation/${row.id}`}
            className="text-primary-600 hover:underline text-sm"
            data-testid={`open-evaluation-${row.id}`}
          >
            {t('common.edit')}
          </Link>
          {canEdit && isEvaluationDeletable(row.status) ? (
            <button
              type="button"
              onClick={() => onDeleteRequest(row)}
              data-testid={`delete-evaluation-${row.id}`}
              aria-label={t('evaluation.delete.action')}
              className="inline-flex items-center gap-1 text-danger-600 hover:underline text-sm"
            >
              <Trash2 size={14} aria-hidden />
              {t('common.delete')}
            </button>
          ) : null}
        </div>
      ),
    },
  ];
}
