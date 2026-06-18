/**
 * React Query hooks for methodology + versions + factors + levels.
 * All mutations invalidate the version detail + version summary list so
 * the locked/approved banner and factor table re-render in lockstep.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  addFactor,
  addFactorLevel,
  approveVersion,
  archiveCustomTemplate,
  archiveMethodology,
  archiveVersion,
  createMethodology,
  createMethodologyFromTemplate,
  createNewVersion,
  fetchInProgressCount,
  fetchMethodologies,
  fetchMethodology,
  fetchMethodologyTemplates,
  fetchMethodologyVersion,
  fetchMethodologyVersions,
  fetchMyMethodologies,
  lockVersion,
  methodologyKeys,
  removeFactor,
  removeFactorLevel,
  reorderFactorLevels,
  reorderFactors,
  restoreMethodology,
  saveMethodologyAsTemplate,
  updateCustomTemplate,
  updateFactor,
  updateFactorLevel,
  updateMethodology,
  updateMethodologyVersionMetadata,
} from '../api/methodologyApi';
import type {
  FactorCreatePayload,
  FactorLevelCreatePayload,
  FactorLevelUpdatePayload,
  FactorUpdatePayload,
  LevelReorderPayload,
  MethodologyCreatePayload,
  MethodologyReasonPayload,
  MethodologyUpdatePayload,
  MethodologyVersionMetadataUpdatePayload,
  ReorderPayload,
  SaveAsTemplatePayload,
  UpdateTemplatePayload,
} from '../types';

// ---------- Queries ----------

export function useMethodologies(projectId: string | undefined) {
  return useQuery({
    queryKey: projectId ? methodologyKeys.listByProject(projectId) : ['methodologies', 'list', null],
    queryFn: () => fetchMethodologies(projectId!),
    enabled: !!projectId,
  });
}

/**
 * Fetch ONLY the methodologies the caller is assigned to (has own evaluations
 * in) whose status = ACTIVE. Used in the by-factor K-sheet for evaluators who
 * do not hold METHODOLOGY_READ. `enabled` is intentionally a prop so the
 * caller can unconditionally invoke both this hook and useMethodologies, then
 * pick which result feeds the dropdown based on the user's permission — this
 * satisfies the Rules of Hooks requirement that hooks are called unconditionally.
 */
export function useMyMethodologies(projectId: string | undefined, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: projectId ? methodologyKeys.myList(projectId) : ['methodologies', 'my', null],
    queryFn: () => fetchMyMethodologies(projectId!),
    enabled: !!projectId && (options?.enabled !== false),
  });
}

export function useRestoreMethodology(id: string, projectId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: MethodologyReasonPayload) => restoreMethodology(id, payload),
    onSuccess: () => invalidateMethodology(qc, projectId, id),
  });
}

/**
 * Lazy query for in-progress evaluation count — only fetches when a dialog
 * is open (enabled by the caller). Used in the deactivate confirmation dialog.
 */
export function useInProgressCount(id: string | undefined, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: id ? methodologyKeys.inProgressCount(id) : ['methodologies', 'in-progress-count', null],
    queryFn: () => fetchInProgressCount(id!),
    enabled: !!id && (options?.enabled !== false),
    staleTime: 0, // always fresh when the dialog opens
  });
}

export function useMethodology(id: string | undefined) {
  return useQuery({
    queryKey: id ? methodologyKeys.detail(id) : ['methodologies', 'detail', null],
    queryFn: () => fetchMethodology(id!),
    enabled: !!id,
  });
}

export function useMethodologyVersions(methodologyId: string | undefined) {
  return useQuery({
    queryKey: methodologyId
      ? methodologyKeys.versions(methodologyId)
      : ['methodologies', 'versions', null],
    queryFn: () => fetchMethodologyVersions(methodologyId!),
    enabled: !!methodologyId,
  });
}

export function useMethodologyVersion(versionId: string | undefined) {
  return useQuery({
    queryKey: versionId ? methodologyKeys.version(versionId) : ['methodology-versions', 'detail', null],
    queryFn: () => fetchMethodologyVersion(versionId!),
    enabled: !!versionId,
  });
}

export function useMethodologyTemplates() {
  return useQuery({
    queryKey: methodologyKeys.templates,
    queryFn: () => fetchMethodologyTemplates(),
    // Templates are global and rarely change — long staleTime is safe.
    staleTime: 5 * 60_000,
  });
}

// ---------- Methodology mutations ----------

function invalidateMethodology(
  qc: ReturnType<typeof useQueryClient>,
  projectId?: string,
  methodologyId?: string,
  versionId?: string,
) {
  qc.invalidateQueries({ queryKey: methodologyKeys.all });
  if (projectId) qc.invalidateQueries({ queryKey: methodologyKeys.listByProject(projectId) });
  if (methodologyId) {
    qc.invalidateQueries({ queryKey: methodologyKeys.detail(methodologyId) });
    qc.invalidateQueries({ queryKey: methodologyKeys.versions(methodologyId) });
  }
  if (versionId) qc.invalidateQueries({ queryKey: methodologyKeys.version(versionId) });
}

export function useCreateMethodology(projectId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: MethodologyCreatePayload) => createMethodology(payload),
    onSuccess: (m) => invalidateMethodology(qc, projectId ?? m.project_id, m.id),
  });
}

export function useCreateMethodologyFromTemplate(projectId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: MethodologyCreatePayload) => createMethodologyFromTemplate(payload),
    onSuccess: (m) => invalidateMethodology(qc, projectId ?? m.project_id, m.id),
  });
}

export function useUpdateMethodology(id: string, projectId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: MethodologyUpdatePayload) => updateMethodology(id, payload),
    onSuccess: () => invalidateMethodology(qc, projectId, id),
  });
}

export function useArchiveMethodology(id: string, projectId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: MethodologyReasonPayload) => archiveMethodology(id, payload),
    onSuccess: () => invalidateMethodology(qc, projectId, id),
  });
}

// ---------- Template mutations (Epic E) ----------

function invalidateTemplates(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: methodologyKeys.templates });
}

/**
 * "Save as template" — snapshot a methodology into a CUSTOM template. Invalidates
 * the templates catalog so the new template shows up in the picker / manager.
 * 409 errors (duplicate code) propagate to the caller for inline handling.
 */
export function useSaveMethodologyAsTemplate(methodologyId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: SaveAsTemplatePayload) =>
      saveMethodologyAsTemplate(methodologyId, payload),
    onSuccess: () => invalidateTemplates(qc),
  });
}

/** Rename a CUSTOM template (name/description). Invalidates the catalog. */
export function useUpdateCustomTemplate(templateId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdateTemplatePayload) => updateCustomTemplate(templateId, payload),
    onSuccess: () => invalidateTemplates(qc),
  });
}

/** Archive a CUSTOM template (removes it from the picker). Invalidates the catalog. */
export function useArchiveCustomTemplate(templateId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => archiveCustomTemplate(templateId),
    onSuccess: () => invalidateTemplates(qc),
  });
}

// ---------- Version mutations ----------

export function useApproveVersion(versionId: string, methodologyId?: string, projectId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => approveVersion(versionId),
    onSuccess: () => invalidateMethodology(qc, projectId, methodologyId, versionId),
  });
}

export function useLockVersion(versionId: string, methodologyId?: string, projectId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => lockVersion(versionId),
    onSuccess: () => invalidateMethodology(qc, projectId, methodologyId, versionId),
  });
}

export function useArchiveVersion(versionId: string, methodologyId?: string, projectId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: MethodologyReasonPayload) => archiveVersion(versionId, payload),
    onSuccess: () => invalidateMethodology(qc, projectId, methodologyId, versionId),
  });
}

export function useCreateNewVersion(methodologyId: string, projectId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (sourceVersionId: string) => createNewVersion(methodologyId, sourceVersionId),
    onSuccess: (v) => invalidateMethodology(qc, projectId, methodologyId, v.id),
  });
}

/**
 * PATCH version-level scoring metadata (scoring_mode + target_total_points).
 * Reuses the shared `invalidateMethodology` helper so the version detail (and
 * thus the FactorEditor weight field / WeightSumVisualizer / ScoringModeBadge,
 * all keyed off `scoring_mode`) refetch after a save. Errors propagate to the
 * caller so the drawer can surface SCORING_TARGET_REQUIRED inline.
 */
export function useUpdateMethodologyVersionMetadata(
  versionId: string,
  methodologyId?: string,
  projectId?: string,
) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: MethodologyVersionMetadataUpdatePayload) =>
      updateMethodologyVersionMetadata(versionId, payload),
    onSuccess: () => invalidateMethodology(qc, projectId, methodologyId, versionId),
  });
}

// ---------- Factor mutations ----------

export function useAddFactor(versionId: string, methodologyId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: FactorCreatePayload) => addFactor(versionId, payload),
    onSuccess: () => invalidateMethodology(qc, undefined, methodologyId, versionId),
  });
}

export function useUpdateFactor(factorId: string, versionId?: string, methodologyId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: FactorUpdatePayload) => updateFactor(factorId, payload),
    onSuccess: () => invalidateMethodology(qc, undefined, methodologyId, versionId),
  });
}

export function useRemoveFactor(factorId: string, versionId?: string, methodologyId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => removeFactor(factorId),
    onSuccess: () => invalidateMethodology(qc, undefined, methodologyId, versionId),
  });
}

export function useReorderFactors(versionId: string, methodologyId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: ReorderPayload) => reorderFactors(versionId, payload),
    onSuccess: () => invalidateMethodology(qc, undefined, methodologyId, versionId),
  });
}

// ---------- Factor level mutations ----------

export function useAddFactorLevel(versionId?: string, methodologyId?: string) {
  const qc = useQueryClient();
  return useMutation({
    // factorId travels with the mutate call (not the hook closure) so a single
    // hook instance can add a level to ANY factor row — mirrors the
    // useUpdateFactorLevel pattern. The previous empty-string-closure id
    // (set from a stale `editorFactor?.id ?? ''`) silently no-op'd level adds
    // whenever the editor factor wasn't yet synced (bug fix EPIC-A/F2).
    mutationFn: (vars: { factorId: string; payload: FactorLevelCreatePayload }) =>
      addFactorLevel(vars.factorId, vars.payload),
    onSuccess: () => invalidateMethodology(qc, undefined, methodologyId, versionId),
  });
}

export function useUpdateFactorLevel(versionId?: string, methodologyId?: string) {
  const qc = useQueryClient();
  return useMutation({
    // levelId travels with the mutate call (not the hook closure) so a single
    // hook instance can patch ANY level row — the previous empty-string id
    // closure silently no-op'd every level edit (bug fix MVP1-METH-LVL).
    mutationFn: (vars: { levelId: string; payload: FactorLevelUpdatePayload }) =>
      updateFactorLevel(vars.levelId, vars.payload),
    onSuccess: () => invalidateMethodology(qc, undefined, methodologyId, versionId),
  });
}

export function useRemoveFactorLevel(versionId?: string, methodologyId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (levelId: string) => removeFactorLevel(levelId),
    onSuccess: () => invalidateMethodology(qc, undefined, methodologyId, versionId),
  });
}

export function useReorderFactorLevels(factorId: string, versionId?: string, methodologyId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: LevelReorderPayload) => reorderFactorLevels(factorId, payload),
    onSuccess: () => invalidateMethodology(qc, undefined, methodologyId, versionId),
  });
}
