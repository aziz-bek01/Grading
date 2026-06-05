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

  const handleSelectChange = useCallback(
    async (e: ChangeEvent<HTMLSelectElement>) => {
      const lvlId = e.target.value || null;
      // Optimistic UI: flip immediately, then await the server.
      setLocalLevelId(lvlId);
      if (!lvlId) return; // No-op for "—" placeholder.
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

  const sortedLevels = [...factor.levels].sort(
    (a, b) => a.level_order - b.level_order,
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
      <td className="px-2 py-2 w-8" onClick={(e) => e.stopPropagation()}>
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
      <td className="px-3 py-2 text-text-secondary">{row.department_name}</td>
      <td className="px-3 py-2 text-text-muted">{row.unit_name ?? '—'}</td>
      <td className="px-3 py-2 text-text-primary">
        <div className="flex flex-col">
          <span className="font-medium">{row.position_title}</span>
          <span className="text-xs text-text-muted font-mono">
            {row.position_code}
          </span>
        </div>
      </td>
      <td className="px-3 py-2">
        <ProgressChip
          filled={row.filled_factors_count}
          total={row.total_factors_count}
        />
      </td>
      <td className="px-3 py-2" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center gap-1.5">
          <select
            value={localLevelId ?? ''}
            onChange={handleSelectChange}
            disabled={disabled}
            aria-label={t('evaluation.byFactor.row.score_aria', {
              factor: factor.code,
            })}
            data-testid={`row-score-${row.position_code}`}
            className={cn(
              'h-8 px-2 text-sm border border-border-strong rounded-md bg-surface tabular-nums',
              disabled && 'opacity-60 cursor-not-allowed bg-divider/30',
            )}
          >
            <option value="">—</option>
            {sortedLevels.map((lvl, idx) => (
              <option key={lvl.id} value={lvl.id}>
                {idx + 1}
              </option>
            ))}
          </select>
          {locked ? (
            <span
              title={t('evaluation.byFactor.locked.tooltip')}
              data-testid={`row-lock-${row.position_code}`}
              className="inline-flex"
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
      <td className="px-3 py-2" onClick={(e) => e.stopPropagation()}>
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
              'w-full min-h-[44px] px-2 py-1 text-sm border border-border-strong rounded-md bg-surface resize-y',
              'transition-[min-height] focus:min-h-[72px]',
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
      <td className="px-3 py-2">
        <EvaluationStatusBadge status={row.status} />
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
    prev.canEdit === next.canEdit
  );
});
