import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useParams } from 'react-router-dom';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { EmptyState } from '@/shared/components/feedback/EmptyState';
import { useApprovalRequests } from '../hooks/useApprovals';
import { ApprovalRequestCard } from '../components/ApprovalRequestCard';
import type {
  ApprovalEntityType,
  ApprovalRequestStatus,
} from '../types';

const STATUSES: ApprovalRequestStatus[] = [
  'PENDING',
  'APPROVED',
  'REJECTED',
  'CHANGES_REQUESTED',
  'CANCELLED',
];

const ENTITY_TYPES: ApprovalEntityType[] = [
  'JOB_PROFILE',
  'METHODOLOGY_VERSION',
  'EVALUATION',
  'GRADE_STRUCTURE',
  'PROJECT',
];

export function ApprovalsListPage() {
  const { t } = useTranslation();
  const { projectId } = useParams<{ projectId: string }>();
  const [status, setStatus] = useState<ApprovalRequestStatus | ''>('');
  const [entityType, setEntityType] = useState<ApprovalEntityType | ''>('');
  const list = useApprovalRequests({
    projectId,
    status: status === '' ? undefined : status,
    entityType: entityType === '' ? undefined : entityType,
  });

  if (list.isLoading) return <LoadingState />;
  if (list.error) return <ErrorState onRetry={() => list.refetch()} />;
  const items = list.data?.items ?? [];

  return (
    <div className="space-y-6" data-testid="approvals-list-page">
      <Breadcrumbs />
      <header>
        <h1 className="text-2xl text-text-primary">{t('approval.project_list_title')}</h1>
        <p className="text-sm text-text-secondary mt-1">
          {t('approval.project_list_subtitle')}
        </p>
      </header>

      <div className="flex flex-wrap gap-3 items-end">
        <label className="text-sm">
          <span className="block text-text-secondary mb-1">{t('common.status')}</span>
          <select
            value={status}
            onChange={(e) => setStatus(e.target.value as ApprovalRequestStatus | '')}
            className="border border-border-strong rounded-md px-2 py-1.5"
          >
            <option value="">{t('common.all')}</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {t(`approval.status.${s}`)}
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm">
          <span className="block text-text-secondary mb-1">{t('approval.filter_entity_type')}</span>
          <select
            value={entityType}
            onChange={(e) => setEntityType(e.target.value as ApprovalEntityType | '')}
            className="border border-border-strong rounded-md px-2 py-1.5"
          >
            <option value="">{t('common.all')}</option>
            {ENTITY_TYPES.map((et) => (
              <option key={et} value={et}>
                {t(`approval.entityType.${et}`)}
              </option>
            ))}
          </select>
        </label>
      </div>

      {items.length === 0 ? (
        <EmptyState
          title={t('approval.project_list_empty_title')}
          body={t('approval.project_list_empty_body')}
        />
      ) : (
        <ul className="grid grid-cols-1 lg:grid-cols-2 gap-3">
          {items.map((r) => (
            <li key={r.id}>
              <ApprovalRequestCard request={r} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
