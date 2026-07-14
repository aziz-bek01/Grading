/**
 * Shared footer for "request a job" dialogs (Report / Export request
 * dialogs, and any future request-a-job dialog): an optional inline
 * `role="alert"` error banner followed by the Cancel / Submit button row.
 *
 * Both dialogs need IDENTICAL markup here — extracted once so future
 * request-dialogs reuse it instead of re-copying the JSX (and so the dup gate
 * doesn't flag the two call sites as clones of each other).
 */
import { useTranslation } from 'react-i18next';

interface RequestDialogFooterProps {
  /** Localized error message to show, or `null` when there is nothing to report. */
  serverError: string | null;
  /** `data-testid` on the error `<p>` — feature-specific so tests stay addressable. */
  errorTestId: string;
  submitting: boolean;
  onCancel: () => void;
  /** Already-localized submit button label (varies per feature/dialog). */
  submitLabel: string;
  /** `data-testid` on the submit `<button>`. */
  submitTestId: string;
}

export function RequestDialogFooter({
  serverError,
  errorTestId,
  submitting,
  onCancel,
  submitLabel,
  submitTestId,
}: RequestDialogFooterProps) {
  const { t } = useTranslation();
  return (
    <>
      {serverError ? (
        <p
          className="text-sm text-danger-700 border border-danger-200 bg-danger-50 rounded-md px-3 py-2"
          role="alert"
          data-testid={errorTestId}
        >
          {serverError}
        </p>
      ) : null}

      <div className="flex justify-end gap-2 pt-2">
        <button
          type="button"
          onClick={onCancel}
          className="px-3 py-1.5 rounded-md border border-border text-sm"
          disabled={submitting}
        >
          {t('common.cancel')}
        </button>
        <button
          type="submit"
          disabled={submitting}
          className="px-3 py-1.5 rounded-md bg-primary-500 text-text-inverse text-sm disabled:opacity-50"
          data-testid={submitTestId}
        >
          {submitting ? t('common.loading') : submitLabel}
        </button>
      </div>
    </>
  );
}
