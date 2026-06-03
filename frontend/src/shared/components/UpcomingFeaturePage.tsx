/**
 * UpcomingFeaturePage — used in place of PlaceholderPage when a route is
 * intentionally locked until a future sprint / MVP phase ships.
 *
 * Why a separate component:
 *   - PlaceholderPage is a generic stub ("section under construction").
 *   - UpcomingFeaturePage tells the user WHAT will appear, WHEN, and which
 *     MVP phase owns it — which is what consultants demoing the platform
 *     actually need.
 *
 * Reuses the existing Card primitive; no new design tokens.
 */
import { useTranslation } from 'react-i18next';
import { CalendarClock, CheckCircle2, Sparkles } from 'lucide-react';
import { Card } from '@/shared/components/ui/Card';

interface UpcomingFeaturePageProps {
  titleKey: string;
  subtitleKey?: string;
  /** Bullet list of i18n keys; each one resolves to one "Что появится:" line. */
  featuresKeys: string[];
  /** Display label for the sprint window — e.g. "Sprint 12 · 09.06-23.06.2026". */
  targetSprintLabel: string;
  /** Machine-readable ISO date (YYYY-MM-DD) used for `<time datetime=…>`. */
  targetDate: string;
  /** Optional roadmap tag — e.g. "MVP 3 Phase 1". */
  mvpPhase?: string;
}

export function UpcomingFeaturePage({
  titleKey,
  subtitleKey,
  featuresKeys,
  targetSprintLabel,
  targetDate,
  mvpPhase,
}: UpcomingFeaturePageProps) {
  const { t } = useTranslation();

  return (
    <div className="space-y-6">
      <header className="flex items-start gap-4">
        <span
          className="inline-flex h-12 w-12 items-center justify-center rounded-xl bg-primary-50 text-primary-700"
          aria-hidden
        >
          <Sparkles size={22} />
        </span>
        <div>
          <h1 className="text-2xl text-text-primary">{t(titleKey)}</h1>
          {subtitleKey ? (
            <p className="text-sm text-text-secondary mt-1">{t(subtitleKey)}</p>
          ) : null}
        </div>
      </header>

      <Card>
        <div className="flex flex-wrap items-center gap-3">
          <span
            className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-primary-50 border border-primary-200 text-primary-700 text-xs font-medium"
            data-testid="upcoming-sprint-badge"
          >
            <CalendarClock size={12} aria-hidden />
            <time dateTime={targetDate}>{targetSprintLabel}</time>
          </span>
          {mvpPhase ? (
            <span className="inline-flex items-center px-2.5 py-1 rounded-full bg-divider text-text-secondary text-xs font-medium">
              {mvpPhase}
            </span>
          ) : null}
          <span className="ml-auto text-xs text-text-muted">
            {t('upcoming.sprint_label_h')}
          </span>
        </div>
      </Card>

      <Card title={t('upcoming.preview_features_title')}>
        <ul className="space-y-2.5">
          {featuresKeys.map((key) => (
            <li key={key} className="flex items-start gap-2 text-sm text-text-primary">
              <CheckCircle2
                size={16}
                aria-hidden
                className="mt-0.5 text-accent-500 shrink-0"
              />
              <span>{t(key)}</span>
            </li>
          ))}
        </ul>
      </Card>
    </div>
  );
}
