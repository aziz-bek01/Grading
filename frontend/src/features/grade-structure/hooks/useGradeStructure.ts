/**
 * React Query hooks for grade structures + grades + bands + pyramid.
 *
 * All mutations invalidate the structure detail + list so the locked /
 * approved banner, GradeTable, and version chain re-render in lockstep.
 */
import { useEffect, useMemo, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  addGrade,
  approveStructure,
  archiveStructure,
  createFromScratch,
  createFromTemplate,
  createNewVersion,
  fetchGradePyramid,
  fetchGradeStructure,
  fetchGradeStructures,
  gradeStructureKeys,
  lockStructure,
  previewGradeLookup,
  removeGrade,
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
  UpdateGradePayload,
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

// ---------- Invalidate helper ----------

function invalidate(
  qc: ReturnType<typeof useQueryClient>,
  id?: string,
) {
  qc.invalidateQueries({ queryKey: gradeStructureKeys.all });
  if (id) {
    qc.invalidateQueries({ queryKey: gradeStructureKeys.detail(id) });
    qc.invalidateQueries({ queryKey: gradeStructureKeys.pyramid(id) });
  }
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

export function useUpsertBand(gradeId: string, structureId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpsertBandPayload) => upsertBand(gradeId, payload),
    onSuccess: () => invalidate(qc, structureId),
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
  const [debouncedScore, setDebouncedScore] = useState<number | null>(score);
  const lastTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (lastTimer.current) clearTimeout(lastTimer.current);
    lastTimer.current = setTimeout(() => setDebouncedScore(score), debounceMs);
    return () => {
      if (lastTimer.current) clearTimeout(lastTimer.current);
    };
  }, [score, debounceMs]);

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
