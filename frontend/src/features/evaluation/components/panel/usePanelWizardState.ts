import { useCallback, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { pickLocalized } from '@/shared/lib/localized';
import { useSelectionSet } from '@/shared/lib/useSelectionSet';
import { routes } from '@/shared/config/routes';
import type { DepartmentCoverage } from '@/features/organization/components/DepartmentSingleSelectTree';
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

export interface UsePanelWizardStateArgs {
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

export type WizardStep = 1 | 2 | 3;

/**
 * All state, derived data and handlers for the dept-first 3-step panel wizard
 * (FE-1..FE-6). Extracted from `OpenPanelDialog` (FE-041) so the component
 * itself stays a thin render/orchestrator layer — this hook owns every piece
 * of local state, the data-fetching hooks it drives, and every handler the
 * step components need. Behaviour is unchanged; this is a structural move.
 */
export function usePanelWizardState({
  projectId,
  methodologies,
  defaultVersionId = null,
  rosterSeed = null,
  onConfirm,
  onCopyRosterToNext,
  onClose,
}: UsePanelWizardStateArgs) {
  const { i18n } = useTranslation();

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

  const changeVersion = (id: string) => {
    setVersionId(id);
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

  return {
    // Step navigation
    step,
    goNext,
    goBack,
    canAdvanceStep1,
    canAdvanceStep2,
    canConfirm,
    submitting,

    // Step 1 — department
    deptSearch,
    setDeptSearch,
    departmentId,
    selectDepartment,
    treeQuery,
    coverageOf,

    // Step 2 — positions
    departmentName,
    activeMethodologies,
    versionId,
    changeVersion,
    posSearch,
    setPosSearch,
    allSelected,
    toggleAll,
    selectablePositions,
    includeSubtree,
    toggleSubtree,
    deptPositionsQuery,
    candidates,
    fullyPaneled,
    isPaneled,
    panelIdFor,
    selectedPositions,
    togglePosition,
    panelDetailHref,

    // Step 3 — roster
    effectiveRows,
    canAddExternal,
    addExtra,
    removeExtra,
    suggestionsQuery,
    chosenUserIds,
    setRow,

    // Result / error / footer
    result,
    error,
    commissionSucceeded,
    onCopyRosterToNext,
    handleCopyRoster,
    handleSubmit,
  };
}
