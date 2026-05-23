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

const base = '/job-profiles';

export async function createJobProfile(positionId: string): Promise<JobProfile> {
  const res = await httpClient.post<JobProfile>(base, { position_id: positionId });
  return res.data;
}

export async function fetchJobProfileById(id: string): Promise<JobProfile> {
  const res = await httpClient.get<JobProfile>(`${base}/${id}`);
  return res.data;
}

export async function fetchJobProfileByPosition(positionId: string): Promise<JobProfile | null> {
  const res = await httpClient.get<JobProfile | null>(`${base}/by-position/${positionId}`);
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

export async function fetchJobProfileRevisions(
  positionId: string,
): Promise<{ items: JobProfileRevisionSummary[] }> {
  const res = await httpClient.get<{ items: JobProfileRevisionSummary[] }>(
    `${base}/by-position/${positionId}/revisions`,
  );
  return res.data;
}
