import { useCallback, useMemo, useState } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { usePermission } from '@/features/auth/usePermission';
import { useAuthStore } from '@/features/auth/authStore';
import { PERMISSIONS } from '@/shared/types/permissions';
import { pickLocalized } from '@/shared/lib/localized';
import { useMethodologies } from '@/features/methodology/hooks/useMethodology';
import { useAllPositions } from '@/features/positions/hooks/usePositions';
import { useDepartmentTree } from '@/features/organization/hooks/useDepartmentTree';
import {
  useAllEvaluations,
  useBulkCreateEvaluations,
  useDeleteEvaluation,
} from '../hooks/useEvaluation';
import { useBulkCreatePanels, usePanels } from '../hooks/usePanels';
import type { RosterSeed } from '../components/panel/OpenPanelDialog';
import type { BulkCreatePanelsResult, PanelEvaluatorDraft } from '../panelTypes';
import type { Evaluation, EvaluationStatus } from '../types';

export type ViewMode = 'by-position' | 'by-factor';
export type TableDensity = 'comfortable' | 'compact';

const DENSITY_STORAGE_KEY = 'evaluation_table_density';

function isViewMode(value: string | null): value is ViewMode {
  return value === 'by-position' || value === 'by-factor';
}

function readDensity(): TableDensity {
  if (typeof window === 'undefined') return 'comfortable';
  try {
    const v = window.localStorage.getItem(DENSITY_STORAGE_KEY);
    return v === 'compact' ? 'compact' : 'comfortable';
  } catch {
    return 'comfortable';
  }
}

function writeDensity(v: TableDensity): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(DENSITY_STORAGE_KEY, v);
  } catch {
    /* ignore */
  }
}

/**
 * All state, data-fetching and derived values behind `EvaluationListPage`.
 * Extracted (FE-041) so the page itself stays a thin render/orchestrator
 * layer — this hook owns URL-driven view state, quick-filter chips, table
 * filters, dialog open-flags and every query/derived map the page's JSX
 * reads. Behaviour is unchanged; this is a structural move.
 */
export function useEvaluationListState() {
  const { i18n } = useTranslation();
  const { projectId = '' } = useParams<{ projectId: string }>();
  const { can } = usePermission();
  const canEdit = can(PERMISSIONS.EVALUATION_EDIT);
  const currentUser = useAuthStore((s) => s.user);

  // Committee scorer = can read evaluations but is NOT a methodology-aware
  // manager/oversight role (HRLAB_SUPER_ADMIN / HRLAB_CONSULTANT /
  // HRLAB_PROJECT_MANAGER / CLIENT_HR_DIRECTOR all hold METHODOLOGY_READ). Such a
  // user (e.g. a Department Director assigned to a panel) gets a focused,
  // factor-by-factor scoring experience — no project-wide management surfaces
  // (by-position list, completion bar, add-positions). Backend enforces the
  // per-sheet bias isolation; this is purely the role-aware UI shaping.
  const isCommitteeScorer =
    can(PERMISSIONS.EVALUATION_READ) && !can(PERMISSIONS.METHODOLOGY_READ);

  const [searchParams, setSearchParams] = useSearchParams();
  // A committee scorer is locked to the by-factor scoring view regardless of the
  // ?mode= URL param — they never see the by-position all-positions table.
  const mode: ViewMode = isCommitteeScorer
    ? 'by-factor'
    : isViewMode(searchParams.get('mode'))
      ? (searchParams.get('mode') as ViewMode)
      : 'by-position';
  const factorParam = searchParams.get('factor');
  // The methodology the K-sheet is scoped to. Shared via the URL so a refresh
  // keeps the choice AND the Add-positions dialog defaults to the same version.
  const methodologyParam = searchParams.get('methodology');

  // Quick-filter chips — driven via URL params.
  // "Only incomplete": status=INCOMPLETE (backend supports `status` param — verified in EvaluationController.java)
  // "Only mine": evaluatorUserId=currentUser.id (backend supports `evaluatorUserId` — verified in EvaluationController.java)
  const chipIncomplete = searchParams.get('chip_incomplete') === '1';
  const chipMine = searchParams.get('chip_mine') === '1';

  const setMode = useCallback(
    (next: ViewMode) => {
      const params = new URLSearchParams(searchParams);
      params.set('mode', next);
      if (next === 'by-position') params.delete('factor');
      setSearchParams(params, { replace: true });
    },
    [searchParams, setSearchParams],
  );

  const setFactorInUrl = useCallback(
    (factorId: string) => {
      const params = new URLSearchParams(searchParams);
      params.set('mode', 'by-factor');
      params.set('factor', factorId);
      setSearchParams(params, { replace: true });
    },
    [searchParams, setSearchParams],
  );

  const setMethodologyInUrl = useCallback(
    (methodologyId: string) => {
      const params = new URLSearchParams(searchParams);
      params.set('mode', 'by-factor');
      params.set('methodology', methodologyId);
      // A methodology switch invalidates the active factor (factors belong to a
      // single version) — drop it so the view re-picks the first factor of the
      // newly-selected version instead of carrying a stale id.
      params.delete('factor');
      setSearchParams(params, { replace: true });
    },
    [searchParams, setSearchParams],
  );

  const toggleChip = useCallback(
    (chip: 'incomplete' | 'mine', currentValue: boolean) => {
      const params = new URLSearchParams(searchParams);
      if (currentValue) {
        params.delete(`chip_${chip}`);
      } else {
        params.set(`chip_${chip}`, '1');
      }
      setSearchParams(params, { replace: true });
    },
    [searchParams, setSearchParams],
  );

  const clearChips = useCallback(() => {
    const params = new URLSearchParams(searchParams);
    params.delete('chip_incomplete');
    params.delete('chip_mine');
    setSearchParams(params, { replace: true });
  }, [searchParams, setSearchParams]);

  // The chip_incomplete and chip_mine params drive the BE query.
  // chip_incomplete maps to status=INCOMPLETE; chip_mine maps to evaluatorUserId=currentUser.id.
  // When both active we pass both; the BE ANDs them.
  // NOTE: chip_mine is only active when a currentUser is available.
  const chipMineUserId = chipMine && currentUser?.id ? currentUser.id : undefined;

  const [statusFilter, setStatusFilter] = useState<EvaluationStatus | ''>('');
  const [methodologyFilter, setMethodologyFilter] = useState<string>('');
  const [density, setDensity] = useState<TableDensity>(readDensity);
  const [adding, setAdding] = useState(false);
  const [openingPanel, setOpeningPanel] = useState(false);
  const [panelsDrawerOpen, setPanelsDrawerOpen] = useState(false);
  // Roster seed for the copy-roster affordance (FE-6) — kept across reopen so a
  // whole department can be commissioned then the roster reused for the next.
  const [rosterSeed, setRosterSeed] = useState<RosterSeed | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Evaluation | null>(null);

  const handleDensityToggle = useCallback(() => {
    setDensity((prev) => {
      const next: TableDensity = prev === 'comfortable' ? 'compact' : 'comfortable';
      writeDensity(next);
      return next;
    });
  }, []);

  // Status dropdown change: also clears chip_incomplete when the user picks a
  // status manually (the chip is more specific and would otherwise win).
  const handleStatusFilterChange = useCallback(
    (value: EvaluationStatus | '') => {
      setStatusFilter(value);
      if (chipIncomplete) {
        const params = new URLSearchParams(searchParams);
        params.delete('chip_incomplete');
        setSearchParams(params, { replace: true });
      }
    },
    [chipIncomplete, searchParams, setSearchParams],
  );

  // The effective status filter: chip_incomplete wins over dropdown
  // because the chip is more specific (forces INCOMPLETE); if neither active,
  // use the dropdown value.
  const effectiveStatus: EvaluationStatus | undefined =
    chipIncomplete ? 'INCOMPLETE' : statusFilter || undefined;

  // The COMPLETE project-wide evaluation set (all pages aggregated via the
  // shared fetchAllPages helper, no server-side status/evaluator cut) — same
  // EPIC-013 pattern as useAllPositions below. One source of truth for the
  // table (status/mine/methodology filters applied client-side in `rows`),
  // the CompletionBar, AND the AddPositionsDialog candidate diff. Before,
  // existingEvalKeys was built from ONE filtered backend page (default size
  // 20), so on large projects already-added positions reappeared as addable
  // and the table silently truncated.
  const evalsQuery = useAllEvaluations(projectId);
  const allEvaluations = useMemo(
    () => evalsQuery.data?.items ?? [],
    [evalsQuery.data],
  );

  // Position lookup for the department/title map, the "Add positions"
  // candidate picker, and the completion bar — ALL of it needs the FULL
  // project position set, not a guessed page size (the former `size: 500`
  // band-aid still silently truncated past that ceiling — see EPIC-013).
  // useAllPositions pages to completion via the shared fetchAllPages helper.
  const positionsQuery = useAllPositions(projectId ? { projectId } : null);
  const methodologiesQuery = useMethodologies(projectId);
  const treeQuery = useDepartmentTree(projectId);
  const panelsQuery = usePanels(projectId ? { projectId } : {});
  const bulkCreateMutation = useBulkCreateEvaluations();
  const deleteMutation = useDeleteEvaluation();
  const bulkCreatePanelsMutation = useBulkCreatePanels();

  /**
   * Panel-commission orchestration (FE-5): a SINGLE bulk-create carrying the
   * shared roster + every chosen position. The BE opens one panel per position
   * and returns the per-position failure collector (no sibling rollback). The
   * min-3-mandatory-roles rule is enforced server-side on lock-roster — the UI
   * mirror only disables confirm.
   */
  const handleBulkOpenPanels = useCallback(
    async (
      versionId: string,
      positionIds: string[],
      roster: PanelEvaluatorDraft[],
    ): Promise<BulkCreatePanelsResult> => {
      return bulkCreatePanelsMutation.mutateAsync({
        methodology_version_id: versionId,
        position_ids: positionIds,
        roster: roster
          .filter((r) => r.evaluator_user_id)
          .map((r) => ({
            evaluator_user_id: r.evaluator_user_id!,
            evaluator_role: r.role,
          })),
        // "Комиссия яратиш" must CREATE AND START the commission: the BE locks
        // each fully-rostered panel and creates the per-evaluator DRAFT sheets,
        // so the assigned experts can score on /app/my-evaluations right away.
        start_evaluations: true,
      });
    },
    [bulkCreatePanelsMutation],
  );

  const positionMap = useMemo(() => {
    const m = new Map<string, string>();
    for (const p of positionsQuery.data?.items ?? []) {
      m.set(p.id, pickLocalized(p.title_i18n, i18n.language));
    }
    return m;
  }, [positionsQuery.data, i18n.language]);

  const positionDeptIdMap = useMemo(() => {
    const m = new Map<string, string>();
    for (const p of positionsQuery.data?.items ?? []) {
      m.set(p.id, p.department_id);
    }
    return m;
  }, [positionsQuery.data]);

  const departmentNameMap = useMemo(() => {
    const m = new Map<string, string>();
    for (const d of treeQuery.data ?? []) {
      m.set(d.id, pickLocalized(d.name_i18n, i18n.language));
    }
    return m;
  }, [treeQuery.data, i18n.language]);

  const departmentNameOfPosition = useCallback(
    (positionId: string): string => {
      const deptId = positionDeptIdMap.get(positionId);
      if (!deptId) return '';
      return departmentNameMap.get(deptId) ?? '';
    },
    [positionDeptIdMap, departmentNameMap],
  );

  // FE-2 candidate diff: keys of (position_id|methodology_version_id) that
  // already have a NON-archived evaluation.
  const existingEvalKeys = useMemo(() => {
    const set = new Set<string>();
    for (const e of allEvaluations) {
      if (e.status === 'ARCHIVED') continue;
      set.add(`${e.position_id}|${e.methodology_version_id}`);
    }
    return set;
  }, [allEvaluations]);

  const methodologyMap = useMemo(() => {
    const m = new Map<string, string>();
    for (const meth of methodologiesQuery.data?.items ?? []) {
      m.set(meth.id, pickLocalized(meth.name_i18n, i18n.language));
    }
    return m;
  }, [methodologiesQuery.data, i18n.language]);

  // FE-027: version→methodology lookup used for (a) the methodology-filter
  // dropdown match and (b) a fallback for the "methodology" column when the
  // backend-resolved `methodologyVersionLabel` is absent (older API). Built
  // ONLY from the already-fetched methodology list's active_version_id /
  // latest_version_id — NO per-methodology version fetch. This previously also
  // resolved every historical (superseded) version id by firing one
  // fetchMethodologyVersions request PER methodology (via useQueries) on every
  // page load purely to label rows — the N+1 the audit flagged as FE-027. The
  // backend now resolves the row label directly, so that fan-out is gone; a row
  // scored against a truly historical (non-active/non-latest) version now falls
  // through to the dash the "methodology" column renders and won't match the
  // filter dropdown — an accepted, narrow trade-off given the overwhelming
  // majority of evaluations are scored against the active/latest version.
  const versionToMeth = useMemo(() => {
    const m = new Map<string, { id: string; name: string }>();
    (methodologiesQuery.data?.items ?? []).forEach((meth) => {
      const entry = { id: meth.id, name: pickLocalized(meth.name_i18n, i18n.language) };
      if (meth.active_version_id) m.set(meth.active_version_id, entry);
      if (meth.latest_version_id) m.set(meth.latest_version_id, entry);
    });
    return m;
  }, [methodologiesQuery.data, i18n.language]);

  const selectedVersionId = useMemo(() => {
    if (!methodologyParam) return null;
    const meth = (methodologiesQuery.data?.items ?? []).find(
      (m) => m.id === methodologyParam,
    );
    return meth?.active_version_id ?? null;
  }, [methodologyParam, methodologiesQuery.data]);

  const rows = useMemo(() => {
    let items = allEvaluations;
    // Status + "mine" cuts moved client-side (the query is now the full
    // project-wide set); both are stored BE enums/ids, so this matches the
    // previous server-side filtering exactly.
    if (effectiveStatus) {
      items = items.filter((e) => e.status === effectiveStatus);
    }
    if (chipMineUserId) {
      items = items.filter((e) => e.evaluator_user_id === chipMineUserId);
    }
    if (methodologyFilter) {
      items = items.filter(
        (e) => versionToMeth.get(e.methodology_version_id)?.id === methodologyFilter,
      );
    }
    return items;
  }, [allEvaluations, effectiveStatus, chipMineUserId, methodologyFilter, versionToMeth]);

  // Whether any quick-filter chip is active — drives "Clear filters" affordance.
  const anyChipActive = chipIncomplete || chipMine;

  return {
    // Route / permissions
    projectId,
    canEdit,
    currentUser,
    isCommitteeScorer,

    // URL-driven view state
    mode,
    factorParam,
    methodologyParam,
    setMode,
    setFactorInUrl,
    setMethodologyInUrl,

    // Quick-filter chips
    chipIncomplete,
    chipMine,
    toggleChip,
    clearChips,
    anyChipActive,

    // Table filters / density / dialog flags
    statusFilter,
    handleStatusFilterChange,
    methodologyFilter,
    setMethodologyFilter,
    density,
    handleDensityToggle,
    adding,
    setAdding,
    openingPanel,
    setOpeningPanel,
    panelsDrawerOpen,
    setPanelsDrawerOpen,
    rosterSeed,
    setRosterSeed,
    deleteTarget,
    setDeleteTarget,

    // Queries / mutations
    evalsQuery,
    positionsQuery,
    methodologiesQuery,
    treeQuery,
    panelsQuery,
    bulkCreateMutation,
    deleteMutation,
    handleBulkOpenPanels,

    // Derived maps / rows
    positionMap,
    departmentNameOfPosition,
    existingEvalKeys,
    methodologyMap,
    versionToMeth,
    selectedVersionId,
    rows,
  };
}
