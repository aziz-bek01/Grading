import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
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
  ExportJobStatus,
  ExportRequestPayload,
  ExportType,
} from '../types';

const IN_FLIGHT: ReadonlySet<ExportJobStatus> = new Set<ExportJobStatus>([
  'REQUESTED',
  'QUEUED',
  'GENERATING',
]);

export function useExports(filters: {
  projectId?: string;
  status?: ExportJobStatus;
  type?: ExportType;
  page?: number;
  size?: number;
}) {
  return useQuery({
    queryKey: exportKeys.list(filters.projectId, filters.status, filters.type),
    queryFn: () => fetchExports(filters),
    enabled: !!filters.projectId,
  });
}

export function useExport(id: string | undefined, opts?: { pollWhileInFlight?: boolean }) {
  return useQuery({
    queryKey: id ? exportKeys.detail(id) : ['exports', 'detail', null],
    queryFn: () => fetchExport(id!),
    enabled: !!id,
    refetchInterval: (query) => {
      if (!opts?.pollWhileInFlight) return false;
      const data = query.state.data as { status?: ExportJobStatus } | undefined;
      if (!data) return 2000;
      return IN_FLIGHT.has(data.status as ExportJobStatus) ? 2000 : false;
    },
  });
}

export function useRequestExport() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: ExportRequestPayload) => requestExport(payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: exportKeys.all }),
  });
}

export function useCancelExport(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => cancelExport(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: exportKeys.all });
      qc.invalidateQueries({ queryKey: exportKeys.detail(id) });
    },
  });
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
