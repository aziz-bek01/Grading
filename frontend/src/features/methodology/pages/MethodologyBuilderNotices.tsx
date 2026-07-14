import { useTranslation } from 'react-i18next';
import { ShieldAlert } from 'lucide-react';
import { InlineBanner } from '@/shared/components/ui/InlineBanner';

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
 * Extracted from `MethodologyBuilderPage` (FE-041); unchanged testids.
 * Now built on the shared `InlineBanner` primitive instead of three hand-rolled
 * notice `<div>`s (dedupe sweep) — same roles/copy/testids, no behaviour change.
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
          semantically distinct from normal DRAFT editing. */}
      {approvedEditMode ? (
        <InlineBanner
          variant="warning"
          icon={<ShieldAlert size={18} className="mt-0.5 shrink-0" aria-hidden />}
          data-testid="approved-edit-banner"
        >
          <p className="font-medium">{t('methodology.approved_edit.banner_title')}</p>
          <p className="text-warning-700/90">{t('methodology.approved_edit.banner_body')}</p>
        </InlineBanner>
      ) : null}

      {/* FE-2 — non-alarming deprecate outcome notice. */}
      {deprecateNotice ? (
        <InlineBanner
          variant="info"
          icon={null}
          onDismiss={onDismissDeprecateNotice}
          data-testid="deprecate-notice"
        >
          {deprecateNotice}
        </InlineBanner>
      ) : null}

      {templateSuccess ? (
        <InlineBanner variant="success" icon={null} data-testid="builder-template-success">
          {templateSuccess}
        </InlineBanner>
      ) : null}
    </>
  );
}
