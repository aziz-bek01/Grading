import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  approveJobProfile,
  archiveJobProfile,
  createJobProfile,
  createJobProfileRevision,
  fetchJobProfileById,
  fetchJobProfileByPosition,
  fetchJobProfileRevisions,
  jobProfileKeys,
  patchJobProfile,
  requestChangesJobProfile,
  submitJobProfile,
} from '../api/jobProfileApi';
import type { JobProfile, JobProfilePatch, JobProfileReasonPayload } from '../types';

export function useJobProfile(profileId: string | undefined) {
  return useQuery({
    queryKey: profileId ? jobProfileKeys.detail(profileId) : ['job-profiles', 'detail', null],
    queryFn: () => fetchJobProfileById(profileId!),
    enabled: !!profileId,
  });
}

export function useJobProfileByPosition(positionId: string | undefined) {
  return useQuery({
    queryKey: positionId
      ? jobProfileKeys.byPosition(positionId)
      : ['job-profiles', 'by-position', null],
    queryFn: () => fetchJobProfileByPosition(positionId!),
    enabled: !!positionId,
  });
}

/**
 * Bulk (client-fan-out) job-profile lookup for a bounded set of position ids —
 * e.g. one server page of the Position Catalog (PAGE-2 fix). There is no
 * backend "list job profiles for many positions" endpoint (only per-position
 * `GET /positions/{id}/job-profile` and per-profile `GET /job-profiles/{id}`),
 * so this fires one request per id via `useQueries`, sharing the SAME cache
 * key as `useJobProfileByPosition` (no duplicate fetches when a user opens a
 * position's Job Profile tab right after browsing the catalog).
 *
 * Callers MUST bound `positionIds` to something small (a single paginated
 * table page, not a whole project) — this is a real fan-out, not a bulk
 * endpoint, and issuing one request per row of an unbounded list would be an
 * N+1 anti-pattern.
 *
 * Map values: `undefined` = still loading (or fetch failed) — render a
 * neutral placeholder, never guess; `null` = confirmed no active profile
 * exists yet; a `JobProfile` = the active profile.
 */
export function useJobProfileStatusesByPositions(
  positionIds: string[],
): Map<string, JobProfile | null | undefined> {
  const queries = useQueries({
    queries: positionIds.map((positionId) => ({
      queryKey: jobProfileKeys.byPosition(positionId),
      queryFn: () => fetchJobProfileByPosition(positionId),
    })),
  });

  const byPositionId = new Map<string, JobProfile | null | undefined>();
  positionIds.forEach((id, index) => {
    const result = queries[index];
    byPositionId.set(id, result?.isSuccess ? result.data : undefined);
  });
  return byPositionId;
}

export function useJobProfileRevisions(positionId: string | undefined) {
  return useQuery({
    queryKey: positionId ? jobProfileKeys.revisions(positionId) : ['job-profiles', 'revisions', null],
    queryFn: () => fetchJobProfileRevisions(positionId!),
    enabled: !!positionId,
  });
}

function invalidate(qc: ReturnType<typeof useQueryClient>, positionId?: string, profileId?: string) {
  qc.invalidateQueries({ queryKey: jobProfileKeys.all });
  if (positionId) qc.invalidateQueries({ queryKey: jobProfileKeys.byPosition(positionId) });
  if (profileId) qc.invalidateQueries({ queryKey: jobProfileKeys.detail(profileId) });
}

export function useCreateJobProfile(positionId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => createJobProfile(positionId),
    onSuccess: (created) => invalidate(qc, positionId, created.id),
  });
}

export function useUpdateJobProfile(profileId: string, positionId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: JobProfilePatch) => patchJobProfile(profileId, payload),
    onSuccess: () => invalidate(qc, positionId, profileId),
  });
}

export function useSubmitJobProfile(profileId: string, positionId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => submitJobProfile(profileId),
    onSuccess: () => invalidate(qc, positionId, profileId),
  });
}

export function useApproveJobProfile(profileId: string, positionId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => approveJobProfile(profileId),
    onSuccess: () => invalidate(qc, positionId, profileId),
  });
}

export function useRequestChanges(profileId: string, positionId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: JobProfileReasonPayload) => requestChangesJobProfile(profileId, payload),
    onSuccess: () => invalidate(qc, positionId, profileId),
  });
}

export function useArchiveJobProfile(profileId: string, positionId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: JobProfileReasonPayload) => archiveJobProfile(profileId, payload),
    onSuccess: () => invalidate(qc, positionId, profileId),
  });
}

export function useCreateJobProfileRevision(profileId: string, positionId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => createJobProfileRevision(profileId),
    onSuccess: (created) => invalidate(qc, positionId, created.id),
  });
}
