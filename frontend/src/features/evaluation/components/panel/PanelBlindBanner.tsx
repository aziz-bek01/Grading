import { useTranslation } from 'react-i18next';
import { EyeOff } from 'lucide-react';
import { cn } from '@/shared/lib/cn';

interface Props {
  className?: string;
}

/**
 * Blind banner shown on the evaluator's own K-sheet while the panel is still
 * collecting (FE-3 / UX section 3). Reuses the ScorePreviewBanner visual
 * language (left primary rule + tinted surface) rather than authoring new modal
 * chrome.
 *
 * IMPORTANT: this banner is a UX affordance ONLY. The actual bias isolation is
 * the server-side read guard that auto-scopes the grid to the current
 * evaluator's own rows — the FE never receives other evaluators' columns. The
 * banner just tells the evaluator why they see only their own sheet.
 */
export function PanelBlindBanner({ className }: Props) {
  const { t } = useTranslation();
  return (
    <div
      data-testid="panel-blind-banner"
      className={cn(
        'rounded-lg border-l-4 border-l-primary-500 border border-border bg-primary-50/50 p-3 flex items-start gap-3',
        className,
      )}
      aria-live="polite"
    >
      <EyeOff size={18} className="text-primary-600 shrink-0 mt-0.5" aria-hidden />
      <p className="text-sm text-text-secondary">{t('panel.blind_banner')}</p>
    </div>
  );
}
