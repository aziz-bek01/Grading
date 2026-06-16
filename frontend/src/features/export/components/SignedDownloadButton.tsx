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
 */
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Download, Loader2 } from 'lucide-react';
import { ApiError } from '@/shared/api/apiError';
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
  const { t } = useTranslation();
  const downloader = useDownloadExport();
  const [errorMsgKey, setErrorMsgKey] = useState<string | null>(null);

  const onClick = async () => {
    setErrorMsgKey(null);
    try {
      await downloader.mutateAsync({ id: exportId, type: exportType, format });
    } catch (err) {
      setErrorMsgKey(errorKey(err));
    }
  };

  const isMsw =
    typeof import.meta !== 'undefined' &&
    (import.meta as unknown as { env?: Record<string, string> }).env?.VITE_USE_MSW === 'true';

  return (
    <div className="inline-flex flex-col items-stretch gap-1" data-testid="signed-download-button">
      <button
        type="button"
        onClick={onClick}
        disabled={disabled || downloader.isPending}
        className={`inline-flex items-center gap-2 px-3 py-1.5 rounded-md bg-primary-500 text-text-inverse text-sm disabled:opacity-50 ${className ?? ''}`}
      >
        {downloader.isPending ? (
          <>
            <Loader2 className="animate-spin" size={14} aria-hidden />
            {t('export.download.downloading')}
          </>
        ) : (
          <>
            <Download size={14} aria-hidden />
            {t('export.download.cta')}
          </>
        )}
      </button>
      {errorMsgKey ? (
        <span className="text-xs text-danger-700" role="alert">
          {t(errorMsgKey)}
        </span>
      ) : null}
      {isMsw && !errorMsgKey ? (
        <span className="text-[10px] text-text-muted">
          {t('export.download.demo_warning')}
        </span>
      ) : null}
    </div>
  );
}
