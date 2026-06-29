import { z } from 'zod';
import { REPORT_FORMAT_AVAILABILITY } from '../types';
import type { ReportType } from '../types';

/**
 * Report types that expose the evaluation filter panel (methodology version,
 * date range, evaluator). Add types here — not scattered as `=== 'X'` literals
 * — to extend the gate without touching component or API code individually.
 */
export const REPORT_TYPES_WITH_EVALUATION_FILTER = new Set<ReportType>([
  'EVALUATION_SUMMARY',
  'EXECUTIVE_SUMMARY',
]);

/** Returns true when `type` supports the evaluation filter panel. */
export function supportsEvaluationFilter(type: ReportType | null | undefined): boolean {
  return type != null && REPORT_TYPES_WITH_EVALUATION_FILTER.has(type);
}

export const ReportTypeSchema = z.enum([
  'GRADE_DISTRIBUTION',
  'POSITION_CATALOG',
  'EVALUATION_SUMMARY',
  'METHODOLOGY_SPEC',
  'AUDIT_SUMMARY',
  'EXECUTIVE_SUMMARY',
]);

export const ReportFormatSchema = z.enum(['PDF', 'DOCX', 'XLSX']);

/**
 * Structured EVALUATION_SUMMARY filter — methodology version, submitted-date
 * range, evaluator (PRD: reports-evaluation-filters §4). Empty arrays / empty
 * date strings are valid (= no narrowing); the only validated constraint
 * client-side is `date_from <= date_to` (mirrors backend AC-2.4
 * `REPORT_FILTER_INVALID_DATE_RANGE`, fail fast rather than silently swap).
 */
export const EvaluationReportFilterSchema = z
  .object({
    methodologyVersionIds: z.array(z.string()).optional(),
    dateFrom: z.string().optional(),
    dateTo: z.string().optional(),
    evaluatorUserIds: z.array(z.string()).optional(),
  })
  .refine(
    (val) => !val.dateFrom || !val.dateTo || val.dateFrom <= val.dateTo,
    {
      message: 'report.error.invalid_date_range',
      path: ['dateTo'],
    },
  );

export type EvaluationReportFilterFormValues = z.infer<typeof EvaluationReportFilterSchema>;

export const RequestReportSchema = z
  .object({
    reportType: ReportTypeSchema,
    format: ReportFormatSchema,
    projectId: z.string().uuid({ message: 'report.error.project_required' }),
    filterParams: z.string().optional().nullable(),
    /** Only read/validated when supportsEvaluationFilter(reportType) is true. */
    evaluationFilter: EvaluationReportFilterSchema.optional(),
  })
  .refine(
    (val) => REPORT_FORMAT_AVAILABILITY[val.reportType].includes(val.format),
    {
      message: 'report.error.format_not_supported',
      path: ['format'],
    },
  )
  .refine(
    (val) =>
      !supportsEvaluationFilter(val.reportType) ||
      !val.evaluationFilter?.dateFrom ||
      !val.evaluationFilter?.dateTo ||
      val.evaluationFilter.dateFrom <= val.evaluationFilter.dateTo,
    {
      message: 'report.error.invalid_date_range',
      path: ['evaluationFilter', 'dateTo'],
    },
  )
  // Exactly ONE methodology version is mandatory for the evaluation-bearing
  // report types: each methodology version defines its own factor set, so a
  // report must be scoped to a single version (mirrors backend
  // REPORT_FILTER_METHODOLOGY_REQUIRED). The wire still carries an array, but
  // the UI / this rule constrain it to length 1.
  .refine(
    (val) =>
      !supportsEvaluationFilter(val.reportType) ||
      (val.evaluationFilter?.methodologyVersionIds?.length ?? 0) === 1,
    {
      message: 'report.error.methodology_required',
      path: ['evaluationFilter', 'methodologyVersionIds'],
    },
  );

export type RequestReportFormValues = z.infer<typeof RequestReportSchema>;

export const CancelReportSchema = z.object({
  cancelReason: z.string().optional().nullable(),
});

export type CancelReportFormValues = z.infer<typeof CancelReportSchema>;
