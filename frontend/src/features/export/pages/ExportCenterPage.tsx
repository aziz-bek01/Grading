import { useState } from 'react';
import { useNavigate, useParams, Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Plus } from 'lucide-react';
import { useAuthStore } from '@/features/auth/authStore';
import { PERMISSIONS } from '@/shared/types/permissions';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { EmptyState } from '@/shared/components/feedback/EmptyState';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { formatDateSafe } from '@/shared/lib/dates';
import { useCancelExport, useExports } from '../hooks/useExports';
import { ExportStatusBadge } from '../components/ExportStatusBadge';
import { ExportTypeBadge } from '../components/ExportTypeBadge';
import { ExportFormatBadge } from '../components/ExportFormatBadge';
import { ExportRequestDialog } from '../components/ExportRequestDialog';
import { SignedDownloadButton } from '../components/SignedDownloadButton';

export function ExportCenterPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { projectId = '' } = useParams<{ projectId: string }>();
  const user = useAuthStore((s) => s.user);
  // POST /exports/request requires EXPORT_REQUEST server-side (see
  // ExportTypePermissions / RequestExportUseCase). Gating the CTA on it here
  // mirrors ReportsCenterPage's `canCreate` (REPORT_CREATE) pattern — this is
  // UX only, the backend remains the source of truth.
  const canCreate = !!user?.permissions.includes(PERMISSIONS.EXPORT_REQUEST);
  const [dialogOpen, setDialogOpen] = useState(false);
  const query = useExports({ projectId, page: 0, size: 50 });

  return (
    <section className="p-6 space-y-4" data-testid="export-center-page">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">{t('export.center.title')}</h1>
          <p className="text-sm text-text-muted">{t('export.center.subtitle')}</p>
        </div>
        {canCreate ? (
          <button
            type="button"
            onClick={() => setDialogOpen(true)}
            className="inline-flex items-center gap-2 px-3 py-2 rounded-md bg-primary-500 text-text-inverse hover:bg-primary-700 text-sm"
            data-testid="export-new-button"
          >
            <Plus size={16} /> {t('export.center.new')}
          </button>
        ) : null}
      </header>

      {query.isError ? (
        <ErrorState onRetry={() => query.refetch()} />
      ) : query.isLoading ? (
        <LoadingState />
      ) : !query.data || query.data.items.length === 0 ? (
        <EmptyState body={t('export.center.empty')} />
      ) : (
        <div className="border border-border rounded-md overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-divider text-text-secondary text-xs uppercase">
              <tr>
                <th scope="col" className="text-left px-3 py-2">{t('export.col.type')}</th>
                <th scope="col" className="text-left px-3 py-2">{t('export.col.format')}</th>
                <th scope="col" className="text-left px-3 py-2">{t('export.col.status')}</th>
                <th scope="col" className="text-left px-3 py-2">{t('export.col.created_at')}</th>
                <th scope="col" className="text-left px-3 py-2">{t('export.col.expires_at')}</th>
                <th scope="col" className="text-right px-3 py-2">{t('export.col.size')}</th>
                <th scope="col" className="text-right px-3 py-2">{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {query.data.items.map((job) => (
                <Row key={job.id} job={job} projectId={projectId} />
              ))}
            </tbody>
          </table>
        </div>
      )}

      <ExportRequestDialog
        open={dialogOpen}
        projectId={projectId}
        onClose={() => setDialogOpen(false)}
        onCreated={(j) => navigate(`/app/projects/${projectId}/exports/${j.id}`)}
      />
    </section>
  );
}

function Row({ job, projectId }: { job: import('../types').ExportJob; projectId: string }) {
  const { t, i18n } = useTranslation();
  const cancelMut = useCancelExport(job.id);
  const canCancel = ['REQUESTED', 'QUEUED', 'GENERATING'].includes(job.status);
  const canDownload = job.status === 'GENERATED' || job.status === 'DOWNLOADED';

  return (
    <tr className="border-t border-border hover:bg-divider">
      <td className="px-3 py-2">
        <Link
          to={`/app/projects/${projectId}/exports/${job.id}`}
          className="text-primary-700 hover:underline"
        >
          <ExportTypeBadge type={job.exportType} />
        </Link>
      </td>
      <td className="px-3 py-2">
        <ExportFormatBadge format={job.format} />
      </td>
      <td className="px-3 py-2">
        <ExportStatusBadge status={job.status} />
      </td>
      <td className="px-3 py-2 text-text-muted">{formatDateSafe(job.requestedAt, i18n.language)}</td>
      <td className="px-3 py-2 text-text-muted">
        {formatDateSafe(job.expiresAt, i18n.language)}
      </td>
      <td className="px-3 py-2 text-right">
        {job.fileSize ? `${Math.round(job.fileSize / 1024)} KB` : '—'}
      </td>
      <td className="px-3 py-2 text-right">
        <div className="flex items-center justify-end gap-2">
          {canDownload ? (
            <SignedDownloadButton
              exportId={job.id}
              exportType={job.exportType}
              format={job.format}
            />
          ) : null}
          {canCancel ? (
            <button
              type="button"
              onClick={() => cancelMut.mutate()}
              disabled={cancelMut.isPending}
              className="text-xs px-2 py-1 rounded border border-border hover:bg-divider"
            >
              {t('common.cancel')}
            </button>
          ) : null}
        </div>
      </td>
    </tr>
  );
}
