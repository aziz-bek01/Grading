/**
 * Report-specific signed download button.
 *
 * Mirrors `features/export/components/SignedDownloadButton` 1:1 — same
 * 60-second TTL contract, same cache-then-refetch behaviour, same UX —
 * but hits `/reports/{id}/download-url` instead of `/exports/{id}/...`.
 *
 * The 60s TTL matches backend SECURITY blueprint §6 (60s max), enforced
 * by `ObjectStorageAdapter.MAX_SIGNED_URL_TTL` on the server side.
 */
import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Download, Loader2 } from 'lucide-react';
import { useReportDownloadUrl } from '../hooks/useReports';

interface Props {
  reportId: string;
  filename?: string;
  disabled?: boolean;
  className?: string;
}

const SIGNED_URL_TTL_MS = 60 * 1000;

export function ReportSignedDownloadButton({ reportId, filename, disabled, className }: Props) {
  const { t } = useTranslation();
  const fetcher = useReportDownloadUrl();
  const cached = useRef<{ url: string; fetchedAt: number } | null>(null);
  const [error, setError] = useState<string | null>(null);

  const triggerDownload = (url: string) => {
    const a = document.createElement('a');
    a.href = url;
    if (filename) a.download = filename;
    a.rel = 'noopener noreferrer';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  };

  const onClick = async () => {
    setError(null);
    const now = Date.now();
    if (cached.current && now - cached.current.fetchedAt < SIGNED_URL_TTL_MS - 5_000) {
      triggerDownload(cached.current.url);
      return;
    }
    try {
      const url = await fetcher.mutateAsync(reportId);
      cached.current = { url, fetchedAt: Date.now() };
      triggerDownload(url);
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'unknown';
      setError(msg);
    }
  };

  return (
    <div className="inline-flex flex-col items-stretch gap-1" data-testid="report-signed-download-button">
      <button
        type="button"
        onClick={onClick}
        disabled={disabled || fetcher.isPending}
        className={`inline-flex items-center gap-2 px-3 py-1.5 rounded-md bg-primary-500 text-text-inverse text-sm disabled:opacity-50 ${className ?? ''}`}
      >
        {fetcher.isPending ? (
          <>
            <Loader2 className="animate-spin" size={14} aria-hidden />
            {t('report.download_generating')}
          </>
        ) : (
          <>
            <Download size={14} aria-hidden />
            {t('report.download_cta')}
          </>
        )}
      </button>
      {error ? (
        <span className="text-xs text-danger-700" role="alert">
          {t('report.download_error')}
        </span>
      ) : null}
    </div>
  );
}
