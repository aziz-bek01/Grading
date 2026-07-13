/**
 * Report download button — streams the generated report file through the
 * authenticated API client and triggers a client-side download.
 *
 * Hits `/reports/{id}/download`. The endpoint is JWT-authenticated and
 * STREAMS the bytes (Content-Type + Content-Disposition set server-side);
 * there is NO token in the URL, so a plain `<a href>` navigation omits the
 * Authorization (+ X-Active-Tenant-Id) header and is rejected. We pull the
 * bytes through the shared httpClient (`useDownloadReport` →
 * downloadAuthenticatedFile) and click a temporary `<a download>` with the
 * server-authoritative filename.
 *
 * Thin wrapper around the shared `SignedDownloadButton`
 * (`@/shared/components/download/SignedDownloadButton`, generalized from
 * this file and `features/export/components/SignedDownloadButton`, which
 * were a ~1:1 copy of each other) — see that file for the shared
 * markup/state machine. This wrapper supplies the report-specific download
 * call, i18n keys, and error-key mapping (no MSW-demo-mode banner — that's
 * export only).
 */
import { SignedDownloadButton as SharedSignedDownloadButton } from '@/shared/components/download/SignedDownloadButton';
import { ApiError } from '@/shared/api/apiError';
import { useDownloadReport } from '../hooks/useReports';
import type { ReportType } from '../types';

interface Props {
  reportId: string;
  /** Used for the fallback filename; server Content-Disposition still wins. */
  reportType?: ReportType;
  disabled?: boolean;
  className?: string;
}

/** Maps an httpClient error to a localized, status-aware message key. */
function errorKey(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.status === 403) return 'report.download_error_forbidden';
    if (err.status === 404) return 'report.download_error_not_found';
    if (err.status === 400) return 'report.download_error_unavailable';
  }
  return 'report.download_error';
}

export function ReportSignedDownloadButton({ reportId, reportType, disabled, className }: Props) {
  const downloader = useDownloadReport();

  return (
    <SharedSignedDownloadButton
      id={reportId}
      onDownload={(id) => downloader.mutateAsync({ id, type: reportType })}
      isDownloading={downloader.isPending}
      ctaKey="report.download_cta"
      downloadingKey="report.download_downloading"
      resolveErrorKey={errorKey}
      testId="report-signed-download-button"
      disabled={disabled}
      className={className}
    />
  );
}
