import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuthStore } from '@/features/auth/authStore';
import { usePermission } from '@/features/auth/usePermission';
import { PERMISSIONS } from '@/shared/types/permissions';
import { ApiError } from '@/shared/api/apiError';
import { useTranslation } from 'react-i18next';
import {
  useAddFactor,
  useAddFactorLevel,
  useApproveVersion,
  useArchiveVersion,
  useCreateNewVersion,
  useMethodology,
  useMethodologyVersion,
  useMethodologyVersions,
  useRemoveFactor,
  useRemoveFactorLevel,
  useReorderFactorLevels,
  useReorderFactors,
  useSaveMethodologyAsTemplate,
  useUpdateFactor,
  useUpdateFactorLevel,
  useUpdateMethodology,
  useUpdateMethodologyVersionMetadata,
} from '../hooks/useMethodology';
import type { MethodologyMetadataPatch } from '../components/MethodologyMetadataDrawer';
import type { Locale } from '@/shared/types/common';
import type { Factor, FactorLevel, SaveAsTemplatePayload } from '../types';

/**
 * All state, mutations and handlers behind `MethodologyBuilderPage`.
 * Extracted (FE-041) so the page itself stays a thin render/orchestrator
 * layer — this hook owns the factor/level editor state, every lifecycle
 * mutation (approve/archive/new-version/save-as-template/metadata), the
 * approved-edit first-action confirm gate, and the transient success/notice
 * banners. Behaviour is unchanged; this is a structural move.
 */
export function useMethodologyBuilderState() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const { projectId = '', methodologyId = '', versionId = '' } = useParams<{
    projectId: string;
    methodologyId: string;
    versionId: string;
  }>();
  const currentLocale = (useAuthStore((s) => s.user?.locale) ?? (i18n.language as Locale)) as Locale;
  const { can } = usePermission();

  const methodologyQuery = useMethodology(methodologyId);
  const versionQuery = useMethodologyVersion(versionId);
  const versionsQuery = useMethodologyVersions(methodologyId);

  const methodology = methodologyQuery.data;
  const version = versionQuery.data;
  // fetchMethodologyVersions now returns a bare array (real-backend contract).
  const versions = versionsQuery.data;

  const factors = useMemo(() => version?.factors ?? [], [version]);

  // Editor state.
  //
  // We track the OPEN factor by ID, not by a captured `Factor` snapshot. The
  // open factor is then DERIVED from the live `factors` list (refreshed by the
  // mutation invalidations). The previous snapshot approach (EPIC-A bug) froze
  // `editorFactor.levels` at open time: a level add/update PATCHed + refetched
  // the version server-side, but the drawer kept rendering the stale snapshot,
  // so the saved level was invisible until the drawer was reopened.
  const [editorFactorId, setEditorFactorId] = useState<string | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [approveOpen, setApproveOpen] = useState(false);
  const [archiveOpen, setArchiveOpen] = useState(false);
  const [newVersionOpen, setNewVersionOpen] = useState(false);
  // Epic E — save this methodology's version as a reusable CUSTOM template.
  const [saveTemplateOpen, setSaveTemplateOpen] = useState(false);
  const [templateSuccess, setTemplateSuccess] = useState<string | null>(null);
  // Edit methodology/version metadata (name/description/type + scoring/target).
  const [metadataOpen, setMetadataOpen] = useState(false);
  // FE-1 — approved-version edit (HRLAB_SUPER_ADMIN only). A confirm gates the
  // FIRST edit action; once acknowledged for this page session, edits flow.
  const [approvedEditConfirmOpen, setApprovedEditConfirmOpen] = useState(false);
  const approvedEditAckRef = useRef(false);
  // Pending edit action deferred behind the first-edit confirm gate.
  const [pendingEditAction, setPendingEditAction] = useState<(() => void) | null>(null);
  // FE-2 — inline notice when a referenced factor/level was soft-deprecated
  // (kept for historical evaluations) instead of hard-deleted.
  const [deprecateNotice, setDeprecateNotice] = useState<string | null>(null);
  // FE-049 — remove-factor / remove-level confirmation (replaces window.confirm
  // with the shared ConfirmDialog). Non-null target = the dialog is open.
  const [removeFactorTarget, setRemoveFactorTarget] = useState<Factor | null>(null);
  const [removeLevelTarget, setRemoveLevelTarget] = useState<FactorLevel | null>(null);

  const editorFactor = useMemo(
    () => factors.find((f) => f.id === editorFactorId) ?? null,
    [factors, editorFactorId],
  );

  // Mutations
  const addFactorMut = useAddFactor(versionId, methodologyId);
  const updateFactorMut = useUpdateFactor(editorFactorId ?? '', versionId, methodologyId);
  const removeFactorMut = useRemoveFactor(editorFactorId ?? '', versionId, methodologyId);
  const reorderMut = useReorderFactors(versionId, methodologyId);
  const addLevelMut = useAddFactorLevel(versionId, methodologyId);
  const updateLevelMut = useUpdateFactorLevel(versionId, methodologyId);
  const removeLevelMut = useRemoveFactorLevel(versionId, methodologyId);
  // Level reorder targets the OPEN factor — the factorId is the editor factor
  // (the only context in which a level row's arrows are visible).
  const reorderLevelMut = useReorderFactorLevels(editorFactorId ?? '', versionId, methodologyId);

  const approveMut = useApproveVersion(versionId, methodologyId, projectId);
  const archiveMut = useArchiveVersion(versionId, methodologyId, projectId);
  const newVersionMut = useCreateNewVersion(methodologyId, projectId);
  const saveTemplateMut = useSaveMethodologyAsTemplate(methodologyId);
  // Metadata edit — container (name/description/type) + version (scoring/target).
  const updateMethodologyMut = useUpdateMethodology(methodologyId, projectId);
  const updateVersionMetadataMut = useUpdateMethodologyVersionMetadata(
    versionId,
    methodologyId,
    projectId,
  );

  // Auto-dismiss the "saved as template" banner like a transient toast.
  useEffect(() => {
    if (!templateSuccess) return;
    const id = window.setTimeout(() => setTemplateSuccess(null), 5000);
    return () => window.clearTimeout(id);
  }, [templateSuccess]);

  // Auto-dismiss the deprecate-outcome notice (FE-2) like a transient toast.
  useEffect(() => {
    if (!deprecateNotice) return;
    const id = window.setTimeout(() => setDeprecateNotice(null), 8000);
    return () => window.clearTimeout(id);
  }, [deprecateNotice]);

  // Approved-edit mode: HRLAB_SUPER_ADMIN editing an APPROVED version. The
  // backend accepts METHODOLOGY_EDIT_APPROVED on the factor/level write
  // endpoints for APPROVED versions and preserves existing evaluations
  // byte-for-byte. LOCKED/ARCHIVED stay immutable for EVERYONE (no carve-out).
  const approvedEditMode =
    !!version &&
    version.status === 'APPROVED' &&
    can(PERMISSIONS.METHODOLOGY_EDIT_APPROVED);

  const readOnly =
    !version || (version.status !== 'DRAFT' && !approvedEditMode);

  // First-edit confirm gate (FE-1): in approved-edit mode the first mutating
  // action is intercepted and routed through a ConfirmDialog. After the user
  // acknowledges, edits flow for the rest of the page session. DRAFT editing is
  // unchanged (no gate). Returns true when the action was deferred.
  //
  // A ref mirrors the acknowledged flag so the DEFERRED action closure (created
  // during the pre-ack render) sees the up-to-date value when the confirm runs
  // it — otherwise the closure would re-defer against the stale `false`.
  const guardApprovedEdit = (action: () => void): boolean => {
    if (approvedEditMode && !approvedEditAckRef.current) {
      setPendingEditAction(() => action);
      setApprovedEditConfirmOpen(true);
      return true;
    }
    return false;
  };

  const target =
    version && version.scoring_mode === 'WEIGHTED_POINTS'
      ? 100
      : version?.target_total_points ?? 100;

  const handleCreateNewVersion = async () => {
    setNewVersionOpen(false);
    if (!version) return;
    const created = await newVersionMut.mutateAsync(version.id);
    navigate(
      `/app/projects/${projectId}/methodology/${methodologyId}/versions/${created.id}/edit`,
    );
  };

  // Epic E — snapshot this methodology into a reusable CUSTOM template. The
  // drawer surfaces a 409 duplicate-code conflict inline (it re-throws on error).
  const handleSaveAsTemplate = async (payload: SaveAsTemplatePayload) => {
    await saveTemplateMut.mutateAsync(payload);
    setSaveTemplateOpen(false);
    setTemplateSuccess(t('methodology.save_as_template.success', { code: payload.code }));
  };

  const handleApproveConfirm = async () => {
    setApproveOpen(false);
    await approveMut.mutateAsync();
  };

  const handleArchiveConfirm = async (reason: string) => {
    setArchiveOpen(false);
    await archiveMut.mutateAsync({ reason });
  };

  // Cancel the first-edit confirm gate — drop the deferred action.
  const handleApprovedEditCancel = () => {
    setApprovedEditConfirmOpen(false);
    setPendingEditAction(null);
  };

  // Acknowledge the first-edit confirm gate — flip the ack ref so subsequent
  // edits flow without re-prompting, then run the deferred action.
  const handleApprovedEditConfirm = () => {
    setApprovedEditConfirmOpen(false);
    approvedEditAckRef.current = true;
    const action = pendingEditAction;
    setPendingEditAction(null);
    action?.();
  };

  const handleEditFactor = (f: Factor) => {
    if (guardApprovedEdit(() => handleEditFactor(f))) return;
    setEditorFactorId(f.id);
    setEditorOpen(true);
  };

  const handleNewFactor = () => {
    if (guardApprovedEdit(() => handleNewFactor())) return;
    setEditorFactorId(null);
    setEditorOpen(true);
  };

  const handleCloseEditor = () => {
    setEditorOpen(false);
    setEditorFactorId(null);
  };

  const handleFactorSubmit = async (patch: {
    code: string;
    name_i18n: import('@/shared/types/common').LocalizedString;
    description_i18n?: import('@/shared/types/common').LocalizedString;
    weight: number;
    max_points: number;
    required: boolean;
  }) => {
    if (editorFactorId) {
      await updateFactorMut.mutateAsync(patch);
      setEditorOpen(false);
      setEditorFactorId(null);
    } else {
      // Create the factor, then keep the drawer OPEN and switch it to the new
      // factor so the level editor section appears in place — the user can add
      // levels without re-opening (EPIC-A/F1). The returned id binds the derived
      // `editorFactor` once the version refetch lands.
      const created = await addFactorMut.mutateAsync({
        ...patch,
        sort_order: factors.length,
      });
      setEditorFactorId(created.id);
    }
  };

  // removeFactorMut is bound to editorFactorId; point it at the row being
  // removed first so the right factor id is deleted.
  //
  // FE-2: on an APPROVED version a factor referenced by existing evaluations is
  // SOFT-DEPRECATED by the backend (kept for historical reads), not deleted.
  // The backend signals a refused hard-delete with the domain code
  // FACTOR_REFERENCED_BY_EVALUATIONS; either way the user sees a clear,
  // non-alarming explanation and the row stays (now with a "deprecated" badge
  // after the version refetch).
  const handleRemoveFactor = (f: Factor) => {
    if (guardApprovedEdit(() => handleRemoveFactor(f))) return;
    setRemoveFactorTarget(f);
  };

  const confirmRemoveFactor = async () => {
    const f = removeFactorTarget;
    if (!f) return;
    setRemoveFactorTarget(null);
    setEditorFactorId(f.id);
    try {
      await removeFactorMut.mutateAsync();
      if (approvedEditMode) {
        setDeprecateNotice(t('methodology.deprecate.factor_deprecated'));
      }
    } catch (e) {
      if (e instanceof ApiError && e.code === 'FACTOR_REFERENCED_BY_EVALUATIONS') {
        setDeprecateNotice(t('methodology.deprecate.factor_referenced'));
      } else {
        throw e;
      }
    } finally {
      setEditorFactorId(null);
    }
  };

  const handleReorder = async (f: Factor, direction: 'up' | 'down') => {
    const sorted = [...factors].sort((a, b) => a.sort_order - b.sort_order);
    const idx = sorted.findIndex((x) => x.id === f.id);
    const swapIdx = direction === 'up' ? idx - 1 : idx + 1;
    if (swapIdx < 0 || swapIdx >= sorted.length) return;
    const next = [...sorted];
    [next[idx], next[swapIdx]] = [next[swapIdx], next[idx]];
    await reorderMut.mutateAsync({ ordered_ids: next.map((x) => x.id) });
  };

  // Level CRUD wired through the editor (needs the editor factor's id).
  // The factor id travels with the mutate VARS (F2) so a single hook instance
  // targets the right factor even when `editorFactorId` was set in the same
  // tick (no stale empty-string closure no-op).
  const handleAddLevel = async (next: Omit<FactorLevel, 'id' | 'factor_id'>) => {
    if (!editorFactorId) return;
    await addLevelMut.mutateAsync({ factorId: editorFactorId, payload: next });
  };

  const handleUpdateLevel = async (lvl: FactorLevel) => {
    // Pass the real level id with the call so the PATCH targets the right
    // row. Errors propagate to the editor (toast) — never swallowed.
    await updateLevelMut.mutateAsync({ levelId: lvl.id, payload: lvl });
  };

  const handleRemoveLevel = (lvl: FactorLevel) => {
    setRemoveLevelTarget(lvl);
  };

  const confirmRemoveLevel = async () => {
    const lvl = removeLevelTarget;
    if (!lvl) return;
    setRemoveLevelTarget(null);
    try {
      await removeLevelMut.mutateAsync(lvl.id);
      if (approvedEditMode) {
        setDeprecateNotice(t('methodology.deprecate.level_deprecated'));
      }
    } catch (e) {
      if (e instanceof ApiError && e.code === 'LEVEL_REFERENCED_BY_EVALUATIONS') {
        setDeprecateNotice(t('methodology.deprecate.level_referenced'));
      } else {
        throw e;
      }
    }
  };

  // Level reorder (ISSUE 1a) — mirrors handleReorder for factors but on the OPEN
  // factor's levels. Sort by level_order, swap with the neighbour, then send the
  // COMPLETE re-indexed order to POST /factors/{id}/levels/reorder. The hook is
  // bound to editorFactorId so a single instance targets the open factor.
  const handleReorderLevel = async (lvl: FactorLevel, direction: 'up' | 'down') => {
    if (!editorFactor) return;
    const sorted = [...editorFactor.levels].sort((a, b) => a.level_order - b.level_order);
    const idx = sorted.findIndex((x) => x.id === lvl.id);
    const swapIdx = direction === 'up' ? idx - 1 : idx + 1;
    if (swapIdx < 0 || swapIdx >= sorted.length) return;
    const next = [...sorted];
    [next[idx], next[swapIdx]] = [next[swapIdx], next[idx]];
    await reorderLevelMut.mutateAsync({ ordered_ids: next.map((x) => x.id) });
  };

  // Metadata edit (ISSUE 2) — sequence the two distinct PATCH surfaces:
  //   (a) container: name/description (+ methodology_type) -> PATCH /methodologies/{id}
  //   (b) version:   scoring_mode (+ target_total_points)  -> PATCH /methodology-versions/{id}
  // Container first so a METHODOLOGY_TYPE_LOCKED conflict aborts before any
  // version change. Errors re-throw so the drawer surfaces them inline; the
  // drawer closes only on success.
  const handleMetadataSubmit = async (patch: MethodologyMetadataPatch) => {
    await updateMethodologyMut.mutateAsync(patch.methodology);
    if (patch.version) {
      await updateVersionMetadataMut.mutateAsync(patch.version);
    }
    setMetadataOpen(false);
  };

  const navigateToTranslations = () => {
    navigate(
      `/app/projects/${projectId}/methodology/${methodologyId}/versions/${versionId}/translations`,
    );
  };

  const navigateToVersion = (vid: string) => {
    navigate(
      `/app/projects/${projectId}/methodology/${methodologyId}/versions/${vid}/edit`,
    );
  };

  return {
    // Route params
    projectId,
    methodologyId,
    versionId,
    currentLocale,

    // Queries / loading-error gating
    methodologyQuery,
    versionQuery,
    versionsQuery,
    methodology,
    version,
    versions,
    factors,
    target,
    readOnly,
    approvedEditMode,

    // Editor state
    editorFactor,
    editorOpen,
    handleEditFactor,
    handleNewFactor,
    handleCloseEditor,
    handleFactorSubmit,
    handleRemoveFactor,
    handleReorder,
    handleAddLevel,
    handleUpdateLevel,
    handleRemoveLevel,
    handleReorderLevel,

    // Lifecycle dialogs
    approveOpen,
    setApproveOpen,
    handleApproveConfirm,
    archiveOpen,
    setArchiveOpen,
    handleArchiveConfirm,
    newVersionOpen,
    setNewVersionOpen,
    handleCreateNewVersion,

    // Remove-factor / remove-level confirmation
    removeFactorTarget,
    setRemoveFactorTarget,
    confirmRemoveFactor,
    removeLevelTarget,
    setRemoveLevelTarget,
    confirmRemoveLevel,

    // Approved-edit first-action confirm gate
    approvedEditConfirmOpen,
    handleApprovedEditCancel,
    handleApprovedEditConfirm,

    // Notices
    deprecateNotice,
    setDeprecateNotice,
    templateSuccess,

    // Save-as-template
    saveTemplateOpen,
    setSaveTemplateOpen,
    handleSaveAsTemplate,

    // Metadata edit
    metadataOpen,
    setMetadataOpen,
    handleMetadataSubmit,

    // Navigation helpers
    navigateToTranslations,
    navigateToVersion,
  };
}
