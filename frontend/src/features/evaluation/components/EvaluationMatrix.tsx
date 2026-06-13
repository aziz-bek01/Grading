import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { AlertTriangle } from 'lucide-react';
import { pickLocalized } from '@/shared/lib/localized';
import { cn } from '@/shared/lib/cn';
import type {
  Factor,
  FactorLevel,
  MethodologyVersion,
} from '@/features/methodology/types';
import { ScorePreviewBanner } from './ScorePreviewBanner';
import { usePreviewScore } from '../hooks/useEvaluation';
import type { EvaluationScore, EvaluationStatus } from '../types';

interface Props {
  version: MethodologyVersion;
  scores: EvaluationScore[];
  status: EvaluationStatus;
  /**
   * Called on level selection. Should call backend upsertScore; parent
   * is responsible for optimistic feedback / error handling.
   */
  onSelectLevel: (factorId: string, factorLevelId: string) => void;
  /** Called on comment blur. */
  onCommentChange?: (factorId: string, factorLevelId: string, comment: string) => void;
  /**
   * Optional initial total (e.g. from the persisted Evaluation entity)
   * shown before the user changes anything.
   */
  initialDisplayedTotal?: number | null;
  initialRawTotal?: number | null;
}

const EDITABLE: ReadonlySet<EvaluationStatus> = new Set([
  'DRAFT',
  'INCOMPLETE',
  'COMPLETE',
]);

/**
 * Render the factor × level matrix.
 *
 * Read-only modes (security-blueprint hard rule from §20 + Phase 3 verify
 * pattern): for APPROVED / LOCKED / SUBMITTED / ARCHIVED evaluations the
 * matrix renders selected levels as `<div>` markers, NOT as
 * `<input disabled>`. Disabled-input bypass via DevTools cannot mutate
 * domain state because the backend rejects writes anyway, but visually
 * marking the difference is the design-foundation §13 rule.
 *
 * Preview: every level click optimistically schedules a 300ms-debounced
 * preview-score request. Stale responses are discarded by the hook.
 */
export function EvaluationMatrix({
  version,
  scores,
  status,
  onSelectLevel,
  onCommentChange,
  initialDisplayedTotal,
  initialRawTotal,
}: Props) {
  const { t, i18n } = useTranslation();
  const editable = EDITABLE.has(status);

  // Local optimistic selection map keyed by factor_id -> factor_level_id.
  const [selections, setSelections] = useState<Map<string, string>>(() => {
    const m = new Map<string, string>();
    for (const s of scores) m.set(s.factor_id, s.factor_level_id);
    return m;
  });

  // Resync when scores prop changes (e.g. after server response).
  const lastScoresRef = useRef(scores);
  useEffect(() => {
    if (lastScoresRef.current === scores) return;
    lastScoresRef.current = scores;
    const m = new Map<string, string>();
    for (const s of scores) m.set(s.factor_id, s.factor_level_id);
    setSelections(m);
  }, [scores]);

  const preview = usePreviewScore();

  const previewKey = useMemo(
    () => Array.from(selections.entries()).map(([k, v]) => `${k}:${v}`).join('|'),
    [selections],
  );

  useEffect(() => {
    if (selections.size === 0) return;
    preview.debouncedPreview({
      methodology_version_id: version.id,
      selections: Array.from(selections.entries()).map(([factor_id, factor_level_id]) => ({
        factor_id,
        factor_level_id,
      })),
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [previewKey, version.id]);

  // Exclude soft-deprecated factors from a NEW evaluation: the backend already
  // drops them from new-evaluation lists, and the FE mirrors that so a deprecated
  // factor never offers a fresh selection. A deprecated factor that an EXISTING
  // (read-only / already-scored) evaluation still references stays visible for
  // the historical read so the committee can audit the original choice.
  const factors = useMemo(() => {
    const selectedFactorIds = new Set(scores.map((s) => s.factor_id));
    return [...version.factors]
      .filter((f) => !f.deprecated_at || !editable || selectedFactorIds.has(f.id))
      .sort((a, b) => a.sort_order - b.sort_order);
  }, [version.factors, editable, scores]);

  const missingRequired = useMemo(
    () => factors.filter((f) => f.required && !selections.has(f.id)),
    [factors, selections],
  );

  const displayedTotal =
    preview.data?.displayed_total ?? initialDisplayedTotal ?? null;
  const rawTotal = preview.data?.raw_total ?? initialRawTotal ?? null;

  const handleSelect = (f: Factor, lvl: FactorLevel) => {
    if (!editable) return;
    // Optimistic local update.
    setSelections((prev) => {
      const next = new Map(prev);
      next.set(f.id, lvl.id);
      return next;
    });
    onSelectLevel(f.id, lvl.id);
  };

  return (
    <div className="space-y-4" data-testid="evaluation-matrix">
      {missingRequired.length > 0 && editable ? (
        <div
          className="rounded-md border border-warning-500/30 bg-warning-50 px-3 py-2 text-sm text-warning-700 flex items-center gap-2"
          data-testid="missing-required-banner"
        >
          <AlertTriangle size={14} aria-hidden />
          {t('evaluation.matrix.missing_required', {
            count: missingRequired.length,
          })}
        </div>
      ) : null}

      <ScorePreviewBanner
        rawTotal={rawTotal}
        displayedTotal={displayedTotal}
        isPending={preview.isPending}
      />

      <ul
        className="space-y-3"
        role="list"
        aria-label={t('evaluation.matrix.aria_label')}
      >
        {factors.map((f) => {
          const selectedLevelId = selections.get(f.id);
          const isMissing = f.required && !selectedLevelId && editable;
          const factorRaw = preview.data?.per_factor_raw?.[f.id];
          return (
            <li
              key={f.id}
              data-testid={`factor-row-${f.code}`}
              className={cn(
                'rounded-lg border border-border bg-surface p-4 shadow-card',
                isMissing && 'border-warning-500/50',
              )}
            >
              <div className="grid grid-cols-1 lg:grid-cols-[20rem_1fr_8rem] gap-4">
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-mono text-text-muted">
                      {f.code}
                    </span>
                    {f.required ? (
                      <span
                        aria-label={t('common.required')}
                        className="text-danger-500"
                      >
                        *
                      </span>
                    ) : null}
                  </div>
                  <h3 className="text-sm font-medium text-text-primary">
                    {pickLocalized(f.name_i18n, i18n.language)}
                  </h3>
                  <p className="text-xs text-text-muted">
                    {t('evaluation.matrix.weight')}: {f.weight} ·{' '}
                    {t('evaluation.matrix.max_points')}: {f.max_points}
                  </p>
                  {isMissing ? (
                    <span
                      data-testid={`factor-required-${f.code}`}
                      className="text-xs text-warning-700 font-medium"
                    >
                      {t('evaluation.matrix.required_indicator')}
                    </span>
                  ) : null}
                </div>

                <div
                  role={editable ? 'radiogroup' : undefined}
                  aria-label={t('evaluation.matrix.levels_for', {
                    factor: pickLocalized(f.name_i18n, i18n.language),
                  })}
                  className="flex flex-wrap gap-2"
                  data-testid={`levels-${f.code}`}
                >
                  {[...f.levels]
                    // Deprecated levels drop out of a new selection but stay
                    // visible when they are the level an existing evaluation
                    // already chose (historical read).
                    .filter(
                      (lvl) =>
                        !lvl.deprecated_at || !editable || selectedLevelId === lvl.id,
                    )
                    .sort((a, b) => a.level_order - b.level_order)
                    .map((lvl) => {
                      const selected = selectedLevelId === lvl.id;
                      if (!editable) {
                        // Read-only marker — NOT a disabled input.
                        return (
                          <div
                            key={lvl.id}
                            data-testid={`level-marker-${f.code}-${lvl.code}`}
                            data-readonly="true"
                            className={cn(
                              'min-w-[7rem] px-3 py-2 rounded-md text-xs border',
                              selected
                                ? 'border-primary-500 bg-primary-50 text-primary-700 font-medium'
                                : 'border-border bg-divider/30 text-text-muted',
                            )}
                            aria-pressed={selected}
                          >
                            <div className="font-mono">{lvl.code}</div>
                            <div>{pickLocalized(lvl.label_i18n, i18n.language)}</div>
                            <div className="text-text-muted">
                              {lvl.points} pts
                            </div>
                          </div>
                        );
                      }
                      return (
                        <button
                          key={lvl.id}
                          type="button"
                          role="radio"
                          aria-checked={selected}
                          onClick={() => handleSelect(f, lvl)}
                          data-testid={`level-${f.code}-${lvl.code}`}
                          className={cn(
                            'min-w-[7rem] px-3 py-2 rounded-md text-xs border text-left transition-colors',
                            selected
                              ? 'border-primary-500 bg-primary-50 text-primary-700 font-medium ring-1 ring-primary-500/30'
                              : 'border-border-strong bg-surface text-text-primary hover:border-primary-500/50',
                          )}
                        >
                          <div className="font-mono">{lvl.code}</div>
                          <div>{pickLocalized(lvl.label_i18n, i18n.language)}</div>
                          <div className="text-text-muted">{lvl.points} pts</div>
                        </button>
                      );
                    })}
                </div>

                <div
                  className="text-right space-y-1"
                  data-testid={`factor-score-${f.code}`}
                >
                  <span className="block text-xs text-text-muted uppercase">
                    {t('evaluation.matrix.factor_raw')}
                  </span>
                  <span className="block text-sm font-semibold tabular-nums">
                    {factorRaw != null ? Number(factorRaw).toFixed(2) : '—'}
                  </span>
                </div>
              </div>

              {selectedLevelId ? (
                <details className="mt-3">
                  <summary className="text-xs text-text-secondary cursor-pointer">
                    {t('evaluation.matrix.comment_label')}
                  </summary>
                  <textarea
                    data-testid={`comment-${f.code}`}
                    defaultValue={
                      scores.find((s) => s.factor_id === f.id)?.comment_text ?? ''
                    }
                    disabled={!editable}
                    onBlur={(e) =>
                      onCommentChange?.(f.id, selectedLevelId, e.target.value)
                    }
                    maxLength={1000}
                    className="mt-2 w-full min-h-[64px] rounded-md border border-border-strong p-2 text-sm disabled:bg-divider/30"
                    placeholder={t('evaluation.matrix.comment_placeholder')}
                  />
                </details>
              ) : null}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
