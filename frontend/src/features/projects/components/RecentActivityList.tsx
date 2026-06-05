/**
 * Recent Activity panel for the Project Workspace (D-2).
 *
 * Reuses the audit feed via `useAuditEvents` and renders a compact list
 * of the latest events touching the project's domain. Click → deep-link
 * to `/app/audit?projectId=...` so the user can drill down with the
 * full filter bar.
 *
 * Backend limitation:
 *   The current `GET /audit` contract has no `projectId` query param.
 *   We approximate by filtering for `entityType=PROJECT` AND
 *   `entityId=<projectId>`, then merging with a server-side action match
 *   on project-relevant codes. When the backend grows a `projectId`
 *   parameter, replace this with a single-call hook.
 *
 * Security:
 *   - Tenant scoping is the responsibility of the audit API (JWT-derived).
 *   - If the user lacks AUDIT_READ, the parent guard never mounts this
 *     panel — see `RequireAuditPermission`. Workspace mounts this for
 *     any user, but the audit hook will return 403 → we degrade
 *     gracefully to the empty/no-access message.
 */
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ApiError } from '@/shared/api/apiError';
import { usePermission } from '@/features/auth/usePermission';
import { PERMISSIONS } from '@/shared/types/permissions';
import { routes } from '@/shared/config/routes';
import { useAuditEvents } from '@/features/audit/hooks/useAuditEvents';
import { ActionIcon } from '@/features/audit/components/AuditEventRow';
import { actionIconKind } from '@/features/audit/components/auditActionIcon';
import type { AuditEvent } from '@/features/audit/types/auditTypes';

interface RecentActivityListProps {
  projectId: string;
  limit?: number;
}

/** Approximate "this project" filter — see note in the file header. */
function isProjectRelated(e: AuditEvent, projectId: string): boolean {
  if (e.entityType === 'PROJECT' && e.entityId === projectId) return true;
  // Heuristic: when backend records child entity events it includes the
  // project id in the metadata bag under a few common keys.
  const md = e.metadata as Record<string, unknown> | null | undefined;
  if (md && typeof md === 'object') {
    if (md.projectId === projectId || md.project_id === projectId) return true;
  }
  return false;
}

function formatRelative(iso: string, locale: string): string {
  const then = new Date(iso).getTime();
  const now = Date.now();
  const diffSec = Math.max(0, Math.round((now - then) / 1000));
  if (diffSec < 60) return `${diffSec}s`;
  const diffMin = Math.round(diffSec / 60);
  if (diffMin < 60) return `${diffMin}m`;
  const diffHr = Math.round(diffMin / 60);
  if (diffHr < 24) return `${diffHr}h`;
  // Fall back to a localised short date for older events.
  return new Date(iso).toLocaleDateString(locale);
}

export function RecentActivityList({ projectId, limit = 20 }: RecentActivityListProps) {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const { can } = usePermission();
  const hasAuditRead = can(PERMISSIONS.AUDIT_READ);

  // Pull a generous page — we filter locally so we still get 20 hits even
  // if a few unrelated events sneak in.
  const { data, isLoading, error } = useAuditEvents(
    {
      entityType: 'PROJECT',
      entityId: projectId,
      page: 0,
      size: Math.max(50, limit * 3),
    },
    { enabled: hasAuditRead },
  );

  if (!hasAuditRead) {
    return (
      <p
        className="text-sm text-text-secondary"
        data-testid="recent-activity-no-access"
      >
        {t('workflow.activity_requires_audit')}
      </p>
    );
  }

  if (isLoading) {
    return (
      <p className="text-sm text-text-secondary" data-testid="recent-activity-loading">
        {t('app.loading')}
      </p>
    );
  }

  if (error) {
    if (error instanceof ApiError && (error.status === 403 || error.status === 401)) {
      return (
        <p className="text-sm text-text-secondary" data-testid="recent-activity-no-access">
          {t('workflow.activity_requires_audit')}
        </p>
      );
    }
    return (
      <p className="text-sm text-danger-700" data-testid="recent-activity-error">
        {t('states.error_title')}
      </p>
    );
  }

  const filtered = (data?.items ?? [])
    .filter((e) => isProjectRelated(e, projectId))
    .slice(0, limit);

  if (filtered.length === 0) {
    return (
      <p className="text-sm text-text-secondary" data-testid="recent-activity-empty">
        {t('workflow.no_activity')}
      </p>
    );
  }

  const goToAudit = (entityType?: string, entityId?: string) => {
    const url = new URL(window.location.origin + routes.audit);
    url.searchParams.set('entityType', 'PROJECT');
    url.searchParams.set('entityId', projectId);
    if (entityType && entityId) {
      // Drill into the precise event row by deep-linking action code too.
      url.searchParams.set('entityType', entityType);
      url.searchParams.set('entityId', entityId);
    }
    navigate(`${url.pathname}${url.search}`);
  };

  return (
    <div className="space-y-2" data-testid="recent-activity-list">
      <ul className="space-y-2">
        {filtered.map((e) => {
          const kind = actionIconKind(e.action);
          return (
            <li key={e.id}>
              <button
                type="button"
                onClick={() => goToAudit(e.entityType ?? undefined, e.entityId ?? undefined)}
                className="w-full text-left p-2 rounded-md hover:bg-divider/40 focus:outline-none focus:ring-2 focus:ring-primary-500"
                data-testid={`recent-activity-row-${e.id}`}
              >
                <div className="flex items-start gap-2">
                  <span className="mt-0.5">
                    <ActionIcon kind={kind} />
                  </span>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm text-text-primary truncate">
                      {t(`audit.action.${e.action}`, { defaultValue: e.action })}
                    </p>
                    <p className="text-xs text-text-secondary truncate">
                      {e.actorName ?? e.actorUserId ?? t('common.unknown_actor')}
                      {' · '}
                      <time dateTime={e.createdAt} title={new Date(e.createdAt).toLocaleString(i18n.language)}>
                        {formatRelative(e.createdAt, i18n.language)}
                      </time>
                    </p>
                  </div>
                </div>
              </button>
            </li>
          );
        })}
      </ul>
      <button
        type="button"
        className="text-xs text-primary-500 hover:underline focus:outline-none focus:ring-2 focus:ring-primary-500 rounded"
        onClick={() => goToAudit()}
        data-testid="recent-activity-open-audit"
      >
        {t('workflow.open_audit_full')}
      </button>
    </div>
  );
}
