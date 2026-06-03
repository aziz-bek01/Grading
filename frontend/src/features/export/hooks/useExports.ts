import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  cancelExport,
  exportKeys,
  fetchExport,
  fetchExportDownloadUrl,
  fetchExports,
  requestExport,
} from '../api/exportApi';
import type {
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

/** One-shot fetcher for a fresh signed URL (5-minute lifetime). */
export function useFetchDownloadUrl() {
  return useMutation({
    mutationFn: (id: string) => fetchExportDownloadUrl(id),
  });
}
