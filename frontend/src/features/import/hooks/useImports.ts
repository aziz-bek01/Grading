/**
 * React Query hooks for the Import lifecycle.
 *
 * The wizard refreshes the detail every 2s while the batch is in any of
 * the "in flight" statuses (SCANNING / PARSING / VALIDATING) so the user
 * sees the pipeline animate without manual refresh.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  cancelImport,
  commitImport,
  fetchImport,
  fetchImportErrors,
  fetchImports,
  importKeys,
  uploadImport,
} from '../api/importApi';
import type {
  ImportBatchStatus,
  ImportErrorLevel,
  ImportTemplateCode,
} from '../types';

const IN_FLIGHT: ReadonlySet<ImportBatchStatus> = new Set<ImportBatchStatus>([
  'UPLOADED',
  'SCANNING',
  'PARSING',
  'VALIDATING',
  'COMMITTING',
]);

export function useImports(filters: {
  projectId?: string;
  status?: ImportBatchStatus;
  templateCode?: string;
  page?: number;
  size?: number;
}) {
  return useQuery({
    queryKey: importKeys.list(filters.projectId, filters.status, filters.templateCode),
    queryFn: () => fetchImports(filters),
    enabled: !!filters.projectId,
  });
}

export function useImport(id: string | undefined, opts?: { pollWhileInFlight?: boolean }) {
  return useQuery({
    queryKey: id ? importKeys.detail(id) : ['imports', 'detail', null],
    queryFn: () => fetchImport(id!),
    enabled: !!id,
    refetchInterval: (query) => {
      if (!opts?.pollWhileInFlight) return false;
      const data = query.state.data as { status?: ImportBatchStatus } | undefined;
      if (!data) return 2000;
      return IN_FLIGHT.has(data.status as ImportBatchStatus) ? 2000 : false;
    },
  });
}

export function useImportErrors(
  id: string | undefined,
  filters: { level?: ImportErrorLevel; page?: number; size?: number } = {},
) {
  return useQuery({
    queryKey: id ? importKeys.errors(id, filters.level) : ['imports', 'errors', null],
    queryFn: () => fetchImportErrors(id!, filters),
    enabled: !!id,
  });
}

export function useUploadImport() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: { file: File; templateCode: ImportTemplateCode; projectId?: string | null }) =>
      uploadImport(payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: importKeys.all });
    },
  });
}

export function useCommitImport(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => commitImport(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: importKeys.all });
      qc.invalidateQueries({ queryKey: importKeys.detail(id) });
    },
  });
}

export function useCancelImport(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => cancelImport(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: importKeys.all });
      qc.invalidateQueries({ queryKey: importKeys.detail(id) });
    },
  });
}
