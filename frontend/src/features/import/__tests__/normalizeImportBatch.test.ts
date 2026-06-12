import { describe, it, expect } from 'vitest';
import { normalizeImportBatch, normalizeImportError } from '../api/importApi';

/**
 * Captured REAL-WIRE response from `ImportBatchResponse` (global SNAKE_CASE,
 * `@JsonInclude(NON_NULL)`). Before FE-8 the FE cast `res.data` directly to a
 * camelCase type → blank uploadedAt ("Invalid Date") + blank projectId
 * (scout sweep #5 / AC11).
 */
const REAL_WIRE = {
  id: 'imp-org-1',
  project_id: 'proj-acme-2026',
  template_code: 'ORG_STRUCTURE_V1',
  status: 'READY_FOR_REVIEW',
  original_filename: 'acme_org_structure_2026.xlsx',
  file_size: 84321,
  file_checksum: 'sha256:demo',
  total_row_count: 12,
  error_row_count: 0,
  warning_row_count: 2,
  committed_row_count: 0,
  contains_salary_data: false,
  uploaded_by: '00000000-0000-0000-0000-000000000001',
  uploaded_at: '2026-05-22T09:30:00Z',
  trace_id: 'trace-imp-org-1',
} as const;

describe('normalizeImportBatch (wire → domain adapter)', () => {
  it('maps snake_case wire fields to camelCase domain fields', () => {
    const b = normalizeImportBatch(REAL_WIRE);
    expect(b.id).toBe('imp-org-1');
    expect(b.projectId).toBe('proj-acme-2026');
    expect(b.templateCode).toBe('ORG_STRUCTURE_V1');
    expect(b.status).toBe('READY_FOR_REVIEW');
    expect(b.originalFilename).toBe('acme_org_structure_2026.xlsx');
    expect(b.fileSize).toBe(84321);
    expect(b.totalRowCount).toBe(12);
    expect(b.uploadedBy).toBe('00000000-0000-0000-0000-000000000001');
    expect(b.uploadedAt).toBe('2026-05-22T09:30:00Z');
  });

  it('produces a parseable uploadedAt (no "Invalid Date")', () => {
    const b = normalizeImportBatch(REAL_WIRE);
    expect(Number.isNaN(new Date(b.uploadedAt).getTime())).toBe(false);
  });

  it('tolerates a camelCase fallback payload', () => {
    const b = normalizeImportBatch({
      id: 'c',
      projectId: 'p',
      templateCode: 'POSITION_CATALOG_V1',
      status: 'UPLOADED',
      originalFilename: 'x.xlsx',
      fileSize: 1,
      uploadedAt: '2026-01-01T00:00:00Z',
    });
    expect(b.projectId).toBe('p');
    expect(b.templateCode).toBe('POSITION_CATALOG_V1');
    expect(b.uploadedAt).toBe('2026-01-01T00:00:00Z');
  });

  it('defaults missing optional fields without throwing', () => {
    const b = normalizeImportBatch({});
    expect(b.id).toBe('');
    expect(b.uploadedAt).toBe('');
    expect(b.projectId).toBeNull();
    expect(b.containsSalaryData).toBe(false);
  });
});

describe('normalizeImportError (wire → domain adapter)', () => {
  it('maps snake_case error-row fields to camelCase', () => {
    const e = normalizeImportError({
      id: 'err-1',
      import_batch_row_id: 'row-3',
      error_level: 'WARNING',
      error_code: 'PARENT_HIERARCHY_DEEP',
      field_name: 'parentCode',
      message: 'Too deep',
      suggested_fix: 'Flatten',
      row_number: 3,
    });
    expect(e.id).toBe('err-1');
    expect(e.importBatchRowId).toBe('row-3');
    expect(e.errorLevel).toBe('WARNING');
    expect(e.errorCode).toBe('PARENT_HIERARCHY_DEEP');
    expect(e.fieldName).toBe('parentCode');
    expect(e.rowNumber).toBe(3);
  });
});
