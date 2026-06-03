import { z } from 'zod';

export const ExportTypeSchema = z.enum([
  'POSITION_CATALOG',
  'JOB_PROFILES',
  'METHODOLOGY',
  'EVALUATION_MATRIX',
  'GRADE_STRUCTURE',
  'GRADE_PYRAMID',
  'SALARY_RANGES',
  'RED_GREEN_CIRCLE',
  'COMPENSATION_SCENARIOS',
  'REPORT_EXECUTIVE',
]);

export const ExportFormatSchema = z.enum(['XLSX', 'CSV', 'PDF', 'DOCX']);

export const ExportRequestFormSchema = z.object({
  exportType: ExportTypeSchema,
  format: ExportFormatSchema,
  projectId: z.string().uuid({ message: 'export.error.project_required' }),
  filterParams: z.string().optional().nullable(),
});

export type ExportRequestFormValues = z.infer<typeof ExportRequestFormSchema>;
