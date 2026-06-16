/**
 * TanStack Query hooks over the 5 report fetchers.
 *
 * `useReport(id, { pollWhileInFlight })` polls every 2 s while the report
 * is in the REQUESTED / QUEUED / GENERATING set so the UI tracks the async
 * worker pipeline (architecture §17 worker FSM).
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  cancelReport,
  downloadReport,
  fetchReport,
  fetchReports,
  reportKeys,
  requestReport,
} from '../api/reportApi';
import { REPORT_IN_FLIGHT_STATUSES } from '../types';
import type {
  ReportFormat,
  ReportRequestPayload,
  ReportStatus,
  ReportType,
} from '../types';

export function useReports(filters: {
  projectId?: string;
  status?: ReportStatus;
  type?: ReportType;
  format?: ReportFormat;
  page?: number;
  size?: number;
}) {
  return useQuery({
    queryKey: reportKeys.list(filters.projectId, filters.status, filters.type, filters.format),
    queryFn: () => fetchReports(filters),
    enabled: !!filters.projectId,
  });
}

export function useReport(id: string | undefined, opts?: { pollWhileInFlight?: boolean }) {
  return useQuery({
    queryKey: id ? reportKeys.detail(id) : ['reports', 'detail', null],
    queryFn: () => fetchReport(id!),
    enabled: !!id,
    refetchInterval: (query) => {
      if (!opts?.pollWhileInFlight) return false;
      const data = query.state.data as { status?: ReportStatus } | undefined;
      if (!data) return 2000;
      return REPORT_IN_FLIGHT_STATUSES.has(data.status as ReportStatus) ? 2000 : false;
    },
  });
}

export function useRequestReport() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: ReportRequestPayload) => requestReport(payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: reportKeys.all }),
  });
}

export function useCancelReport(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => cancelReport(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: reportKeys.all });
      qc.invalidateQueries({ queryKey: reportKeys.detail(id) });
    },
  });
}

/**
 * Streams the generated report through the authenticated httpClient and
 * triggers a browser download. Bytes (not a token-bearing URL) flow through
 * the API layer so Authorization + X-Active-Tenant-Id headers are attached;
 * a bare `<a href>` would omit them and the server would reject with 401/403.
 */
export function useDownloadReport() {
  return useMutation({
    mutationFn: (args: { id: string; type?: ReportType }) =>
      downloadReport(args.id, { type: args.type }),
  });
}
