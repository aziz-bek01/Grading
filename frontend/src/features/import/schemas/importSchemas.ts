/**
 * Zod schemas for the upload step + commit confirmation.
 *
 * Client-side file checks (size, MIME, extension) match the backend
 * gate-keeping rules in integration-blueprint §10.1 so users get fast
 * feedback. The server STILL re-checks every rule — defence in depth.
 */
import { z } from 'zod';

export const XLSX_MIME = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
export const MAX_UPLOAD_BYTES = 25 * 1024 * 1024; // 25 MB

export const ImportTemplateCodeSchema = z.enum([
  'ORG_STRUCTURE_V1',
  'POSITION_CATALOG_V1',
  'JOB_PROFILE_V1',
  'METHODOLOGY_FACTORS_V1',
  'GRADE_BANDS_V1',
]);

export const ImportUploadFormSchema = z.object({
  templateCode: ImportTemplateCodeSchema,
  projectId: z.string().uuid().optional().nullable(),
  file: z
    .instanceof(File, { message: 'import.file.error.required' })
    .refine((f) => f.size > 0, { message: 'import.file.error.empty' })
    .refine((f) => f.size <= MAX_UPLOAD_BYTES, { message: 'import.file.error.too_large' })
    .refine((f) => f.name.toLowerCase().endsWith('.xlsx'), { message: 'import.file.error.extension' })
    .refine(
      (f) => f.type === '' || f.type === XLSX_MIME,
      { message: 'import.file.error.mime' },
    ),
});

export type ImportUploadFormValues = z.infer<typeof ImportUploadFormSchema>;

/** Commit confirmation: requires acknowledgement that warnings were reviewed. */
export const ImportCommitConfirmationSchema = z.object({
  acknowledged: z.literal(true),
});
export type ImportCommitConfirmationValues = z.infer<typeof ImportCommitConfirmationSchema>;
