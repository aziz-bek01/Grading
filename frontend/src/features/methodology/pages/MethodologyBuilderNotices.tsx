import { useTranslation } from 'react-i18next';
import { ShieldAlert } from 'lucide-react';

interface MethodologyBuilderNoticesProps {
  approvedEditMode: boolean;
  deprecateNotice: string | null;
  onDismissDeprecateNotice: () => void;
  templateSuccess: string | null;
}

/**
 * The builder's three transient/persistent notice banners: the
 * approved-edit-mode warning, the soft-deprecate outcome notice (FE-2, with
 * a dismiss control; also auto-dismisses via the state hook), and the
 * save-as-template success toast (auto-dismisses via the state hook).
 * Extracted from `MethodologyBuilderPage` (FE-041); unchanged behaviour and
 * testids.
 */
export function MethodologyBuilderNotices({
  approvedEditMode,
  deprecateNotice,
  onDismissDeprecateNotice,
  templateSuccess,
}: MethodologyBuilderNoticesProps) {
  const { t } = useTranslation();

  return (
    <>
      {/* FE-1 — persistent warning banner: approved-edit mode is visually and
          semantically distinct from normal DRAFT editing. Reuses the same inline
          alert pattern as the template-success / missing-required notices. */}
      {approvedEditMode ? (
        <div
          role="alert"
          className="flex items-start gap-3 rounded-md border border-warning-500/40 bg-warning-50 px-4 py-3 text-sm text-warning-700"
          data-testid="approved-edit-banner"
        >
          <ShieldAlert size={18} className="mt-0.5 shrink-0" aria-hidden />
          <div>
            <p className="font-medium">{t('methodology.approved_edit.banner_title')}</p>
            <p className="text-warning-700/90">{t('methodology.approved_edit.banner_body')}</p>
          </div>
        </div>
      ) : null}

      {/* FE-2 — non-alarming deprecate outcome notice. */}
      {deprecateNotice ? (
        <div
          role="status"
          className="flex items-start justify-between gap-3 rounded-md border border-info-500/30 bg-info-50 px-4 py-3 text-sm text-info-600"
          data-testid="deprecate-notice"
        >
          <span>{deprecateNotice}</span>
          <button
            type="button"
            className="text-info-600/70 hover:text-info-600"
            onClick={onDismissDeprecateNotice}
            aria-label={t('common.dismiss')}
          >
            ×
          </button>
        </div>
      ) : null}

      {templateSuccess ? (
        <div
          role="status"
          className="rounded-md border border-success-500/30 bg-success-50 px-4 py-3 text-sm text-success-700"
          data-testid="builder-template-success"
        >
          {templateSuccess}
        </div>
      ) : null}
    </>
  );
}
