/**
 * Export REST fetchers — 5 endpoints (integration-blueprint §17).
 *
 * NO tenant identifier is ever sent — backend derives it from the JWT.
 */
import { httpClient } from '@/shared/api/httpClient';
import type {
  ExportFormat,
  ExportJob,
  ExportJobStatus,
  ExportPage,
  ExportRequestPayload,
  ExportType,
} from '../types';

/* ------------------------------------------------------------------ *
 * Wire → domain adapter
 *
 * The real backend serialises every response in SNAKE_CASE (global Jackson
 * `SNAKE_CASE`) with `@JsonInclude(NON_NULL)`. The export domain type +
 * every consumer (Export Center table, detail page, status badges) read
 * camelCase. Without this map `requestedAt`/`generatedAt` are `undefined`
 * → "Invalid Date" badges, and `projectId`/`requestedBy` columns are blank
 * (scout sweep #4). This mirrors `normalizeApprovalRequest` (approvalApi.ts):
 * read snake_case FIRST with a camelCase fallback so both the MSW mock and
 * the real backend deserialise. This is the ONLY case-translation point —
 * no per-component mapping.
 * ------------------------------------------------------------------ */

type Raw = Record<string, unknown>;

/** `(raw[snake] ?? raw[camel]) ?? null` — snake_case first, camelCase fallback. */
function pick<T = string>(raw: Raw, snake: string, camel: string): T | null {
  return ((raw[snake] ?? raw[camel]) as T | undefined) ?? null;
}

function pickBool(raw: Raw, snake: string, camel: string): boolean {
  return Boolean(raw[snake] ?? raw[camel] ?? false);
}

export function normalizeExportJob(input: unknown): ExportJob {
  const raw = (input ?? {}) as Raw;
  return {
    id: String(raw.id ?? ''),
    projectId: pick(raw, 'project_id', 'projectId') ?? '',
    exportType: (pick(raw, 'export_type', 'exportType') ?? '') as ExportType,
    format: (pick(raw, 'format', 'format') ?? 'XLSX') as ExportFormat,
    status: (pick(raw, 'status', 'status') ?? 'REQUESTED') as ExportJobStatus,
    requestedBy: pick(raw, 'requested_by', 'requestedBy'),
    requestedAt: pick(raw, 'requested_at', 'requestedAt') ?? '',
    generatedAt: pick(raw, 'generated_at', 'generatedAt'),
    expiresAt: pick(raw, 'expires_at', 'expiresAt'),
    downloadedAt: pick(raw, 'downloaded_at', 'downloadedAt'),
    rowCount: pick<number>(raw, 'row_count', 'rowCount'),
    fileSize: pick<number>(raw, 'file_size', 'fileSize'),
    containsSalaryData: pickBool(raw, 'contains_salary_data', 'containsSalaryData'),
    containsPersonalData: pickBool(raw, 'contains_personal_data', 'containsPersonalData'),
    traceId: pick(raw, 'trace_id', 'traceId'),
  };
}

export const exportKeys = {
  all: ['exports'] as const,
  list: (projectId: string | undefined, status?: ExportJobStatus, type?: ExportType) =>
    ['exports', 'list', projectId ?? null, status ?? null, type ?? null] as const,
  detail: (id: string) => ['exports', 'detail', id] as const,
  downloadUrl: (id: string) => ['exports', 'downloadUrl', id] as const,
};

export async function requestExport(payload: ExportRequestPayload): Promise<ExportJob> {
  const res = await httpClient.post<unknown>('/exports/request', payload);
  return normalizeExportJob(res.data);
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
  const res = await httpClient.get<unknown>('/exports', { params });
  const env = (res.data ?? {}) as Record<string, unknown>;
  const rawItems = Array.isArray(env.items) ? (env.items as unknown[]) : [];
  return {
    items: rawItems.map((r) => normalizeExportJob(r)),
    page: Number(env.page ?? 0),
    size: Number(env.size ?? rawItems.length),
    totalElements: Number(env.total_elements ?? env.totalElements ?? rawItems.length),
    totalPages: Number(env.total_pages ?? env.totalPages ?? 1),
  };
}

export async function fetchExport(id: string): Promise<ExportJob> {
  const res = await httpClient.get<unknown>(`/exports/${id}`);
  return normalizeExportJob(res.data);
}

/** Returns a fresh 5-minute signed URL. Call site triggers the download. */
export async function fetchExportDownloadUrl(id: string): Promise<string> {
  const res = await httpClient.get<{ url: string }>(`/exports/${id}/download-url`);
  return res.data.url;
}

export async function cancelExport(id: string): Promise<ExportJob> {
  const res = await httpClient.post<unknown>(`/exports/${id}/cancel`, {});
  return normalizeExportJob(res.data);
}
