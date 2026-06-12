import { describe, it, expect } from 'vitest';
import { normalizeReport } from '../api/reportApi';

/**
 * Captured REAL-WIRE response from `ReportResponse` (global SNAKE_CASE,
 * `@JsonInclude(NON_NULL)`). Before FE-7 the FE cast `res.data` directly to a
 * camelCase type → "Invalid Date" + blank requestedBy/projectId. This pins the
 * snake→camel boundary (scout sweep #4 / AC10).
 */
const REAL_WIRE = {
  id: 'rep-1',
  project_id: 'proj-acme-2026',
  report_type: 'GRADE_DISTRIBUTION',
  format: 'PDF',
  status: 'GENERATED',
  title: 'Grade distribution — ACME Grading 2026',
  locale: 'ru-RU',
  requested_by: '00000000-0000-0000-0000-000000000001',
  requested_at: '2026-05-23T08:15:00Z',
  generated_at: '2026-05-23T08:15:12Z',
  expires_at: '2026-06-06T08:15:12Z',
  file_size: 184273,
  contains_salary_data: false,
  contains_personal_data: false,
  attempt_count: 1,
  failure_reason: null,
  trace_id: 'trace-rep-1',
} as const;

describe('normalizeReport (wire → domain adapter)', () => {
  it('maps snake_case wire fields to camelCase domain fields', () => {
    const r = normalizeReport(REAL_WIRE);
    expect(r.id).toBe('rep-1');
    expect(r.projectId).toBe('proj-acme-2026');
    expect(r.reportType).toBe('GRADE_DISTRIBUTION');
    expect(r.status).toBe('GENERATED');
    expect(r.requestedBy).toBe('00000000-0000-0000-0000-000000000001');
    expect(r.requestedAt).toBe('2026-05-23T08:15:00Z');
    expect(r.generatedAt).toBe('2026-05-23T08:15:12Z');
    expect(r.fileSize).toBe(184273);
    expect(r.attemptCount).toBe(1);
  });

  it('produces a parseable requestedAt (no "Invalid Date")', () => {
    const r = normalizeReport(REAL_WIRE);
    expect(Number.isNaN(new Date(r.requestedAt).getTime())).toBe(false);
  });

  it('tolerates a camelCase fallback payload', () => {
    const r = normalizeReport({
      id: 'c',
      projectId: 'p',
      reportType: 'AUDIT_SUMMARY',
      format: 'XLSX',
      status: 'QUEUED',
      requestedAt: '2026-01-01T00:00:00Z',
    });
    expect(r.projectId).toBe('p');
    expect(r.reportType).toBe('AUDIT_SUMMARY');
    expect(r.requestedAt).toBe('2026-01-01T00:00:00Z');
  });

  it('defaults missing optional fields without throwing', () => {
    const r = normalizeReport({});
    expect(r.id).toBe('');
    expect(r.requestedAt).toBe('');
    expect(r.attemptCount).toBe(0);
    expect(r.containsSalaryData).toBe(false);
  });
});
