import { httpClient } from '@/shared/api/httpClient';
import type {
  JobProfile,
  JobProfilePatch,
  JobProfileReasonPayload,
  JobProfileRevisionSummary,
} from '../types';

/**
 * React-Query cache keys for job profile data.
 *
 * tenant id is intentionally absent — backend derives it from JWT
 * (security blueprint API-13 / noTenantIdLeak.test.ts).
 */
export const jobProfileKeys = {
  all: ['job-profiles'] as const,
  byPosition: (positionId: string) => ['job-profiles', 'by-position', positionId] as const,
  detail: (id: string) => ['job-profiles', 'detail', id] as const,
  revisions: (positionId: string) => ['job-profiles', 'revisions', positionId] as const,
};

/**
 * Two backend controllers serve job profiles:
 *   - PositionJobProfileController → /positions/{positionId}/job-profile[...]
 *   - JobProfileController          → /job-profiles/{id}[...]
 * `positionBase` builds the former; `base` is the latter.
 *
 * The Spring response (`JobProfileResponse`, global SNAKE_CASE Jackson) maps
 * 1:1 onto the FE `JobProfile` type — no field remapping needed; the only
 * drift was the URLs + the create/revisions shapes, fixed below.
 */
const base = '/job-profiles';
const positionBase = (positionId: string) => `/positions/${positionId}/job-profile`;

/**
 * POST /positions/{positionId}/job-profile — positionId is path-sourced; the
 * backend takes an empty/minimal body (all profile fields nullable on create).
 * Never send position_id/tenant_id in the body (FE-TI tenant rule).
 */
export async function createJobProfile(positionId: string): Promise<JobProfile> {
  const res = await httpClient.post<JobProfile>(positionBase(positionId), {});
  return res.data;
}

export async function fetchJobProfileById(id: string): Promise<JobProfile> {
  const res = await httpClient.get<JobProfile>(`${base}/${id}`);
  return res.data;
}

/**
 * GET /positions/{positionId}/job-profile.
 * Backend returns 204 No Content (empty body) when the position has no active
 * profile, or 200 + JobProfileResponse when it exists. 204/empty → null.
 */
export async function fetchJobProfileByPosition(positionId: string): Promise<JobProfile | null> {
  const res = await httpClient.get<JobProfile | null>(positionBase(positionId));
  // 204 No Content arrives with an empty body — axios may surface it as null
  // or an empty string depending on the adapter. Normalise both to null.
  const body = res.data as unknown;
  if (res.status === 204 || body == null || body === '') return null;
  return res.data;
}

export async function patchJobProfile(id: string, payload: JobProfilePatch): Promise<JobProfile> {
  const res = await httpClient.patch<JobProfile>(`${base}/${id}`, payload);
  return res.data;
}

export async function submitJobProfile(id: string): Promise<JobProfile> {
  const res = await httpClient.post<JobProfile>(`${base}/${id}/submit`, {});
  return res.data;
}

export async function approveJobProfile(id: string): Promise<JobProfile> {
  const res = await httpClient.post<JobProfile>(`${base}/${id}/approve`, {});
  return res.data;
}

export async function requestChangesJobProfile(
  id: string,
  payload: JobProfileReasonPayload,
): Promise<JobProfile> {
  const res = await httpClient.post<JobProfile>(`${base}/${id}/request-changes`, payload);
  return res.data;
}

export async function archiveJobProfile(
  id: string,
  payload: JobProfileReasonPayload,
): Promise<JobProfile> {
  const res = await httpClient.post<JobProfile>(`${base}/${id}/archive`, payload);
  return res.data;
}

export async function createJobProfileRevision(id: string): Promise<JobProfile> {
  const res = await httpClient.post<JobProfile>(`${base}/${id}/create-revision`, {});
  return res.data;
}

/**
 * GET /positions/{positionId}/job-profile/revisions.
 * Backend returns a BARE JSON array (`JobProfileResponse[]`), not wrapped.
 * Wrap locally so callers keep the `{ items }` envelope contract. Each
 * element already carries the fields `JobProfileRevisionSummary` needs.
 */
export async function fetchJobProfileRevisions(
  positionId: string,
): Promise<{ items: JobProfileRevisionSummary[] }> {
  const res = await httpClient.get<JobProfileRevisionSummary[]>(`${positionBase(positionId)}/revisions`);
  return { items: res.data ?? [] };
}
