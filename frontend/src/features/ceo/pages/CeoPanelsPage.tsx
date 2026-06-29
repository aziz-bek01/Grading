/**
 * CEO Panel Overview — org-wide (no projectId).
 *
 * Lists ALL panels across the tenant grouped by status section:
 *   - "Awaiting my sign-off" (SUBMITTED / AVERAGED) → inline sign-off buttons.
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
 *   - CeoInlineSignOffCell — inline approve/reject/request-changes (reuses
 *     the three decide hooks and both shared dialogs from the approval feature).
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
import type { ApprovalStep } from '@/features/approval/types';
import { CeoInlineSignOffCell } from '../components/CeoInlineSignOffCell';

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

/** Richer map value: both the approvalRequestId AND the current pending step. */
export interface CeoApprovalEntry {
  approvalId: string;
  currentStep: ApprovalStep;
}

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
  /** panelId -> { approvalId, currentStep } for inline sign-off; only present when step is PENDING. */
  approvalIdByPanelId: Map<string, CeoApprovalEntry>;
}) {
  const { t, i18n } = useTranslation();

  const columns: DataTableColumn<Panel>[] = useMemo(
    () => [
      {
        key: 'index',
        header: '№',
        render: (_row, index) => index + 1,
      },
      {
        key: 'department',
        header: t('ceo.panels.col_department'),
        render: (row) =>
          row.department_label_i18n
            ? pickLocalized(row.department_label_i18n, i18n.language, t('common.dash'))
            : t('common.dash'),
        sortable: true,
        sortAccessor: (row) =>
          row.department_label_i18n
            ? pickLocalized(row.department_label_i18n, i18n.language)
            : '',
      },
      {
        key: 'division',
        header: t('ceo.panels.col_division'),
        render: (row) =>
          row.division_label_i18n
            ? pickLocalized(row.division_label_i18n, i18n.language, '')
            : '',
        sortable: true,
        sortAccessor: (row) =>
          row.division_label_i18n
            ? pickLocalized(row.division_label_i18n, i18n.language)
            : '',
      },
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
        key: 'avg_score',
        header: t('ceo.panels.col_avg_score'),
        render: (row) =>
          row.displayed_total_score != null
            ? row.displayed_total_score.toFixed(1)
            : t('common.dash'),
        sortable: true,
        sortAccessor: (row) => row.displayed_total_score ?? -Infinity,
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
          // the inbox with a resolvable currentStep. Show inline sign-off buttons
          // using the CeoInlineSignOffCell (which reuses the three decide hooks and
          // both shared dialogs — no new approval logic duplicated here).
          const entry = approvalIdByPanelId.get(row.id);
          if (entry) {
            // Awaiting sign-off: show the decision dropdown AND an "Open"
            // link to ApprovalDetailsPage (the same rich page reached from the
            // Approvals inbox) so the CEO can review the full per-evaluator
            // breakdown without leaving the overview.
            return (
              <div className="flex items-center gap-2 flex-wrap">
                <CeoInlineSignOffCell
                  approvalId={entry.approvalId}
                  currentStep={entry.currentStep}
                />
                <Link
                  to={routes.approvalDetails(entry.approvalId)}
                  className="text-primary-600 hover:underline text-sm whitespace-nowrap"
                  data-testid={`ceo-open-approval-${row.id}`}
                >
                  {t('panel.list.open')}
                </Link>
              </div>
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

  // Default to the "Awaiting sign-off" tab: that is the CEO's primary action and
  // the only group whose rows carry the inline decision buttons. Landing on "All"
  // showed early-stage (COLLECTING / AWAITING_EVALUATIONS) panels first — no
  // average yet, only "Open" — which read as "the buttons are missing".
  const [activeGroup, setActiveGroup] = useState<StatusGroup>('pending');

  // Reuse the same inbox hook the sidebar badge uses — counts panels awaiting
  // CEO sign-off without a new endpoint.
  const inbox = useMyApprovalInbox();
  const pendingApprovalCount = useMemo(
    () =>
      (inbox.data ?? []).filter(
        (r) => r.entityType === 'EVALUATION_PANEL',
      ).length,
    [inbox.data],
  );

  // Map each panel awaiting sign-off to its pending approvalRequestId AND the
  // current PENDING step, so the inline cell can fire mutations directly.
  // Reuses the inbox data already fetched above — no extra request.
  // Only adds an entry when a PENDING step is found (same derivation as
  // ApprovalDetailsPage's currentStep logic).
  const approvalIdByPanelId = useMemo(() => {
    const m = new Map<string, CeoApprovalEntry>();
    for (const r of inbox.data ?? []) {
      if (r.entityType === 'EVALUATION_PANEL' && r.entityId) {
        const currentStep = r.steps.find((s) => s.status === 'PENDING') ?? null;
        if (currentStep) {
          m.set(r.entityId, { approvalId: r.id, currentStep });
        }
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
