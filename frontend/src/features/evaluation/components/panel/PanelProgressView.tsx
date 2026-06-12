import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/shared/components/ui/Button';
import { Card } from '@/shared/components/ui/Card';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { EmptyState } from '@/shared/components/feedback/EmptyState';
import { NoAccessState } from '@/shared/components/feedback/NoAccessState';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import { usePermission } from '@/features/auth/usePermission';
import { ApiError } from '@/shared/api/apiError';
import { pickLocalized } from '@/shared/lib/localized';
import { shortId } from '@/shared/lib/shortId';
import { formatDateSafe } from '@/shared/lib/dates';
import { ProgressChip } from '../byFactor/ProgressChip';
import { usePanelDetail } from '../../hooks/usePanels';
import {
  MANDATORY_EVALUATOR_ROLES,
  type EvaluatorRole,
  type PanelAssignment,
} from '../../panelTypes';
import { PanelRoleChip } from './PanelRoleChip';
import { PanelStatusBadge } from './PanelStatusBadge';
import { PanelAssignmentStatusBadge } from './PanelAssignmentStatusBadge';

interface Props {
  panelId: string;
  /** CTA back to the assign-evaluators dialog when the roster is empty. */
  onAssign?: () => void;
}

/**
 * Panel progress view (FE-4 / UX 6.3).
 *
 * Compact roster: one line per assignment → name (resolver) + role chip +
 * assignment-status badge + last-updated. Header carries a "N/M completed"
 * ProgressChip and the panel status badge.
 *
 * BLIND-SAFE: STATUS ONLY, never scores. It is shown to PM/HR before completion
 * without leaking bias — the BE detail endpoint deliberately omits scores while
 * collecting. A "blocked" line names the responsible role + person.
 *
 * Hidden entirely without EVALUATION_READ (the parent PermissionGate); a 403
 * from the detail endpoint degrades to a no-access state, not the global crash.
 */
export function PanelProgressView({ panelId, onAssign }: Props) {
  const { t, i18n } = useTranslation();
  const { can } = usePermission();
  const canRead = can(PERMISSIONS.EVALUATION_READ);
  const detailQuery = usePanelDetail(canRead ? panelId : undefined);

  const roster: PanelAssignment[] = useMemo(
    () => detailQuery.data?.roster ?? [],
    [detailQuery.data],
  );
  // Active = not withdrawn. The progress denominator and the "blocked on" line
  // both exclude WITHDRAWN seats (mirrors the BE averaging denominator AC-9).
  const active = useMemo(
    () => roster.filter((a) => a.assignment_status !== 'WITHDRAWN'),
    [roster],
  );
  const completed = active.filter((a) => a.assignment_status === 'COMPLETED');

  // First non-completed active seat among the mandatory trio — drives the
  // "Waiting on: External expert" blocked line (UX 6.3).
  const blockedOn = useMemo(() => {
    const order: Record<EvaluatorRole, number> = {
      HR_DIRECTOR: 0,
      DEPARTMENT_DIRECTOR: 1,
      EXTERNAL_EXPERT: 2,
      ADDITIONAL: 3,
    };
    const pending = active
      .filter((a) => a.assignment_status !== 'COMPLETED')
      .sort((a, b) => order[a.evaluator_role] - order[b.evaluator_role]);
    return pending[0] ?? null;
  }, [active]);

  if (!canRead) return <NoAccessState />;
  if (detailQuery.isLoading) return <LoadingState />;
  if (detailQuery.error) {
    const err = detailQuery.error;
    if (err instanceof ApiError && err.isForbidden()) return <NoAccessState />;
    const correlationId = err instanceof ApiError ? err.correlationId : undefined;
    return (
      <ErrorState correlationId={correlationId} onRetry={() => detailQuery.refetch()} />
    );
  }

  const detail = detailQuery.data;
  if (!detail) return <EmptyState />;
  const panel = detail.panel;

  return (
    <Card
      compact
      data-testid="panel-progress-view"
      title={pickLocalized(panel.position_title_i18n, i18n.language)}
      action={
        <div className="flex items-center gap-2">
          <ProgressChip filled={completed.length} total={active.length} />
          <PanelStatusBadge status={panel.status} />
        </div>
      }
    >
      {active.length === 0 ? (
        <EmptyState
          title={t('panel.progress.empty_title')}
          body={t('panel.progress.empty_body')}
          action={
            onAssign ? (
              <PermissionGate permission={PERMISSIONS.EVALUATION_PANEL_MANAGE}>
                <Button onClick={onAssign} data-testid="panel-progress-assign-cta">
                  {t('panel.progress.assign_cta')}
                </Button>
              </PermissionGate>
            ) : undefined
          }
        />
      ) : (
        <>
          {blockedOn ? (
            <p
              className="text-sm text-warning-700 mb-3 flex items-center gap-1.5 flex-wrap"
              data-testid="panel-progress-blocked"
            >
              <span>{t('panel.progress.waiting_on')}</span>
              <PanelRoleChip role={blockedOn.evaluator_role} />
              <span className="text-text-secondary">
                {blockedOn.evaluator_name ?? shortId(blockedOn.evaluator_user_id)}
              </span>
            </p>
          ) : (
            <p
              className="text-sm text-success-700 mb-3"
              data-testid="panel-progress-all-done"
            >
              {t('panel.progress.all_complete')}
            </p>
          )}

          <ul className="divide-y divide-divider" data-testid="panel-roster-list">
            {/* Mandatory trio first, then extras — stable, readable order. */}
            {[...active]
              .sort((a, b) => {
                const ai = MANDATORY_EVALUATOR_ROLES.indexOf(a.evaluator_role);
                const bi = MANDATORY_EVALUATOR_ROLES.indexOf(b.evaluator_role);
                return (ai < 0 ? 99 : ai) - (bi < 0 ? 99 : bi);
              })
              .map((a) => (
                <li
                  key={a.id}
                  data-testid={`panel-roster-row-${a.evaluator_user_id}`}
                  className="flex items-center gap-3 py-2 text-sm flex-wrap"
                >
                  <span className="font-medium text-text-primary min-w-[10rem]">
                    {a.evaluator_name ?? shortId(a.evaluator_user_id)}
                  </span>
                  <span className="min-w-[8.5rem]">
                    <PanelRoleChip role={a.evaluator_role} />
                  </span>
                  <PanelAssignmentStatusBadge status={a.assignment_status} />
                  <span className="text-xs text-text-muted ml-auto">
                    {formatDateSafe(a.updated_at, i18n.language, t('common.dash'))}
                  </span>
                </li>
              ))}
          </ul>
        </>
      )}
    </Card>
  );
}
