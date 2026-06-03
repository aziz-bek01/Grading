import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Card } from '@/shared/components/ui/Card';
import { pickLocalized } from '@/shared/lib/localized';
import { routes } from '@/shared/config/routes';
import { ApprovalStatusBadge } from './ApprovalStatusBadge';
import type { ApprovalRequestSummary } from '../types';

interface Props {
  request: ApprovalRequestSummary;
}

export function ApprovalRequestCard({ request }: Props) {
  const { t, i18n } = useTranslation();
  const initiated = new Date(request.initiatedAt).toLocaleString(i18n.language);
  const entityLabel = request.entityLabel
    ? pickLocalized(request.entityLabel, i18n.language)
    : request.entityId;
  return (
    <Card compact data-testid="approval-request-card">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <Link
            to={routes.approvalDetails(request.id)}
            className="text-sm font-medium text-text-primary hover:underline truncate inline-block max-w-full"
          >
            {entityLabel}
          </Link>
          <div className="text-xs text-text-muted mt-0.5">
            {t(`approval.entityType.${request.entityType}`)} · {t('approval.steps_count', {
              total: request.totalSteps,
            })}
          </div>
          <div className="text-xs text-text-muted mt-0.5">
            {t('approval.initiated_by', {
              name: request.initiatedByName ?? request.initiatedByUserId,
              time: initiated,
            })}
          </div>
        </div>
        <ApprovalStatusBadge status={request.status} />
      </div>
    </Card>
  );
}
