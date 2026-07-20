import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Check, ChevronDown, X } from 'lucide-react';
import { Modal } from '@/shared/components/ui/Modal';
import { cn } from '@/shared/lib/cn';
import { pickLocalized } from '@/shared/lib/localized';
import {
  effectiveLevelPoints,
  formatLevelPoints,
} from '@/features/methodology/lib/levelPoints';
import type { Factor, FactorLevel, ScoringMode } from '@/features/methodology/types';

interface LevelDropSelectProps {
  /** Active factor whose levels populate the drop. */
  factor: Factor;
  /** Currently selected level id (null = nothing chosen yet). */
  selectedLevelId: string | null;
  /**
   * Fired when the user picks a level. Receives the level id — the SAME
   * payload the previous score <select> emitted (the parent forwards it to
   * the unchanged upsert mutation). The modal closes itself before calling.
   */
  onSelect: (factorLevelId: string) => void;
  /** Disabled (locked status / no edit permission). Renders read-only. */
  disabled?: boolean;
  /**
   * Project-admin / HR-director only: show a small muted point value next
   * to each level. Derived ONCE upstream from CALIBRATION_EDIT. Plain
   * evaluators NEVER receive `true` (anchoring-bias guard).
   */
  canSeePoints?: boolean;
  /**
   * Scoring mode of the methodology version — drives WHICH field a level's
   * point badge shows (see {@link effectiveLevelPoints}): raw `points` for
   * DIRECT/WEIGHTED_POINTS vs `weight × scale_value` for WEIGHTED_SCALE
   * (where `points` is always 0). Optional: absent falls back to raw points.
   */
  scoringMode?: ScoringMode;
  /**
   * Optional position context shown as the modal title (e.g. the position
   * title from PositionScoreRow) so an evaluator knows WHICH position they are
   * scoring while the level list is open full-screen-centered.
   */
  contextTitle?: string;
  /** Stable suffix for test ids so row + bulk instances stay distinct. */
  testIdSuffix?: string;
  className?: string;
  /** aria-label for the collapsed trigger button. */
  ariaLabel?: string;
}

/**
 * One shared level-list `<ul>` — the SINGLE source of the level-list markup
 * ("кодлар 2 мартадан ёзилмасин"). Rendered inside the centered modal here AND
 * indirectly via {@link LevelDropSelect} by both PositionScoreRow (table row)
 * and BulkScoreDialog (bulk dialog). NO other component reproduces this list.
 */
function LevelOptionList({
  levels,
  selectedLevelId,
  canSeePoints,
  pointsOf,
  testIdSuffix,
  ariaLabel,
  onPick,
}: {
  levels: FactorLevel[];
  selectedLevelId: string | null;
  canSeePoints: boolean;
  /** Mode-aware point label for a level (see {@link effectiveLevelPoints}). */
  pointsOf: (lvl: FactorLevel) => string;
  testIdSuffix: string;
  ariaLabel?: string;
  onPick: (id: string) => void;
}) {
  const { t, i18n } = useTranslation();
  const suffix = testIdSuffix ? `-${testIdSuffix}` : '';

  // Description with the documented fallback chain: description_i18n in the
  // active locale → label_i18n when description is empty.
  const levelDescription = (lvl: FactorLevel): string => {
    const desc = lvl.description_i18n
      ? pickLocalized(lvl.description_i18n, i18n.language)
      : '';
    if (desc) return desc;
    return pickLocalized(lvl.label_i18n, i18n.language);
  };

  return (
    <ul
      role="listbox"
      aria-label={ariaLabel}
      data-testid={`level-drop-list${suffix}`}
      className="divide-y divide-border"
    >
      {levels.map((lvl) => {
        const selected = lvl.id === selectedLevelId;
        return (
          <li key={lvl.id}>
            <button
              type="button"
              role="option"
              aria-selected={selected}
              onClick={() => onPick(lvl.id)}
              data-testid={`level-drop-option-${lvl.code}${suffix}`}
              data-selected={selected || undefined}
              className={cn(
                // Large click target + full (non-clamped) description — the
                // modal is never clipped by the table overflow container.
                'flex w-full items-start gap-2.5 px-4 py-3 text-left text-sm transition-colors',
                selected
                  ? 'bg-success-50/70 hover:bg-success-50'
                  : 'hover:bg-divider/40',
              )}
            >
              <span
                className={cn(
                  'mt-0.5 shrink-0 rounded px-1.5 py-0.5 font-mono text-[11px] uppercase',
                  selected
                    ? 'bg-success-100 text-success-700'
                    : 'bg-primary-50 text-primary-700',
                )}
              >
                {lvl.code}
              </span>
              <span className="min-w-0 flex-1 leading-snug text-text-primary">
                {levelDescription(lvl) ||
                  t('evaluation.byFactor.rubric.no_translation')}
              </span>
              {canSeePoints ? (
                <span
                  className="mt-0.5 shrink-0 tabular-nums text-[11px] text-text-muted"
                  data-testid={`level-drop-option-points-${lvl.code}${suffix}`}
                >
                  {pointsOf(lvl)}
                </span>
              ) : null}
              {selected ? (
                <Check
                  size={16}
                  aria-hidden
                  className="mt-0.5 shrink-0 text-success-600"
                />
              ) : null}
            </button>
          </li>
        );
      })}
    </ul>
  );
}

/**
 * Level-drop selector — the K-sheet's level-by-description picker.
 *
 * Replaces the numeric score <select>. Evaluators judge a position purely
 * from the level DESCRIPTIONS (description_i18n, falling back to
 * label_i18n) — never a raw point number, which would anchor the score
 * (UX brief: "points hidden from experts"). Project admins / HR directors
 * (`canSeePoints`) additionally see a small muted point value.
 *
 * Layout (FE-10): the COLLAPSED control (selected value display / chevron)
 * stays IN the table cell. Clicking it opens the level LIST inside a CENTERED
 * MODAL overlay (the shared `<Modal>` primitive — src/shared/components/ui/Modal.tsx
 * — also used by BulkScoreDialog / ConfirmDialog) so the list is never clipped
 * by the table's `overflow-x-auto` container — the previous absolute in-cell
 * dropdown clipped for bottom rows. Picking a level closes the modal and fires
 * `onSelect`.
 *
 * ONE list component ({@link LevelOptionList}), reused by BOTH the table row
 * (PositionScoreRow, via this collapsed control) and the bulk dialog
 * (BulkScoreDialog, which embeds this component) so the level-list markup lives
 * in exactly one place ("кодлар 2 мартадан ёзилмасин").
 */
export function LevelDropSelect({
  factor,
  selectedLevelId,
  onSelect,
  disabled = false,
  canSeePoints = false,
  scoringMode,
  contextTitle,
  testIdSuffix,
  className,
  ariaLabel,
}: LevelDropSelectProps) {
  const { t, i18n } = useTranslation();
  const [open, setOpen] = useState(false);

  const suffix = testIdSuffix ? `-${testIdSuffix}` : '';

  // Mode-aware point label — raw `points` is 0 for every level of a
  // WEIGHTED_SCALE methodology (the value lives in scale_value × weight).
  const pointsOf = (lvl: FactorLevel): string =>
    formatLevelPoints(effectiveLevelPoints(scoringMode, factor, lvl));

  const sortedLevels = useMemo(
    () => [...factor.levels].sort((a, b) => a.level_order - b.level_order),
    [factor.levels],
  );

  const selectedLevel = useMemo(
    () => sortedLevels.find((l) => l.id === selectedLevelId) ?? null,
    [sortedLevels, selectedLevelId],
  );

  // Description with the documented fallback chain: description_i18n in the
  // active locale → label_i18n when description is empty.
  const levelDescription = (lvl: FactorLevel): string => {
    const desc = lvl.description_i18n
      ? pickLocalized(lvl.description_i18n, i18n.language)
      : '';
    if (desc) return desc;
    return pickLocalized(lvl.label_i18n, i18n.language);
  };

  const handlePick = (id: string) => {
    setOpen(false);
    onSelect(id);
  };

  const factorName = pickLocalized(factor.name_i18n, i18n.language) || factor.code;
  const modalTitle = contextTitle ?? factorName;

  return (
    <div
      // Base host: in-flow relative box with a sensible minimum. The width
      // ceiling is intentionally left to the caller — the K-sheet row passes
      // `w-full max-w-none` so the drop fills the dominant ДАРАЖА column.
      className={cn('relative min-w-[14rem] w-full', className)}
      data-testid={`level-drop${suffix}`}
    >
      <button
        type="button"
        disabled={disabled}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-label={ariaLabel}
        onClick={() => setOpen((v) => !v)}
        data-testid={`level-drop-trigger${suffix}`}
        data-open={open || undefined}
        className={cn(
          'flex min-h-[44px] w-full items-start gap-2 rounded-md border bg-surface px-2.5 py-1.5 text-left text-sm',
          'transition-colors hover:bg-divider/40',
          selectedLevel ? 'border-border-strong' : 'border-warning-500/50',
          disabled && 'cursor-not-allowed opacity-60 hover:bg-surface',
        )}
      >
        {selectedLevel ? (
          <>
            <span className="mt-0.5 shrink-0 rounded bg-primary-50 px-1.5 py-0.5 font-mono text-[11px] uppercase text-primary-700">
              {selectedLevel.code}
            </span>
            <span className="min-w-0 flex-1 leading-snug text-text-primary line-clamp-2">
              {levelDescription(selectedLevel) ||
                t('evaluation.byFactor.rubric.no_translation')}
            </span>
            {canSeePoints ? (
              <span
                className="mt-0.5 shrink-0 tabular-nums text-[11px] text-text-muted"
                data-testid={`level-drop-points${suffix}`}
              >
                {pointsOf(selectedLevel)}
              </span>
            ) : null}
          </>
        ) : (
          <>
            <span className="mt-0.5 shrink-0 rounded bg-warning-50 px-1.5 py-0.5 font-mono text-[11px] text-warning-700">
              —
            </span>
            <span className="min-w-0 flex-1 leading-snug text-text-muted">
              {t('evaluation.byFactor.level.level_not_selected')}
            </span>
          </>
        )}
        <ChevronDown
          size={16}
          aria-hidden
          className={cn(
            'mt-0.5 shrink-0 text-text-muted transition-transform',
            open && 'rotate-180',
          )}
        />
      </button>

      <Modal
        open={open && !disabled}
        onClose={() => setOpen(false)}
        ariaLabel={ariaLabel}
        size="lg"
        data-testid={`level-drop-modal${suffix}`}
        className="bg-surface rounded-xl shadow-lg border border-border max-h-[80vh] flex flex-col overflow-hidden"
      >
        <>
          <header className="flex items-start justify-between gap-3 px-4 py-3 border-b border-border shrink-0">
            <div className="min-w-0">
              <p className="text-xs uppercase tracking-wide text-text-muted">
                {factorName}
              </p>
              <h2
                className="text-base text-text-primary truncate"
                data-testid={`level-drop-modal-title${suffix}`}
              >
                {modalTitle}
              </h2>
            </div>
            <button
              type="button"
              onClick={() => setOpen(false)}
              aria-label={t('common.close')}
              data-testid={`level-drop-modal-close${suffix}`}
              className="shrink-0 rounded-md p-1 text-text-muted hover:bg-divider/40 hover:text-text-primary"
            >
              <X size={18} aria-hidden />
            </button>
          </header>
          <div className="overflow-y-auto">
            <LevelOptionList
              levels={sortedLevels}
              selectedLevelId={selectedLevelId}
              canSeePoints={canSeePoints}
              pointsOf={pointsOf}
              testIdSuffix={testIdSuffix ?? ''}
              ariaLabel={ariaLabel}
              onPick={handlePick}
            />
          </div>
        </>
      </Modal>
    </div>
  );
}
