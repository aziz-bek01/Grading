/**
 * Import — MVP 2 Phase 2 domain types.
 *
 * Tenant identifiers are NEVER carried by these types — the backend derives
 * the active tenant from the JWT (security blueprint API-13).
 */

export type ImportBatchStatus =
  | 'UPLOADED'
  | 'SCANNING'
  | 'SCAN_FAILED'
  | 'PARSING'
  | 'VALIDATING'
  | 'VALIDATION_FAILED'
  | 'READY_FOR_REVIEW'
  | 'READY_TO_COMMIT'
  | 'COMMITTING'
  | 'COMMITTED'
  | 'PARTIALLY_COMMITTED'
  | 'FAILED'
  | 'CANCELLED'
  | 'ARCHIVED';

/**
 * Statuses from which a batch may be CANCELLED — MUST mirror the backend
 * state machine exactly (`ImportBatchStatusTransitionPolicy`,
 * integration-blueprint §8.1). Once rows are committed (PARTIALLY_COMMITTED /
 * COMMITTED) or the batch is already terminal, cancel is no longer a legal
 * transition — the backend rejects it with `IMPORT_BATCH_TRANSITION_REJECTED`
 * (409). Centralized here (not scattered across pages) so the FE gate can
 * never silently drift from the BE policy again.
 */
const CANCELLABLE_IMPORT_STATUSES: ReadonlySet<ImportBatchStatus> = new Set([
  'UPLOADED',
  'SCANNING',
  'PARSING',
  'VALIDATING',
  'READY_FOR_REVIEW',
  'READY_TO_COMMIT',
  'VALIDATION_FAILED',
]);

/**
 * Statuses from which a batch may be ARCHIVED — the non-destructive,
 * retention-only terminal action (mirrors the same backend policy). Archiving
 * NEVER touches already-committed rows; it only removes the batch record from
 * the default imports list. VALIDATION_FAILED intentionally appears in BOTH
 * sets — the backend allows cancelling OR archiving it directly.
 */
const ARCHIVABLE_IMPORT_STATUSES: ReadonlySet<ImportBatchStatus> = new Set([
  'COMMITTED',
  'PARTIALLY_COMMITTED',
  'CANCELLED',
  'FAILED',
  'SCAN_FAILED',
  'VALIDATION_FAILED',
]);

export function canCancelImportStatus(status: ImportBatchStatus): boolean {
  return CANCELLABLE_IMPORT_STATUSES.has(status);
}

export function canArchiveImportStatus(status: ImportBatchStatus): boolean {
  return ARCHIVABLE_IMPORT_STATUSES.has(status);
}

export type ImportTemplateCode =
  | 'ORG_STRUCTURE_V1'
  | 'POSITION_CATALOG_V1'
  | 'JOB_PROFILE_V1'
  | 'METHODOLOGY_FACTORS_V1'
  | 'GRADE_BANDS_V1';

export type ImportErrorLevel = 'BLOCKER' | 'ERROR' | 'WARNING' | 'INFO';

/** 5-level validation pipeline (integration-blueprint §12.0). */
export type ImportValidationLevel = 'FILE' | 'STRUCTURE' | 'ROW' | 'BUSINESS' | 'SECURITY';

export interface ImportBatch {
  id: string;
  projectId: string | null;
  templateCode: ImportTemplateCode | string;
  status: ImportBatchStatus;
  originalFilename: string;
  fileSize: number;
  fileChecksum?: string | null;
  totalRowCount?: number | null;
  errorRowCount?: number | null;
  warningRowCount?: number | null;
  committedRowCount?: number | null;
  containsSalaryData: boolean;
  uploadedBy?: string | null;
  uploadedAt: string;
  committedBy?: string | null;
  committedAt?: string | null;
  traceId?: string | null;
}

export interface ImportError {
  id: string;
  importBatchRowId?: string | null;
  errorLevel: ImportErrorLevel;
  errorCode: string;
  fieldName?: string | null;
  message: string;
  suggestedFix?: string | null;
  /** Optional client-side enrichment for the 5-level indicator. */
  validationLevel?: ImportValidationLevel;
  /** Optional pre-computed row number for table display. */
  rowNumber?: number | null;
  traceId?: string | null;
}

export interface ImportUploadPayload {
  file: File;
  templateCode: ImportTemplateCode;
  projectId?: string | null;
}

export interface ImportTemplateDescriptor {
  code: ImportTemplateCode;
  permission: string;
}

/** Page response shape from the backend. */
export interface ImportPage<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
