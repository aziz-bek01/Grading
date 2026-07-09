/**
 * React Query hooks for grade structures + grades + bands + pyramid + templates.
 *
 * All mutations invalidate the structure detail + list so the locked /
 * approved banner, GradeTable, and version chain re-render in lockstep.
 */
import { useMemo } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useDebouncedValue } from '@/shared/lib/useDebounce';
import {
  addGrade,
  approveStructure,
  archiveGradeTemplate,
  archiveStructure,
  createFromScratch,
  createFromTemplate,
  createNewVersion,
  deleteGradeStructure,
  fetchGradePyramid,
  fetchGradeStructure,
  fetchGradeStructures,
  fetchGradeTemplates,
  gradeStructureKeys,
  lockStructure,
  previewGradeLookup,
  removeBand,
  removeGrade,
  reorderGrades,
  saveGradeStructureAsTemplate,
  updateGradeTemplate,
  updateGrade,
  updateMetadata,
  upsertBand,
} from '../api/gradeStructureApi';
import type {
  AddGradePayload,
  CreateFromScratchPayload,
  CreateFromTemplatePayload,
  GradeLookupResult,
  GradeStructureListFilters,
  ReasonPayload,
  ReorderPayload,
  SaveAsGradeTemplatePayload,
  UpdateGradePayload,
  UpdateGradeTemplatePayload,
  UpdateMetadataPayload,
  UpsertBandPayload,
} from '../types';

// ---------- Queries ----------

export function useGradeStructures(filters: GradeStructureListFilters) {
  return useQuery({
    queryKey: gradeStructureKeys.list(filters),
    queryFn: () => fetchGradeStructures(filters),
    enabled: !!filters.projectId,
  });
}

export function useGradeStructure(id: string | undefined) {
  return useQuery({
    queryKey: id ? gradeStructureKeys.detail(id) : ['grade-structures', 'detail', null],
    queryFn: () => fetchGradeStructure(id!),
    enabled: !!id,
  });
}

export function useGradePyramid(id: string | undefined) {
  return useQuery({
    queryKey: id ? gradeStructureKeys.pyramid(id) : ['grade-structures', 'pyramid', null],
    queryFn: () => fetchGradePyramid(id!),
    enabled: !!id,
  });
}

export function useGradeTemplates() {
  return useQuery({
    queryKey: gradeStructureKeys.templates,
    queryFn: () => fetchGradeTemplates(),
    // Templates rarely change — long staleTime is safe (mirrors methodology).
    staleTime: 5 * 60_000,
  });
}

// ---------- Invalidate helper ----------

function invalidate(qc: ReturnType<typeof useQueryClient>, id?: string) {
  qc.invalidateQueries({ queryKey: gradeStructureKeys.all });
  if (id) {
    qc.invalidateQueries({ queryKey: gradeStructureKeys.detail(id) });
    qc.invalidateQueries({ queryKey: gradeStructureKeys.pyramid(id) });
  }
}

function invalidateTemplates(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: gradeStructureKeys.templates });
}

// ---------- Structure mutations ----------

export function useCreateFromTemplate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateFromTemplatePayload) => createFromTemplate(payload),
    onSuccess: (s) => invalidate(qc, s.id),
  });
}

export function useCreateFromScratch() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateFromScratchPayload) => createFromScratch(payload),
    onSuccess: (s) => invalidate(qc, s.id),
  });
}

export function useUpdateMetadata(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdateMetadataPayload) => updateMetadata(id, payload),
    onSuccess: () => invalidate(qc, id),
  });
}

/** BE-4: DRAFT-only hard delete of a structure (non-DRAFT keeps Archive). */
export function useDeleteGradeStructure(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => deleteGradeStructure(id),
    onSuccess: () => invalidate(qc, id),
  });
}

export function useApprove(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => approveStructure(id),
    onSuccess: () => invalidate(qc, id),
  });
}

export function useLock(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => lockStructure(id),
    onSuccess: () => invalidate(qc, id),
  });
}

export function useArchive(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: ReasonPayload) => archiveStructure(id, payload),
    onSuccess: () => invalidate(qc, id),
  });
}

export function useCreateNewVersion(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => createNewVersion(id),
    onSuccess: (s) => invalidate(qc, s.id),
  });
}

// ---------- Grade mutations ----------

export function useAddGrade(structureId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: AddGradePayload) => addGrade(structureId, payload),
    onSuccess: () => invalidate(qc, structureId),
  });
}

export function useUpdateGrade(gradeId: string, structureId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdateGradePayload) => updateGrade(gradeId, payload),
    onSuccess: () => invalidate(qc, structureId),
  });
}

export function useRemoveGrade(gradeId: string, structureId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => removeGrade(gradeId),
    onSuccess: () => invalidate(qc, structureId),
  });
}

/**
 * BE-5: reorder grades. Sends the full id set in its new order; the table
 * refetches on settle so it reflects the persisted `sort_order`.
 */
export function useReorderGrades(structureId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: ReorderPayload) => reorderGrades(structureId, payload),
    onSettled: () => invalidate(qc, structureId),
  });
}

// ---------- Grade band mutations ----------

export function useUpsertBand(gradeId: string, structureId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpsertBandPayload) => upsertBand(gradeId, payload),
    onSuccess: () => invalidate(qc, structureId),
  });
}

/** BE-6: explicit band clear (does not delete the grade). */
export function useRemoveBand(gradeId: string, structureId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => removeBand(gradeId),
    onSuccess: () => invalidate(qc, structureId),
  });
}

// ---------- Template mutations (BE-9) ----------

/**
 * "Save as template" — snapshot a structure into a CUSTOM template. Invalidates
 * the template catalog so the new template shows in the picker. 409 (duplicate
 * code) propagates to the caller for inline handling.
 */
export function useSaveAsGradeTemplate(structureId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: SaveAsGradeTemplatePayload) =>
      saveGradeStructureAsTemplate(structureId, payload),
    onSuccess: () => invalidateTemplates(qc),
  });
}

/** Rename a CUSTOM template (name/description). Invalidates the catalog. */
export function useUpdateGradeTemplate(templateId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdateGradeTemplatePayload) =>
      updateGradeTemplate(templateId, payload),
    onSuccess: () => invalidateTemplates(qc),
  });
}

/** Archive a CUSTOM template (removes it from the picker). Invalidates the catalog. */
export function useArchiveGradeTemplate(templateId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => archiveGradeTemplate(templateId),
    onSuccess: () => invalidateTemplates(qc),
  });
}

// ---------- Score-to-grade preview with 300ms debounce ----------

/**
 * Debounced "what grade does this score map to?" preview. Returns
 *   { result, isLoading, error }
 * The endpoint never audits and never persists — purely advisory.
 */
export function usePreviewGradeLookup(
  structureId: string | undefined,
  score: number | null,
  debounceMs = 300,
) {
  const debouncedScore = useDebouncedValue(score, debounceMs);

  const enabled = !!structureId && debouncedScore != null && !Number.isNaN(debouncedScore);

  const query = useQuery({
    queryKey: ['grade-structure-preview-lookup', structureId, debouncedScore],
    queryFn: () => previewGradeLookup(structureId!, { score: debouncedScore! }),
    enabled,
    // Lookups are advisory and don't mutate — cache briefly for free reuse.
    staleTime: 60_000,
  });

  return useMemo(
    () => ({
      result: query.data as GradeLookupResult | undefined,
      isLoading: enabled && query.isLoading,
      error: query.error,
    }),
    [query.data, query.isLoading, query.error, enabled],
  );
}
