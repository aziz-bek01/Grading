import { z } from 'zod';
import { REPORT_FORMAT_AVAILABILITY } from '../types';

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
    /** Only read/validated when reportType === 'EVALUATION_SUMMARY'. */
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
      val.reportType !== 'EVALUATION_SUMMARY' ||
      !val.evaluationFilter?.dateFrom ||
      !val.evaluationFilter?.dateTo ||
      val.evaluationFilter.dateFrom <= val.evaluationFilter.dateTo,
    {
      message: 'report.error.invalid_date_range',
      path: ['evaluationFilter', 'dateTo'],
    },
  );

export type RequestReportFormValues = z.infer<typeof RequestReportSchema>;

export const CancelReportSchema = z.object({
  cancelReason: z.string().optional().nullable(),
});

export type CancelReportFormValues = z.infer<typeof CancelReportSchema>;
