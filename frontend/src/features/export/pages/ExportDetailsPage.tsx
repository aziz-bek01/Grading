import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useExport } from '../hooks/useExports';
import { ExportStatusBadge } from '../components/ExportStatusBadge';
import { ExportTypeBadge } from '../components/ExportTypeBadge';
import { ExportFormatBadge } from '../components/ExportFormatBadge';
import { SignedDownloadButton } from '../components/SignedDownloadButton';

export function ExportDetailsPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { projectId = '', exportId = '' } = useParams<{ projectId: string; exportId: string }>();
  const query = useExport(exportId, { pollWhileInFlight: true });

  if (query.isLoading || !query.data) {
    return <section className="p-6 text-sm text-text-muted">{t('states.loading')}</section>;
  }
  const job = query.data;

  return (
    <section className="p-6 space-y-4" data-testid="export-details-page">
      <header className="flex items-start justify-between gap-3">
        <div className="space-y-2">
          <h1 className="text-2xl font-semibold">{t(`export.type.${job.exportType}`)}</h1>
          <div className="flex items-center gap-2 flex-wrap">
            <ExportTypeBadge type={job.exportType} />
            <ExportFormatBadge format={job.format} />
            <ExportStatusBadge status={job.status} />
            {job.containsSalaryData ? (
              <span className="text-xs px-2 py-0.5 rounded bg-salary-sensitive-bg text-salary-sensitive border border-salary-sensitive/30">
                {t('export.contains_salary')}
              </span>
            ) : null}
          </div>
        </div>
        <button
          type="button"
          onClick={() => navigate(`/app/projects/${projectId}/exports`)}
          className="px-3 py-2 rounded-md border border-border text-sm"
        >
          {t('common.back')}
        </button>
      </header>

      <dl className="grid sm:grid-cols-2 gap-3 text-sm">
        <Field label={t('export.col.created_at')} value={new Date(job.requestedAt).toLocaleString()} />
        <Field
          label={t('export.col.generated_at')}
          value={job.generatedAt ? new Date(job.generatedAt).toLocaleString() : '—'}
        />
        <Field
          label={t('export.col.expires_at')}
          value={job.expiresAt ? new Date(job.expiresAt).toLocaleString() : '—'}
        />
        <Field
          label={t('export.col.downloaded_at')}
          value={job.downloadedAt ? new Date(job.downloadedAt).toLocaleString() : '—'}
        />
        <Field label={t('export.col.rows')} value={job.rowCount ?? '—'} />
        <Field
          label={t('export.col.size')}
          value={job.fileSize ? `${Math.round(job.fileSize / 1024)} KB` : '—'}
        />
      </dl>

      {(job.status === 'GENERATED' || job.status === 'DOWNLOADED') && (
        <SignedDownloadButton exportId={job.id} />
      )}
    </section>
  );
}

function Field({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="p-3 rounded-md border border-border bg-surface">
      <div className="text-xs text-text-muted uppercase">{label}</div>
      <div className="text-text-primary">{value}</div>
    </div>
  );
}
