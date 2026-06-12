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

/* ------------------------------------------------------------------ *
 * Wire → domain adapter
 *
 * The real backend serialises every response in SNAKE_CASE (global Jackson)
 * with `@JsonInclude(NON_NULL)`. The Import domain types + every consumer
 * (Import Center upload history, batch details, error table) read camelCase.
 * Without this map `uploadedAt` is `undefined` → "Invalid Date" and the
 * `projectId` column is blank (scout sweep #5). Mirrors
 * `normalizeApprovalRequest`: snake_case FIRST with a camelCase fallback so
 * both the MSW mock and the real backend deserialise. Single adapter — no
 * per-component mapping.
 * ------------------------------------------------------------------ */

type Raw = Record<string, unknown>;

function pick<T = string>(raw: Raw, snake: string, camel: string): T | null {
  return ((raw[snake] ?? raw[camel]) as T | undefined) ?? null;
}

function pickBool(raw: Raw, snake: string, camel: string): boolean {
  return Boolean(raw[snake] ?? raw[camel] ?? false);
}

export function normalizeImportBatch(input: unknown): ImportBatch {
  const raw = (input ?? {}) as Raw;
  return {
    id: String(raw.id ?? ''),
    projectId: pick(raw, 'project_id', 'projectId'),
    templateCode: (pick(raw, 'template_code', 'templateCode') ?? '') as
      | ImportTemplateCode
      | string,
    status: (pick(raw, 'status', 'status') ?? 'UPLOADED') as ImportBatchStatus,
    originalFilename: pick(raw, 'original_filename', 'originalFilename') ?? '',
    fileSize: Number(pick<number>(raw, 'file_size', 'fileSize') ?? 0),
    fileChecksum: pick(raw, 'file_checksum', 'fileChecksum'),
    totalRowCount: pick<number>(raw, 'total_row_count', 'totalRowCount'),
    errorRowCount: pick<number>(raw, 'error_row_count', 'errorRowCount'),
    warningRowCount: pick<number>(raw, 'warning_row_count', 'warningRowCount'),
    committedRowCount: pick<number>(raw, 'committed_row_count', 'committedRowCount'),
    containsSalaryData: pickBool(raw, 'contains_salary_data', 'containsSalaryData'),
    uploadedBy: pick(raw, 'uploaded_by', 'uploadedBy'),
    uploadedAt: pick(raw, 'uploaded_at', 'uploadedAt') ?? '',
    committedBy: pick(raw, 'committed_by', 'committedBy'),
    committedAt: pick(raw, 'committed_at', 'committedAt'),
    traceId: pick(raw, 'trace_id', 'traceId'),
  };
}

export function normalizeImportError(input: unknown): ImportError {
  const raw = (input ?? {}) as Raw;
  return {
    id: String(raw.id ?? ''),
    importBatchRowId: pick(raw, 'import_batch_row_id', 'importBatchRowId'),
    errorLevel: (pick(raw, 'error_level', 'errorLevel') ?? 'ERROR') as ImportErrorLevel,
    errorCode: pick(raw, 'error_code', 'errorCode') ?? '',
    fieldName: pick(raw, 'field_name', 'fieldName'),
    message: pick(raw, 'message', 'message') ?? '',
    suggestedFix: pick(raw, 'suggested_fix', 'suggestedFix'),
    rowNumber: pick<number>(raw, 'row_number', 'rowNumber'),
    traceId: pick(raw, 'trace_id', 'traceId'),
  };
}

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
  const res = await httpClient.post<unknown>('/imports/upload', formData, {
    params,
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return normalizeImportBatch(res.data);
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
  const res = await httpClient.get<unknown>('/imports', { params });
  const env = (res.data ?? {}) as Record<string, unknown>;
  const rawItems = Array.isArray(env.items) ? (env.items as unknown[]) : [];
  return {
    items: rawItems.map((r) => normalizeImportBatch(r)),
    page: Number(env.page ?? 0),
    size: Number(env.size ?? rawItems.length),
    totalElements: Number(env.total_elements ?? env.totalElements ?? rawItems.length),
    totalPages: Number(env.total_pages ?? env.totalPages ?? 1),
  };
}

export async function fetchImport(id: string): Promise<ImportBatch> {
  const res = await httpClient.get<unknown>(`/imports/${id}`);
  return normalizeImportBatch(res.data);
}

export async function fetchImportErrors(
  id: string,
  filters: { level?: ImportErrorLevel; page?: number; size?: number } = {},
): Promise<ImportPage<ImportError>> {
  const params: Record<string, unknown> = {};
  if (filters.level) params.level = filters.level;
  if (filters.page !== undefined) params.page = filters.page;
  if (filters.size !== undefined) params.size = filters.size;
  const res = await httpClient.get<unknown>(`/imports/${id}/errors`, { params });
  const env = (res.data ?? {}) as Record<string, unknown>;
  const rawItems = Array.isArray(env.items) ? (env.items as unknown[]) : [];
  return {
    items: rawItems.map((r) => normalizeImportError(r)),
    page: Number(env.page ?? 0),
    size: Number(env.size ?? rawItems.length),
    totalElements: Number(env.total_elements ?? env.totalElements ?? rawItems.length),
    totalPages: Number(env.total_pages ?? env.totalPages ?? 1),
  };
}

export async function commitImport(id: string): Promise<ImportBatch> {
  const res = await httpClient.post<unknown>(`/imports/${id}/commit`, {});
  return normalizeImportBatch(res.data);
}

export async function cancelImport(id: string): Promise<ImportBatch> {
  const res = await httpClient.post<unknown>(`/imports/${id}/cancel`, {});
  return normalizeImportBatch(res.data);
}
