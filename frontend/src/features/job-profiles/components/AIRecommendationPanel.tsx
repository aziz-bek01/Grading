import { useTranslation } from 'react-i18next';
import { Sparkles } from 'lucide-react';
import { StatusBadge } from '@/shared/components/status/StatusBadge';
import { Button } from '@/shared/components/ui/Button';

interface AIRecommendationPanelProps {
  /** Optional placeholder text — MVP 1 has no live backend call (design-foundation §12). */
  placeholderBody?: string;
  /** Optional accept/reject hooks reserved for MVP 4 wiring. */
  onAccept?: () => void;
  onReject?: () => void;
  disabled?: boolean;
}

/**
 * Advisory-only AI recommendation panel.
 *
 * Visually distinct (sky/ai-suggestion tone) and always labelled
 * "AI suggestion — human approval required" so reviewers never mistake it
 * for system-approved output. Design-foundation §12 hard rule.
 */
export function AIRecommendationPanel({
  placeholderBody,
  onAccept,
  onReject,
  disabled = true,
}: AIRecommendationPanelProps) {
  const { t } = useTranslation();
  return (
    <section
      className="rounded-lg border border-ai-suggestion/30 border-l-4 border-l-ai-suggestion bg-ai-suggestion-bg p-4 space-y-3"
      data-testid="ai-recommendation-panel"
      aria-label={t('aiAssist.advisory_label')}
    >
      <header className="flex items-center justify-between gap-2 flex-wrap">
        <div className="inline-flex items-center gap-2">
          <Sparkles size={16} className="text-ai-suggestion" aria-hidden />
          <h3 className="text-sm font-semibold text-text-primary">{t('aiAssist.panel_title')}</h3>
        </div>
        <StatusBadge tone="ai-suggestion" label={t('aiAssist.advisory_label')} />
      </header>

      <p className="text-xs text-text-secondary" data-testid="ai-disclaimer">
        {t('aiAssist.disclaimer')}
      </p>

      <p className="text-sm text-text-primary" data-testid="ai-placeholder">
        {placeholderBody ?? t('aiAssist.coming_soon')}
      </p>

      <footer className="flex items-center justify-end gap-2 pt-2 border-t border-ai-suggestion/20">
        <Button variant="secondary" size="sm" onClick={onReject} disabled={disabled}>
          {t('aiAssist.reject')}
        </Button>
        <Button variant="primary" size="sm" onClick={onAccept} disabled={disabled}>
          {t('aiAssist.accept')}
        </Button>
      </footer>
    </section>
  );
}
