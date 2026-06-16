/**
 * Report download button — streams the generated report file through the
 * authenticated API client and triggers a client-side download.
 *
 * Mirrors `features/export/components/SignedDownloadButton` 1:1 but hits
 * `/reports/{id}/download`. The endpoint is JWT-authenticated and STREAMS the
 * bytes (Content-Type + Content-Disposition set server-side); there is NO
 * token in the URL, so a plain `<a href>` navigation omits the Authorization
 * (+ X-Active-Tenant-Id) header and is rejected. We pull the bytes through the
 * shared httpClient (`useDownloadReport` → downloadAuthenticatedFile) and
 * click a temporary `<a download>` with the server-authoritative filename.
 */
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Download, Loader2 } from 'lucide-react';
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

export function ReportSignedDownloadButton({
  reportId,
  reportType,
  disabled,
  className,
}: Props) {
  const { t } = useTranslation();
  const downloader = useDownloadReport();
  const [errorMsgKey, setErrorMsgKey] = useState<string | null>(null);

  const onClick = async () => {
    setErrorMsgKey(null);
    try {
      await downloader.mutateAsync({ id: reportId, type: reportType });
    } catch (err) {
      setErrorMsgKey(errorKey(err));
    }
  };

  return (
    <div
      className="inline-flex flex-col items-stretch gap-1"
      data-testid="report-signed-download-button"
    >
      <button
        type="button"
        onClick={onClick}
        disabled={disabled || downloader.isPending}
        className={`inline-flex items-center gap-2 px-3 py-1.5 rounded-md bg-primary-500 text-text-inverse text-sm disabled:opacity-50 ${className ?? ''}`}
      >
        {downloader.isPending ? (
          <>
            <Loader2 className="animate-spin" size={14} aria-hidden />
            {t('report.download_downloading')}
          </>
        ) : (
          <>
            <Download size={14} aria-hidden />
            {t('report.download_cta')}
          </>
        )}
      </button>
      {errorMsgKey ? (
        <span className="text-xs text-danger-700" role="alert">
          {t(errorMsgKey)}
        </span>
      ) : null}
    </div>
  );
}
