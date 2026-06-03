/**
 * Export — MVP 2 Phase 2 domain types.
 *
 * Tenant identifiers are NEVER carried by these types — backend derives the
 * active tenant from the JWT (security blueprint API-13).
 */

export type ExportJobStatus =
  | 'REQUESTED'
  | 'QUEUED'
  | 'GENERATING'
  | 'GENERATED'
  | 'FAILED'
  | 'DOWNLOADED'
  | 'EXPIRED'
  | 'CANCELLED';

export type ExportType =
  | 'POSITION_CATALOG'
  | 'JOB_PROFILES'
  | 'METHODOLOGY'
  | 'EVALUATION_MATRIX'
  | 'GRADE_STRUCTURE'
  | 'GRADE_PYRAMID'
  | 'SALARY_RANGES'
  | 'RED_GREEN_CIRCLE'
  | 'COMPENSATION_SCENARIOS'
  | 'REPORT_EXECUTIVE';

export type ExportFormat = 'XLSX' | 'CSV' | 'PDF' | 'DOCX';

export interface ExportJob {
  id: string;
  projectId: string;
  exportType: ExportType;
  format: ExportFormat;
  status: ExportJobStatus;
  requestedBy?: string | null;
  requestedAt: string;
  generatedAt?: string | null;
  expiresAt?: string | null;
  downloadedAt?: string | null;
  rowCount?: number | null;
  fileSize?: number | null;
  containsSalaryData: boolean;
  containsPersonalData: boolean;
  traceId?: string | null;
}

export interface ExportRequestPayload {
  exportType: ExportType;
  format: ExportFormat;
  projectId: string;
  filterParams?: string | null;
}

export interface ExportPage<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
