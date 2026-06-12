import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { AlertTriangle, Plus, X } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { cn } from '@/shared/lib/cn';
import { pickLocalized } from '@/shared/lib/localized';
import type { Methodology } from '@/features/methodology/types';
import type { Position } from '@/features/positions/types/positionTypes';
import {
  MANDATORY_EVALUATOR_ROLES,
  type EvaluatorRole,
  type PanelEvaluatorDraft,
} from '../../panelTypes';
import { PanelRosterDraftSchema } from '../../schemas/panelSchemas';
import { PanelRoleChip } from './PanelRoleChip';
import { EvaluatorPicker } from './EvaluatorPicker';

/** Outcome of the confirm handler — supports a partial-fail result block. */
export interface OpenPanelResult {
  /** True when the panel + all evaluators were created. */
  ok: boolean;
  /** Per-evaluator failures (role + reason) surfaced inline before close. */
  failed: { role: EvaluatorRole; reason: string }[];
}

interface Props {
  open: boolean;
  positions: Position[];
  methodologies: Methodology[];
  defaultVersionId?: string | null;
  departmentNameOf: (positionId: string) => string;
  /**
   * Confirm handler. Receives the chosen position, methodology version and the
   * full roster draft (each row has a user + role). Returns a result so the
   * dialog can show partial failures inline (mirrors AddPositionsDialog).
   */
  onConfirm: (
    positionId: string,
    versionId: string,
    roster: PanelEvaluatorDraft[],
  ) => Promise<OpenPanelResult>;
  onClose: () => void;
}

function makeMandatoryRows(): PanelEvaluatorDraft[] {
  return MANDATORY_EVALUATOR_ROLES.map((role) => ({
    role,
    evaluator_user_id: null,
    evaluator_name: null,
  }));
}

/**
 * "Open evaluation panel" dialog (FE-2 / UX 6.1).
 *
 * Generalizes the AddPositionsDialog modal shell + partial-fail block. Picks a
 * position + methodology version (existing default-version logic) + the NEW
 * Evaluators section: three required rows pre-labelled HR Director / Department
 * Director / External Expert (reuse the users-access people picker) + a
 * "+ Add evaluator" button that appends ADDITIONAL rows.
 *
 * Confirm is DISABLED until the three mandatory roles are filled — a UI MIRROR
 * of the server lock-roster rule (server is the source of truth; it rejects
 * ROSTER_BELOW_FLOOR / MANDATORY_ROLES_MISSING regardless of the UI).
 */
export function OpenPanelDialog({ open, ...rest }: Props) {
  if (!open) return null;
  return <OpenPanelDialogBody {...rest} />;
}

function OpenPanelDialogBody({
  positions,
  methodologies,
  departmentNameOf,
  defaultVersionId = null,
  onConfirm,
  onClose,
}: Omit<Props, 'open'>) {
  const { t, i18n } = useTranslation();

  const activeMethodologies = useMemo(
    () => methodologies.filter((m) => m.active_version_id),
    [methodologies],
  );

  const [positionId, setPositionId] = useState('');
  const [versionId, setVersionId] = useState(() => {
    const preferred =
      defaultVersionId &&
      activeMethodologies.some((m) => m.active_version_id === defaultVersionId)
        ? defaultVersionId
        : '';
    return preferred || activeMethodologies[0]?.active_version_id || '';
  });
  const [rows, setRows] = useState<PanelEvaluatorDraft[]>(makeMandatoryRows);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<OpenPanelResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const candidatePositions = useMemo(
    () => positions.filter((p) => p.status !== 'ARCHIVED'),
    [positions],
  );

  const chosenUserIds = useMemo(
    () => rows.map((r) => r.evaluator_user_id).filter((x): x is string => !!x),
    [rows],
  );

  const setRow = (index: number, userId: string, userName: string | null) => {
    setRows((prev) =>
      prev.map((r, i) =>
        i === index ? { ...r, evaluator_user_id: userId || null, evaluator_name: userName } : r,
      ),
    );
    setResult(null);
  };

  const addExtra = () => {
    setRows((prev) => [
      ...prev,
      { role: 'ADDITIONAL', evaluator_user_id: null, evaluator_name: null },
    ]);
  };

  const removeExtra = (index: number) => {
    setRows((prev) => prev.filter((_, i) => i !== index));
  };

  // UI mirror of the server rule: 3 mandatory roles filled + total >= floor.
  const rosterValid = useMemo(
    () => PanelRosterDraftSchema.safeParse(rows).success,
    [rows],
  );

  const canSubmit =
    Boolean(positionId) && Boolean(versionId) && rosterValid && !submitting;

  const handleSubmit = async () => {
    setError(null);
    setSubmitting(true);
    try {
      const filledRows = rows.filter((r) => r.evaluator_user_id);
      const r = await onConfirm(positionId, versionId, filledRows);
      setResult(r);
      if (r.ok && r.failed.length === 0) {
        setTimeout(() => onClose(), 400);
      }
    } catch (e) {
      setError((e as Error).message ?? 'Panel creation failed');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="open-panel-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onClick={(e) => {
        if (e.target === e.currentTarget && !submitting) onClose();
      }}
    >
      <div className="bg-surface rounded-xl shadow-lg border border-border w-full max-w-lg max-h-[85vh] flex flex-col p-6 space-y-4">
        <header className="shrink-0">
          <h2 id="open-panel-title" className="text-lg text-text-primary">
            {t('panel.dialog.title')}
          </h2>
          <p className="text-sm text-text-secondary mt-1">
            {t('panel.dialog.body')}
          </p>
        </header>

        <div className="shrink-0">
          <label htmlFor="open-panel-position" className="block text-sm font-medium mb-1">
            {t('panel.dialog.position')}
          </label>
          <select
            id="open-panel-position"
            value={positionId}
            onChange={(e) => {
              setPositionId(e.target.value);
              setResult(null);
            }}
            data-testid="open-panel-position-select"
            className="w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface"
          >
            <option value="">—</option>
            {candidatePositions.map((p) => (
              <option key={p.id} value={p.id}>
                {pickLocalized(p.title_i18n, i18n.language)} ({p.code})
                {departmentNameOf(p.id) ? ` · ${departmentNameOf(p.id)}` : ''}
              </option>
            ))}
          </select>
        </div>

        <div className="shrink-0">
          <label htmlFor="open-panel-version" className="block text-sm font-medium mb-1">
            {t('panel.dialog.methodology')}
          </label>
          <select
            id="open-panel-version"
            value={versionId}
            onChange={(e) => {
              setVersionId(e.target.value);
              setResult(null);
            }}
            data-testid="open-panel-version-select"
            className="w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface"
          >
            <option value="">—</option>
            {activeMethodologies.map((m) => (
              <option key={m.id} value={m.active_version_id!}>
                {pickLocalized(m.name_i18n, i18n.language)} v{m.active_version_number}
              </option>
            ))}
          </select>
        </div>

        <div className="flex-1 min-h-0 overflow-y-auto space-y-2">
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium">{t('panel.dialog.evaluators')}</span>
            <Button
              variant="secondary"
              onClick={addExtra}
              data-testid="open-panel-add-evaluator"
              className="h-8 px-2 text-xs"
            >
              <Plus size={14} aria-hidden /> {t('panel.dialog.add_evaluator')}
            </Button>
          </div>
          <p className="text-xs text-text-muted" data-testid="open-panel-helper">
            {t('panel.dialog.min_helper')}
          </p>

          <ul className="space-y-2" data-testid="open-panel-roster">
            {rows.map((row, index) => {
              const isMandatory = MANDATORY_EVALUATOR_ROLES.includes(row.role);
              return (
                <li
                  key={`${row.role}-${index}`}
                  data-testid={`open-panel-row-${index}`}
                  className="flex items-center gap-2"
                >
                  {/* Role column gets a min-width so the longer Uzbek-Cyrillic
                      labels ("Ташқи эксперт") do not squeeze the picker (FE-8). */}
                  <span className="min-w-[9rem] shrink-0">
                    <PanelRoleChip role={row.role} />
                  </span>
                  <span className="flex-1 min-w-0">
                    <EvaluatorPicker
                      selectId={`open-panel-picker-${index}`}
                      value={row.evaluator_user_id ?? ''}
                      onChange={(userId, name) => setRow(index, userId, name)}
                      excludeUserIds={chosenUserIds.filter(
                        (id) => id !== row.evaluator_user_id,
                      )}
                    />
                  </span>
                  {!isMandatory ? (
                    <button
                      type="button"
                      onClick={() => removeExtra(index)}
                      aria-label={t('panel.dialog.remove_evaluator')}
                      data-testid={`open-panel-remove-${index}`}
                      className="text-text-muted hover:text-danger-600 p-1"
                    >
                      <X size={16} aria-hidden />
                    </button>
                  ) : (
                    <span className="w-7" aria-hidden />
                  )}
                </li>
              );
            })}
          </ul>
        </div>

        {result ? (
          <div
            role="status"
            className={cn(
              'shrink-0 rounded-md border p-3 text-sm',
              result.ok && result.failed.length === 0
                ? 'bg-success-50 border-success-500/30 text-success-700'
                : 'bg-warning-50 border-warning-500/30 text-warning-700',
            )}
            data-testid="open-panel-result"
          >
            <div className="flex items-center gap-2">
              {result.failed.length > 0 ? <AlertTriangle size={14} aria-hidden /> : null}
              <span>
                {result.ok && result.failed.length === 0
                  ? t('panel.dialog.result_ok')
                  : t('panel.dialog.result_partial', { failed: result.failed.length })}
              </span>
            </div>
            {result.failed.length > 0 ? (
              <ul className="mt-2 text-xs list-disc pl-5 space-y-0.5">
                {result.failed.map((f, i) => (
                  <li key={i}>
                    {t(`panel.role.${f.role}`)} — {f.reason}
                  </li>
                ))}
              </ul>
            ) : null}
          </div>
        ) : null}

        {error ? (
          <div
            role="alert"
            className="shrink-0 rounded-md border border-danger-500/30 bg-danger-50 text-danger-700 text-sm p-3"
            data-testid="open-panel-error"
          >
            {error}
          </div>
        ) : null}

        <div className="shrink-0 flex justify-end items-center gap-2 pt-2">
          <Button
            variant="secondary"
            onClick={onClose}
            disabled={submitting}
            data-testid="open-panel-cancel"
          >
            {t('common.cancel')}
          </Button>
          <Button
            onClick={handleSubmit}
            disabled={!canSubmit}
            data-testid="open-panel-confirm"
          >
            {t('panel.dialog.confirm')}
          </Button>
        </div>
      </div>
    </div>
  );
}
