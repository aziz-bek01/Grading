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
