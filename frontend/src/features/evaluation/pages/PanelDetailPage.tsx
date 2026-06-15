import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Archive, RotateCcw, Trash2 } from 'lucide-react';
import { Card } from '@/shared/components/ui/Card';
import { Button } from '@/shared/components/ui/Button';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { EmptyState } from '@/shared/components/feedback/EmptyState';
import { NoAccessState } from '@/shared/components/feedback/NoAccessState';
import { ConfirmDialog } from '@/shared/components/confirm-dialog/ConfirmDialog';
import { PERMISSIONS } from '@/shared/types/permissions';
import { usePermission } from '@/features/auth/usePermission';
import { ApiError } from '@/shared/api/apiError';
import { pickLocalized } from '@/shared/lib/localized';
import { routes } from '@/shared/config/routes';
import {
  useArchivePanel,
  useDeletePanel,
  usePanelDetail,
  useReopenPanel,
} from '../hooks/usePanels';
import {
  isPanelArchivable,
  isPanelDeletable,
  isPanelReopenable,
} from '../panelTypes';
import { PanelStatusBadge } from '../components/panel/PanelStatusBadge';
import { PanelProgressView } from '../components/panel/PanelProgressView';
import { PanelAveragedResult } from '../components/panel/PanelAveragedResult';
import { PanelApprovalSummary } from '../components/panel/PanelApprovalSummary';

/**
 * T3 (Defect 2) — evaluation-panel detail page.
 *
 * Un-dead-ends a created panel: it was listable/fetchable via API but had no UI
 * surface. This page REUSES the existing panel components — {@link PanelProgressView}
 * (roster + blind-safe status), {@link PanelAveragedResult} (gated averages once
 * AVERAGED+) and {@link PanelApprovalSummary} — adding the three management
 * actions Delete / Archive / Reopen.
 *
 * Action gating: each action renders ONLY when the matching status predicate
 * holds (the SINGLE-source `isPanelDeletable` / `isPanelArchivable` /
 * `isPanelReopenable`, mirroring the BE guards) AND the user holds
 * EVALUATION_PANEL_MANAGE — so an APPROVED/LOCKED panel shows no action and
 * there is no 403/400 round-trip. Delete/Archive reuse the SAME reason-required
 * ConfirmDialog the evaluation delete uses (requireReason, ≥ 5 chars). The BE
 * remains the source of truth; this is UX gating only.
 */
type Pending = 'delete' | 'archive' | null;

export function PanelDetailPage() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const { projectId = '', panelId = '' } = useParams<{
    projectId: string;
    panelId: string;
  }>();
  const { can } = usePermission();
  const canRead = can(PERMISSIONS.EVALUATION_READ);
  const canManage = can(PERMISSIONS.EVALUATION_PANEL_MANAGE);

  const detailQuery = usePanelDetail(canRead ? panelId : undefined);
  const deleteMutation = useDeletePanel(panelId);
  const archiveMutation = useArchivePanel(panelId);
  const reopenMutation = useReopenPanel(panelId);

  const [pending, setPending] = useState<Pending>(null);

  const backToList = () => navigate(routes.projectEvaluation(projectId));

  // Turn a failed management action into a VISIBLE, localized message instead of
  // silently doing nothing (the prod symptom: confirm a delete → nothing
  // happens). We surface the stable backend error code + correlation id so the
  // real cause (e.g. PANEL_NOT_DELETABLE after a status change, an ABAC 404, or
  // a 403) is diagnosable rather than swallowed. The BE stays the source of
  // truth — this only reports what it returned.
  const describeError = (err: unknown): string => {
    if (!(err instanceof ApiError)) return t('panel.detail.action_failed');
    if (err.status === 0) return t('panel.detail.error_network');
    const ref = t('panel.detail.error_ref', {
      code: err.code,
      corr: err.correlationId ?? t('common.dash'),
    });
    let base: string;
    if (err.code === 'PANEL_NOT_DELETABLE') base = t('panel.detail.error_not_deletable');
    else if (err.code === 'PANEL_NOT_ARCHIVABLE') base = t('panel.detail.error_not_archivable');
    else if (err.isForbidden()) base = t('panel.detail.error_forbidden');
    else if (err.isNotFound()) base = t('panel.detail.error_not_found');
    else base = t('panel.detail.action_failed');
    return `${base} ${ref}`;
  };

  const closeDialog = () => {
    deleteMutation.reset();
    archiveMutation.reset();
    setPending(null);
  };

  if (!canRead) return <NoAccessState />;
  if (detailQuery.isLoading) return <LoadingState />;
  if (detailQuery.error) {
    const err = detailQuery.error;
    if (err instanceof ApiError && err.isForbidden()) return <NoAccessState />;
    // A cross-tenant / missing panel 404s — surface "not found or no access"
    // without enumerating, then offer a way back to the list (no dead-end).
    if (err instanceof ApiError && err.isNotFound()) {
      return (
        <div className="space-y-6">
          <Breadcrumbs extra={[{ label: t('nav.evaluation') }]} />
          <EmptyState
            title={t('panel.detail.not_found_title')}
            body={t('panel.detail.not_found_body')}
            action={
              <Button variant="secondary" onClick={backToList}>
                {t('panel.detail.back_to_list')}
              </Button>
            }
          />
        </div>
      );
    }
    const correlationId = err instanceof ApiError ? err.correlationId : undefined;
    return <ErrorState correlationId={correlationId} onRetry={() => detailQuery.refetch()} />;
  }

  const detail = detailQuery.data;
  if (!detail) return <EmptyState />;
  const panel = detail.panel;
  const status = panel.status;

  const showDelete = canManage && isPanelDeletable(status);
  const showArchive = canManage && isPanelArchivable(status);
  const showReopen = canManage && isPanelReopenable(status);
  const hasActions = showDelete || showArchive || showReopen;

  return (
    <div className="space-y-6" data-testid="panel-detail-page">
      <Breadcrumbs extra={[{ label: t('nav.evaluation') }]} />
      <header className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <div className="flex items-center gap-3 flex-wrap">
            <h1 className="text-2xl text-text-primary">
              {pickLocalized(panel.position_title_i18n, i18n.language)}
            </h1>
            <PanelStatusBadge status={status} />
          </div>
          <p className="text-sm text-text-secondary mt-1">
            {t('panel.detail.subtitle')}
          </p>
        </div>

        {hasActions ? (
          <div className="flex items-center gap-2" data-testid="panel-detail-actions">
            {showReopen ? (
              <Button
                variant="secondary"
                onClick={() => reopenMutation.mutate()}
                disabled={reopenMutation.isPending}
                data-testid="panel-reopen-action"
              >
                <RotateCcw size={14} aria-hidden /> {t('panel.detail.reopen')}
              </Button>
            ) : null}
            {showArchive ? (
              <Button
                variant="secondary"
                onClick={() => setPending('archive')}
                disabled={archiveMutation.isPending}
                data-testid="panel-archive-action"
              >
                <Archive size={14} aria-hidden /> {t('panel.detail.archive')}
              </Button>
            ) : null}
            {showDelete ? (
              <Button
                variant="danger"
                onClick={() => setPending('delete')}
                disabled={deleteMutation.isPending}
                data-testid="panel-delete-action"
              >
                <Trash2 size={14} aria-hidden /> {t('common.delete')}
              </Button>
            ) : null}
          </div>
        ) : null}
      </header>

      {/* Reopen has no confirm dialog, so surface its failure as an inline
          banner (instead of the previous silent no-op). */}
      {reopenMutation.isError ? (
        <div
          role="alert"
          data-testid="panel-reopen-error"
          className="rounded-md border border-danger-500/30 bg-danger-50 text-danger-700 text-sm p-3"
        >
          {describeError(reopenMutation.error)}
        </div>
      ) : null}

      {/* Roster + blind-safe status (reused component). */}
      <PanelProgressView panelId={panel.id} />

      {/* Averaged result — gated component renders a locked placeholder until
          AVERAGED, the table once AVERAGED+ AND CAMPAIGN_RESULTS_VIEW. */}
      <PanelAveragedResult panelId={panel.id} status={status} />

      {/* Post-approval summary (assigned grade + official averages). Reuses the
          SAME PanelApprovalSummary the CEO approval detail page renders. */}
      {status === 'APPROVED' || status === 'LOCKED' ? (
        <PanelApprovalSummary panelId={panel.id} />
      ) : null}

      {!hasActions ? (
        <Card compact>
          <p className="text-sm text-text-muted" data-testid="panel-detail-no-actions">
            {t('panel.detail.no_actions')}
          </p>
        </Card>
      ) : null}

      {/* Delete — reason-required confirm (≥ 5 chars), identical to the
          evaluation delete confirm. On success return to the list; on failure
          the dialog STAYS OPEN with the server error surfaced (no silent
          no-op). */}
      <ConfirmDialog
        open={pending === 'delete'}
        destructive
        requireReason
        reasonMinLength={5}
        busy={deleteMutation.isPending}
        error={deleteMutation.isError ? describeError(deleteMutation.error) : null}
        title={t('panel.detail.delete_title')}
        body={t('panel.detail.delete_body')}
        confirmLabel={t('common.delete')}
        reasonLabel={t('common.reason_label')}
        reasonPlaceholder={t('panel.detail.delete_reason_placeholder')}
        onCancel={closeDialog}
        onConfirm={async (reason) => {
          if (!reason) return;
          try {
            await deleteMutation.mutateAsync(reason);
            setPending(null);
            backToList();
          } catch {
            // Keep the dialog open; the error is shown via `error` above.
          }
        }}
      />

      {/* Archive — reason-required confirm; stays on the page (status flips to
          ARCHIVED, actions disappear). On failure the dialog stays open with
          the server error surfaced. */}
      <ConfirmDialog
        open={pending === 'archive'}
        requireReason
        reasonMinLength={5}
        busy={archiveMutation.isPending}
        error={archiveMutation.isError ? describeError(archiveMutation.error) : null}
        title={t('panel.detail.archive_title')}
        body={t('panel.detail.archive_body')}
        confirmLabel={t('panel.detail.archive')}
        reasonLabel={t('common.reason_label')}
        reasonPlaceholder={t('panel.detail.archive_reason_placeholder')}
        onCancel={closeDialog}
        onConfirm={async (reason) => {
          if (!reason) return;
          try {
            await archiveMutation.mutateAsync(reason);
            setPending(null);
          } catch {
            // Keep the dialog open; the error is shown via `error` above.
          }
        }}
      />
    </div>
  );
}
