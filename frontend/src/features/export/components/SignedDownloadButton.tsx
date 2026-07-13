/**
 * SignedDownloadButton — streams the generated export file through the
 * authenticated API client and triggers a client-side download.
 *
 * The backend `GET /exports/{id}/download` endpoint is JWT-authenticated and
 * STREAMS the bytes (Content-Type + Content-Disposition set server-side).
 * There is NO token in the URL, so a plain `<a href>` navigation omits the
 * Authorization (+ X-Active-Tenant-Id) header and the server rejects it. We
 * therefore pull the bytes through the shared httpClient (`useDownloadExport`
 * → downloadAuthenticatedFile), build an object URL, and click a temporary
 * `<a download>` with the server-authoritative filename.
 *
 * Thin wrapper around the shared `SignedDownloadButton`
 * (`@/shared/components/download/SignedDownloadButton`) — see that file for
 * the shared markup/state machine. This wrapper supplies the export-specific
 * download call, i18n keys, error-key mapping, and the MSW-demo-mode
 * warning banner (export only).
 */
import { SignedDownloadButton as SharedSignedDownloadButton } from '@/shared/components/download/SignedDownloadButton';
import { ApiError } from '@/shared/api/apiError';
import { env } from '@/shared/config/env';
import { useDownloadExport } from '../hooks/useExports';
import type { ExportFormat, ExportType } from '../types';

interface Props {
  exportId: string;
  /** Used for the fallback filename; server Content-Disposition still wins. */
  exportType?: ExportType;
  format?: ExportFormat;
  disabled?: boolean;
  className?: string;
}

/** Maps an httpClient error to a localized, status-aware message key. */
function errorKey(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.status === 403) return 'export.download.error_forbidden';
    if (err.status === 404) return 'export.download.error_not_found';
    if (err.status === 400) return 'export.download.error_unavailable';
  }
  return 'export.download.error_v2';
}

export function SignedDownloadButton({
  exportId,
  exportType,
  format,
  disabled,
  className,
}: Props) {
  const downloader = useDownloadExport();

  return (
    <SharedSignedDownloadButton
      id={exportId}
      onDownload={(id) => downloader.mutateAsync({ id, type: exportType, format })}
      isDownloading={downloader.isPending}
      ctaKey="export.download.cta"
      downloadingKey="export.download.downloading"
      resolveErrorKey={errorKey}
      testId="signed-download-button"
      showMswWarning={env.useMockApi}
      mswWarningKey="export.download.demo_warning"
      disabled={disabled}
      className={className}
    />
  );
}
