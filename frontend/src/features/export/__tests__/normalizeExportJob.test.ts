import { describe, it, expect } from 'vitest';
import { normalizeExportJob } from '../api/exportApi';

/**
 * Captured REAL-WIRE response from `ExportJobResponse` (global SNAKE_CASE,
 * `@JsonInclude(NON_NULL)`). Before FE-6 the FE cast `res.data` directly to a
 * camelCase type → undefined dates ("Invalid Date") + blank requestedBy/
 * projectId. This pins the snake→camel boundary (scout sweep #4 / AC9).
 */
const REAL_WIRE = {
  id: 'exp-1',
  project_id: 'proj-acme-2026',
  export_type: 'EVALUATION_MATRIX',
  format: 'XLSX',
  status: 'GENERATED',
  requested_by: '00000000-0000-0000-0000-000000000001',
  requested_at: '2026-05-22T10:00:00Z',
  generated_at: '2026-05-22T10:00:08Z',
  expires_at: '2026-05-29T10:00:08Z',
  row_count: 42,
  file_size: 56234,
  contains_salary_data: false,
  contains_personal_data: false,
  trace_id: 'trace-exp-1',
} as const;

describe('normalizeExportJob (wire → domain adapter)', () => {
  it('maps snake_case wire fields to camelCase domain fields', () => {
    const j = normalizeExportJob(REAL_WIRE);
    expect(j.id).toBe('exp-1');
    expect(j.projectId).toBe('proj-acme-2026');
    expect(j.exportType).toBe('EVALUATION_MATRIX');
    expect(j.status).toBe('GENERATED');
    expect(j.requestedBy).toBe('00000000-0000-0000-0000-000000000001');
    expect(j.requestedAt).toBe('2026-05-22T10:00:00Z');
    expect(j.generatedAt).toBe('2026-05-22T10:00:08Z');
    expect(j.rowCount).toBe(42);
    expect(j.fileSize).toBe(56234);
    expect(j.containsSalaryData).toBe(false);
  });

  it('produces a parseable requestedAt (no "Invalid Date")', () => {
    const j = normalizeExportJob(REAL_WIRE);
    expect(Number.isNaN(new Date(j.requestedAt).getTime())).toBe(false);
  });

  it('tolerates a camelCase fallback payload', () => {
    const j = normalizeExportJob({
      id: 'c',
      projectId: 'p',
      exportType: 'POSITION_CATALOG',
      format: 'CSV',
      status: 'REQUESTED',
      requestedAt: '2026-01-01T00:00:00Z',
    });
    expect(j.projectId).toBe('p');
    expect(j.exportType).toBe('POSITION_CATALOG');
    expect(j.requestedAt).toBe('2026-01-01T00:00:00Z');
  });

  it('defaults missing optional fields without throwing', () => {
    const j = normalizeExportJob({});
    expect(j.id).toBe('');
    expect(j.requestedAt).toBe('');
    expect(j.requestedBy).toBeNull();
    expect(j.containsSalaryData).toBe(false);
  });
});
