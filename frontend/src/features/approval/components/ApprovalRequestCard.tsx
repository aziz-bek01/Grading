import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Card } from '@/shared/components/ui/Card';
import { pickLocalized } from '@/shared/lib/localized';
import { formatDateSafe } from '@/shared/lib/dates';
import { shortId } from '@/shared/lib/shortId';
import { routes } from '@/shared/config/routes';
import { ApprovalStatusBadge } from './ApprovalStatusBadge';
import type { ApprovalRequestSummary } from '../types';

interface Props {
  request: ApprovalRequestSummary;
}

export function ApprovalRequestCard({ request }: Props) {
  const { t, i18n } = useTranslation();
  const initiated = formatDateSafe(request.initiatedAt, i18n.language, t('common.dash'));
  const entityLabelText = request.entityLabel
    ? pickLocalized(request.entityLabel, i18n.language)
    : '';
  const hasEntityLabel = entityLabelText.length > 0;
  // When the BE-resolved label is genuinely unavailable, the PRIMARY title is a
  // neutral localized placeholder — not a raw hex fragment. The short id is kept
  // as a secondary, muted detail (below) for traceability, and the full UUID
  // stays in the link `title` attribute. Never a full 36-char UUID as a title.
  const entityLabel = hasEntityLabel ? entityLabelText : t('approval.unknown_entity');
  const entityShortId = shortId(request.entityId);
  // Prefer the BE-resolved initiator name; otherwise show a short id, not the
  // full requesting-user UUID (FE-3).
  const initiatedByDisplay = request.initiatedByName ?? shortId(request.initiatedByUserId);
  return (
    <Card compact data-testid="approval-request-card">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <Link
            to={routes.approvalDetails(request.id)}
            title={request.entityId}
            className="text-sm font-medium text-text-primary hover:underline truncate inline-block max-w-full"
          >
            {entityLabel}
          </Link>
          <div className="text-xs text-text-muted mt-0.5">
            {t(`approval.entityType.${request.entityType}`)}
            {!hasEntityLabel && entityShortId ? ` · ${entityShortId}` : ''} · {t('approval.steps_count', {
              total: request.totalSteps,
            })}
          </div>
          <div className="text-xs text-text-muted mt-0.5">
            {t('approval.initiated_by', {
              name: initiatedByDisplay,
              time: initiated,
            })}
          </div>
        </div>
        <ApprovalStatusBadge status={request.status} />
      </div>
    </Card>
  );
}
