import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { PERMISSIONS } from '@/shared/types/permissions';
import { usePermission } from '@/features/auth/usePermission';
import { useAuthStore } from '@/features/auth/authStore';
import { useMethodologies, useMyMethodologies, useMethodologyVersion } from '@/features/methodology/hooks/useMethodology';
import { useDepartmentTree } from '@/features/organization/hooks/useDepartmentTree';
import { pickLocalized } from '@/shared/lib/localized';
import { useSelectionSet } from '@/shared/lib/useSelectionSet';
import { useEvaluations } from '../../hooks/useEvaluation';
import { upsertScore } from '../../api/evaluationApi';
import {
  useBulkScoreSet,
  useBulkSubmit,
  useEvaluationsByFactor,
} from '../../hooks/useEvaluationsByFactor';
import { evaluationKeys } from '../../api/evaluationApi';
import type {
  EvaluationByFactorRow,
  EvaluationStatus,
  EvaluationsByFactorFilters,
} from '../../types';
import type { FactorCompletionMap } from './FactorTabs';
import type { EvaluatorRole } from '../../panelTypes';

export interface UseByFactorViewStateArgs {
  projectId: string;
  factorIdFromUrl: string | null;
  onFactorChange: (factorId: string) => void;
  methodologyIdFromUrl?: string | null;
  onMethodologyChange?: (methodologyId: string) => void;
  selfRole?: EvaluatorRole | null;
  blind?: boolean;
}

export const BY_FACTOR_PAGE_SIZE = 25;

/**
 * All state, data-fetching and derived values behind
 * `EvaluationByFactorView`. Extracted (FE-041) so the view itself stays a
 * thin render/orchestrator layer — this hook owns methodology/factor
 * resolution, filters, the by-factor rows query, bulk selection, the
 * per-row score/comment handlers and the two bulk-action mutations.
 * Behaviour is unchanged; this is a structural move.
 */
export function useByFactorViewState({
  projectId,
  factorIdFromUrl,
  onFactorChange,
  methodologyIdFromUrl = null,
  onMethodologyChange,
  selfRole = null,
  blind = false,
}: UseByFactorViewStateArgs) {
  const { i18n } = useTranslation();
  const qc = useQueryClient();
  const { can } = usePermission();
  const canEdit = can(PERMISSIONS.EVALUATION_EDIT);
  /**
   * PART 1 — evaluator methodology scoping.
   * Managers/oversight (METHODOLOGY_READ) see all project methodologies.
   * Plain evaluators (no METHODOLOGY_READ) see only the ones they are
   * assigned to via GET /methodologies/my (BE derives the scope from the JWT).
   * Both hooks are called UNCONDITIONALLY to satisfy the Rules of Hooks;
   * only the enabled flag gates the actual network request.
   */
  const canMethodologyRead = can(PERMISSIONS.METHODOLOGY_READ);
  /**
   * Points-visibility exception (PO-ratified): plain expert evaluators must
   * judge positions by level DESCRIPTIONS only — surfacing the raw point
   * value anchors the score (anchoring bias). Project admins / HR directors
   * are exempt: they already hold `CALIBRATION_EDIT` (the manual-calibration
   * permission used by the calibration/approve flow — see CalibrationDialog
   * and EvaluationActionsBar), a role plain experts do NOT have. We reuse
   * that EXISTING permission rather than inventing a new code, derive the
   * boolean ONCE here, and thread it down to every place a level renders
   * (row control, open list, bulk dialog).
   */
  const canSeePoints = can(PERMISSIONS.CALIBRATION_EDIT);
  const setSidebarCollapsed = useAuthStore((s) => s.setSidebarCollapsed);

  // Auto-collapse the sidebar to icon-only mode while the by-factor grid is
  // mounted (the K-sheet needs the horizontal room). Restore on unmount so
  // other pages keep the user's default (expanded) layout.
  useEffect(() => {
    setSidebarCollapsed(true);
    return () => setSidebarCollapsed(false);
  }, [setSidebarCollapsed]);

  // ----- Filters (local; reset to page 0 when any filter changes) -----
  const [statusFilter, setStatusFilter] = useState<EvaluationStatus | ''>('');
  const [departmentId, setDepartmentId] = useState('');
  const [onlyUnfilled, setOnlyUnfilled] = useState(false);
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [activeRowId, setActiveRowId] = useState<string | null>(null);

  // Dialog open flags
  const [bulkScoreOpen, setBulkScoreOpen] = useState(false);
  const [bulkSubmitOpen, setBulkSubmitOpen] = useState(false);

  const changeStatusFilter = useCallback((value: EvaluationStatus | '') => {
    setStatusFilter(value);
    setPage(0);
  }, []);
  const changeDepartmentFilter = useCallback((value: string) => {
    setDepartmentId(value);
    setPage(0);
  }, []);
  const changeOnlyUnfilled = useCallback((value: boolean) => {
    setOnlyUnfilled(value);
    setPage(0);
  }, []);
  const changeSearch = useCallback((value: string) => {
    setSearch(value);
    setPage(0);
  }, []);

  // ----- Resolve selectable methodologies + the active selection -----
  // Both hooks called unconditionally (Rules of Hooks); only one actually
  // fetches based on the `enabled` flag derived from canMethodologyRead.
  const methodologiesQuery = useMethodologies(canMethodologyRead ? projectId : undefined);
  const myMethodologiesQuery = useMyMethodologies(
    !canMethodologyRead ? projectId : undefined,
    { enabled: !canMethodologyRead },
  );
  // Evaluations are reused (NO new endpoint) only to (a) compute a sensible
  // default selection — the methodology with the most evaluations — and (b)
  // decide which methodologies are worth offering in the selector. The
  // by-factor ROWS themselves still come from the scoped by-factor endpoint
  // (BE derives the version from factorId), never from this list.
  const evaluationsQuery = useEvaluations({ projectId });

  /**
   * The active query result to source methodologies from, based on the
   * caller's METHODOLOGY_READ permission. Both branches are always declared
   * above; we only pick which one feeds the downstream derived state.
   */
  const activeMethodologiesQuery = canMethodologyRead ? methodologiesQuery : myMethodologiesQuery;

  /**
   * Methodologies the user may switch between in the K-sheet: every ACTIVE
   * methodology that owns an active version. Ordered by the methodology
   * list itself (stable). A selector is only RENDERED when this has >1
   * entry — single-methodology projects keep the original chrome.
   *
   * ARCHIVED (deactivated) methodologies are excluded for EVERYONE here: the
   * evaluator path (`/methodologies/my`) already drops them server-side, but
   * the manager path (`useMethodologies`, full list) returns archived
   * containers too — without this filter a deactivated methodology (and, once
   * selected, its positions) would still appear on the scoring grid. Managers
   * still manage archived methodologies from the Methodology list ("show
   * inactive").
   */
  const selectableMethodologies = useMemo(
    () =>
      (activeMethodologiesQuery.data?.items ?? []).filter(
        (m) => m.active_version_id && m.status !== 'ARCHIVED',
      ),
    [activeMethodologiesQuery.data],
  );

  /**
   * version_id -> methodology, bridged via the active/latest version pointers
   * the enriched list response provides (mirrors EvaluationListPage's
   * `versionToMeth`). Lets us attribute each evaluation to its methodology so
   * the default selection can favour the one with the most evaluations.
   */
  const versionToMethodologyId = useMemo(() => {
    const m = new Map<string, string>();
    for (const meth of activeMethodologiesQuery.data?.items ?? []) {
      if (meth.active_version_id) m.set(meth.active_version_id, meth.id);
      if (meth.latest_version_id) m.set(meth.latest_version_id, meth.id);
    }
    return m;
  }, [activeMethodologiesQuery.data]);

  /** Default selection = methodology with the MOST non-archived evaluations. */
  const defaultMethodologyId = useMemo(() => {
    if (selectableMethodologies.length === 0) return null;
    const counts = new Map<string, number>();
    for (const e of evaluationsQuery.data?.items ?? []) {
      if (e.status === 'ARCHIVED') continue;
      const methId = versionToMethodologyId.get(e.methodology_version_id);
      if (!methId) continue;
      counts.set(methId, (counts.get(methId) ?? 0) + 1);
    }
    let best = selectableMethodologies[0];
    let bestCount = counts.get(best.id) ?? 0;
    for (const m of selectableMethodologies) {
      const c = counts.get(m.id) ?? 0;
      if (c > bestCount) {
        best = m;
        bestCount = c;
      }
    }
    return best.id;
  }, [selectableMethodologies, evaluationsQuery.data, versionToMethodologyId]);

  /**
   * The methodology actually driving the K-sheet. The URL value wins when it
   * still maps to a selectable methodology (so a refresh / share keeps the
   * choice); otherwise we fall back to the data-driven default.
   */
  const activeMethodology = useMemo(() => {
    if (selectableMethodologies.length === 0) return null;
    const byUrl = methodologyIdFromUrl
      ? selectableMethodologies.find((m) => m.id === methodologyIdFromUrl)
      : null;
    return (
      byUrl ??
      selectableMethodologies.find((m) => m.id === defaultMethodologyId) ??
      selectableMethodologies[0]
    );
  }, [selectableMethodologies, methodologyIdFromUrl, defaultMethodologyId]);

  const versionQuery = useMethodologyVersion(
    activeMethodology?.active_version_id ?? undefined,
  );
  const factors = useMemo(
    () =>
      [...(versionQuery.data?.factors ?? [])].sort(
        (a, b) => a.sort_order - b.sort_order,
      ),
    [versionQuery.data],
  );

  // ----- Resolve active factor -----
  const activeFactor = useMemo(() => {
    if (factors.length === 0) return null;
    const byUrl = factorIdFromUrl
      ? factors.find((f) => f.id === factorIdFromUrl || f.code === factorIdFromUrl)
      : null;
    return byUrl ?? factors[0];
  }, [factors, factorIdFromUrl]);

  // Push the canonical methodology id back to URL on first auto-pick so a
  // refresh keeps the choice AND the parent's Add-positions dialog defaults
  // to the same version. Only fires when the URL value does not already
  // resolve to the active methodology (avoids an update loop).
  useEffect(() => {
    if (
      activeMethodology &&
      onMethodologyChange &&
      methodologyIdFromUrl !== activeMethodology.id
    ) {
      onMethodologyChange(activeMethodology.id);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeMethodology?.id]);

  // Push the canonical factor id back to URL on first auto-pick so a
  // refresh keeps the same tab. Avoid an infinite loop by only firing
  // when the URL value does not already match.
  useEffect(() => {
    if (activeFactor && factorIdFromUrl !== activeFactor.id) {
      onFactorChange(activeFactor.id);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeFactor?.id]);

  // Explicit user selection from the header selector. Pushing the new
  // methodology id to the URL re-derives factors → tabs → activeFactor →
  // rows request for the SELECTED version (BE scopes rows by the factor's
  // own version). The factor param is intentionally NOT preserved by the
  // parent on a methodology switch so a stale (other-version) factor never
  // leaks into the new view.
  const handleMethodologyChange = useCallback(
    (methodologyId: string) => {
      onMethodologyChange?.(methodologyId);
    },
    [onMethodologyChange],
  );

  // ----- Department list for filter -----
  // `fetchDepartmentTree` returns Department[] (already unwrapped — see
  // organizationApi.ts) so the data shape is a flat array, not an envelope.
  const treeQuery = useDepartmentTree(projectId);
  const departmentOptions = useMemo(
    () =>
      (treeQuery.data ?? []).map((d) => ({
        id: d.id,
        label: pickLocalized(d.name_i18n, i18n.language),
      })),
    [treeQuery.data, i18n.language],
  );

  // ----- Server query for the K-sheet rows -----
  const filters: EvaluationsByFactorFilters = {
    projectId,
    factorId: activeFactor?.id ?? '',
    status: statusFilter,
    departmentId,
    onlyUnfilled,
    search: search.trim() || undefined,
    page,
    size: BY_FACTOR_PAGE_SIZE,
  };
  const rowsQuery = useEvaluationsByFactor(filters);

  const rows: EvaluationByFactorRow[] = useMemo(
    () => rowsQuery.data?.items ?? [],
    [rowsQuery.data],
  );
  const totalElements = rowsQuery.data?.total_elements ?? 0;
  const totalPages = rowsQuery.data?.total_pages ?? 1;

  const {
    selected: bulkSet,
    toggle: toggleRow,
    toggleMany: toggleManyBulk,
    clear: clearBulkSet,
  } = useSelectionSet(rows, (r) => r.evaluation_id);

  // ----- Per-row mutations -----
  const bulkScoreMutation = useBulkScoreSet(activeFactor?.id ?? '');
  const bulkSubmitMutation = useBulkSubmit(activeFactor?.id ?? '');

  // Reset bulk selection / active row / page when the factor changes (per-factor
  // state). Done during render via a previous-id ref (React's "adjust state when
  // a prop changes" pattern) instead of a synchronous setState-in-effect.
  const prevFactorIdRef = useRef(activeFactor?.id ?? null);
  if (prevFactorIdRef.current !== (activeFactor?.id ?? null)) {
    prevFactorIdRef.current = activeFactor?.id ?? null;
    clearBulkSet();
    setActiveRowId(null);
    setPage(0);
  }

  // ----- Factor-level completion summary (for tab indicators) -----
  // Aggregated from CURRENT PAGE only — the parent does not have a
  // project-wide aggregate endpoint in MVP 1; a future iteration can
  // expand this via a dedicated `/by-factor-completion` summary.
  const completionMap: FactorCompletionMap = useMemo(() => {
    const map: FactorCompletionMap = {};
    if (!activeFactor) return map;
    if (rows.length === 0) {
      map[activeFactor.id] = 'empty';
      return map;
    }
    const filledRows = rows.filter((r) => r.current_score_factor_level_id).length;
    if (filledRows === 0) map[activeFactor.id] = 'empty';
    else if (filledRows === rows.length) map[activeFactor.id] = 'full';
    else map[activeFactor.id] = 'partial';
    // Other factors: derive a rough estimate from filled_factors_count per row.
    for (const f of factors) {
      if (f.id === activeFactor.id) continue;
      // We don't have per-other-factor truth — leave undefined for honesty.
    }
    return map;
  }, [rows, factors, activeFactor]);

  // ----- Inline score change for a single row -----
  const handleScoreChange = useCallback(
    async (row: EvaluationByFactorRow, factorLevelId: string) => {
      if (!activeFactor) return;
      await upsertScore(row.evaluation_id, {
        factor_id: activeFactor.id,
        factor_level_id: factorLevelId,
      });
      // Invalidate the by-factor list so the row reflects the saved value
      // and the progress chip recomputes. Per-evaluation cache is
      // refreshed indirectly via the `evaluations.all` parent key.
      qc.invalidateQueries({ queryKey: evaluationKeys.all });
    },
    [activeFactor, qc],
  );

  const handleCommentChange = useCallback(
    async (row: EvaluationByFactorRow, comment: string) => {
      if (!activeFactor) return;
      // Comments require a level — skip when none.
      if (!row.current_score_factor_level_id) return;
      await upsertScore(row.evaluation_id, {
        factor_id: activeFactor.id,
        factor_level_id: row.current_score_factor_level_id,
        comment,
      });
      qc.invalidateQueries({ queryKey: evaluationKeys.all });
    },
    [activeFactor, qc],
  );

  // ----- Bulk selection helpers -----
  const allRowIds = useMemo(() => rows.map((r) => r.evaluation_id), [rows]);
  const allSelected =
    bulkSet.size > 0 && allRowIds.every((id) => bulkSet.has(id));
  const toggleAll = () => toggleManyBulk(allRowIds);

  // The active (row-clicked) row — drives the subtle row highlight only.
  // The rubric panel that previously consumed it has been retired; the
  // highlight is a low-cost focus cue retained from the original UX.
  const activeRow = useMemo(
    () => rows.find((r) => r.evaluation_id === activeRowId) ?? rows[0] ?? null,
    [rows, activeRowId],
  );

  const methodologyName = activeMethodology
    ? pickLocalized(activeMethodology.name_i18n, i18n.language) || activeMethodology.code
    : '';
  const scoringMode = versionQuery.data?.scoring_mode;

  return {
    // Permissions / role chrome
    canEdit,
    canMethodologyRead,
    canSeePoints,
    selfRole,
    blind,

    // Loading / empty gating (mirrors the original early-return branches)
    activeMethodologiesQuery,
    versionQuery,
    selectableMethodologies,

    // Header
    activeMethodology,
    methodologyName,
    scoringMode,
    handleMethodologyChange,
    factors,
    activeFactor,
    completionMap,
    onFactorChange,

    // Filter bar
    departmentOptions,
    statusFilter,
    changeStatusFilter,
    departmentId,
    changeDepartmentFilter,
    onlyUnfilled,
    changeOnlyUnfilled,
    search,
    changeSearch,

    // Table
    rowsQuery,
    rows,
    activeRow,
    bulkSet,
    toggleRow,
    setActiveRowId,
    allSelected,
    toggleAll,
    handleScoreChange,
    handleCommentChange,

    // Toolbar
    totalElements,
    page,
    totalPages,
    setPage,
    bulkScoreMutation,
    bulkSubmitMutation,
    bulkScoreOpen,
    setBulkScoreOpen,
    bulkSubmitOpen,
    setBulkSubmitOpen,
    clearBulkSet,
  };
}
