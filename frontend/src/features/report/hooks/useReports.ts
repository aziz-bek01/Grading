/**
 * TanStack Query hooks over the 5 report fetchers.
 *
 * The list/detail-polling/request/cancel scaffolding is shared with
 * `features/export/hooks/useExports` via `createAsyncJobQueries` (see
 * `@/shared/hooks/asyncJobQueryFactory`) — only the fetchers, query-key
 * builders (`reportKeys`), filter shape, and in-flight status set are
 * report-specific. Query keys, the 2s polling interval, and the
 * invalidation behavior are unchanged from before the extraction.
 *
 * `useReport(id, { pollWhileInFlight })` polls every 2 s while the report
 * is in the REQUESTED / QUEUED / GENERATING set so the UI tracks the async
 * worker pipeline (architecture §17 worker FSM).
 */
import { createAsyncJobQueries } from '@/shared/hooks/asyncJobQueryFactory';
import { useMutation } from '@tanstack/react-query';
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
  Report,
  ReportFormat,
  ReportPage,
  ReportRequestPayload,
  ReportStatus,
  ReportType,
} from '../types';

interface ReportListFilters {
  projectId?: string;
  status?: ReportStatus;
  type?: ReportType;
  format?: ReportFormat;
  page?: number;
  size?: number;
}

const reportJobQueries = createAsyncJobQueries<
  Report,
  ReportStatus,
  ReportListFilters,
  ReportPage<Report>,
  ReportRequestPayload
>({
  keys: {
    all: reportKeys.all,
    list: (filters) =>
      reportKeys.list(filters.projectId, filters.status, filters.type, filters.format),
    detail: reportKeys.detail,
  },
  fetchers: {
    fetchList: fetchReports,
    fetchDetail: fetchReport,
    request: requestReport,
    cancel: cancelReport,
  },
  inFlightStatuses: REPORT_IN_FLIGHT_STATUSES,
});

export function useReports(filters: ReportListFilters) {
  return reportJobQueries.useList(filters);
}

export function useReport(id: string | undefined, opts?: { pollWhileInFlight?: boolean }) {
  return reportJobQueries.useDetail(id, opts);
}

export function useRequestReport() {
  return reportJobQueries.useRequest();
}

export function useCancelReport(id: string) {
  return reportJobQueries.useCancel(id);
}

/**
 * Streams the generated report through the authenticated httpClient and
 * triggers a browser download. Bytes (not a token-bearing URL) flow through
 * the API layer so Authorization + X-Active-Tenant-Id headers are attached;
 * a bare `<a href>` would omit them and the server would reject with 401/403.
 */
export function useDownloadReport() {
  return useMutation({
    mutationFn: (args: { id: string; type?: ReportType }) => downloadReport(args.id, { type: args.type }),
  });
}
