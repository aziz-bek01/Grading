/**
 * Export REST fetchers — 5 endpoints (integration-blueprint §17).
 *
 * NO tenant identifier is ever sent — backend derives it from the JWT.
 */
import { httpClient } from '@/shared/api/httpClient';
import type {
  ExportJob,
  ExportJobStatus,
  ExportPage,
  ExportRequestPayload,
  ExportType,
} from '../types';

export const exportKeys = {
  all: ['exports'] as const,
  list: (projectId: string | undefined, status?: ExportJobStatus, type?: ExportType) =>
    ['exports', 'list', projectId ?? null, status ?? null, type ?? null] as const,
  detail: (id: string) => ['exports', 'detail', id] as const,
  downloadUrl: (id: string) => ['exports', 'downloadUrl', id] as const,
};

export async function requestExport(payload: ExportRequestPayload): Promise<ExportJob> {
  const res = await httpClient.post<ExportJob>('/exports/request', payload);
  return res.data;
}

export async function fetchExports(filters: {
  projectId?: string;
  status?: ExportJobStatus;
  type?: ExportType;
  requestedBy?: string;
  page?: number;
  size?: number;
}): Promise<ExportPage<ExportJob>> {
  const params: Record<string, unknown> = {};
  if (filters.projectId) params.projectId = filters.projectId;
  if (filters.status) params.status = filters.status;
  if (filters.type) params.type = filters.type;
  if (filters.requestedBy) params.requestedBy = filters.requestedBy;
  if (filters.page !== undefined) params.page = filters.page;
  if (filters.size !== undefined) params.size = filters.size;
  const res = await httpClient.get<ExportPage<ExportJob>>('/exports', { params });
  return res.data;
}

export async function fetchExport(id: string): Promise<ExportJob> {
  const res = await httpClient.get<ExportJob>(`/exports/${id}`);
  return res.data;
}

/** Returns a fresh 5-minute signed URL. Call site triggers the download. */
export async function fetchExportDownloadUrl(id: string): Promise<string> {
  const res = await httpClient.get<{ url: string }>(`/exports/${id}/download-url`);
  return res.data.url;
}

export async function cancelExport(id: string): Promise<ExportJob> {
  const res = await httpClient.post<ExportJob>(`/exports/${id}/cancel`, {});
  return res.data;
}
