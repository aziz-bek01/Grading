import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ArrowDown, ArrowUp, Plus, Trash2 } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { StatusBadge } from '@/shared/components/status/StatusBadge';
import { LocalizedNameTabs } from '@/features/projects/components/LocalizedNameTabs';
import type { LocalizedString } from '@/shared/types/common';
import type { FactorLevel, ScoringMode } from '../types';

interface FactorLevelEditorProps {
  levels: FactorLevel[];
  scoringMode: ScoringMode;
  readOnly?: boolean;
  onAdd?: (next: Omit<FactorLevel, 'id' | 'factor_id'>) => void;
  onUpdate?: (level: FactorLevel) => void;
  onRemove?: (level: FactorLevel) => void;
  onReorder?: (level: FactorLevel, direction: 'up' | 'down') => void;
}

/**
 * Inline editor for factor levels.
 *
 * Points field visible in DIRECT_POINTS / WEIGHTED_POINTS modes.
 * Scale value visible in WEIGHTED_SCALE mode.
 *
 * Each existing level also exposes localized `label_i18n` + `description_i18n`
 * (the per-score "rubric" text the evaluator reads in the K-sheet RubricPanel).
 */
export function FactorLevelEditor({
  levels,
  scoringMode,
  readOnly,
  onAdd,
  onUpdate,
  onRemove,
  onReorder,
}: FactorLevelEditorProps) {
  const { t } = useTranslation();
  const [draft, setDraft] = useState<{
    code: string;
    points: string;
    scale_value: string;
    label: LocalizedString;
    description: LocalizedString;
  }>({ code: '', points: '0', scale_value: '0', label: {}, description: {} });

  const sorted = [...levels].sort((a, b) => a.level_order - b.level_order);
  const showPoints = scoringMode === 'DIRECT_POINTS' || scoringMode === 'WEIGHTED_POINTS';
  const showScale = scoringMode === 'WEIGHTED_SCALE';

  return (
    <section className="space-y-3" data-testid="factor-level-editor">
      <header className="text-sm font-semibold text-text-primary">
        {t('factor.levels_title')}
      </header>

      {sorted.length === 0 ? (
        <p className="text-xs text-text-secondary">{t('factor.no_levels')}</p>
      ) : (
        <ul className="space-y-2">
          {sorted.map((lvl, idx) => (
            <ExistingLevelRow
              key={lvl.id}
              level={lvl}
              index={idx}
              total={sorted.length}
              readOnly={readOnly}
              showPoints={showPoints}
              showScale={showScale}
              onUpdate={onUpdate}
              onRemove={onRemove}
              onReorder={onReorder}
            />
          ))}
        </ul>
      )}

      {!readOnly ? (
        <div className="rounded-md border border-dashed border-border-strong bg-divider/30 p-3 space-y-2" data-testid="level-new-form">
          <div className="grid grid-cols-2 gap-2">
            <label className="text-xs text-text-secondary">
              <span>{t('factor.field.code')}</span>
              <input
                type="text"
                value={draft.code}
                onChange={(e) => setDraft((d) => ({ ...d, code: e.target.value.toUpperCase() }))}
                placeholder="A"
                className="mt-1 w-full h-9 px-2 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500 font-mono"
              />
            </label>
            {showPoints ? (
              <label className="text-xs text-text-secondary">
                <span>{t('factor.field.points')}</span>
                <input
                  type="number"
                  step="any"
                  value={draft.points}
                  onChange={(e) => setDraft((d) => ({ ...d, points: e.target.value }))}
                  className="mt-1 w-full h-9 px-2 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500 tabular-nums"
                />
              </label>
            ) : null}
            {showScale ? (
              <label className="text-xs text-text-secondary">
                <span>{t('factor.field.scale_value')}</span>
                <input
                  type="number"
                  step="any"
                  value={draft.scale_value}
                  onChange={(e) => setDraft((d) => ({ ...d, scale_value: e.target.value }))}
                  className="mt-1 w-full h-9 px-2 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500 tabular-nums"
                />
              </label>
            ) : null}
          </div>
          <LocalizedNameTabs
            value={draft.label}
            onChange={(next) => setDraft((d) => ({ ...d, label: next }))}
            label={t('factor.field.label')}
          />
          <LocalizedNameTabs
            value={draft.description}
            onChange={(next) => setDraft((d) => ({ ...d, description: next }))}
            label={t('factor.field.description')}
          />
          <div className="flex justify-end">
            <Button
              variant="secondary"
              size="sm"
              leadingIcon={<Plus size={14} />}
              data-testid="level-add"
              // Enable when a code + a label in ANY supported locale is present.
              // Hard-requiring ru-RU trapped users working in a uz/en tab (they
              // filled the label in their own language, ru-RU stayed empty, the
              // button stayed disabled → click did nothing → the draft cleared
              // on close: "appears then disappears"). The backend accepts any
              // supported-locale key (no ru-RU-required), so this is safe.
              disabled={!draft.code.trim() || !Object.values(draft.label).some((v) => v?.trim())}
              onClick={() => {
                onAdd?.({
                  code: draft.code.trim(),
                  level_order: sorted.length,
                  points: Number.parseFloat(draft.points) || 0,
                  scale_value: Number.parseFloat(draft.scale_value) || 0,
                  label_i18n: draft.label,
                  description_i18n: draft.description,
                });
                setDraft({ code: '', points: '0', scale_value: '0', label: {}, description: {} });
              }}
            >
              {t('factor.add_level')}
            </Button>
          </div>
        </div>
      ) : null}
    </section>
  );
}

interface ExistingLevelRowProps {
  level: FactorLevel;
  index: number;
  total: number;
  readOnly?: boolean;
  showPoints: boolean;
  showScale: boolean;
  onUpdate?: (level: FactorLevel) => void;
  onRemove?: (level: FactorLevel) => void;
  onReorder?: (level: FactorLevel, direction: 'up' | 'down') => void;
}

/**
 * One existing-level row.
 *
 * All editable fields are kept in LOCAL state and committed to the parent
 * (which fires the update mutation) ON BLUR — never per keystroke. This is
 * mandatory: the update mutation has no optimistic cache write, so binding a
 * controlled input straight to the server value would (a) discard keystrokes
 * until each PATCH round-trips and (b) emit one PATCH + one audit event per
 * character. Local buffer + blur-commit gives smooth typing and one write per
 * field edit. The buffer re-seeds only when a DIFFERENT level fills this row
 * (keyed by id) so an in-flight refetch can never clobber what the user typed.
 */
function ExistingLevelRow({
  level,
  index,
  total,
  readOnly,
  showPoints,
  showScale,
  onUpdate,
  onRemove,
  onReorder,
}: ExistingLevelRowProps) {
  const { t } = useTranslation();
  // Buffers seed from props on mount. The parent list keys each row by
  // `level.id` (<ExistingLevelRow key={lvl.id} />), so a different level
  // occupying this slot (reorder / list change) remounts the row and re-seeds
  // these buffers — while an in-flight refetch of the SAME level never
  // clobbers what the user typed. This replaces the previous re-seed effect.
  const [code, setCode] = useState<string>(level.code);
  const [points, setPoints] = useState<string>(String(level.points));
  const [scale, setScale] = useState<string>(String(level.scale_value));
  const [label, setLabel] = useState<LocalizedString>(level.label_i18n ?? {});
  const [description, setDescription] = useState<LocalizedString>(
    level.description_i18n ?? {},
  );

  const displayLabel = label?.['ru-RU'] ?? label?.['en-US'] ?? '—';

  // Commit a code edit on blur. Uppercase + trim like the new-level form; a no-op
  // (empty or unchanged) edit is skipped so we don't fire a needless PATCH/audit.
  // The BE rejects an in-factor duplicate with 400 LEVEL_CODE_DUPLICATE; that
  // error bubbles through onUpdate's mutation (toast) and the typed value stays
  // in this buffer (no re-seed for the same level id), so nothing is lost.
  const commitCode = () => {
    const next = code.trim().toUpperCase();
    if (!next || next === level.code) return;
    onUpdate?.({ ...level, code: next });
  };

  return (
    <li
      data-testid={`level-row-${level.code}`}
      className="rounded-md border border-border bg-surface p-3 space-y-2"
    >
      <div className="flex items-center justify-between gap-2 flex-wrap">
        <div className="flex items-center gap-2 text-sm">
          <span className="text-text-secondary tabular-nums">{index + 1}.</span>
          {readOnly ? (
            <span className="font-mono text-xs text-text-secondary">{level.code}</span>
          ) : (
            <input
              type="text"
              value={code}
              onChange={(e) => setCode(e.target.value.toUpperCase())}
              onBlur={commitCode}
              aria-label={t('factor.field.code')}
              className="w-16 h-7 px-2 border border-border-strong rounded-md text-xs bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500 font-mono"
              data-testid={`level-${level.code}-code`}
            />
          )}
          <span className="text-text-primary">{displayLabel}</span>
          {level.deprecated_at ? (
            <StatusBadge
              tone="archived"
              label={t('methodology.deprecated_badge')}
              outline
              className="text-[10px]"
            />
          ) : null}
        </div>
        {!readOnly ? (
          <div className="inline-flex items-center gap-1">
            <Button
              variant="ghost"
              size="sm"
              aria-label={t('factor.action.move_up')}
              onClick={() => onReorder?.(level, 'up')}
              disabled={index === 0}
              data-testid={`level-${level.code}-move-up`}
            >
              <ArrowUp size={12} />
            </Button>
            <Button
              variant="ghost"
              size="sm"
              aria-label={t('factor.action.move_down')}
              onClick={() => onReorder?.(level, 'down')}
              disabled={index === total - 1}
              data-testid={`level-${level.code}-move-down`}
            >
              <ArrowDown size={12} />
            </Button>
            <Button
              variant="ghost"
              size="sm"
              aria-label={t('factor.action.remove')}
              onClick={() => onRemove?.(level)}
              data-testid={`level-${level.code}-remove`}
            >
              <Trash2 size={12} className="text-danger-700" />
            </Button>
          </div>
        ) : null}
      </div>
      {!readOnly ? (
        <div className="space-y-2">
          <div className="grid grid-cols-2 gap-2">
            {showPoints ? (
              <label className="text-xs text-text-secondary">
                <span>{t('factor.field.points')}</span>
                <input
                  type="number"
                  step="any"
                  value={points}
                  onChange={(e) => setPoints(e.target.value)}
                  onBlur={() =>
                    onUpdate?.({ ...level, points: Number.parseFloat(points) || 0 })
                  }
                  className="mt-1 w-full h-9 px-2 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500 tabular-nums"
                  data-testid={`level-${level.code}-points`}
                />
              </label>
            ) : null}
            {showScale ? (
              <label className="text-xs text-text-secondary">
                <span>{t('factor.field.scale_value')}</span>
                <input
                  type="number"
                  step="any"
                  value={scale}
                  onChange={(e) => setScale(e.target.value)}
                  onBlur={() =>
                    onUpdate?.({ ...level, scale_value: Number.parseFloat(scale) || 0 })
                  }
                  className="mt-1 w-full h-9 px-2 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500 tabular-nums"
                  data-testid={`level-${level.code}-scale-value`}
                />
              </label>
            ) : null}
          </div>
          {/* Localized short label. Commit on blur (focusout bubbles to this
              wrapper) so typing stays smooth and only one PATCH fires. */}
          <div onBlur={() => onUpdate?.({ ...level, label_i18n: label })}>
            <LocalizedNameTabs
              value={label}
              onChange={setLabel}
              label={t('factor.field.label')}
            />
          </div>
          {/* Localized per-score rubric description (what the evaluator reads). */}
          <div onBlur={() => onUpdate?.({ ...level, description_i18n: description })}>
            <LocalizedNameTabs
              value={description}
              onChange={setDescription}
              label={t('factor.field.description')}
            />
          </div>
        </div>
      ) : null}
    </li>
  );
}
