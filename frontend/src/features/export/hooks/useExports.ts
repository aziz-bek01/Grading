/**
 * TanStack Query hooks over the 5 export fetchers.
 *
 * The list/detail-polling/request/cancel scaffolding is shared with
 * `features/report/hooks/useReports` via `createAsyncJobQueries` (see
 * `@/shared/hooks/asyncJobQueryFactory`) — only the fetchers, query-key
 * builders (`exportKeys`), filter shape, and in-flight status set are
 * export-specific. Query keys, the 2s polling interval, and the
 * invalidation behavior are unchanged from before the extraction.
 */
import { createAsyncJobQueries } from '@/shared/hooks/asyncJobQueryFactory';
import { useMutation } from '@tanstack/react-query';
import {
  cancelExport,
  downloadExport,
  exportKeys,
  fetchExport,
  fetchExports,
  requestExport,
} from '../api/exportApi';
import type {
  ExportFormat,
  ExportJob,
  ExportJobStatus,
  ExportPage,
  ExportRequestPayload,
  ExportType,
} from '../types';

const IN_FLIGHT: ReadonlySet<ExportJobStatus> = new Set<ExportJobStatus>([
  'REQUESTED',
  'QUEUED',
  'GENERATING',
]);

interface ExportListFilters {
  projectId?: string;
  status?: ExportJobStatus;
  type?: ExportType;
  page?: number;
  size?: number;
}

const exportJobQueries = createAsyncJobQueries<
  ExportJob,
  ExportJobStatus,
  ExportListFilters,
  ExportPage<ExportJob>,
  ExportRequestPayload
>({
  keys: {
    all: exportKeys.all,
    list: (filters) => exportKeys.list(filters.projectId, filters.status, filters.type),
    detail: exportKeys.detail,
  },
  fetchers: {
    fetchList: fetchExports,
    fetchDetail: fetchExport,
    request: requestExport,
    cancel: cancelExport,
  },
  inFlightStatuses: IN_FLIGHT,
});

export function useExports(filters: ExportListFilters) {
  return exportJobQueries.useList(filters);
}

export function useExport(id: string | undefined, opts?: { pollWhileInFlight?: boolean }) {
  return exportJobQueries.useDetail(id, opts);
}

export function useRequestExport() {
  return exportJobQueries.useRequest();
}

export function useCancelExport(id: string) {
  return exportJobQueries.useCancel(id);
}

/**
 * Streams the generated export through the authenticated httpClient and
 * triggers a browser download. Bytes (not a token-bearing URL) flow through
 * the API layer so Authorization + X-Active-Tenant-Id headers are attached;
 * a bare `<a href>` would omit them and the server would reject with 401/403.
 */
export function useDownloadExport() {
  return useMutation({
    mutationFn: (args: { id: string; type?: ExportType; format?: ExportFormat }) =>
      downloadExport(args.id, { type: args.type, format: args.format }),
  });
}
