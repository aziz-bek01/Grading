/**
 * Hooks for the Access-scope slice (E4-S1).
 *
 *   useUserScopes(userId, tenantId)   — read current ACTIVE scope sets.
 *   useSetDepartmentScopes(userId)    — REPLACE department scope (mutation).
 *   useSetProjectAssignments(userId)  — REPLACE project assignment (mutation).
 *
 * Both mutations invalidate the per-(user, tenant) scopes query on success so
 * the form re-prefills from the authoritative server set.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  fetchUserScopes,
  scopeKeys,
  setDepartmentScopes,
  setProjectAssignments,
} from '../api/scopeApi';
import type {
  SetDepartmentScopesPayload,
  SetProjectAssignmentsPayload,
} from '../types/scopeTypes';

export function useUserScopes(
  userId: string | undefined,
  tenantId: string | undefined,
) {
  return useQuery({
    queryKey:
      userId && tenantId
        ? scopeKeys.detail(userId, tenantId)
        : ['user-scopes', null],
    queryFn: () => fetchUserScopes(userId!, tenantId!),
    enabled: !!userId && !!tenantId,
  });
}

export function useSetDepartmentScopes(userId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: SetDepartmentScopesPayload) =>
      setDepartmentScopes(userId, payload),
    onSuccess: (_data, payload) => {
      void qc.invalidateQueries({
        queryKey: scopeKeys.detail(userId, payload.tenant_id),
      });
    },
  });
}

export function useSetProjectAssignments(userId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: SetProjectAssignmentsPayload) =>
      setProjectAssignments(userId, payload),
    onSuccess: (_data, payload) => {
      void qc.invalidateQueries({
        queryKey: scopeKeys.detail(userId, payload.tenant_id),
      });
    },
  });
}
