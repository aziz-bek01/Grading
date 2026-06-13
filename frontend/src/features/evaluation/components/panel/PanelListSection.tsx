import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Card } from '@/shared/components/ui/Card';
import { DataTable, type DataTableColumn } from '@/shared/components/data-table/DataTable';
import { pickLocalized } from '@/shared/lib/localized';
import { formatDateSafe } from '@/shared/lib/dates';
import { routes } from '@/shared/config/routes';
import type { Panel } from '../../panelTypes';
import { PanelStatusBadge } from './PanelStatusBadge';

interface Props {
  projectId: string;
  /** Already-loaded panels (the host loads them for the coverage strip). */
  panels: Panel[];
  loading?: boolean;
}

/**
 * T3 (Defect 2) — minimal evaluation-panels list surface on the evaluation
 * list page. Un-dead-ends a created panel: each row links to the panel detail
 * route where it can be viewed and managed.
 *
 * REUSES the shared {@link DataTable} + {@link PanelStatusBadge} (no bespoke
 * table) and the `panels` the host already loaded via `usePanels` (no new
 * fetch). The position title comes from the panel's own
 * `position_title_i18n` (BE-resolved), so no positions cross-reference is
 * needed here.
 */
export function PanelListSection({ projectId, panels, loading }: Props) {
  const { t, i18n } = useTranslation();

  const columns: DataTableColumn<Panel>[] = useMemo(
    () => [
      {
        key: 'position',
        header: t('evaluation.column.position'),
        render: (row) => pickLocalized(row.position_title_i18n, i18n.language),
        sortable: true,
        sortAccessor: (row) => pickLocalized(row.position_title_i18n, i18n.language),
      },
      {
        key: 'status',
        header: t('common.status'),
        render: (row) => <PanelStatusBadge status={row.status} />,
        sortable: true,
        sortAccessor: (row) => row.status,
      },
      {
        key: 'evaluators',
        header: t('panel.list.col_evaluators'),
        render: (row) =>
          t('panel.list.evaluators_value', {
            completed: row.completed_count,
            total: row.evaluator_count,
          }),
        sortAccessor: (row) => row.evaluator_count,
      },
      {
        key: 'created',
        header: t('panel.list.col_created'),
        render: (row) => formatDateSafe(row.created_at, i18n.language, t('common.dash')),
        sortable: true,
        sortAccessor: (row) => row.created_at,
      },
      {
        key: 'actions',
        header: t('common.actions'),
        render: (row) => (
          <Link
            to={routes.projectPanelDetail(projectId, row.id)}
            className="text-primary-600 hover:underline text-sm"
            data-testid={`open-panel-detail-${row.id}`}
          >
            {t('panel.list.open')}
          </Link>
        ),
      },
    ],
    [t, i18n.language, projectId],
  );

  return (
    <Card>
      <div className="flex items-center justify-between mb-3">
        <h2 className="text-sm font-medium text-text-primary">
          {t('panel.list.title')}
        </h2>
      </div>
      <DataTable<Panel>
        columns={columns}
        rows={panels}
        rowKey={(row) => row.id}
        loading={loading}
        searchPredicate={(row, q) =>
          pickLocalized(row.position_title_i18n, i18n.language)
            .toLowerCase()
            .includes(q)
        }
        emptyTitle={t('panel.list.empty_title')}
        emptyBody={t('panel.list.empty_body')}
      />
    </Card>
  );
}
