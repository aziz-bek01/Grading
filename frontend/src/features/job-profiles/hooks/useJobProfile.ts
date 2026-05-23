import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
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
import type { JobProfilePatch, JobProfileReasonPayload } from '../types';

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
