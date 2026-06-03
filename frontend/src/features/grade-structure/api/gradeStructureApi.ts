/**
 * Grade Structure — REST fetchers (Phase 6).
 *
 * NO tenant identifier is ever sent: the backend derives the active tenant
 * from the JWT (security blueprint API-13 / noTenantIdLeak.test.ts).
 *
 * Every mutating endpoint is audited server-side per PRD Phase 6 ACs.
 */
import { httpClient } from '@/shared/api/httpClient';
import type {
  AddGradePayload,
  CreateFromScratchPayload,
  CreateFromTemplatePayload,
  Grade,
  GradeBand,
  GradeLookupResult,
  GradePyramidResponse,
  GradeStructure,
  GradeStructureListFilters,
  PreviewLookupPayload,
  ReasonPayload,
  UpdateGradePayload,
  UpdateMetadataPayload,
  UpsertBandPayload,
} from '../types';

const base = '/grade-structures';

export const gradeStructureKeys = {
  all: ['grade-structures'] as const,
  list: (filters: GradeStructureListFilters) =>
    ['grade-structures', 'list', filters] as const,
  detail: (id: string) => ['grade-structures', 'detail', id] as const,
  pyramid: (id: string) => ['grade-structures', 'pyramid', id] as const,
};

// ---------- Structure listing / CRUD ----------

export async function fetchGradeStructures(
  filters: GradeStructureListFilters,
): Promise<{ items: GradeStructure[] }> {
  const res = await httpClient.get<{ items: GradeStructure[] }>(base, {
    params: {
      projectId: filters.projectId,
      status: filters.status,
    },
  });
  return res.data;
}

export async function fetchGradeStructure(id: string): Promise<GradeStructure> {
  const res = await httpClient.get<GradeStructure>(`${base}/${id}`);
  return res.data;
}

export async function createFromTemplate(
  payload: CreateFromTemplatePayload,
): Promise<GradeStructure> {
  const res = await httpClient.post<GradeStructure>(`${base}/from-template`, payload);
  return res.data;
}

export async function createFromScratch(
  payload: CreateFromScratchPayload,
): Promise<GradeStructure> {
  const res = await httpClient.post<GradeStructure>(base, payload);
  return res.data;
}

export async function updateMetadata(
  id: string,
  payload: UpdateMetadataPayload,
): Promise<GradeStructure> {
  const res = await httpClient.patch<GradeStructure>(`${base}/${id}`, payload);
  return res.data;
}

export async function approveStructure(id: string): Promise<GradeStructure> {
  const res = await httpClient.post<GradeStructure>(`${base}/${id}/approve`, {});
  return res.data;
}

export async function lockStructure(id: string): Promise<GradeStructure> {
  const res = await httpClient.post<GradeStructure>(`${base}/${id}/lock`, {});
  return res.data;
}

export async function archiveStructure(
  id: string,
  payload: ReasonPayload,
): Promise<GradeStructure> {
  const res = await httpClient.post<GradeStructure>(`${base}/${id}/archive`, payload);
  return res.data;
}

export async function createNewVersion(id: string): Promise<GradeStructure> {
  const res = await httpClient.post<GradeStructure>(`${base}/${id}/create-new-version`, {});
  return res.data;
}

// ---------- Grades ----------

export async function addGrade(
  structureId: string,
  payload: AddGradePayload,
): Promise<Grade> {
  const res = await httpClient.post<Grade>(`${base}/${structureId}/grades`, payload);
  return res.data;
}

export async function updateGrade(
  gradeId: string,
  payload: UpdateGradePayload,
): Promise<Grade> {
  const res = await httpClient.patch<Grade>(`/grades/${gradeId}`, payload);
  return res.data;
}

export async function removeGrade(gradeId: string): Promise<void> {
  await httpClient.delete(`/grades/${gradeId}`);
}

// ---------- Grade band ----------

export async function upsertBand(
  gradeId: string,
  payload: UpsertBandPayload,
): Promise<GradeBand> {
  const res = await httpClient.post<GradeBand>(`/grades/${gradeId}/band`, payload);
  return res.data;
}

// ---------- Pyramid + lookup ----------

export async function fetchGradePyramid(id: string): Promise<GradePyramidResponse> {
  const res = await httpClient.get<GradePyramidResponse>(`${base}/${id}/pyramid`);
  return res.data;
}

export async function previewGradeLookup(
  id: string,
  payload: PreviewLookupPayload,
): Promise<GradeLookupResult> {
  const res = await httpClient.post<GradeLookupResult>(
    `${base}/${id}/preview-grade-lookup`,
    payload,
  );
  return res.data;
}
