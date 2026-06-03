import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { CheckCircle2, ChevronLeft, ChevronRight } from 'lucide-react';
import { cn } from '@/shared/lib/cn';
import { pickLocalized } from '@/shared/lib/localized';
import type { Locale } from '@/shared/types/common';
import type { Factor } from '@/features/methodology/types';
import { BY_FACTOR_STICKY_TOP } from './stickyOffset';

interface RubricPanelProps {
  factor: Factor;
  /**
   * Currently selected level id from the actively edited row, OR null
   * if no row is selected (the panel still renders the rubric so the
   * evaluator can read the description before clicking).
   */
  selectedLevelId: string | null;
  /**
   * Click-to-score: forwarded to the parent so the active row receives
   * the level (parent owns the autosave debounce).
   */
  onSelectLevel?: (factorLevelId: string) => void;
  className?: string;
  /**
   * When true the panel is hosted inside a slide-over drawer (narrow
   * viewports). In that mode it drops the sticky positioning, the
   * collapse-to-rail behaviour and the fixed 360px width — the drawer
   * host owns visibility and sizing. The header (locale tabs) + level
   * cards markup is rendered identically; nothing is duplicated.
   */
  embedded?: boolean;
}

const LOCALE_TABS: { key: Locale; label: string }[] = [
  { key: 'ru-RU', label: 'Русский' },
  { key: 'uz-Cyrl-UZ', label: 'Ўзбек' },
  { key: 'uz-Latn-UZ', label: "O'zbek" },
  { key: 'en-US', label: 'English' },
];

const COLLAPSE_STORAGE_KEY = 'eval.byFactor.rubric.collapsed';

/**
 * Sticky right-side rubric reference panel (Excel "L-sheet" reading aid).
 *
 * Shows the active factor's name + description and the 5-6 level cards
 * in a locale-switchable tab strip. The panel does NOT own scoring
 * state — it is purely a reading aid + click-to-score shortcut.
 *
 * Collapsible: evaluators can yashir the panel to reclaim horizontal
 * space for the K-sheet table; the choice persists in localStorage so
 * it survives reloads and tab switches within the same factor view.
 *
 * The locale tab is local-only: changing it does NOT affect the global
 * i18next language. This is intentional — evaluators frequently want to
 * cross-reference the Russian rubric against the local translation
 * during a session without changing the rest of the app.
 */
export function RubricPanel({
  factor,
  selectedLevelId,
  onSelectLevel,
  className,
  embedded = false,
}: RubricPanelProps) {
  const { t, i18n } = useTranslation();
  const [locale, setLocale] = useState<Locale>(() => i18n.language as Locale);

  const [collapsed, setCollapsed] = useState<boolean>(() => {
    // Default-EXPANDED on first visit so the legend is visible out of the box.
    // Only an explicit prior preference ('1') collapses the panel. When the
    // panel is embedded in the drawer it must always show its content.
    if (embedded) return false;
    if (typeof window === 'undefined') return false;
    try {
      return window.localStorage.getItem(COLLAPSE_STORAGE_KEY) === '1';
    } catch {
      // Safari private mode etc. — fall back to expanded.
      return false;
    }
  });

  const toggleCollapsed = () => {
    setCollapsed((prev) => {
      const next = !prev;
      try {
        window.localStorage.setItem(COLLAPSE_STORAGE_KEY, next ? '1' : '0');
      } catch {
        // ignore quota / privacy errors
      }
      return next;
    });
  };

  const factorName = pickLocalized(factor.name_i18n, locale);
  const factorDesc = factor.description_i18n
    ? pickLocalized(factor.description_i18n, locale)
    : '';

  const sortedLevels = useMemo(
    () => [...factor.levels].sort((a, b) => a.level_order - b.level_order),
    [factor.levels],
  );

  // Sticky positioning lives on the panel itself so it survives independent
  // of the EvaluationByFactorView grid. The offset is the SHARED constant
  // (BY_FACTOR_STICKY_TOP = top-20 / 80px) so it can never diverge from the
  // FactorTabs strip again. It clears the 62px TopBar (h-14 + h-1.5
  // fingerprint bar) plus the page padding gap. The max-h keeps the rubric
  // body from outgrowing the viewport so its internal overflow-y-auto can
  // scroll level cards. When embedded in the drawer we drop sticky entirely
  // (the drawer host scrolls its own body).
  const stickyClasses = embedded
    ? ''
    : cn(
        'sticky',
        BY_FACTOR_STICKY_TOP,
        'max-h-[calc(100vh-6rem)] transition-[width] duration-200 ease-out',
      );

  if (collapsed && !embedded) {
    return (
      <aside
        className={cn(
          'flex flex-col bg-surface border border-border rounded-lg shadow-sm overflow-hidden',
          stickyClasses,
          'w-12 self-start',
          className,
        )}
        data-testid="rubric-panel"
        data-collapsed="true"
        aria-label={t('evaluation.byFactor.rubric.title')}
      >
        <button
          type="button"
          onClick={toggleCollapsed}
          aria-label={t('evaluation.byFactor.rubric.aria_toggle')}
          aria-expanded={false}
          data-testid="rubric-toggle"
          className="flex flex-col items-center justify-between h-full py-3 gap-3 hover:bg-divider/60 text-text-secondary"
        >
          <ChevronLeft size={16} aria-hidden />
          {/*
            Vertical label rotated -90deg so "РУБРИКА" reads bottom-to-top
            (right-side panel convention). Tracking widened for legibility.
          */}
          <span
            className="text-[11px] font-semibold tracking-[0.18em] uppercase text-text-muted whitespace-nowrap"
            style={{ writingMode: 'vertical-rl', transform: 'rotate(180deg)' }}
          >
            {t('evaluation.byFactor.rubric.vertical_label')}
          </span>
          <ChevronLeft size={16} className="opacity-0" aria-hidden />
        </button>
      </aside>
    );
  }

  return (
    <aside
      className={cn(
        'flex flex-col bg-surface overflow-hidden',
        // The drawer host already supplies a surface + border; embedded mode
        // renders edge-to-edge and full-width inside it.
        embedded
          ? 'w-full h-full'
          : 'border border-border rounded-lg shadow-sm self-start',
        stickyClasses,
        className,
      )}
      style={embedded ? undefined : { width: '360px' }}
      data-testid="rubric-panel"
      data-collapsed="false"
      data-embedded={embedded || undefined}
      aria-label={t('evaluation.byFactor.rubric.title')}
    >
      <header className="px-4 py-3 border-b border-border space-y-2">
        <div className="flex items-baseline justify-between gap-2">
          <div className="flex items-baseline gap-2 min-w-0">
            <span className="text-xs font-mono uppercase text-text-muted">
              {factor.code}
            </span>
            <h2 className="text-sm font-semibold text-text-primary truncate">
              {factorName || t('evaluation.byFactor.rubric.no_translation')}
            </h2>
          </div>
          {/* The collapse-to-rail chevron is meaningless inside the drawer
              (the drawer has its own close button), so hide it there. */}
          {embedded ? null : (
            <button
              type="button"
              onClick={toggleCollapsed}
              aria-label={t('evaluation.byFactor.rubric.aria_toggle')}
              aria-expanded={true}
              data-testid="rubric-toggle"
              title={t('evaluation.byFactor.rubric.collapse_label')}
              className="shrink-0 p-1 rounded hover:bg-divider text-text-muted hover:text-text-secondary"
            >
              <ChevronRight size={16} aria-hidden />
            </button>
          )}
        </div>
        {factorDesc ? (
          <p className="text-xs text-text-secondary leading-relaxed">
            {factorDesc}
          </p>
        ) : null}
        <div
          role="tablist"
          aria-label="Rubric language"
          className="flex gap-1 pt-1"
        >
          {LOCALE_TABS.map((lt) => {
            const active = locale === lt.key;
            return (
              <button
                key={lt.key}
                type="button"
                role="tab"
                aria-selected={active}
                onClick={() => setLocale(lt.key)}
                data-testid={`rubric-locale-${lt.key}`}
                className={cn(
                  'px-2 py-0.5 text-xs rounded-md border transition-colors',
                  active
                    ? 'border-primary-500 bg-primary-50 text-primary-700'
                    : 'border-border bg-surface text-text-secondary hover:border-border-strong',
                )}
              >
                {lt.label}
              </button>
            );
          })}
        </div>
      </header>

      {/*
        Excel "Балл | Комментарий" reading table. The score number column is
        deliberately prominent (the evaluator reads "Балл N = <meaning>" then
        picks the same N in the K-sheet <select>) and the comment column carries
        the localized label + per-score rubric description so what a 1 / 2 / 3
        actually MEANS is unambiguous. The score number is the 1-based position
        (`idx + 1`) of the level within the ascending level_order list — it ALWAYS
        starts at 1 regardless of the raw level_order base, and is computed the
        same way in the scoring <select> (PositionScoreRow) + bulk dialog, so the
        number the evaluator reads here is exactly the number they pick there.
      */}
      <div className="flex-1 overflow-y-auto" data-testid="rubric-levels">
        {/* Sticky column-header row. */}
        <div className="sticky top-0 z-10 grid grid-cols-[3.25rem_1fr] border-b border-border bg-divider/70 backdrop-blur-sm text-[11px] font-semibold uppercase tracking-wide text-text-secondary">
          <div className="px-2 py-2 text-center border-r border-border">
            {t('evaluation.byFactor.rubric.score_col')}
          </div>
          <div className="px-3 py-2">
            {t('evaluation.byFactor.rubric.comment_col')}
          </div>
        </div>

        <ul className="divide-y divide-border">
          {sortedLevels.map((lvl, idx) => {
            const selected = selectedLevelId === lvl.id;
            const label = pickLocalized(lvl.label_i18n, locale);
            const desc = lvl.description_i18n
              ? pickLocalized(lvl.description_i18n, locale)
              : '';
            const interactive = Boolean(onSelectLevel);
            return (
              <li
                key={lvl.id}
                role={interactive ? 'button' : undefined}
                tabIndex={interactive ? 0 : undefined}
                onClick={interactive ? () => onSelectLevel!(lvl.id) : undefined}
                onKeyDown={
                  interactive
                    ? (e) => {
                        if (e.key === 'Enter' || e.key === ' ') {
                          e.preventDefault();
                          onSelectLevel!(lvl.id);
                        }
                      }
                    : undefined
                }
                data-testid={`rubric-level-${lvl.code}`}
                data-selected={selected || undefined}
                className={cn(
                  'grid grid-cols-[3.25rem_1fr] transition-colors',
                  interactive && 'cursor-pointer',
                  selected ? 'bg-success-50/70' : 'bg-surface hover:bg-divider/40',
                )}
              >
                {/* Балл — prominent score number (+ code for traceability). */}
                <div
                  className={cn(
                    'flex flex-col items-center justify-center gap-0.5 py-3 border-r border-border',
                    selected ? 'bg-success-100/70' : 'bg-divider/30',
                  )}
                >
                  <span
                    className={cn(
                      'text-2xl font-bold leading-none tabular-nums',
                      selected ? 'text-success-700' : 'text-text-primary',
                    )}
                  >
                    {idx + 1}
                  </span>
                  <span className="text-[10px] font-mono uppercase text-text-muted">
                    {lvl.code}
                  </span>
                </div>

                {/* Комментарий — localized label + per-score description. */}
                <div className="min-w-0 px-3 py-2.5">
                  <div className="flex items-start justify-between gap-2">
                    <span className="text-sm font-semibold text-text-primary">
                      {label || t('evaluation.byFactor.rubric.no_translation')}
                    </span>
                    {selected ? (
                      <CheckCircle2
                        size={16}
                        aria-hidden
                        className="shrink-0 text-success-600"
                      />
                    ) : null}
                  </div>
                  {desc ? (
                    <p className="mt-1 text-xs leading-relaxed text-text-secondary">
                      {desc}
                    </p>
                  ) : (
                    <p className="mt-1 text-xs italic text-text-muted">
                      {t('evaluation.byFactor.rubric.no_translation')}
                    </p>
                  )}
                </div>
              </li>
            );
          })}
        </ul>
      </div>

      {/* Bottom collapse affordance for long rubrics (mirror of header chevron).
          Hidden inside the drawer (the drawer owns its own close button). */}
      {embedded ? null : (
        <div className="border-t border-border px-2 py-1.5 flex justify-end">
          <button
            type="button"
            onClick={toggleCollapsed}
            aria-label={t('evaluation.byFactor.rubric.aria_toggle')}
            data-testid="rubric-toggle-bottom"
            className="inline-flex items-center gap-1 text-xs text-text-muted hover:text-text-secondary px-2 py-1 rounded hover:bg-divider"
          >
            <ChevronRight size={14} aria-hidden />
            {t('evaluation.byFactor.rubric.collapse_label')}
          </button>
        </div>
      )}
    </aside>
  );
}
