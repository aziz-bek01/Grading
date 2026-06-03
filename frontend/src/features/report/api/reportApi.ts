/**
 * Report REST fetchers — 5 endpoints (architecture §17 / ADR-009).
 *
 *   POST   /api/v1/reports/request         — request a new report
 *   GET    /api/v1/reports                 — paged list (max size 200)
 *   GET    /api/v1/reports/{id}            — single report
 *   GET    /api/v1/reports/{id}/download-url — fresh 60s signed URL
 *   POST   /api/v1/reports/{id}/cancel     — cancel pre-GENERATED report
 *
 * NO tenant identifier is ever sent — backend derives it from the JWT.
 */
import { httpClient } from '@/shared/api/httpClient';
import type {
  Report,
  ReportFormat,
  ReportPage,
  ReportRequestPayload,
  ReportStatus,
  ReportType,
} from '../types';

export const reportKeys = {
  all: ['reports'] as const,
  list: (
    projectId: string | undefined,
    status?: ReportStatus,
    type?: ReportType,
    format?: ReportFormat,
  ) => ['reports', 'list', projectId ?? null, status ?? null, type ?? null, format ?? null] as const,
  detail: (id: string) => ['reports', 'detail', id] as const,
  downloadUrl: (id: string) => ['reports', 'downloadUrl', id] as const,
};

export async function requestReport(payload: ReportRequestPayload): Promise<Report> {
  const res = await httpClient.post<Report>('/reports/request', payload);
  return res.data;
}

export async function fetchReports(filters: {
  projectId?: string;
  status?: ReportStatus;
  type?: ReportType;
  format?: ReportFormat;
  requestedBy?: string;
  page?: number;
  size?: number;
}): Promise<ReportPage<Report>> {
  const params: Record<string, unknown> = {};
  if (filters.projectId) params.projectId = filters.projectId;
  if (filters.status) params.status = filters.status;
  if (filters.type) params.type = filters.type;
  // Backend does not currently filter by format server-side, but we keep the
  // param so the API surface is forward-compatible; ignored if unknown.
  if (filters.format) params.format = filters.format;
  if (filters.requestedBy) params.requestedBy = filters.requestedBy;
  if (filters.page !== undefined) params.page = filters.page;
  if (filters.size !== undefined) params.size = filters.size;
  const res = await httpClient.get<ReportPage<Report>>('/reports', { params });
  return res.data;
}

export async function fetchReport(id: string): Promise<Report> {
  const res = await httpClient.get<Report>(`/reports/${id}`);
  return res.data;
}

/**
 * Returns a fresh 60-second signed URL. Call site triggers the download
 * via `SignedDownloadButton`. The 60s TTL matches backend SECURITY blueprint
 * §6 and is enforced by `ObjectStorageAdapter.MAX_SIGNED_URL_TTL`.
 */
export async function fetchReportDownloadUrl(id: string): Promise<string> {
  const res = await httpClient.get<{ url: string }>(`/reports/${id}/download-url`);
  return res.data.url;
}

export async function cancelReport(id: string): Promise<Report> {
  const res = await httpClient.post<Report>(`/reports/${id}/cancel`, {});
  return res.data;
}
