import {
  memo,
  useCallback,
  useEffect,
  useId,
  useRef,
  useState,
  type ChangeEvent,
} from 'react';
import { useTranslation } from 'react-i18next';
import { Lock } from 'lucide-react';
import { cn } from '@/shared/lib/cn';
import type { Factor } from '@/features/methodology/types';
import type {
  EvaluationByFactorRow,
  EvaluationStatus,
} from '../../types';
import { EvaluationStatusBadge } from '../EvaluationStatusBadge';
import { ProgressChip } from './ProgressChip';
import { LevelDropSelect } from './LevelDropSelect';

interface PositionScoreRowProps {
  row: EvaluationByFactorRow;
  factor: Factor;
  /** Whether this row is the user-selected "active" row (for rubric sync). */
  selected: boolean;
  /** Whether this row is part of the bulk-action selection set. */
  bulkSelected: boolean;
  /** Whether the user has CALIBRATION_EDIT permission (locked-row reason). */
  canEdit: boolean;
  /**
   * Project-admin / HR-director only: surface a small muted point value next
   * to each level in the drop. Derived ONCE upstream from CALIBRATION_EDIT;
   * plain evaluators receive `false` (anchoring-bias guard).
   */
  canSeePoints: boolean;
  /**
   * Score-set callback. The parent invokes the upsert mutation and
   * handles optimistic rollback via TanStack Query.
   */
  onScoreChange: (factorLevelId: string) => Promise<void> | void;
  onCommentChange: (comment: string) => Promise<void> | void;
  onRowSelect: () => void;
  onBulkToggle: (next: boolean) => void;
}

const LOCKED_STATUSES: ReadonlySet<EvaluationStatus> = new Set([
  'SUBMITTED',
  'APPROVED',
  'LOCKED',
  'ARCHIVED',
]);

/**
 * One row in the K-sheet table — Excel cell metaphor.
 *
 * Mandatory FE engineering rules per UX brief:
 *  - Optimistic local select; rollback on mutation rejection (handled
 *    by the parent via TanStack Query — we only update local UI here
 *    and surface a transient "saving" / "saved" / "failed" state).
 *  - 300ms autosave debounce on the inline comment (Excel parity).
 *  - Locked statuses (SUBMITTED/APPROVED/LOCKED/ARCHIVED) disable both
 *    the select and the comment, and render a lock icon. This is a UX
 *    cue ONLY — the backend remains source of truth.
 *  - Memoized: parent re-renders never cascade unless `row` or
 *    `selected`/`bulkSelected`/`factor.id` actually change.
 *  - A 2px left border (success tone) flashes for 300ms on save success.
 */
function PositionScoreRowBase({
  row,
  factor,
  selected,
  bulkSelected,
  canEdit,
  canSeePoints,
  onScoreChange,
  onCommentChange,
  onRowSelect,
  onBulkToggle,
}: PositionScoreRowProps) {
  const { t } = useTranslation();
  const cbId = useId();
  const cmtId = useId();

  const locked = LOCKED_STATUSES.has(row.status);
  const disabled = locked || !canEdit;

  // Local optimistic mirror of the saved level value — flips immediately
  // on change, then resyncs from server when the row prop refreshes. The
  // resync happens during render via a previous-value ref (React's "adjust
  // state when a prop changes" pattern) rather than a setState-in-effect.
  const [localLevelId, setLocalLevelId] = useState<string | null>(
    row.current_score_factor_level_id,
  );
  // Track the last server value in state (not a ref) so we can resync the local
  // mirror during render when the row prop refreshes.
  const [seenServerLevelId, setSeenServerLevelId] = useState(
    row.current_score_factor_level_id,
  );
  if (seenServerLevelId !== row.current_score_factor_level_id) {
    setSeenServerLevelId(row.current_score_factor_level_id);
    setLocalLevelId(row.current_score_factor_level_id);
  }

  // Comment local state for debounced autosave; resynced from server the same
  // way (during render, no setState-in-effect).
  const serverComment = row.current_comment ?? '';
  const [comment, setComment] = useState<string>(serverComment);
  const [commentFocused, setCommentFocused] = useState(false);
  const [seenServerComment, setSeenServerComment] = useState(serverComment);
  if (seenServerComment !== serverComment) {
    setSeenServerComment(serverComment);
    setComment(serverComment);
  }

  // Save state: idle / saving / saved / failed. `saved` clears after
  // 300ms (matches the green left-border flash spec).
  const [saveState, setSaveState] =
    useState<'idle' | 'saving' | 'saved' | 'failed'>('idle');
  const flashTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const commentTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(
    () => () => {
      if (flashTimer.current) clearTimeout(flashTimer.current);
      if (commentTimer.current) clearTimeout(commentTimer.current);
    },
    [],
  );

  const triggerFlash = useCallback(() => {
    setSaveState('saved');
    if (flashTimer.current) clearTimeout(flashTimer.current);
    flashTimer.current = setTimeout(() => setSaveState('idle'), 600);
  }, []);

  const handleLevelPick = useCallback(
    async (lvlId: string) => {
      // Optimistic UI: flip immediately, then await the server. The payload
      // (the level id) is identical to what the old <select> sent — the API
      // contract (current_score_factor_level_id) is unchanged.
      setLocalLevelId(lvlId);
      try {
        setSaveState('saving');
        await onScoreChange(lvlId);
        triggerFlash();
      } catch {
        // Rollback on failure.
        setLocalLevelId(row.current_score_factor_level_id);
        setSaveState('failed');
      }
    },
    [onScoreChange, row.current_score_factor_level_id, triggerFlash],
  );

  const handleCommentChange = useCallback(
    (e: ChangeEvent<HTMLTextAreaElement>) => {
      const next = e.target.value;
      setComment(next);
      if (commentTimer.current) clearTimeout(commentTimer.current);
      commentTimer.current = setTimeout(async () => {
        try {
          setSaveState('saving');
          await onCommentChange(next);
          triggerFlash();
        } catch {
          setSaveState('failed');
        }
      }, 300);
    },
    [onCommentChange, triggerFlash],
  );

  // Stacked progress + status — rendered in the dedicated narrow ҲОЛАТ
  // column on lg+, and folded inline as a third muted line inside the
  // ЛАВОЗИМ cell below lg (responsive, single source of markup via this
  // local fragment so the chip/badge pair is authored once).
  const progressStatus = (
    <>
      <ProgressChip
        filled={row.filled_factors_count}
        total={row.total_factors_count}
      />
      <EvaluationStatusBadge status={row.status} />
    </>
  );

  return (
    <tr
      data-testid={`position-row-${row.position_code}`}
      data-selected={selected || undefined}
      data-locked={locked || undefined}
      onClick={() => onRowSelect()}
      className={cn(
        'border-t border-border align-top text-sm cursor-pointer',
        'hover:bg-divider/30',
        selected && 'bg-primary-50/40',
        // Save-state left border flash (2px, 600ms tail).
        'border-l-2',
        saveState === 'saved' && 'border-l-success-500',
        saveState === 'saving' && 'border-l-info-500',
        saveState === 'failed' && 'border-l-danger-500',
        saveState === 'idle' && 'border-l-transparent',
      )}
    >
      <td className="px-2 py-3 w-8 align-top" onClick={(e) => e.stopPropagation()}>
        <input
          id={cbId}
          type="checkbox"
          checked={bulkSelected}
          onChange={(e) => onBulkToggle(e.target.checked)}
          disabled={locked}
          aria-label={t('evaluation.byFactor.row.select_aria')}
          data-testid={`row-select-${row.position_code}`}
          className="h-4 w-4 accent-primary-500"
        />
      </td>
      {/* ЛАВОЗИМ (merged): title + muted code · department · sector line. */}
      <td className="px-3 py-3 align-top text-text-primary">
        <div className="flex flex-col gap-0.5">
          <span className="font-medium text-text-primary">
            {row.position_title}
          </span>
          <span className="text-xs text-text-muted">
            <span className="font-mono">{row.position_code}</span>
            {' · '}
            {row.department_name}
            {row.unit_name ? ` · ${row.unit_name}` : ''}
          </span>
          {/* Below lg: fold progress + status into the position cell. */}
          <span className="lg:hidden mt-1 flex flex-wrap items-center gap-1.5 text-text-muted">
            {progressStatus}
          </span>
        </div>
      </td>
      {/* ДАРАЖА (dominant): hosts the level drop, fills the column width. */}
      <td className="px-3 py-3 align-top" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-start gap-1.5">
          <LevelDropSelect
            factor={factor}
            selectedLevelId={localLevelId}
            onSelect={handleLevelPick}
            disabled={disabled}
            canSeePoints={canSeePoints}
            testIdSuffix={row.position_code}
            className="w-full max-w-none"
            ariaLabel={t('evaluation.byFactor.row.score_aria', {
              factor: factor.code,
            })}
          />
          {locked ? (
            <span
              title={t('evaluation.byFactor.locked.tooltip')}
              data-testid={`row-lock-${row.position_code}`}
              className="inline-flex pt-2"
              aria-label={t('evaluation.byFactor.locked.tooltip')}
            >
              <Lock
                size={12}
                aria-hidden
                className="text-text-muted shrink-0"
              />
            </span>
          ) : null}
        </div>
      </td>
      {/* ИЗОҲ: app-standard small font, taller resizable box. */}
      <td className="px-3 py-3 align-top" onClick={(e) => e.stopPropagation()}>
        <div className="flex flex-col gap-0.5">
          <textarea
            id={cmtId}
            value={comment}
            onChange={handleCommentChange}
            onFocus={() => setCommentFocused(true)}
            onBlur={() => setCommentFocused(false)}
            disabled={disabled}
            maxLength={1000}
            rows={2}
            aria-label={t('evaluation.byFactor.row.comment_aria')}
            data-testid={`row-comment-${row.position_code}`}
            placeholder={t('evaluation.matrix.comment_placeholder')}
            className={cn(
              'w-full min-h-[56px] max-h-[112px] px-2.5 py-1.5 text-xs border border-border-strong rounded-md bg-surface resize-y overflow-auto',
              disabled && 'opacity-60 cursor-not-allowed bg-divider/30',
            )}
          />
          {(commentFocused || comment.length > 0) && (
            <span
              className="text-[10px] text-text-muted self-end tabular-nums"
              data-testid={`row-comment-counter-${row.position_code}`}
            >
              {comment.length}/1000
            </span>
          )}
        </div>
      </td>
      {/* ҲОЛАТ (merged, narrow): stacked progress + status, right-aligned. */}
      <td className="px-3 py-3 align-top hidden lg:table-cell">
        <div className="flex flex-col items-end gap-1">{progressStatus}</div>
      </td>
    </tr>
  );
}

/**
 * Memoization predicate: re-render ONLY when the underlying row data,
 * the active factor, or this row's selection flags change. Bulk row
 * arrays are usually mutated reference-wise so reference equality is
 * sufficient here — callers pass primitive `bulkSelected: boolean`.
 */
export const PositionScoreRow = memo(PositionScoreRowBase, (prev, next) => {
  return (
    prev.row === next.row &&
    prev.factor === next.factor &&
    prev.selected === next.selected &&
    prev.bulkSelected === next.bulkSelected &&
    prev.canEdit === next.canEdit &&
    prev.canSeePoints === next.canSeePoints
  );
});
