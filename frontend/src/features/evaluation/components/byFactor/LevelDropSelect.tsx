import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Check, ChevronDown } from 'lucide-react';
import { cn } from '@/shared/lib/cn';
import { pickLocalized } from '@/shared/lib/localized';
import type { Factor, FactorLevel } from '@/features/methodology/types';

interface LevelDropSelectProps {
  /** Active factor whose levels populate the drop. */
  factor: Factor;
  /** Currently selected level id (null = nothing chosen yet). */
  selectedLevelId: string | null;
  /**
   * Fired when the user picks a level. Receives the level id — the SAME
   * payload the previous score <select> emitted (the parent forwards it to
   * the unchanged upsert mutation). The drop closes itself before calling.
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
  /** Stable suffix for test ids so row + bulk instances stay distinct. */
  testIdSuffix?: string;
  className?: string;
  /** aria-label for the collapsed trigger button. */
  ariaLabel?: string;
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
 * Layout (in-flow, NOT a modal — expands inside the host cell/container):
 *   Collapsed: [level code badge] + description + chevron.
 *              When nothing is chosen: warning "—" badge + "not selected".
 *   Expanded:  one row per level = code badge + (multi-line) description,
 *              selected one tinted teal/success + check icon. Picking a
 *              level closes the drop and fires `onSelect`.
 *
 * ONE component, reused by BOTH the table row (PositionScoreRow) and the
 * bulk dialog (BulkScoreDialog) so the level-list markup lives in exactly
 * one place ("кодлар 2 мартадан ёзилмасин").
 */
export function LevelDropSelect({
  factor,
  selectedLevelId,
  onSelect,
  disabled = false,
  canSeePoints = false,
  testIdSuffix,
  className,
  ariaLabel,
}: LevelDropSelectProps) {
  const { t, i18n } = useTranslation();
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

  const suffix = testIdSuffix ? `-${testIdSuffix}` : '';

  const sortedLevels = useMemo(
    () => [...factor.levels].sort((a, b) => a.level_order - b.level_order),
    [factor.levels],
  );

  const selectedLevel = useMemo(
    () => sortedLevels.find((l) => l.id === selectedLevelId) ?? null,
    [sortedLevels, selectedLevelId],
  );

  // Outside-click + Escape close the in-flow drop. Only bound while open.
  useEffect(() => {
    if (!open) return;
    const onDocClick = (e: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onDocClick);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDocClick);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

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

  return (
    <div
      ref={rootRef}
      className={cn('relative min-w-[14rem] max-w-md', className)}
      data-testid={`level-drop${suffix}`}
    >
      <button
        type="button"
        disabled={disabled}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-label={ariaLabel}
        onClick={() => setOpen((v) => !v)}
        data-testid={`level-drop-trigger${suffix}`}
        data-open={open || undefined}
        className={cn(
          'flex w-full items-start gap-2 rounded-md border bg-surface px-2.5 py-1.5 text-left text-sm',
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
                {selectedLevel.points}
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

      {open && !disabled ? (
        <div
          role="listbox"
          aria-label={ariaLabel}
          data-testid={`level-drop-list${suffix}`}
          className={cn(
            'absolute z-30 mt-1 w-full overflow-hidden rounded-md border border-border bg-surface shadow-lg',
            'max-h-72 overflow-y-auto',
          )}
        >
          <ul className="divide-y divide-border">
            {sortedLevels.map((lvl) => {
              const selected = lvl.id === selectedLevelId;
              return (
                <li key={lvl.id}>
                  <button
                    type="button"
                    role="option"
                    aria-selected={selected}
                    onClick={() => handlePick(lvl.id)}
                    data-testid={`level-drop-option-${lvl.code}${suffix}`}
                    data-selected={selected || undefined}
                    className={cn(
                      'flex w-full items-start gap-2 px-2.5 py-2 text-left text-sm transition-colors',
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
                        {lvl.points}
                      </span>
                    ) : null}
                    {selected ? (
                      <Check
                        size={15}
                        aria-hidden
                        className="mt-0.5 shrink-0 text-success-600"
                      />
                    ) : null}
                  </button>
                </li>
              );
            })}
          </ul>
        </div>
      ) : null}
    </div>
  );
}
