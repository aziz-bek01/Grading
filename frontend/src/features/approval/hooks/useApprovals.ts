import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
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
  return useQuery({
    queryKey: approvalKeys.myInbox,
    queryFn: fetchMyInbox,
    refetchInterval: 30_000,
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
