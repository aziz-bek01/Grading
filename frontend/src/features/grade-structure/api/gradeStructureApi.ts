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
  GradeStructureListItem,
  GradeStructureTemplate,
  PreviewLookupPayload,
  ReasonPayload,
  ReorderPayload,
  SaveAsGradeTemplatePayload,
  UpdateGradePayload,
  UpdateGradeTemplatePayload,
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
  templates: ['grade-structure-templates'] as const,
};

// ---------- Structure listing / detail ----------

export async function fetchGradeStructures(
  filters: GradeStructureListFilters,
): Promise<{ items: GradeStructureListItem[] }> {
  // The real backend returns a PageResponse `{ items, page, size, ... }`. We only
  // need `items` here; tolerate a bare-array shape too (defensive, mirrors the
  // array-OR-{items} normalisation used across the FE).
  const res = await httpClient.get<
    { items: GradeStructureListItem[] } | GradeStructureListItem[]
  >(base, {
    params: {
      projectId: filters.projectId,
      status: filters.status,
    },
  });
  const data = res.data as unknown;
  const items = Array.isArray(data)
    ? data
    : ((data as { items?: GradeStructureListItem[] })?.items ?? []);
  return { items };
}

/**
 * The wire `GradeStructureResponse` (the envelope's `structure` field) — the flat
 * structure attributes WITHOUT `grades`. The aggregator flattens this to the
 * domain `GradeStructure`.
 */
type WireStructure = Omit<GradeStructure, 'grades'>;
/** The wire `GradeResponse` — i18n keys, NO nested band. */
type WireGrade = Omit<Grade, 'band'>;

/**
 * Detail-view AGGREGATOR (single grade-structure detail code path).
 *
 * The real backend's `GET /grade-structures/{id}` returns the ENVELOPE
 * `{ structure, grades:[...], bands:[...] }` where `bands` is a SEPARATE array.
 * This fetcher is the SINGLE place that produces the hydrated flat
 * `GradeStructure` domain shape by:
 *   (a) flattening `structure.*` to top level,
 *   (b) mapping each grade and JOINing its band from `bands[]` by grade_id.
 *
 * It also tolerates the legacy flat shape (MSW back-compat during the
 * transition) — exactly like the methodology array-OR-{items} pattern — so the
 * page survives either envelope OR flat response.
 */
export async function fetchGradeStructure(id: string): Promise<GradeStructure> {
  const res = await httpClient.get<unknown>(`${base}/${id}`);
  return hydrateGradeStructure(res.data);
}

/** Pure transform — exported for reuse by other detail-returning fetchers. */
export function hydrateGradeStructure(raw: unknown): GradeStructure {
  const data = raw as
    | { structure?: WireStructure; grades?: WireGrade[]; bands?: GradeBand[] }
    | (WireStructure & { grades?: Grade[] });

  // Envelope shape: `{ structure, grades, bands }`.
  if (data && typeof data === 'object' && 'structure' in data && data.structure) {
    const envelope = data as {
      structure: WireStructure;
      grades?: WireGrade[];
      bands?: GradeBand[];
    };
    const bandsByGrade = new Map<string, GradeBand>();
    for (const b of envelope.bands ?? []) {
      if (b?.grade_id) bandsByGrade.set(b.grade_id, b);
    }
    const grades: Grade[] = (envelope.grades ?? []).map((g) => ({
      ...g,
      band: bandsByGrade.get(g.id) ?? null,
    }));
    return { ...envelope.structure, grades };
  }

  // Legacy flat shape (MSW back-compat): grades already carry nested band.
  const flat = data as WireStructure & { grades?: Grade[] };
  return { ...(flat as GradeStructure), grades: flat.grades ?? [] };
}

export async function createFromTemplate(
  payload: CreateFromTemplatePayload,
): Promise<GradeStructure> {
  const res = await httpClient.post<unknown>(`${base}/from-template`, payload);
  return hydrateGradeStructure(res.data);
}

export async function createFromScratch(
  payload: CreateFromScratchPayload,
): Promise<GradeStructure> {
  const res = await httpClient.post<unknown>(base, payload);
  return hydrateGradeStructure(res.data);
}

export async function updateMetadata(
  id: string,
  payload: UpdateMetadataPayload,
): Promise<GradeStructure> {
  // PATCH returns the lean single-item shape (no grades). Hydrate defensively so
  // callers always get a `grades: []` field rather than undefined.
  const res = await httpClient.patch<unknown>(`${base}/${id}`, payload);
  return hydrateGradeStructure(res.data);
}

export async function deleteGradeStructure(id: string): Promise<void> {
  await httpClient.delete(`${base}/${id}`);
}

export async function approveStructure(id: string): Promise<GradeStructure> {
  const res = await httpClient.post<unknown>(`${base}/${id}/approve`, {});
  return hydrateGradeStructure(res.data);
}

export async function lockStructure(id: string): Promise<GradeStructure> {
  const res = await httpClient.post<unknown>(`${base}/${id}/lock`, {});
  return hydrateGradeStructure(res.data);
}

export async function archiveStructure(
  id: string,
  payload: ReasonPayload,
): Promise<GradeStructure> {
  const res = await httpClient.post<unknown>(`${base}/${id}/archive`, payload);
  return hydrateGradeStructure(res.data);
}

export async function createNewVersion(id: string): Promise<GradeStructure> {
  const res = await httpClient.post<unknown>(`${base}/${id}/create-new-version`, {});
  return hydrateGradeStructure(res.data);
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

/**
 * BE-5: DRAFT-only atomic reorder of a structure's grades. Body reuses the
 * methodology `ReorderRequest { ordered_ids }`. Returns `{ items:[grade...] }`
 * in the new order (mirrors reorderFactors).
 */
export async function reorderGrades(
  structureId: string,
  payload: ReorderPayload,
): Promise<{ items: Grade[] }> {
  const res = await httpClient.post<{ items: Grade[] }>(
    `${base}/${structureId}/grades/reorder`,
    payload,
  );
  return res.data;
}

// ---------- Grade band ----------

export async function upsertBand(
  gradeId: string,
  payload: UpsertBandPayload,
): Promise<GradeBand> {
  const res = await httpClient.post<GradeBand>(`/grades/${gradeId}/band`, payload);
  return res.data;
}

/**
 * BE-6: explicit band clear — removes a grade's band WITHOUT deleting the grade.
 * DRAFT-only, idempotent (204 even when no band existed). Prevents an accidental
 * 0/0 band wipe from the row editor.
 */
export async function removeBand(gradeId: string): Promise<void> {
  await httpClient.delete(`/grades/${gradeId}/band`);
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

// ---------- Templates (BE-9: catalog + save-as / manage custom) ----------

export async function fetchGradeTemplates(): Promise<{ items: GradeStructureTemplate[] }> {
  const res = await httpClient.get<
    { items: GradeStructureTemplate[] } | GradeStructureTemplate[]
  >('/grade-structure-templates');
  const data = res.data as unknown;
  const items = Array.isArray(data)
    ? data
    : ((data as { items?: GradeStructureTemplate[] })?.items ?? []);
  return { items };
}

/**
 * "Save as template" — snapshot a structure's grades + bands into a reusable
 * tenant CUSTOM template. `POST /grade-structures/{id}/save-as-template` → 201
 * `{ template_id }`. 409 `GRADE_TEMPLATE_CODE_EXISTS` on a duplicate/reserved code.
 */
export async function saveGradeStructureAsTemplate(
  structureId: string,
  payload: SaveAsGradeTemplatePayload,
): Promise<{ template_id: string }> {
  const res = await httpClient.post<{ template_id: string }>(
    `${base}/${structureId}/save-as-template`,
    payload,
  );
  return res.data;
}

/**
 * Rename a CUSTOM template (name/description — code is immutable).
 * `PUT /grade-structure-templates/{id}`. Built-in / cross-tenant ids → 404.
 */
export async function updateGradeTemplate(
  templateId: string,
  payload: UpdateGradeTemplatePayload,
): Promise<{ template_id: string }> {
  const res = await httpClient.put<{ template_id: string }>(
    `/grade-structure-templates/${templateId}`,
    payload,
  );
  return res.data;
}

/**
 * Archive (soft-delete) a CUSTOM template — it disappears from the picker.
 * `DELETE /grade-structure-templates/{id}`. Built-in / cross-tenant ids → 404.
 */
export async function archiveGradeTemplate(templateId: string): Promise<void> {
  await httpClient.delete(`/grade-structure-templates/${templateId}`);
}
