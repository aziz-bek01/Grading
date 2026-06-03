import { httpClient } from '@/shared/api/httpClient';
import { endpoints } from '@/shared/api/endpoints';
import type {
  ApprovalRequest,
  ApprovalRequestCreatePayload,
  ApprovalRequestListFilters,
  ApprovalRequestListResponse,
  ApprovalRequestSummary,
} from '../types';

export const approvalKeys = {
  all: ['approvals'] as const,
  list: (filters?: ApprovalRequestListFilters) =>
    ['approvals', 'list', filters ?? {}] as const,
  detail: (id: string) => ['approvals', 'detail', id] as const,
  myInbox: ['approvals', 'my-inbox'] as const,
};

export async function createApprovalRequest(
  payload: ApprovalRequestCreatePayload,
): Promise<ApprovalRequest> {
  const res = await httpClient.post<ApprovalRequest>(endpoints.approval.requests, payload);
  return res.data;
}

export async function fetchApprovalRequests(
  filters: ApprovalRequestListFilters = {},
): Promise<ApprovalRequestListResponse> {
  const res = await httpClient.get<ApprovalRequestListResponse>(endpoints.approval.requests, {
    params: filters,
  });
  return res.data;
}

export async function fetchApprovalRequest(id: string): Promise<ApprovalRequest> {
  const res = await httpClient.get<ApprovalRequest>(endpoints.approval.detail(id));
  return res.data;
}

export async function approveStep(
  approvalId: string,
  stepId: string,
  notes?: string,
): Promise<ApprovalRequest> {
  const res = await httpClient.post<ApprovalRequest>(
    endpoints.approval.approveStep(approvalId, stepId),
    notes ? { notes } : {},
  );
  return res.data;
}

export async function rejectStep(
  approvalId: string,
  stepId: string,
  reason: string,
): Promise<ApprovalRequest> {
  const res = await httpClient.post<ApprovalRequest>(
    endpoints.approval.rejectStep(approvalId, stepId),
    { reason },
  );
  return res.data;
}

export async function requestChangesStep(
  approvalId: string,
  stepId: string,
  reason: string,
): Promise<ApprovalRequest> {
  const res = await httpClient.post<ApprovalRequest>(
    endpoints.approval.requestChangesStep(approvalId, stepId),
    { reason },
  );
  return res.data;
}

export async function cancelApprovalRequest(approvalId: string): Promise<ApprovalRequest> {
  const res = await httpClient.post<ApprovalRequest>(endpoints.approval.cancel(approvalId), {});
  return res.data;
}

export async function fetchMyInbox(): Promise<{ items: ApprovalRequestSummary[] }> {
  // Backend returns a raw array (`[]`), not a `{items: [...]}` envelope —
  // /my-inbox is a custom action endpoint, not a paged list. We normalise
  // here so existing callers can keep their `.items.length` shape.
  const res = await httpClient.get<ApprovalRequestSummary[] | { items: ApprovalRequestSummary[] }>(endpoints.approval.myInbox);
  if (Array.isArray(res.data)) return { items: res.data };
  return res.data;
}
