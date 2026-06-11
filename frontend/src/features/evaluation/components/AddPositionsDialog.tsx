import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { AlertTriangle, Search } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { cn } from '@/shared/lib/cn';
import { pickLocalized } from '@/shared/lib/localized';
import type { Methodology } from '@/features/methodology/types';
import type { Position } from '@/features/positions/types/positionTypes';
import type { BulkCreateEvaluationResult } from '../types';

interface AddPositionsDialogProps {
  open: boolean;
  /** All project positions (candidates before the per-version diff). */
  positions: Position[];
  /** Methodologies that own an active version (the only valid targets). */
  methodologies: Methodology[];
  /**
   * Set of `${position_id}|${methodology_version_id}` keys that ALREADY have a
   * non-archived evaluation. Candidates matching the selected version are
   * filtered out (FE-2 candidate diff).
   */
  existingKeys: Set<string>;
  /** position_id -> localized department name (FE-1 map, reused here). */
  departmentNameOf: (positionId: string) => string;
  /**
   * Confirm handler — returns the backend bulk-create result so the dialog can
   * show partial failures inline before closing (mirrors BulkScoreDialog).
   */
  onConfirm: (
    versionId: string,
    positionIds: string[],
  ) => Promise<BulkCreateEvaluationResult>;
  onClose: () => void;
}

/**
 * "Add positions" multi-select dialog (Item 1, FE-2).
 *
 * Reuses the BulkScoreDialog modal STRUCTURE (header + body + footer in the
 * same `fixed inset-0 z-50` centered overlay) — no new modal chrome is authored.
 * Top control: methodology-version select (active versions only). Body: a
 * checkbox list of CANDIDATE positions = project positions MINUS positions that
 * already have a non-archived evaluation for the selected version. Each row
 * shows title + code + department (FE-1 map). Multi-select with select-all +
 * search. Partial-fail summary uses the SAME success-50 / warning-50 result
 * block pattern as BulkScoreDialog.
 */
export function AddPositionsDialog({ open, ...rest }: AddPositionsDialogProps) {
  // Mount fresh while open so each session starts clean (no stale selection /
  // result), matching the BulkScoreDialog lifecycle.
  if (!open) return null;
  return <AddPositionsDialogBody {...rest} />;
}

function AddPositionsDialogBody({
  positions,
  methodologies,
  existingKeys,
  departmentNameOf,
  onConfirm,
  onClose,
}: Omit<AddPositionsDialogProps, 'open'>) {
  const { t, i18n } = useTranslation();

  const activeMethodologies = useMemo(
    () => methodologies.filter((m) => m.active_version_id),
    [methodologies],
  );

  const [versionId, setVersionId] = useState(
    activeMethodologies[0]?.active_version_id ?? '',
  );
  const [search, setSearch] = useState('');
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<BulkCreateEvaluationResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Candidate diff: project positions MINUS those already evaluated for the
  // selected version. Done by (position_id|version_id) key against existingKeys.
  const candidates = useMemo(() => {
    if (!versionId) return [];
    const q = search.trim().toLowerCase();
    return positions
      .filter((p) => p.status !== 'ARCHIVED')
      .filter((p) => !existingKeys.has(`${p.id}|${versionId}`))
      .filter((p) => {
        if (!q) return true;
        const title = pickLocalized(p.title_i18n, i18n.language).toLowerCase();
        return (
          title.includes(q) ||
          p.code.toLowerCase().includes(q) ||
          departmentNameOf(p.id).toLowerCase().includes(q)
        );
      });
  }, [positions, versionId, existingKeys, search, i18n.language, departmentNameOf]);

  const allSelected =
    candidates.length > 0 && candidates.every((p) => selected.has(p.id));

  const toggleAll = () => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (allSelected) {
        for (const p of candidates) next.delete(p.id);
      } else {
        for (const p of candidates) next.add(p.id);
      }
      return next;
    });
  };

  const toggleRow = (id: string, on: boolean) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (on) next.add(id);
      else next.delete(id);
      return next;
    });
  };

  const canSubmit = Boolean(versionId) && selected.size > 0 && !submitting;

  const handleSubmit = async () => {
    setError(null);
    setSubmitting(true);
    try {
      const r = await onConfirm(versionId, Array.from(selected));
      setResult(r);
      if (r.failed.length === 0) {
        setTimeout(() => onClose(), 400);
      } else {
        // Keep open so the user sees per-row failures; drop the rows that
        // succeeded from the selection so a retry only re-attempts failures.
        const failedIds = new Set(r.failed.map((f) => f.position_id));
        setSelected((prev) => {
          const next = new Set<string>();
          for (const id of prev) if (failedIds.has(id)) next.add(id);
          return next;
        });
      }
    } catch (e) {
      setError((e as Error).message ?? 'Bulk create failed');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="add-positions-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onClick={(e) => {
        if (e.target === e.currentTarget && !submitting) onClose();
      }}
    >
      <div className="bg-surface rounded-xl shadow-lg border border-border w-full max-w-lg max-h-[85vh] flex flex-col p-6 space-y-4">
        <header className="shrink-0">
          <h2 id="add-positions-title" className="text-lg text-text-primary">
            {t('evaluation.add_positions.title')}
          </h2>
          <p className="text-sm text-text-secondary mt-1">
            {t('evaluation.add_positions.body')}
          </p>
        </header>

        <div className="shrink-0">
          <label
            htmlFor="add-positions-version"
            className="block text-sm font-medium mb-1"
          >
            {t('evaluation.add_positions.methodology')}
          </label>
          <select
            id="add-positions-version"
            value={versionId}
            onChange={(e) => {
              setVersionId(e.target.value);
              setSelected(new Set());
              setResult(null);
            }}
            data-testid="add-positions-version-select"
            className="w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface"
          >
            <option value="">—</option>
            {activeMethodologies.map((m) => (
              <option key={m.id} value={m.active_version_id!}>
                {pickLocalized(m.name_i18n, i18n.language)} v
                {m.active_version_number}
              </option>
            ))}
          </select>
        </div>

        <div className="shrink-0 flex items-center gap-2">
          <label className="relative flex-1">
            <span className="sr-only">{t('common.search')}</span>
            <Search
              size={14}
              aria-hidden
              className="absolute left-2 top-1/2 -translate-y-1/2 text-text-muted"
            />
            <input
              type="search"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder={t('common.search')}
              data-testid="add-positions-search"
              className="w-full h-9 pl-7 pr-3 border border-border-strong rounded-md text-sm bg-surface"
            />
          </label>
          <label className="inline-flex items-center gap-1.5 text-sm text-text-secondary whitespace-nowrap">
            <input
              type="checkbox"
              checked={allSelected}
              onChange={toggleAll}
              disabled={candidates.length === 0}
              data-testid="add-positions-select-all"
              className="h-4 w-4 accent-primary-500"
            />
            {t('evaluation.add_positions.select_all')}
          </label>
        </div>

        <div
          className="flex-1 min-h-0 overflow-y-auto border border-border rounded-md divide-y divide-border"
          data-testid="add-positions-list"
        >
          {candidates.length === 0 ? (
            <p className="p-4 text-sm text-text-muted text-center">
              {t('evaluation.add_positions.empty')}
            </p>
          ) : (
            candidates.map((p) => {
              const checked = selected.has(p.id);
              return (
                <label
                  key={p.id}
                  data-testid={`add-positions-row-${p.code}`}
                  className={cn(
                    'flex items-start gap-2.5 px-3 py-2.5 text-sm cursor-pointer transition-colors',
                    checked ? 'bg-primary-50/40' : 'hover:bg-divider/40',
                  )}
                >
                  <input
                    type="checkbox"
                    checked={checked}
                    onChange={(e) => toggleRow(p.id, e.target.checked)}
                    data-testid={`add-positions-check-${p.code}`}
                    className="mt-0.5 h-4 w-4 accent-primary-500"
                  />
                  <span className="min-w-0 flex-1">
                    <span className="block font-medium text-text-primary">
                      {pickLocalized(p.title_i18n, i18n.language)}
                    </span>
                    <span className="block text-xs text-text-muted">
                      <span className="font-mono">{p.code}</span>
                      {' · '}
                      {departmentNameOf(p.id) || '—'}
                    </span>
                  </span>
                </label>
              );
            })
          )}
        </div>

        {result ? (
          <div
            role="status"
            className={cn(
              'shrink-0 rounded-md border p-3 text-sm',
              result.failed.length === 0
                ? 'bg-success-50 border-success-500/30 text-success-700'
                : 'bg-warning-50 border-warning-500/30 text-warning-700',
            )}
            data-testid="add-positions-result"
          >
            <div className="flex items-center gap-2">
              {result.failed.length > 0 ? (
                <AlertTriangle size={14} aria-hidden />
              ) : null}
              <span>
                {t('evaluation.add_positions.result_summary', {
                  created: result.created,
                  failed: result.failed.length,
                })}
              </span>
            </div>
            {result.failed.length > 0 ? (
              <ul className="mt-2 text-xs list-disc pl-5 space-y-0.5 max-h-32 overflow-y-auto">
                {result.failed.slice(0, 10).map((f) => (
                  <li key={f.position_id}>
                    <span className="font-mono">
                      {f.position_id.slice(0, 8)}
                    </span>{' '}
                    — {f.error_code}
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
            data-testid="add-positions-error"
          >
            {error}
          </div>
        ) : null}

        <div className="shrink-0 flex justify-between items-center gap-2 pt-2">
          <span className="text-xs text-text-muted tabular-nums">
            {t('evaluation.add_positions.selected_count', {
              count: selected.size,
            })}
          </span>
          <div className="flex gap-2">
            <Button
              variant="secondary"
              onClick={onClose}
              disabled={submitting}
              data-testid="add-positions-cancel"
            >
              {t('common.cancel')}
            </Button>
            <Button
              onClick={handleSubmit}
              disabled={!canSubmit}
              data-testid="add-positions-confirm"
            >
              {t('evaluation.add_positions.confirm')}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
