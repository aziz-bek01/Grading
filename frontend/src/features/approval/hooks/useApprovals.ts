import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '@/features/auth/authStore';
import {
  approvalKeys,
  approveStep,
  cancelApprovalRequest,
  createApprovalRequest,
  fetchApprovalRequest,
  fetchApprovalRequests,
  fetchMyInbox,
  rejectStep,
  requestChangesStep,
} from '../api/approvalApi';
import type {
  ApprovalRequestCreatePayload,
  ApprovalRequestListFilters,
} from '../types';

export function useApprovalRequests(filters: ApprovalRequestListFilters = {}) {
  return useQuery({
    queryKey: approvalKeys.list(filters),
    queryFn: () => fetchApprovalRequests(filters),
  });
}

export function useApprovalRequest(id: string | undefined) {
  return useQuery({
    queryKey: id ? approvalKeys.detail(id) : ['approvals', 'detail', null],
    queryFn: () => fetchApprovalRequest(id!),
    enabled: !!id,
  });
}

export function useMyApprovalInbox() {
  // FE-1 (2026-06-12) — close the unattended-poll wrong-tenant window.
  //
  // The 30s inbox poll (and the sidebar badge that shares this query) must
  // NEVER fire before an active company-client is selected: a poll fired without
  // X-Active-Tenant-Id reached a backend that, for a multi-membership user,
  // silently defaulted to some tenant and returned its rows (the cross-tenant
  // inbox leak). Now:
  //   - `enabled: !!activeTenantId` blocks the query (and the poll) until a
  //     tenant is active;
  //   - the query key INCLUDES the active tenant id, so switching companies
  //     invalidates the cached inbox/badge instead of showing the previous
  //     tenant's rows.
  const activeTenantId = useAuthStore((s) => s.activeTenant?.id ?? null);
  return useQuery({
    queryKey: approvalKeys.myInboxForTenant(activeTenantId),
    queryFn: fetchMyInbox,
    enabled: !!activeTenantId,
    refetchInterval: 30_000,
    // Don't poll the inbox/badge while the tab is hidden (P1-T2) — resumes on
    // return; window-focus refetch (global default) keeps the badge fresh.
    refetchIntervalInBackground: false,
  });
}

export function useCreateApprovalRequest() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: ApprovalRequestCreatePayload) => createApprovalRequest(payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: approvalKeys.all });
    },
  });
}

export function useApproveStep(approvalId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { stepId: string; notes?: string }) =>
      approveStep(approvalId!, vars.stepId, vars.notes),
    onSuccess: (data) => {
      if (approvalId) qc.setQueryData(approvalKeys.detail(approvalId), data);
      qc.invalidateQueries({ queryKey: approvalKeys.all });
    },
  });
}

export function useRejectStep(approvalId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { stepId: string; reason: string }) =>
      rejectStep(approvalId!, vars.stepId, vars.reason),
    onSuccess: (data) => {
      if (approvalId) qc.setQueryData(approvalKeys.detail(approvalId), data);
      qc.invalidateQueries({ queryKey: approvalKeys.all });
    },
  });
}

export function useRequestChangesStep(approvalId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { stepId: string; reason: string }) =>
      requestChangesStep(approvalId!, vars.stepId, vars.reason),
    onSuccess: (data) => {
      if (approvalId) qc.setQueryData(approvalKeys.detail(approvalId), data);
      qc.invalidateQueries({ queryKey: approvalKeys.all });
    },
  });
}

export function useCancelApprovalRequest(approvalId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => cancelApprovalRequest(approvalId!),
    onSuccess: (data) => {
      if (approvalId) qc.setQueryData(approvalKeys.detail(approvalId), data);
      qc.invalidateQueries({ queryKey: approvalKeys.all });
    },
  });
}
