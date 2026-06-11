/**
 * Methodology — REST fetchers (Phase 4).
 *
 * NO tenant identifier is ever sent: the backend derives the active tenant
 * from the JWT (security blueprint API-13 / noTenantIdLeak.test.ts).
 *
 * Every mutating endpoint is audited server-side per PRD MVP1-E7-*.
 */
import { httpClient } from '@/shared/api/httpClient';
import type {
  Factor,
  FactorCreatePayload,
  FactorLevel,
  FactorLevelCreatePayload,
  FactorLevelUpdatePayload,
  FactorUpdatePayload,
  LevelReorderPayload,
  Methodology,
  MethodologyCreatePayload,
  MethodologyReasonPayload,
  MethodologyTemplate,
  MethodologyUpdatePayload,
  MethodologyVersion,
  MethodologyVersionMetadataUpdatePayload,
  MethodologyVersionSummary,
  ReorderPayload,
  SaveAsTemplatePayload,
  UpdateTemplatePayload,
} from '../types';

const base = '/methodologies';

export const methodologyKeys = {
  all: ['methodologies'] as const,
  listByProject: (projectId: string) => ['methodologies', 'list', projectId] as const,
  detail: (id: string) => ['methodologies', 'detail', id] as const,
  versions: (methodologyId: string) =>
    ['methodologies', 'versions', methodologyId] as const,
  version: (versionId: string) => ['methodology-versions', 'detail', versionId] as const,
  templates: ['methodology-templates'] as const,
};

// ---------- Methodology listing / CRUD ----------

export async function fetchMethodologies(projectId: string): Promise<{ items: Methodology[] }> {
  // projectId is a SCOPE filter, not a tenant identifier. Allowed on the wire.
  const res = await httpClient.get<{ items: Methodology[] }>(base, {
    params: { projectId },
  });
  return res.data;
}

export async function fetchMethodology(id: string): Promise<Methodology> {
  const res = await httpClient.get<Methodology>(`${base}/${id}`);
  return res.data;
}

export async function createMethodology(payload: MethodologyCreatePayload): Promise<Methodology> {
  const res = await httpClient.post<Methodology>(base, payload);
  return res.data;
}

export async function createMethodologyFromTemplate(
  payload: MethodologyCreatePayload,
): Promise<Methodology> {
  // Backend `/from-template` (CreateMethodologyFromTemplateRequest) requires a
  // NON-BLANK snake_case `template_code` and derives methodology_type +
  // scoring_mode from the built-in template itself. The FE's MethodologyType
  // maps 1:1 to the registry template codes (CLASSIC_8_FACTOR /
  // EXTENDED_11_CRITERIA / CUSTOM), so we send `template_code`, falling back to
  // `methodology_type` for CUSTOM (where source_template_code is null). Sending
  // the legacy `source_template_code` field left `template_code` null → 400.
  const body = {
    project_id: payload.project_id,
    code: payload.code,
    name_i18n: payload.name_i18n,
    description_i18n: payload.description_i18n,
    template_code: payload.source_template_code ?? payload.methodology_type,
  };
  const res = await httpClient.post<Methodology>(`${base}/from-template`, body);
  return res.data;
}

export async function updateMethodology(
  id: string,
  payload: MethodologyUpdatePayload,
): Promise<Methodology> {
  const res = await httpClient.patch<Methodology>(`${base}/${id}`, payload);
  return res.data;
}

export async function archiveMethodology(
  id: string,
  payload: MethodologyReasonPayload,
): Promise<Methodology> {
  const res = await httpClient.post<Methodology>(`${base}/${id}/archive`, payload);
  return res.data;
}

// ---------- Versions ----------

export async function fetchMethodologyVersions(
  methodologyId: string,
): Promise<MethodologyVersionSummary[]> {
  // Real backend returns a BARE ARRAY here (not a `{items}` envelope). Reuse the
  // established array-OR-{items} normalisation pattern (cf. organizationApi
  // `fetchDepartmentTree` / approvalApi `fetchMyInbox`, fixes #29/#30/#31) so the
  // version panel survives either shape (MSW now also returns a bare array).
  const res = await httpClient.get<
    MethodologyVersionSummary[] | { items: MethodologyVersionSummary[] }
  >(`${base}/${methodologyId}/versions`);
  const data = res.data as unknown;
  return Array.isArray(data) ? data : ((data as { items?: MethodologyVersionSummary[] })?.items ?? []);
}

/** Bare `FactorResponse` — the array shape before the aggregator adds levels. */
type BareFactor = Omit<Factor, 'levels'>;

/**
 * GET the bare FactorResponse[] for a version (no embedded levels). Single
 * source of truth for "fetch a version's factors" — reused by the version-detail
 * aggregator below.
 */
export async function fetchFactorsByVersion(versionId: string): Promise<BareFactor[]> {
  const res = await httpClient.get<BareFactor[] | { items: BareFactor[] }>(
    `/methodology-versions/${versionId}/factors`,
  );
  const data = res.data as unknown;
  return Array.isArray(data) ? data : ((data as { items?: BareFactor[] })?.items ?? []);
}

/**
 * GET the bare FactorLevelResponse[] for one factor. Single source of truth for
 * "fetch a factor's levels" — reused by the version-detail aggregator below.
 */
export async function fetchLevelsByFactor(factorId: string): Promise<FactorLevel[]> {
  const res = await httpClient.get<FactorLevel[] | { items: FactorLevel[] }>(
    `/factors/${factorId}/levels`,
  );
  const data = res.data as unknown;
  return Array.isArray(data) ? data : ((data as { items?: FactorLevel[] })?.items ?? []);
}

/**
 * Version-detail AGGREGATOR (single version-detail code path).
 *
 * The real backend's `GET /methodology-versions/{id}` returns the version OBJECT
 * WITHOUT a `factors` field, and factors/levels live on separate endpoints. This
 * fetcher therefore:
 *   1. GETs the version object,
 *   2. GETs its factors (bare array),
 *   3. GETs each factor's levels in parallel (Promise.all),
 * filling the EXISTING optional `MethodologyVersion.factors` / `Factor.levels`
 * fields so every consumer (FactorTable, Translations, WeightSumVisualizer) sees
 * a fully-hydrated version exactly as it did under the old inline-MSW shape.
 */
export async function fetchMethodologyVersion(versionId: string): Promise<MethodologyVersion> {
  // The raw version object has NO `factors` field — type it accordingly so the
  // aggregator is the single place that produces the fully-hydrated domain shape.
  const res = await httpClient.get<Omit<MethodologyVersion, 'factors'>>(
    `/methodology-versions/${versionId}`,
  );
  const version = res.data;

  const factors = await fetchFactorsByVersion(versionId);
  const hydratedFactors: Factor[] = await Promise.all(
    factors.map(async (factor) => ({
      ...factor,
      levels: await fetchLevelsByFactor(factor.id),
    })),
  );

  return { ...version, factors: hydratedFactors };
}

export async function createNewVersion(
  _methodologyId: string,
  sourceVersionId: string,
): Promise<MethodologyVersion> {
  // Real backend: POST /methodology-versions/{sourceVersionId}/create-new-version
  // with NO body (the controller takes only the path id; it clones the source
  // version server-side). The old `/methodologies/{id}/versions` + body endpoint
  // did not exist. `_methodologyId` is kept for the hook's invalidation closure.
  const res = await httpClient.post<MethodologyVersion>(
    `/methodology-versions/${sourceVersionId}/create-new-version`,
    {},
  );
  return res.data;
}

export async function approveVersion(versionId: string): Promise<MethodologyVersion> {
  const res = await httpClient.post<MethodologyVersion>(
    `/methodology-versions/${versionId}/approve`,
    {},
  );
  return res.data;
}

export async function lockVersion(versionId: string): Promise<MethodologyVersion> {
  const res = await httpClient.post<MethodologyVersion>(
    `/methodology-versions/${versionId}/lock`,
    {},
  );
  return res.data;
}

export async function archiveVersion(
  versionId: string,
  payload: MethodologyReasonPayload,
): Promise<MethodologyVersion> {
  const res = await httpClient.post<MethodologyVersion>(
    `/methodology-versions/${versionId}/archive`,
    payload,
  );
  return res.data;
}

/**
 * PATCH version-level scoring metadata (scoring_mode + target_total_points).
 *
 * DRAFT-only server-side. Changing scoring_mode PRESERVES factors & levels — only
 * the scoring-time interpretation changes, so the caller must refetch the version
 * + factors afterwards (the hook's invalidation does this) so the FactorEditor
 * weight field / WeightSumVisualizer / points-vs-scale_value column re-render off
 * the new mode. The backend returns the version object WITHOUT a `factors` field
 * (same shape as `GET /methodology-versions/{id}`), so the response is typed
 * without the aggregated `factors`.
 */
export async function updateMethodologyVersionMetadata(
  versionId: string,
  payload: MethodologyVersionMetadataUpdatePayload,
): Promise<Omit<MethodologyVersion, 'factors'>> {
  const res = await httpClient.patch<Omit<MethodologyVersion, 'factors'>>(
    `/methodology-versions/${versionId}`,
    payload,
  );
  return res.data;
}

// ---------- Factors ----------

export async function addFactor(
  versionId: string,
  payload: FactorCreatePayload,
): Promise<Factor> {
  const res = await httpClient.post<Factor>(
    `/methodology-versions/${versionId}/factors`,
    payload,
  );
  return res.data;
}

export async function updateFactor(factorId: string, payload: FactorUpdatePayload): Promise<Factor> {
  const res = await httpClient.patch<Factor>(`/factors/${factorId}`, payload);
  return res.data;
}

export async function removeFactor(factorId: string): Promise<void> {
  await httpClient.delete(`/factors/${factorId}`);
}

export async function reorderFactors(
  versionId: string,
  payload: ReorderPayload,
): Promise<{ items: Factor[] }> {
  const res = await httpClient.post<{ items: Factor[] }>(
    `/methodology-versions/${versionId}/factors/reorder`,
    payload,
  );
  return res.data;
}

// ---------- Factor levels ----------

export async function addFactorLevel(
  factorId: string,
  payload: FactorLevelCreatePayload,
): Promise<FactorLevel> {
  const res = await httpClient.post<FactorLevel>(`/factors/${factorId}/levels`, payload);
  return res.data;
}

export async function updateFactorLevel(
  levelId: string,
  payload: FactorLevelUpdatePayload,
): Promise<FactorLevel> {
  const res = await httpClient.patch<FactorLevel>(`/factor-levels/${levelId}`, payload);
  return res.data;
}

export async function removeFactorLevel(levelId: string): Promise<void> {
  await httpClient.delete(`/factor-levels/${levelId}`);
}

export async function reorderFactorLevels(
  factorId: string,
  payload: LevelReorderPayload,
): Promise<{ items: FactorLevel[] }> {
  const res = await httpClient.post<{ items: FactorLevel[] }>(
    `/factors/${factorId}/levels/reorder`,
    payload,
  );
  return res.data;
}

// ---------- Templates (Epic E: save-as / manage custom) ----------

export async function fetchMethodologyTemplates(): Promise<{ items: MethodologyTemplate[] }> {
  const res = await httpClient.get<{ items: MethodologyTemplate[] }>('/methodology-templates');
  return res.data;
}

/**
 * "Save as template" — snapshot a methodology's latest version into a reusable
 * tenant CUSTOM template. Backend route:
 * `POST /methodologies/{id}/save-as-template`. Returns 201 with the new template.
 * 409 `METHODOLOGY_TEMPLATE_CODE_EXISTS` when the code is already taken. Gated
 * server-side by METHODOLOGY_CREATE.
 */
export async function saveMethodologyAsTemplate(
  methodologyId: string,
  payload: SaveAsTemplatePayload,
): Promise<MethodologyTemplate> {
  const res = await httpClient.post<MethodologyTemplate>(
    `${base}/${methodologyId}/save-as-template`,
    payload,
  );
  return res.data;
}

/**
 * Rename a CUSTOM template (name/description only — code is immutable).
 * `PUT /methodology-templates/{id}`. Built-in / cross-tenant ids return 404.
 * Gated server-side by METHODOLOGY_EDIT.
 */
export async function updateCustomTemplate(
  templateId: string,
  payload: UpdateTemplatePayload,
): Promise<MethodologyTemplate> {
  const res = await httpClient.put<MethodologyTemplate>(
    `/methodology-templates/${templateId}`,
    payload,
  );
  return res.data;
}

/**
 * Archive (soft-delete) a CUSTOM template — it disappears from the picker.
 * `DELETE /methodology-templates/{id}`. Built-in / cross-tenant ids return 404.
 * Gated server-side by METHODOLOGY_EDIT.
 */
export async function archiveCustomTemplate(templateId: string): Promise<void> {
  await httpClient.delete(`/methodology-templates/${templateId}`);
}
