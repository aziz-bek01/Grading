import { useCallback, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  AlertTriangle,
  ArrowLeft,
  ArrowRight,
  ExternalLink,
  Plus,
  Search,
  X,
} from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { Modal } from '@/shared/components/ui/Modal';
import { cn } from '@/shared/lib/cn';
import { pickLocalized } from '@/shared/lib/localized';
import { useSelectionSet } from '@/shared/lib/useSelectionSet';
import { routes } from '@/shared/config/routes';
import {
  DepartmentSingleSelectTree,
  type DepartmentCoverage,
} from '@/features/organization/components/DepartmentSingleSelectTree';
import { useDepartmentTree } from '@/features/organization/hooks/useDepartmentTree';
import { useDepartmentPositionCounts } from '@/features/organization/hooks/useDepartmentPositionCounts';
import { usePositions } from '@/features/positions/hooks/usePositions';
import { useAllUsers } from '@/features/users-access/hooks/useUsers';
import type { Methodology } from '@/features/methodology/types';
import type { Position } from '@/features/positions/types/positionTypes';
import { usePanels, useRosterSuggestions } from '../../hooks/usePanels';
import {
  MANDATORY_EVALUATOR_ROLES,
  MAX_EXTERNAL_SEATS,
  type BulkCreatePanelsResult,
  type PanelEvaluatorDraft,
} from '../../panelTypes';
import { WizardRosterDraftSchema } from '../../schemas/panelSchemas';
import { PanelRoleChip } from './PanelRoleChip';
import { EvaluatorPicker } from './EvaluatorPicker';

/** Persisted last-used HR director per project (convenience only, not security). */
const HR_DIRECTOR_LS_KEY = (projectId: string) =>
  `hrlab.panel.last_hr_director.${projectId}`;

function readLastHrDirector(projectId: string): string | null {
  try {
    return window.localStorage.getItem(HR_DIRECTOR_LS_KEY(projectId));
  } catch {
    return null;
  }
}

function writeLastHrDirector(projectId: string, userId: string): void {
  try {
    window.localStorage.setItem(HR_DIRECTOR_LS_KEY(projectId), userId);
  } catch {
    /* storage unavailable — convenience only, ignore */
  }
}

/** A seed used by the copy-roster affordance (FE-6) when reopening the wizard. */
export interface RosterSeed {
  /** HR_DIRECTOR + EXTERNAL/ADDITIONAL seats kept; DEPT_DIRECTOR is re-suggested. */
  rows: PanelEvaluatorDraft[];
}

interface Props {
  open: boolean;
  projectId: string;
  methodologies: Methodology[];
  defaultVersionId?: string | null;
  /** Optional roster seed (copy-roster from the previous department). */
  rosterSeed?: RosterSeed | null;
  /**
   * Confirm handler — fires ONE bulk-create with the shared roster. Returns the
   * BE per-position failure collector so the wizard can surface partial failures
   * inline (mirrors AddPositionsDialog's retry behaviour).
   */
  onConfirm: (
    versionId: string,
    positionIds: string[],
    roster: PanelEvaluatorDraft[],
  ) => Promise<BulkCreatePanelsResult>;
  /** Copy-roster to next department: keep HR + externals, clear dept director. */
  onCopyRosterToNext?: (seed: RosterSeed) => void;
  onClose: () => void;
}

function makeMandatoryRows(): PanelEvaluatorDraft[] {
  return MANDATORY_EVALUATOR_ROLES.map((role) => ({
    role,
    evaluator_user_id: null,
    evaluator_name: null,
  }));
}

type WizardStep = 1 | 2 | 3;

/**
 * Dept-first 3-step panel wizard (FE-1..FE-6).
 *
 * REFACTOR of the former flat-select dialog — same shared `<Modal>` overlay
 * chrome (src/shared/components/ui/Modal.tsx), the same EvaluatorPicker rows / PanelRoleChip / makeMandatoryRows /
 * addExtra/removeExtra roster machinery, and the same partial-fail result block.
 * The flat position <select> is replaced by:
 *   Step 1 DEPARTMENT  — searchable single-select dept tree + coverage badges.
 *   Step 2 POSITIONS   — server-filtered candidate list (multi-select, select-all,
 *                        search); already-paneled rows DISABLED + marked.
 *   Step 3 COMMISSION  — shared roster (dept-director suggested, HR last-used,
 *                        3-4 externals); Confirm fires ONE bulk-create.
 */
export function OpenPanelDialog({ open, ...rest }: Props) {
  if (!open) return null;
  return <OpenPanelDialogBody {...rest} />;
}

function OpenPanelDialogBody({
  projectId,
  methodologies,
  defaultVersionId = null,
  rosterSeed = null,
  onConfirm,
  onCopyRosterToNext,
  onClose,
}: Omit<Props, 'open'>) {
  const { t, i18n } = useTranslation();

  const activeMethodologies = useMemo(
    () => methodologies.filter((m) => m.active_version_id),
    [methodologies],
  );

  const [step, setStep] = useState<WizardStep>(1);
  const [departmentId, setDepartmentId] = useState<string | null>(null);
  const [deptSearch, setDeptSearch] = useState('');
  const [posSearch, setPosSearch] = useState('');
  // T4 — Step 2 subtree toggle: when ON the candidate list includes the selected
  // unit's descendants (server expands via includeSubtree=true); OFF = direct.
  const [includeSubtree, setIncludeSubtree] = useState(false);
  const [versionId, setVersionId] = useState(() => {
    const preferred =
      defaultVersionId &&
      activeMethodologies.some((m) => m.active_version_id === defaultVersionId)
        ? defaultVersionId
        : '';
    return preferred || activeMethodologies[0]?.active_version_id || '';
  });
  const [rows, setRows] = useState<PanelEvaluatorDraft[]>(
    () => rosterSeed?.rows ?? makeMandatoryRows(),
  );
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<BulkCreatePanelsResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  // ----- Data: dept tree (Step 1), all-project panels (coverage marking) -----
  const treeQuery = useDepartmentTree(projectId);
  const panelsQuery = usePanels({ projectId });

  // Active panels keyed by `${position_id}|${methodology_version_id}` → panel_id
  // so an already-paneled position for the chosen version is marked (not hidden)
  // AND can be deep-linked to its panel detail (T3 — un-dead-end). REUSES the
  // already-loaded panelsQuery; carries panel_id instead of just membership.
  const paneledPanelIds = useMemo(() => {
    const map = new Map<string, string>();
    for (const p of panelsQuery.data?.items ?? []) {
      if (p.status === 'ARCHIVED') continue;
      map.set(`${p.position_id}|${p.methodology_version_id}`, p.id);
    }
    return map;
  }, [panelsQuery.data]);

  // ----- Data: per-department coverage counts (Step 1 badges) -----
  // T4 — server-authoritative counts (direct + subtree roll-up) from the ONE
  // shared hook. Replaces the former FE 200-row count scan, so a parent rolls up
  // its descendants (never 0) and the count is never page-capped.
  const countsQuery = useDepartmentPositionCounts(projectId);

  const coverageOf = useCallback(
    (deptId: string): DepartmentCoverage | undefined => {
      const c = countsQuery.data?.get(deptId);
      if (!c) return undefined;
      // Position totals are server-authoritative (direct + subtree roll-up). The
      // per-department PANELED coverage lives in the dedicated DepartmentPanelProgress
      // strip (which has the position→department map); the Step-1 tree badge shows
      // the authoritative position counts only (paneledCount=0 ⇒ no paneled chip),
      // so there is no FE count scan here and no second count computation.
      return {
        positionCount: c.directCount,
        paneledCount: 0,
        subtreeCount: c.subtreeCount,
      };
    },
    [countsQuery.data],
  );

  // ----- Data: positions for the CHOSEN department (server-filtered) -----
  // No client-side scan of 600-1200 positions — the BE GET /positions accepts
  // departmentId + status and returns just that department's cut. T4 — when the
  // subtree toggle is ON, includeSubtree=true expands departmentId to its whole
  // subtree (BE closure call); OFF = direct positions only (current behaviour).
  const deptPositionsQuery = usePositions(
    departmentId
      ? { projectId, departmentId, status: 'ACTIVE', size: 200, includeSubtree }
      : null,
  );

  // ----- Step 2 candidate diff: department positions, paneled rows disabled -----
  const candidates = useMemo(() => {
    const q = posSearch.trim().toLowerCase();
    return (deptPositionsQuery.data?.items ?? [])
      .filter((p) => p.status !== 'ARCHIVED')
      .filter((p) => {
        if (!q) return true;
        const title = pickLocalized(p.title_i18n, i18n.language).toLowerCase();
        return title.includes(q) || p.code.toLowerCase().includes(q);
      });
  }, [deptPositionsQuery.data, posSearch, i18n.language]);

  const panelIdFor = useCallback(
    (p: Position): string | undefined =>
      versionId ? paneledPanelIds.get(`${p.id}|${versionId}`) : undefined,
    [versionId, paneledPanelIds],
  );

  const isPaneled = useCallback(
    (p: Position) => panelIdFor(p) != null,
    [panelIdFor],
  );

  const selectablePositions = useMemo(
    () => candidates.filter((p) => !isPaneled(p)),
    [candidates, isPaneled],
  );

  const fullyPaneled =
    candidates.length > 0 && selectablePositions.length === 0;

  const {
    selected: selectedPositions,
    setSelected: setSelectedPositions,
    toggle: toggleSelectedPosition,
    toggleAll: toggleAllSelectedPositions,
    allSelected,
    clear: clearSelectedPositions,
  } = useSelectionSet(selectablePositions, (p) => p.id);

  const toggleAll = () => {
    toggleAllSelectedPositions();
    setResult(null);
  };

  const togglePosition = (id: string, on: boolean) => {
    toggleSelectedPosition(id, on);
    setResult(null);
  };

  // ----- Step 3 roster: dept-director suggestion + HR last-used -----
  const suggestionsQuery = useRosterSuggestions(
    projectId,
    step >= 3 && departmentId ? departmentId : undefined,
  );
  // Full tenant roster (shared fetchAllPages helper) — this ONLY resolves the
  // previously-used HR director's display name from a persisted id, so an HR
  // director beyond the backend's default page-20 cap is still found (see
  // EPIC-013). The seat pickers themselves are EvaluatorPicker, which loads
  // its own full set independently.
  const { data: usersData } = useAllUsers();

  // Advisory defaults are OVERLAID at render time (no setState-in-effect): the
  // DEPT_DIRECTOR seat is pre-filled from the advisory suggestion and the
  // HR_DIRECTOR seat from the project's last-used HR director, but ONLY for a
  // seat the user has not explicitly edited (tracked in `dirtyIndexes`) and only
  // when that seat is still empty in the base `rows` state. This keeps the seats
  // editable while never clobbering a manual / copied choice.
  const [dirtyIndexes, setDirtyIndexes] = useState<Set<number>>(new Set());

  const suggestedDeptDirector = suggestionsQuery.data?.dept_director_candidates?.[0] ?? null;
  const lastHrDirector = useMemo(() => {
    if (step < 3) return null;
    const lastId = readLastHrDirector(projectId);
    if (!lastId) return null;
    const u = (usersData?.items ?? []).find(
      (x) => x.id === lastId && x.status === 'ACTIVE',
    );
    return u ? { user_id: u.id, full_name: u.full_name } : null;
  }, [step, projectId, usersData]);

  const effectiveRows = useMemo<PanelEvaluatorDraft[]>(() => {
    return rows.map((r, i) => {
      if (r.evaluator_user_id || dirtyIndexes.has(i)) return r;
      if (r.role === 'DEPARTMENT_DIRECTOR' && suggestedDeptDirector) {
        return {
          ...r,
          evaluator_user_id: suggestedDeptDirector.user_id,
          evaluator_name: suggestedDeptDirector.full_name,
        };
      }
      if (r.role === 'HR_DIRECTOR' && lastHrDirector) {
        return {
          ...r,
          evaluator_user_id: lastHrDirector.user_id,
          evaluator_name: lastHrDirector.full_name,
        };
      }
      return r;
    });
  }, [rows, dirtyIndexes, suggestedDeptDirector, lastHrDirector]);

  const chosenUserIds = useMemo(
    () =>
      effectiveRows
        .map((r) => r.evaluator_user_id)
        .filter((x): x is string => !!x),
    [effectiveRows],
  );

  const setRow = (index: number, userId: string, userName: string | null) => {
    setDirtyIndexes((prev) => new Set(prev).add(index));
    setRows((prev) =>
      prev.map((r, i) =>
        i === index
          ? { ...r, evaluator_user_id: userId || null, evaluator_name: userName }
          : r,
      ),
    );
    setResult(null);
  };

  const externalSeatCount = useMemo(
    () =>
      effectiveRows.filter(
        (r) => r.role === 'EXTERNAL_EXPERT' || r.role === 'ADDITIONAL',
      ).length,
    [effectiveRows],
  );
  const canAddExternal = externalSeatCount < MAX_EXTERNAL_SEATS;

  const addExtra = () => {
    if (!canAddExternal) return;
    setRows((prev) => [
      ...prev,
      { role: 'ADDITIONAL', evaluator_user_id: null, evaluator_name: null },
    ]);
  };

  const removeExtra = (index: number) => {
    setRows((prev) => prev.filter((_, i) => i !== index));
    setDirtyIndexes((prev) => {
      // Re-key dirty markers after the splice so they keep pointing at the same
      // logical seats.
      const next = new Set<number>();
      for (const idx of prev) {
        if (idx < index) next.add(idx);
        else if (idx > index) next.add(idx - 1);
      }
      return next;
    });
  };

  const rosterValid = useMemo(
    () => WizardRosterDraftSchema.safeParse(effectiveRows).success,
    [effectiveRows],
  );

  const filledRows = useMemo(
    () => effectiveRows.filter((r) => r.evaluator_user_id),
    [effectiveRows],
  );

  // ----- Step gating -----
  const canAdvanceStep1 = !!departmentId;
  const canAdvanceStep2 = !!versionId && selectedPositions.size > 0;
  const canConfirm =
    canAdvanceStep2 && rosterValid && !submitting;

  const goNext = () => {
    setError(null);
    setResult(null);
    if (step === 1 && canAdvanceStep1) setStep(2);
    else if (step === 2 && canAdvanceStep2) setStep(3);
  };

  const goBack = () => {
    setError(null);
    setResult(null);
    if (step === 3) setStep(2);
    else if (step === 2) setStep(1);
  };

  const selectDepartment = (id: string) => {
    setDepartmentId(id);
    clearSelectedPositions();
    setIncludeSubtree(false);
    setResult(null);
  };

  const toggleSubtree = (on: boolean) => {
    setIncludeSubtree(on);
    // The candidate set changes — drop any selection that may no longer be listed.
    clearSelectedPositions();
    setResult(null);
  };

  /** Deep-link to a position's existing panel detail (T3 — un-dead-end). */
  const panelDetailHref = (panelId: string) =>
    routes.projectPanelDetail(projectId, panelId);

  const handleSubmit = async () => {
    setError(null);
    setSubmitting(true);
    try {
      const r = await onConfirm(
        versionId,
        Array.from(selectedPositions),
        filledRows,
      );
      setResult(r);
      // Persist the HR director for next time (convenience).
      const hr = filledRows.find((row) => row.role === 'HR_DIRECTOR');
      if (hr?.evaluator_user_id) writeLastHrDirector(projectId, hr.evaluator_user_id);
      if (r.failed.length === 0) {
        setTimeout(() => onClose(), 400);
      } else {
        // Keep open; drop succeeded positions so a retry only re-attempts
        // failures (mirrors AddPositionsDialog). A row reported ROSTER_PARTIAL
        // or ROSTER_LOCK_FAILED DID create its panel, so it is also dropped — a
        // retry would just hit ALREADY_EXISTS. Only fully-failed rows
        // (ALREADY_EXISTS / ACCESS_DENIED / VALIDATION) remain selected.
        const retryIds = new Set(
          r.failed
            .filter(
              (f) =>
                f.error_code !== 'ROSTER_PARTIAL' &&
                f.error_code !== 'ROSTER_LOCK_FAILED',
            )
            .map((f) => f.position_id),
        );
        setSelectedPositions((prev) => {
          const next = new Set<string>();
          for (const id of prev) if (retryIds.has(id)) next.add(id);
          return next;
        });
      }
    } catch (e) {
      setError((e as Error).message ?? 'Bulk create failed');
    } finally {
      setSubmitting(false);
    }
  };

  const handleCopyRoster = () => {
    // Keep HR + external/additional seats; clear the DEPT_DIRECTOR seat (it is
    // re-suggested for the next department). Pure FE state — no new API.
    const kept = effectiveRows.map((r) =>
      r.role === 'DEPARTMENT_DIRECTOR'
        ? { ...r, evaluator_user_id: null, evaluator_name: null }
        : r,
    );
    onCopyRosterToNext?.({ rows: kept });
  };

  const commissionSucceeded =
    result != null && result.created > 0;

  const departmentName = (() => {
    if (!departmentId) return '';
    const d = (treeQuery.data ?? []).find((x) => x.id === departmentId);
    return d ? pickLocalized(d.name_i18n, i18n.language) : '';
  })();

  return (
    <Modal
      open
      onClose={onClose}
      labelledBy="open-panel-title"
      size="lg"
      dismissible={!submitting}
      className="bg-surface rounded-xl shadow-lg border border-border max-h-[85vh] flex flex-col p-6 space-y-4"
    >
      <>
        <header className="shrink-0">
          <h2 id="open-panel-title" className="text-lg text-text-primary">
            {t('panel.dialog.title')}
          </h2>
          {/* Step indicator (FE-1) */}
          <ol
            className="mt-2 flex items-center gap-2 text-xs"
            data-testid="wizard-steps"
            aria-label={t('panel.wizard.steps_aria')}
          >
            {([1, 2, 3] as WizardStep[]).map((s) => (
              <li
                key={s}
                aria-current={step === s ? 'step' : undefined}
                className={cn(
                  'flex items-center gap-1.5',
                  step === s ? 'text-primary-700 font-medium' : 'text-text-muted',
                )}
              >
                <span
                  className={cn(
                    'inline-flex h-5 w-5 items-center justify-center rounded-full border tabular-nums',
                    step === s
                      ? 'border-primary-500 bg-primary-50'
                      : step > s
                        ? 'border-success-500 bg-success-50 text-success-700'
                        : 'border-border-strong',
                  )}
                >
                  {s}
                </span>
                {t(`panel.wizard.step_${s}_title`)}
                {s < 3 ? <span aria-hidden>·</span> : null}
              </li>
            ))}
          </ol>
        </header>

        {/* ---------------- STEP 1 — DEPARTMENT ---------------- */}
        {step === 1 ? (
          <div className="flex flex-col flex-1 min-h-0 space-y-2" data-testid="wizard-step-1">
            <p className="shrink-0 text-sm text-text-secondary">
              {t('panel.wizard.step_1_body')}
            </p>
            <label className="relative shrink-0">
              <span className="sr-only">{t('common.search')}</span>
              <Search
                size={14}
                aria-hidden
                className="absolute left-2 top-1/2 -translate-y-1/2 text-text-muted"
              />
              <input
                type="search"
                value={deptSearch}
                onChange={(e) => setDeptSearch(e.target.value)}
                placeholder={t('panel.wizard.dept.search_placeholder')}
                data-testid="wizard-dept-search"
                className="w-full h-9 pl-7 pr-3 border border-border-strong rounded-md text-sm bg-surface"
              />
            </label>
            <div
              className="flex-1 min-h-0 overflow-y-auto border border-border rounded-md p-1"
              data-testid="wizard-dept-list"
            >
              {treeQuery.isLoading ? (
                <p className="p-4 text-sm text-text-muted text-center">
                  {t('common.loading')}
                </p>
              ) : treeQuery.isError ? (
                <p className="p-4 text-sm text-danger-600 text-center">
                  {t('panel.wizard.dept.load_error')}
                </p>
              ) : (
                <DepartmentSingleSelectTree
                  items={treeQuery.data ?? []}
                  selectedId={departmentId}
                  onSelect={selectDepartment}
                  countsOf={coverageOf}
                  search={deptSearch}
                />
              )}
            </div>
          </div>
        ) : null}

        {/* ---------------- STEP 2 — POSITIONS ---------------- */}
        {step === 2 ? (
          <div className="flex flex-col flex-1 min-h-0 space-y-3" data-testid="wizard-step-2">
            <div className="shrink-0">
              <p className="text-sm text-text-secondary">
                {t('panel.wizard.step_2_body', { department: departmentName })}
              </p>
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
                  clearSelectedPositions();
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
                  value={posSearch}
                  onChange={(e) => setPosSearch(e.target.value)}
                  placeholder={t('common.search')}
                  data-testid="wizard-position-search"
                  className="w-full h-9 pl-7 pr-3 border border-border-strong rounded-md text-sm bg-surface"
                />
              </label>
              <label className="inline-flex items-center gap-1.5 text-sm text-text-secondary whitespace-nowrap">
                <input
                  type="checkbox"
                  checked={allSelected}
                  onChange={toggleAll}
                  disabled={selectablePositions.length === 0}
                  data-testid="wizard-select-all"
                  className="h-4 w-4 accent-primary-500"
                />
                {t('evaluation.add_positions.select_all')}
              </label>
            </div>

            {/* T4 — subtree toggle: list the unit's descendants too (server
                expands via includeSubtree=true) so a parent department is not
                limited to its direct positions. */}
            <label className="shrink-0 inline-flex items-center gap-1.5 text-sm text-text-secondary">
              <input
                type="checkbox"
                checked={includeSubtree}
                onChange={(e) => toggleSubtree(e.target.checked)}
                data-testid="wizard-include-subtree"
                className="h-4 w-4 accent-primary-500"
              />
              {t('panel.wizard.positions.include_subtree')}
            </label>

            <div
              className="flex-1 min-h-0 overflow-y-auto border border-border rounded-md divide-y divide-border"
              data-testid="wizard-position-list"
            >
              {deptPositionsQuery.isLoading ? (
                <p className="p-4 text-sm text-text-muted text-center">
                  {t('common.loading')}
                </p>
              ) : candidates.length === 0 ? (
                <p
                  className="p-4 text-sm text-text-muted text-center"
                  data-testid="wizard-positions-empty"
                >
                  {t('panel.wizard.positions.empty')}
                </p>
              ) : fullyPaneled ? (
                <div
                  className="p-4 text-sm text-text-muted text-center space-y-2"
                  data-testid="wizard-positions-fully-paneled"
                >
                  {/* T3 — un-dead-end: keep the disabled rows for context but
                      give each already-paneled position an "open existing panel"
                      CTA that deep-links to its panel detail. panel_id resolved
                      from the already-loaded panelsQuery (no new fetch). */}
                  <p>{t('panel.wizard.positions.fully_paneled')}</p>
                  {candidates.map((p) => {
                    const existingPanelId = panelIdFor(p);
                    return (
                      <PositionRow
                        key={p.id}
                        position={p}
                        checked={false}
                        disabled
                        onToggle={() => undefined}
                        locale={i18n.language}
                        paneledLabel={t('panel.wizard.positions.already_paneled')}
                        openPanelHref={
                          existingPanelId ? panelDetailHref(existingPanelId) : null
                        }
                        openPanelLabel={t('panel.wizard.positions.open_existing')}
                      />
                    );
                  })}
                </div>
              ) : (
                candidates.map((p) => {
                  const paneled = isPaneled(p);
                  const existingPanelId = panelIdFor(p);
                  return (
                    <PositionRow
                      key={p.id}
                      position={p}
                      checked={selectedPositions.has(p.id)}
                      disabled={paneled}
                      onToggle={(on) => togglePosition(p.id, on)}
                      locale={i18n.language}
                      paneledLabel={
                        paneled ? t('panel.wizard.positions.already_paneled') : null
                      }
                      openPanelHref={
                        paneled && existingPanelId
                          ? panelDetailHref(existingPanelId)
                          : null
                      }
                      openPanelLabel={t('panel.wizard.positions.open_existing')}
                    />
                  );
                })
              )}
            </div>
          </div>
        ) : null}

        {/* ---------------- STEP 3 — COMMISSION ROSTER ---------------- */}
        {step === 3 ? (
          <div className="flex flex-col flex-1 min-h-0 space-y-2" data-testid="wizard-step-3">
            <p className="shrink-0 text-sm text-text-secondary">
              {t('panel.wizard.step_3_body', {
                count: selectedPositions.size,
                department: departmentName,
              })}
            </p>
            <div className="shrink-0 flex items-center justify-between">
              <span className="text-sm font-medium">{t('panel.dialog.evaluators')}</span>
              <Button
                variant="secondary"
                onClick={addExtra}
                disabled={!canAddExternal}
                data-testid="open-panel-add-evaluator"
                className="h-8 px-2 text-xs"
              >
                <Plus size={14} aria-hidden /> {t('panel.dialog.add_evaluator')}
              </Button>
            </div>
            <p className="shrink-0 text-xs text-text-muted" data-testid="open-panel-helper">
              {t('panel.dialog.min_helper')}
            </p>
            {suggestionsQuery.isError ? (
              <p
                className="shrink-0 text-xs text-warning-700"
                data-testid="wizard-suggestion-error"
              >
                {t('panel.wizard.roster.suggestion_error')}
              </p>
            ) : null}

            <ul
              className="flex-1 min-h-0 overflow-y-auto space-y-2"
              data-testid="open-panel-roster"
            >
              {effectiveRows.map((row, index) => {
                const isMandatory = MANDATORY_EVALUATOR_ROLES.includes(row.role);
                const isDeptDirSuggested =
                  row.role === 'DEPARTMENT_DIRECTOR' &&
                  !!row.evaluator_user_id &&
                  (suggestionsQuery.data?.dept_director_candidates ?? []).some(
                    (c) => c.user_id === row.evaluator_user_id,
                  );
                return (
                  <li
                    key={`${row.role}-${index}`}
                    data-testid={`open-panel-row-${index}`}
                    className="flex items-center gap-2"
                  >
                    <span className="min-w-[9rem] shrink-0">
                      <PanelRoleChip role={row.role} />
                      {isDeptDirSuggested ? (
                        <span
                          className="block text-[10px] text-text-muted mt-0.5"
                          data-testid={`open-panel-suggested-${index}`}
                        >
                          {t('panel.wizard.roster.dept_director_suggested')}
                        </span>
                      ) : null}
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
        ) : null}

        {/* ---------------- Result / error ---------------- */}
        {result ? (
          <div
            role="status"
            className={cn(
              'shrink-0 rounded-md border p-3 text-sm',
              result.failed.length === 0
                ? 'bg-success-50 border-success-500/30 text-success-700'
                : 'bg-warning-50 border-warning-500/30 text-warning-700',
            )}
            data-testid="open-panel-result"
          >
            <div className="flex items-center gap-2">
              {result.failed.length > 0 ? <AlertTriangle size={14} aria-hidden /> : null}
              <span>
                {t('panel.wizard.result.summary', {
                  created: result.created,
                  failed: result.failed.length,
                })}
              </span>
            </div>
            {result.failed.length > 0 ? (
              <ul className="mt-2 text-xs list-disc pl-5 space-y-1 max-h-32 overflow-y-auto">
                {result.failed.slice(0, 12).map((f) => (
                  <li key={f.position_id} data-testid={`open-panel-fail-${f.position_id}`}>
                    <span className="font-mono">{f.position_id.slice(0, 8)}</span>
                    {' — '}
                    {t(`panel.wizard.result.error.${f.error_code}`)}
                    {f.error_code === 'ROSTER_PARTIAL' && f.seat_failures?.length ? (
                      <ul className="mt-0.5 list-[circle] pl-4">
                        {f.seat_failures.map((sf, i) => (
                          <li key={i}>
                            {t(`panel.role.${sf.evaluator_role}`)} — {sf.reason}
                          </li>
                        ))}
                      </ul>
                    ) : null}
                  </li>
                ))}
              </ul>
            ) : null}
            {commissionSucceeded && onCopyRosterToNext ? (
              <Button
                variant="secondary"
                onClick={handleCopyRoster}
                data-testid="open-panel-copy-roster"
                className="mt-3 h-8 px-2 text-xs"
              >
                {t('panel.wizard.copy_roster')}
              </Button>
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

        {/* ---------------- Footer nav ---------------- */}
        <div className="shrink-0 flex justify-between items-center gap-2 pt-2">
          <div className="text-xs text-text-muted tabular-nums">
            {step === 2
              ? t('panel.wizard.selected_count', { count: selectedPositions.size })
              : null}
          </div>
          <div className="flex gap-2">
            {step > 1 ? (
              <Button
                variant="secondary"
                onClick={goBack}
                disabled={submitting}
                data-testid="wizard-back"
              >
                <ArrowLeft size={14} aria-hidden /> {t('common.back')}
              </Button>
            ) : (
              <Button
                variant="secondary"
                onClick={onClose}
                disabled={submitting}
                data-testid="open-panel-cancel"
              >
                {t('common.cancel')}
              </Button>
            )}
            {step < 3 ? (
              <Button
                onClick={goNext}
                disabled={step === 1 ? !canAdvanceStep1 : !canAdvanceStep2}
                data-testid="wizard-next"
              >
                {t('panel.wizard.next')} <ArrowRight size={14} aria-hidden />
              </Button>
            ) : (
              <Button
                onClick={handleSubmit}
                disabled={!canConfirm}
                data-testid="open-panel-confirm"
              >
                {t('panel.wizard.confirm')}
              </Button>
            )}
          </div>
        </div>
      </>
    </Modal>
  );
}

/** One position row — reuses the AddPositionsDialog checkbox-row visuals. */
function PositionRow({
  position,
  checked,
  disabled,
  onToggle,
  locale,
  paneledLabel,
  openPanelHref,
  openPanelLabel,
}: {
  position: Position;
  checked: boolean;
  disabled?: boolean;
  onToggle: (on: boolean) => void;
  locale: string;
  paneledLabel: string | null;
  /** T3 — deep-link to the existing panel (already-paneled rows only). */
  openPanelHref?: string | null;
  openPanelLabel?: string;
}) {
  return (
    <div
      data-testid={`wizard-position-row-${position.code}`}
      className={cn(
        'flex items-start gap-2.5 px-3 py-2.5 text-sm transition-colors',
        disabled
          ? 'opacity-60'
          : checked
            ? 'bg-primary-50/40'
            : 'hover:bg-divider/40',
      )}
    >
      <label
        className={cn(
          'flex items-start gap-2.5 min-w-0 flex-1',
          disabled ? 'cursor-not-allowed' : 'cursor-pointer',
        )}
      >
        <input
          type="checkbox"
          checked={checked}
          disabled={disabled}
          onChange={(e) => onToggle(e.target.checked)}
          data-testid={`wizard-position-check-${position.code}`}
          className="mt-0.5 h-4 w-4 accent-primary-500"
        />
        <span className="min-w-0 flex-1 text-left">
          <span className="block font-medium text-text-primary">
            {pickLocalized(position.title_i18n, locale)}
          </span>
          <span className="block text-xs text-text-muted">
            <span className="font-mono">{position.code}</span>
            {paneledLabel ? (
              <span
                className="ml-2 rounded-full bg-divider px-1.5 py-0.5 text-text-secondary"
                data-testid={`wizard-position-paneled-${position.code}`}
              >
                {paneledLabel}
              </span>
            ) : null}
          </span>
        </span>
      </label>
      {openPanelHref ? (
        <Link
          to={openPanelHref}
          data-testid={`wizard-open-existing-${position.code}`}
          className="shrink-0 inline-flex items-center gap-1 text-xs text-primary-600 hover:underline whitespace-nowrap mt-0.5"
        >
          <ExternalLink size={12} aria-hidden />
          {openPanelLabel}
        </Link>
      ) : null}
    </div>
  );
}
