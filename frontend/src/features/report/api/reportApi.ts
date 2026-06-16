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
import { downloadAuthenticatedFile } from '@/shared/api/downloadFile';
import type {
  Report,
  ReportFormat,
  ReportPage,
  ReportRequestPayload,
  ReportStatus,
  ReportType,
} from '../types';

/* ------------------------------------------------------------------ *
 * Wire → domain adapter
 *
 * The real backend serialises every response in SNAKE_CASE (global Jackson)
 * with `@JsonInclude(NON_NULL)`. The Report domain type + every consumer
 * (Reports Center table, detail page, date/actor badges) read camelCase.
 * Without this map `requestedAt`/`generatedAt` are `undefined` → "Invalid
 * Date" badges and `projectId`/`requestedBy` columns are blank (scout
 * sweep #4). Mirrors `normalizeApprovalRequest`: snake_case FIRST with a
 * camelCase fallback so the MSW mock and the real backend both deserialise.
 * Single adapter — no per-component mapping.
 * ------------------------------------------------------------------ */

type Raw = Record<string, unknown>;

function pick<T = string>(raw: Raw, snake: string, camel: string): T | null {
  return ((raw[snake] ?? raw[camel]) as T | undefined) ?? null;
}

function pickBool(raw: Raw, snake: string, camel: string): boolean {
  return Boolean(raw[snake] ?? raw[camel] ?? false);
}

export function normalizeReport(input: unknown): Report {
  const raw = (input ?? {}) as Raw;
  return {
    id: String(raw.id ?? ''),
    projectId: pick(raw, 'project_id', 'projectId') ?? '',
    reportType: (pick(raw, 'report_type', 'reportType') ?? '') as ReportType,
    format: (pick(raw, 'format', 'format') ?? 'PDF') as ReportFormat,
    status: (pick(raw, 'status', 'status') ?? 'REQUESTED') as ReportStatus,
    title: pick(raw, 'title', 'title'),
    locale: pick(raw, 'locale', 'locale'),
    requestedBy: pick(raw, 'requested_by', 'requestedBy'),
    requestedAt: pick(raw, 'requested_at', 'requestedAt') ?? '',
    generatedAt: pick(raw, 'generated_at', 'generatedAt'),
    expiresAt: pick(raw, 'expires_at', 'expiresAt'),
    downloadedAt: pick(raw, 'downloaded_at', 'downloadedAt'),
    fileSize: pick<number>(raw, 'file_size', 'fileSize'),
    containsSalaryData: pickBool(raw, 'contains_salary_data', 'containsSalaryData'),
    containsPersonalData: pickBool(raw, 'contains_personal_data', 'containsPersonalData'),
    attemptCount: Number(pick<number>(raw, 'attempt_count', 'attemptCount') ?? 0),
    failureReason: pick(raw, 'failure_reason', 'failureReason'),
    traceId: pick(raw, 'trace_id', 'traceId'),
  };
}

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
  const res = await httpClient.post<unknown>('/reports/request', payload);
  return normalizeReport(res.data);
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
  const res = await httpClient.get<unknown>('/reports', { params });
  const env = (res.data ?? {}) as Record<string, unknown>;
  const rawItems = Array.isArray(env.items) ? (env.items as unknown[]) : [];
  return {
    items: rawItems.map((r) => normalizeReport(r)),
    page: Number(env.page ?? 0),
    size: Number(env.size ?? rawItems.length),
    totalElements: Number(env.total_elements ?? env.totalElements ?? rawItems.length),
    totalPages: Number(env.total_pages ?? env.totalPages ?? 1),
  };
}

export async function fetchReport(id: string): Promise<Report> {
  const res = await httpClient.get<unknown>(`/reports/${id}`);
  return normalizeReport(res.data);
}

/**
 * Deterministic same-origin download path for a report (relative to the
 * httpClient baseURL `/api/v1`). The backend `download-url` endpoint returns
 * exactly this path; using it directly avoids an extra round-trip AND keeps
 * the bytes flowing through the authenticated `httpClient` (Authorization +
 * X-Active-Tenant-Id headers), which a bare `<a href>` navigation cannot do.
 */
export function reportDownloadPath(id: string): string {
  return `/reports/${id}/download`;
}

/**
 * Streams the generated report bytes through the authenticated `httpClient`
 * and triggers a browser download. The filename is taken from the server's
 * Content-Disposition header (falls back to `<type>_<id>.<ext>`). Rejects with
 * the typed `ApiError` on 401/403/404/400 so the call site can localize it.
 */
export async function downloadReport(
  id: string,
  fallback: { type?: ReportType },
): Promise<void> {
  const base = (fallback.type ?? 'report').toLowerCase();
  await downloadAuthenticatedFile({
    path: reportDownloadPath(id),
    fallbackFilename: `${base}_${id}`,
  });
}

export async function cancelReport(id: string): Promise<Report> {
  const res = await httpClient.post<unknown>(`/reports/${id}/cancel`, {});
  return normalizeReport(res.data);
}
