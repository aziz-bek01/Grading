import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { Button } from '@/shared/components/ui/Button';
import { formatDateSafe } from '@/shared/lib/dates';
import {
  useCancelImport,
  useCommitImport,
  useImport,
  useImportErrors,
} from '../hooks/useImports';
import { ImportProgressIndicator } from '../components/ImportProgressIndicator';
import { ImportSummaryCard } from '../components/ImportSummaryCard';
import { ImportErrorsTable } from '../components/ImportErrorsTable';
import { ImportTemplateBadge } from '../components/ImportTemplateBadge';

export function ImportDetailsPage() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const { projectId = '', importId = '' } = useParams<{
    projectId: string;
    importId: string;
  }>();
  const detail = useImport(importId, { pollWhileInFlight: true });
  const errors = useImportErrors(importId, { page: 0, size: 200 });
  const commit = useCommitImport(importId);
  const cancel = useCancelImport(importId);

  if (detail.isError) {
    return (
      <section className="p-6" data-testid="import-details-page">
        <ErrorState onRetry={() => detail.refetch()} />
      </section>
    );
  }
  if (detail.isLoading || !detail.data) {
    return (
      <section className="p-6" data-testid="import-details-page">
        <LoadingState />
      </section>
    );
  }
  const batch = detail.data;
  const canCommit =
    batch.status === 'READY_FOR_REVIEW' || batch.status === 'READY_TO_COMMIT';
  const canCancel = !['COMMITTED', 'CANCELLED', 'ARCHIVED'].includes(batch.status);

  return (
    <section className="p-6 space-y-4" data-testid="import-details-page">
      <header className="flex items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">{batch.originalFilename}</h1>
          <div className="flex items-center gap-2 mt-1">
            <ImportTemplateBadge code={batch.templateCode} />
            <span className="text-xs text-text-muted">
              {formatDateSafe(batch.uploadedAt, i18n.language)}
            </span>
          </div>
        </div>
        <Button
          variant="secondary"
          size="compact"
          onClick={() => navigate(`/app/projects/${projectId}/imports`)}
        >
          {t('common.back')}
        </Button>
      </header>

      <ImportProgressIndicator status={batch.status} />
      <ImportSummaryCard batch={batch} />
      <ImportErrorsTable errors={errors.data?.items ?? []} loading={errors.isLoading} />

      <div className="flex items-center justify-end gap-2">
        {canCancel ? (
          <Button
            variant="secondary"
            size="compact"
            onClick={() => cancel.mutate()}
            disabled={cancel.isPending}
            data-testid="import-details-cancel"
          >
            {t('import.wizard.cancel')}
          </Button>
        ) : null}
        {canCommit ? (
          <Button
            variant="primary"
            size="compact"
            onClick={() => commit.mutate()}
            disabled={commit.isPending}
            data-testid="import-details-commit"
          >
            {t('import.wizard.commit_rows', { count: batch.totalRowCount ?? 0 })}
          </Button>
        ) : null}
      </div>
    </section>
  );
}
