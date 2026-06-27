import { useTranslation } from 'react-i18next';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { EmptyState } from '@/shared/components/feedback/EmptyState';
import { useMyApprovalInbox } from '../hooks/useApprovals';
import { ApprovalRequestCard } from '../components/ApprovalRequestCard';

export function ApprovalsInboxPage() {
  const { t } = useTranslation();
  const inbox = useMyApprovalInbox();

  if (inbox.isLoading) return <LoadingState />;
  if (inbox.error) return <ErrorState onRetry={() => inbox.refetch()} />;

  const items = inbox.data ?? [];

  return (
    <div className="space-y-6" data-testid="approvals-inbox-page">
      <Breadcrumbs />
      <header>
        <h1 className="text-2xl text-text-primary">{t('approval.inbox_title')}</h1>
        <p className="text-sm text-text-secondary mt-1">{t('approval.inbox_subtitle')}</p>
      </header>

      {items.length === 0 ? (
        <EmptyState
          title={t('approval.inbox_empty_title')}
          body={t('approval.inbox_empty_body')}
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
