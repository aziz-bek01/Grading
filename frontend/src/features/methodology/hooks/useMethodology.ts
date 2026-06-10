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
  archiveMethodology,
  archiveVersion,
  createMethodology,
  createMethodologyFromTemplate,
  createNewVersion,
  fetchMethodologies,
  fetchMethodology,
  fetchMethodologyTemplates,
  fetchMethodologyVersion,
  fetchMethodologyVersions,
  lockVersion,
  methodologyKeys,
  removeFactor,
  removeFactorLevel,
  reorderFactorLevels,
  reorderFactors,
  updateFactor,
  updateFactorLevel,
  updateMethodology,
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
  ReorderPayload,
} from '../types';

// ---------- Queries ----------

export function useMethodologies(projectId: string | undefined) {
  return useQuery({
    queryKey: projectId ? methodologyKeys.listByProject(projectId) : ['methodologies', 'list', null],
    queryFn: () => fetchMethodologies(projectId!),
    enabled: !!projectId,
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
