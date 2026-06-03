/**
 * SignedDownloadButton — clicks call `GET /exports/{id}/download-url`,
 * receive a fresh 60-second signed URL, then trigger a browser download
 * via a temporary <a download>. Tracks expiry and re-fetches the URL on
 * the next click after it lapses.
 *
 * The 60s TTL matches backend SECURITY blueprint §6 (60s max) — tightened
 * from the original 5-minute window per MVP 2 Phase 2 integration review
 * finding F1. Keep this constant in lock-step with
 * `ObjectStorageAdapter.MAX_SIGNED_URL_TTL` on the backend.
 */
import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Download, Loader2 } from 'lucide-react';
import { useFetchDownloadUrl } from '../hooks/useExports';

interface Props {
  exportId: string;
  /** Suggested filename; backend signed URL may override via content-disposition. */
  filename?: string;
  disabled?: boolean;
  className?: string;
}

// Matches backend SECURITY blueprint §6 (60s max signed URL TTL).
// Kept in lock-step with ObjectStorageAdapter.MAX_SIGNED_URL_TTL.
const SIGNED_URL_TTL_MS = 60 * 1000;

export function SignedDownloadButton({ exportId, filename, disabled, className }: Props) {
  const { t } = useTranslation();
  const fetcher = useFetchDownloadUrl();
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
    // Reuse cached URL only if still within the 60s TTL (with a 5s safety margin).
    const now = Date.now();
    if (cached.current && now - cached.current.fetchedAt < SIGNED_URL_TTL_MS - 5_000) {
      triggerDownload(cached.current.url);
      return;
    }
    try {
      const url = await fetcher.mutateAsync(exportId);
      cached.current = { url, fetchedAt: Date.now() };
      triggerDownload(url);
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'unknown';
      setError(msg);
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
        disabled={disabled || fetcher.isPending}
        className={`inline-flex items-center gap-2 px-3 py-1.5 rounded-md bg-primary-500 text-text-inverse text-sm disabled:opacity-50 ${className ?? ''}`}
      >
        {fetcher.isPending ? (
          <>
            <Loader2 className="animate-spin" size={14} aria-hidden />
            {t('export.download.generating_url')}
          </>
        ) : (
          <>
            <Download size={14} aria-hidden />
            {t('export.download.cta')}
          </>
        )}
      </button>
      {error ? (
        <span className="text-xs text-danger-700" role="alert">
          {t('export.download.error_v2')}
        </span>
      ) : null}
      {isMsw && !error ? (
        <span className="text-[10px] text-text-muted">
          {t('export.download.demo_warning')}
        </span>
      ) : null}
    </div>
  );
}
