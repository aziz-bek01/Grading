import { useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Plus } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { FilterBar, type FilterDefinition } from '@/shared/components/data-table/FilterBar';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { PositionTable } from '../components/PositionTable';
import { PositionFormDrawer } from '../components/PositionFormDrawer';
import { usePositions } from '../hooks/usePositions';
import { useCreatePosition } from '../hooks/useCreatePosition';
import { useDepartmentTree } from '@/features/organization/hooks/useDepartmentTree';
import type { PositionStatus } from '../types/positionTypes';
import { pickLocalized } from '@/shared/lib/localized';

export function PositionListPage() {
  const { t, i18n } = useTranslation();
  const { projectId } = useParams<{ projectId: string }>();
  const [departmentId, setDepartmentId] = useState<string | null>(null);
  const [status, setStatus] = useState<PositionStatus | null>(null);
  const [jobFamily, setJobFamily] = useState<string | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const tree = useDepartmentTree(projectId);
  const positions = usePositions(
    projectId ? { projectId, departmentId, status, jobFamily, page: 0, size: 50 } : null,
  );
  const createMutation = useCreatePosition();

  const departments = tree.data ?? [];
  const rows = positions.data?.items ?? [];

  const filters: FilterDefinition[] = useMemo(
    () => [
      {
        key: 'department',
        label: t('positions.filter_department'),
        value: departmentId,
        onChange: (v) => setDepartmentId(v),
        options: departments
          .filter((d) => d.status !== 'ARCHIVED')
          .map((d) => ({ value: d.id, label: `${d.code} · ${pickLocalized(d.name, i18n.language)}` })),
      },
      {
        key: 'jobFamily',
        label: t('positions.filter_job_family'),
        value: jobFamily,
        onChange: (v) => setJobFamily(v),
        options: Array.from(new Set(rows.map((p) => p.job_family).filter((x): x is string => !!x))).map(
          (jf) => ({ value: jf, label: jf }),
        ),
      },
      {
        key: 'status',
        label: t('positions.filter_status'),
        value: status,
        onChange: (v) => setStatus((v as PositionStatus | null) ?? null),
        options: [
          { value: 'ACTIVE', label: t('status.active') },
          { value: 'DRAFT', label: t('status.draft') },
          { value: 'ARCHIVED', label: t('status.archived') },
        ],
      },
    ],
    [departments, rows, departmentId, jobFamily, status, t, i18n.language],
  );

  return (
    <div className="space-y-6">
      <Breadcrumbs />
      <header className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-2xl text-text-primary">{t('positions.list_title')}</h1>
          <p className="text-sm text-text-secondary mt-1">{t('positions.list_subtitle')}</p>
        </div>
      </header>

      {positions.error ? (
        <ErrorState onRetry={() => positions.refetch()} />
      ) : positions.isLoading ? (
        <LoadingState />
      ) : (
        <PositionTable
          projectId={projectId ?? ''}
          rows={rows}
          departments={departments}
          loading={positions.isFetching}
          filterBar={
            <FilterBar
              filters={filters}
              onReset={() => {
                setDepartmentId(null);
                setStatus(null);
                setJobFamily(null);
              }}
            />
          }
          toolbarRight={
            <PermissionGate permission={PERMISSIONS.POSITION_CREATE}>
              <Button
                variant="primary"
                size="sm"
                leadingIcon={<Plus size={14} />}
                onClick={() => setDrawerOpen(true)}
                data-testid="new-position-button"
              >
                {t('positions.new_position')}
              </Button>
            </PermissionGate>
          }
        />
      )}

      <PositionFormDrawer
        open={drawerOpen}
        projectId={projectId ?? ''}
        departments={departments}
        onClose={() => setDrawerOpen(false)}
        onSubmit={async (input) => {
          await createMutation.mutateAsync({
            project_id: input.project_id,
            department_id: input.department_id,
            code: input.code,
            title: input.title,
            function: input.function || undefined,
            category: input.category || undefined,
            job_family: input.job_family || undefined,
            job_level: input.job_level || undefined,
          });
        }}
      />
    </div>
  );
}
