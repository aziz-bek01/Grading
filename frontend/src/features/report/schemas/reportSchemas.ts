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

export const RequestReportSchema = z
  .object({
    reportType: ReportTypeSchema,
    format: ReportFormatSchema,
    projectId: z.string().uuid({ message: 'report.error.project_required' }),
    filterParams: z.string().optional().nullable(),
  })
  .refine(
    (val) => REPORT_FORMAT_AVAILABILITY[val.reportType].includes(val.format),
    {
      message: 'report.error.format_not_supported',
      path: ['format'],
    },
  );

export type RequestReportFormValues = z.infer<typeof RequestReportSchema>;

export const CancelReportSchema = z.object({
  cancelReason: z.string().optional().nullable(),
});

export type CancelReportFormValues = z.infer<typeof CancelReportSchema>;
