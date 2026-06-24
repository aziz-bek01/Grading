/**
 * CEO Panel Overview — org-wide (no projectId).
 *
 * Lists ALL panels across the tenant grouped by status section:
 *   - "Awaiting my sign-off" (SUBMITTED / AVERAGED) → links to the approvals inbox.
 *   - "In flight" (AWAITING_EVALUATIONS / COLLECTING) → shows dept head progress.
 *   - "Approved / Locked history" (APPROVED / LOCKED) → shows averaged result.
 *
 * REUSE STRATEGY (no duplicated code):
 *   - PanelListSection  — the same panel table used in EvaluationListPage.
 *   - PanelStatusBadge  — the same status chip used everywhere.
 *   - useCeoPanels      — thin wrapper around usePanels with status filter.
 *   - useMyApprovalInbox — count reuse (same hook the sidebar badge uses).
 *   - PermissionGate    — same gate component, gated EVALUATION_PANEL_APPROVE.
 *   - routes.approvalsInbox / routes.approvalDetails — no new approval pages.
 *
 * The CEO cannot manage (delete/archive/reopen) panels here — those actions
 * live on PanelDetailPage. Clicking any row navigates to the existing
 * PanelDetailPage (routes.projectPanelDetail) via PanelListSection.
 *
 * PanelListSection requires a `projectId` for its "Open" link column.
 * Since org-wide panels carry their own project_id, we group them and pass
 * the per-panel project_id inline via a custom render column rather than
 * forcing a single projectId. To keep the NO-DUPLICATION rule, we reuse the
 * existing DataTable + PanelStatusBadge directly for the status-grouped table
 * instead of patching PanelListSection's signature.
 */
import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Inbox, ClipboardCheck, RefreshCw } from 'lucide-react';
import { Card } from '@/shared/components/ui/Card';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { EmptyState } from '@/shared/components/feedback/EmptyState';
import { NoAccessState } from '@/shared/components/feedback/NoAccessState';
import { DataTable, type DataTableColumn } from '@/shared/components/data-table/DataTable';
import { PERMISSIONS } from '@/shared/types/permissions';
import { usePermission } from '@/features/auth/usePermission';
import { useMyApprovalInbox } from '@/features/approval/hooks/useApprovals';
import { ApiError } from '@/shared/api/apiError';
import { routes } from '@/shared/config/routes';
import { pickLocalized } from '@/shared/lib/localized';
import { formatDateSafe } from '@/shared/lib/dates';
import {
  useCeoPanels,
  useReconcilePanelApprovals,
} from '@/features/evaluation/hooks/usePanels';
import { PanelStatusBadge } from '@/features/evaluation/components/panel/PanelStatusBadge';
import type { Panel, PanelStatus } from '@/features/evaluation/panelTypes';

/** All statuses the CEO overview surfaces (excludes ARCHIVED). */
const ALL_CEO_STATUSES: PanelStatus[] = [
  'COLLECTING',
  'AWAITING_EVALUATIONS',
  'AVERAGED',
  'SUBMITTED',
  'APPROVED',
  'LOCKED',
];

/** Status groups for the tab/section filter. */
type StatusGroup = 'all' | 'pending' | 'inflight' | 'history';

const STATUS_GROUP_MAP: Record<StatusGroup, PanelStatus[]> = {
  all: ALL_CEO_STATUSES,
  pending: ['SUBMITTED', 'AVERAGED'],
  inflight: ['COLLECTING', 'AWAITING_EVALUATIONS'],
  history: ['APPROVED', 'LOCKED'],
};

/**
 * Org-wide panel table — reuses DataTable + PanelStatusBadge; links each row
 * to the existing PanelDetailPage (routes.projectPanelDetail).
 */
function CeoPanelTable({
  panels,
  loading,
  approvalIdByPanelId,
}: {
  panels: Panel[];
  loading?: boolean;
  /** panelId -> pending approvalRequestId (from the CEO inbox), for sign-off routing. */
  approvalIdByPanelId: Map<string, string>;
}) {
  const { t, i18n } = useTranslation();

  const columns: DataTableColumn<Panel>[] = useMemo(
    () => [
      {
        key: 'position',
        header: t('evaluation.column.position'),
        render: (row) =>
          pickLocalized(row.position_title_i18n, i18n.language, t('common.untitled')),
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
        render: (row) => {
          // A panel awaiting the CEO's decision has a pending approval request in
          // the inbox. Route it straight to the approval detail page — that is the
          // ONLY surface with the Approve/Reject controls AND the averaged result +
          // per-evaluator breakdown. The read-only PanelDetailPage has no sign-off
          // action for the CEO (no EVALUATION_PANEL_MANAGE), so linking there was a
          // dead end. Other statuses keep the read-only panel detail link.
          const approvalId = approvalIdByPanelId.get(row.id);
          if (approvalId) {
            return (
              <Link
                to={routes.approvalDetails(approvalId)}
                className="text-primary-600 hover:underline text-sm font-medium"
                data-testid={`ceo-signoff-panel-${row.id}`}
              >
                {t('ceo.panels.review_sign_off')}
              </Link>
            );
          }
          return (
            <Link
              to={routes.projectPanelDetail(row.project_id, row.id)}
              className="text-primary-600 hover:underline text-sm"
              data-testid={`ceo-open-panel-${row.id}`}
            >
              {t('panel.list.open')}
            </Link>
          );
        },
      },
    ],
    [t, i18n.language, approvalIdByPanelId],
  );

  return (
    <DataTable<Panel>
      columns={columns}
      rows={panels}
      rowKey={(row) => row.id}
      loading={loading}
      searchPredicate={(row, q) =>
        pickLocalized(row.position_title_i18n, i18n.language).toLowerCase().includes(q)
      }
      emptyTitle={t('ceo.panels.empty_title')}
      emptyBody={t('ceo.panels.empty_body')}
    />
  );
}

export function CeoPanelsPage() {
  const { t } = useTranslation();
  const { can } = usePermission();
  const canApprove = can(PERMISSIONS.EVALUATION_PANEL_APPROVE);
  const canManage = can(PERMISSIONS.EVALUATION_PANEL_MANAGE);
  const reconcile = useReconcilePanelApprovals();

  const [activeGroup, setActiveGroup] = useState<StatusGroup>('all');

  // Reuse the same inbox hook the sidebar badge uses — counts panels awaiting
  // CEO sign-off without a new endpoint.
  const inbox = useMyApprovalInbox();
  const pendingApprovalCount = useMemo(
    () =>
      (inbox.data?.items ?? []).filter(
        (r) => r.entityType === 'EVALUATION_PANEL',
      ).length,
    [inbox.data],
  );

  // Map each panel awaiting sign-off to its pending approvalRequestId, so a row can
  // link straight to the approval page (Approve/Reject). Reuses the inbox data
  // already fetched above — no extra request.
  const approvalIdByPanelId = useMemo(() => {
    const m = new Map<string, string>();
    for (const r of inbox.data?.items ?? []) {
      if (r.entityType === 'EVALUATION_PANEL' && r.entityId) {
        m.set(r.entityId, r.id);
      }
    }
    return m;
  }, [inbox.data]);

  // Single org-wide fetch with all relevant statuses (ARCHIVED excluded).
  // The hook is enabled because status.length > 0.
  const panelsQuery = useCeoPanels(ALL_CEO_STATUSES);

  const queryItems = panelsQuery.data?.items;
  const allPanels = useMemo(() => queryItems ?? [], [queryItems]);

  // Filter client-side by selected group (data already loaded).
  const visiblePanels = useMemo(() => {
    const allowed = STATUS_GROUP_MAP[activeGroup];
    return allPanels.filter((p) => allowed.includes(p.status));
  }, [allPanels, activeGroup]);

  if (!canApprove) return <NoAccessState />;

  const groups: { key: StatusGroup; labelKey: string }[] = [
    { key: 'all', labelKey: 'ceo.panels.filter_all' },
    { key: 'pending', labelKey: 'ceo.panels.filter_pending' },
    { key: 'inflight', labelKey: 'ceo.panels.filter_inflight' },
    { key: 'history', labelKey: 'ceo.panels.filter_history' },
  ];

  return (
    <div className="space-y-6" data-testid="ceo-panels-page">
      <Breadcrumbs />

      <header className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-2xl text-text-primary">{t('ceo.panels.title')}</h1>
          <p className="text-sm text-text-secondary mt-1">{t('ceo.panels.subtitle')}</p>
        </div>
        {/* Link to the existing approvals inbox — no duplicate approval UI. */}
        <div className="flex items-center gap-2">
          {/* Ops repair: recompute missing averages + open missing CEO sign-offs
              for backfilled panels (panels stuck "Awaiting CEO approval" with no
              average / not in the inbox). Idempotent; only for panel managers. */}
          {canManage ? (
            <button
              type="button"
              onClick={() => reconcile.mutate()}
              disabled={reconcile.isPending}
              className="inline-flex items-center gap-2 h-10 px-4 text-sm rounded-md font-medium bg-surface text-text-primary border border-border-strong hover:bg-divider disabled:opacity-60 disabled:cursor-not-allowed"
              data-testid="ceo-reconcile-btn"
              title={t('ceo.panels.reconcile_hint')}
            >
              <RefreshCw
                size={16}
                aria-hidden
                className={reconcile.isPending ? 'animate-spin' : undefined}
              />
              {reconcile.isPending
                ? t('ceo.panels.reconcile_running')
                : t('ceo.panels.reconcile')}
            </button>
          ) : null}
          <Link
            to={routes.approvalsInbox}
            className="inline-flex items-center gap-2 h-10 px-4 text-sm rounded-md font-medium bg-surface text-text-primary border border-border-strong hover:bg-divider"
            data-testid="ceo-inbox-link"
          >
            <Inbox size={16} aria-hidden />
            {t('ceo.panels.go_to_inbox')}
            {pendingApprovalCount > 0 ? (
              <span
                className="inline-flex items-center justify-center min-w-[20px] h-5 px-1.5 rounded-full bg-danger-500 text-text-inverse text-xs font-medium"
                data-testid="ceo-inbox-count"
              >
                {pendingApprovalCount > 99 ? '99+' : pendingApprovalCount}
              </span>
            ) : null}
          </Link>
        </div>
      </header>

      {/* Reconcile outcome — inline (no toast system in this app). */}
      {reconcile.isSuccess ? (
        <div
          className="rounded-md border border-success-200 bg-success-50 px-4 py-2 text-sm text-success-700"
          role="status"
          data-testid="ceo-reconcile-result"
        >
          {t('ceo.panels.reconcile_done', {
            opened: reconcile.data.openedPanelApprovals,
            cancelled: reconcile.data.cancelledLegacyApprovals,
          })}
        </div>
      ) : null}
      {reconcile.isError ? (
        <div
          className="rounded-md border border-danger-200 bg-danger-50 px-4 py-2 text-sm text-danger-700"
          role="alert"
          data-testid="ceo-reconcile-error"
        >
          {t('ceo.panels.reconcile_error')}
          {reconcile.error instanceof ApiError && reconcile.error.correlationId ? (
            <span className="ml-1 opacity-80">
              (
              {t('ceo.panels.reconcile_error_ref', {
                code: reconcile.error.code,
                ref: reconcile.error.correlationId,
              })}
              )
            </span>
          ) : null}
        </div>
      ) : null}

      {/* Status-group tabs (client-side filter — data is already loaded). */}
      <div
        className="flex gap-1 flex-wrap border-b border-border pb-0"
        role="tablist"
        aria-label={t('ceo.panels.filter_aria')}
      >
        {groups.map(({ key, labelKey }) => (
          <button
            key={key}
            role="tab"
            aria-selected={activeGroup === key}
            onClick={() => setActiveGroup(key)}
            data-testid={`ceo-filter-${key}`}
            className={
              activeGroup === key
                ? 'px-4 py-2 text-sm font-medium border-b-2 border-primary-500 text-primary-700 -mb-px'
                : 'px-4 py-2 text-sm text-text-secondary hover:text-text-primary -mb-px'
            }
          >
            {t(labelKey)}
          </button>
        ))}
      </div>

      {/* Panel table */}
      {panelsQuery.isLoading ? (
        <LoadingState />
      ) : panelsQuery.error ? (
        <ErrorState onRetry={() => panelsQuery.refetch()} />
      ) : allPanels.length === 0 ? (
        <EmptyState
          icon={<ClipboardCheck size={32} aria-hidden />}
          title={t('ceo.panels.empty_title')}
          body={t('ceo.panels.empty_body')}
        />
      ) : (
        <Card data-testid="ceo-panels-table-card">
          <CeoPanelTable
            panels={visiblePanels}
            loading={panelsQuery.isFetching}
            approvalIdByPanelId={approvalIdByPanelId}
          />
        </Card>
      )}
    </div>
  );
}
