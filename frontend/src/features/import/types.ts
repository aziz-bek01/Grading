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

/**
 * Where a COMMITTED import's data actually lands — the project sub-route the
 * import materialized into, plus the i18n label for a "go there" link. Imported
 * domain data is ALWAYS project-scoped (a methodology import creates a DRAFT
 * methodology under the batch's project, never at the company level), so after a
 * commit the detail page links the user straight to it — closing the "I imported
 * it, where did it go?" gap. Returns null for templates with no dedicated view.
 */
export function importResultDestination(
  templateCode: ImportTemplateCode | string,
): { pathSuffix: string; labelKey: string } | null {
  switch (templateCode) {
    case 'ORG_STRUCTURE_V1':
      return { pathSuffix: 'organization', labelKey: 'import.details.open_organization' };
    case 'POSITION_CATALOG_V1':
    case 'JOB_PROFILE_V1':
      return { pathSuffix: 'positions', labelKey: 'import.details.open_positions' };
    case 'GRADE_BANDS_V1':
      return { pathSuffix: 'grades', labelKey: 'import.details.open_grades' };
    case 'METHODOLOGY_FACTORS_V1':
      return { pathSuffix: 'methodology', labelKey: 'import.details.open_methodology' };
    default:
      return null;
  }
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
