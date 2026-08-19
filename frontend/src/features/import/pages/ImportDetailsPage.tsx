import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Archive } from 'lucide-react';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { Button } from '@/shared/components/ui/Button';
import { InlineBanner } from '@/shared/components/ui/InlineBanner';
import { formatDateSafe } from '@/shared/lib/dates';
import { describeRequestError } from '@/shared/lib/requestErrorMapper';
import {
  useArchiveImport,
  useCancelImport,
  useCommitImport,
  useImport,
  useImportErrors,
} from '../hooks/useImports';
import { ImportProgressIndicator } from '../components/ImportProgressIndicator';
import { ImportSummaryCard } from '../components/ImportSummaryCard';
import { ImportErrorsTable } from '../components/ImportErrorsTable';
import { ImportTemplateBadge } from '../components/ImportTemplateBadge';
import { canArchiveImportStatus, canCancelImportStatus, importResultDestination } from '../types';

type ImportAction = 'commit' | 'cancel' | 'archive';

/** Per-action success copy — commit already had one (with a row count). */
const SUCCESS_KEY: Record<ImportAction, string> = {
  commit: 'import.wizard.commit_success',
  cancel: 'import.details.cancel_success',
  archive: 'import.details.archive_success',
};

/** Per-action generic-failure copy fed to the shared `describeRequestError` mapper. */
const FAILURE_KEY: Record<ImportAction, string> = {
  commit: 'import.details.error.commit_failed',
  cancel: 'import.details.error.cancel_failed',
  archive: 'import.details.error.archive_failed',
};

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
  const archive = useArchiveImport(importId);

  // A single shared error/success surface for all three mutations — a
  // rejected commit/cancel/archive previously threw away the rejection with
  // NO onError handler, so the button click looked like "nothing happened"
  // (prod bug: PARTIALLY_COMMITTED cancel 409'd silently).
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);

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
  // Gates mirror the backend ImportBatchStatusTransitionPolicy exactly (see
  // ../types.ts) — cancel no longer renders for PARTIALLY_COMMITTED/COMMITTED
  // etc. (rows are already live); archive is the retention-only action for
  // those terminal states instead.
  const canCancel = canCancelImportStatus(batch.status);
  const canArchive = canArchiveImportStatus(batch.status);
  // After a commit the imported data is live but PROJECT-SCOPED (a methodology
  // import creates a DRAFT methodology under THIS project, never at the company
  // level), so surface a direct link to where it landed — the user should never
  // have to hunt for "where did my imported methodology go?".
  const isCommitted =
    batch.status === 'COMMITTED' || batch.status === 'PARTIALLY_COMMITTED';
  const resultDest = isCommitted ? importResultDestination(batch.templateCode) : null;

  function runAction(action: ImportAction, mutateAsync: () => Promise<unknown>) {
    setActionError(null);
    setActionSuccess(null);
    mutateAsync()
      .then((result) => {
        if (action === 'archive') {
          // Non-destructive terminal action — the batch drops out of the
          // default imports list, so navigate the user back to it instead of
          // leaving them staring at a batch that no longer shows up there.
          navigate(`/app/projects/${projectId}/imports`, {
            state: { flashMessageKey: SUCCESS_KEY.archive },
          });
          return;
        }
        if (action === 'commit') {
          const committed =
            (result as { totalRowCount?: number | null } | undefined)?.totalRowCount ??
            batch.totalRowCount ??
            0;
          setActionSuccess(t(SUCCESS_KEY.commit, { count: committed }));
          return;
        }
        setActionSuccess(t(SUCCESS_KEY.cancel));
      })
      .catch((err: unknown) => {
        setActionError(
          describeRequestError(err, t, {
            permissionDeniedKey: 'import.details.error.permission_denied',
            genericFailedKey: FAILURE_KEY[action],
            knownCodes: {
              IMPORT_BATCH_TRANSITION_REJECTED: 'import.details.error.transition_rejected',
            },
          }),
        );
      });
  }

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

      {actionError ? (
        <InlineBanner variant="warning" data-testid="import-details-error">
          {actionError}
        </InlineBanner>
      ) : null}
      {!actionError && actionSuccess ? (
        <InlineBanner variant="success" data-testid="import-details-success">
          {actionSuccess}
        </InlineBanner>
      ) : null}

      <div className="flex items-center justify-end gap-2">
        {resultDest ? (
          <Button
            variant="primary"
            size="compact"
            onClick={() => navigate(`/app/projects/${projectId}/${resultDest.pathSuffix}`)}
            data-testid="import-details-open-result"
          >
            {t(resultDest.labelKey)}
          </Button>
        ) : null}
        {canCancel ? (
          <Button
            variant="secondary"
            size="compact"
            onClick={() => runAction('cancel', () => cancel.mutateAsync())}
            disabled={cancel.isPending}
            data-testid="import-details-cancel"
          >
            {t('import.wizard.cancel')}
          </Button>
        ) : null}
        {canArchive ? (
          <Button
            variant="secondary"
            size="compact"
            leadingIcon={<Archive size={14} aria-hidden />}
            onClick={() => runAction('archive', () => archive.mutateAsync())}
            disabled={archive.isPending}
            data-testid="import-details-archive"
          >
            {t('import.details.archive')}
          </Button>
        ) : null}
        {canCommit ? (
          <Button
            variant="primary"
            size="compact"
            onClick={() => runAction('commit', () => commit.mutateAsync())}
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
