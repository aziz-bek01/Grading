import { useCallback, useMemo, useState } from 'react';
import {
  Link,
  useNavigate,
  useParams,
  useSearchParams,
} from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { LayoutGrid, TableProperties } from 'lucide-react';
import { Card } from '@/shared/components/ui/Card';
import { Button } from '@/shared/components/ui/Button';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { DataTable, type DataTableColumn } from '@/shared/components/data-table/DataTable';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import { pickLocalized } from '@/shared/lib/localized';
import { cn } from '@/shared/lib/cn';
import { useMethodologies } from '@/features/methodology/hooks/useMethodology';
import { usePositions } from '@/features/positions/hooks/usePositions';
import {
  useCreateEvaluation,
  useEvaluations,
} from '../hooks/useEvaluation';
import { EvaluationStatusBadge } from '../components/EvaluationStatusBadge';
import { EvaluationByFactorView } from '../components/byFactor/EvaluationByFactorView';
import type { Evaluation, EvaluationStatus } from '../types';

type ViewMode = 'by-position' | 'by-factor';

function isViewMode(value: string | null): value is ViewMode {
  return value === 'by-position' || value === 'by-factor';
}

const STATUSES: EvaluationStatus[] = [
  'DRAFT',
  'INCOMPLETE',
  'COMPLETE',
  'SUBMITTED',
  'APPROVED',
  'LOCKED',
  'ARCHIVED',
];

/**
 * Evaluation list — table with status / methodology / position filters.
 * "+ New evaluation" CTA gated behind EVALUATION_EDIT.
 */
export function EvaluationListPage() {
  const { t, i18n } = useTranslation();
  const { projectId = '' } = useParams<{ projectId: string }>();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const mode: ViewMode = isViewMode(searchParams.get('mode'))
    ? (searchParams.get('mode') as ViewMode)
    : 'by-position';
  const factorParam = searchParams.get('factor');

  const setMode = useCallback(
    (next: ViewMode) => {
      // Preserve the factor param when switching to by-factor; drop it
      // when going back to by-position so URLs stay minimal.
      const params = new URLSearchParams(searchParams);
      params.set('mode', next);
      if (next === 'by-position') params.delete('factor');
      setSearchParams(params, { replace: true });
    },
    [searchParams, setSearchParams],
  );

  const setFactorInUrl = useCallback(
    (factorId: string) => {
      const params = new URLSearchParams(searchParams);
      params.set('mode', 'by-factor');
      params.set('factor', factorId);
      setSearchParams(params, { replace: true });
    },
    [searchParams, setSearchParams],
  );

  const [statusFilter, setStatusFilter] = useState<EvaluationStatus | ''>('');
  const [methodologyFilter, setMethodologyFilter] = useState<string>('');
  const [creating, setCreating] = useState(false);
  const [newPositionId, setNewPositionId] = useState('');
  const [newVersionId, setNewVersionId] = useState('');

  const evalsQuery = useEvaluations({
    projectId,
    status: statusFilter || undefined,
  });
  const positionsQuery = usePositions(projectId ? { projectId } : null);
  const methodologiesQuery = useMethodologies(projectId);
  const createMutation = useCreateEvaluation();

  const positionMap = useMemo(() => {
    const m = new Map<string, string>();
    for (const p of positionsQuery.data?.items ?? []) {
      m.set(p.id, pickLocalized(p.title_i18n, i18n.language));
    }
    return m;
  }, [positionsQuery.data, i18n.language]);

  const methodologyMap = useMemo(() => {
    const m = new Map<string, string>();
    for (const meth of methodologiesQuery.data?.items ?? []) {
      m.set(meth.id, pickLocalized(meth.name_i18n, i18n.language));
    }
    return m;
  }, [methodologiesQuery.data, i18n.language]);

  /**
   * Evaluations reference a methodology *version* id, but the dropdown filters
   * by methodology (container) id. Bridge version → methodology via the
   * active/latest version pointers the enriched list response now provides, so
   * the column shows the methodology name and the filter actually matches.
   */
  const versionToMeth = useMemo(() => {
    const m = new Map<string, { id: string; name: string }>();
    for (const meth of methodologiesQuery.data?.items ?? []) {
      const entry = { id: meth.id, name: pickLocalized(meth.name_i18n, i18n.language) };
      if (meth.active_version_id) m.set(meth.active_version_id, entry);
      if (meth.latest_version_id) m.set(meth.latest_version_id, entry);
    }
    return m;
  }, [methodologiesQuery.data, i18n.language]);

  const rows = useMemo(() => {
    let items = evalsQuery.data?.items ?? [];
    if (methodologyFilter) {
      items = items.filter(
        (e) => versionToMeth.get(e.methodology_version_id)?.id === methodologyFilter,
      );
    }
    return items;
  }, [evalsQuery.data, methodologyFilter, versionToMeth]);

  const columns: DataTableColumn<Evaluation>[] = [
    {
      key: 'position',
      header: t('evaluation.column.position'),
      render: (row) => positionMap.get(row.position_id) ?? row.position_id,
      sortable: true,
      sortAccessor: (row) => positionMap.get(row.position_id) ?? '',
    },
    {
      key: 'methodology',
      header: t('evaluation.column.methodology'),
      render: (row) =>
        versionToMeth.get(row.methodology_version_id)?.name ??
        row.methodology_version_id.slice(0, 8),
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
        (row.submitted_at ?? row.approved_at ?? row.locked_at ?? '').slice(
          0,
          10,
        ) || '—',
    },
    {
      key: 'actions',
      header: t('common.actions'),
      render: (row) => (
        <Link
          to={`/app/projects/${projectId}/evaluation/${row.id}`}
          className="text-primary-600 hover:underline text-sm"
          data-testid={`open-evaluation-${row.id}`}
        >
          {t('common.edit')}
        </Link>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <Breadcrumbs extra={[{ label: t('nav.evaluation') }]} />
      <header className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-2xl text-text-primary">
            {t('evaluation.list_title')}
          </h1>
          <p className="text-sm text-text-secondary mt-1">
            {t('evaluation.list_subtitle')}
          </p>
        </div>
        <PermissionGate permission={PERMISSIONS.EVALUATION_EDIT}>
          <Button
            onClick={() => setCreating(true)}
            data-testid="new-evaluation"
          >
            {t('evaluation.new_evaluation')}
          </Button>
        </PermissionGate>
      </header>

      {/* Mode toggle — preserves URL state via ?mode= so refresh / share works. */}
      <div
        role="tablist"
        aria-label={t('evaluation.byFactor.mode_toggle.aria')}
        className="inline-flex gap-1 p-1 bg-divider rounded-md"
        data-testid="evaluation-mode-toggle"
      >
        <button
          type="button"
          role="tab"
          aria-selected={mode === 'by-position'}
          onClick={() => setMode('by-position')}
          data-testid="mode-by-position"
          className={cn(
            'inline-flex items-center gap-1.5 px-3 py-1.5 text-sm rounded-md transition-colors',
            mode === 'by-position'
              ? 'bg-surface text-text-primary shadow-sm'
              : 'text-text-secondary hover:text-text-primary',
          )}
        >
          <LayoutGrid size={14} aria-hidden />
          {t('evaluation.byFactor.mode_toggle.by_position')}
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={mode === 'by-factor'}
          onClick={() => setMode('by-factor')}
          data-testid="mode-by-factor"
          className={cn(
            'inline-flex items-center gap-1.5 px-3 py-1.5 text-sm rounded-md transition-colors',
            mode === 'by-factor'
              ? 'bg-surface text-text-primary shadow-sm'
              : 'text-text-secondary hover:text-text-primary',
          )}
        >
          <TableProperties size={14} aria-hidden />
          {t('evaluation.byFactor.mode_toggle.by_factor')}
        </button>
      </div>

      {mode === 'by-factor' ? (
        <EvaluationByFactorView
          projectId={projectId}
          factorIdFromUrl={factorParam}
          onFactorChange={setFactorInUrl}
        />
      ) : null}

      {mode === 'by-position' ? (
      <Card>
        <DataTable<Evaluation>
          columns={columns}
          rows={rows}
          rowKey={(row) => row.id}
          loading={evalsQuery.isLoading}
          searchPredicate={(row, q) =>
            (positionMap.get(row.position_id) ?? '').toLowerCase().includes(q)
          }
          emptyTitle={t('evaluation.empty_title')}
          emptyBody={t('evaluation.empty_body')}
          filterBar={
            <div className="flex items-center gap-2 flex-wrap">
              <select
                aria-label={t('evaluation.filter.status')}
                value={statusFilter}
                onChange={(e) =>
                  setStatusFilter(e.target.value as EvaluationStatus | '')
                }
                data-testid="filter-status"
                className="h-9 px-3 border border-border-strong rounded-md text-sm bg-surface"
              >
                <option value="">{t('common.all')}</option>
                {STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {t(`evaluation.status.${s.toLowerCase()}`)}
                  </option>
                ))}
              </select>
              <select
                aria-label={t('evaluation.filter.methodology')}
                value={methodologyFilter}
                onChange={(e) => setMethodologyFilter(e.target.value)}
                data-testid="filter-methodology"
                className="h-9 px-3 border border-border-strong rounded-md text-sm bg-surface"
              >
                <option value="">{t('common.all')}</option>
                {Array.from(methodologyMap.entries()).map(([id, name]) => (
                  <option key={id} value={id}>
                    {name}
                  </option>
                ))}
              </select>
            </div>
          }
        />
      </Card>
      ) : null}

      {creating ? (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
        >
          <div className="bg-surface rounded-xl shadow-lg border border-border w-full max-w-md p-6 space-y-4">
            <h2 className="text-lg text-text-primary">
              {t('evaluation.create.title')}
            </h2>
            <label className="block text-sm font-medium">
              {t('evaluation.create.position')}
            </label>
            <select
              value={newPositionId}
              onChange={(e) => setNewPositionId(e.target.value)}
              data-testid="new-position-select"
              className="w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface"
            >
              <option value="">—</option>
              {(positionsQuery.data?.items ?? []).map((p) => (
                <option key={p.id} value={p.id}>
                  {pickLocalized(p.title_i18n, i18n.language)}
                </option>
              ))}
            </select>
            <label className="block text-sm font-medium">
              {t('evaluation.create.methodology')}
            </label>
            <select
              value={newVersionId}
              onChange={(e) => setNewVersionId(e.target.value)}
              data-testid="new-version-select"
              className="w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface"
            >
              <option value="">—</option>
              {(methodologiesQuery.data?.items ?? [])
                .filter((m) => m.active_version_id)
                .map((m) => (
                  <option key={m.id} value={m.active_version_id!}>
                    {pickLocalized(m.name_i18n, i18n.language)} v
                    {m.active_version_number}
                  </option>
                ))}
            </select>
            <div className="flex justify-end gap-2 pt-2">
              <Button variant="secondary" onClick={() => setCreating(false)}>
                {t('common.cancel')}
              </Button>
              <Button
                disabled={!newPositionId || !newVersionId}
                onClick={async () => {
                  const created = await createMutation.mutateAsync({
                    position_id: newPositionId,
                    methodology_version_id: newVersionId,
                  });
                  setCreating(false);
                  setNewPositionId('');
                  setNewVersionId('');
                  navigate(
                    `/app/projects/${projectId}/evaluation/${created.id}`,
                  );
                }}
                data-testid="new-evaluation-confirm"
              >
                {t('common.create')}
              </Button>
            </div>
          </div>
        </div>
      ) : null}

      {evalsQuery.isLoading ? <LoadingState /> : null}
    </div>
  );
}
