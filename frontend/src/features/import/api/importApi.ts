/**
 * Import REST fetchers — 7 endpoints (integration-blueprint §17).
 *
 * NO tenant identifier is ever sent: the backend derives the active tenant
 * from the JWT (security blueprint API-13).
 */
import { httpClient } from '@/shared/api/httpClient';
import type {
  ImportBatch,
  ImportBatchStatus,
  ImportError,
  ImportErrorLevel,
  ImportPage,
  ImportTemplateCode,
} from '../types';

export const importKeys = {
  all: ['imports'] as const,
  list: (projectId: string | undefined, status?: ImportBatchStatus, templateCode?: string) =>
    ['imports', 'list', projectId ?? null, status ?? null, templateCode ?? null] as const,
  detail: (id: string) => ['imports', 'detail', id] as const,
  errors: (id: string, level?: ImportErrorLevel) =>
    ['imports', 'errors', id, level ?? null] as const,
};

/**
 * Upload a multipart .xlsx file. Returns the freshly created ImportBatch.
 * Note: `projectId` is a SCOPE filter, NOT a tenant identifier — wire-safe.
 */
export async function uploadImport(payload: {
  file: File;
  templateCode: ImportTemplateCode;
  projectId?: string | null;
}): Promise<ImportBatch> {
  const formData = new FormData();
  formData.append('file', payload.file);
  const params: Record<string, string> = { templateCode: payload.templateCode };
  if (payload.projectId) params.projectId = payload.projectId;
  const res = await httpClient.post<ImportBatch>('/imports/upload', formData, {
    params,
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return res.data;
}

export async function fetchImports(filters: {
  projectId?: string;
  status?: ImportBatchStatus;
  templateCode?: string;
  page?: number;
  size?: number;
}): Promise<ImportPage<ImportBatch>> {
  const params: Record<string, unknown> = {};
  if (filters.projectId) params.projectId = filters.projectId;
  if (filters.status) params.status = filters.status;
  if (filters.templateCode) params.templateCode = filters.templateCode;
  if (filters.page !== undefined) params.page = filters.page;
  if (filters.size !== undefined) params.size = filters.size;
  const res = await httpClient.get<ImportPage<ImportBatch>>('/imports', { params });
  return res.data;
}

export async function fetchImport(id: string): Promise<ImportBatch> {
  const res = await httpClient.get<ImportBatch>(`/imports/${id}`);
  return res.data;
}

export async function fetchImportErrors(
  id: string,
  filters: { level?: ImportErrorLevel; page?: number; size?: number } = {},
): Promise<ImportPage<ImportError>> {
  const params: Record<string, unknown> = {};
  if (filters.level) params.level = filters.level;
  if (filters.page !== undefined) params.page = filters.page;
  if (filters.size !== undefined) params.size = filters.size;
  const res = await httpClient.get<ImportPage<ImportError>>(`/imports/${id}/errors`, { params });
  return res.data;
}

export async function commitImport(id: string): Promise<ImportBatch> {
  const res = await httpClient.post<ImportBatch>(`/imports/${id}/commit`, {});
  return res.data;
}

export async function cancelImport(id: string): Promise<ImportBatch> {
  const res = await httpClient.post<ImportBatch>(`/imports/${id}/cancel`, {});
  return res.data;
}
