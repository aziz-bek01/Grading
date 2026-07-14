import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  approveJobProfile,
  archiveJobProfile,
  createJobProfile,
  createJobProfileRevision,
  fetchJobProfileById,
  fetchJobProfileByPosition,
  fetchJobProfileRevisions,
  fetchJobProfileStatusesByPositions,
  jobProfileKeys,
  patchJobProfile,
  requestChangesJobProfile,
  submitJobProfile,
} from '../api/jobProfileApi';
import type { JobProfilePatch, JobProfileReasonPayload, JobProfileStatusByPosition } from '../types';

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
 * Return shape of {@link useJobProfileStatusesByPositions} — a small result
 * object (rather than a bare `Map`) so callers can tell "still loading" apart
 * from "the bulk request failed" instead of both collapsing into the same
 * `undefined` map entries forever.
 */
export interface JobProfileStatusesByPositionsResult {
  /** 3-state per-id map — see the state contract documented on the hook. */
  statuses: Map<string, JobProfileStatusByPosition | null | undefined>;
  /** The bulk request is in flight (first fetch, or ids changed). */
  isLoading: boolean;
  /**
   * The bulk request settled with a failure (after any retries) — every id
   * still maps to `undefined` in `statuses`, but callers should render a
   * distinguishable "couldn't load" affordance instead of an indefinite
   * loading placeholder.
   */
  isError: boolean;
}

/**
 * Bulk job-profile STATUS lookup for a bounded set of position ids — e.g. one
 * server page of the Position Catalog (PAGE-2 fix). Backed by a SINGLE call
 * to `GET /job-profiles/statuses?positionIds=...` (one request total, not one
 * per row — the earlier `useQueries` client fan-out is gone now that the
 * bulk endpoint exists).
 *
 * Callers MUST still bound `positionIds` to something small (a single
 * paginated table page, not a whole project) — the endpoint caps at 200 ids.
 *
 * This hook intentionally does NOT seed `jobProfileKeys.byPosition` / the
 * full-profile detail cache: the bulk response only carries
 * `{ position_id, status, job_profile_id, revision_number }`, not a full
 * `JobProfile`, so writing it into those caches would corrupt them for any
 * consumer expecting the complete shape (e.g. the Job Profile editor tab).
 *
 * `statuses` map values preserve the SAME 3-state contract the fan-out
 * version had:
 *  - `undefined` = still loading, OR the fetch failed (check the sibling
 *    `isError` flag to tell the two apart) — every requested id maps to
 *    `undefined` until the single query resolves successfully
 *    (`query.isSuccess`).
 *  - `null` = confirmed no active profile — the id was requested but is
 *    ABSENT from the bulk response (backend omits positions with no active,
 *    non-ARCHIVED profile, or outside the caller's ABAC scope).
 *  - a `JobProfileStatusByPosition` = the id IS present in the response, i.e.
 *    has an active profile.
 */
export function useJobProfileStatusesByPositions(
  positionIds: string[],
): JobProfileStatusesByPositionsResult {
  const query = useQuery({
    queryKey: jobProfileKeys.statusesByPositions(positionIds),
    queryFn: () => fetchJobProfileStatusesByPositions(positionIds),
    enabled: positionIds.length > 0,
  });

  const statuses = new Map<string, JobProfileStatusByPosition | null | undefined>();
  if (!query.isSuccess) {
    positionIds.forEach((id) => statuses.set(id, undefined));
    return { statuses, isLoading: query.isLoading, isError: query.isError };
  }
  const byId = new Map(query.data.map((entry) => [entry.position_id, entry] as const));
  positionIds.forEach((id) => statuses.set(id, byId.get(id) ?? null));
  return { statuses, isLoading: false, isError: false };
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
