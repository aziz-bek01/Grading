/**
 * Generic authenticated-file download button — streams bytes through the
 * caller-supplied `onDownload` (typically a TanStack `mutateAsync` wrapper
 * around `downloadAuthenticatedFile`, see `@/shared/api/downloadFile`) and
 * renders the idle / in-flight / error states.
 *
 * Extracted from `features/export/components/SignedDownloadButton` and
 * `features/report/components/ReportSignedDownloadButton`, which were a
 * ~1:1 copy of this markup + state machine (the report file's own comment
 * said "Mirrors features/export/components/SignedDownloadButton 1:1"). Both
 * now render this component with their own bound download call, i18n keys,
 * error-key resolver, and testid — behavior is unchanged.
 *
 * Design notes on what stayed OUT of this component (kept as caller props)
 * and why:
 *  - `resolveErrorKey(err)` instead of a shared "error key prefix": the
 *    export and report i18n trees use genuinely different key shapes for
 *    the same four cases — `export.download.error_forbidden` (nested under
 *    `download`) vs `report.download_error_forbidden` (flat), and export's
 *    generic fallback carries a `_v2` suffix (`export.download.error_v2`)
 *    that report's fallback (`report.download_error`) does not. Deriving
 *    both from one fixed prefix+suffix scheme would be fragile and could
 *    silently point at a translation key that doesn't exist. A resolver
 *    function lets each feature keep its exact, already-tested key mapping
 *    verbatim.
 *  - `onDownload(id)` instead of embedding a mutation hook: keeps this
 *    component framework-agnostic re: which resource type is downloading —
 *    callers pass a bound async function (their `useDownloadExport` /
 *    `useDownloadReport` mutation) plus the `isDownloading` flag.
 */
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Download, Loader2 } from 'lucide-react';

export interface SignedDownloadButtonProps {
  /** Resource id passed to `onDownload`. */
  id: string;
  /** Performs the download for `id`; rejects with the error to localize. */
  onDownload: (id: string) => Promise<void>;
  /** Drives the spinner + disables the button while true. */
  isDownloading: boolean;
  /** i18n key for the idle-state CTA label, e.g. `export.download.cta`. */
  ctaKey: string;
  /** i18n key for the in-flight label, e.g. `export.download.downloading`. */
  downloadingKey: string;
  /** Maps a caught error (typically an `ApiError`) to a localized message key. */
  resolveErrorKey: (err: unknown) => string;
  /** `data-testid` on the outer wrapper — each feature keeps its existing testid. */
  testId: string;
  /** Shows the MSW-demo-mode warning banner (export only). */
  showMswWarning?: boolean;
  /** i18n key for the MSW warning copy — required when `showMswWarning` is true. */
  mswWarningKey?: string;
  disabled?: boolean;
  className?: string;
}

export function SignedDownloadButton({
  id,
  onDownload,
  isDownloading,
  ctaKey,
  downloadingKey,
  resolveErrorKey,
  testId,
  showMswWarning,
  mswWarningKey,
  disabled,
  className,
}: SignedDownloadButtonProps) {
  const { t } = useTranslation();
  const [errorMsgKey, setErrorMsgKey] = useState<string | null>(null);

  const onClick = async () => {
    setErrorMsgKey(null);
    try {
      await onDownload(id);
    } catch (err) {
      setErrorMsgKey(resolveErrorKey(err));
    }
  };

  return (
    <div className="inline-flex flex-col items-stretch gap-1" data-testid={testId}>
      <button
        type="button"
        onClick={onClick}
        disabled={disabled || isDownloading}
        className={`inline-flex items-center gap-2 px-3 py-1.5 rounded-md bg-primary-500 text-text-inverse text-sm disabled:opacity-50 ${className ?? ''}`}
      >
        {isDownloading ? (
          <>
            <Loader2 className="animate-spin" size={14} aria-hidden />
            {t(downloadingKey)}
          </>
        ) : (
          <>
            <Download size={14} aria-hidden />
            {t(ctaKey)}
          </>
        )}
      </button>
      {errorMsgKey ? (
        <span className="text-xs text-danger-700" role="alert">
          {t(errorMsgKey)}
        </span>
      ) : null}
      {showMswWarning && mswWarningKey && !errorMsgKey ? (
        <span className="text-[10px] text-text-muted">{t(mswWarningKey)}</span>
      ) : null}
    </div>
  );
}
